package com.cnapcapture.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the name-extraction heuristics used by [CnapAccessibilityService].
 *
 * The actual accessibility service cannot be instantiated in a JVM unit test because it
 * extends [android.accessibilityservice.AccessibilityService], which requires the Android
 * framework.  The heuristic logic is therefore replicated here as a standalone pure-Kotlin
 * function so it can be tested without Robolectric.
 */
class CnapNameHeuristicTest {

    // -----------------------------------------------------------------------
    // Mirror of the heuristic from CnapAccessibilityService
    // -----------------------------------------------------------------------

    private val genericLabels = setOf(
        "Calling", "Connecting", "Incoming call", "On hold", "Hold",
        "Mute", "Speaker", "Keypad", "Add call", "Swap", "Merge",
        "Unknown", "Private number", "Restricted", "Hang up",
        "Answer", "Decline", "Swipe up to answer"
    )

    private fun String.isPlausibleCnapName(): Boolean {
        if (length < 3) return false
        if (all { it.isDigit() || it in " +-()" }) return false
        if (genericLabels.any { equals(it, ignoreCase = true) }) return false
        return true
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `real name is plausible`() {
        assertTrue("RAJESH KUMAR".isPlausibleCnapName())
        assertTrue("Priya Sharma".isPlausibleCnapName())
        assertTrue("John Smith".isPlausibleCnapName())
    }

    @Test
    fun `phone number string is not plausible`() {
        assertFalse("+91 98765 43210".isPlausibleCnapName())
        assertFalse("09876543210".isPlausibleCnapName())
        assertFalse("(555) 867-5309".isPlausibleCnapName())
    }

    @Test
    fun `too-short string is not plausible`() {
        assertFalse("".isPlausibleCnapName())
        assertFalse("A".isPlausibleCnapName())
        assertFalse("AB".isPlausibleCnapName())
    }

    @Test
    fun `generic UI labels are not plausible`() {
        assertFalse("Calling".isPlausibleCnapName())
        assertFalse("CALLING".isPlausibleCnapName())
        assertFalse("Incoming call".isPlausibleCnapName())
        assertFalse("Unknown".isPlausibleCnapName())
        assertFalse("Hold".isPlausibleCnapName())
        assertFalse("Mute".isPlausibleCnapName())
        assertFalse("Decline".isPlausibleCnapName())
    }

    @Test
    fun `name with numbers is plausible (e_g business names)`() {
        assertTrue("7-Eleven Store".isPlausibleCnapName())
        assertTrue("3M India".isPlausibleCnapName())
    }

    @Test
    fun `minimum-length boundary`() {
        assertFalse("AB".isPlausibleCnapName())   // length 2 – too short
        assertTrue("ABC".isPlausibleCnapName())   // length 3 – just enough
    }
}
