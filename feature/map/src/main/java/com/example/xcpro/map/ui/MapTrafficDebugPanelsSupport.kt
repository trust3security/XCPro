package com.example.xcpro.map.ui

import com.example.xcpro.map.ADSB_ERROR_CIRCUIT_BREAKER_OPEN
import com.example.xcpro.map.ADSB_ERROR_CIRCUIT_BREAKER_PROBE
import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbConnectionState
import com.example.xcpro.map.AdsbNetworkFailureKind
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.OgnConnectionState
import com.example.xcpro.map.backoffRetryAfterSec
import com.example.xcpro.map.isActive
import com.example.xcpro.map.isBackingOff
import com.example.xcpro.map.isDisabled
import com.example.xcpro.map.isError
import java.util.Locale
import com.example.xcpro.map.OgnTrafficSnapshot

internal fun OgnConnectionState.toDebugLabel(): String = when (this) {
    OgnConnectionState.DISCONNECTED -> "DISCONNECTED"
    OgnConnectionState.CONNECTING -> "CONNECTING"
    OgnConnectionState.CONNECTED -> "CONNECTED"
    OgnConnectionState.ERROR -> "ERROR"
}

internal fun AdsbConnectionState.toDebugLabel(): String = when {
    isDisabled() -> "DISABLED"
    isActive() -> "ACTIVE"
    isBackingOff() -> "BACKOFF ${backoffRetryAfterSec() ?: 0}s"
    isError() -> "ERROR"
    else -> "UNKNOWN"
}

internal fun AdsbAuthMode.toDebugLabel(): String = when (this) {
    AdsbAuthMode.Anonymous -> "ANONYMOUS"
    AdsbAuthMode.Authenticated -> "AUTHENTICATED"
    AdsbAuthMode.AuthFailed -> "AUTH FAILED"
}

internal fun AdsbTrafficSnapshot.debugReasonLabel(): String? {
    val networkFailureKind = lastNetworkFailureKind
    if (connectionState.isError() && lastError == ADSB_ERROR_CIRCUIT_BREAKER_OPEN) {
        return "Circuit breaker open"
    }
    if (connectionState.isError() && lastError == ADSB_ERROR_CIRCUIT_BREAKER_PROBE) {
        return "Circuit breaker half-open probe"
    }
    if (authMode == AdsbAuthMode.AuthFailed && lastHttpStatus != 429) {
        return "Credential auth failed; using anonymous fallback"
    }
    if (connectionState.isError() && networkFailureKind != null) {
        return "Network: ${networkFailureKind.toDebugLabel()}"
    }
    if (!connectionState.isBackingOff()) return null
    return when {
        lastHttpStatus == 429 && authMode == AdsbAuthMode.Anonymous ->
            "Anonymous quota exceeded (OpenSky 429)"
        lastHttpStatus == 429 && authMode == AdsbAuthMode.Authenticated ->
            "Account quota exceeded (OpenSky 429)"
        lastHttpStatus == 429 && authMode == AdsbAuthMode.AuthFailed ->
            "Credential auth failed; anonymous quota exceeded"
        lastHttpStatus == 429 ->
            "OpenSky request quota exceeded"
        authMode == AdsbAuthMode.AuthFailed ->
            "Credential auth failed; using anonymous fallback"
        networkFailureKind != null ->
            "Network: ${networkFailureKind.toDebugLabel()}"
        else -> null
    }
}

private fun AdsbNetworkFailureKind.toDebugLabel(): String = when (this) {
    AdsbNetworkFailureKind.DNS -> "DNS lookup failed"
    AdsbNetworkFailureKind.TIMEOUT -> "Socket timeout"
    AdsbNetworkFailureKind.CONNECT -> "Connection refused/unreachable"
    AdsbNetworkFailureKind.NO_ROUTE -> "No route to host"
    AdsbNetworkFailureKind.TLS -> "TLS handshake failure"
    AdsbNetworkFailureKind.MALFORMED_RESPONSE -> "Malformed provider payload"
    AdsbNetworkFailureKind.UNKNOWN -> "Unknown network failure"
}

internal fun formatCoord(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    return String.format(Locale.US, "%.4f", value)
}

internal fun formatAge(ageMs: Long?): String {
    if (ageMs == null || ageMs < 0L) return "--"
    val seconds = ageMs / 1000L
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        else -> "${seconds / 3600L}h"
    }
}

internal fun formatBackoff(backoffMs: Long?): String {
    if (backoffMs == null || backoffMs <= 0L) return "--"
    return "${backoffMs / 1000L}s"
}

internal fun formatMonoMs(monoMs: Long?): String {
    if (monoMs == null || monoMs < 0L) return "--"
    return "$monoMs"
}

internal fun formatRatePerHour(rate: Double): String {
    if (!rate.isFinite() || rate < 0.0) return "--"
    return String.format(Locale.US, "%.2f/h", rate)
}

internal fun formatPercent(rate: Double): String {
    if (!rate.isFinite() || rate < 0.0) return "--"
    return String.format(Locale.US, "%.1f%%", rate * 100.0)
}

internal fun isOgnReadyForAutoDismiss(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.CONNECTED

internal fun isAdsbReadyForAutoDismiss(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState.isActive() && snapshot.authMode != AdsbAuthMode.AuthFailed

internal fun shouldFlashAdsbIssue(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState.isError() || snapshot.connectionState.isBackingOff()

internal fun shouldSurfaceOgnDebugPanel(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.ERROR

internal fun shouldSurfaceAdsbDebugPanel(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState.isError() || snapshot.connectionState.isBackingOff()

internal fun shouldHideOgnDebugPanelWhileConnecting(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.CONNECTING || snapshot.connectionState == OgnConnectionState.DISCONNECTED

internal fun shouldHideAdsbDebugPanelWhileConnecting(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState.isDisabled()

internal const val ADSB_ISSUE_FLASH_PERIOD_MS = 260
internal const val ADSB_ISSUE_FLASH_ALPHA_LOW = 0.35f
internal const val ADSB_ISSUE_FLASH_ALPHA_HIGH = 1.0f
