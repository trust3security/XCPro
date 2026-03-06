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

private typealias Center = AdsbTrafficRepositoryRuntime.Center
private typealias ReferencePoint = AdsbTrafficRepositoryRuntime.ReferencePoint
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
private const val NETWORK_WAIT_HOUSEKEEPING_TICK_MS = 1_000L
private const val OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS = 1_000L
private const val OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS = 10_000L
private const val OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS = 1.0
private const val OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS = 25.0
private const val OWNSHIP_REFERENCE_STALE_AFTER_MS = 120_000L

private sealed interface LoopCenterWaitState {
    data object Waiting : LoopCenterWaitState
    data object Disabled : LoopCenterWaitState
    data class Ready(val center: Center) : LoopCenterWaitState
}

private sealed interface LoopNetworkWaitState {
    data object Offline : LoopNetworkWaitState
    data object Disabled : LoopNetworkWaitState
    data object Online : LoopNetworkWaitState
}

internal fun AdsbTrafficRepositoryRuntime.handleSuccess(
    result: ProviderResult.Success,
    centerAtPoll: Center,
    nowMonoMs: Long
) {
    lastHttpStatus = result.httpCode
    remainingCredits = result.remainingCredits
    fetchedCount = result.response.states.size

    val mappedTargets = buildList {
        for (state in result.response.states) {
            val mapped = state.toTarget(nowMonoMs) ?: continue
            add(mapped)
        }
    }
    store.upsertAll(mappedTargets)
    store.purgeExpired(nowMonoMs = nowMonoMs, expiryAfterSec = EXPIRY_AFTER_SEC)
    val ownshipReference = ownshipReference(centerAtPoll, nowMonoMs = nowMonoMs)
    val nowWallEpochSec = clock.nowWallMs() / 1_000L
    val selection = store.select(
        nowMonoMs = nowMonoMs,
        nowWallEpochSec = nowWallEpochSec,
        queryCenterLat = centerAtPoll.latitude,
        queryCenterLon = centerAtPoll.longitude,
        referenceLat = ownshipReference.latitude,
        referenceLon = ownshipReference.longitude,
        ownshipAltitudeMeters = ownshipReference.altitudeMeters,
        referenceSampleMonoMs = ownshipReference.referenceSampleMonoMs,
        ownshipTrackDeg = ownshipReference.ownshipTrackDeg,
        ownshipSpeedMps = ownshipReference.ownshipSpeedMps,
        usesOwnshipReference = ownshipReference.usesOwnshipReference,
        radiusMeters = receiveRadiusKm * 1_000.0,
        verticalAboveMeters = verticalFilterAboveMeters,
        verticalBelowMeters = verticalFilterBelowMeters,
        ownshipIsCircling = ownshipCircling,
        circlingFeatureEnabled = ownshipCirclingFeatureEnabled,
        maxDisplayed = MAX_DISPLAYED_TARGETS,
        staleAfterSec = STALE_AFTER_SEC
    )
    withinRadiusCount = selection.withinRadiusCount
    withinVerticalCount = selection.withinVerticalCount
    filteredByVerticalCount = selection.filteredByVerticalCount
    cappedCount = selection.cappedCount
    emergencyAudioCandidateId = selection.emergencyAudioCandidateId
    consecutiveEmptyPolls = if (selection.withinRadiusCount == 0) {
        consecutiveEmptyPolls + 1
    } else {
        0
    }
    _targets.value = selection.displayed
    lastSuccessMonoMs = nowMonoMs
    if (selection.displayed.isNotEmpty()) {
        AppLogger.d(TAG, "ADS-B updated targets: ${selection.displayed.size}")
    }
}

internal fun AdsbTrafficRepositoryRuntime.publishFromStore(
    centerAtPoll: Center,
    nowMonoMs: Long = clock.nowMonoMs()
) {
    store.purgeExpired(nowMonoMs = nowMonoMs, expiryAfterSec = EXPIRY_AFTER_SEC)
    val ownshipReference = ownshipReference(centerAtPoll, nowMonoMs = nowMonoMs)
    val nowWallEpochSec = clock.nowWallMs() / 1_000L
    val selection = store.select(
        nowMonoMs = nowMonoMs,
        nowWallEpochSec = nowWallEpochSec,
        queryCenterLat = centerAtPoll.latitude,
        queryCenterLon = centerAtPoll.longitude,
        referenceLat = ownshipReference.latitude,
        referenceLon = ownshipReference.longitude,
        ownshipAltitudeMeters = ownshipReference.altitudeMeters,
        referenceSampleMonoMs = ownshipReference.referenceSampleMonoMs,
        ownshipTrackDeg = ownshipReference.ownshipTrackDeg,
        ownshipSpeedMps = ownshipReference.ownshipSpeedMps,
        usesOwnshipReference = ownshipReference.usesOwnshipReference,
        radiusMeters = receiveRadiusKm * 1_000.0,
        verticalAboveMeters = verticalFilterAboveMeters,
        verticalBelowMeters = verticalFilterBelowMeters,
        ownshipIsCircling = ownshipCircling,
        circlingFeatureEnabled = ownshipCirclingFeatureEnabled,
        maxDisplayed = MAX_DISPLAYED_TARGETS,
        staleAfterSec = STALE_AFTER_SEC
    )
    withinRadiusCount = selection.withinRadiusCount
    withinVerticalCount = selection.withinVerticalCount
    filteredByVerticalCount = selection.filteredByVerticalCount
    cappedCount = selection.cappedCount
    emergencyAudioCandidateId = selection.emergencyAudioCandidateId
    _targets.value = selection.displayed
    publishSnapshot()
}

internal fun AdsbTrafficRepositoryRuntime.shouldReselectForOwnshipAltitude(
    nowMonoMs: Long,
    nextOwnshipAltitudeMeters: Double?
): Boolean {
    val lastMonoMs = lastOwnshipAltitudeReselectMonoMs
    if (lastMonoMs == Long.MIN_VALUE) return true

    val deltaMeters = ownshipAltitudeDeltaMeters(
        previousAltitudeMeters = lastOwnshipAltitudeReselectMeters,
        nextAltitudeMeters = nextOwnshipAltitudeMeters
    )
    if (deltaMeters >= OWN_ALTITUDE_RESELECT_FORCE_DELTA_METERS) return true

    val elapsedMs = nowMonoMs - lastMonoMs
    if (elapsedMs < OWN_ALTITUDE_RESELECT_MIN_INTERVAL_MS) return false
    if (deltaMeters >= OWN_ALTITUDE_RESELECT_MIN_DELTA_METERS) return true

    return elapsedMs >= OWN_ALTITUDE_RESELECT_MAX_INTERVAL_MS && deltaMeters > 0.0
}

internal fun AdsbTrafficRepositoryRuntime.ownshipAltitudeDeltaMeters(
    previousAltitudeMeters: Double?,
    nextAltitudeMeters: Double?
): Double {
    if (previousAltitudeMeters == null && nextAltitudeMeters == null) return 0.0
    if (previousAltitudeMeters == null || nextAltitudeMeters == null) return Double.MAX_VALUE
    return abs(nextAltitudeMeters - previousAltitudeMeters)
}

internal fun AdsbTrafficRepositoryRuntime.hasFreshOwnshipReference(nowMonoMs: Long): Boolean {
    if (ownshipOrigin == null) return false
    val lastUpdateMonoMs = ownshipReferenceLastUpdateMonoMs ?: return false
    val ageMs = (nowMonoMs - lastUpdateMonoMs).coerceAtLeast(0L)
    return ageMs <= OWNSHIP_REFERENCE_STALE_AFTER_MS
}

internal fun AdsbTrafficRepositoryRuntime.ownshipReference(
    fallbackCenter: Center,
    nowMonoMs: Long
): ReferencePoint {
    val ownship = ownshipOrigin
    val altitude = ownshipAltitudeMeters?.takeIf { it.isFinite() }
    return if (ownship != null && hasFreshOwnshipReference(nowMonoMs = nowMonoMs)) {
        ReferencePoint(
            latitude = ownship.latitude,
            longitude = ownship.longitude,
            altitudeMeters = altitude,
            usesOwnshipReference = true,
            referenceSampleMonoMs = ownshipReferenceLastUpdateMonoMs ?: nowMonoMs,
            ownshipTrackDeg = ownshipTrackDeg,
            ownshipSpeedMps = ownshipSpeedMps
        )
    } else {
        ReferencePoint(
            latitude = fallbackCenter.latitude,
            longitude = fallbackCenter.longitude,
            altitudeMeters = altitude,
            usesOwnshipReference = false,
            referenceSampleMonoMs = null,
            ownshipTrackDeg = null,
            ownshipSpeedMps = null
        )
    }
}

internal fun AdsbTrafficRepositoryRuntime.computeNextPollDelayMs(centerAtPoll: Center, nowMonoMs: Long): Long {
    val adaptiveMs = computeAdaptivePollDelayMs(centerAtPoll)
    val budgetFloorMs = computeBudgetFloorDelayMs(nowMonoMs)
    val shouldPrioritizeNearbyTraffic = withinRadiusCount > 0
    val baseDelayMs = if (shouldPrioritizeNearbyTraffic) {
        adaptiveMs
    } else {
        maxOf(adaptiveMs, budgetFloorMs)
    }
    val authFloorMs = when (authMode) {
        AdsbAuthMode.Authenticated -> 0L
        AdsbAuthMode.Anonymous -> ANONYMOUS_POLL_FLOOR_MS
        AdsbAuthMode.AuthFailed -> AUTH_FAILED_POLL_FLOOR_MS
    }
    val delayMs = maxOf(baseDelayMs, authFloorMs)
    lastPolledCenter = centerAtPoll
    return delayMs.coerceIn(POLL_INTERVAL_HOT_MS, POLL_INTERVAL_MAX_MS)
}

internal fun AdsbTrafficRepositoryRuntime.computeAdaptivePollDelayMs(centerAtPoll: Center): Long {
    if (withinRadiusCount > 0) return POLL_INTERVAL_HOT_MS
    val movementMeters = lastPolledCenter?.let {
        AdsbGeoMath.haversineMeters(
            lat1 = it.latitude,
            lon1 = it.longitude,
            lat2 = centerAtPoll.latitude,
            lon2 = centerAtPoll.longitude
        )
    } ?: 0.0
    if (movementMeters >= MOVEMENT_FAST_POLL_THRESHOLD_METERS) return POLL_INTERVAL_HOT_MS
    return when {
        consecutiveEmptyPolls >= EMPTY_STREAK_QUIET_POLLS -> POLL_INTERVAL_QUIET_MS
        consecutiveEmptyPolls >= EMPTY_STREAK_COLD_POLLS -> POLL_INTERVAL_COLD_MS
        consecutiveEmptyPolls >= EMPTY_STREAK_WARM_POLLS -> POLL_INTERVAL_WARM_MS
        else -> POLL_INTERVAL_HOT_MS
    }
}

internal fun AdsbTrafficRepositoryRuntime.computeBudgetFloorDelayMs(nowMonoMs: Long): Long {
    val credits = remainingCredits
    if (credits != null) {
        return when {
            credits <= CREDIT_FLOOR_CRITICAL -> BUDGET_FLOOR_CRITICAL_MS
            credits <= CREDIT_FLOOR_LOW -> BUDGET_FLOOR_LOW_MS
            credits <= CREDIT_FLOOR_GUARDED -> BUDGET_FLOOR_GUARDED_MS
            else -> 0L
        }
    }
    pruneRequestHistory(nowMonoMs)
    val requestsInLastHour = requestTimesMonoMs.size
    return when {
        requestsInLastHour >= REQUESTS_PER_HOUR_CRITICAL -> BUDGET_FLOOR_CRITICAL_MS
        requestsInLastHour >= REQUESTS_PER_HOUR_LOW -> BUDGET_FLOOR_LOW_MS
        requestsInLastHour >= REQUESTS_PER_HOUR_GUARDED -> BUDGET_FLOOR_GUARDED_MS
        else -> 0L
    }
}

internal fun AdsbTrafficRepositoryRuntime.recordRequest(nowMonoMs: Long) {
    requestTimesMonoMs.addLast(nowMonoMs)
    pruneRequestHistory(nowMonoMs)
}

internal fun AdsbTrafficRepositoryRuntime.pruneRequestHistory(nowMonoMs: Long) {
    val cutoff = nowMonoMs - REQUEST_HISTORY_WINDOW_MS
    while (requestTimesMonoMs.isNotEmpty() && requestTimesMonoMs.first() < cutoff) {
        requestTimesMonoMs.removeFirst()
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.waitForCenter(): Center? {
    center?.let { return it }
    val waitResult = combine(_isEnabled, centerState) { enabled, centerValue ->
        when {
            !enabled -> LoopCenterWaitState.Disabled
            centerValue != null -> LoopCenterWaitState.Ready(centerValue)
            else -> LoopCenterWaitState.Waiting
        }
    }.first { it !is LoopCenterWaitState.Waiting }
    return when (waitResult) {
        is LoopCenterWaitState.Ready -> waitResult.center
        LoopCenterWaitState.Disabled -> null
        LoopCenterWaitState.Waiting -> null
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.awaitNetworkOnline(): Boolean {
    if (!_isEnabled.value) return false
    if (networkAvailabilityPort.isOnline.value) return true
    connectionState = AdsbConnectionState.Error(ADSB_ERROR_OFFLINE)
    lastHttpStatus = null
    lastError = ADSB_ERROR_OFFLINE
    lastNetworkFailureKind = AdsbNetworkFailureKind.NO_ROUTE
    pollingHealthPolicy.markFailureEvent(clock.nowMonoMs())
    pollingHealthPolicy.clearNextRetry()
    publishSnapshot()
    while (_isEnabled.value) {
        val waitResult = withTimeoutOrNull(NETWORK_WAIT_HOUSEKEEPING_TICK_MS) {
            combine(_isEnabled, networkAvailabilityPort.isOnline) { enabled, isOnline ->
                when {
                    !enabled -> LoopNetworkWaitState.Disabled
                    isOnline -> LoopNetworkWaitState.Online
                    else -> LoopNetworkWaitState.Offline
                }
            }.first { it != LoopNetworkWaitState.Offline }
        }
        when (waitResult) {
            LoopNetworkWaitState.Disabled -> return false
            LoopNetworkWaitState.Online -> return true
            LoopNetworkWaitState.Offline,
            null -> {
                runHousekeepingTick()
            }
        }
    }
    return false
}

internal fun AdsbTrafficRepositoryRuntime.runHousekeepingTick(nowMonoMs: Long = clock.nowMonoMs()) {
    val activeCenter = center
    if (activeCenter != null) {
        publishFromStore(centerAtPoll = activeCenter, nowMonoMs = nowMonoMs)
    } else {
        publishSnapshot()
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.delayForNextAttempt(waitMs: Long): Boolean {
    if (!_isEnabled.value) return false
    if (!networkAvailabilityPort.isOnline.value) {
        pollingHealthPolicy.clearNextRetry()
        return awaitNetworkOnline()
    }
    val normalizedWaitMs = waitMs.coerceAtLeast(0L)
    pollingHealthPolicy.scheduleNextRetry(
        nowMonoMs = clock.nowMonoMs(),
        waitMs = normalizedWaitMs
    )
    publishSnapshot()
    val interrupted = withTimeoutOrNull(normalizedWaitMs) {
        combine(_isEnabled, networkAvailabilityPort.isOnline) { enabled, isOnline ->
            when {
                !enabled -> LoopNetworkWaitState.Disabled
                !isOnline -> LoopNetworkWaitState.Offline
                else -> LoopNetworkWaitState.Online
            }
        }.first { it != LoopNetworkWaitState.Online }
    }
    pollingHealthPolicy.clearNextRetry()
    publishSnapshot()
    return when (interrupted) {
        null -> _isEnabled.value
        LoopNetworkWaitState.Disabled -> false
        LoopNetworkWaitState.Offline -> awaitNetworkOnline()
        LoopNetworkWaitState.Online -> _isEnabled.value
    }
}

internal suspend fun AdsbTrafficRepositoryRuntime.awaitCircuitBreakerReady(): Boolean {
    val probeAfterMs = pollingHealthPolicy.circuitOpenProbeDelayMsOrNull() ?: run {
        return _isEnabled.value
    }

    connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
    lastHttpStatus = null
    lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
    publishSnapshot()
    if (!delayForNextAttempt(probeAfterMs)) {
        return false
    }
    pollingHealthPolicy.transitionOpenToHalfOpen()
    connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_PROBE)
    lastError = ADSB_ERROR_CIRCUIT_BREAKER_PROBE
    publishSnapshot()
    return _isEnabled.value
}
