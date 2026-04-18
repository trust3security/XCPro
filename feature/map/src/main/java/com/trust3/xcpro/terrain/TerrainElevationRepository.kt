package com.trust3.xcpro.terrain

import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class TerrainElevationRepository @Inject constructor(
    @OfflineTerrainSource private val offlineDataSource: TerrainElevationDataSource,
    @OnlineTerrainSource private val onlineDataSource: TerrainElevationDataSource,
    private val clock: Clock
) : TerrainElevationReadPort {

    private data class TerrainFetchResult(
        val source: String,
        val elevationMeters: Double
    )

    companion object {
        private const val TAG = "TerrainRepo"
        private const val FETCH_THROTTLE_DISTANCE_METERS = 200.0
        private const val SAME_CELL_RETRY_INTERVAL_MS = 30_000L
        private const val FAILED_FETCH_BASE_BACKOFF_MS = 5_000L
        private const val FAILED_FETCH_MAX_BACKOFF_MS = 120_000L
        private const val FAILED_FETCH_CIRCUIT_BREAKER_THRESHOLD = 5
        private const val FAILED_FETCH_CIRCUIT_OPEN_MS = 60_000L
        private const val FAILURE_LOG_INTERVAL_MS = 30_000L
    }

    private val cache = TerrainElevationResultCache()
    private val stateLock = Any()
    private var lastAttemptLocation: Pair<Double, Double>? = null
    private var lastAttemptMonoMs: Long = 0L
    private var nextRetryAfterMonoMs: Long = 0L
    private var circuitOpenUntilMonoMs: Long = 0L
    private var consecutiveFailureCount: Int = 0

    override suspend fun getElevationMeters(lat: Double, lon: Double): Double? {
        cache.get(lat, lon)?.let { return it }

        val nowMonoMs = clock.nowMonoMs()
        val attemptAllowed = synchronized(stateLock) {
            if (!shouldAttemptFetchLocked(lat, lon, nowMonoMs)) {
                false
            } else {
                lastAttemptLocation = lat to lon
                lastAttemptMonoMs = nowMonoMs
                true
            }
        }
        if (!attemptAllowed) {
            return null
        }

        val fetched = fetchFromSources(lat, lon)
        if (fetched != null) {
            cache.store(lat, lon, fetched.elevationMeters)
            synchronized(stateLock) {
                resetFailureStateLocked()
            }
            AppLogger.d(
                TAG,
                "Fetched ${fetched.elevationMeters.toInt()}m terrain from ${fetched.source}"
            )
            return fetched.elevationMeters
        }

        synchronized(stateLock) {
            recordFetchFailureLocked(nowMonoMs)
            if (AppLogger.rateLimit(TAG, "terrain-fetch-failure", FAILURE_LOG_INTERVAL_MS)) {
                AppLogger.w(
                    TAG,
                    "No terrain data available after SRTM and Open-Meteo lookup (consecutiveFailures=$consecutiveFailureCount)"
                )
            }
        }
        return null
    }

    private suspend fun fetchFromSources(lat: Double, lon: Double): TerrainFetchResult? {
        val offlineResult = offlineDataSource.getElevationMeters(lat, lon)
        if (offlineResult != null) {
            return TerrainFetchResult(source = "SRTM", elevationMeters = offlineResult)
        }

        val onlineResult = onlineDataSource.getElevationMeters(lat, lon)
        if (onlineResult != null) {
            return TerrainFetchResult(source = "OPEN_METEO", elevationMeters = onlineResult)
        }

        return null
    }

    private fun shouldAttemptFetchLocked(lat: Double, lon: Double, nowMonoMs: Long): Boolean {
        val movedEnoughForFetch = lastAttemptLocation?.let { (lastLat, lastLon) ->
            haversineDistance(lastLat, lastLon, lat, lon) > FETCH_THROTTLE_DISTANCE_METERS
        } ?: true
        val sameCellRetryDue = if (lastAttemptMonoMs <= 0L) {
            true
        } else {
            nowMonoMs - lastAttemptMonoMs >= SAME_CELL_RETRY_INTERVAL_MS
        }
        val shouldAttemptByLocation = movedEnoughForFetch || sameCellRetryDue
        val canAttemptNow = !isCircuitOpenLocked(nowMonoMs) && !isBackoffActiveLocked(nowMonoMs)
        if (!shouldAttemptByLocation || !canAttemptNow) {
            AppLogger.d(
                TAG,
                buildString {
                    append("Terrain fetch throttled")
                    if (!shouldAttemptByLocation) append(" by movement gate")
                    if (!canAttemptNow) {
                        append(" by ")
                        append(if (isCircuitOpenLocked(nowMonoMs)) "circuit" else "backoff")
                    }
                }
            )
            return false
        }
        return true
    }

    private fun isCircuitOpenLocked(nowMonoMs: Long): Boolean =
        nowMonoMs < circuitOpenUntilMonoMs

    private fun isBackoffActiveLocked(nowMonoMs: Long): Boolean =
        nowMonoMs < nextRetryAfterMonoMs

    private fun recordFetchFailureLocked(nowMonoMs: Long) {
        consecutiveFailureCount += 1
        val backoffMs = nextBackoffMs(consecutiveFailureCount)
        nextRetryAfterMonoMs = nowMonoMs + backoffMs
        if (consecutiveFailureCount >= FAILED_FETCH_CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenUntilMonoMs = nowMonoMs + FAILED_FETCH_CIRCUIT_OPEN_MS
            nextRetryAfterMonoMs = maxOf(nextRetryAfterMonoMs, circuitOpenUntilMonoMs)
        }
    }

    private fun resetFailureStateLocked() {
        consecutiveFailureCount = 0
        nextRetryAfterMonoMs = 0L
        circuitOpenUntilMonoMs = 0L
    }

    private fun nextBackoffMs(failureCount: Int): Long {
        val shift = (failureCount - 1).coerceAtLeast(0).coerceAtMost(6)
        val scaled = FAILED_FETCH_BASE_BACKOFF_MS * (1L shl shift)
        return scaled.coerceAtMost(FAILED_FETCH_MAX_BACKOFF_MS)
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
}
