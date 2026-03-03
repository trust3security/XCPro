package com.example.xcpro.adsb

import com.example.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val TAG = "AdsbTrafficRepository"
private const val MAX_DISPLAYED_TARGETS = 30
private const val STALE_AFTER_SEC = 60
private const val EXPIRY_AFTER_SEC = 120
private const val POLL_INTERVAL_HOT_MS = 10_000L
private const val POLL_INTERVAL_WARM_MS = 20_000L
private const val POLL_INTERVAL_COLD_MS = 30_000L
private const val POLL_INTERVAL_QUIET_MS = 40_000L
private const val POLL_INTERVAL_MAX_MS = 60_000L
private const val MOVEMENT_FAST_POLL_THRESHOLD_METERS = 500.0
private const val EMPTY_STREAK_WARM_POLLS = 1
private const val EMPTY_STREAK_COLD_POLLS = 3
private const val EMPTY_STREAK_QUIET_POLLS = 6
private const val CREDIT_FLOOR_GUARDED = 500
private const val CREDIT_FLOOR_LOW = 200
private const val CREDIT_FLOOR_CRITICAL = 50
private const val BUDGET_FLOOR_GUARDED_MS = 20_000L
private const val BUDGET_FLOOR_LOW_MS = 30_000L
private const val BUDGET_FLOOR_CRITICAL_MS = 60_000L
private const val ANONYMOUS_POLL_FLOOR_MS = 30_000L
private const val AUTH_FAILED_POLL_FLOOR_MS = 45_000L
private const val REQUEST_HISTORY_WINDOW_MS = 60L * 60L * 1_000L
private const val REQUESTS_PER_HOUR_GUARDED = 120
private const val REQUESTS_PER_HOUR_LOW = 180
private const val REQUESTS_PER_HOUR_CRITICAL = 300
private const val RECONNECT_BACKOFF_START_MS = 2_000L
private const val RECONNECT_BACKOFF_MAX_MS = 60_000L
private const val NETWORK_WAIT_HOUSEKEEPING_TICK_MS = 1_000L
private const val OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS = 1_000L
private const val OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS = 10_000L
private const val OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS = 1.0
private const val OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS = 25.0

internal fun AdsbTrafficRepositoryRuntime.ensureLoopRunning() {
    synchronized(loopJobLock) {
        val existing = loopJob
        if (existing != null && existing.isActive) return
        loopJob = scope.launch {
            runLoop()
        }
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.stopLoop(clearTargets: Boolean) {
    val jobToCancel = synchronized(loopJobLock) {
        val existing = loopJob
        loopJob = null
        existing
    }
    jobToCancel?.cancelAndJoin()
    connectionState = AdsbConnectionState.Disabled
    lastError = null
    lastNetworkFailureKind = null
    pollingHealthPolicy.resetForStop()
    if (clearTargets) {
        clearCachedTargets()
    }
    publishSnapshot()
}

internal fun AdsbTrafficRepositoryRuntime.clearCachedTargets() {
    store.clear()
    _targets.value = emptyList()
    fetchedCount = 0
    withinRadiusCount = 0
    withinVerticalCount = 0
    filteredByVerticalCount = 0
    cappedCount = 0
    consecutiveEmptyPolls = 0
    lastPolledCenter = null
    lastOwnshipAltitudeReselectMonoMs = Long.MIN_VALUE
    lastOwnshipAltitudeReselectMeters = ownshipAltitudeMeters
}

internal fun AdsbTrafficRepositoryRuntime.observeEmergencyAudioSettings() {
    scope.launch {
        combine(
            emergencyAudioSettingsPort.emergencyAudioEnabledFlow,
            emergencyAudioSettingsPort.emergencyAudioCooldownMsFlow
        ) { enabled, cooldownMs ->
            AdsbEmergencyAudioSettings(
                enabled = enabled,
                cooldownMs = cooldownMs
            )
        }.collect { settings ->
            emergencyAudioSettings = settings
            publishSnapshot()
        }
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.runLoop() {
    val thisJob = currentCoroutineContext()[Job]
    var backoffMs = RECONNECT_BACKOFF_START_MS
    try {
        while (_isEnabled.value) {
            try {
                val centerAtPoll = waitForCenter() ?: break
                val loopMonoMs = clock.nowMonoMs()
                publishFromStore(centerAtPoll, loopMonoMs)
                if (!awaitNetworkOnline()) break
                if (!awaitCircuitBreakerReady()) break
                lastPollMonoMs = clock.nowMonoMs()

                val bbox = AdsbGeoMath.computeBbox(
                    centerLat = centerAtPoll.latitude,
                    centerLon = centerAtPoll.longitude,
                    radiusKm = receiveRadiusKm.toDouble()
                )
                when (val result = fetchWithAuthRetry(bbox)) {
                    is ProviderResult.Success -> {
                        val nowSuccessMonoMs = clock.nowMonoMs()
                        handleSuccess(result, centerAtPoll, nowSuccessMonoMs)
                        pollingHealthPolicy.resetAfterSuccessfulRequest()
                        connectionState = AdsbConnectionState.Active
                        lastError = null
                        lastNetworkFailureKind = null
                        publishSnapshot()
                        backoffMs = RECONNECT_BACKOFF_START_MS
                        if (!delayForNextAttempt(computeNextPollDelayMs(centerAtPoll, nowSuccessMonoMs))) {
                            break
                        }
                    }

                    is ProviderResult.RateLimited -> {
                        pollingHealthPolicy.resetAfterSuccessfulRequest()
                        lastHttpStatus = 429
                        remainingCredits = result.remainingCredits
                        connectionState = AdsbConnectionState.BackingOff(result.retryAfterSec)
                        lastError = null
                        lastNetworkFailureKind = null
                        publishSnapshot()
                        if (!delayForNextAttempt(result.retryAfterSec.coerceAtLeast(1) * 1_000L)) {
                            break
                        }
                    }

                    is ProviderResult.HttpError -> {
                        val nowFailureMonoMs = clock.nowMonoMs()
                        lastHttpStatus = result.code
                        lastError = "HTTP ${result.code}"
                        lastNetworkFailureKind = null
                        pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
                        connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                        publishSnapshot()
                        if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
                            connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
                            lastHttpStatus = null
                            lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
                            publishSnapshot()
                            backoffMs = RECONNECT_BACKOFF_START_MS
                            continue
                        }
                        val waitMs = withJitter(backoffMs)
                        if (!delayForNextAttempt(waitMs)) {
                            break
                        }
                        backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                    }

                    is ProviderResult.NetworkError -> {
                        val nowFailureMonoMs = clock.nowMonoMs()
                        lastHttpStatus = null
                        lastNetworkFailureKind = result.kind
                        lastError = result.toDebugMessage()
                        pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
                        connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                        publishSnapshot()
                        if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
                            connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
                            lastHttpStatus = null
                            lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
                            publishSnapshot()
                            backoffMs = RECONNECT_BACKOFF_START_MS
                            continue
                        }
                        val waitMs = maxOf(
                            withJitter(backoffMs),
                            networkFailureRetryFloorMs(result.kind)
                        )
                        if (!delayForNextAttempt(waitMs)) {
                            break
                        }
                        backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "ADS-B loop recovered from unexpected error: ${e::class.java.simpleName}")
                val nowFailureMonoMs = clock.nowMonoMs()
                lastHttpStatus = null
                lastNetworkFailureKind = AdsbNetworkFailureKind.UNKNOWN
                lastError = "Unexpected ADS-B loop failure"
                pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
                connectionState = AdsbConnectionState.Error(lastError.orEmpty())
                publishSnapshot()
                if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
                    connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
                    lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
                    publishSnapshot()
                    backoffMs = RECONNECT_BACKOFF_START_MS
                    continue
                }
                val waitMs = maxOf(
                    withJitter(backoffMs),
                    networkFailureRetryFloorMs(AdsbNetworkFailureKind.UNKNOWN)
                )
                if (!delayForNextAttempt(waitMs)) {
                    break
                }
                backoffMs = (backoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
            }
        }
        connectionState = AdsbConnectionState.Disabled
        publishSnapshot()
    } finally {
        synchronized(loopJobLock) {
            if (loopJob == thisJob) {
                loopJob = null
            }
        }
    }
}

