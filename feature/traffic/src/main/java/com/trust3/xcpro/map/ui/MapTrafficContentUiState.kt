package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.trust3.xcpro.map.AdsbAuthMode
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.OgnSelectionLookup
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnTrafficSnapshot
import com.trust3.xcpro.map.OgnTrafficTarget
import com.trust3.xcpro.map.buildOgnSelectionLookup
import com.trust3.xcpro.map.haversineMeters
import com.trust3.xcpro.map.isBackingOff
import com.trust3.xcpro.map.isError
import com.trust3.xcpro.map.normalizeOgnAircraftKey
import com.trust3.xcpro.map.selectionLookupContainsOgnKey
import com.trust3.xcpro.map.TrafficMapCoordinate
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class OgnTrailAircraftRowUi(
    val key: String,
    val label: String,
    val trailsEnabled: Boolean
)

data class TrafficDebugPanelVisibility(
    val showOgnDebugPanel: Boolean,
    val showAdsbDebugPanel: Boolean,
    val showAdsbIssueFlash: Boolean,
    val showAdsbPersistentStatus: Boolean
)

data class MapTrafficContentUiState(
    val ognTrailAircraftRows: List<OgnTrailAircraftRowUi>,
    val hasTrafficDetailsOpen: Boolean,
    val selectedOgnTargetSciaEnabled: Boolean,
    val selectedOgnTargetTargetEnabled: Boolean,
    val selectedOgnTargetTargetToggleEnabled: Boolean,
    val connectionIndicators: TrafficConnectionIndicatorsUiState,
    val panelVisibility: TrafficDebugPanelVisibility
)

@Composable
fun rememberMapTrafficContentUiState(
    traffic: MapTrafficUiBinding,
    selectedTrailAircraftKeys: Set<String>,
    debugPanelsEnabled: Boolean
): MapTrafficContentUiState {
    val trailSelectionLookup = remember(selectedTrailAircraftKeys) {
        buildOgnSelectionLookup(selectedTrailAircraftKeys)
    }
    val ognTrailAircraftRows = remember(traffic.ognSnapshot.targets, trailSelectionLookup) {
        buildOgnTrailAircraftRows(traffic.ognSnapshot.targets, trailSelectionLookup)
    }
    val hasTrafficDetailsOpen = traffic.selectedOgnTarget != null ||
        (traffic.selectedOgnThermal != null && traffic.selectedOgnThermalDetailsVisible) ||
        traffic.selectedAdsbTarget != null
    val selectedOgnTargetSciaEnabled = remember(traffic.selectedOgnTarget, trailSelectionLookup) {
        traffic.selectedOgnTarget?.let { target ->
            selectionLookupContainsOgnKey(
                lookup = trailSelectionLookup,
                candidateKey = target.canonicalKey
            )
        } ?: false
    }
    val targetSelectionLookup = remember(traffic.ognTargetAircraftKey) {
        val selectedKeys = traffic.ognTargetAircraftKey?.let(::setOf) ?: emptySet()
        buildOgnSelectionLookup(selectedKeys)
    }
    val selectedOgnTargetTargetEnabled = remember(
        traffic.selectedOgnTarget,
        traffic.ognTargetEnabled,
        targetSelectionLookup
    ) {
        if (!traffic.ognTargetEnabled) return@remember false
        val target = traffic.selectedOgnTarget ?: return@remember false
        selectionLookupContainsOgnKey(
            lookup = targetSelectionLookup,
            candidateKey = target.canonicalKey
        )
    }
    val selectedOgnTargetTargetToggleEnabled = remember(traffic.selectedOgnTarget) {
        isTargetableOgnAircraftTypeCode(
            traffic.selectedOgnTarget?.identity?.aircraftTypeCode
        )
    }
    val connectionIndicators = remember(
        traffic.ognOverlayEnabled,
        traffic.ognSnapshot,
        traffic.adsbOverlayEnabled,
        traffic.adsbSnapshot
    ) {
        MapTrafficConnectionIndicatorModelBuilder.build(
            ognOverlayEnabled = traffic.ognOverlayEnabled,
            ognSnapshot = traffic.ognSnapshot,
            adsbOverlayEnabled = traffic.adsbOverlayEnabled,
            adsbSnapshot = traffic.adsbSnapshot
        )
    }
    val panelVisibility = rememberTrafficDebugPanelVisibility(
        debugPanelsEnabled = debugPanelsEnabled,
        adsbOverlayEnabled = traffic.adsbOverlayEnabled,
        adsbSnapshot = traffic.adsbSnapshot,
        ognOverlayEnabled = traffic.ognOverlayEnabled,
        ognSnapshot = traffic.ognSnapshot
    )
    return MapTrafficContentUiState(
        ognTrailAircraftRows = ognTrailAircraftRows,
        hasTrafficDetailsOpen = hasTrafficDetailsOpen,
        selectedOgnTargetSciaEnabled = selectedOgnTargetSciaEnabled,
        selectedOgnTargetTargetEnabled = selectedOgnTargetTargetEnabled,
        selectedOgnTargetTargetToggleEnabled = selectedOgnTargetTargetToggleEnabled,
        connectionIndicators = connectionIndicators,
        panelVisibility = panelVisibility
    )
}

@Composable
fun rememberTrafficDebugPanelVisibility(
    debugPanelsEnabled: Boolean,
    adsbOverlayEnabled: Boolean,
    adsbSnapshot: AdsbTrafficSnapshot,
    ognOverlayEnabled: Boolean,
    ognSnapshot: OgnTrafficSnapshot
): TrafficDebugPanelVisibility {
    val showOgnDebugPanel = rememberTimedVisibility(
        enabled = debugPanelsEnabled &&
            ognOverlayEnabled &&
            shouldSurfaceOgnDebugPanel(ognSnapshot),
        readyForAutoDismiss = isOgnReadyForAutoDismiss(ognSnapshot),
        autoDismissDelayMs = TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS
    )
    val showAdsbDebugPanel = rememberTimedVisibility(
        enabled = debugPanelsEnabled &&
            adsbOverlayEnabled &&
            shouldSurfaceAdsbDebugPanel(adsbSnapshot),
        readyForAutoDismiss = isAdsbReadyForAutoDismiss(adsbSnapshot) ||
            shouldFlashAdsbIssue(adsbSnapshot),
        autoDismissDelayMs = ADSB_ISSUE_FLASH_AUTO_DISMISS_MS
    )
    val showAdsbIssueFlash = rememberTimedVisibility(
        enabled = adsbOverlayEnabled && shouldFlashAdsbIssue(adsbSnapshot),
        readyForAutoDismiss = true,
        autoDismissDelayMs = ADSB_ISSUE_FLASH_AUTO_DISMISS_MS
    )
    val showAdsbPersistentStatus = rememberPersistentIssueVisibility(
        enabled = adsbOverlayEnabled,
        issueActive = shouldSurfacePersistentAdsbStatus(adsbSnapshot),
        healthy = isAdsbReadyForAutoDismiss(adsbSnapshot),
        recoveryDwellMs = ADSB_PERSISTENT_STATUS_RECOVERY_DISMISS_MS
    )
    return TrafficDebugPanelVisibility(
        showOgnDebugPanel = showOgnDebugPanel,
        showAdsbDebugPanel = showAdsbDebugPanel,
        showAdsbIssueFlash = showAdsbIssueFlash,
        showAdsbPersistentStatus = showAdsbPersistentStatus
    )
}

@Composable
private fun rememberTimedVisibility(
    enabled: Boolean,
    readyForAutoDismiss: Boolean,
    autoDismissDelayMs: Long
): Boolean {
    var visible by remember(enabled) { mutableStateOf(enabled) }
    androidx.compose.runtime.LaunchedEffect(enabled, readyForAutoDismiss, autoDismissDelayMs) {
        if (!enabled) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (!readyForAutoDismiss) return@LaunchedEffect
        delay(autoDismissDelayMs)
        if (isActive) {
            visible = false
        }
    }
    return visible
}

data class PersistentIssueVisibilityState(
    val visible: Boolean,
    val healthySinceMonoMs: Long?
)

fun reducePersistentIssueVisibility(
    previous: PersistentIssueVisibilityState,
    enabled: Boolean,
    issueActive: Boolean,
    healthy: Boolean,
    recoveryDwellMs: Long,
    nowMonoMs: Long
): PersistentIssueVisibilityState {
    if (!enabled) {
        return PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)
    }
    if (issueActive) {
        return PersistentIssueVisibilityState(visible = true, healthySinceMonoMs = null)
    }
    if (!previous.visible) {
        return previous.copy(healthySinceMonoMs = null)
    }
    if (!healthy) {
        return previous.copy(healthySinceMonoMs = null)
    }
    if (recoveryDwellMs <= 0L) {
        return PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)
    }
    val healthySinceMonoMs = previous.healthySinceMonoMs ?: nowMonoMs
    val elapsedMs = nowMonoMs - healthySinceMonoMs
    return if (elapsedMs >= recoveryDwellMs) {
        PersistentIssueVisibilityState(visible = false, healthySinceMonoMs = null)
    } else {
        PersistentIssueVisibilityState(visible = true, healthySinceMonoMs = healthySinceMonoMs)
    }
}

@Composable
private fun rememberPersistentIssueVisibility(
    enabled: Boolean,
    issueActive: Boolean,
    healthy: Boolean,
    recoveryDwellMs: Long
): Boolean {
    var state by remember(enabled) {
        mutableStateOf(
            PersistentIssueVisibilityState(
                visible = enabled && issueActive,
                healthySinceMonoMs = null
            )
        )
    }
    androidx.compose.runtime.LaunchedEffect(enabled, issueActive, healthy, recoveryDwellMs) {
        fun updateState(nowMonoMs: Long) {
            state = reducePersistentIssueVisibility(
                previous = state,
                enabled = enabled,
                issueActive = issueActive,
                healthy = healthy,
                recoveryDwellMs = recoveryDwellMs,
                nowMonoMs = nowMonoMs
            )
        }

        updateState(androidx.compose.runtime.withFrameNanos { it / 1_000_000L })
        if (!state.visible || issueActive || !healthy) {
            return@LaunchedEffect
        }

        while (isActive && state.visible && !issueActive && healthy) {
            updateState(androidx.compose.runtime.withFrameNanos { it / 1_000_000L })
        }
    }
    return state.visible
}

fun shouldSurfacePersistentAdsbStatus(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState.isError() ||
        snapshot.connectionState.isBackingOff() ||
        snapshot.authMode == AdsbAuthMode.AuthFailed

fun computeOwnshipDistanceToHotspotMeters(
    ownshipCoordinate: TrafficMapCoordinate?,
    hotspot: OgnThermalHotspot
): Double? {
    val ownshipLat = ownshipCoordinate?.latitude ?: return null
    val ownshipLon = ownshipCoordinate.longitude
    val hotspotLat = hotspot.latitude
    val hotspotLon = hotspot.longitude
    if (!ownshipLat.isFinite() || !ownshipLon.isFinite()) return null
    if (!hotspotLat.isFinite() || !hotspotLon.isFinite()) return null
    if (ownshipLat !in -90.0..90.0 || hotspotLat !in -90.0..90.0) return null
    if (ownshipLon !in -180.0..180.0 || hotspotLon !in -180.0..180.0) return null
    return haversineMeters(
        lat1 = ownshipLat,
        lon1 = ownshipLon,
        lat2 = hotspotLat,
        lon2 = hotspotLon
    )
}

fun buildOgnTrailAircraftRows(
    targets: List<OgnTrafficTarget>,
    selectionLookup: OgnSelectionLookup
): List<OgnTrailAircraftRowUi> {
    val seenKeys = HashSet<String>(targets.size)
    val rows = ArrayList<OgnTrailAircraftRowSortModel>(targets.size)
    for (target in targets) {
        val key = normalizeOgnAircraftKey(target.canonicalKey)
        if (!seenKeys.add(key)) continue
        val label = target.identity?.competitionNumber
            ?.takeIf { it.isNotBlank() }
            ?: target.identity?.registration?.takeIf { it.isNotBlank() }
            ?: target.callsign.takeIf { it.isNotBlank() }
            ?: target.displayLabel.takeIf { it.isNotBlank() }
            ?: key
        rows += OgnTrailAircraftRowSortModel(
            row = OgnTrailAircraftRowUi(
                key = key,
                label = label,
                trailsEnabled = selectionLookupContainsOgnKey(
                    lookup = selectionLookup,
                    candidateKey = key
                )
            ),
            normalizedSortKey = label.lowercase(Locale.US)
        )
    }
    rows.sortBy { it.normalizedSortKey }
    return rows.map { it.row }
}

private data class OgnTrailAircraftRowSortModel(
    val row: OgnTrailAircraftRowUi,
    val normalizedSortKey: String
)

fun isTargetableOgnAircraftTypeCode(aircraftTypeCode: Int?): Boolean =
    aircraftTypeCode == OGN_GLIDER_AIRCRAFT_TYPE_CODE ||
        aircraftTypeCode == OGN_PARAGLIDER_AIRCRAFT_TYPE_CODE ||
        aircraftTypeCode == OGN_HANG_GLIDER_AIRCRAFT_TYPE_CODE

private const val OGN_GLIDER_AIRCRAFT_TYPE_CODE = 1
private const val OGN_PARAGLIDER_AIRCRAFT_TYPE_CODE = 4
private const val OGN_HANG_GLIDER_AIRCRAFT_TYPE_CODE = 5
private const val TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS = 3_000L
private const val ADSB_ISSUE_FLASH_AUTO_DISMISS_MS = 2_000L
private const val ADSB_PERSISTENT_STATUS_RECOVERY_DISMISS_MS = 10_000L
