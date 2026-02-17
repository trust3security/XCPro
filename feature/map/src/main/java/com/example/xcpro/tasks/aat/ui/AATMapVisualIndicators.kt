package com.example.xcpro.tasks.aat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.map.AATEditSession
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun AATMapVisualIndicators(
    editSession: AATEditSession,
    mapWidth: Float,
    mapHeight: Float,
    coordinateToPixel: (AATLatLng) -> Offset?,
    nowMs: Long,
    modifier: Modifier = Modifier
) {
    if (!editSession.isEditingArea || editSession.focusedWaypoint == null) {
        return
    }

    val waypoint = editSession.focusedWaypoint!!
    val targetPoint = editSession.currentTargetPoint!!

    Canvas(modifier = modifier.size(mapWidth.dp, mapHeight.dp)) {
        drawAreaBoundaryHighlight(
            waypoint = waypoint,
            coordinateToPixel = coordinateToPixel
        )

        drawTargetPointIndicator(
            targetPoint = targetPoint,
            coordinateToPixel = coordinateToPixel,
            hasChanges = editSession.hasUnsavedChanges
        )

        drawCenterToTargetLine(
            waypoint = waypoint,
            targetPoint = targetPoint,
            coordinateToPixel = coordinateToPixel
        )

        if (editSession.sessionDurationMs(nowMs) > 2000) {
            drawDistanceRings(
                waypoint = waypoint,
                coordinateToPixel = coordinateToPixel
            )
        }
    }
}

private fun DrawScope.drawAreaBoundaryHighlight(
    waypoint: AATWaypoint,
    coordinateToPixel: (AATLatLng) -> Offset?
) {
    val centerPixel = coordinateToPixel(AATLatLng(waypoint.lat, waypoint.lon)) ?: return
    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0

    val edgePoint = AATLatLng(
        waypoint.lat + radiusKm / 111.0,
        waypoint.lon
    )
    val edgePixel = coordinateToPixel(edgePoint) ?: return
    val radiusPixels = abs(edgePixel.y - centerPixel.y)

    drawCircle(
        color = Color(0xFF388E3C),
        radius = radiusPixels,
        center = centerPixel,
        style = Stroke(
            width = 4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
        ),
        alpha = 0.8f
    )

    drawCircle(
        color = Color(0xFF388E3C),
        radius = radiusPixels,
        center = centerPixel,
        alpha = 0.1f
    )
}

private fun DrawScope.drawTargetPointIndicator(
    targetPoint: AATLatLng,
    coordinateToPixel: (AATLatLng) -> Offset?,
    hasChanges: Boolean
) {
    val targetPixel = coordinateToPixel(targetPoint) ?: return

    drawCircle(
        color = if (hasChanges) Color(0xFF388E3C) else Color(0xFF4CAF50),
        radius = 12.dp.toPx(),
        center = targetPixel
    )

    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = targetPixel
    )

    drawCircle(
        color = Color(0xFF388E3C),
        radius = 16.dp.toPx(),
        center = targetPixel,
        style = Stroke(width = 2.dp.toPx()),
        alpha = 0.6f
    )

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

private fun DrawScope.drawCenterToTargetLine(
    waypoint: AATWaypoint,
    targetPoint: AATLatLng,
    coordinateToPixel: (AATLatLng) -> Offset?
) {
    val centerPixel = coordinateToPixel(AATLatLng(waypoint.lat, waypoint.lon)) ?: return
    val targetPixel = coordinateToPixel(targetPoint) ?: return

    val distance = sqrt(
        (targetPixel.x - centerPixel.x) * (targetPixel.x - centerPixel.x) +
            (targetPixel.y - centerPixel.y) * (targetPixel.y - centerPixel.y)
    )

    if (distance > 20) {
        drawLine(
            color = Color(0xFF388E3C),
            start = centerPixel,
            end = targetPixel,
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
            alpha = 0.6f
        )
    }
}

private fun DrawScope.drawDistanceRings(
    waypoint: AATWaypoint,
    coordinateToPixel: (AATLatLng) -> Offset?
) {
    val centerPixel = coordinateToPixel(AATLatLng(waypoint.lat, waypoint.lon)) ?: return
    val maxRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

    val ringPercentages = listOf(0.25f, 0.5f, 0.75f)

    ringPercentages.forEach { percentage ->
        val ringRadiusKm = maxRadiusKm * percentage
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
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f)
            ),
            alpha = 0.3f
        )
    }
}
