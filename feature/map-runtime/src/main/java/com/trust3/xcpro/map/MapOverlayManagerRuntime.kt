package com.trust3.xcpro.map

import android.content.Context
import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.core.time.TimeBridge
import com.trust3.xcpro.forecast.ForecastLegendSpec
import com.trust3.xcpro.forecast.ForecastTileSpec
import com.trust3.xcpro.forecast.ForecastWindDisplayMode
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.weather.rain.WeatherRainFrameSelection
import com.trust3.xcpro.weather.rain.WeatherRadarStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

open class MapOverlayManagerRuntime(
    private val context: Context,
    private val taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    private val coroutineScope: CoroutineScope,
    private val trafficRuntimeState: TrafficOverlayRuntimeState,
    private val forecastWeatherRuntimeState: ForecastWeatherOverlayRuntimeState,
    private val ognTrafficOverlayFactory: OgnTrafficOverlayFactory =
        TrafficOverlayFactories::createOgnTrafficOverlay,
    private val ognTargetRingOverlayFactory: OgnTargetRingOverlayFactory =
        TrafficOverlayFactories::createOgnTargetRingOverlay,
    private val ognTargetLineOverlayFactory: OgnTargetLineOverlayFactory =
        TrafficOverlayFactories::createOgnTargetLineOverlay,
    private val ognOwnshipTargetBadgeOverlayFactory: OgnOwnshipTargetBadgeOverlayFactory =
        TrafficOverlayFactories::createOgnOwnshipTargetBadgeOverlay,
    private val ognThermalOverlayFactory: OgnThermalOverlayFactory =
        TrafficOverlayFactories::createOgnThermalOverlay,
    private val ognGliderTrailOverlayFactory: OgnGliderTrailOverlayFactory =
        TrafficOverlayFactories::createOgnGliderTrailOverlay,
    private val ognSelectedThermalOverlayFactory: OgnSelectedThermalOverlayFactory =
        TrafficOverlayFactories::createOgnSelectedThermalOverlay,
    private val adsbTrafficOverlayFactory: AdsbTrafficOverlayFactory =
        TrafficOverlayFactories::createAdsbTrafficOverlay,
    private val nowMonoMs: () -> Long = TimeBridge::nowMonoMs
) {
    companion object {
        private const val OWN_ALTITUDE_RENDER_RESOLUTION_SCALE = 10.0
        private const val INTERACTION_RELEASE_FLUSH_SETTLE_MS = 120L
    }

    private var aatPreviewForwardCount = 0L
    private var ognTrafficCollectorEmissionCount = 0L
    private var ognTrafficCollectorDedupedCount = 0L
    private var ognTrafficPortUpdateCount = 0L
    private var ognTargetVisualCollectorEmissionCount = 0L
    private var ognTargetVisualCollectorDedupedCount = 0L
    private var ognTargetVisualPortUpdateCount = 0L
    private var adsbTrafficCollectorEmissionCount = 0L
    private var adsbTrafficCollectorDedupedCount = 0L
    private var adsbTrafficPortUpdateCount = 0L
    private var ognThermalCollectorEmissionCount = 0L
    private var ognTrailCollectorEmissionCount = 0L
    private var selectedOgnThermalCollectorEmissionCount = 0L
    private var pendingInteractionReleaseFlushJob: Job? = null
    private lateinit var lifecyclePort: MapOverlayRuntimeLifecyclePort
    private lateinit var trafficDelegate: MapOverlayManagerRuntimeTrafficDelegate
    private val forecastWeatherDelegate = MapOverlayManagerRuntimeForecastWeatherDelegate(
        runtimeState = forecastWeatherRuntimeState,
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
        context = context,
        ognTrafficOverlayFactory = ognTrafficOverlayFactory,
        ognTargetRingOverlayFactory = ognTargetRingOverlayFactory,
        ognTargetLineOverlayFactory = ognTargetLineOverlayFactory,
        ognOwnshipTargetBadgeOverlayFactory = ognOwnshipTargetBadgeOverlayFactory,
        ognThermalOverlayFactory = ognThermalOverlayFactory,
        ognGliderTrailOverlayFactory = ognGliderTrailOverlayFactory,
        ognSelectedThermalOverlayFactory = ognSelectedThermalOverlayFactory,
        bringTrafficOverlaysToFront = { trafficDelegate.bringTrafficOverlaysToFront() },
        satelliteContrastIconsEnabled = { forecastWeatherDelegate.satelliteContrastIconsEnabled() },
        normalizeOwnshipAltitudeForRender = ::normalizeOwnshipAltitudeForRender,
        nowMonoMs = nowMonoMs
    )
    private lateinit var statusReporter: MapOverlayRuntimeStatusReporter

    init {
        trafficDelegate = MapOverlayManagerRuntimeTrafficDelegate(
            runtimeState = trafficRuntimeState,
            coroutineScope = coroutineScope,
            adsbTrafficOverlayFactory = adsbTrafficOverlayFactory,
            context = context,
            interactionActiveProvider = { interactionDelegate.isMapInteractionActive },
            bringOgnOverlaysToFront = { ognDelegate.bringOverlaysToFront() },
            nowMonoMs = nowMonoMs
        )
    }

    protected fun attachShellPorts(
        lifecyclePort: MapOverlayRuntimeLifecyclePort,
        statusReporter: MapOverlayRuntimeStatusReporter
    ) {
        this.lifecyclePort = lifecyclePort
        this.statusReporter = statusReporter
    }

    val forecastRuntimeWarningMessage: StateFlow<String?> =
        forecastWeatherDelegate.forecastRuntimeWarningMessage
    val skySightSatelliteRuntimeErrorMessage: StateFlow<String?> =
        forecastWeatherDelegate.skySightSatelliteRuntimeErrorMessage

    fun toggleDistanceCircles() = lifecyclePort.toggleDistanceCircles()
    fun refreshAirspace(map: MapLibreMap?) = lifecyclePort.refreshAirspace(map)
    fun refreshWaypoints(map: MapLibreMap?) = lifecyclePort.refreshWaypoints(map)
    fun plotSavedTask(map: MapLibreMap?) = lifecyclePort.plotSavedTask(map)
    fun clearTaskOverlays(map: MapLibreMap?) = lifecyclePort.clearTaskOverlays(map)

    fun onMapStyleChanged(map: MapLibreMap?) = lifecyclePort.onMapStyleChanged(map)
    fun initializeOverlays(map: MapLibreMap?) = lifecyclePort.initializeOverlays(map)
    fun initializeTrafficOverlays(map: MapLibreMap?) = lifecyclePort.initializeTrafficOverlays(map)

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
            adsbDefaultMediumUnknownIconEnabled = counters.adsbDefaultMediumUnknownIconEnabled,
            adsbAnimationFrameScheduledCount = counters.adsbAnimationFrameScheduledCount,
            adsbAnimationFrameRenderedCount = counters.adsbAnimationFrameRenderedCount,
            adsbAnimationFrameSkippedCount = counters.adsbAnimationFrameSkippedCount,
            adsbActiveAnimatedTargetCount = counters.adsbActiveAnimatedTargetCount,
            adsbEmergencyAnimatedTargetCount = counters.adsbEmergencyAnimatedTargetCount,
            adsbInteractionReducedMotionActive = counters.adsbInteractionReducedMotionActive,
            ognTrafficCollectorEmissionCount = ognTrafficCollectorEmissionCount,
            ognTrafficCollectorDedupedCount = ognTrafficCollectorDedupedCount,
            ognTrafficPortUpdateCount = ognTrafficPortUpdateCount,
            ognTargetVisualCollectorEmissionCount = ognTargetVisualCollectorEmissionCount,
            ognTargetVisualCollectorDedupedCount = ognTargetVisualCollectorDedupedCount,
            ognTargetVisualPortUpdateCount = ognTargetVisualPortUpdateCount,
            adsbTrafficCollectorEmissionCount = adsbTrafficCollectorEmissionCount,
            adsbTrafficCollectorDedupedCount = adsbTrafficCollectorDedupedCount,
            adsbTrafficPortUpdateCount = adsbTrafficPortUpdateCount,
            ognThermalCollectorEmissionCount = ognThermalCollectorEmissionCount,
            ognTrailCollectorEmissionCount = ognTrailCollectorEmissionCount,
            selectedOgnThermalCollectorEmissionCount = selectedOgnThermalCollectorEmissionCount
        )
    }

    protected fun recordOgnTrafficCollectorEmissionRuntime() {
        ognTrafficCollectorEmissionCount += 1
    }

    protected fun recordOgnTrafficCollectorDedupedRuntime() {
        ognTrafficCollectorDedupedCount += 1
    }

    protected fun recordOgnTrafficPortUpdateRuntime() {
        ognTrafficPortUpdateCount += 1
    }

    protected fun recordOgnTargetVisualCollectorEmissionRuntime() {
        ognTargetVisualCollectorEmissionCount += 1
    }

    protected fun recordOgnTargetVisualCollectorDedupedRuntime() {
        ognTargetVisualCollectorDedupedCount += 1
    }

    protected fun recordOgnTargetVisualPortUpdateRuntime() {
        ognTargetVisualPortUpdateCount += 1
    }

    protected fun recordAdsbTrafficCollectorEmissionRuntime() {
        adsbTrafficCollectorEmissionCount += 1
    }

    protected fun recordAdsbTrafficCollectorDedupedRuntime() {
        adsbTrafficCollectorDedupedCount += 1
    }

    protected fun recordAdsbTrafficPortUpdateRuntime() {
        adsbTrafficPortUpdateCount += 1
    }

    protected fun recordOgnThermalCollectorEmissionRuntime() {
        ognThermalCollectorEmissionCount += 1
    }

    protected fun recordOgnTrailCollectorEmissionRuntime() {
        ognTrailCollectorEmissionCount += 1
    }

    protected fun recordSelectedOgnThermalCollectorEmissionRuntime() {
        selectedOgnThermalCollectorEmissionCount += 1
    }

    fun onTaskStateChanged(signature: TaskRenderSyncCoordinator.TaskStateSignature) =
        taskRenderSyncCoordinator.onTaskStateChanged(signature)
    fun setOgnDisplayUpdateMode(mode: OgnDisplayUpdateMode) = ognDelegate.setDisplayUpdateMode(mode)

    fun onMapDetached() {
        pendingInteractionReleaseFlushJob?.cancel()
        pendingInteractionReleaseFlushJob = null
        forecastWeatherDelegate.onMapDetached()
        trafficDelegate.onMapDetached()
        ognDelegate.onMapDetached()
        interactionDelegate.onMapDetached()
        lifecyclePort.onMapDetached()
    }

    fun updateOgnTrafficTargets(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String? = null,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences = UnitsPreferences(),
        forceImmediate: Boolean = false
    ) = ognDelegate.updateTrafficTargets(
        targets = targets,
        selectedTargetKey = selectedTargetKey,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences,
        forceImmediate = forceImmediate
    )

    fun updateOgnThermalHotspots(hotspots: List<OgnThermalHotspot>, forceImmediate: Boolean = false) =
        ognDelegate.updateThermalHotspots(hotspots, forceImmediate)

    fun updateOgnGliderTrailSegments(segments: List<OgnGliderTrailSegment>, forceImmediate: Boolean = false) =
        ognDelegate.updateGliderTrailSegments(segments, forceImmediate)

    fun updateSelectedOgnThermalContext(
        context: SelectedOgnThermalOverlayContext?,
        forceImmediate: Boolean = false
    ) = ognDelegate.updateSelectedThermalContext(context, forceImmediate)

    fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: OgnTrafficTarget?,
        ownshipLocation: MapLocationUiModel?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences,
        forceImmediate: Boolean = false
    ) = ognDelegate.updateTargetVisuals(
        enabled = enabled,
        resolvedTarget = resolvedTarget,
        ownshipLocation = ownshipLocation?.run {
            OverlayCoordinate(latitude = latitude, longitude = longitude)
        },
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences,
        forceImmediate = forceImmediate
    )

    fun updateOgnTargetVisuals(
        enabled: Boolean,
        resolvedTarget: OgnTrafficTarget?,
        ownshipCoordinate: TrafficMapCoordinate?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences,
        forceImmediate: Boolean = false
    ) = ognDelegate.updateTargetVisuals(
        enabled = enabled,
        resolvedTarget = resolvedTarget,
        ownshipLocation = ownshipCoordinate?.run {
            OverlayCoordinate(latitude = latitude, longitude = longitude)
        },
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        altitudeUnit = altitudeUnit,
        unitsPreferences = unitsPreferences,
        forceImmediate = forceImmediate
    )

    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        selectedTargetId: Icao24? = null,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences
    ) = trafficDelegate.updateAdsbTrafficTargets(
        targets = targets,
        selectedTargetId = selectedTargetId,
        ownshipAltitudeMeters = ownshipAltitudeMeters,
        unitsPreferences = unitsPreferences,
        normalizeOwnshipAltitudeForRender = ::normalizeOwnshipAltitudeForRender
    )

    fun setAdsbViewportZoom(zoomLevel: Float) = trafficDelegate.setAdsbViewportZoom(zoomLevel)
    fun setOgnViewportZoom(zoomLevel: Float) = ognDelegate.setViewportZoom(zoomLevel)
    fun invalidateTrafficProjection(forceImmediate: Boolean = false) {
        ognDelegate.invalidateProjection(forceImmediate = forceImmediate)
        trafficDelegate.invalidateProjection(forceImmediate = forceImmediate)
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

    fun onZoomChanged(map: MapLibreMap?) = lifecyclePort.onZoomChanged(map)

    fun getOverlayStatus(): String {
        return statusReporter.getOverlayStatus()
    }

    private fun onSatelliteContrastIconsChanged(enabled: Boolean) {
        ognDelegate.applySatelliteContrastIcons(enabled)
    }

    protected fun initializeTrafficOverlaysRuntime(map: MapLibreMap?) {
        ognDelegate.initializeTrafficOverlays(map)
        trafficDelegate.initializeAdsbTrafficOverlay(map)
    }

    protected fun bringTrafficOverlaysToFrontRuntime() {
        trafficDelegate.bringTrafficOverlaysToFront()
    }

    protected fun handleForecastMapStyleChangedRuntime(map: MapLibreMap?) {
        forecastWeatherDelegate.onMapStyleChanged(map)
    }

    protected fun handleForecastInitializeRuntime(map: MapLibreMap?) {
        forecastWeatherDelegate.onInitialize(map)
    }

    protected fun ognStatusSnapshotRuntime(): OgnOverlayStatusSnapshot = ognDelegate.statusSnapshot()

    protected fun latestAdsbTargetsCountRuntime(): Int = trafficDelegate.latestAdsbTargetsCount()

    protected fun forecastWeatherStatusRuntime(): MapOverlayForecastWeatherStatus =
        forecastWeatherDelegate.statusSnapshot()

    protected fun runtimeCountersSnapshot(): MapOverlayRuntimeCounters = runtimeCounters()

    private fun normalizeOwnshipAltitudeForRender(altitudeMeters: Double?): Double? {
        val altitude = altitudeMeters?.takeIf { it.isFinite() } ?: return null
        return kotlin.math.round(altitude * OWN_ALTITUDE_RENDER_RESOLUTION_SCALE) /
            OWN_ALTITUDE_RENDER_RESOLUTION_SCALE
    }

    private fun applyMapInteractionState(active: Boolean) {
        pendingInteractionReleaseFlushJob?.cancel()
        pendingInteractionReleaseFlushJob = null
        forecastWeatherDelegate.setMapInteractionActive(active)
        ognDelegate.setMapInteractionActive(active)
        trafficDelegate.setMapInteractionActive(active)
        if (active) {
            return
        }
        pendingInteractionReleaseFlushJob = coroutineScope.launch {
            delay(INTERACTION_RELEASE_FLUSH_SETTLE_MS)
            pendingInteractionReleaseFlushJob = null
            if (interactionDelegate.isMapInteractionActive) return@launch

            val forecastFlushed = forecastWeatherDelegate.flushDeferredInteractionReleaseWork(
                reconcileFrontOrder = false
            )
            val ognFlushed = ognDelegate.flushDeferredInteractionReleaseWork()
            val adsbFlushed = trafficDelegate.flushDeferredAdsbRenderIfNeeded(
                reconcileFrontOrder = false
            )
            if (forecastFlushed || ognFlushed || adsbFlushed) {
                trafficDelegate.bringTrafficOverlaysToFront()
            }
        }
    }
}
