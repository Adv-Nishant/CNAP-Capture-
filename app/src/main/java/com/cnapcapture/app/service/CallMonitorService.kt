package com.cnapcapture.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cnapcapture.app.R
import com.cnapcapture.app.data.CnapRepository
import com.cnapcapture.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the CNAP capture process alive for the full duration of an
 * incoming call. It is started by [CallReceiver] when a call begins and stopped when the
 * call ends.
 *
 * **Structured data path**: When the Android telecom stack exposes the CNAP name as a
 * structured field (via [android.telecom.Call.Details.getCallerDisplayName]), this service
 * reads it directly – the cleanest, most reliable approach.
 *
 * **Visual fallback**: If the system does *not* expose the name through the API (which is
 * likely during the early CNAP rollout), [CnapAccessibilityService] captures it by reading
 * the text rendered on the in-call screen.
 */
class CallMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: CnapRepository
    private var currentPhoneNumber: String? = null

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        repository = CnapRepository(applicationContext)
        createNotificationChannel()
        Log.d(TAG, "CallMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CALL_STARTED -> {
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
                currentPhoneNumber = number
                startForeground(NOTIFICATION_ID, buildNotification())
                Log.d(TAG, "Monitoring call from $number")
            }

            ACTION_CNAP_NAME_CAPTURED -> {
                val name = intent.getStringExtra(EXTRA_CNAP_NAME) ?: return START_NOT_STICKY
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                    ?: currentPhoneNumber
                    ?: return START_NOT_STICKY
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_ACCESSIBILITY
                saveCnapEntry(number, name, source)
            }

            ACTION_CALL_ENDED -> {
                Log.d(TAG, "Call ended – stopping service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "CallMonitorService destroyed")
    }

    // -----------------------------------------------------------------------
    // CNAP name persistence
    // -----------------------------------------------------------------------

    /**
     * Persists the captured CNAP name. Called either from this service (structured API path)
     * or via [ACTION_CNAP_NAME_CAPTURED] sent by [CnapAccessibilityService] (visual path).
     */
    private fun saveCnapEntry(phoneNumber: String, cnapName: String, source: String) {
        serviceScope.launch {
            val id = repository.insertEntry(
                phoneNumber = phoneNumber,
                cnapName = cnapName,
                timestampMs = System.currentTimeMillis(),
                source = source
            )
            Log.i(TAG, "Saved CNAP entry #$id – $phoneNumber → \"$cnapName\" ($source)")
        }
    }

    // -----------------------------------------------------------------------
    // Notification helpers
    // -----------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            getString(R.string.notification_channel_id),
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    companion object {
        private const val TAG = "CallMonitorService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_CALL_STARTED = "com.cnapcapture.app.ACTION_CALL_STARTED"
        const val ACTION_CALL_ENDED = "com.cnapcapture.app.ACTION_CALL_ENDED"
        const val ACTION_CNAP_NAME_CAPTURED = "com.cnapcapture.app.ACTION_CNAP_NAME_CAPTURED"

        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CNAP_NAME = "extra_cnap_name"
        const val EXTRA_SOURCE = "extra_source"

        const val SOURCE_TELECOM_API = "TELECOM_API"
        const val SOURCE_ACCESSIBILITY = "ACCESSIBILITY"
    }
}
