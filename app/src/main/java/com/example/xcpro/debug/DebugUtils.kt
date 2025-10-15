package com.example.xcpro.debug

import android.util.Log

object DebugUtils {
    private const val TAG = "DebugUtils"
    
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        // Always log in debug builds
        Log.e(tag, message, throwable)
    }
    
    fun logWarning(tag: String, message: String) {
        // Always log in debug builds  
        Log.w(tag, message)
    }
    
    fun logInfo(tag: String, message: String) {
        // Always log in debug builds
        Log.i(tag, message)
    }
    
    fun logDebug(tag: String, message: String) {
        // Always log in debug builds
        Log.d(tag, message)
    }
    
    fun reportCrash(throwable: Throwable, context: String = "") {
        logError(TAG, "Crash reported: $context", throwable)
        // In production, integrate with Firebase Crashlytics or similar
    }
}