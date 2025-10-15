package com.example.xcpro.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.xcpro.MapOrientationMode

/**
 * Fixed position aircraft icon overlay that draws on top of the map.
 * The icon stays at a fixed screen position while the map moves beneath it.
 * This eliminates flashing/flickering caused by updating map layers.
 */
@Composable
fun AircraftIconOverlay(
    gpsTrack: Float,
    magneticHeading: Float,
    orientationMode: MapOrientationMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Position icon at center horizontally, 65% down vertically for forward visibility
        val centerX = size.width / 2f
        val centerY = size.height * 0.65f

        // Calculate rotation based on orientation mode
        val rotation = when (orientationMode) {
            MapOrientationMode.TRACK_UP -> 0f  // Always points up
            MapOrientationMode.NORTH_UP -> gpsTrack  // Rotates to show track direction
            MapOrientationMode.HEADING_UP -> {
                // Shows drift angle (difference between track and heading)
                val drift = gpsTrack - magneticHeading
                // Normalize to -180 to 180 range
                when {
                    drift > 180 -> drift - 360
                    drift < -180 -> drift + 360
                    else -> drift
                }
            }
        }

        // Size of the aircraft icon
        val iconSize = 30.dp.toPx()

        // Draw the aircraft icon
        rotate(rotation, Offset(centerX, centerY)) {
            // Create aircraft path (triangle/arrow shape pointing up)
            val path = Path().apply {
                // Nose of aircraft
                moveTo(centerX, centerY - iconSize)
                // Right wing
                lineTo(centerX + iconSize * 0.5f, centerY + iconSize * 0.5f)
                // Tail center
                lineTo(centerX, centerY + iconSize * 0.2f)
                // Left wing
                lineTo(centerX - iconSize * 0.5f, centerY + iconSize * 0.5f)
                // Back to nose
                close()
            }

            // Draw filled aircraft with white outline for visibility
            // Blue fill
            drawPath(
                path = path,
                color = Color(0xFF0066FF),  // Same blue as racing task lines
                style = Fill
            )

            // White outline for contrast against any map background
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 2.dp.toPx())
            )

            // Add small center dot for precise position reference
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }

        // Draw fixed position indicator (optional - helps show it's not moving)
        // Small crosshair at exact position
        val crosshairSize = 8.dp.toPx()
        drawLine(
            color = Color.Black.copy(alpha = 0.3f),
            start = Offset(centerX - crosshairSize, centerY),
            end = Offset(centerX + crosshairSize, centerY),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.3f),
            start = Offset(centerX, centerY - crosshairSize),
            end = Offset(centerX, centerY + crosshairSize),
            strokeWidth = 1.dp.toPx()
        )
    }
}