package com.example.xcpro.map

import com.example.xcpro.tasks.core.Task
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class TaskViewportPlan(
    val target: MapPoint,
    val zoom: Double
)

internal object TaskViewportPlanner {
    private const val DEFAULT_SINGLE_WAYPOINT_ZOOM = 12.0
    private const val DEFAULT_VIEWPORT_WIDTH_PX = 1080
    private const val DEFAULT_VIEWPORT_HEIGHT_PX = 1920
    private const val TASK_FIT_PADDING_PX = 100
    private const val TILE_SIZE_PX = 256.0
    private const val MAX_FIT_ZOOM = 20.0

    fun plan(task: Task, viewport: MapCameraViewportMetrics?): TaskViewportPlan? {
        val waypoints = task.waypoints
        if (waypoints.isEmpty()) {
            return null
        }
        if (waypoints.size == 1) {
            val waypoint = waypoints.single()
            return TaskViewportPlan(
                target = MapPoint(waypoint.lat, waypoint.lon),
                zoom = DEFAULT_SINGLE_WAYPOINT_ZOOM
            )
        }

        val minLat = waypoints.minOf { it.lat }
        val maxLat = waypoints.maxOf { it.lat }
        val minLon = waypoints.minOf { it.lon }
        val maxLon = waypoints.maxOf { it.lon }
        val viewportWidth = max(
            1,
            (viewport?.widthPx ?: DEFAULT_VIEWPORT_WIDTH_PX) - (TASK_FIT_PADDING_PX * 2)
        )
        val viewportHeight = max(
            1,
            (viewport?.heightPx ?: DEFAULT_VIEWPORT_HEIGHT_PX) - (TASK_FIT_PADDING_PX * 2)
        )
        val latFraction = ((latRad(maxLat) - latRad(minLat)) / PI).coerceAtLeast(0.0)
        val lonFraction = ((maxLon - minLon) / 360.0).coerceAtLeast(0.0)
        val latZoom = zoomForFraction(viewportHeight.toDouble(), latFraction)
        val lonZoom = zoomForFraction(viewportWidth.toDouble(), lonFraction)
        val center = MapPoint(
            latitude = (minLat + maxLat) / 2.0,
            longitude = (minLon + maxLon) / 2.0
        )
        return TaskViewportPlan(
            target = center,
            zoom = min(latZoom, lonZoom).coerceAtMost(MAX_FIT_ZOOM)
        )
    }

    private fun zoomForFraction(mapPixels: Double, fraction: Double): Double {
        if (fraction <= 0.0) {
            return MAX_FIT_ZOOM
        }
        return floor(log2(mapPixels / TILE_SIZE_PX / fraction)).coerceAtLeast(0.0)
    }

    private fun latRad(latitude: Double): Double {
        val sinValue = sin(latitude * PI / 180.0).coerceIn(-0.9999, 0.9999)
        return ln((1.0 + sinValue) / (1.0 - sinValue)) / 2.0
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)
}
