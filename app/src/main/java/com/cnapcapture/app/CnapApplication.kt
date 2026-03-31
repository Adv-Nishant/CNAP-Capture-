package com.cnapcapture.app

import android.app.Application
import android.util.Log

/**
 * Custom [Application] subclass.  Declared in AndroidManifest.xml so it is instantiated
 * once when the process starts.
 *
 * Currently used to log the app startup and to give a single hook point for any future
 * process-level initialization (e.g. dependency injection, crash reporting).
 */
class CnapApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "CNAP Capture application started")
    }

    companion object {
        private const val TAG = "CnapApplication"
    }
}
