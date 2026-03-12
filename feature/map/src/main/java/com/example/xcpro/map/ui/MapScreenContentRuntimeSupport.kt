package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */
import androidx.compose.ui.unit.IntSize
import org.maplibre.android.geometry.LatLng


/**
 * Map screen runtime support helpers/constants extracted for global file-size compliance.
 */

internal data class WindArrowTapCallout(
    val tapLatLng: LatLng,
    val speedKt: Double
)

internal const val WIND_ARROW_SPEED_TAP_DISPLAY_MS = 4_000L
internal const val DEFAULT_WIND_SPEED_UNIT_LABEL = "kt"
internal const val SATELLITE_MAP_STYLE_NAME = "Satellite"
internal const val DEFAULT_NON_SATELLITE_MAP_STYLE_NAME = "Topo"
internal const val WIND_TAP_LABEL_EDGE_PADDING_DP = 8
internal const val WIND_TAP_LABEL_ANCHOR_GAP_DP = 10
internal const val WIND_TAP_LABEL_ESTIMATED_WIDTH_DP = 136
internal const val WIND_TAP_LABEL_ESTIMATED_HEIGHT_DP = 42
