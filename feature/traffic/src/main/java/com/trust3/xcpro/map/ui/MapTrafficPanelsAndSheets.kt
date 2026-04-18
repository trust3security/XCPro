package com.trust3.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.map.AdsbMarkerDetailsSheet
import com.trust3.xcpro.map.AdsbAuthMode
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.OgnMarkerDetailsSheet
import com.trust3.xcpro.map.OgnThermalDetailsSheet
import com.trust3.xcpro.map.TrafficMapCoordinate
import com.trust3.xcpro.map.isBackingOff
import com.trust3.xcpro.map.isError

@Composable
fun BoxScope.MapTrafficPanelsAndSheetsLayer(
    traffic: MapTrafficUiBinding,
    uiState: MapTrafficContentUiState,
    ownshipCoordinate: TrafficMapCoordinate?,
    unitsPreferences: UnitsPreferences,
    onTrailAircraftSelectionChanged: (String, Boolean) -> Unit,
    trafficActions: MapTrafficUiActions
) {
    when {
        traffic.selectedOgnTarget != null -> {
            OgnMarkerDetailsSheet(
                target = traffic.selectedOgnTarget,
                sciaEnabledForAircraft = uiState.selectedOgnTargetSciaEnabled,
                onSciaEnabledForAircraftChanged = { enabled ->
                    onTrailAircraftSelectionChanged(
                        traffic.selectedOgnTarget.canonicalKey,
                        enabled
                    )
                    if (enabled) {
                        if (!traffic.showOgnSciaEnabled) {
                            trafficActions.onToggleOgnScia()
                        } else if (!traffic.ognOverlayEnabled) {
                            trafficActions.onToggleOgnTraffic()
                        }
                    }
                },
                targetEnabledForAircraft = uiState.selectedOgnTargetTargetEnabled,
                onTargetEnabledForAircraftChanged = { enabled ->
                    trafficActions.onSetOgnTarget(
                        traffic.selectedOgnTarget.canonicalKey,
                        enabled
                    )
                },
                targetToggleEnabled = uiState.selectedOgnTargetTargetToggleEnabled,
                unitsPreferences = unitsPreferences,
                onDismiss = trafficActions.onDismissOgnTargetDetails
            )
        }

        traffic.selectedOgnThermal != null && traffic.selectedOgnThermalDetailsVisible -> {
            OgnThermalDetailsSheet(
                hotspot = traffic.selectedOgnThermal,
                context = traffic.selectedOgnThermalContext,
                distanceMeters = computeOwnshipDistanceToHotspotMeters(
                    ownshipCoordinate = ownshipCoordinate,
                    hotspot = traffic.selectedOgnThermal
                ),
                unitsPreferences = unitsPreferences,
                onDismiss = trafficActions.onDismissOgnThermalDetails
            )
        }

        traffic.selectedAdsbTarget != null -> {
            AdsbMarkerDetailsSheet(
                target = traffic.selectedAdsbTarget,
                unitsPreferences = unitsPreferences,
                onDismiss = trafficActions.onDismissAdsbTargetDetails
            )
        }
    }
}

@Composable
fun AdsbPersistentStatusBadge(
    visible: Boolean,
    snapshot: AdsbTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val (title, body, background) = persistentAdsbStatusPresentation(snapshot)
    Surface(
        modifier = modifier.testTag(TRAFFIC_ADSB_PERSISTENT_STATUS_BADGE_TAG),
        color = background,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.testTag(TRAFFIC_ADSB_PERSISTENT_STATUS_TITLE_TAG),
                color = Color(0xFFF9FAFB),
                style = MaterialTheme.typography.labelMedium
            )
            body?.let {
                Text(
                    text = it,
                    color = Color(0xFFE5E7EB),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun persistentAdsbStatusPresentation(
    snapshot: AdsbTrafficSnapshot
): Triple<String, String?, Color> {
    val reason = snapshot.debugReasonLabel() ?: snapshot.lastError?.takeIf { it.isNotBlank() }
    return when {
        snapshot.connectionState.isError() -> Triple(
            "ADS-B Offline",
            reason,
            Color(0xFF991B1B)
        )

        snapshot.connectionState.isBackingOff() -> Triple(
            "ADS-B Backoff",
            reason ?: "Waiting before next retry",
            Color(0xFF92400E)
        )

        snapshot.authMode == AdsbAuthMode.AuthFailed -> Triple(
            "ADS-B Credential Issue",
            reason ?: "Using anonymous fallback",
            Color(0xFF7C2D12)
        )

        else -> Triple("ADS-B Active", null, Color(0xFF065F46))
    }
}

private const val TRAFFIC_ADSB_PERSISTENT_STATUS_BADGE_TAG = "adsb_persistent_status_badge"
private const val TRAFFIC_ADSB_PERSISTENT_STATUS_TITLE_TAG = "adsb_persistent_status_title"
