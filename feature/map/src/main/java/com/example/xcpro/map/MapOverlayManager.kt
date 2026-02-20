package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.clampForecastOpacity
import com.example.xcpro.forecast.clampForecastWindOverlayScale
import com.example.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.clampOgnIconSizePx
import com.example.xcpro.weather.rain.WEATHER_RAIN_OPACITY_DEFAULT
import com.example.xcpro.weather.rain.WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX
import com.example.xcpro.weather.rain.WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.clampWeatherRainOpacity
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Centralized overlay management for MapScreen. Handles distance circles, airspace, waypoints,
 * and task plotting interactions through TaskRenderSyncCoordinator.
 */
class MapOverlayManager(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val taskWaypointCountProvider: () -> Int,
    private val stateActions: MapStateActions,
    private val snailTrailManager: SnailTrailManager,
    private val coroutineScope: CoroutineScope,
    private val airspaceUseCase: AirspaceUseCase,
    private val waypointFilesUseCase: WaypointFilesUseCase
) {
    companion object {
        private const val TAG = "MapOverlayManager"
    }

    private var latestOgnTargets: List<OgnTrafficTarget> = emptyList()
    private var latestOgnThermalHotspots: List<OgnThermalHotspot> = emptyList()
    private var latestOgnGliderTrailSegments: List<OgnGliderTrailSegment> = emptyList()
    private var latestAdsbTargets: List<AdsbTrafficUiModel> = emptyList()
    private var ognIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
    private var adsbIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
    private var forecastOverlayEnabled: Boolean = false
    private var forecastSecondaryPrimaryOverlayEnabled: Boolean = false
    private var forecastWindOverlayEnabled: Boolean = false
    private var latestForecastPrimaryTileSpec: ForecastTileSpec? = null
    private var latestForecastPrimaryLegend: ForecastLegendSpec? = null
    private var latestForecastSecondaryPrimaryTileSpec: ForecastTileSpec? = null
    private var latestForecastSecondaryPrimaryLegend: ForecastLegendSpec? = null
    private var latestForecastWindTileSpec: ForecastTileSpec? = null
    private var latestForecastWindLegend: ForecastLegendSpec? = null
    private var forecastOpacity: Float = FORECAST_OPACITY_DEFAULT
    private var forecastWindOverlayScale: Float = FORECAST_WIND_OVERLAY_SCALE_DEFAULT
    private var forecastWindDisplayMode: ForecastWindDisplayMode = FORECAST_WIND_DISPLAY_MODE_DEFAULT
    private var weatherRainEnabled: Boolean = false
    private var weatherRainOpacity: Float = WEATHER_RAIN_OPACITY_DEFAULT
    private var weatherRainTransitionDurationMs: Long = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
    private var weatherRainFrameSelection: WeatherRainFrameSelection? = null
    private var weatherRainStatusCode: WeatherRadarStatusCode = WeatherRadarStatusCode.NO_METADATA
    private var weatherRainStale: Boolean = true
    private var lastWeatherRainConfig: WeatherRainRuntimeConfig? = null

    fun toggleDistanceCircles() {
        stateActions.toggleDistanceCircles()
        val next = mapStateReader.showDistanceCircles.value
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Distance circles toggled: $next (Canvas overlay is active)"
            )
        }
    }

    fun refreshAirspace(map: MapLibreMap?) {
        try {
            coroutineScope.launch {
                loadAndApplyAirspace(map, airspaceUseCase)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Airspace overlays refreshed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing airspace: ${e.message}", e)
        }
    }

    fun refreshWaypoints(map: MapLibreMap?) {
        try {
            coroutineScope.launch {
                val (files, checks) = waypointFilesUseCase.loadWaypointFiles()
                loadAndApplyWaypoints(context, map, files, checks)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Waypoint overlays refreshed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing waypoints: ${e.message}", e)
        }
    }

    fun plotSavedTask(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Refreshing task overlays for ${taskWaypointCountProvider()} waypoints"
                )
            }
            taskRenderSyncCoordinator.onOverlayRefresh(map)
        } catch (e: Exception) {
            Log.e(TAG, "Error plotting saved task: ${e.message}", e)
        }
    }

    fun clearTaskOverlays(map: MapLibreMap?) {
        try {
            taskRenderSyncCoordinator.clearTaskVisuals(map)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Task overlays cleared (handled by TaskManager)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing task overlays: ${e.message}", e)
        }
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Map style changed, reloading overlays")
            }
            refreshAirspace(map)
            refreshWaypoints(map)
            taskRenderSyncCoordinator.onMapStyleChanged(map)
            if (mapState.blueLocationOverlay == null && map != null) {
                Log.d(TAG, "Blue location overlay missing after style change; creating now")
                mapState.blueLocationOverlay = BlueLocationOverlay(context, map)
            }
            mapState.blueLocationOverlay?.initialize()
            mapState.blueLocationOverlay?.let {
                Log.d(TAG, "Blue location overlay initialized via style change")
            }
            if (map != null) {
                mapState.ognTrafficOverlay?.cleanup()
                mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
                mapState.ognTrafficOverlay?.initialize()
                mapState.ognTrafficOverlay?.render(latestOgnTargets)

                mapState.ognThermalOverlay?.cleanup()
                mapState.ognThermalOverlay = createOgnThermalOverlay(map)
                mapState.ognThermalOverlay?.initialize()
                mapState.ognThermalOverlay?.render(latestOgnThermalHotspots)

                mapState.ognGliderTrailOverlay?.cleanup()
                mapState.ognGliderTrailOverlay = createOgnGliderTrailOverlay(map)
                mapState.ognGliderTrailOverlay?.initialize()
                mapState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)

                mapState.adsbTrafficOverlay?.cleanup()
                mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
                mapState.adsbTrafficOverlay?.initialize()
                mapState.adsbTrafficOverlay?.render(latestAdsbTargets)

                mapState.forecastOverlay?.cleanup()
                mapState.forecastSecondaryOverlay?.cleanup()
                mapState.forecastWindOverlay?.cleanup()
                mapState.forecastOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "primary"
                )
                mapState.forecastSecondaryOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "secondary"
                )
                mapState.forecastWindOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "wind"
                )
                reapplyForecastOverlay(map)

                mapState.weatherRainOverlay?.cleanup()
                mapState.weatherRainOverlay = WeatherRainOverlay(map)
                reapplyWeatherRainOverlay(map)
            }
            snailTrailManager.onMapStyleChanged(map)
            mapState.blueLocationOverlay?.bringToFront()
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "All overlays reloaded for new style")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling map style change: ${e.message}", e)
        }
    }

    fun initializeOverlays(map: MapLibreMap?) {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Initializing map overlays")
            }
            refreshAirspace(map)
            refreshWaypoints(map)
            taskRenderSyncCoordinator.onOverlayRefresh(map)
            if (map != null) {
                mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
                mapState.ognTrafficOverlay?.initialize()
                mapState.ognTrafficOverlay?.render(latestOgnTargets)

                mapState.ognThermalOverlay = createOgnThermalOverlay(map)
                mapState.ognThermalOverlay?.initialize()
                mapState.ognThermalOverlay?.render(latestOgnThermalHotspots)

                mapState.ognGliderTrailOverlay = createOgnGliderTrailOverlay(map)
                mapState.ognGliderTrailOverlay?.initialize()
                mapState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)

                mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
                mapState.adsbTrafficOverlay?.initialize()
                mapState.adsbTrafficOverlay?.render(latestAdsbTargets)

                mapState.forecastOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "primary"
                )
                mapState.forecastSecondaryOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "secondary"
                )
                mapState.forecastWindOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "wind"
                )
                reapplyForecastOverlay(map)

                mapState.weatherRainOverlay = WeatherRainOverlay(map)
                reapplyWeatherRainOverlay(map)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Map overlays initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing overlays: ${e.message}", e)
        }
    }

    fun requestTaskRenderSync() {
        taskRenderSyncCoordinator.onTaskMutation()
    }

    fun onTaskStateChanged(signature: TaskRenderSyncCoordinator.TaskStateSignature) {
        taskRenderSyncCoordinator.onTaskStateChanged(signature)
    }

    fun updateOgnTrafficTargets(targets: List<OgnTrafficTarget>) {
        latestOgnTargets = targets
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognTrafficOverlay == null) {
            mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
        }
        mapState.ognTrafficOverlay?.render(targets)
    }

    fun updateOgnThermalHotspots(hotspots: List<OgnThermalHotspot>) {
        latestOgnThermalHotspots = hotspots
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognThermalOverlay == null) {
            mapState.ognThermalOverlay = createOgnThermalOverlay(map)
        }
        mapState.ognThermalOverlay?.render(hotspots)
    }

    fun updateOgnGliderTrailSegments(segments: List<OgnGliderTrailSegment>) {
        latestOgnGliderTrailSegments = segments
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognGliderTrailOverlay == null) {
            mapState.ognGliderTrailOverlay = createOgnGliderTrailOverlay(map)
        }
        mapState.ognGliderTrailOverlay?.render(segments)
    }

    fun updateAdsbTrafficTargets(targets: List<AdsbTrafficUiModel>) {
        latestAdsbTargets = targets
        val map = mapState.mapLibreMap ?: return
        if (mapState.adsbTrafficOverlay == null) {
            mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
        }
        mapState.adsbTrafficOverlay?.render(targets)
    }

    fun setForecastOverlay(
        enabled: Boolean,
        primaryTileSpec: ForecastTileSpec?,
        primaryLegendSpec: ForecastLegendSpec?,
        secondaryPrimaryOverlayEnabled: Boolean,
        secondaryPrimaryTileSpec: ForecastTileSpec?,
        secondaryPrimaryLegendSpec: ForecastLegendSpec?,
        windOverlayEnabled: Boolean,
        windTileSpec: ForecastTileSpec?,
        windLegendSpec: ForecastLegendSpec?,
        opacity: Float,
        windOverlayScale: Float,
        windDisplayMode: ForecastWindDisplayMode
    ) {
        val primaryOverlayEnabled = enabled
        forecastOverlayEnabled = primaryOverlayEnabled || windOverlayEnabled
        forecastSecondaryPrimaryOverlayEnabled = primaryOverlayEnabled && secondaryPrimaryOverlayEnabled
        forecastWindOverlayEnabled = windOverlayEnabled
        latestForecastPrimaryTileSpec = primaryTileSpec
        latestForecastPrimaryLegend = primaryLegendSpec
        latestForecastSecondaryPrimaryTileSpec = secondaryPrimaryTileSpec
        latestForecastSecondaryPrimaryLegend = secondaryPrimaryLegendSpec
        latestForecastWindTileSpec = windTileSpec
        latestForecastWindLegend = windLegendSpec
        forecastOpacity = clampForecastOpacity(opacity)
        forecastWindOverlayScale = clampForecastWindOverlayScale(windOverlayScale)
        forecastWindDisplayMode = windDisplayMode
        val map = mapState.mapLibreMap ?: return
        if (!forecastOverlayEnabled) {
            mapState.forecastOverlay?.clear()
            mapState.forecastSecondaryOverlay?.clear()
            mapState.forecastWindOverlay?.clear()
            return
        }
        if (mapState.forecastOverlay == null) {
            mapState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
            )
        }
        if (mapState.forecastSecondaryOverlay == null) {
            mapState.forecastSecondaryOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "secondary"
            )
        }
        if (mapState.forecastWindOverlay == null) {
            mapState.forecastWindOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "wind"
            )
        }
        if (primaryOverlayEnabled && primaryTileSpec != null) {
            mapState.forecastOverlay?.render(
                tileSpec = primaryTileSpec,
                opacity = forecastOpacity,
                windOverlayScale = forecastWindOverlayScale,
                windDisplayMode = forecastWindDisplayMode,
                legendSpec = primaryLegendSpec
            )
        } else {
            mapState.forecastOverlay?.clear()
        }
        if (forecastSecondaryPrimaryOverlayEnabled && secondaryPrimaryTileSpec != null) {
            mapState.forecastSecondaryOverlay?.render(
                tileSpec = secondaryPrimaryTileSpec,
                opacity = forecastOpacity,
                windOverlayScale = forecastWindOverlayScale,
                windDisplayMode = forecastWindDisplayMode,
                legendSpec = secondaryPrimaryLegendSpec
            )
        } else {
            mapState.forecastSecondaryOverlay?.clear()
        }
        if (windOverlayEnabled && windTileSpec != null) {
            mapState.forecastWindOverlay?.render(
                tileSpec = windTileSpec,
                opacity = forecastOpacity,
                windOverlayScale = forecastWindOverlayScale,
                windDisplayMode = forecastWindDisplayMode,
                legendSpec = windLegendSpec
            )
        } else {
            mapState.forecastWindOverlay?.clear()
        }
    }

    fun clearForecastOverlay() {
        forecastOverlayEnabled = false
        forecastSecondaryPrimaryOverlayEnabled = false
        forecastWindOverlayEnabled = false
        latestForecastPrimaryTileSpec = null
        latestForecastPrimaryLegend = null
        latestForecastSecondaryPrimaryTileSpec = null
        latestForecastSecondaryPrimaryLegend = null
        latestForecastWindTileSpec = null
        latestForecastWindLegend = null
        mapState.forecastOverlay?.clear()
        mapState.forecastSecondaryOverlay?.clear()
        mapState.forecastWindOverlay?.clear()
    }

    fun setWeatherRainOverlay(
        enabled: Boolean,
        frameSelection: WeatherRainFrameSelection?,
        opacity: Float,
        transitionDurationMs: Long,
        statusCode: WeatherRadarStatusCode,
        stale: Boolean
    ) {
        val resolvedOpacity = clampWeatherRainOpacity(opacity)
        val resolvedTransitionDurationMs = transitionDurationMs.coerceAtLeast(0L)

        weatherRainEnabled = enabled
        weatherRainFrameSelection = frameSelection
        weatherRainOpacity = resolvedOpacity
        weatherRainTransitionDurationMs = resolvedTransitionDurationMs
        weatherRainStatusCode = statusCode
        weatherRainStale = stale

        val nextConfig = WeatherRainRuntimeConfig(
            enabled = enabled,
            frameSelection = frameSelection,
            opacity = resolvedOpacity,
            transitionDurationMs = resolvedTransitionDurationMs,
            stale = stale
        )
        if (nextConfig == lastWeatherRainConfig) return
        lastWeatherRainConfig = nextConfig

        val map = mapState.mapLibreMap ?: return
        applyWeatherRainOverlay(map, nextConfig)
    }

    fun clearWeatherRainOverlay() {
        weatherRainEnabled = false
        weatherRainFrameSelection = null
        weatherRainOpacity = WEATHER_RAIN_OPACITY_DEFAULT
        weatherRainTransitionDurationMs = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
        weatherRainStatusCode = WeatherRadarStatusCode.NO_METADATA
        weatherRainStale = true
        lastWeatherRainConfig = null
        mapState.weatherRainOverlay?.clear()
    }

    fun reapplyWeatherRainOverlay() {
        val map = mapState.mapLibreMap ?: return
        reapplyWeatherRainOverlay(map)
    }

    fun reapplyForecastOverlay() {
        val map = mapState.mapLibreMap ?: return
        reapplyForecastOverlay(map)
    }

    fun setAdsbIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        adsbIconSizePx = clamped
        if (mapState.adsbTrafficOverlay == null) {
            val map = mapState.mapLibreMap
            if (map != null) {
                mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
            }
        }
        mapState.adsbTrafficOverlay?.setIconSizePx(clamped)
    }

    fun setOgnIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        ognIconSizePx = clamped
        if (mapState.ognTrafficOverlay == null) {
            val map = mapState.mapLibreMap
            if (map != null) {
                mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
            }
        }
        mapState.ognTrafficOverlay?.setIconSizePx(clamped)
    }

    private fun createOgnTrafficOverlay(map: MapLibreMap): OgnTrafficOverlay =
        OgnTrafficOverlay(
            context = context,
            map = map,
            initialIconSizePx = ognIconSizePx
        )

    private fun createOgnThermalOverlay(map: MapLibreMap): OgnThermalOverlay =
        OgnThermalOverlay(map = map)

    private fun createOgnGliderTrailOverlay(map: MapLibreMap): OgnGliderTrailOverlay =
        OgnGliderTrailOverlay(map = map)

    private fun createAdsbTrafficOverlay(map: MapLibreMap): AdsbTrafficOverlay =
        AdsbTrafficOverlay(
            context = context,
            map = map,
            initialIconSizePx = adsbIconSizePx
        )

    fun findAdsbTargetAt(tap: LatLng): Icao24? {
        return mapState.adsbTrafficOverlay?.findTargetAt(tap)
    }

    fun findOgnTargetAt(tap: LatLng): String? {
        return mapState.ognTrafficOverlay?.findTargetAt(tap)
    }

    fun findOgnThermalHotspotAt(tap: LatLng): String? {
        return mapState.ognThermalOverlay?.findTargetAt(tap)
    }

    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? {
        if (!forecastOverlayEnabled) return null
        if (!forecastWindOverlayEnabled) return null
        val tileSpec = latestForecastWindTileSpec ?: return null
        if (tileSpec.format != ForecastTileFormat.VECTOR_WIND_POINTS) return null
        if (forecastWindDisplayMode != ForecastWindDisplayMode.ARROW) return null
        return mapState.forecastWindOverlay?.findWindArrowSpeedAt(tap)
    }

    fun onZoomChanged(map: MapLibreMap?) {
        try {
            refreshWaypoints(map)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Waypoints refreshed for zoom change")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling zoom change: ${e.message}", e)
        }
    }

    fun getOverlayStatus(): String {
        return buildString {
            append("MapOverlayManager Status:\n")
            append("- Distance Circles: ${mapStateReader.showDistanceCircles.value}\n")
            append(
                "- Distance Circles Overlay: ${
                    if (mapState.distanceCirclesOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- Blue Location Overlay: ${
                    if (mapState.blueLocationOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- OGN Traffic Overlay: ${
                    if (mapState.ognTrafficOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- OGN Targets: ${latestOgnTargets.size}\n")
            append(
                "- OGN Thermal Overlay: ${
                    if (mapState.ognThermalOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- OGN Thermal Hotspots: ${latestOgnThermalHotspots.size}\n")
            append(
                "- OGN Glider Trail Overlay: ${
                    if (mapState.ognGliderTrailOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- OGN Glider Trail Segments: ${latestOgnGliderTrailSegments.size}\n")
            append(
                "- ADS-B Traffic Overlay: ${
                    if (mapState.adsbTrafficOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- ADS-B Targets: ${latestAdsbTargets.size}\n")
            append("- Forecast Overlay Enabled: $forecastOverlayEnabled\n")
            append("- Forecast Secondary Overlay Enabled: $forecastSecondaryPrimaryOverlayEnabled\n")
            append("- Forecast Wind Overlay Enabled: $forecastWindOverlayEnabled\n")
            append(
                "- Forecast Raster Overlay: ${
                    if (mapState.forecastOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- Forecast Secondary Overlay: ${
                    if (mapState.forecastSecondaryOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- Forecast Wind Overlay: ${
                    if (mapState.forecastWindOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- Weather Rain Enabled: $weatherRainEnabled\n")
            append("- Weather Rain Status: $weatherRainStatusCode\n")
            append("- Weather Rain Stale: $weatherRainStale\n")
            append("- Weather Rain Frame Selected: ${weatherRainFrameSelection != null}\n")
            append("- Weather Rain Transition Duration Ms: $weatherRainTransitionDurationMs\n")
            append(
                "- Weather Rain Overlay: ${
                    if (mapState.weatherRainOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- Task Waypoints: ${taskWaypointCountProvider()}\n")
        }
    }

    private fun reapplyForecastOverlay(map: MapLibreMap) {
        if (!forecastOverlayEnabled) {
            mapState.forecastOverlay?.clear()
            mapState.forecastSecondaryOverlay?.clear()
            mapState.forecastWindOverlay?.clear()
            return
        }
        if (mapState.forecastOverlay == null) {
            mapState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
            )
        }
        if (mapState.forecastSecondaryOverlay == null) {
            mapState.forecastSecondaryOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "secondary"
            )
        }
        if (mapState.forecastWindOverlay == null) {
            mapState.forecastWindOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "wind"
            )
        }
        val primaryTileSpec = latestForecastPrimaryTileSpec
        if (primaryTileSpec != null) {
            mapState.forecastOverlay?.render(
                tileSpec = primaryTileSpec,
                opacity = forecastOpacity,
                windOverlayScale = forecastWindOverlayScale,
                windDisplayMode = forecastWindDisplayMode,
                legendSpec = latestForecastPrimaryLegend
            )
        } else {
            mapState.forecastOverlay?.clear()
        }
        val secondaryPrimaryTileSpec = latestForecastSecondaryPrimaryTileSpec
        if (forecastSecondaryPrimaryOverlayEnabled && secondaryPrimaryTileSpec != null) {
            mapState.forecastSecondaryOverlay?.render(
                tileSpec = secondaryPrimaryTileSpec,
                opacity = forecastOpacity,
                windOverlayScale = forecastWindOverlayScale,
                windDisplayMode = forecastWindDisplayMode,
                legendSpec = latestForecastSecondaryPrimaryLegend
            )
        } else {
            mapState.forecastSecondaryOverlay?.clear()
        }
        val windTileSpec = latestForecastWindTileSpec
        if (forecastWindOverlayEnabled && windTileSpec != null) {
            mapState.forecastWindOverlay?.render(
                tileSpec = windTileSpec,
                opacity = forecastOpacity,
                windOverlayScale = forecastWindOverlayScale,
                windDisplayMode = forecastWindDisplayMode,
                legendSpec = latestForecastWindLegend
            )
        } else {
            mapState.forecastWindOverlay?.clear()
        }
    }

    private fun reapplyWeatherRainOverlay(map: MapLibreMap) {
        val config = WeatherRainRuntimeConfig(
            enabled = weatherRainEnabled,
            frameSelection = weatherRainFrameSelection,
            opacity = weatherRainOpacity,
            transitionDurationMs = weatherRainTransitionDurationMs,
            stale = weatherRainStale
        )
        applyWeatherRainOverlay(map, config)
    }

    private fun applyWeatherRainOverlay(
        map: MapLibreMap,
        config: WeatherRainRuntimeConfig
    ) {
        val frameSelection = config.frameSelection
        if (!config.enabled || frameSelection == null) {
            mapState.weatherRainOverlay?.clear()
            return
        }
        if (mapState.weatherRainOverlay == null) {
            mapState.weatherRainOverlay = WeatherRainOverlay(map)
        }
        val effectiveOpacity = if (config.stale) {
            minOf(config.opacity, WEATHER_RAIN_STALE_DIMMED_OPACITY_MAX)
        } else {
            config.opacity
        }
        runCatching {
            mapState.weatherRainOverlay?.render(
                frameSelection = frameSelection,
                opacity = effectiveOpacity,
                transitionDurationMs = config.transitionDurationMs
            )
        }.onFailure { throwable ->
            Log.e(TAG, "Weather rain overlay apply failed: ${throwable.message}", throwable)
        }
    }

    private data class WeatherRainRuntimeConfig(
        val enabled: Boolean,
        val frameSelection: WeatherRainFrameSelection?,
        val opacity: Float,
        val transitionDurationMs: Long,
        val stale: Boolean
    )
}
