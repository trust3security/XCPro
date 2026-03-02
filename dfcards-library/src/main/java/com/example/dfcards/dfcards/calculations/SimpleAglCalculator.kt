package com.example.dfcards.dfcards.calculations

import android.content.Context
import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Simple AGL (Above Ground Level) calculator that combines cached terrain data with the latest
 * altitude input. The implementation emphasises a cache-first flow to minimise network work
 * while still allowing on-demand refresh when the aircraft has moved far enough.
 */
class SimpleAglCalculator(
    context: Context,
    private val nowMonoMsProvider: () -> Long = { TimeBridge.nowMonoMs() }
) {

    companion object {
        private const val TAG = "SimpleAGL"
        private const val FETCH_THROTTLE_DISTANCE_METERS = 200.0
        private const val SAME_CELL_RETRY_INTERVAL_MS = 30_000L
        private const val FAILED_FETCH_BASE_BACKOFF_MS = 5_000L
        private const val FAILED_FETCH_MAX_BACKOFF_MS = 120_000L
        private const val FAILED_FETCH_CIRCUIT_BREAKER_THRESHOLD = 5
        private const val FAILED_FETCH_CIRCUIT_OPEN_MS = 60_000L
    }

    private inline fun debug(message: () -> String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message())
        }
    }

    private val api = OpenMeteoElevationApi(context)
    private val cache = ElevationCache()
    private var lastAttemptLocation: Pair<Double, Double>? = null
    private var lastAttemptMonoMs: Long = 0L
    private var nextRetryAfterMonoMs: Long = 0L
    private var circuitOpenUntilMonoMs: Long = 0L
    private var consecutiveFailureCount: Int = 0

    suspend fun calculateAgl(
        altitude: Double,
        lat: Double,
        lon: Double,
        speed: Double? = null
    ): Double? {
        var groundElevation = cache.get(lat, lon)
        if (groundElevation == null) {
            val nowMonoMs = nowMonoMsProvider()
            val movedEnoughForFetch = lastAttemptLocation?.let { (lastLat, lastLon) ->
                haversineDistance(lastLat, lastLon, lat, lon) > FETCH_THROTTLE_DISTANCE_METERS
            } ?: true
            val sameCellRetryDue = if (lastAttemptMonoMs <= 0L) {
                true
            } else {
                nowMonoMs - lastAttemptMonoMs >= SAME_CELL_RETRY_INTERVAL_MS
            }
            val shouldAttemptByLocation = movedEnoughForFetch || sameCellRetryDue
            val canAttemptNow = shouldAttemptFetch(nowMonoMs)
            val shouldFetch = shouldAttemptByLocation && canAttemptNow

            if (!shouldFetch) {
                debug {
                    val reason = buildString {
                        if (!shouldAttemptByLocation) {
                            append("movement")
                        }
                        if (!canAttemptNow) {
                            if (isCircuitOpen(nowMonoMs)) {
                                if (isNotEmpty()) append('+')
                                append("circuit")
                            } else if (isBackoffActive(nowMonoMs)) {
                                if (isNotEmpty()) append('+')
                                append("backoff")
                            }
                        }
                    }
                    "Throttled terrain fetch; waiting for policy gate (reason=$reason)"
                }
                return null
            }

            lastAttemptLocation = lat to lon
            lastAttemptMonoMs = nowMonoMs
            groundElevation = api.fetchElevation(lat, lon)
            if (groundElevation != null) {
                cache.store(lat, lon, groundElevation)
                resetFailureState()
                debug { "Fetched ${groundElevation.toInt()}m terrain elevation" }
            } else {
                recordFetchFailure(nowMonoMs)
                Log.w(
                    TAG,
                    "No terrain data available (network issue, consecutiveFailures=$consecutiveFailureCount)"
                )
                return null
            }
        } else {
            debug {
                "Using cached terrain elevation ${groundElevation.toInt()}m"
            }
        }

        val terrain = groundElevation ?: run {
            Log.e(TAG, "Ground elevation missing after fetch attempt")
            return null
        }

        val agl = altitude - terrain

        if (agl < -50.0) {
            Log.w(TAG, "AGL very negative (${agl.toInt()}m) - verify QNH calibration")
        }

        debug {
            val speedLabel = speed?.toInt()?.toString() ?: "?"
            "AGL=${agl.toInt()}m (alt=${altitude.toInt()}m, terrain=${terrain.toInt()}m, speed=${speedLabel}m/s)"
        }

        return agl
    }

    suspend fun getTerrainElevation(lat: Double, lon: Double): Double? {
        var elevation = cache.get(lat, lon)
        if (elevation == null) {
            val nowMonoMs = nowMonoMsProvider()
            if (!shouldAttemptFetch(nowMonoMs)) {
                debug { "QNH calibration: throttled terrain fetch by retry/circuit policy" }
                return null
            }
            lastAttemptLocation = lat to lon
            lastAttemptMonoMs = nowMonoMs
            elevation = api.fetchElevation(lat, lon)
            if (elevation != null) {
                cache.store(lat, lon, elevation)
                resetFailureState()
                debug { "QNH calibration fetched ${elevation.toInt()}m elevation @ ($lat,$lon)" }
            } else {
                recordFetchFailure(nowMonoMs)
                Log.w(TAG, "QNH calibration: no terrain data available (network issue)")
            }
        } else {
            debug { "QNH calibration using cached ${elevation.toInt()}m elevation" }
        }
        return elevation
    }

    @Deprecated("Use formatAglWithStatus for better ground detection")
    fun formatAgl(agl: Double?): String = when {
        agl == null -> "---"
        agl < 5.0 -> "0"
        else -> "${agl.toInt()}"
    }

    fun formatAglWithStatus(agl: Double?, speed: Double?): Pair<String, String> = when {
        agl == null -> "---" to "NO DATA"
        agl < 5.0 && (speed == null || speed < 2.0) -> "0" to "ON GROUND"
        agl < 5.0 -> "${agl.toInt()}" to "LOW!"
        else -> "${agl.toInt()}" to "AGL"
    }

    fun getCacheStats(): CacheStats = cache.getStats()

    fun clearCache() {
        cache.clear()
        debug { "Cache cleared" }
    }

    suspend fun prefetchRoute(waypoints: List<Pair<Double, Double>>) {
        debug { "Prefetching ${waypoints.size} waypoints for terrain cache" }
        waypoints.forEach { (lat, lon) ->
            if (!cache.contains(lat, lon)) {
                api.fetchElevation(lat, lon)?.let { cache.store(lat, lon, it) }
            }
        }
        val stats = cache.getStats()
        debug { "Prefetch complete: ${stats.size} locations cached" }
    }

    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun shouldAttemptFetch(nowMonoMs: Long): Boolean {
        if (isCircuitOpen(nowMonoMs)) return false
        if (isBackoffActive(nowMonoMs)) return false
        return true
    }

    private fun isCircuitOpen(nowMonoMs: Long): Boolean =
        nowMonoMs < circuitOpenUntilMonoMs

    private fun isBackoffActive(nowMonoMs: Long): Boolean =
        nowMonoMs < nextRetryAfterMonoMs

    private fun recordFetchFailure(nowMonoMs: Long) {
        consecutiveFailureCount += 1
        val backoffMs = nextBackoffMs(consecutiveFailureCount)
        nextRetryAfterMonoMs = nowMonoMs + backoffMs
        if (consecutiveFailureCount >= FAILED_FETCH_CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntilMonoMs = nowMonoMs + FAILED_FETCH_CIRCUIT_OPEN_MS
            nextRetryAfterMonoMs = maxOf(nextRetryAfterMonoMs, circuitOpenUntilMonoMs)
        }
    }

    private fun resetFailureState() {
        consecutiveFailureCount = 0
        nextRetryAfterMonoMs = 0L
        circuitOpenUntilMonoMs = 0L
    }

    private fun nextBackoffMs(failureCount: Int): Long {
        val shift = (failureCount - 1).coerceAtLeast(0).coerceAtMost(6)
        val scaled = FAILED_FETCH_BASE_BACKOFF_MS * (1L shl shift)
        return scaled.coerceAtMost(FAILED_FETCH_MAX_BACKOFF_MS)
    }
}
