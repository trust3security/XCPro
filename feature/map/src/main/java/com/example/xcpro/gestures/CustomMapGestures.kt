package com.example.xcpro.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.MapGestureRegion
import com.example.xcpro.map.MapZoomConstraints
import com.example.xcpro.map.model.MapLocationUiModel
import kotlin.math.abs
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

private const val MODE_SWITCH_THRESHOLD_PX = 250f
private const val SINGLE_FINGER_ZOOM_FACTOR = 0.003
private const val SINGLE_FINGER_DRAG_MIN_PX = 2f
private const val TAP_MAX_DURATION_MS = 300L
private const val TAP_MAX_MOVE_PX = 14f
private const val LONG_PRESS_MIN_DURATION_MS = 550L

@Composable
fun CustomMapGestureHandler(
    mapLibreMap: MapLibreMap?,
    currentMode: FlightMode,
    onModeChange: (FlightMode) -> Unit,
    showReturnButton: Boolean,
    onShowReturnButton: (Boolean) -> Unit,
    currentLocation: MapLocationUiModel?,
    onSaveLocation: (MapLocationUiModel?, Double, Double) -> Unit,
    bottomSheetHeight: Float = 0f,
    visibleModes: List<FlightMode> = FlightMode.values().toList(),
    taskGestureHandler: TaskGestureHandler? = null,
    gestureRegions: List<MapGestureRegion> = emptyList(),
    onMapTap: (org.maplibre.android.geometry.LatLng) -> Unit = {},
    onMapLongPress: (org.maplibre.android.geometry.LatLng) -> Unit = {},
    mapViewPixelRatio: Float = 0f,
    modifier: Modifier = Modifier
) {
    val totalDragX = remember { mutableStateOf(0f) }
    val totalDragY = remember { mutableStateOf(0f) }
    val hasSwitchedMode = remember { mutableStateOf(false) }
    val gestureStartPosition = remember { mutableStateOf(Offset.Zero) }
    val initialFingerCount = remember { mutableStateOf(1) }
    val pixelRatio = if (mapViewPixelRatio > 0f) mapViewPixelRatio else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(currentMode, gestureRegions, taskGestureHandler) {
                awaitEachGesture {
                    // Pre-scan for down inside overlay regions before map consumes it
                    while (true) {
                        val preDownEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val preDownChange = preDownEvent.changes.firstOrNull { it.changedToDown() }
                        if (preDownChange != null) {
                            gestureStartPosition.value = preDownChange.position
                            val overlayRegion = gestureRegions.firstOrNull { region ->
                                region.bounds.contains(gestureStartPosition.value)
                            }
                            if (overlayRegion != null) {
                                if (overlayRegion.consumeGestures) {
                                    return@awaitEachGesture
                                } else {
                                    break
                                }
                            } else {
                                break
                            }
                        }
                    }

                    val firstDown = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Final
                    )
                    if (firstDown.isConsumed) {
                        return@awaitEachGesture
                    }
                    gestureStartPosition.value = firstDown.position

                    val screenHeight = size.height.toFloat()
                    val isOverFlightDataCards = gestureStartPosition.value.y > (screenHeight - bottomSheetHeight)
                    if (isOverFlightDataCards) {
                        return@awaitEachGesture
                    }

                    totalDragX.value = 0f
                    totalDragY.value = 0f
                    hasSwitchedMode.value = false

                    val gestureStartTimeMs = System.currentTimeMillis()
                    var fingerCount = 1
                    var isFirstFrame = true
                    var handledGesture = false
                    var maxDistanceFromStartPx = 0f

                    val startContext = TaskGestureContext(
                        mapLibreMap = mapLibreMap,
                        gestureStartPosition = gestureStartPosition.value,
                        activePointers = listOf(firstDown),
                        gestureStartTimeMs = gestureStartTimeMs,
                        currentTimeMs = gestureStartTimeMs
                    )
                    if (taskGestureHandler?.onGestureStart(startContext) == TaskGestureConsume.Consume) {
                        firstDown.consume()
                    }

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                        val activePointers = event.changes.filter { !it.changedToUp() }
                        fingerCount = activePointers.size

                        if (isFirstFrame) {
                            initialFingerCount.value = fingerCount
                            isFirstFrame = false
                        }

                        val gestureContext = TaskGestureContext(
                            mapLibreMap = mapLibreMap,
                            gestureStartPosition = gestureStartPosition.value,
                            activePointers = activePointers,
                            gestureStartTimeMs = gestureStartTimeMs,
                            currentTimeMs = System.currentTimeMillis()
                        )

                        if (taskGestureHandler?.onGestureMove(gestureContext) == TaskGestureConsume.Consume) {
                            handledGesture = true
                            activePointers.forEach { it.consume() }
                            continue
                        }

                        var consumeThisFrame = false
                        when (fingerCount) {
                            1 -> {
                                if (initialFingerCount.value != 1) {
                                    continue
                                }
                                val firstPointer = activePointers.firstOrNull() ?: continue
                                val dragAmount = firstPointer.position - gestureStartPosition.value
                                totalDragX.value = dragAmount.x
                                totalDragY.value = dragAmount.y
                                val distanceFromStart = kotlin.math.hypot(
                                    dragAmount.x.toDouble(),
                                    dragAmount.y.toDouble()
                                ).toFloat()
                                if (distanceFromStart > maxDistanceFromStartPx) {
                                    maxDistanceFromStartPx = distanceFromStart
                                }

                                if (abs(totalDragX.value) > abs(totalDragY.value)) {
                                    if (abs(totalDragX.value) > MODE_SWITCH_THRESHOLD_PX && !hasSwitchedMode.value) {
                                        val availableModes = if (visibleModes.isNotEmpty()) visibleModes else listOf(FlightMode.CRUISE)
                                        val currentIndex = availableModes.indexOf(currentMode)
                                        val newMode = if (currentIndex != -1) {
                                            val nextIndex = if (totalDragX.value > 0) {
                                                (currentIndex + 1) % availableModes.size
                                            } else {
                                                (currentIndex - 1 + availableModes.size) % availableModes.size
                                            }
                                            availableModes[nextIndex]
                                        } else {
                                            availableModes.first()
                                        }
                                        onModeChange(newMode)
                                        hasSwitchedMode.value = true
                                        handledGesture = true
                                        consumeThisFrame = true
                                    }
                                } else {
                                    val previous = firstPointer.previousPosition ?: firstPointer.position
                                    val currentDrag = firstPointer.position - previous
                                    if (abs(currentDrag.y) > SINGLE_FINGER_DRAG_MIN_PX) {
                                        handledGesture = true
                                        consumeThisFrame = true
                                        val zoomDelta = -currentDrag.y * SINGLE_FINGER_ZOOM_FACTOR
                                        mapLibreMap?.let { map ->
                                            val currentZoom = map.cameraPosition.zoom
                                            val targetZoom = currentZoom + zoomDelta
                                            val clampedZoom = MapZoomConstraints.clampZoom(
                                                zoom = targetZoom,
                                                widthPx = size.width.toInt(),
                                                currentZoom = currentZoom,
                                                distancePerPixel = run {
                                                    val lat = map.cameraPosition.target?.latitude ?: 0.0
                                                    val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(lat)
                                                    if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
                                                }
                                            )
                                            if (clampedZoom != currentZoom) {
                                                map.moveCamera(CameraUpdateFactory.zoomTo(clampedZoom))
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                val firstPointer = activePointers.getOrNull(0)
                                val secondPointer = activePointers.getOrNull(1)
                                if (firstPointer != null && secondPointer != null) {
                                    val centerPoint = Offset(
                                        (firstPointer.position.x + secondPointer.position.x) / 2f,
                                        (firstPointer.position.y + secondPointer.position.y) / 2f
                                    )
                                    val previousCenterPoint = Offset(
                                        ((firstPointer.previousPosition?.x ?: firstPointer.position.x) +
                                            (secondPointer.previousPosition?.x ?: secondPointer.position.x)) / 2f,
                                        ((firstPointer.previousPosition?.y ?: firstPointer.position.y) +
                                            (secondPointer.previousPosition?.y ?: secondPointer.position.y)) / 2f
                                    )
                                    val panDelta = centerPoint - previousCenterPoint
                                    if (abs(panDelta.x) > 1f || abs(panDelta.y) > 1f) {
                                        handledGesture = true
                                        consumeThisFrame = true
                                        if (!showReturnButton) {
                                            if (currentLocation != null) {
                                                onSaveLocation(
                                                    currentLocation,
                                                    mapLibreMap?.cameraPosition?.zoom ?: 10.0,
                                                    mapLibreMap?.cameraPosition?.bearing ?: 0.0
                                                )
                                            }
                                            onShowReturnButton(true)
                                        }

                                        mapLibreMap?.let { map ->
                                            val currentTarget = map.cameraPosition.target
                                            if (currentTarget != null) {
                                                val projection = map.projection
                                                val screenCenter = android.graphics.PointF(size.width / 2f, size.height / 2f)
                                                val screenPanned = android.graphics.PointF(
                                                    screenCenter.x - panDelta.x,
                                                    screenCenter.y - panDelta.y
                                                )
                                                val centerLatLng = projection.fromScreenLocation(screenCenter)
                                                val pannedLatLng = projection.fromScreenLocation(screenPanned)
                                                if (centerLatLng != null && pannedLatLng != null) {
                                                    val latDelta = pannedLatLng.latitude - centerLatLng.latitude
                                                    val lngDelta = pannedLatLng.longitude - centerLatLng.longitude
                                                    val newTarget = org.maplibre.android.geometry.LatLng(
                                                        currentTarget.latitude + latDelta,
                                                        currentTarget.longitude + lngDelta
                                                    )
                                                    map.moveCamera(CameraUpdateFactory.newLatLng(newTarget))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }

                        if (consumeThisFrame) {
                            activePointers.forEach { it.consume() }
                        }
                    } while (fingerCount > 0)

                    val endContext = TaskGestureContext(
                        mapLibreMap = mapLibreMap,
                        gestureStartPosition = gestureStartPosition.value,
                        activePointers = emptyList(),
                        gestureStartTimeMs = gestureStartTimeMs,
                        currentTimeMs = System.currentTimeMillis()
                    )
                    taskGestureHandler?.onGestureEnd(endContext)

                    val gestureDurationMs = System.currentTimeMillis() - gestureStartTimeMs
                    val isTap = initialFingerCount.value == 1 &&
                        !handledGesture &&
                        gestureDurationMs <= TAP_MAX_DURATION_MS &&
                        maxDistanceFromStartPx <= TAP_MAX_MOVE_PX
                    val isLongPress = initialFingerCount.value == 1 &&
                        !handledGesture &&
                        gestureDurationMs >= LONG_PRESS_MIN_DURATION_MS &&
                        maxDistanceFromStartPx <= TAP_MAX_MOVE_PX
                    if (isTap || isLongPress) {
                        val tapPoint = android.graphics.PointF(
                            gestureStartPosition.value.x,
                            gestureStartPosition.value.y
                        )
                        val tapLatLng = mapLibreMap?.projection?.fromScreenLocation(tapPoint)
                        if (tapLatLng != null) {
                            if (isLongPress) {
                                onMapLongPress(tapLatLng)
                            } else {
                                onMapTap(tapLatLng)
                            }
                        }
                    }
                }
            }
    )
}
