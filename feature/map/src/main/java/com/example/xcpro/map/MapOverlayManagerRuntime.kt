package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.forecast.FORECAST_OPACITY_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_DEFAULT
import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.clampForecastOpacity
import com.example.xcpro.forecast.clampForecastWindOverlayScale
import com.example.xcpro.forecast.FORECAST_WIND_DISPLAY_MODE_DEFAULT
import com.example.xcpro.forecast.clampSkySightSatelliteHistoryFrames
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OgnDisplayUpdateMode
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

open class MapOverlayManagerRuntime(
    private val context: Context,
    private val mapState: MapScreenState,
    private val mapStateReader: MapStateReader,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val taskWaypointCountProvider: () -> Int,
    private val stateActions: MapStateActions,
    private val snailTrailManager: SnailTrailManager,
    private val coroutineScope: CoroutineScope,
    private val airspaceUseCase: AirspaceUseCase,
    private val waypointFilesUseCase: WaypointFilesUseCase,
    private val ognTrafficOverlayFactory: (MapLibreMap, Int, Boolean) -> OgnTrafficOverlay =
        { map, iconSizePx, useSatelliteContrastIcons ->
            OgnTrafficOverlay(
                context = context,
                map = map,
                initialIconSizePx = iconSizePx,
                initialUseSatelliteContrastIcons = useSatelliteContrastIcons
            )
        },
    private val ognThermalOverlayFactory: (MapLibreMap) -> OgnThermalOverlay =
        { map -> OgnThermalOverlay(map = map) },
    private val ognGliderTrailOverlayFactory: (MapLibreMap) -> OgnGliderTrailOverlay =
        { map -> OgnGliderTrailOverlay(map = map) },
    private val adsbTrafficOverlayFactory: (MapLibreMap, Int) -> AdsbTrafficOverlay =
        { map, iconSizePx ->
            AdsbTrafficOverlay(
                context = context,
                map = map,
                initialIconSizePx = iconSizePx
            )
        }
) {
    companion object {
        private const val TAG = "MapOverlayManager"
        private const val OWN_ALTITUDE_RENDER_RESOLUTION_SCALE = 10.0
    }

    private var latestOgnTargets: List<OgnTrafficTarget> = emptyList()
    private var latestOgnOwnshipAltitudeMeters: Double? = null
    private var latestOgnAltitudeUnit: AltitudeUnit = AltitudeUnit.METERS
    private var latestOgnUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var latestOgnThermalHotspots: List<OgnThermalHotspot> = emptyList()
    private var latestOgnGliderTrailSegments: List<OgnGliderTrailSegment> = emptyList()
    private var latestAdsbTargets: List<AdsbTrafficUiModel> = emptyList()
    private var latestAdsbOwnshipAltitudeMeters: Double? = null
    private var latestAdsbUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var ognIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
    private var ognDisplayUpdateMode: OgnDisplayUpdateMode = OgnDisplayUpdateMode.DEFAULT
    private var ognSatelliteContrastIconsEnabled: Boolean = false
    private var adsbIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
    private var forecastOverlayEnabled: Boolean = false
    private var forecastWindOverlayEnabled: Boolean = false
    private var latestForecastPrimaryTileSpec: ForecastTileSpec? = null
    private var latestForecastPrimaryLegend: ForecastLegendSpec? = null
    private var latestForecastWindTileSpec: ForecastTileSpec? = null
    private var latestForecastWindLegend: ForecastLegendSpec? = null
    private var forecastOpacity: Float = FORECAST_OPACITY_DEFAULT
    private var forecastWindOverlayScale: Float = FORECAST_WIND_OVERLAY_SCALE_DEFAULT
    private var forecastWindDisplayMode: ForecastWindDisplayMode = FORECAST_WIND_DISPLAY_MODE_DEFAULT
    private var skySightSatelliteEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
    private var skySightSatelliteImageryEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
    private var skySightSatelliteRadarEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
    private var skySightSatelliteLightningEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
    private var skySightSatelliteAnimateEnabled: Boolean = FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
    private var skySightSatelliteHistoryFrames: Int = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
    private var skySightSatelliteReferenceTimeUtcMs: Long? = null
    private var lastSkySightSatelliteConfig: SkySightSatelliteRuntimeConfig? = null
    private var weatherRainEnabled: Boolean = false
    private var weatherRainOpacity: Float = WEATHER_RAIN_OPACITY_DEFAULT
    private var weatherRainTransitionDurationMs: Long = WEATHER_RAIN_TRANSITION_DURATION_BALANCED_MS
    private var weatherRainFrameSelection: WeatherRainFrameSelection? = null
    private var weatherRainStatusCode: WeatherRadarStatusCode = WeatherRadarStatusCode.NO_METADATA
    private var weatherRainStale: Boolean = true
    private var lastWeatherRainConfig: WeatherRainRuntimeConfig? = null
    private val _forecastRuntimeWarningMessage = MutableStateFlow<String?>(null)
    val forecastRuntimeWarningMessage: StateFlow<String?> = _forecastRuntimeWarningMessage.asStateFlow()
    private val _skySightSatelliteRuntimeErrorMessage = MutableStateFlow<String?>(null)
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        _skySightSatelliteRuntimeErrorMessage.asStateFlow()
    private var refreshAirspaceJob: Job? = null
    private val refreshAirspaceRequestId = AtomicLong(0L)
    private val ognTrafficRenderState = OgnRenderThrottleState()
    private val ognThermalRenderState = OgnRenderThrottleState()
    private val ognTrailRenderState = OgnRenderThrottleState()

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
        val requestId = refreshAirspaceRequestId.incrementAndGet()
        refreshAirspaceJob?.cancel()
        refreshAirspaceJob = coroutineScope.launch {
            try {
                loadAndApplyAirspace(map, airspaceUseCase)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Airspace overlays refreshed (requestId=$requestId)")
                }
            } catch (cancellation: CancellationException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Airspace refresh canceled (requestId=$requestId)")
                }
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing airspace: ${e.message}", e)
            } finally {
                if (refreshAirspaceRequestId.get() == requestId) {
                    refreshAirspaceJob = null
                }
            }
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
                initializeTrafficOverlays(map)

                mapState.forecastOverlay?.cleanup()
                mapState.forecastWindOverlay?.cleanup()
                mapState.forecastOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "primary"
                )
                mapState.forecastWindOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "wind"
                )
                reapplyForecastOverlay(map)

                mapState.skySightSatelliteOverlay?.cleanup()
                mapState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
                reapplySkySightSatelliteOverlay(map)

                mapState.weatherRainOverlay?.cleanup()
                mapState.weatherRainOverlay = WeatherRainOverlay(map)
                reapplyWeatherRainOverlay(map)
            }
            snailTrailManager.onMapStyleChanged(map)
            mapState.blueLocationOverlay?.bringToFront()
            bringTrafficOverlaysToFront()
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
                initializeTrafficOverlays(map)

                mapState.forecastOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "primary"
                )
                mapState.forecastWindOverlay = ForecastRasterOverlay(
                    map = map,
                    idNamespace = "wind"
                )
                reapplyForecastOverlay(map)

                mapState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
                reapplySkySightSatelliteOverlay(map)

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

    fun initializeTrafficOverlays(map: MapLibreMap?) {
        if (map == null) return
        cancelOgnPendingRenders()
        mapState.ognTrafficOverlay?.cleanup()
        mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
        mapState.ognTrafficOverlay?.initialize()
        mapState.ognTrafficOverlay?.render(
            targets = latestOgnTargets,
            ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
            altitudeUnit = latestOgnAltitudeUnit,
            unitsPreferences = latestOgnUnitsPreferences
        )

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
        mapState.adsbTrafficOverlay?.render(
            targets = latestAdsbTargets,
            ownshipAltitudeMeters = latestAdsbOwnshipAltitudeMeters,
            unitsPreferences = latestAdsbUnitsPreferences
        )
        bringTrafficOverlaysToFront()
        val nowMonoMs = TimeBridge.nowMonoMs()
        ognTrafficRenderState.lastRenderMonoMs = nowMonoMs
        ognThermalRenderState.lastRenderMonoMs = nowMonoMs
        ognTrailRenderState.lastRenderMonoMs = nowMonoMs
    }

    fun requestTaskRenderSync() {
        taskRenderSyncCoordinator.onTaskMutation()
    }

    fun onTaskStateChanged(signature: TaskRenderSyncCoordinator.TaskStateSignature) {
        taskRenderSyncCoordinator.onTaskStateChanged(signature)
    }

    fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        if (ognDisplayUpdateMode == mode) return
        ognDisplayUpdateMode = mode
        cancelOgnPendingRenders()
        renderOgnTargetsNow()
        renderOgnThermalsNow()
        renderOgnTrailsNow()
        val nowMonoMs = TimeBridge.nowMonoMs()
        ognTrafficRenderState.lastRenderMonoMs = nowMonoMs
        ognThermalRenderState.lastRenderMonoMs = nowMonoMs
        ognTrailRenderState.lastRenderMonoMs = nowMonoMs
    }

    fun onMapDetached() {
        refreshAirspaceJob?.cancel()
        refreshAirspaceJob = null
        cancelOgnPendingRenders()
        _forecastRuntimeWarningMessage.value = null
        _skySightSatelliteRuntimeErrorMessage.value = null
    }

    fun updateOgnTrafficTargets(
        targets: List<OgnTrafficTarget>,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences = UnitsPreferences(),
        forceImmediate: Boolean = false
    ) {
        val normalizedOwnshipAltitude = normalizeOwnshipAltitudeForRender(ownshipAltitudeMeters)
        val sameTargets = latestOgnTargets == targets
        val sameOwnshipAltitude = latestOgnOwnshipAltitudeMeters == normalizedOwnshipAltitude
        val sameAltitudeUnit = latestOgnAltitudeUnit == altitudeUnit
        val sameUnitsPreferences = latestOgnUnitsPreferences == unitsPreferences
        if (sameTargets &&
            sameOwnshipAltitude &&
            sameAltitudeUnit &&
            sameUnitsPreferences &&
            !forceImmediate &&
            mapState.ognTrafficOverlay != null
        ) {
            return
        }
        latestOgnTargets = targets
        latestOgnOwnshipAltitudeMeters = normalizedOwnshipAltitude
        latestOgnAltitudeUnit = altitudeUnit
        latestOgnUnitsPreferences = unitsPreferences
        scheduleOgnRender(
            state = ognTrafficRenderState,
            forceImmediate = forceImmediate || targets.isEmpty()
        ) {
            renderOgnTargetsNow()
        }
    }

    fun updateOgnThermalHotspots(hotspots: List<OgnThermalHotspot>, forceImmediate: Boolean = false) {
        val sameHotspots = latestOgnThermalHotspots == hotspots
        if (sameHotspots && !forceImmediate && mapState.ognThermalOverlay != null) return
        latestOgnThermalHotspots = hotspots
        scheduleOgnRender(
            state = ognThermalRenderState,
            forceImmediate = forceImmediate || hotspots.isEmpty()
        ) {
            renderOgnThermalsNow()
        }
    }

    fun updateOgnGliderTrailSegments(segments: List<OgnGliderTrailSegment>, forceImmediate: Boolean = false) {
        val sameSegments = latestOgnGliderTrailSegments == segments
        if (sameSegments && !forceImmediate && mapState.ognGliderTrailOverlay != null) return
        latestOgnGliderTrailSegments = segments
        scheduleOgnRender(
            state = ognTrailRenderState,
            forceImmediate = forceImmediate || segments.isEmpty()
        ) {
            renderOgnTrailsNow()
        }
    }

    private fun renderOgnTargetsNow() {
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognTrafficOverlay == null) {
            mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
            mapState.ognTrafficOverlay?.initialize()
        }
        mapState.ognTrafficOverlay?.render(
            targets = latestOgnTargets,
            ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
            altitudeUnit = latestOgnAltitudeUnit,
            unitsPreferences = latestOgnUnitsPreferences
        )
    }

    private fun renderOgnThermalsNow() {
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognThermalOverlay == null) {
            mapState.ognThermalOverlay = createOgnThermalOverlay(map)
            mapState.ognThermalOverlay?.initialize()
        }
        runCatching {
            mapState.ognThermalOverlay?.render(latestOgnThermalHotspots)
        }.onFailure { throwable ->
            Log.e(TAG, "OGN thermal overlay render failed: ${throwable.message}", throwable)
        }
    }

    private fun renderOgnTrailsNow() {
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognGliderTrailOverlay == null) {
            mapState.ognGliderTrailOverlay = createOgnGliderTrailOverlay(map)
            mapState.ognGliderTrailOverlay?.initialize()
        }
        runCatching {
            mapState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to render OGN glider trails: ${throwable.message}", throwable)
        }
    }

    private fun scheduleOgnRender(
        state: OgnRenderThrottleState,
        forceImmediate: Boolean,
        renderNow: () -> Unit
    ) {
        val map = mapState.mapLibreMap ?: return
        val intervalMs = ognDisplayUpdateMode.renderIntervalMs

        if (forceImmediate || intervalMs <= 0L) {
            state.pendingJob?.cancel()
            state.pendingJob = null
            renderNow()
            state.lastRenderMonoMs = TimeBridge.nowMonoMs()
            return
        }

        val nowMonoMs = TimeBridge.nowMonoMs()
        val elapsedMs = nowMonoMs - state.lastRenderMonoMs
        if (elapsedMs >= intervalMs && state.pendingJob == null) {
            renderNow()
            state.lastRenderMonoMs = nowMonoMs
            return
        }

        if (state.pendingJob != null) return

        val remainingMs = (intervalMs - elapsedMs).coerceAtLeast(0L)
        state.pendingJob = coroutineScope.launch {
            delay(remainingMs)
            state.pendingJob = null
            if (mapState.mapLibreMap != map || mapState.mapLibreMap == null) return@launch
            renderNow()
            state.lastRenderMonoMs = TimeBridge.nowMonoMs()
        }
    }

    private fun cancelOgnPendingRenders() {
        ognTrafficRenderState.pendingJob?.cancel()
        ognTrafficRenderState.pendingJob = null
        ognThermalRenderState.pendingJob?.cancel()
        ognThermalRenderState.pendingJob = null
        ognTrailRenderState.pendingJob?.cancel()
        ognTrailRenderState.pendingJob = null
    }

    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) {
        val normalizedOwnshipAltitude = normalizeOwnshipAltitudeForRender(ownshipAltitudeMeters)
        val sameTargets = latestAdsbTargets == targets
        val sameOwnshipAltitude = latestAdsbOwnshipAltitudeMeters == normalizedOwnshipAltitude
        val sameUnitsPreferences = latestAdsbUnitsPreferences == unitsPreferences
        if (sameTargets &&
            sameOwnshipAltitude &&
            sameUnitsPreferences &&
            mapState.adsbTrafficOverlay != null
        ) {
            return
        }
        latestAdsbTargets = targets
        latestAdsbOwnshipAltitudeMeters = normalizedOwnshipAltitude
        latestAdsbUnitsPreferences = unitsPreferences
        val map = mapState.mapLibreMap ?: return
        if (mapState.adsbTrafficOverlay == null) {
            mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
        }
        mapState.adsbTrafficOverlay?.render(
            targets = targets,
            ownshipAltitudeMeters = normalizedOwnshipAltitude,
            unitsPreferences = unitsPreferences
        )
        bringTrafficOverlaysToFront()
    }

    fun setForecastOverlay(
        enabled: Boolean,
        primaryTileSpec: ForecastTileSpec?,
        primaryLegendSpec: ForecastLegendSpec?,
        windOverlayEnabled: Boolean,
        windTileSpec: ForecastTileSpec?,
        windLegendSpec: ForecastLegendSpec?,
        opacity: Float,
        windOverlayScale: Float,
        windDisplayMode: ForecastWindDisplayMode
    ) {
        val primaryOverlayEnabled = enabled
        forecastOverlayEnabled = primaryOverlayEnabled || windOverlayEnabled
        forecastWindOverlayEnabled = windOverlayEnabled
        latestForecastPrimaryTileSpec = primaryTileSpec
        latestForecastPrimaryLegend = primaryLegendSpec
        latestForecastWindTileSpec = windTileSpec
        latestForecastWindLegend = windLegendSpec
        forecastOpacity = clampForecastOpacity(opacity)
        forecastWindOverlayScale = clampForecastWindOverlayScale(windOverlayScale)
        forecastWindDisplayMode = windDisplayMode
        if (!forecastOverlayEnabled) {
            mapState.forecastOverlay?.clear()
            mapState.forecastWindOverlay?.clear()
            _forecastRuntimeWarningMessage.value = null
            return
        }
        val map = mapState.mapLibreMap ?: run {
            _forecastRuntimeWarningMessage.value = null
            return
        }
        if (mapState.forecastOverlay == null) {
            mapState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
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
        refreshForecastRuntimeWarningMessage()
        mapState.blueLocationOverlay?.bringToFront()
        bringTrafficOverlaysToFront()
    }

    fun clearForecastOverlay() {
        forecastOverlayEnabled = false
        forecastWindOverlayEnabled = false
        latestForecastPrimaryTileSpec = null
        latestForecastPrimaryLegend = null
        latestForecastWindTileSpec = null
        latestForecastWindLegend = null
        mapState.forecastOverlay?.clear()
        mapState.forecastWindOverlay?.clear()
        _forecastRuntimeWarningMessage.value = null
    }

    fun setSkySightSatelliteOverlay(
        enabled: Boolean,
        showSatelliteImagery: Boolean,
        showRadar: Boolean,
        showLightning: Boolean,
        animate: Boolean,
        historyFrameCount: Int,
        referenceTimeUtcMs: Long?
    ) {
        val hasAnySatelliteLayer = showSatelliteImagery || showRadar || showLightning
        val previousContrastIconsEnabled = ognSatelliteContrastIconsEnabled
        ognSatelliteContrastIconsEnabled = enabled && hasAnySatelliteLayer
        mapState.ognTrafficOverlay?.setUseSatelliteContrastIcons(ognSatelliteContrastIconsEnabled)
        val shouldRefreshOgnTargetsForContrast =
            previousContrastIconsEnabled != ognSatelliteContrastIconsEnabled &&
                (mapState.ognTrafficOverlay != null || latestOgnTargets.isNotEmpty())
        if (shouldRefreshOgnTargetsForContrast) {
            updateOgnTrafficTargets(
                targets = latestOgnTargets,
                ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
                altitudeUnit = latestOgnAltitudeUnit,
                unitsPreferences = latestOgnUnitsPreferences,
                forceImmediate = true
            )
        }

        val resolvedHistoryFrames = clampSkySightSatelliteHistoryFrames(historyFrameCount)
        skySightSatelliteEnabled = enabled
        skySightSatelliteImageryEnabled = showSatelliteImagery
        skySightSatelliteRadarEnabled = showRadar
        skySightSatelliteLightningEnabled = showLightning
        skySightSatelliteAnimateEnabled = animate
        skySightSatelliteHistoryFrames = resolvedHistoryFrames
        skySightSatelliteReferenceTimeUtcMs = referenceTimeUtcMs

        val nextConfig = SkySightSatelliteRuntimeConfig(
            enabled = enabled,
            showSatelliteImagery = showSatelliteImagery,
            showRadar = showRadar,
            showLightning = showLightning,
            animate = animate,
            historyFrameCount = resolvedHistoryFrames,
            referenceTimeUtcMs = referenceTimeUtcMs
        )
        if (!enabled || !hasAnySatelliteLayer) {
            mapState.skySightSatelliteOverlay?.clear()
            _skySightSatelliteRuntimeErrorMessage.value = null
            lastSkySightSatelliteConfig = nextConfig
            return
        }
        if (nextConfig == lastSkySightSatelliteConfig) return

        val map = mapState.mapLibreMap ?: run {
            _skySightSatelliteRuntimeErrorMessage.value = null
            return
        }
        if (applySkySightSatelliteOverlay(map, nextConfig)) {
            lastSkySightSatelliteConfig = nextConfig
        }
    }

    fun clearSkySightSatelliteOverlay() {
        val previousContrastIconsEnabled = ognSatelliteContrastIconsEnabled
        skySightSatelliteEnabled = FORECAST_SKYSIGHT_SATELLITE_OVERLAY_ENABLED_DEFAULT
        skySightSatelliteImageryEnabled = FORECAST_SKYSIGHT_SATELLITE_IMAGERY_ENABLED_DEFAULT
        skySightSatelliteRadarEnabled = FORECAST_SKYSIGHT_SATELLITE_RADAR_ENABLED_DEFAULT
        skySightSatelliteLightningEnabled = FORECAST_SKYSIGHT_SATELLITE_LIGHTNING_ENABLED_DEFAULT
        skySightSatelliteAnimateEnabled = FORECAST_SKYSIGHT_SATELLITE_ANIMATE_ENABLED_DEFAULT
        skySightSatelliteHistoryFrames = FORECAST_SKYSIGHT_SATELLITE_HISTORY_FRAMES_DEFAULT
        skySightSatelliteReferenceTimeUtcMs = null
        lastSkySightSatelliteConfig = null
        ognSatelliteContrastIconsEnabled = false
        mapState.ognTrafficOverlay?.setUseSatelliteContrastIcons(false)
        if (previousContrastIconsEnabled && (mapState.ognTrafficOverlay != null || latestOgnTargets.isNotEmpty())) {
            updateOgnTrafficTargets(
                targets = latestOgnTargets,
                ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
                altitudeUnit = latestOgnAltitudeUnit,
                unitsPreferences = latestOgnUnitsPreferences,
                forceImmediate = true
            )
        }
        mapState.skySightSatelliteOverlay?.clear()
        _skySightSatelliteRuntimeErrorMessage.value = null
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

    fun reapplySkySightSatelliteOverlay() {
        val map = mapState.mapLibreMap ?: return
        reapplySkySightSatelliteOverlay(map)
    }

    fun reapplyForecastOverlay() {
        val map = mapState.mapLibreMap ?: return
        reapplyForecastOverlay(map)
    }

    fun setAdsbIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        adsbIconSizePx = clamped
        mapState.adsbTrafficOverlay?.setIconSizePx(clamped)
    }

    fun setOgnIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        ognIconSizePx = clamped
        mapState.ognTrafficOverlay?.setIconSizePx(clamped)
    }

    private fun createOgnTrafficOverlay(map: MapLibreMap): OgnTrafficOverlay =
        ognTrafficOverlayFactory(
            map,
            ognIconSizePx,
            ognSatelliteContrastIconsEnabled
        )

    private fun createOgnThermalOverlay(map: MapLibreMap): OgnThermalOverlay =
        ognThermalOverlayFactory(map)

    private fun createOgnGliderTrailOverlay(map: MapLibreMap): OgnGliderTrailOverlay =
        ognGliderTrailOverlayFactory(map)

    private fun createAdsbTrafficOverlay(map: MapLibreMap): AdsbTrafficOverlay =
        adsbTrafficOverlayFactory(map, adsbIconSizePx)

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
            append("- OGN Display Update Mode: ${ognDisplayUpdateMode.displayLabel}\n")
            append("- OGN Satellite Contrast Icons Enabled: $ognSatelliteContrastIconsEnabled\n")
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
            append("- Forecast Wind Overlay Enabled: $forecastWindOverlayEnabled\n")
            append(
                "- Forecast Raster Overlay: ${
                    if (mapState.forecastOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append(
                "- Forecast Wind Overlay: ${
                    if (mapState.forecastWindOverlay != null) "Initialized" else "Not Initialized"
                }\n"
            )
            append("- SkySight Satellite Overlay Enabled: $skySightSatelliteEnabled\n")
            append("- SkySight Satellite Imagery Enabled: $skySightSatelliteImageryEnabled\n")
            append("- SkySight Satellite Radar Enabled: $skySightSatelliteRadarEnabled\n")
            append("- SkySight Satellite Lightning Enabled: $skySightSatelliteLightningEnabled\n")
            append("- SkySight Satellite Animate Enabled: $skySightSatelliteAnimateEnabled\n")
            append("- SkySight Satellite History Frames: $skySightSatelliteHistoryFrames\n")
            append(
                "- SkySight Satellite Runtime Overlay: ${
                    if (mapState.skySightSatelliteOverlay != null) "Initialized" else "Not Initialized"
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
            mapState.forecastWindOverlay?.clear()
            _forecastRuntimeWarningMessage.value = null
            return
        }
        if (mapState.forecastOverlay == null) {
            mapState.forecastOverlay = ForecastRasterOverlay(
                map = map,
                idNamespace = "primary"
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
        refreshForecastRuntimeWarningMessage()
        mapState.blueLocationOverlay?.bringToFront()
        bringTrafficOverlaysToFront()
    }

    private fun refreshForecastRuntimeWarningMessage() {
        _forecastRuntimeWarningMessage.value = joinNonBlankMessages(
            mapState.forecastOverlay?.runtimeWarningMessage(),
            mapState.forecastWindOverlay?.runtimeWarningMessage()
        )
    }

    private fun joinNonBlankMessages(vararg messages: String?): String? {
        val joined = messages.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
        return joined.takeIf { it.isNotBlank() }
    }

    private fun reapplySkySightSatelliteOverlay(map: MapLibreMap) {
        val config = SkySightSatelliteRuntimeConfig(
            enabled = skySightSatelliteEnabled,
            showSatelliteImagery = skySightSatelliteImageryEnabled,
            showRadar = skySightSatelliteRadarEnabled,
            showLightning = skySightSatelliteLightningEnabled,
            animate = skySightSatelliteAnimateEnabled,
            historyFrameCount = skySightSatelliteHistoryFrames,
            referenceTimeUtcMs = skySightSatelliteReferenceTimeUtcMs
        )
        if (applySkySightSatelliteOverlay(map, config)) {
            lastSkySightSatelliteConfig = config
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

    private fun applySkySightSatelliteOverlay(
        map: MapLibreMap,
        config: SkySightSatelliteRuntimeConfig
    ): Boolean {
        val hasAnySatelliteLayer = config.showSatelliteImagery || config.showRadar || config.showLightning
        if (!config.enabled || !hasAnySatelliteLayer) {
            mapState.skySightSatelliteOverlay?.clear()
            _skySightSatelliteRuntimeErrorMessage.value = null
            return true
        }
        if (mapState.skySightSatelliteOverlay == null) {
            mapState.skySightSatelliteOverlay = SkySightSatelliteOverlay(map)
        }
        return runCatching {
            mapState.skySightSatelliteOverlay?.render(
                SkySightSatelliteRenderConfig(
                    enabled = config.enabled,
                    showSatelliteImagery = config.showSatelliteImagery,
                    showRadar = config.showRadar,
                    showLightning = config.showLightning,
                    animate = config.animate,
                    historyFrameCount = config.historyFrameCount,
                    referenceTimeUtcMs = config.referenceTimeUtcMs
                )
            )
            _skySightSatelliteRuntimeErrorMessage.value = null
            mapState.blueLocationOverlay?.bringToFront()
            bringTrafficOverlaysToFront()
            true
        }.getOrElse { throwable ->
            _skySightSatelliteRuntimeErrorMessage.value =
                throwable.message?.trim()?.takeIf { it.isNotEmpty() }
                    ?: "SkySight satellite overlay failed to apply"
            Log.e(TAG, "SkySight satellite overlay apply failed: ${throwable.message}", throwable)
            false
        }
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
            mapState.blueLocationOverlay?.bringToFront()
            bringTrafficOverlaysToFront()
        }.onFailure { throwable ->
            Log.e(TAG, "Weather rain overlay apply failed: ${throwable.message}", throwable)
        }
    }

    private fun bringTrafficOverlaysToFront() {
        mapState.ognTrafficOverlay?.bringToFront()
        mapState.adsbTrafficOverlay?.bringToFront()
    }

    private data class WeatherRainRuntimeConfig(
        val enabled: Boolean,
        val frameSelection: WeatherRainFrameSelection?,
        val opacity: Float,
        val transitionDurationMs: Long,
        val stale: Boolean
    )

    private data class OgnRenderThrottleState(
        var lastRenderMonoMs: Long = 0L,
        var pendingJob: Job? = null
    )

    private fun normalizeOwnshipAltitudeForRender(altitudeMeters: Double?): Double? {
        val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
        return kotlin.math.round(altitude * OWN_ALTITUDE_RENDER_RESOLUTION_SCALE) /
            OWN_ALTITUDE_RENDER_RESOLUTION_SCALE
    }

    private data class SkySightSatelliteRuntimeConfig(
        val enabled: Boolean,
        val showSatelliteImagery: Boolean,
        val showRadar: Boolean,
        val showLightning: Boolean,
        val animate: Boolean,
        val historyFrameCount: Int,
        val referenceTimeUtcMs: Long?
    )
}
