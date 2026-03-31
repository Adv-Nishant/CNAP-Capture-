package com.cnapcapture.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.cnapcapture.app.data.CnapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [BroadcastReceiver] that wakes the app on incoming call events and, where possible,
 * captures the CNAP name exposed through the Android telephony API.
 *
 * On Android 10+ the system restricts the data available in the PHONE_STATE broadcast, so
 * this receiver primarily serves two purposes:
 *
 *  1. Detect the start of an incoming call and start [CallMonitorService] (foreground service).
 *  2. Restart the monitor service after a device reboot (BOOT_COMPLETED).
 *
 * The actual CNAP name capture happens in [CallMonitorService] and, as a visual fallback,
 * in [CnapAccessibilityService].
 */
class CallReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed – capture service will start on next call")
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?: return

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> onCallRinging(context, phoneNumber)
                    TelephonyManager.EXTRA_STATE_IDLE -> onCallEnded(context)
                    else -> { /* OFFHOOK – call was answered, service is already running */ }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private fun onCallRinging(context: Context, phoneNumber: String) {
        Log.d(TAG, "Incoming call from $phoneNumber")

        // Start the foreground monitor service, passing the caller's number so it can
        // immediately look up any previously captured name (repeat-caller recognition).
        val serviceIntent = Intent(context, CallMonitorService::class.java).apply {
            action = CallMonitorService.ACTION_CALL_STARTED
            putExtra(CallMonitorService.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        context.startForegroundService(serviceIntent)

        // Attempt to pre-fill the name from a prior capture while the call rings.
        scope.launch {
            val repo = CnapRepository(context)
            val previous = repo.getPreviousNameForNumber(phoneNumber)
            if (previous != null) {
                Log.i(TAG, "Repeat caller $phoneNumber – previously captured name: ${previous.cnapName}")
                // The foreground service and/or the UI will surface this stored name.
            }
        }
    }

    private fun onCallEnded(context: Context) {
        Log.d(TAG, "Call ended – stopping monitor service")
        val serviceIntent = Intent(context, CallMonitorService::class.java).apply {
            action = CallMonitorService.ACTION_CALL_ENDED
        }
        context.startService(serviceIntent)
    }

    companion object {
        private const val TAG = "CnapCallReceiver"
    }
}
