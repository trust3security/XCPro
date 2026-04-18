package com.trust3.xcpro.adsb

import com.trust3.xcpro.core.common.logging.AppLogger
import kotlinx.coroutines.CancellationException

private const val TAG = "AdsbTrafficRepository"

internal sealed interface AdsbRuntimeLoopStep {
    data class Continue(val nextBackoffMs: Long) : AdsbRuntimeLoopStep
    data object Stop : AdsbRuntimeLoopStep
}

internal suspend fun AdsbTrafficRepositoryRuntime.executePollCycle(
    centerAtPoll: AdsbTrafficRepositoryRuntime.Center,
    currentBackoffMs: Long
): AdsbRuntimeLoopStep {
    if (!prepareForPoll(centerAtPoll)) return AdsbRuntimeLoopStep.Stop
    val bbox = AdsbGeoMath.computeBbox(
        centerLat = centerAtPoll.latitude,
        centerLon = centerAtPoll.longitude,
        radiusKm = receiveRadiusKm.toDouble()
    )
    return handleProviderResult(
        result = fetchWithAuthRetry(bbox = bbox),
        centerAtPoll = centerAtPoll,
        currentBackoffMs = currentBackoffMs
    )
}

internal suspend fun AdsbTrafficRepositoryRuntime.prepareForPoll(
    centerAtPoll: AdsbTrafficRepositoryRuntime.Center
): Boolean {
    val loopMonoMs = clock.nowMonoMs()
    publishFromStore(centerAtPoll, loopMonoMs)
    if (!awaitNetworkOnline()) return false
    if (!awaitCircuitBreakerReady()) return false
    lastPollMonoMs = clock.nowMonoMs()
    return true
}

internal suspend fun AdsbTrafficRepositoryRuntime.handleProviderResult(
    result: ProviderResult,
    centerAtPoll: AdsbTrafficRepositoryRuntime.Center,
    currentBackoffMs: Long
): AdsbRuntimeLoopStep = when (result) {
    is ProviderResult.Success -> handleSuccessResult(result = result, centerAtPoll = centerAtPoll)
    is ProviderResult.RateLimited -> handleRateLimitedResult(result)
    is ProviderResult.HttpError -> handleHttpErrorResult(
        result = result,
        currentBackoffMs = currentBackoffMs
    )
    is ProviderResult.NetworkError -> handleNetworkErrorResult(
        result = result,
        currentBackoffMs = currentBackoffMs
    )
}

internal suspend fun AdsbTrafficRepositoryRuntime.handleUnexpectedLoopFailure(
    throwable: Throwable,
    currentBackoffMs: Long
): AdsbRuntimeLoopStep {
    if (throwable is CancellationException) throw throwable
    AppLogger.w(TAG, "ADS-B loop recovered from unexpected error: ${throwable::class.java.simpleName}")

    val nowFailureMonoMs = clock.nowMonoMs()
    lastHttpStatus = null
    lastNetworkFailureKind = AdsbNetworkFailureKind.UNKNOWN
    lastError = "Unexpected ADS-B loop failure"
    pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
    connectionState = AdsbConnectionState.Error(lastError.orEmpty())
    publishSnapshot()
    if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
        transitionToCircuitBreakerOpen()
        return AdsbRuntimeLoopStep.Continue(RECONNECT_BACKOFF_START_MS)
    }
    val waitMs = maxOf(
        withJitter(currentBackoffMs),
        networkFailureRetryFloorMs(AdsbNetworkFailureKind.UNKNOWN)
    )
    return waitForNextAttempt(waitMs = waitMs, nextBackoffMs = nextExponentialBackoffMs(currentBackoffMs))
}

private suspend fun AdsbTrafficRepositoryRuntime.handleSuccessResult(
    result: ProviderResult.Success,
    centerAtPoll: AdsbTrafficRepositoryRuntime.Center
): AdsbRuntimeLoopStep {
    val nowSuccessMonoMs = clock.nowMonoMs()
    handleSuccess(result, centerAtPoll, nowSuccessMonoMs)
    pollingHealthPolicy.resetAfterSuccessfulRequest()
    connectionState = AdsbConnectionState.Active
    lastError = null
    lastNetworkFailureKind = null
    publishSnapshot()
    val waitMs = computeNextPollDelayMs(centerAtPoll, nowSuccessMonoMs)
    return waitForNextAttempt(waitMs = waitMs, nextBackoffMs = RECONNECT_BACKOFF_START_MS)
}

private suspend fun AdsbTrafficRepositoryRuntime.handleRateLimitedResult(
    result: ProviderResult.RateLimited
): AdsbRuntimeLoopStep {
    pollingHealthPolicy.resetAfterSuccessfulRequest()
    lastHttpStatus = 429
    remainingCredits = result.remainingCredits
    connectionState = AdsbConnectionState.BackingOff(result.retryAfterSec)
    lastError = null
    lastNetworkFailureKind = null
    publishSnapshot()
    val waitMs = result.retryAfterSec.coerceAtLeast(1) * 1_000L
    return waitForNextAttempt(waitMs = waitMs, nextBackoffMs = RECONNECT_BACKOFF_START_MS)
}

private suspend fun AdsbTrafficRepositoryRuntime.handleHttpErrorResult(
    result: ProviderResult.HttpError,
    currentBackoffMs: Long
): AdsbRuntimeLoopStep {
    val nowFailureMonoMs = clock.nowMonoMs()
    lastHttpStatus = result.code
    lastError = "HTTP ${result.code}"
    lastNetworkFailureKind = null
    pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
    connectionState = AdsbConnectionState.Error(lastError.orEmpty())
    publishSnapshot()
    if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
        transitionToCircuitBreakerOpen()
        return AdsbRuntimeLoopStep.Continue(RECONNECT_BACKOFF_START_MS)
    }
    val waitMs = withJitter(currentBackoffMs)
    return waitForNextAttempt(waitMs = waitMs, nextBackoffMs = nextExponentialBackoffMs(currentBackoffMs))
}

private suspend fun AdsbTrafficRepositoryRuntime.handleNetworkErrorResult(
    result: ProviderResult.NetworkError,
    currentBackoffMs: Long
): AdsbRuntimeLoopStep {
    val nowFailureMonoMs = clock.nowMonoMs()
    lastHttpStatus = null
    lastNetworkFailureKind = result.kind
    lastError = result.toDebugMessage()
    pollingHealthPolicy.markFailureEvent(nowFailureMonoMs)
    connectionState = AdsbConnectionState.Error(lastError.orEmpty())
    publishSnapshot()
    if (pollingHealthPolicy.recordFailureAndMaybeOpenCircuit(nowFailureMonoMs)) {
        transitionToCircuitBreakerOpen()
        return AdsbRuntimeLoopStep.Continue(RECONNECT_BACKOFF_START_MS)
    }
    val waitMs = maxOf(
        withJitter(currentBackoffMs),
        networkFailureRetryFloorMs(result.kind)
    )
    return waitForNextAttempt(waitMs = waitMs, nextBackoffMs = nextExponentialBackoffMs(currentBackoffMs))
}

private suspend fun AdsbTrafficRepositoryRuntime.waitForNextAttempt(
    waitMs: Long,
    nextBackoffMs: Long
): AdsbRuntimeLoopStep {
    return if (delayForNextAttempt(waitMs)) {
        AdsbRuntimeLoopStep.Continue(nextBackoffMs)
    } else {
        AdsbRuntimeLoopStep.Stop
    }
}

private fun AdsbTrafficRepositoryRuntime.transitionToCircuitBreakerOpen() {
    connectionState = AdsbConnectionState.Error(ADSB_ERROR_CIRCUIT_BREAKER_OPEN)
    lastHttpStatus = null
    lastError = ADSB_ERROR_CIRCUIT_BREAKER_OPEN
    publishSnapshot()
}

private fun nextExponentialBackoffMs(currentBackoffMs: Long): Long =
    (currentBackoffMs * 2L).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
