package com.example.xcpro.map.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.xcpro.adsb.AdsbAuthMode
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnTrafficSnapshot

@Composable
internal fun OgnDebugPanel(
    visible: Boolean,
    snapshot: OgnTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible || shouldHideOgnDebugPanelWhileConnecting(snapshot)) return
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
            Text(
                text = "Drops (order/motion): ${snapshot.droppedOutOfOrderSourceFrames}/${snapshot.droppedImplausibleMotionFrames}",
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
    if (!visible || shouldHideAdsbDebugPanelWhileConnecting(snapshot)) return
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
            val circlingRuleCount = snapshot.targets.count { it.isCirclingEmergencyRedRule }
            val geometryEmergencyCount = snapshot.targets.count { it.isEmergencyCollisionRisk }
            val emergencyAudioEligibleCount = snapshot.targets.count { it.isEmergencyAudioEligible }
            Text(
                text = "Rule hits (circling/geometry/audio): $circlingRuleCount/$geometryEmergencyCount/$emergencyAudioEligibleCount",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
            AdsbDebugPolicyAndKpiRows(snapshot = snapshot)
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
                text = "Emergency audio gate (master/shadow): ${snapshot.emergencyAudioMasterRolloutEnabled}/${snapshot.emergencyAudioShadowModeEnabled}",
                color = Color(0xFFE5E7EB),
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

@Composable
internal fun AdsbIssueFlashBadge(
    visible: Boolean,
    snapshot: AdsbTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible || !shouldFlashAdsbIssue(snapshot)) return
    val flashTransition = rememberInfiniteTransition(label = "adsbIssueFlash")
    val alpha by flashTransition.animateFloat(
        initialValue = ADSB_ISSUE_FLASH_ALPHA_LOW,
        targetValue = ADSB_ISSUE_FLASH_ALPHA_HIGH,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ADSB_ISSUE_FLASH_PERIOD_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "adsbIssueFlashAlpha"
    )
    Surface(
        modifier = modifier.alpha(alpha).testTag(ADSB_ISSUE_FLASH_BADGE_TAG),
        color = Color(0xFFEAB308),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "ADS-B ISSUE",
                color = Color(0xFF111827),
                style = MaterialTheme.typography.labelMedium
            )
            val reason = snapshot.debugReasonLabel()
                ?: snapshot.lastError?.takeIf { it.isNotBlank() }
            reason?.let {
                Text(
                    text = it,
                    color = Color(0xFF1F2937),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun isOgnReadyForAutoDismiss(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.CONNECTED
internal fun isAdsbReadyForAutoDismiss(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState is AdsbConnectionState.Active && snapshot.authMode != AdsbAuthMode.AuthFailed
internal fun shouldFlashAdsbIssue(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState is AdsbConnectionState.Error || snapshot.connectionState is AdsbConnectionState.BackingOff
internal fun shouldSurfaceOgnDebugPanel(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.ERROR
internal fun shouldSurfaceAdsbDebugPanel(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState is AdsbConnectionState.Error || snapshot.connectionState is AdsbConnectionState.BackingOff
internal fun shouldHideOgnDebugPanelWhileConnecting(snapshot: OgnTrafficSnapshot): Boolean =
    snapshot.connectionState == OgnConnectionState.CONNECTING || snapshot.connectionState == OgnConnectionState.DISCONNECTED
internal fun shouldHideAdsbDebugPanelWhileConnecting(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState is AdsbConnectionState.Disabled

private const val ADSB_ISSUE_FLASH_PERIOD_MS = 260
private const val ADSB_ISSUE_FLASH_ALPHA_LOW = 0.35f
private const val ADSB_ISSUE_FLASH_ALPHA_HIGH = 1.0f
