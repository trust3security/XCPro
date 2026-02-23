package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbNetworkFailureKind
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_OPEN
import com.example.xcpro.adsb.ADSB_ERROR_CIRCUIT_BREAKER_PROBE
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnTrafficSnapshot
import java.util.Locale

@Composable
internal fun OgnDebugPanel(
    visible: Boolean,
    snapshot: OgnTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Surface(
        modifier = modifier,
        color = Color(0xCC111827),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "OGN ${snapshot.connectionState.toDebugLabel()}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Targets: ${snapshot.targets.size}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Suppressed: ${snapshot.suppressedTargetIds.size}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Radius: ${snapshot.receiveRadiusKm} km",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "DDB age: ${formatAge(snapshot.ddbCacheAgeMs)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Backoff: ${formatBackoff(snapshot.reconnectBackoffMs)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            snapshot.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun AdsbDebugPanel(
    visible: Boolean,
    snapshot: AdsbTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Surface(
        modifier = modifier,
        color = Color(0xCC1F2937),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "ADS-B ${snapshot.connectionState.toDebugLabel()}",
                color = Color(0xFFF9FAFB),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Active displayed: ${snapshot.displayedCount}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Counts (fetched/horizontal/vertical/displayed): ${snapshot.fetchedCount}/${snapshot.withinRadiusCount}/${snapshot.withinVerticalCount}/${snapshot.displayedCount}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Filtered (vertical/capped): ${snapshot.filteredByVerticalCount}/${snapshot.cappedCount}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Center: ${formatCoord(snapshot.centerLat)}, ${formatCoord(snapshot.centerLon)}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Ownship ref: ${if (snapshot.usesOwnshipReference) "YES" else "NO"}",
                color = if (snapshot.usesOwnshipReference) Color(0xFF86EFAC) else Color(0xFFFDE68A),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Radius: ${snapshot.receiveRadiusKm} km",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "HTTP: ${snapshot.lastHttpStatus ?: "--"} | Credits: ${snapshot.remainingCredits ?: "--"}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Auth: ${snapshot.authMode.toDebugLabel()}",
                color = if (snapshot.authMode == AdsbAuthMode.AuthFailed) Color(0xFFFCA5A5) else Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Failures: ${snapshot.consecutiveFailureCount} | Next retry mono: ${formatMonoMs(snapshot.nextRetryMonoMs)}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Last failure mono: ${formatMonoMs(snapshot.lastFailureMonoMs)}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            snapshot.debugReasonLabel()?.let { reason ->
                Text(
                    text = "Reason: $reason",
                    color = Color(0xFFFDE68A),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            snapshot.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun isOgnReadyForAutoDismiss(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.CONNECTED

internal fun isAdsbReadyForAutoDismiss(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState is AdsbConnectionState.Active &&
        snapshot.authMode != AdsbAuthMode.AuthFailed

private fun OgnConnectionState.toDebugLabel(): String = when (this) {
    OgnConnectionState.DISCONNECTED -> "DISCONNECTED"
    OgnConnectionState.CONNECTING -> "CONNECTING"
    OgnConnectionState.CONNECTED -> "CONNECTED"
    OgnConnectionState.ERROR -> "ERROR"
}

private fun AdsbConnectionState.toDebugLabel(): String = when (this) {
    AdsbConnectionState.Disabled -> "DISABLED"
    AdsbConnectionState.Active -> "ACTIVE"
    is AdsbConnectionState.BackingOff -> "BACKOFF ${retryAfterSec}s"
    is AdsbConnectionState.Error -> "ERROR"
}

private fun AdsbAuthMode.toDebugLabel(): String = when (this) {
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

private fun formatCoord(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    return String.format(Locale.US, "%.4f", value)
}

private fun formatAge(ageMs: Long?): String {
    if (ageMs == null || ageMs < 0L) return "--"
    val seconds = ageMs / 1000L
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        else -> "${seconds / 3600L}h"
    }
}

private fun formatBackoff(backoffMs: Long?): String {
    if (backoffMs == null || backoffMs <= 0L) return "--"
    return "${backoffMs / 1000L}s"
}

private fun formatMonoMs(monoMs: Long?): String {
    if (monoMs == null || monoMs < 0L) return "--"
    return "$monoMs"
}
