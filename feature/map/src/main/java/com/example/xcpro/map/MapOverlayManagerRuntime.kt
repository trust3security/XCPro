package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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
    private val ognTargetRingOverlayFactory: (MapLibreMap, Int) -> OgnTargetRingOverlay =
        { map, iconSizePx ->
            OgnTargetRingOverlay(
                map = map,
                initialIconSizePx = iconSizePx
            )
        },
    private val ognTargetLineOverlayFactory: (MapLibreMap) -> OgnTargetLineOverlay =
        { map -> OgnTargetLineOverlay(map = map) },
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
        },
    private val nowMonoMs: () -> Long = TimeBridge::nowMonoMs
) {
    companion object {
        private const val TAG = "MapOverlayManager"
        private const val OWN_ALTITUDE_RENDER_RESOLUTION_SCALE = 10.0
    }

    private var aatPreviewForwardCount = 0L
    private var mapInteractionActive: Boolean = false
    private var pendingInteractionDeactivateJob: Job? = null

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
    private lateinit var trafficDelegate: MapOverlayManagerRuntimeTrafficDelegate
    private val forecastWeatherDelegate = MapOverlayManagerRuntimeForecastWeatherDelegate(
        mapState = mapState,
        bringTrafficOverlaysToFront = { trafficDelegate.bringTrafficOverlaysToFront() },
        onSatelliteContrastIconsChanged = ::onSatelliteContrastIconsChanged,
        nowMonoMs = nowMonoMs
    )
    private val ognDelegate = MapOverlayManagerRuntimeOgnDelegate(
        mapState = mapState,
        coroutineScope = coroutineScope,
        ognTrafficOverlayFactory = ognTrafficOverlayFactory,
        ognTargetRingOverlayFactory = ognTargetRingOverlayFactory,
        ognTargetLineOverlayFactory = ognTargetLineOverlayFactory,
        ognThermalOverlayFactory = ognThermalOverlayFactory,
        ognGliderTrailOverlayFactory = ognGliderTrailOverlayFactory,
        bringTrafficOverlaysToFront = { trafficDelegate.bringTrafficOverlaysToFront() },
        satelliteContrastIconsEnabled = { forecastWeatherDelegate.satelliteContrastIconsEnabled() },
        normalizeOwnshipAltitudeForRender = ::normalizeOwnshipAltitudeForRender,
        nowMonoMs = nowMonoMs
    )

    init {
        trafficDelegate = MapOverlayManagerRuntimeTrafficDelegate(
            mapState = mapState,
            coroutineScope = coroutineScope,
            adsbTrafficOverlayFactory = adsbTrafficOverlayFactory,
            interactionActiveProvider = { mapInteractionActive },
            bringOgnOverlaysToFront = { ognDelegate.bringOverlaysToFront() },
            nowMonoMs = nowMonoMs
        )
    }

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
            trafficDelegate.bringTrafficOverlaysToFront()
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
        trafficDelegate.initializeAdsbTrafficOverlay(map)
    }

    fun setMapInteractionActive(active: Boolean) {
        if (active) {
            pendingInteractionDeactivateJob?.cancel()
            pendingInteractionDeactivateJob = null
            applyMapInteractionState(true)
            return
        }
        val deactivateDelayMs = resolveMapInteractionDeactivateDelayMs(
            interactionWasActive = mapInteractionActive,
            requestedActive = false
        )
        if (deactivateDelayMs <= 0L) {
            pendingInteractionDeactivateJob?.cancel()
            pendingInteractionDeactivateJob = null
            applyMapInteractionState(false)
            return
        }
        if (pendingInteractionDeactivateJob != null) return
        pendingInteractionDeactivateJob = coroutineScope.launch {
            delay(deactivateDelayMs)
            pendingInteractionDeactivateJob = null
            applyMapInteractionState(false)
        }
    }

    fun requestTaskRenderSync() {
        taskRenderSyncCoordinator.onTaskMutation()
    }

    fun previewAatTargetPoint(index: Int, lat: Double, lon: Double) {
        aatPreviewForwardCount += 1
        taskRenderSyncCoordinator.previewAatTargetPoint(
            waypointIndex = index,
            latitude = lat,
            longitude = lon
        )
    }

    fun runtimeCounters(): RuntimeCounters {
        val counters = trafficDelegate.runtimeCounters()
        return RuntimeCounters(
            overlayFrontOrderApplyCount = counters.overlayFrontOrderApplyCount,
            overlayFrontOrderSkippedCount = counters.overlayFrontOrderSkippedCount,
            aatPreviewForwardCount = aatPreviewForwardCount,
            adsbIconUnknownRenderCount = counters.adsbIconUnknownRenderCount,
            adsbIconLegacyUnknownRenderCount = counters.adsbIconLegacyUnknownRenderCount,
            adsbIconResolveLatencySampleCount = counters.adsbIconResolveLatencySampleCount,
            adsbIconResolveLatencyLastMs = counters.adsbIconResolveLatencyLastMs,
            adsbIconResolveLatencyMaxMs = counters.adsbIconResolveLatencyMaxMs,
            adsbIconResolveLatencyAverageMs = counters.adsbIconResolveLatencyAverageMs,
            adsbDefaultMediumUnknownIconEnabled = counters.adsbDefaultMediumUnknownIconEnabled
        )
    }

    fun onTaskStateChanged(signature: TaskRenderSyncCoordinator.TaskStateSignature) {
        taskRenderSyncCoordinator.onTaskStateChanged(signature)
    }

    fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        ognDelegate.setDisplayUpdateMode(mode)
    }

    fun onMapDetached() {
        pendingInteractionDeactivateJob?.cancel()
        pendingInteractionDeactivateJob = null
        applyMapInteractionState(false)
        trafficDelegate.onMapDetached()
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

    fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: OgnTrafficTarget?,
        ownshipLocation: MapLocationUiModel?,
        forceImmediate: Boolean = false
    ) {
        ognDelegate.updateTargetVisuals(
            enabled = enabled,
            resolvedTarget = resolvedTarget,
            ownshipLocation = ownshipLocation,
            forceImmediate = forceImmediate
        )
    }

    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) {
        trafficDelegate.updateAdsbTrafficTargets(
            targets = targets,
            ownshipAltitudeMeters = ownshipAltitudeMeters,
            unitsPreferences = unitsPreferences,
            normalizeOwnshipAltitudeForRender = ::normalizeOwnshipAltitudeForRender
        )
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
        trafficDelegate.setAdsbIconSizePx(iconSizePx)
    }

    fun setAdsbEmergencyFlashEnabled(enabled: Boolean) {
        trafficDelegate.setAdsbEmergencyFlashEnabled(enabled)
    }

    fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean) {
        trafficDelegate.setAdsbDefaultMediumUnknownIconEnabled(enabled)
    }

    fun setOgnIconSizePx(iconSizePx: Int) {
        ognDelegate.setIconSizePx(iconSizePx)
    }

    fun findAdsbTargetAt(tap: LatLng): Icao24? {
        return trafficDelegate.findAdsbTargetAt(tap)
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
            ognTargetEnabled = ognStatus.targetEnabled,
            ognTargetResolved = ognStatus.targetResolved,
            latestAdsbTargetsCount = trafficDelegate.latestAdsbTargetsCount(),
            taskWaypointCount = taskWaypointCountProvider(),
            forecastWeatherStatus = forecastWeatherDelegate.statusSnapshot()
        )
    }

    private fun onSatelliteContrastIconsChanged(enabled: Boolean) {
        ognDelegate.applySatelliteContrastIcons(enabled)
    }

    private fun normalizeOwnshipAltitudeForRender(altitudeMeters: Double?): Double? {
        val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
        return kotlin.math.round(altitude * OWN_ALTITUDE_RENDER_RESOLUTION_SCALE) /
            OWN_ALTITUDE_RENDER_RESOLUTION_SCALE
    }

    private fun applyMapInteractionState(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
        forecastWeatherDelegate.setMapInteractionActive(active)
        ognDelegate.setMapInteractionActive(active)
        if (!active) {
            trafficDelegate.flushDeferredAdsbRenderIfNeeded()
        }
    }

    data class RuntimeCounters(
        val overlayFrontOrderApplyCount: Long,
        val overlayFrontOrderSkippedCount: Long,
        val aatPreviewForwardCount: Long,
        val adsbIconUnknownRenderCount: Long,
        val adsbIconLegacyUnknownRenderCount: Long,
        val adsbIconResolveLatencySampleCount: Long,
        val adsbIconResolveLatencyLastMs: Long?,
        val adsbIconResolveLatencyMaxMs: Long?,
        val adsbIconResolveLatencyAverageMs: Long?,
        val adsbDefaultMediumUnknownIconEnabled: Boolean
    )
}
