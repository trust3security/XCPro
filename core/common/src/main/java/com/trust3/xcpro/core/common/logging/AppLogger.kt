package com.trust3.xcpro.core.common.logging

import android.util.Log
import com.trust3.xcpro.core.common.BuildConfig
import com.trust3.xcpro.core.time.TimeBridge
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Canonical production logging seam for Kotlin callsites.
 *
 * This object is an allowed infrastructure singleton: it owns only bounded,
 * non-authoritative helper state for rate limiting and sampling. It must not
 * become an application state owner or a side-channel source of truth.
 */
object AppLogger {
    private val lastLogMs = ConcurrentHashMap<String, Long>()
    private val localJvmFallbackMonoMs = AtomicLong()
    private val rng = Random.Default
    private val isDebug = BuildConfig.DEBUG

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (!isDebug) return
        emitSafely {
            if (throwable != null) {
                Log.d(tag, message, throwable)
            } else {
                Log.d(tag, message)
            }
        }
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (!isDebug) return
        emitSafely {
            if (throwable != null) {
                Log.i(tag, message, throwable)
            } else {
                Log.i(tag, message)
            }
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        emitSafely {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        emitSafely {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
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
        val now = safeNowMonoMs()
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

    fun dRateLimited(tag: String, key: String, intervalMs: Long, message: () -> String) {
        if (!isDebug) return
        if (!rateLimit(tag, key, intervalMs)) return
        d(tag, message())
    }

    fun dSampled(tag: String, key: String, probability: Double, message: () -> String) {
        if (!isDebug) return
        if (!sample(tag, key, probability)) return
        d(tag, message())
    }

    private fun formatCoord(value: Double): String = String.format(Locale.US, "%.5f", value)

    private fun safeNowMonoMs(): Long {
        return try {
            TimeBridge.nowMonoMs()
        } catch (_: RuntimeException) {
            localJvmFallbackMonoMs.incrementAndGet()
        } catch (_: LinkageError) {
            localJvmFallbackMonoMs.incrementAndGet()
        }
    }

    // Local JVM tests use android.jar stubs; logging must stay non-fatal there.
    private inline fun emitSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
        } catch (_: LinkageError) {
        }
    }
}
