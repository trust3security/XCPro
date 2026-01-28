package com.example.ui1.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import android.net.Uri

private const val TAG = "TaskMapView"
private const val SWIPE_COOLDOWN_MS = 300L
private const val ZOOM_SENSITIVITY = 0.005f
private const val DOUBLE_TAP_ZOOM_DELTA = 1.0
private const val INITIAL_LATITUDE = -30.87
private const val INITIAL_LONGITUDE = 150.52
private const val INITIAL_ZOOM = 7.5

/**
 * Task Map View Module
 *
 * Handles MapLibre map display, gestures, and camera controls.
 * Extracted from Task.kt for better modularity.
 */

@Composable
fun TaskMapView(
    context: Context,
    currentMode: FlightMode,
    onModeChange: (FlightMode) -> Unit,
    targetZoom: Float?,
    targetLatLng: LatLng?,
    onMapReady: (MapLibreMap) -> Unit,
    selectedWaypointFiles: List<Uri>,
    waypointCheckedStates: Map<String, Boolean>
) {
    var swipeDirection by remember { mutableStateOf<String?>(null) }
    var lastSwipeTime by remember { mutableLongStateOf(0L) }

    val animatedZoom by animateFloatAsState(
        targetValue = targetZoom ?: INITIAL_ZOOM.toFloat(),
        animationSpec = tween(durationMillis = 300),
        label = "zoom_animation"
    )

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val airspaceRepository = remember(context) { AirspaceRepository(context) }

    // Camera animation effect
    LaunchedEffect(animatedZoom, targetLatLng) {
        mapLibreMap?.let { map ->
            try {
                val latLng = targetLatLng ?: LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, animatedZoom.toDouble()))
                Log.d(TAG, "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$animatedZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error moving camera: ${e.message}")
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                Log.d(TAG, " Creating MapView for Task screen")
                getMapAsync { map ->
                    mapLibreMap = map
                    onMapReady(map)

                    // Set initial camera position
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE))
                        .zoom(INITIAL_ZOOM)
                        .build()

                    // Gesture listeners
                    map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                        override fun onMoveBegin(detector: MoveGestureDetector) {
                            Log.d(TAG, "Map move began")
                        }
                        override fun onMove(detector: MoveGestureDetector) {}
                        override fun onMoveEnd(detector: MoveGestureDetector) {
                            Log.d(TAG, "Map move ended")
                        }
                    })

                    map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
                        override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                            Log.d(TAG, "Scale began")
                        }
                        override fun onScale(detector: StandardScaleGestureDetector) {}
                        override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                            Log.d(TAG, "Scale ended, current zoom: ${map.cameraPosition.zoom}")
                        }
                    })

                    // Load and apply airspace and waypoints
                    coroutineScope.launch {
                        loadAndApplyAirspace(ctx, map, airspaceRepository)
                    }
                    coroutineScope.launch {
                        loadAndApplyWaypoints(ctx, map, selectedWaypointFiles, waypointCheckedStates)
                    }

                    Log.d(TAG, " MapView initialized successfully")
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    // Swipe direction indicator (optional overlay)
    swipeDirection?.let { direction ->
        LaunchedEffect(direction) {
            kotlinx.coroutines.delay(500)
            swipeDirection = null
        }
    }
}

