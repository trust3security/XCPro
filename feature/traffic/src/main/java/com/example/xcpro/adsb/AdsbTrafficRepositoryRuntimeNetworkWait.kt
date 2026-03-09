package com.example.xcpro.adsb

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun AdsbTrafficRepositoryRuntime.waitForCenter():
    AdsbTrafficRepositoryRuntime.Center? {
    center?.let { return it }
    val waitResult = combine(_isEnabled, centerState) { enabled, centerValue ->
        when {
            !enabled -> CenterWaitState.Disabled
            centerValue != null -> CenterWaitState.Ready(centerValue)
            else -> CenterWaitState.Waiting
        }
    }.first { it !is CenterWaitState.Waiting }
    return when (waitResult) {
        is CenterWaitState.Ready -> waitResult.center
        CenterWaitState.Disabled -> null
        CenterWaitState.Waiting -> null
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
                    !enabled -> NetworkWaitState.Disabled
                    isOnline -> NetworkWaitState.Online
                    else -> NetworkWaitState.Offline
                }
            }.first { it != NetworkWaitState.Offline }
        }
        when (waitResult) {
            NetworkWaitState.Disabled -> return false
            NetworkWaitState.Online -> return true
            NetworkWaitState.Offline,
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
                !enabled -> NetworkWaitState.Disabled
                !isOnline -> NetworkWaitState.Offline
                else -> NetworkWaitState.Online
            }
        }.first { it != NetworkWaitState.Online }
    }
    pollingHealthPolicy.clearNextRetry()
    publishSnapshot()
    return when (interrupted) {
        null -> _isEnabled.value
        NetworkWaitState.Disabled -> false
        NetworkWaitState.Offline -> awaitNetworkOnline()
        NetworkWaitState.Online -> _isEnabled.value
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
