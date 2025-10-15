package com.example.xcpro.tasks.aat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.map.AATEditSession
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng
import kotlin.math.*

/**
 * AAT Map Visual Indicators - Phase 2 Visual Feedback
 *
 * Provides visual indicators for AAT areas and target points during editing.
 * Overlays on the map to show edit state, boundaries, and interaction zones.
 *
 * Features:
 * - Highlighted edit area boundaries
 * - Animated target point indicators
 * - Distance rings and guides
 * - Visual feedback for valid/invalid positions
 * - Smooth animations and transitions
 *
 * Visual Design:
 * - AAT green theme with transparency
 * - Pulsing animations for active elements
 * - Clear boundary indicators
 * - Non-intrusive overlay approach
 */

@Composable
fun AATMapVisualIndicators(
    editSession: AATEditSession,
    mapWidth: Float,
    mapHeight: Float,
    coordinateToPixel: (AATLatLng) -> Offset?,
    modifier: Modifier = Modifier
) {
    if (!editSession.isEditingArea || editSession.focusedWaypoint == null) {
        return
    }

    val waypoint = editSession.focusedWaypoint!!
    val targetPoint = editSession.currentTargetPoint!!

    Canvas(
        modifier = modifier.size(mapWidth.dp, mapHeight.dp)
    ) {
        // Draw area boundary highlight
        drawAreaBoundaryHighlight(
            waypoint = waypoint,
            coordinateToPixel = coordinateToPixel
        )

        // Draw target point indicator
        drawTargetPointIndicator(
            targetPoint = targetPoint,
            coordinateToPixel = coordinateToPixel,
            hasChanges = editSession.hasUnsavedChanges
        )

        // Draw connection line from center to target
        drawCenterToTargetLine(
            waypoint = waypoint,
            targetPoint = targetPoint,
            coordinateToPixel = coordinateToPixel
        )

        // Draw distance rings (optional)
        if (editSession.sessionDurationMs > 2000) { // Show after 2 seconds
            drawDistanceRings(
                waypoint = waypoint,
                coordinateToPixel = coordinateToPixel
            )
        }
    }
}

/**
 * Draw highlighted boundary of the focused AAT area
 */
private fun DrawScope.drawAreaBoundaryHighlight(
    waypoint: AATWaypoint,
    coordinateToPixel: (AATLatLng) -> Offset?
) {
    val centerPixel = coordinateToPixel(AATLatLng(waypoint.lat, waypoint.lon)) ?: return
    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0

    // Calculate pixel radius (approximate)
    val edgePoint = AATLatLng(
        waypoint.lat + radiusKm / 111.0, // Rough km to degree conversion
        waypoint.lon
    )
    val edgePixel = coordinateToPixel(edgePoint) ?: return
    val radiusPixels = abs(edgePixel.y - centerPixel.y)

    // Draw highlighted area boundary
    drawCircle(
        color = Color(0xFF388E3C), // AAT green
        radius = radiusPixels,
        center = centerPixel,
        style = Stroke(
            width = 4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(10f, 5f), 0f
            )
        ),
        alpha = 0.8f
    )

    // Draw semi-transparent fill
    drawCircle(
        color = Color(0xFF388E3C),
        radius = radiusPixels,
        center = centerPixel,
        alpha = 0.1f
    )
}

/**
 * Draw animated target point indicator
 */
private fun DrawScope.drawTargetPointIndicator(
    targetPoint: AATLatLng,
    coordinateToPixel: (AATLatLng) -> Offset?,
    hasChanges: Boolean
) {
    val targetPixel = coordinateToPixel(targetPoint) ?: return

    // Main target point circle
    drawCircle(
        color = if (hasChanges) Color(0xFF388E3C) else Color(0xFF4CAF50),
        radius = 12.dp.toPx(),
        center = targetPixel
    )

    // White center dot
    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = targetPixel
    )

    // Outer ring (for visual prominence)
    drawCircle(
        color = Color(0xFF388E3C),
        radius = 16.dp.toPx(),
        center = targetPixel,
        style = Stroke(width = 2.dp.toPx()),
        alpha = 0.6f
    )

    // Crosshairs for precision
    val crosshairLength = 8.dp.toPx()
    drawLine(
        color = Color.White,
        start = Offset(targetPixel.x - crosshairLength, targetPixel.y),
        end = Offset(targetPixel.x + crosshairLength, targetPixel.y),
        strokeWidth = 2.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(targetPixel.x, targetPixel.y - crosshairLength),
        end = Offset(targetPixel.x, targetPixel.y + crosshairLength),
        strokeWidth = 2.dp.toPx()
    )
}

/**
 * Draw line from area center to target point
 */
private fun DrawScope.drawCenterToTargetLine(
    waypoint: AATWaypoint,
    targetPoint: AATLatLng,
    coordinateToPixel: (AATLatLng) -> Offset?
) {
    val centerPixel = coordinateToPixel(AATLatLng(waypoint.lat, waypoint.lon)) ?: return
    val targetPixel = coordinateToPixel(targetPoint) ?: return

    // Only draw if target is not at center
    val distance = sqrt(
        (targetPixel.x - centerPixel.x) * (targetPixel.x - centerPixel.x) +
        (targetPixel.y - centerPixel.y) * (targetPixel.y - centerPixel.y)
    )

    if (distance > 20) { // Minimum distance to draw line
        drawLine(
            color = Color(0xFF388E3C),
            start = centerPixel,
            end = targetPixel,
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(5f, 5f), 0f
            ),
            alpha = 0.6f
        )
    }
}

/**
 * Draw distance rings for reference
 */
private fun DrawScope.drawDistanceRings(
    waypoint: AATWaypoint,
    coordinateToPixel: (AATLatLng) -> Offset?
) {
    val centerPixel = coordinateToPixel(AATLatLng(waypoint.lat, waypoint.lon)) ?: return
    val maxRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

    // Draw rings at 25%, 50%, 75% of max radius
    val ringPercentages = listOf(0.25f, 0.5f, 0.75f)

    ringPercentages.forEach { percentage ->
        val ringRadiusKm = maxRadiusKm * percentage

        // Calculate pixel radius
        val edgePoint = AATLatLng(
            waypoint.lat + ringRadiusKm / 111.0,
            waypoint.lon
        )
        val edgePixel = coordinateToPixel(edgePoint) ?: return@forEach
        val radiusPixels = abs(edgePixel.y - centerPixel.y)

        drawCircle(
            color = Color(0xFF388E3C),
            radius = radiusPixels,
            center = centerPixel,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3f, 6f), 0f
                )
            ),
            alpha = 0.3f
        )
    }
}

/**
 * Animated target point with pulsing effect
 */
@Composable
fun AnimatedTargetPointIndicator(
    isVisible: Boolean,
    hasUnsavedChanges: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulsing animation for active target point
    val pulseAnimation by rememberInfiniteTransition().animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        )
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .scale(if (hasUnsavedChanges) pulseAnimation else 1f)
                .background(
                    color = if (hasUnsavedChanges)
                        Color(0xFF388E3C).copy(alpha = 0.2f)
                    else
                        Color(0xFF4CAF50).copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (hasUnsavedChanges) Color(0xFF388E3C) else Color(0xFF4CAF50),
                    shape = CircleShape
                )
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.Center)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

/**
 * Area boundary highlight component for overlay
 */
@Composable
fun AreaBoundaryHighlight(
    isEditMode: Boolean,
    modifier: Modifier = Modifier
) {
    val highlightAnimation by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        )
    )

    AnimatedVisibility(
        visible = isEditMode,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(highlightAnimation)
                .border(
                    width = 3.dp,
                    color = Color(0xFF388E3C),
                    shape = CircleShape
                )
        )
    }
}

/**
 * Edit mode status indicator
 */
@Composable
fun EditModeStatusIndicator(
    isEditMode: Boolean,
    areaName: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isEditMode,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = spring()
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { -it }
        ) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF388E3C).copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing indicator
                val pulseScale by rememberInfiniteTransition().animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(pulseScale)
                        .background(Color.White, CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "EDITING: $areaName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}