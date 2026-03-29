package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.core.time.TimeBridge
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
    private val waypointFilesUseCase: WaypointFilesUseCase,
    private val localOwnshipRenderEnabledProvider: () -> Boolean = { true }
) {
    companion object {
        private const val LOG_TAG = "MapInitializer"
        private const val INITIAL_LATITUDE = 46.52
        private const val INITIAL_LONGITUDE = 6.63
        private const val INITIAL_ZOOM = 8.0
        private const val STYLE_LOAD_TIMEOUT_MS = 3_000L
    }

    private var styleLoadToken: Long = 0L
    private var interactionGestureDepth: Int = 0

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
            AppLogger.d(LOG_TAG, "Starting map initialization")
            SkySightMapLibreNetworkConfigurator.ensureConfigured()
            mapState.mapLibreMap = map
            setupMapStyle(map)
            setupInitialPosition(map)
            setupGestures(map)
            setupListeners(map)
            taskRenderSyncCoordinator.onMapReady(map)
            AppLogger.d(LOG_TAG, "Map initialization completed successfully")
            map
        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Fatal error in map initialization: ${e.message}", e)
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
                    val setupApplied = applyStyleSetupIfNeeded()
                    if (continuation.isActive) {
                        continuation.resume(setupApplied)
                    }
                }
            }
        } ?: false

        if (!loadedInTime && requestToken == styleLoadToken) {
            AppLogger.w(LOG_TAG, "Map style load timeout for $styleName; checking fallback init")
            if (!applyStyleSetupIfNeeded()) {
                AppLogger.w(LOG_TAG, "Style unavailable at timeout; waiting for async style callback")
            }
        }
    }

    private fun setupInitialPosition(map: MapLibreMap) {
        val cameraSnapshot = mapStateReader.lastCameraSnapshot.value
        val fallbackLocation = if (localOwnshipRenderEnabledProvider()) {
            mapStateReader.currentUserLocation.value
        } else {
            null
        } ?: mapStateReader.savedLocation.value
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
            target = MapPoint(targetLatLng.latitude, targetLatLng.longitude),
            zoom = zoomToUse,
            bearing = bearingToUse
        )
        overlayManager.setOgnViewportZoom(zoomToUse.toFloat())
        overlayManager.setAdsbViewportZoom(zoomToUse.toFloat())
    }

    private fun setupOverlays(map: MapLibreMap) {
        try {
            mapState.scaleBarController = scaleBarController
            // Initialize blue location overlay
            mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            mapState.blueLocationOverlay?.initialize()
            mapState.blueLocationOverlay?.setVisible(localOwnshipRenderEnabledProvider())
            updateBlueLocationViewportMetrics()
            overlayManager.initializeTrafficOverlays(map)
            overlayManager.reapplyForecastOverlay()
            overlayManager.reapplySkySightSatelliteOverlay()
            overlayManager.reapplyWeatherRainOverlay()
            snailTrailManager.initialize(map)
            scaleBarController.setupScaleBar(map)

        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Error setting up overlays: ${e.message}", e)
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
    }

    private fun setupListeners(map: MapLibreMap) {
        // Move listener for pan detection
        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                onUserMapInteractionBegan()
                handleMapMovement(map)
            }

            override fun onMove(detector: MoveGestureDetector) {
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                onUserMapInteractionEnded()
                dataLoader.refreshWaypoints(map)
            }
        })

        // Rotation listener for orientation override
        map.addOnRotateListener(object : MapLibreMap.OnRotateListener {
            override fun onRotateBegin(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                onUserMapInteractionBegan()
                orientationManager.onUserInteraction()
            }

            override fun onRotate(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                // Rotation in progress
            }

            override fun onRotateEnd(detector: org.maplibre.android.gestures.RotateGestureDetector) {
                onUserMapInteractionEnded()
            }
        })

        // Camera change listener for zoom-adaptive distance circles
        map.addOnCameraIdleListener {
            try {
                interactionGestureDepth = 0
                overlayManager.setMapInteractionActive(false)
                val cameraPosition = map.cameraPosition
                val currentZoom = cameraPosition.zoom
                stateActions.updateCurrentZoom(currentZoom.toFloat())
                overlayManager.setOgnViewportZoom(currentZoom.toFloat())
                overlayManager.setAdsbViewportZoom(currentZoom.toFloat())
                updateBlueLocationViewportMetrics()
                val target = cameraPosition.target
                if (target != null) {
                    stateActions.updateCameraSnapshot(
                        target = MapPoint(target.latitude, target.longitude),
                        zoom = currentZoom,
                        bearing = cameraPosition.bearing
                    )
                }
                scaleBarController.onCameraIdle(map)
                // Canvas overlay listens to MapStateStore.currentZoom for zoom-adaptive effects.
            } catch (e: Exception) {
                AppLogger.e(LOG_TAG, "Error updating camera-idle overlay state: ${e.message}", e)
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
            }
        }

        // Show return button on user interaction
        stateActions.setShowRecenterButton(false)
        stateActions.setShowReturnButton(true)
        stateActions.updateLastUserPanTime(TimeBridge.nowWallMs())
    }

    private fun onUserMapInteractionBegan() {
        interactionGestureDepth += 1
        if (interactionGestureDepth == 1) {
            overlayManager.setMapInteractionActive(true)
        }
    }

    private fun onUserMapInteractionEnded() {
        if (interactionGestureDepth <= 0) {
            interactionGestureDepth = 0
            overlayManager.setMapInteractionActive(false)
            return
        }
        interactionGestureDepth -= 1
        if (interactionGestureDepth == 0) {
            overlayManager.setMapInteractionActive(false)
        }
    }

    private fun updateBlueLocationViewportMetrics() {
        mapState.blueLocationOverlay?.setViewportMetrics(
            metrics = resolveCurrentViewportMetrics(),
            distancePerPixelMeters = resolveCurrentBlueLocationDistancePerPixelMeters()
        )
    }

    private fun resolveCurrentViewportMetrics(): MapCameraViewportMetrics? {
        val mapView = mapState.mapView ?: return null
        return MapCameraViewportMetrics(
            widthPx = mapView.width,
            heightPx = mapView.height,
            pixelRatio = mapView.pixelRatio
        )
    }

    private fun resolveCurrentBlueLocationDistancePerPixelMeters(): Double? {
        val map = mapState.mapLibreMap ?: return null
        val mapView = mapState.mapView ?: return null
        val pixelRatio = mapView.pixelRatio
        if (!pixelRatio.isFinite() || pixelRatio <= 0f) {
            return null
        }
        val latitude = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = runCatching {
            map.projection.getMetersPerPixelAtLatitude(latitude)
        }.getOrNull() ?: return null
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) {
            return null
        }
        return metersPerPixel / pixelRatio
    }

}





