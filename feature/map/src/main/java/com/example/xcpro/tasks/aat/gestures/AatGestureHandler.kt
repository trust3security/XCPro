package com.example.xcpro.tasks.aat.gestures

import androidx.compose.ui.geometry.Offset
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.gestures.TaskGestureConsume
import com.example.xcpro.gestures.TaskGestureContext
import com.example.xcpro.gestures.TaskGestureHandler
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverter
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverterFactory
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import kotlin.math.sqrt
import org.maplibre.android.maps.MapLibreMap

private const val DEFAULT_RADIUS_KM = 10.0

fun findAatWaypointHitForMapPoint(
    mapLat: Double,
    mapLon: Double,
    waypoints: List<TaskWaypoint>
): Int? {
    var closestIndex: Int? = null
    var closestDistance = Double.MAX_VALUE

    waypoints.forEachIndexed { index, waypoint ->
        if (waypoint.role != WaypointRole.TURNPOINT) return@forEachIndexed
        val radiusKm = waypoint.customRadius ?: DEFAULT_RADIUS_KM
        val distanceKm = AATMathUtils.calculateDistanceKm(
            mapLat,
            mapLon,
            waypoint.lat,
            waypoint.lon
        )
        if (distanceKm <= radiusKm && distanceKm < closestDistance) {
            closestDistance = distanceKm
            closestIndex = index
        }
    }

    return closestIndex
}

class AatGestureHandler(
    private val waypointsProvider: () -> List<TaskWaypoint>,
    private val callbacks: TaskGestureCallbacks
) : TaskGestureHandler {

    private var editModeIndex: Int = -1
    private var isDragging = false
    private var lastTapTime = 0L
    private var lastTapPosition = Offset.Zero
    private var lastTapWaypointIndex: Int? = null
    private var pendingTapWaypointIndex: Int? = null
    private var gestureStartPosition: Offset = Offset.Zero
    private var converter: AATMapCoordinateConverter? = null
    private var mapRef: MapLibreMap? = null

    override fun onExternalEditModeChanged(isEditMode: Boolean) {
        if (!isEditMode) {
            editModeIndex = -1
            isDragging = false
        }
    }

    override fun onGestureStart(context: TaskGestureContext): TaskGestureConsume {
        gestureStartPosition = context.gestureStartPosition
        updateConverter(context.mapLibreMap)
        pendingTapWaypointIndex = findWaypointHit(gestureStartPosition)
        return if (editModeIndex != -1) TaskGestureConsume.Consume else TaskGestureConsume.PassThrough
    }

    override fun onGestureMove(context: TaskGestureContext): TaskGestureConsume {
        if (editModeIndex == -1) return TaskGestureConsume.PassThrough

        val pointer = context.activePointers.firstOrNull() ?: return TaskGestureConsume.Consume
        val currentPos = pointer.position
        val dragDistance = distance(currentPos, gestureStartPosition)

        if (!isDragging && dragDistance > DRAG_THRESHOLD_PX) {
            isDragging = true
        }

        if (isDragging) {
            val mapPoint = converter?.screenToMap(currentPos.x, currentPos.y)
            if (mapPoint != null) {
                callbacks.onDragTarget(editModeIndex, mapPoint.latitude, mapPoint.longitude)
            }
        }

        return TaskGestureConsume.Consume
    }

    override fun onGestureEnd(context: TaskGestureContext): TaskGestureConsume {
        val gestureDuration = context.currentTimeMs - context.gestureStartTimeMs
        val isQuickTap = gestureDuration < QUICK_TAP_MAX_MS

        if (editModeIndex != -1) {
            if (!isDragging && !isQuickTap && gestureDuration >= LONG_PRESS_MIN_MS) {
                exitEditMode()
            }
            isDragging = false
            return TaskGestureConsume.Consume
        }

        if (isQuickTap && !isDragging) {
            handleDoubleTap(context.currentTimeMs)
        }

        isDragging = false
        return TaskGestureConsume.PassThrough
    }

    private fun handleDoubleTap(currentTimeMs: Long) {
        val timeSinceLastTap = currentTimeMs - lastTapTime
        val distanceFromLastTap = distance(gestureStartPosition, lastTapPosition)

        if (timeSinceLastTap < DOUBLE_TAP_MAX_MS && distanceFromLastTap < DOUBLE_TAP_MAX_DISTANCE_PX) {
            if (editModeIndex == -1 && pendingTapWaypointIndex != null && pendingTapWaypointIndex == lastTapWaypointIndex) {
                enterEditMode(pendingTapWaypointIndex!!)
            } else if (editModeIndex != -1) {
                exitEditMode()
            }
            lastTapTime = 0L
            lastTapWaypointIndex = null
        } else {
            lastTapTime = currentTimeMs
            lastTapPosition = gestureStartPosition
            lastTapWaypointIndex = pendingTapWaypointIndex
        }
    }

    private fun enterEditMode(index: Int) {
        val waypoint = waypointsProvider().getOrNull(index) ?: return
        val radiusKm = waypoint.customRadius ?: DEFAULT_RADIUS_KM
        callbacks.onEnterEditMode(index, waypoint.lat, waypoint.lon, radiusKm)
        editModeIndex = index
    }

    private fun exitEditMode() {
        callbacks.onExitEditMode()
        editModeIndex = -1
        isDragging = false
    }

    private fun updateConverter(map: MapLibreMap?) {
        if (map == null) return
        if (mapRef !== map) {
            mapRef = map
            converter = AATMapCoordinateConverterFactory.create(map)
        }
    }

    private fun findWaypointHit(screenPosition: Offset): Int? {
        val mapPoint = converter?.screenToMap(screenPosition.x, screenPosition.y) ?: return null
        return findAatWaypointHitForMapPoint(
            mapLat = mapPoint.latitude,
            mapLon = mapPoint.longitude,
            waypoints = waypointsProvider()
        )
    }

    private fun distance(a: Offset, b: Offset): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        private const val QUICK_TAP_MAX_MS = 200L
        private const val DOUBLE_TAP_MAX_MS = 500L
        private const val DOUBLE_TAP_MAX_DISTANCE_PX = 50f
        private const val LONG_PRESS_MIN_MS = 350L
        private const val DRAG_THRESHOLD_PX = 10f
    }
}
