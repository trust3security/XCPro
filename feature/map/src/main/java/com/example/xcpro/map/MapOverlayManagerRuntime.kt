package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
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

    private var latestAdsbTargets: List<AdsbTrafficUiModel> = emptyList()
    private var latestAdsbOwnshipAltitudeMeters: Double? = null
    private var latestAdsbUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var adsbIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
    private var adsbEmergencyFlashEnabled: Boolean = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
    private val baseOpsDelegate = MapOverlayManagerRuntimeBaseOpsDelegate(
        context = context,
        mapStateReader = mapStateReader,
        taskRenderSyncCoordinator = taskRenderSyncCoordinator,
        taskWaypointCountProvider = taskWaypointCountProvider,
        stateActions = stateActions,
        snailTrailManager = snailTrailManager,
        coroutineScope = coroutineScope,
        airspaceUseCase = airspaceUseCase,
        waypointFilesUseCase = waypointFilesUseCase
    )
    private val forecastWeatherDelegate = MapOverlayManagerRuntimeForecastWeatherDelegate(
        mapState = mapState,
        bringTrafficOverlaysToFront = ::bringTrafficOverlaysToFront,
        onSatelliteContrastIconsChanged = ::onSatelliteContrastIconsChanged
    )
    private val ognDelegate = MapOverlayManagerRuntimeOgnDelegate(
        mapState = mapState,
        coroutineScope = coroutineScope,
        ognTrafficOverlayFactory = ognTrafficOverlayFactory,
        ognThermalOverlayFactory = ognThermalOverlayFactory,
        ognGliderTrailOverlayFactory = ognGliderTrailOverlayFactory,
        bringTrafficOverlaysToFront = ::bringTrafficOverlaysToFront,
        satelliteContrastIconsEnabled = { forecastWeatherDelegate.satelliteContrastIconsEnabled() },
        normalizeOwnshipAltitudeForRender = ::normalizeOwnshipAltitudeForRender
    )
    val forecastRuntimeWarningMessage: StateFlow<String?> =
        forecastWeatherDelegate.forecastRuntimeWarningMessage
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        forecastWeatherDelegate.skySightSatelliteRuntimeErrorMessage

    fun toggleDistanceCircles() {
        baseOpsDelegate.toggleDistanceCircles()
    }

    fun refreshAirspace(map: MapLibreMap?) {
        baseOpsDelegate.refreshAirspace(map)
    }

    fun refreshWaypoints(map: MapLibreMap?) {
        baseOpsDelegate.refreshWaypoints(map)
    }

    fun plotSavedTask(map: MapLibreMap?) {
        baseOpsDelegate.plotSavedTask(map)
    }

    fun clearTaskOverlays(map: MapLibreMap?) {
        baseOpsDelegate.clearTaskOverlays(map)
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
                forecastWeatherDelegate.onMapStyleChanged(map)
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
                forecastWeatherDelegate.onInitialize(map)
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Map overlays initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing overlays: ${e.message}", e)
        }
    }

    fun initializeTrafficOverlays(map: MapLibreMap?) {
        ognDelegate.initializeTrafficOverlays(map)

        mapState.adsbTrafficOverlay?.cleanup()
        if (map == null) return
        mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
        mapState.adsbTrafficOverlay?.initialize()
        mapState.adsbTrafficOverlay?.render(
            targets = latestAdsbTargets,
            ownshipAltitudeMeters = latestAdsbOwnshipAltitudeMeters,
            unitsPreferences = latestAdsbUnitsPreferences
        )
        bringTrafficOverlaysToFront()
    }

    fun requestTaskRenderSync() {
        taskRenderSyncCoordinator.onTaskMutation()
    }

    fun onTaskStateChanged(signature: TaskRenderSyncCoordinator.TaskStateSignature) {
        taskRenderSyncCoordinator.onTaskStateChanged(signature)
    }

    fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        ognDelegate.setDisplayUpdateMode(mode)
    }

    fun onMapDetached() {
        baseOpsDelegate.onMapDetached()
        ognDelegate.onMapDetached()
        forecastWeatherDelegate.onMapDetached()
    }

    fun updateOgnTrafficTargets(
        targets: List<OgnTrafficTarget>,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences = UnitsPreferences(),
        forceImmediate: Boolean = false
    ) {
        ognDelegate.updateTrafficTargets(
            targets = targets,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            altitudeUnit = altitudeUnit,
            unitsPreferences = unitsPreferences,
            forceImmediate = forceImmediate
        )
    }

    fun updateOgnThermalHotspots(hotspots: List<OgnThermalHotspot>, forceImmediate: Boolean = false) {
        ognDelegate.updateThermalHotspots(hotspots, forceImmediate)
    }

    fun updateOgnGliderTrailSegments(segments: List<OgnGliderTrailSegment>, forceImmediate: Boolean = false) {
        ognDelegate.updateGliderTrailSegments(segments, forceImmediate)
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
        forecastWeatherDelegate.setForecastOverlay(
            enabled = enabled,
            primaryTileSpec = primaryTileSpec,
            primaryLegendSpec = primaryLegendSpec,
            windOverlayEnabled = windOverlayEnabled,
            windTileSpec = windTileSpec,
            windLegendSpec = windLegendSpec,
            opacity = opacity,
            windOverlayScale = windOverlayScale,
            windDisplayMode = windDisplayMode
        )
    }

    fun clearForecastOverlay() {
        forecastWeatherDelegate.clearForecastOverlay()
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
        forecastWeatherDelegate.setSkySightSatelliteOverlay(
            enabled = enabled,
            showSatelliteImagery = showSatelliteImagery,
            showRadar = showRadar,
            showLightning = showLightning,
            animate = animate,
            historyFrameCount = historyFrameCount,
            referenceTimeUtcMs = referenceTimeUtcMs
        )
    }

    fun clearSkySightSatelliteOverlay() {
        forecastWeatherDelegate.clearSkySightSatelliteOverlay()
    }

    fun setWeatherRainOverlay(
        enabled: Boolean,
        frameSelection: WeatherRainFrameSelection?,
        opacity: Float,
        transitionDurationMs: Long,
        statusCode: WeatherRadarStatusCode,
        stale: Boolean
    ) {
        forecastWeatherDelegate.setWeatherRainOverlay(
            enabled = enabled,
            frameSelection = frameSelection,
            opacity = opacity,
            transitionDurationMs = transitionDurationMs,
            statusCode = statusCode,
            stale = stale
        )
    }

    fun clearWeatherRainOverlay() {
        forecastWeatherDelegate.clearWeatherRainOverlay()
    }

    fun reapplyWeatherRainOverlay() {
        forecastWeatherDelegate.reapplyWeatherRainOverlay()
    }

    fun reapplySkySightSatelliteOverlay() {
        forecastWeatherDelegate.reapplySkySightSatelliteOverlay()
    }

    fun reapplyForecastOverlay() {
        forecastWeatherDelegate.reapplyForecastOverlay()
    }

    fun setAdsbIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        adsbIconSizePx = clamped
        mapState.adsbTrafficOverlay?.setIconSizePx(clamped)
    }

    fun setAdsbEmergencyFlashEnabled(enabled: Boolean) {
        adsbEmergencyFlashEnabled = enabled
        mapState.adsbTrafficOverlay?.setEmergencyFlashEnabled(enabled)
    }

    fun setOgnIconSizePx(iconSizePx: Int) {
        ognDelegate.setIconSizePx(iconSizePx)
    }

    private fun createAdsbTrafficOverlay(map: MapLibreMap): AdsbTrafficOverlay =
        adsbTrafficOverlayFactory(map, adsbIconSizePx).also { overlay ->
            overlay.setEmergencyFlashEnabled(adsbEmergencyFlashEnabled)
        }

    fun findAdsbTargetAt(tap: LatLng): Icao24? {
        return mapState.adsbTrafficOverlay?.findTargetAt(tap)
    }

    fun findOgnTargetAt(tap: LatLng): String? {
        return ognDelegate.findTargetAt(tap)
    }

    fun findOgnThermalHotspotAt(tap: LatLng): String? {
        return ognDelegate.findThermalHotspotAt(tap)
    }

    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? {
        return forecastWeatherDelegate.findForecastWindArrowSpeedAt(tap)
    }

    fun onZoomChanged(map: MapLibreMap?) {
        baseOpsDelegate.onZoomChanged(map)
    }

    fun getOverlayStatus(): String {
        val ognStatus = ognDelegate.statusSnapshot()
        return buildMapOverlayManagerStatus(
            mapState = mapState,
            showDistanceCircles = mapStateReader.showDistanceCircles.value,
            ognDisplayUpdateMode = ognStatus.displayUpdateMode,
            latestOgnTargetsCount = ognStatus.targetsCount,
            latestOgnThermalHotspotsCount = ognStatus.thermalHotspotsCount,
            latestOgnGliderTrailSegmentsCount = ognStatus.gliderTrailSegmentsCount,
            latestAdsbTargetsCount = latestAdsbTargets.size,
            taskWaypointCount = taskWaypointCountProvider(),
            forecastWeatherStatus = forecastWeatherDelegate.statusSnapshot()
        )
    }

    private fun onSatelliteContrastIconsChanged(enabled: Boolean) {
        ognDelegate.applySatelliteContrastIcons(enabled)
    }

    private fun bringTrafficOverlaysToFront() {
        mapState.ognTrafficOverlay?.bringToFront()
        mapState.adsbTrafficOverlay?.bringToFront()
    }

    private fun normalizeOwnshipAltitudeForRender(altitudeMeters: Double?): Double? {
        val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
        return kotlin.math.round(altitude * OWN_ALTITUDE_RENDER_RESOLUTION_SCALE) /
            OWN_ALTITUDE_RENDER_RESOLUTION_SCALE
    }

}
