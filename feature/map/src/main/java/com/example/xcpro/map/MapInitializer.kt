package com.example.xcpro.map

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import kotlin.math.max
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.scalebar.ScaleBarOptions
import org.maplibre.android.plugins.scalebar.ScaleBarPlugin
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MapInitializer(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val orientationManager: MapOrientationManager,
    private val taskManager: TaskManagerCoordinator,
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
        private const val SCALE_BAR_TEXT_SIZE_SP = 12f
        private const val SCALE_BAR_BAR_HEIGHT_DP = 6f
        private const val SCALE_BAR_TEXT_MARGIN_DP = 2f
        private const val SCALE_BAR_BORDER_WIDTH_DP = 1f
        private const val SCALE_BAR_TEXT_BORDER_WIDTH_DP = 2f
        private const val SCALE_BAR_REFRESH_INTERVAL_MS = 200
        private const val SCALE_BAR_DISTANCE_EPSILON = 1e-6
    }

    private var scaleBarLayoutListenerInstalled = false
    private var lastScaleBarWidth = 0
    private var lastScaleBarHeight = 0
    private var lastScaleBarDistancePerPixel = 0.0

    suspend fun initializeMap(map: MapLibreMap): MapLibreMap {
        return try {
            Log.d(TAG, "Starting map initialization")
            mapState.mapLibreMap = map
            setupMapStyle(map)
            setupInitialPosition(map)
            setupGestures(map)
            setupListeners(map)
            // CRITICAL FIX: Set map instance in TaskManagerCoordinator for cleanup operations
            taskManager.setMapInstance(map)
            Log.d(TAG, "Set map instance in TaskManagerCoordinator for task switching cleanup")
            Log.d(TAG, "Map initialization completed successfully")
            map
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in map initialization: ${e.message}", e)
            map
        }
    }

    private fun setupMapStyle(map: MapLibreMap) {
        val styleName = mapStateReader.mapStyleName.value
        val styleUrl = getMapStyleUrl(styleName)
        map.setStyle(styleUrl) { _ ->
            Log.d(TAG, "Map style loaded: $styleName")

            // Initialize overlays after style is loaded
            setupOverlays(map)

            loadMapData(map)
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

    private fun loadMapData(map: MapLibreMap) {
        try {
            // Load airspace data
            coroutineScope.launch {
                loadAndApplyAirspace(map, airspaceUseCase)
            }

            // Load waypoints
            coroutineScope.launch {
                val (waypointFiles, waypointChecks) = waypointFilesUseCase.loadWaypointFiles()
                loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)
            }

            // Plot saved task if available
            plotSavedTask(map)
            mapState.blueLocationOverlay?.bringToFront()

        } catch (e: Exception) {
            Log.e(TAG, " Error loading map data: ${e.message}", e)
        }
    }

    private fun plotSavedTask(map: MapLibreMap) {
        try {
            if (taskManager.currentTask.waypoints.isNotEmpty()) {
                Log.d(TAG, " Plotting saved task with ${taskManager.currentTask.waypoints.size} waypoints")
                taskManager.plotOnMap(map)
            } else {
                Log.d(TAG, " No saved task to plot")
            }
        } catch (e: Exception) {
            Log.e(TAG, " Error plotting saved task: ${e.message}", e)
        }
    }

    private fun setupOverlays(map: MapLibreMap) {
        try {
            // Initialize blue location overlay
            mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            mapState.blueLocationOverlay?.initialize()
            mapState.ognTrafficOverlay = OgnTrafficOverlay(map)
            mapState.ognTrafficOverlay?.initialize()
            mapState.adsbTrafficOverlay = AdsbTrafficOverlay(context, map)
            mapState.adsbTrafficOverlay?.initialize()
            snailTrailManager.initialize(map)
            setupScaleBar(map)
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


    private fun setupScaleBar(map: MapLibreMap) {
        val mapView = mapState.mapView ?: return
        mapView.post { updateScaleBar(map, forceCreate = true) }
        if (!scaleBarLayoutListenerInstalled) {
            scaleBarLayoutListenerInstalled = true
            mapView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    updateScaleBar(map, forceCreate = true)
                }
            }
        }
    }

    private fun updateScaleBar(map: MapLibreMap, forceCreate: Boolean = false) {
        val mapView = mapState.mapView ?: return
        val width = mapView.width
        val height = mapView.height
        if (width <= 0 || height <= 0) return

        val distancePerPixel = resolveDistancePerPixel(map, mapView)
        val sizeChanged = width != lastScaleBarWidth || height != lastScaleBarHeight
        val distanceChanged = kotlin.math.abs(distancePerPixel - lastScaleBarDistancePerPixel) > SCALE_BAR_DISTANCE_EPSILON
        if (!forceCreate && !sizeChanged && !distanceChanged && mapState.scaleBarWidget != null) {
            return
        }

        lastScaleBarWidth = width
        lastScaleBarHeight = height
        lastScaleBarDistancePerPixel = distancePerPixel
        applyMaxZoomPreference(map)

        val resources = mapView.resources
        val density = resources.displayMetrics.density
        val scaledDensity = density * resources.configuration.fontScale

        val textSizePx = SCALE_BAR_TEXT_SIZE_SP * scaledDensity
        val barHeightPx = SCALE_BAR_BAR_HEIGHT_DP * density
        val textBarMarginPx = SCALE_BAR_TEXT_MARGIN_DP * density
        val borderWidthPx = SCALE_BAR_BORDER_WIDTH_DP * density
        val textBorderWidthPx = SCALE_BAR_TEXT_BORDER_WIDTH_DP * density
        val textColorRes = android.R.color.black
        val primaryColorRes = android.R.color.black
        val secondaryColorRes = android.R.color.white
        val textColor = mapView.context.getColor(textColorRes)
        val primaryColor = mapView.context.getColor(primaryColorRes)
        val secondaryColor = mapView.context.getColor(secondaryColorRes)

        val contentHeightPx = textSizePx + textBarMarginPx + barHeightPx + borderWidthPx * 2f
        val marginTopPx = max(0f, (height - contentHeightPx) / 2f)
        val maxDistanceMeters = if (distancePerPixel.isFinite() && distancePerPixel > 0.0) {
            width * distancePerPixel * MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO
        } else {
            0.0
        }
        val scaleDistanceMeters = MapZoomConstraints.resolveScaleBarDistanceMeters(maxDistanceMeters)
        val barWidthPx = if (scaleDistanceMeters != null && distancePerPixel > 0.0) {
            (scaleDistanceMeters / distancePerPixel).toFloat()
        } else {
            width * MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO
        }
        val marginLeftPx = max(0f, (width - barWidthPx) / 2f)

        val plugin = mapState.scaleBarPlugin ?: ScaleBarPlugin(mapView, map).also {
            mapState.scaleBarPlugin = it
        }

        if (mapState.scaleBarWidget == null || sizeChanged || forceCreate) {
            val options = ScaleBarOptions(mapView.context)
                .setMetricUnit(true)
                .setRefreshInterval(SCALE_BAR_REFRESH_INTERVAL_MS)
                .setTextColor(textColorRes)
                .setPrimaryColor(primaryColorRes)
                .setSecondaryColor(secondaryColorRes)
                .setTextSize(textSizePx)
                .setBarHeight(barHeightPx)
                .setBorderWidth(borderWidthPx)
                .setTextBarMargin(textBarMarginPx)
                .setTextBorderWidth(textBorderWidthPx)
                .setShowTextBorder(true)
                .setMarginLeft(marginLeftPx)
                .setMarginTop(marginTopPx)
                .setMaxWidthRatio(MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO)

            mapState.scaleBarWidget = plugin.create(options).also { widget ->
                val params = widget.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                widget.layoutParams = params
                widget.visibility = View.VISIBLE
                widget.bringToFront()
            }
        } else {
            mapState.scaleBarWidget?.apply {
                setMetricUnit(true)
                setRefreshInterval(SCALE_BAR_REFRESH_INTERVAL_MS)
                setTextColor(textColor)
                setPrimaryColor(primaryColor)
                setSecondaryColor(secondaryColor)
                setTextSize(textSizePx)
                setBarHeight(barHeightPx)
                setBorderWidth(borderWidthPx)
                setTextBarMargin(textBarMarginPx)
                setTextBorderWidth(textBorderWidthPx)
                setShowTextBorder(true)
                setMarginLeft(marginLeftPx)
                setMarginTop(marginTopPx)
                setRatio(MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO)
                visibility = View.VISIBLE
                bringToFront()
                invalidate()
            }
        }

        plugin.setEnabled(true)
        mapView.invalidate()
    }

    private fun resolveDistancePerPixel(map: MapLibreMap, mapView: MapView): Double {
        val latitude = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(latitude)
        val pixelRatio = mapView.pixelRatio
        return if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
    }


    private fun applyMaxZoomPreference(map: MapLibreMap) {
        val mapView = mapState.mapView ?: return
        val width = mapView.width
        if (width <= 0) return
        val latitude = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(latitude)
        val pixelRatio = mapView.pixelRatio
        val distancePerPixel = if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
        val maxZoom = MapZoomConstraints.maxZoomForMinScaleMeters(
            widthPx = width,
            currentZoom = map.cameraPosition.zoom,
            distancePerPixel = distancePerPixel
        ) ?: return
        map.setMaxZoomPreference(maxZoom)
        if (map.cameraPosition.zoom > maxZoom + 1e-3) {
            map.moveCamera(CameraUpdateFactory.zoomTo(maxZoom))
        }
    }

    private fun setupGestures(map: MapLibreMap) {
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
                refreshWaypoints(map)
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
                updateScaleBar(map)
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
        stateActions.setShowReturnButton(true)
        stateActions.updateLastUserPanTime(System.currentTimeMillis())
        Log.d(TAG, "User interaction detected - return button shown")
    }

    private fun refreshWaypoints(map: MapLibreMap) {
        try {
            coroutineScope.launch {
                val (waypointFiles, waypointChecks) = waypointFilesUseCase.loadWaypointFiles()
                loadAndApplyWaypoints(context, map, waypointFiles, waypointChecks)
            }
        } catch (e: Exception) {
            Log.e(TAG, " Error refreshing waypoints: ${e.message}", e)
        }
    }

}





