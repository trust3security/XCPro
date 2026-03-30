package com.example.xcpro.ogn

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val NETWORK_WAIT_HOUSEKEEPING_TICK_MS = 1_000L

internal suspend fun OgnTrafficRepositoryRuntime.waitForCenter():
    OgnTrafficRepositoryRuntime.Center? {
    center?.let { return it }
    val waitResult = combine(_isEnabled, centerState) { enabled, centerValue ->
        when {
            !enabled -> OgnCenterWaitState.Disabled
            centerValue != null -> OgnCenterWaitState.Ready(centerValue)
            else -> OgnCenterWaitState.Waiting
        }
    }.first { it !is OgnCenterWaitState.Waiting }
    return when (waitResult) {
        is OgnCenterWaitState.Ready -> waitResult.center
        OgnCenterWaitState.Disabled -> null
        OgnCenterWaitState.Waiting -> null
    }
}

internal suspend fun OgnTrafficRepositoryRuntime.awaitNetworkOnline(): Boolean {
    if (!_isEnabled.value) return false
    if (currentNetworkOnlineState()) return true
    runOnWriter {
        networkOnline = false
        connectionState = OgnConnectionState.ERROR
        connectionIssue = OgnConnectionIssue.OFFLINE_WAIT
        lastError = OGN_ERROR_OFFLINE
        reconnectBackoffMs = null
        publishSnapshot()
    }
    while (_isEnabled.value) {
        val waitResult = withTimeoutOrNull(NETWORK_WAIT_HOUSEKEEPING_TICK_MS) {
            combine(_isEnabled, networkAvailabilityPort.isOnline) { enabled, isOnline ->
                val resolvedOnline = isOnline || currentNetworkOnlineState()
                when {
                    !enabled -> OgnNetworkWaitState.Disabled
                    resolvedOnline -> OgnNetworkWaitState.Online
                    else -> OgnNetworkWaitState.Offline
                }
            }.first { it != OgnNetworkWaitState.Offline }
        }
        when (waitResult) {
            OgnNetworkWaitState.Disabled -> return false
            OgnNetworkWaitState.Online -> return true
            OgnNetworkWaitState.Offline,
            null -> {
                if (currentNetworkOnlineState()) return true
                runHousekeepingTick()
            }
        }
    }
    return false
}

internal suspend fun OgnTrafficRepositoryRuntime.delayForNextAttempt(waitMs: Long): Boolean {
    if (!_isEnabled.value) return false
    if (!currentNetworkOnlineState()) {
        return awaitNetworkOnline()
    }
    val normalizedWaitMs = waitMs.coerceAtLeast(0L)
    runOnWriter {
        reconnectBackoffMs = normalizedWaitMs
        lastReconnectWallMs = clock.nowWallMs()
        publishSnapshot()
    }
    val interrupted = withTimeoutOrNull(normalizedWaitMs) {
        combine(_isEnabled, networkAvailabilityPort.isOnline) { enabled, isOnline ->
            val resolvedOnline = isOnline || currentNetworkOnlineState()
            when {
                !enabled -> OgnNetworkWaitState.Disabled
                !resolvedOnline -> OgnNetworkWaitState.Offline
                else -> OgnNetworkWaitState.Online
            }
        }.first { it != OgnNetworkWaitState.Online }
    }
    return when (interrupted) {
        null -> _isEnabled.value
        OgnNetworkWaitState.Disabled -> false
        OgnNetworkWaitState.Offline -> {
            if (currentNetworkOnlineState()) _isEnabled.value else awaitNetworkOnline()
        }
        OgnNetworkWaitState.Online -> _isEnabled.value
    }
}

internal suspend fun OgnTrafficRepositoryRuntime.runHousekeepingTick(
    nowMonoMs: Long = clock.nowMonoMs()
) {
    runOnWriter {
        sweepStaleTargets(nowMonoMs)
    }
}

internal fun OgnTrafficRepositoryRuntime.currentNetworkOnlineState(): Boolean =
    runCatching { networkAvailabilityPort.currentOnlineState() }.getOrDefault(networkOnline)

private sealed interface OgnCenterWaitState {
    data object Disabled : OgnCenterWaitState
    data object Waiting : OgnCenterWaitState
    data class Ready(val center: OgnTrafficRepositoryRuntime.Center) : OgnCenterWaitState
}

private enum class OgnNetworkWaitState {
    Disabled,
    Offline,
    Online
}

internal const val OGN_ERROR_OFFLINE = "Offline"
