package com.example.xcpro.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.graphics.Paint
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import kotlin.math.*

/**
 * Fixed screen overlay for distance circles centered on aircraft icon.
 * Circles stay at fixed screen position while map moves beneath.
 * Sizes adjust based on zoom level to represent accurate distances.
 */
@Composable
fun DistanceCirclesCanvas(
    distancePerPixelMeters: Double,
    unitsPreferences: UnitsPreferences,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    if (!isVisible || distancePerPixelMeters <= 0.0) return

    Canvas(modifier = modifier.fillMaxSize()) {
        // Position circles at same center as aircraft icon
        val centerX = size.width / 2f
        val centerY = size.height * 0.65f  // 65% down from top, matching aircraft
        val maxRadiusPx = max(
            max(centerX, size.width - centerX),
            max(centerY, size.height - centerY)
        )
        if (maxRadiusPx <= 0f) return@Canvas
        val maxDistanceKm = (maxRadiusPx * distancePerPixelMeters) / 1000.0
        if (maxDistanceKm <= 0.0 || !maxDistanceKm.isFinite()) return@Canvas
        val distances = getDistancesForScale(maxDistanceKm)

        // Draw circles from largest to smallest
        distances.reversed().forEach { distanceKm ->
            val radiusPx = calculatePixelRadius(distanceKm, distancePerPixelMeters)

            // Draw ALL circles, even if they extend beyond screen
            // This ensures pilots can see distance to screen edges
            if (radiusPx > 0) {
                // Draw circle with dark grey color matching original
                drawCircle(
                    color = Color(0xFF404040).copy(alpha = 0.5f), // Dark grey with 50% transparency (50% opacity)
                    radius = radiusPx,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() }) // Slightly thicker line
                )

                // Draw distance label at top of circle
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = with(density) { 12.dp.toPx() }
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        // Add white background for readability
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.WHITE)
                    }

                    // Format distance text
                    val text = formatDistanceLabel(
                        distanceMeters = distanceKm * 1000.0,
                        unitsPreferences = unitsPreferences
                    )

                    // Position label at top of circle with slight offset
                    canvas.nativeCanvas.drawText(
                        text,
                        centerX,
                        centerY - radiusPx - with(density) { 4.dp.toPx() },
                        paint
                    )
                }
            }
        }

        // Center point removed - was only for debugging
    }
}

/**
 * Calculate pixel radius for a given distance at current zoom level.
 * Uses Web Mercator projection formula with proper latitude correction.
 */
private fun calculatePixelRadius(
    distanceKm: Double,
    distancePerPixelMeters: Double
): Float {
    val distanceMeters = distanceKm * 1000.0
    val radiusPx = (distanceMeters / distancePerPixelMeters).toFloat()

    // Don't clamp - we want to see partial circles at screen edges
    return radiusPx
}

/**
 * Get appropriate circle distances based on the current visible radius.
 * Shows 10 circles spread out to reach screen edges.
 */
private fun getDistancesForScale(maxDistanceKm: Double): List<Double> {
    // Standard aviation distance intervals
    val availableIntervals = listOf(
        0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 400.0, 800.0
    )

    // Show 10 circles for better gliding reference
    val targetCircleCount = 10
    val idealInterval = maxDistanceKm / targetCircleCount

    // Find the closest standard interval
    val optimalInterval = availableIntervals.minByOrNull { interval ->
        abs(interval - idealInterval)
    } ?: 1.0

    // Generate exactly 10 circles using multiples of the optimal interval
    // This gives us: 1x, 2x, 3x, 4x, 5x, 6x, 7x, 8x, 9x, 10x the interval
    // Spreading them out to the screen edges
    val distances = (1..10).map { multiplier ->
        optimalInterval * multiplier
    }

    return distances
}

private fun formatDistanceLabel(
    distanceMeters: Double,
    unitsPreferences: UnitsPreferences
): String {
    if (unitsPreferences.distance == DistanceUnit.KILOMETERS && distanceMeters < 1000.0) {
        return "${distanceMeters.toInt()} m"
    }
    val converted = unitsPreferences.distance.fromSi(DistanceM(distanceMeters))
    val decimals = when {
        converted >= 10.0 -> 0
        converted >= 1.0 -> 1
        else -> 2
    }
    return UnitsFormatter.distance(
        distance = DistanceM(distanceMeters),
        preferences = unitsPreferences,
        decimals = decimals
    ).text
}
