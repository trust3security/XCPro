package com.example.xcpro.map

import android.content.Context
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
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
        private const val OWN_ALTITUDE_RENDER_RESOLUTION_SCALE = 10.0
    }

    private var aatPreviewForwardCount = 0L
    private val trafficRuntimeState = MapOverlayRuntimeStateAdapter(mapState)

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
    private val mapLifecycleDelegate = MapOverlayRuntimeMapLifecycleDelegate(
        context = context,
        mapState = mapState,
        baseOpsDelegate = baseOpsDelegate,
        taskRenderSyncCoordinator = taskRenderSyncCoordinator,
        initializeTrafficOverlaysFn = { map ->
            ognDelegate.initializeTrafficOverlays(map)
            trafficDelegate.initializeAdsbTrafficOverlay(map)
        },
        forecastOnMapStyleChanged = { map -> forecastWeatherDelegate.onMapStyleChanged(map) },
        forecastOnInitialize = { map -> forecastWeatherDelegate.onInitialize(map) },
        bringTrafficOverlaysToFront = { trafficDelegate.bringTrafficOverlaysToFront() },
        snailTrailManager = snailTrailManager
    )
    private lateinit var trafficDelegate: MapOverlayManagerRuntimeTrafficDelegate
    private val forecastWeatherDelegate = MapOverlayManagerRuntimeForecastWeatherDelegate(
        mapState = mapState,
        bringTrafficOverlaysToFront = { trafficDelegate.bringTrafficOverlaysToFront() },
        onSatelliteContrastIconsChanged = ::onSatelliteContrastIconsChanged,
        nowMonoMs = nowMonoMs
    )
    private val interactionDelegate = MapOverlayRuntimeInteractionDelegate(
        coroutineScope = coroutineScope,
        applyMapInteractionState = ::applyMapInteractionState
    )
    private val ognDelegate = MapOverlayManagerRuntimeOgnDelegate(
        runtimeState = trafficRuntimeState,
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
    private val statusCoordinator = MapOverlayRuntimeStatusCoordinator(
        mapState = mapState,
        showDistanceCircles = { mapStateReader.showDistanceCircles.value },
        taskWaypointCount = taskWaypointCountProvider,
        ognStatusSnapshot = { ognDelegate.statusSnapshot() },
        latestAdsbTargetsCount = { trafficDelegate.latestAdsbTargetsCount() },
        runtimeCounters = { runtimeCounters() },
        forecastWeatherStatus = { forecastWeatherDelegate.statusSnapshot() }
    )

    init {
        trafficDelegate = MapOverlayManagerRuntimeTrafficDelegate(
            runtimeState = trafficRuntimeState,
            coroutineScope = coroutineScope,
            adsbTrafficOverlayFactory = adsbTrafficOverlayFactory,
            interactionActiveProvider = { interactionDelegate.isMapInteractionActive },
            bringOgnOverlaysToFront = { ognDelegate.bringOverlaysToFront() },
            nowMonoMs = nowMonoMs
        )
    }

    val forecastRuntimeWarningMessage: StateFlow<String?> =
        forecastWeatherDelegate.forecastRuntimeWarningMessage
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        forecastWeatherDelegate.skySightSatelliteRuntimeErrorMessage

    fun toggleDistanceCircles() = mapLifecycleDelegate.toggleDistanceCircles()
    fun refreshAirspace(map: MapLibreMap?) = mapLifecycleDelegate.refreshAirspace(map)
    fun refreshWaypoints(map: MapLibreMap?) = mapLifecycleDelegate.refreshWaypoints(map)
    fun plotSavedTask(map: MapLibreMap?) = mapLifecycleDelegate.plotSavedTask(map)
    fun clearTaskOverlays(map: MapLibreMap?) = mapLifecycleDelegate.clearTaskOverlays(map)

    fun onMapStyleChanged(map: MapLibreMap?) = mapLifecycleDelegate.onMapStyleChanged(map)
    fun initializeOverlays(map: MapLibreMap?) = mapLifecycleDelegate.initializeOverlays(map)
    fun initializeTrafficOverlays(map: MapLibreMap?) = mapLifecycleDelegate.initializeTrafficOverlays(map)

    fun setMapInteractionActive(active: Boolean) {
        interactionDelegate.setMapInteractionActive(active)
    }

    fun requestTaskRenderSync() = taskRenderSyncCoordinator.onTaskMutation()
    fun previewAatTargetPoint(index: Int, lat: Double, lon: Double) {
        aatPreviewForwardCount += 1
        taskRenderSyncCoordinator.previewAatTargetPoint(index, lat, lon)
    }

    internal fun runtimeCounters(): MapOverlayRuntimeCounters {
        val counters = trafficDelegate.runtimeCounters()
        return MapOverlayRuntimeCounters(
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

    fun onTaskStateChanged(signature: TaskRenderSyncCoordinator.TaskStateSignature) =
        taskRenderSyncCoordinator.onTaskStateChanged(signature)
    fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode) = ognDelegate.setDisplayUpdateMode(mode)

    fun onMapDetached() {
        interactionDelegate.onMapDetached()
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
    ) = ognDelegate.updateTrafficTargets(
        targets = targets,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences,
        forceImmediate = forceImmediate
    )

    fun updateOgnThermalHotspots(hotspots: List<OgnThermalHotspot>, forceImmediate: Boolean = false) =
        ognDelegate.updateThermalHotspots(hotspots, forceImmediate)

    fun updateOgnGliderTrailSegments(segments: List<OgnGliderTrailSegment>, forceImmediate: Boolean = false) =
        ognDelegate.updateGliderTrailSegments(segments, forceImmediate)

    fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: OgnTrafficTarget?,
        ownshipLocation: MapLocationUiModel?,
        forceImmediate: Boolean = false
    ) = ognDelegate.updateTargetVisuals(
        enabled = enabled,
        resolvedTarget = resolvedTarget,
        ownshipLocation = ownshipLocation?.run {
            OverlayCoordinate(latitude = latitude, longitude = longitude)
        },
        forceImmediate = forceImmediate
    )

    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) = trafficDelegate.updateAdsbTrafficTargets(
        targets = targets,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        unitsPreferences = unitsPreferences,
        normalizeOwnshipAltitudeForRender = ::normalizeOwnshipAltitudeForRender
    )

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
    ) = forecastWeatherDelegate.setForecastOverlay(
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

    fun clearForecastOverlay() = forecastWeatherDelegate.clearForecastOverlay()

    fun setSkySightSatelliteOverlay(
        enabled: Boolean,
        showSatelliteImagery: Boolean,
        showRadar: Boolean,
        showLightning: Boolean,
        animate: Boolean,
        historyFrameCount: Int,
        referenceTimeUtcMs: Long?
    ) = forecastWeatherDelegate.setSkySightSatelliteOverlay(
        enabled = enabled,
        showSatelliteImagery = showSatelliteImagery,
        showRadar = showRadar,
        showLightning = showLightning,
        animate = animate,
        historyFrameCount = historyFrameCount,
        referenceTimeUtcMs = referenceTimeUtcMs
    )

    fun clearSkySightSatelliteOverlay() = forecastWeatherDelegate.clearSkySightSatelliteOverlay()

    fun setWeatherRainOverlay(
        enabled: Boolean,
        frameSelection: WeatherRainFrameSelection?,
        opacity: Float,
        transitionDurationMs: Long,
        statusCode: WeatherRadarStatusCode,
        stale: Boolean
    ) = forecastWeatherDelegate.setWeatherRainOverlay(
        enabled = enabled,
        frameSelection = frameSelection,
        opacity = opacity,
        transitionDurationMs = transitionDurationMs,
        statusCode = statusCode,
        stale = stale
    )

    fun clearWeatherRainOverlay() = forecastWeatherDelegate.clearWeatherRainOverlay()
    fun reapplyWeatherRainOverlay() = forecastWeatherDelegate.reapplyWeatherRainOverlay()
    fun reapplySkySightSatelliteOverlay() = forecastWeatherDelegate.reapplySkySightSatelliteOverlay()
    fun reapplyForecastOverlay() = forecastWeatherDelegate.reapplyForecastOverlay()

    fun setAdsbIconSizePx(iconSizePx: Int) = trafficDelegate.setAdsbIconSizePx(iconSizePx)
    fun setAdsbEmergencyFlashEnabled(enabled: Boolean) =
        trafficDelegate.setAdsbEmergencyFlashEnabled(enabled)

    fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean) =
        trafficDelegate.setAdsbDefaultMediumUnknownIconEnabled(enabled)

    fun setOgnIconSizePx(iconSizePx: Int) = ognDelegate.setIconSizePx(iconSizePx)

    fun findAdsbTargetAt(tap: LatLng): Icao24? = trafficDelegate.findAdsbTargetAt(tap)
    fun findOgnTargetAt(tap: LatLng): String? = ognDelegate.findTargetAt(tap)
    fun findOgnThermalHotspotAt(tap: LatLng): String? =
        ognDelegate.findThermalHotspotAt(tap)

    fun findForecastWindArrowSpeedAt(tap: LatLng): Double? =
        forecastWeatherDelegate.findForecastWindArrowSpeedAt(tap)

    fun onZoomChanged(map: MapLibreMap?) = mapLifecycleDelegate.onZoomChanged(map)

    fun getOverlayStatus(): String {
        return statusCoordinator.getOverlayStatus()
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
        forecastWeatherDelegate.setMapInteractionActive(active)
        ognDelegate.setMapInteractionActive(active)
        if (!active) {
            trafficDelegate.flushDeferredAdsbRenderIfNeeded()
        }
    }
}
