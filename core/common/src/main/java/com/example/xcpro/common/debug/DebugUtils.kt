package com.example.xcpro.common.debug

import android.util.Log

/**
 * Shared debug helpers for conditional logging used by multiple feature modules.
 */
object DebugUtils {
    private const val TAG = "DebugUtils"

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    fun logWarning(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun reportCrash(throwable: Throwable, context: String = "") {
        logError(TAG, "Crash reported: $context", throwable)
        // Hook crash reporting in production (e.g., Crashlytics)
    }
}
