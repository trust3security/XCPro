package com.example.xcpro.map.ui

import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbNetworkFailureKind
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_OPEN
import com.example.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_PROBE
import com.example.xcpro.ogn.OgnConnectionState
import java.util.Locale

internal fun OgnConnectionState.toDebugLabel(): String = when (this) {
    OgnConnectionState.DISCONNECTED -> "DISCONNECTED"
    OgnConnectionState.CONNECTING -> "CONNECTING"
    OgnConnectionState.CONNECTED -> "CONNECTED"
    OgnConnectionState.ERROR -> "ERROR"
}

internal fun AdsbConnectionState.toDebugLabel(): String = when (this) {
    AdsbConnectionState.Disabled -> "DISABLED"
    AdsbConnectionState.Active -> "ACTIVE"
    is AdsbConnectionState.BackingOff -> "BACKOFF ${retryAfterSec}s"
    is AdsbConnectionState.Error -> "ERROR"
}

internal fun AdsbAuthMode.toDebugLabel(): String = when (this) {
    AdsbAuthMode.Anonymous -> "ANONYMOUS"
    AdsbAuthMode.Authenticated -> "AUTHENTICATED"
    AdsbAuthMode.AuthFailed -> "AUTH FAILED"
}

internal fun AdsbTrafficSnapshot.debugReasonLabel(): String? {
    if (connectionState is AdsbConnectionState.Error && lastError == ADSB_ERROR_CIRCUIT_BREAKER_OPEN) {
        return "Circuit breaker open"
    }
    if (connectionState is AdsbConnectionState.Error && lastError == ADSB_ERROR_CIRCUIT_BREAKER_PROBE) {
        return "Circuit breaker half-open probe"
    }
    if (authMode == AdsbAuthMode.AuthFailed && lastHttpStatus != 429) {
        return "Credential auth failed; using anonymous fallback"
    }
    if (connectionState is AdsbConnectionState.Error && lastNetworkFailureKind != null) {
        return "Network: ${lastNetworkFailureKind.toDebugLabel()}"
    }
    if (connectionState !is AdsbConnectionState.BackingOff) return null
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
        lastNetworkFailureKind != null ->
            "Network: ${lastNetworkFailureKind.toDebugLabel()}"
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
