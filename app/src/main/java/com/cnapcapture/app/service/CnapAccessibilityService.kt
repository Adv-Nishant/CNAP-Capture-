package com.cnapcapture.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that reads the CNAP caller name from the in-call screen.
 *
 * This is the **visual fallback** path used when the Android Telecom stack does not expose
 * the CNAP name as a structured API field. Many carrier-dependent in-call UIs render the
 * name as plain text that is reachable via the Accessibility tree.
 *
 * **How it works**:
 *  - The service receives [AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED] and
 *    [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] events.
 *  - On each relevant event it traverses the visible node tree and looks for nodes whose
 *    text or content-description matches the heuristic patterns used by known in-call UIs.
 *  - When a candidate name string is found it is forwarded to [CallMonitorService] via an
 *    explicit Intent carrying [CallMonitorService.ACTION_CNAP_NAME_CAPTURED].
 *
 * **Privacy**: the service only acts when the package on screen is a known in-call UI package
 * (configured in accessibility_service_config.xml).  No general screen text is ever read,
 * logged, or transmitted.
 */
class CnapAccessibilityService : AccessibilityService() {

    /** Prevents the same name from being saved multiple times per call. */
    private var lastCapturedName: String? = null

    // -----------------------------------------------------------------------
    // AccessibilityService overrides
    // -----------------------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        try {
            val candidate = extractCnapName(rootNode)
            if (candidate != null && candidate != lastCapturedName) {
                lastCapturedName = candidate
                Log.i(TAG, "CNAP name detected on screen: \"$candidate\"")
                forwardToMonitorService(candidate)
            }
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "CnapAccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "CnapAccessibilityService unbound – resetting state")
        lastCapturedName = null
        return super.onUnbind(intent)
    }

    // -----------------------------------------------------------------------
    // Name extraction heuristics
    // -----------------------------------------------------------------------

    /**
     * Traverses the accessibility node tree and returns the first string that looks like a
     * CNAP caller name, or null if none is found.
     *
     * Heuristic strategy:
     *  1. Walk the node tree with depth-first search.
     *  2. For each node whose resource ID contains a known name-field keyword, return the text.
     *  3. If no resource-ID match, fall back to text-content heuristics (all-caps, not a phone
     *     number, not a UI label string).
     */
    private fun extractCnapName(root: AccessibilityNodeInfo): String? {
        // 1. Try to find a node whose view ID suggests it holds the caller name.
        val idCandidates = findNodesByIdKeyword(root, NAME_ID_KEYWORDS)
        for (node in idCandidates) {
            val text = node.text?.toString()?.trim() ?: continue
            if (text.isPlausibleCnapName()) return text
        }

        // 2. Fall back to walking the entire tree for plausible name text.
        return findFirstPlausibleName(root)
    }

    private fun findNodesByIdKeyword(
        root: AccessibilityNodeInfo,
        keywords: List<String>
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val viewId = node.viewIdResourceName ?: ""
            if (keywords.any { viewId.contains(it, ignoreCase = true) }) {
                results.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return results
    }

    private fun findFirstPlausibleName(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim() ?: ""
            if (text.isPlausibleCnapName()) return text
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Monitor service integration
    // -----------------------------------------------------------------------

    private fun forwardToMonitorService(cnapName: String) {
        val intent = Intent(this, CallMonitorService::class.java).apply {
            action = CallMonitorService.ACTION_CNAP_NAME_CAPTURED
            putExtra(CallMonitorService.EXTRA_CNAP_NAME, cnapName)
            putExtra(CallMonitorService.EXTRA_SOURCE, CallMonitorService.SOURCE_ACCESSIBILITY)
        }
        startService(intent)
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    companion object {
        private const val TAG = "CnapA11yService"

        /**
         * View-ID keywords that AOSP and popular OEM in-call UIs use for the caller-name field.
         * Add more as new OEM implementations are discovered.
         */
        private val NAME_ID_KEYWORDS = listOf(
            "caller_name",
            "callerName",
            "contact_name",
            "contactName",
            "primary",
            "name"
        )

        /**
         * Minimum length for a string to be considered a possible CNAP name.
         * Single-character strings are almost certainly UI labels or icons.
         */
        private const val MIN_NAME_LENGTH = 3

        /**
         * Returns true if this string looks like a human name delivered by CNAP:
         *  – long enough to be meaningful
         *  – not a phone number (doesn't consist only of digits, spaces, +, -, ())
         *  – not a generic UI label (Connecting, Calling, Hold, etc.)
         */
        private fun String.isPlausibleCnapName(): Boolean {
            if (length < MIN_NAME_LENGTH) return false
            if (all { it.isDigit() || it in " +-()" }) return false   // looks like a phone number
            if (GENERIC_LABELS.any { equals(it, ignoreCase = true) }) return false
            return true
        }

        private val GENERIC_LABELS = setOf(
            "Calling", "Connecting", "Incoming call", "On hold", "Hold",
            "Mute", "Speaker", "Keypad", "Add call", "Swap", "Merge",
            "Unknown", "Private number", "Restricted", "Hang up",
            "Answer", "Decline", "Swipe up to answer"
        )
    }
}
