package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.AdsbMarkerDetailsSheet
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastOverlayViewModel
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.forecastRegionLabel
import com.example.xcpro.forecast.forecastRegionLikelyContainsCoordinate
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.map.MapCameraManager
import com.example.xcpro.map.MapModalManager
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.ui.MapOverlayStack
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.ui.task.MapTaskScreenUi
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.WindArrowUiState
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnMarkerDetailsSheet
import com.example.xcpro.ogn.OgnSubscriptionPolicy
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnTrailSelectionViewModel
import com.example.xcpro.ogn.OgnThermalDetailsSheet
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.buildOgnSelectionLookup
import com.example.xcpro.ogn.normalizeOgnAircraftKey
import com.example.xcpro.ogn.selectionLookupContainsOgnKey
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.xcpro.seedQnhInputValue
import com.example.xcpro.convertQnhInputToHpa
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.weather.rain.WeatherOverlayViewModel
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.qnh.QnhCalibrationState
import java.util.Locale


/**
 * Map screen runtime support helpers/constants extracted for global file-size compliance.
 */

internal fun computeOwnshipDistanceToHotspotMeters(
    currentLocation: MapLocationUiModel?,
    hotspot: OgnThermalHotspot
): Double? {
    val ownshipLat = currentLocation?.latitude ?: return null
    val ownshipLon = currentLocation.longitude
    val hotspotLat = hotspot.latitude
    val hotspotLon = hotspot.longitude

    if (!ownshipLat.isFinite() || !ownshipLon.isFinite()) return null
    if (!hotspotLat.isFinite() || !hotspotLon.isFinite()) return null
    if (ownshipLat !in -90.0..90.0 || hotspotLat !in -90.0..90.0) return null
    if (ownshipLon !in -180.0..180.0 || hotspotLon !in -180.0..180.0) return null

    return OgnSubscriptionPolicy.haversineMeters(
        lat1 = ownshipLat,
        lon1 = ownshipLon,
        lat2 = hotspotLat,
        lon2 = hotspotLon
    )
}

@Composable
internal fun WindArrowSpeedTapLabel(
    speedKt: Double,
    unitLabel: String,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = MaterialTheme.typography.headlineSmall.fontSize * 0.5f,
        fontWeight = FontWeight.SemiBold
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Text(
            text = "Wind ${formatWindSpeedForTap(speedKt)} $unitLabel",
            color = Color.Black,
            style = textStyle,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

internal fun formatWindSpeedForTap(speedKt: Double): String =
    String.format(Locale.US, "%.0f", speedKt)

internal fun buildOgnTrailAircraftRows(
    targets: List<OgnTrafficTarget>,
    selectionLookup: com.example.xcpro.ogn.OgnSelectionLookup
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
    rows.sortBy { model -> model.normalizedSortKey }
    return rows.map { model -> model.row }
}

private data class OgnTrailAircraftRowSortModel(
    val row: OgnTrailAircraftRowUi,
    val normalizedSortKey: String
)

internal data class WindArrowTapCallout(
    val tapLatLng: LatLng,
    val speedKt: Double
)

internal const val WIND_ARROW_SPEED_TAP_DISPLAY_MS = 4_000L
internal const val TRAFFIC_DEBUG_PANEL_AUTO_DISMISS_MS = 3_000L
internal const val ADSB_ISSUE_FLASH_AUTO_DISMISS_MS = 2_000L
internal const val ADSB_PERSISTENT_STATUS_RECOVERY_DISMISS_MS = 10_000L
internal const val DEFAULT_WIND_SPEED_UNIT_LABEL = "kt"
internal const val SATELLITE_MAP_STYLE_NAME = "Satellite"
internal const val DEFAULT_NON_SATELLITE_MAP_STYLE_NAME = "Topo"
internal const val WIND_TAP_LABEL_EDGE_PADDING_DP = 8
internal const val WIND_TAP_LABEL_ANCHOR_GAP_DP = 10
internal const val WIND_TAP_LABEL_ESTIMATED_WIDTH_DP = 136
internal const val WIND_TAP_LABEL_ESTIMATED_HEIGHT_DP = 42
