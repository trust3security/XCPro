package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.trail.SnailTrailManager
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class MapInitializer(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val overlayManager: MapOverlayManager,
    private val orientationManager: MapOrientationManager,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val snailTrailManager: SnailTrailManager,
    private val coroutineScope: CoroutineScope,
    private val airspaceUseCase: AirspaceUseCase,
    private val waypointFilesUseCase: WaypointFilesUseCase
) {
    companion object {
        private const val TAG = "MapInitializer"
        private const val INITIAL_LATITUDE = 46.52
        private const val INITIAL_LONGITUDE = 6.63
        private const val INITIAL_ZOOM = 8.0
        private const val STYLE_LOAD_TIMEOUT_MS = 3_000L
    }

    private var styleLoadToken: Long = 0L

    private val scaleBarController = MapScaleBarController(mapState).also {
        mapState.scaleBarController = it
    }
    private val dataLoader = MapInitializerDataLoader(
        context = context,
        mapState = mapState,
        coroutineScope = coroutineScope,
        airspaceUseCase = airspaceUseCase,
        waypointFilesUseCase = waypointFilesUseCase
    )

    suspend fun initializeMap(map: MapLibreMap): MapLibreMap {
        return try {
            Log.d(TAG, "Starting map initialization")
            SkySightMapLibreNetworkConfigurator.ensureConfigured()
            mapState.mapLibreMap = map
            setupMapStyle(map)
            setupInitialPosition(map)
            setupGestures(map)
            setupListeners(map)
            taskRenderSyncCoordinator.onMapReady(map)
            Log.d(TAG, "Map initialization completed successfully")
            map
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in map initialization: ${e.message}", e)
            map
        }
    }

    private suspend fun setupMapStyle(map: MapLibreMap) {
        val styleName = mapStateReader.mapStyleName.value
        val styleUrl = MapStyleUrlResolver.resolve(styleName)
        val requestToken = ++styleLoadToken
        var styleSetupApplied = false

        fun applyStyleSetupIfNeeded(): Boolean {
            if (styleSetupApplied || requestToken != styleLoadToken) {
                return styleSetupApplied
            }
            if (mapState.mapLibreMap !== map) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Skipping stale style callback for detached map instance")
                }
                return false
            }
            val hasLoadedStyle = map.style != null
            if (!hasLoadedStyle) {
                return false
            }
            styleSetupApplied = true
            setupOverlays(map)
            dataLoader.loadInitialData(map)
            return true
        }

        val loadedInTime = withTimeoutOrNull(STYLE_LOAD_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                map.setStyle(styleUrl) { _ ->
                    if (requestToken != styleLoadToken) {
                        return@setStyle
                    }
                    Log.d(TAG, "Map style loaded: $styleName")
                    val setupApplied = applyStyleSetupIfNeeded()
                    if (continuation.isActive) {
                        continuation.resume(setupApplied)
                    }
                }
            }
        } ?: false

        if (!loadedInTime && requestToken == styleLoadToken) {
            Log.w(TAG, "Map style load timeout for $styleName; checking fallback init")
            if (!applyStyleSetupIfNeeded()) {
                Log.w(TAG, "Style unavailable at timeout; waiting for async style callback")
            }
        }
    }

    private fun setupInitialPosition(map: MapLibreMap) {
        val cameraSnapshot = mapStateReader.lastCameraSnapshot.value
        val fallbackLocation = mapStateReader.currentUserLocation.value
            ?: mapStateReader.savedLocation.value
        val target = cameraSnapshot?.target ?: fallbackLocation
        val targetLatLng = target?.let {
            org.maplibre.android.geometry.LatLng(it.latitude, it.longitude)
        } ?: org.maplibre.android.geometry.LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE)
        val zoomToUse = cameraSnapshot?.zoom ?: run {
            val currentZoom = mapStateReader.currentZoom.value.toDouble()
            if (mapStateReader.hasInitiallyCentered.value && currentZoom.isFinite() && currentZoom > 0.0) {
                currentZoom
            } else {
                INITIAL_ZOOM
            }
        }
        val bearingToUse = cameraSnapshot?.bearing ?: 0.0

        val cameraPosition = CameraPosition.Builder()
            .target(targetLatLng)
            .zoom(zoomToUse)
            .bearing(bearingToUse)
            .build()

        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        // Keep Compose overlays (distance circles) in sync from the first frame.
        stateActions.updateCurrentZoom(zoomToUse.toFloat())
        stateActions.updateCameraSnapshot(
            target = MapStateStore.MapPoint(targetLatLng.latitude, targetLatLng.longitude),
            zoom = zoomToUse,
            bearing = bearingToUse
        )
        Log.d(TAG, "Initial map position set (restored=${cameraSnapshot != null}, zoom=$zoomToUse)")
    }

    private fun setupOverlays(map: MapLibreMap) {
        try {
            mapState.scaleBarController = scaleBarController
            // Initialize blue location overlay
            mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            mapState.blueLocationOverlay?.initialize()
            overlayManager.initializeTrafficOverlays(map)
            snailTrailManager.initialize(map)
            scaleBarController.setupScaleBar(map)
            Log.d(TAG, " Blue location overlay initialized")

            // DISABLED: Map-based distance circles replaced with DistanceCirclesCanvas
            // The circles are now drawn as a fixed screen overlay in MapScreen.kt
            // This prevents them from moving with the map
            // mapState.distanceCirclesOverlay = DistanceCirclesOverlay(context, map)
            // mapState.distanceCirclesOverlay?.initialize()
            // mapState.distanceCirclesOverlay?.setVisible(mapState.showDistanceCircles)
            Log.d(TAG, " Distance circles using Canvas overlay (map-based circles disabled)")

        } catch (e: Exception) {
            Log.e(TAG, " Error setting up overlays: ${e.message}", e)
        }
    }
    private fun setupGestures(map: MapLibreMap) {
        // Keep provider attribution reachable even with custom gesture routing overlays.
        map.uiSettings.isAttributionEnabled = true
        // Disable MapLibre standard gestures for custom gesture system
        map.uiSettings.isZoomGesturesEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isQuickZoomGesturesEnabled = false
        Log.d(TAG, " MapLibre standard gestures disabled for custom system")
    }

    private fun setupListeners(map: MapLibreMap) {
        // Move listener for pan detection
        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                Log.d(TAG, " Map movement detected - fingers: ${detector.pointersCount}")
                handleMapMovement(map)
            }

            override fun onMove(detector: MoveGestureDetector) {
                if (detector.pointersCount >= 2) {
                    Log.d(TAG, "Two-finger pan in progress")
                }
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                Log.d(TAG, "Pan ended")
                dataLoader.refreshWaypoints(map)
            }
        })

        // Rotation listener for orientation override
        map.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                Log.d(TAG, " Map rotation started")
                orientationManager.onUserInteraction()
            }

            override fun onRotate(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                // Rotation in progress
            }

            override fun onRotateEnd(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                Log.d(TAG, " Map rotation ended")
            }
        })

        // Camera change listener for zoom-adaptive distance circles
        map.addOnCameraIdleListener {
            try {
                val cameraPosition = map.cameraPosition
                val currentZoom = cameraPosition.zoom
                stateActions.updateCurrentZoom(currentZoom.toFloat())
                val target = cameraPosition.target
                if (target != null) {
                    stateActions.updateCameraSnapshot(
                        target = MapStateStore.MapPoint(target.latitude, target.longitude),
                        zoom = currentZoom,
                        bearing = cameraPosition.bearing
                    )
                }
                scaleBarController.onCameraIdle(map)
                // Canvas overlay listens to MapStateStore.currentZoom for zoom-adaptive effects.
                Log.d(TAG, "Camera idle, zoom: $currentZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating distance circles zoom: ${e.message}", e)
            }
        }
    }

    private fun handleMapMovement(map: MapLibreMap) {
        // Trigger orientation override on user pan gesture
        orientationManager.onUserInteraction()

        // Save current position before movement for return functionality
        if (!mapStateReader.showReturnButton.value) {
            val currentLocation = mapStateReader.currentUserLocation.value
            if (currentLocation != null) {
                stateActions.saveLocation(
                    location = currentLocation,
                    zoom = map.cameraPosition.zoom,
                    bearing = map.cameraPosition.bearing
                )
                Log.d(TAG, "Saved position for return")
            }
        }

        // Show return button on user interaction
        stateActions.setShowRecenterButton(false)
        stateActions.setShowReturnButton(true)
        stateActions.updateLastUserPanTime(System.currentTimeMillis())
        Log.d(TAG, "User interaction detected - return button shown")
    }

}





