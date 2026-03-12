package com.example.xcpro.map.ui
/**
 * Map screen body content used inside MapScreenScaffold.
 * Invariants: UI renders state only and routes mutations through the ViewModel.
 */


import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import org.maplibre.android.geometry.LatLng
import java.util.Locale


/**
 * Map screen runtime support helpers/constants extracted for global file-size compliance.
 */

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
