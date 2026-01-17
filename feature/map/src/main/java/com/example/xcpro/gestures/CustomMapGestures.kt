package com.example.xcpro.gestures

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverter
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverterFactory
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.map.MapGestureRegion
import com.example.xcpro.map.MapZoomConstraints
import com.example.xcpro.map.BuildConfig
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "CustomMapGestures"
private const val MAP_GESTURE_TAG = "MAP_GESTURE"

/**
 * Check if a screen coordinate hits any AAT waypoint area
 * For AAT tasks: Only TURNPOINT role waypoints are editable (not START or FINISH)
 * Uses closest-match logic to handle overlapping areas
 */
private fun checkAATWaypointHit(
    offset: Offset,
    waypoints: List<TaskWaypoint>,
    converter: AATMapCoordinateConverter?
): Int? {
    if (BuildConfig.DEBUG) Log.d(TAG, "🐛 HIT CHECK: converter=$converter, waypoints.size=${waypoints.size}")
    if (converter == null) {
        if (BuildConfig.DEBUG) Log.d(TAG, "🐛 HIT CHECK: Converter is null!")
        return null
    }

    val tapLatLng = converter.screenToMap(offset.x, offset.y)
    if (BuildConfig.DEBUG) Log.d(TAG, "🐛 HIT CHECK: Screen ${offset.x},${offset.y} -> Map $tapLatLng")
    if (tapLatLng == null) {
        if (BuildConfig.DEBUG) Log.d(TAG, "🐛 HIT CHECK: Failed to convert screen to map coordinates")
        return null
    }

    // Find the closest TURNPOINT waypoint (not START or FINISH)
    var closestIndex = -1
    var closestDistance = Double.MAX_VALUE

    waypoints.forEachIndexed { index, waypoint ->
        // ✅ AAT Rule: Only TURNPOINT role waypoints can be edited
        // START and FINISH waypoints are fixed and cannot be moved
        if (waypoint.role != WaypointRole.TURNPOINT) {
            if (BuildConfig.DEBUG) Log.d(TAG, "⛔ HIT CHECK: Skipping ${waypoint.title} - role ${waypoint.role} is not editable")
            return@forEachIndexed
        }

        val distance = haversineDistance(
            tapLatLng.latitude, tapLatLng.longitude,
            waypoint.lat, waypoint.lon
        )

        // Get AAT area radius from waypoint custom parameters (defaults to 10km)
        val areaRadiusKm = (waypoint.customParameters["aatAreaRadiusKm"] as? Double) ?: 10.0

        if (BuildConfig.DEBUG) Log.d(TAG, "🐛 HIT CHECK: Turnpoint $index (${waypoint.title}) - distance=${String.format("%.2f", distance)}km, radius=${String.format("%.2f", areaRadiusKm)}km")

        // Check if within area and closer than previous matches
        if (distance <= areaRadiusKm && distance < closestDistance) {
            closestDistance = distance
            closestIndex = index
            if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT Hit: New closest turnpoint: ${waypoint.title} at index $index (${String.format("%.2f", distance)}km from center)")
        }
    }

    if (closestIndex >= 0) {
        if (BuildConfig.DEBUG) Log.d(TAG, "✅ HIT CHECK: Selected turnpoint at index $closestIndex (${String.format("%.2f", closestDistance)}km away)")
        return closestIndex
    } else {
        if (BuildConfig.DEBUG) Log.d(TAG, "🐛 HIT CHECK: No editable turnpoint found at tap location")
        return null
    }
}

/**
 * Calculate haversine distance between two points in kilometers
 */
@Composable
fun CustomMapGestureHandler(
    mapLibreMap: MapLibreMap?,
    currentMode: FlightMode,
    onModeChange: (FlightMode) -> Unit,
    showReturnButton: Boolean,
    onShowReturnButton: (Boolean) -> Unit,
    currentLocation: GPSData?,
    onSaveLocation: (GPSData?, Double, Double) -> Unit,
    bottomSheetHeight: Float = 0f,
    visibleModes: List<FlightMode> = FlightMode.values().toList(),
    // ✅ AAT Long Press Support
    taskType: TaskType? = null,
    aatWaypoints: List<TaskWaypoint> = emptyList(),
    isAATEditMode: Boolean = false, // ✅ NEW: External edit mode state
    onAATLongPress: (Int) -> Unit = {},
    onAATExitEditMode: () -> Unit = {},
    onAATDrag: (Int, AATLatLng) -> Unit = { _, _ -> },
    gestureRegions: List<MapGestureRegion> = emptyList(),
    mapViewPixelRatio: Float = 0f,
    modifier: Modifier = Modifier
) {
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }
    var hasSwitchedMode by remember { mutableStateOf(false) }
    var gestureStartPosition by remember { mutableStateOf(Offset.Zero) }
    var initialFingerCount by remember { mutableStateOf(1) } // ✅ FIX: Track initial finger count

    // ✅ AAT Edit Mode State
    var aatEditModeIndex by remember { mutableStateOf(-1) }
    var isDraggingAAT by remember { mutableStateOf(false) }
    val coordinateConverter = remember(mapLibreMap) {
        mapLibreMap?.let { AATMapCoordinateConverterFactory.create(it) }
    }
    val pixelRatio = if (mapViewPixelRatio > 0f) mapViewPixelRatio else 1f

    // ✅ AAT Double-Tap Detection State
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    var lastTapWaypointIndex by remember { mutableStateOf<Int?>(null) }

    // ✅ CRITICAL FIX: Reset local state when external edit mode is disabled OR task type changes
    LaunchedEffect(isAATEditMode, taskType) {
        if (!isAATEditMode || taskType != TaskType.AAT) {
            if (BuildConfig.DEBUG) Log.d(TAG, "🔧 RESET: Clearing AAT gesture state (editMode=$isAATEditMode, taskType=$taskType)")
            aatEditModeIndex = -1
            isDraggingAAT = false
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            // Include AAT state in pointerInput keys so gesture coroutine restarts when task changes
            .pointerInput(currentMode, gestureRegions, taskType, aatWaypoints, isAATEditMode) {
                awaitEachGesture {
                    if (BuildConfig.DEBUG) Log.d(MAP_GESTURE_TAG, "awaitEachGesture start (taskType=$taskType, isAATEditMode=$isAATEditMode)")
                    // Pre-scan for down inside overlay regions before map consumes it
                    while (true) {
                        val preDownEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val preDownChange = preDownEvent.changes.firstOrNull { it.changedToDown() }
                        if (preDownChange != null) {
                            gestureStartPosition = preDownChange.position
                            val overlayRegion = gestureRegions.firstOrNull { region ->
                                region.bounds.contains(gestureStartPosition)
                            }
                            if (BuildConfig.DEBUG) Log.d(MAP_GESTURE_TAG, "pre-scan pointer=${preDownChange.position}, region=${(overlayRegion?.target)}, consume=${(overlayRegion?.consumeGestures)}")
                            if (overlayRegion != null) {
                                if (BuildConfig.DEBUG) Log.d(TAG, "Pointer down inside overlay region ${overlayRegion.target} (consume=${overlayRegion.consumeGestures})")
                                if (overlayRegion.consumeGestures) {
                                    return@awaitEachGesture
                                } else {
                                    // Let event fall through to map gesture handling
                                    break
                                }
                            } else {
                                break
                            }
                        }
                    }

                    // Wait for first finger down
                    val firstDown = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Final
                    )
                    if (firstDown.isConsumed) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Pointer down already consumed by overlay; skipping map gesture")
                        return@awaitEachGesture
                    }
                    gestureStartPosition = firstDown.position

                    // Check if gesture is over flight data cards
                    val screenHeight = size.height.toFloat()
                    val isOverFlightDataCards = gestureStartPosition.y > (screenHeight - bottomSheetHeight)

                    if (isOverFlightDataCards) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "🚫 Gesture over flight data cards - ignoring (y=${gestureStartPosition.y}, cardHeight=$bottomSheetHeight)")
                        return@awaitEachGesture
                    }

                    // Reset tracking for new gesture
                    totalDragX = 0f
                    totalDragY = 0f
                    hasSwitchedMode = false

                    if (BuildConfig.DEBUG) Log.d(TAG, "🎯 First finger down at ${gestureStartPosition}")

                    // ✅ AAT Waypoint Hit Detection: Check for AAT waypoint hit immediately
                    var aatWaypointHit: Int? = null
                    val gestureStartTime = System.currentTimeMillis()

                    // 🐛 DEBUG: Log AAT state
                    if (BuildConfig.DEBUG) Log.d(TAG, "🐛 AAT DEBUG: taskType=$taskType, aatWaypoints.size=${aatWaypoints.size}, converter=$coordinateConverter")

                    if (taskType == TaskType.AAT && aatWaypoints.isNotEmpty()) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "🐛 AAT DEBUG: Checking hit on ${aatWaypoints.size} waypoints")
                        aatWaypointHit = checkAATWaypointHit(gestureStartPosition, aatWaypoints, coordinateConverter)
                        if (aatWaypointHit != null) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Potential waypoint hit detected at index $aatWaypointHit")
                        } else {
                            if (BuildConfig.DEBUG) Log.d(TAG, "🐛 AAT DEBUG: No waypoint hit detected")
                        }
                    } else {
                        if (BuildConfig.DEBUG) Log.d(TAG, "🐛 AAT DEBUG: AAT conditions not met - taskType=$taskType, waypoints=${aatWaypoints.size}")
                    }

                    // Track pointer events to count fingers
                    var fingerCount = 1
                    var isFirstFrame = true

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Final)

                        // Count active pointers (fingers on screen)
                        val activePointers = event.changes.filter { !it.changedToUp() }
                        fingerCount = activePointers.size

                        if (isFirstFrame) {
                            initialFingerCount = fingerCount // ✅ FIX: Capture initial finger count
                            if (BuildConfig.DEBUG) Log.d(TAG, "🖐️ Gesture started with $fingerCount finger(s)")
                            isFirstFrame = false
                        }

                        // ✅ AAT Edit Mode Entry: Double-tap only (long press removed for better UX)

                        // ✅ AAT Edit Mode: Handle ALL gestures to prevent drawer opening
                        if (aatEditModeIndex != -1 && taskType == TaskType.AAT) {
                            // In AAT edit mode, consume ALL gestures to prevent drawer from opening

                            if (fingerCount == 1) {
                                val currentPos = activePointers.firstOrNull()?.position ?: gestureStartPosition
                                val dragDistance = (currentPos - gestureStartPosition).getDistance()

                                // Start dragging if movement detected
                                if (!isDraggingAAT && dragDistance > 10f) {
                                    isDraggingAAT = true
                                    if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Started dragging pin $aatEditModeIndex")
                                }

                                // Update pin position during drag
                                if (isDraggingAAT) {
                                    val newLatLng = coordinateConverter?.screenToMap(currentPos.x, currentPos.y)
                                    if (newLatLng != null) {
                                        val newPosition = AATLatLng(newLatLng.latitude, newLatLng.longitude)
                                        onAATDrag(aatEditModeIndex, newPosition)
                                        // Log less frequently to avoid spam
                                        if (System.currentTimeMillis() % 500 < 50) {
                                            if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Dragging pin $aatEditModeIndex")
                                        }
                                    }
                                }
                            }

                            // Consume all pointer events during AAT edit mode
                            // This prevents drawer gestures and other interactions
                            activePointers.forEach { it.consume() }

                            // Skip normal gesture processing
                            continue
                        }

                        // Route gesture based on finger count
                        when (fingerCount) {
                            1 -> {
                                // ✅ FIX: Only allow mode switching if gesture STARTED with single finger
                                // This prevents 2-finger pan from triggering mode switch if one finger lifts
                                if (initialFingerCount != 1) {
                                    if (BuildConfig.DEBUG) Log.d(TAG, "🚫 Single finger detected but gesture started with $initialFingerCount fingers - ignoring mode switch logic")
                                    // Don't process mode switching for gestures that started with multiple fingers
                                    continue
                                }

                                // ✅ Single finger: Mode switching (horizontal) or zoom (vertical)
                                val firstPointer = activePointers.firstOrNull()
                                if (firstPointer != null) {
                                    val dragAmount = firstPointer.position - gestureStartPosition
                                    totalDragX = dragAmount.x
                                    totalDragY = dragAmount.y

                                    if (abs(totalDragX) > abs(totalDragY)) {
                                        // ✅ Horizontal: Flight mode switching (DISCRETE) - RESPECTS VISIBILITY
                                        if (abs(totalDragX) > 250f && !hasSwitchedMode) {
                                            // ✅ FIX: Use only visible modes instead of all modes
                                            val availableModes = if (visibleModes.isNotEmpty()) visibleModes else listOf(FlightMode.CRUISE)
                                            val currentIndex = availableModes.indexOf(currentMode)

                                            if (BuildConfig.DEBUG) Log.d(TAG, "🔍 DEBUG: currentMode=${currentMode.displayName}, currentIndex=$currentIndex, availableModes=${availableModes.map { it.displayName }}")

                                            val newMode = if (currentIndex != -1) {
                                                // ✅ FIX: Always cycle sequentially - make direction consistent
                                                val nextIndex = if (totalDragX > 0) {
                                                    // RIGHT swipe: go to next mode in sequence [0→1→2→0...]
                                                    (currentIndex + 1) % availableModes.size
                                                } else {
                                                    // LEFT swipe: go to previous mode in sequence [0←1←2←0...]
                                                    (currentIndex - 1 + availableModes.size) % availableModes.size
                                                }

                                                val directionText = if (totalDragX > 0) "RIGHT→NEXT" else "LEFT→PREV"
                                                if (BuildConfig.DEBUG) Log.d(TAG, "🔄 CYCLE: ${currentMode.displayName}[$currentIndex] $directionText → ${availableModes[nextIndex].displayName}[$nextIndex]")
                                                if (BuildConfig.DEBUG) Log.d(TAG, "🔄 SEQUENCE: ${availableModes.mapIndexed { i, mode -> if (i == currentIndex) "[${mode.displayName}]" else mode.displayName }.joinToString(" → ")}")

                                                availableModes[nextIndex]
                                            } else {
                                                // Current mode not in visible list - fallback to first visible mode
                                                if (BuildConfig.DEBUG) Log.d(TAG, "🔍 DEBUG: Current mode not in visible list - fallback to first")
                                                availableModes.first()
                                            }

                                            if (BuildConfig.DEBUG) Log.d(TAG, "📞 About to call onModeChange with: ${newMode.displayName}")
                                            onModeChange(newMode)
                                            if (BuildConfig.DEBUG) Log.d(TAG, "✅ onModeChange callback completed")
                                            hasSwitchedMode = true
                                            if (BuildConfig.DEBUG) Log.d(TAG, "🔄 Single finger horizontal: Flight mode changed to ${newMode.displayName} (visible modes: ${availableModes.map { it.displayName }})")
                                        }
                                    } else {
                                        // ✅ Vertical: Zoom (single finger up/down)
                                        val currentDrag = firstPointer.position - (firstPointer.previousPosition ?: firstPointer.position)
                                        if (abs(currentDrag.y) > 2f) {
                                            val zoomDelta = -currentDrag.y * 0.008
                                            mapLibreMap?.let { map ->
                                                val currentZoom = map.cameraPosition.zoom
                                                val targetZoom = currentZoom + zoomDelta
                                                val clampedZoom = MapZoomConstraints.clampZoom(
                                                    zoom = targetZoom,
                                                    widthPx = size.width.toInt(),
                                                    currentZoom = map.cameraPosition.zoom,
                                                    distancePerPixel = run {
                                                        val lat = map.cameraPosition.target?.latitude ?: 0.0
                                                        val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(lat)
                                                        if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
                                                    }
                                                )
                                                if (clampedZoom != currentZoom) {
                                                    map.moveCamera(CameraUpdateFactory.zoomTo(clampedZoom))
                                                    if (BuildConfig.DEBUG) Log.d(TAG, "🔍 Single finger vertical: Custom zoom $zoomDelta")
                                                }
                                            }
                                        }
                                    }
                                    // ❌ CRITICAL: NO PANNING WITH SINGLE FINGER
                                    if (BuildConfig.DEBUG) Log.d(TAG, "✅ Single finger gesture - no panning (complies with CLAUDE.md)")
                                }
                            }

                            2 -> {
                                // Two finger: Map panning (CLAUDE.md requirement)
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
                                    if (!showReturnButton) {
                                        if (currentLocation != null) {
                                            onSaveLocation(
                                                currentLocation,
                                                mapLibreMap?.cameraPosition?.zoom ?: 10.0,
                                                mapLibreMap?.cameraPosition?.bearing ?: 0.0
                                            )
                                            if (BuildConfig.DEBUG) Log.d(TAG, "Saved position for return button")
                                        }
                                        // Always allow users to break tracking even if location is null.
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
                                                    if (BuildConfig.DEBUG) Log.d(TAG, "Two finger pan: delta=(${panDelta.x}, ${panDelta.y})")
                                                }
                                            }
                                        }
                                    }
                                }
                            }                            else -> {
                                // ✅ 3+ fingers: Ignore for now
                                if (BuildConfig.DEBUG) Log.d(TAG, "🖐️ ${fingerCount} fingers detected - ignoring complex gestures")
                            }
                        }

                        // Mark changes as consumed
                        activePointers.forEach { it.consume() }

                    } while (activePointers.isNotEmpty())

                    if (BuildConfig.DEBUG) Log.d(TAG, "🏁 Gesture ended")

                    // ✅ AAT Double-Tap Detection: Check for quick tap on AAT area
                    val currentTime = System.currentTimeMillis()
                    val gestureDuration = currentTime - gestureStartTime
                    val isQuickTap = gestureDuration < 200L // Quick tap, not a drag

                    if (taskType == TaskType.AAT && isQuickTap && !isDraggingAAT) {
                        val timeSinceLastTap = currentTime - lastTapTime
                        val distanceFromLastTap = (gestureStartPosition - lastTapPosition).getDistance()

                        // Double-tap conditions: within 500ms and within 50px of previous tap
                        if (timeSinceLastTap < 500L && distanceFromLastTap < 50f) {
                            // This is a double-tap!
                            if (aatEditModeIndex == -1) {
                                // Not in edit mode: Check if double-tapped on AAT area
                                if (aatWaypointHit != null && aatWaypointHit == lastTapWaypointIndex) {
                                    // Double-tapped on same AAT area: Enter edit mode
                                    if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Double-tap detected on waypoint $aatWaypointHit - entering edit mode")
                                    aatEditModeIndex = aatWaypointHit
                                    onAATLongPress(aatWaypointHit) // Reuse same callback
                                }
                            } else {
                                // Already in edit mode: Double-tap exits edit mode
                                if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Double-tap detected - exiting edit mode")
                                aatEditModeIndex = -1
                                isDraggingAAT = false
                                onAATExitEditMode()
                            }

                            // Reset double-tap tracking after successful double-tap
                            lastTapTime = 0L
                            lastTapWaypointIndex = null
                        } else {
                            // First tap or too far apart: Record this tap for potential double-tap
                            lastTapTime = currentTime
                            lastTapPosition = gestureStartPosition
                            lastTapWaypointIndex = aatWaypointHit
                            if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: First tap recorded (waypoint=$aatWaypointHit)")
                        }
                    }

                    // ✅ AAT: Handle long-press to exit edit mode (ONLY long press, no quick tap)
                    if (aatEditModeIndex != -1 && !isDraggingAAT && !isQuickTap) {
                        val gestureDuration = currentTime - gestureStartTime

                        // Exit condition: ONLY long press (>=350ms) anywhere on screen
                        val isLongPress = gestureDuration >= 350L

                        if (isLongPress) {
                            // Long press while in edit mode: Exit edit mode (works anywhere on screen)
                            if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Long press detected - exiting edit mode (held ${gestureDuration}ms)")
                            aatEditModeIndex = -1
                            isDraggingAAT = false
                            onAATExitEditMode()
                        }
                    }

                    // Reset AAT drag state at end of gesture
                    if (aatEditModeIndex != -1 && isDraggingAAT) {
                        isDraggingAAT = false
                        if (BuildConfig.DEBUG) Log.d(TAG, "🎯 AAT: Drag ended for waypoint $aatEditModeIndex")
                    }
                }
            }
    )
}



