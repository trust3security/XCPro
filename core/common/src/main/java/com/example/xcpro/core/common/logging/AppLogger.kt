package com.example.xcpro.core.common.logging

import android.os.SystemClock
import android.util.Log
import com.example.xcpro.core.common.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Debug-gated logger with rate limiting for hot paths.
 */
object AppLogger {
    private val lastLogMs = ConcurrentHashMap<String, Long>()
    private val rng = Random.Default
    private val isDebug = BuildConfig.DEBUG

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (!isDebug) return
        if (throwable != null) {
            Log.d(tag, message, throwable)
        } else {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (!isDebug) return
        if (throwable != null) {
            Log.i(tag, message, throwable)
        } else {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    fun redactLatLon(lat: Double?, lon: Double?): String {
        if (!isDebug) return "lat=redacted, lon=redacted"
        if (lat == null || lon == null) return "lat=--, lon=--"
        return "lat=${formatCoord(lat)}, lon=${formatCoord(lon)}"
    }

    fun redactCoord(value: Double?): String {
        if (!isDebug) return "redacted"
        if (value == null) return "--"
        return formatCoord(value)
    }

    /**
     * Returns true when the log should be emitted for the given key.
     */
    fun rateLimit(tag: String, key: String, intervalMs: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        val token = "$tag:$key"
        val last = lastLogMs[token]
        if (last != null && now - last < intervalMs) return false
        lastLogMs[token] = now
        return true
    }

    /**
     * Returns true when a sampled log should be emitted (debug only).
     */
    fun sample(probability: Double): Boolean {
        if (!isDebug) return false
        if (probability >= 1.0) return true
        if (probability <= 0.0) return false
        return rng.nextDouble() < probability
    }

    fun sample(tag: String, key: String, probability: Double): Boolean {
        if (!sample(probability)) return false
        return rateLimit(tag, key, 0L)
    }

    private fun formatCoord(value: Double): String = String.format("%.5f", value)
}
