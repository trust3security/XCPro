package com.example.xcpro.map

import com.example.xcpro.adsb.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.clampAdsbIconSizePx
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal data class MapOverlayRuntimeTrafficCounters(
    val overlayFrontOrderApplyCount: Long,
    val overlayFrontOrderSkippedCount: Long,
    val adsbIconUnknownRenderCount: Long,
    val adsbIconLegacyUnknownRenderCount: Long,
    val adsbIconResolveLatencySampleCount: Long,
    val adsbIconResolveLatencyLastMs: Long?,
    val adsbIconResolveLatencyMaxMs: Long?,
    val adsbIconResolveLatencyAverageMs: Long?,
    val adsbDefaultMediumUnknownIconEnabled: Boolean
)

internal class MapOverlayManagerRuntimeTrafficDelegate(
    private val mapState: MapScreenState,
    private val coroutineScope: CoroutineScope,
    private val adsbTrafficOverlayFactory: (MapLibreMap, Int) -> AdsbTrafficOverlay,
    private val interactionActiveProvider: () -> Boolean,
    private val bringOgnOverlaysToFront: () -> Unit,
    private val nowMonoMs: () -> Long
) {
    private var latestAdsbTargets: List<AdsbTrafficUiModel> = emptyList()
    private var latestAdsbOwnshipAltitudeMeters: Double? = null
    private var latestAdsbUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var adsbIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
    private var adsbEmergencyFlashEnabled: Boolean = ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
    private var lastOverlayFrontOrderSignature: OverlayFrontOrderSignature? = null
    private var lastOverlayFrontOrderApplyMonoMs: Long = 0L
    private var overlayFrontOrderApplyCount = 0L
    private var overlayFrontOrderSkippedCount = 0L
    private val adsbRenderState = AdsbRenderThrottleState()
    private val adsbIconTelemetryTracker = AdsbIconTelemetryTracker()
    private val stickyIconProjectionCache = AdsbStickyIconProjectionCache()
    private var defaultMediumUnknownIconEnabled = true

    fun initializeAdsbTrafficOverlay(map: MapLibreMap?) {
        adsbRenderState.pendingJob?.cancel()
        adsbRenderState.pendingJob = null
        mapState.adsbTrafficOverlay?.cleanup()
        if (map == null) return
        mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
        mapState.adsbTrafficOverlay?.initialize()
        val projectedStyleIds = stickyIconProjectionCache.projectStyleImageIds(
            targets = latestAdsbTargets,
            nowMonoMs = nowMonoMs(),
            defaultMediumUnknownIconEnabled = defaultMediumUnknownIconEnabled
        )
        mapState.adsbTrafficOverlay?.render(
            targets = latestAdsbTargets,
            ownshipAltitudeMeters = latestAdsbOwnshipAltitudeMeters,
            unitsPreferences = latestAdsbUnitsPreferences,
            iconStyleIdOverrides = projectedStyleIds
        )
        adsbRenderState.lastRenderMonoMs = nowMonoMs()
        bringTrafficOverlaysToFront()
    }

    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences,
        normalizeOwnshipAltitudeForRender: (Double?) -> Double?
    ) {
        val previouslyHadNoTargets = latestAdsbTargets.isEmpty()
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
        scheduleAdsbRender(forceImmediate = targets.isEmpty() || previouslyHadNoTargets)
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

    fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean) {
        if (defaultMediumUnknownIconEnabled == enabled) return
        defaultMediumUnknownIconEnabled = enabled
        scheduleAdsbRender(forceImmediate = true)
    }

    fun findAdsbTargetAt(tap: LatLng): Icao24? {
        return mapState.adsbTrafficOverlay?.findTargetAt(tap)
    }

    fun latestAdsbTargetsCount(): Int = latestAdsbTargets.size

    fun runtimeCounters(): MapOverlayRuntimeTrafficCounters {
        val iconTelemetry = adsbIconTelemetryTracker.snapshot()
        return MapOverlayRuntimeTrafficCounters(
            overlayFrontOrderApplyCount = overlayFrontOrderApplyCount,
            overlayFrontOrderSkippedCount = overlayFrontOrderSkippedCount,
            adsbIconUnknownRenderCount = iconTelemetry.unknownRenderCount,
            adsbIconLegacyUnknownRenderCount = iconTelemetry.legacyUnknownRenderCount,
            adsbIconResolveLatencySampleCount = iconTelemetry.resolveLatencySampleCount,
            adsbIconResolveLatencyLastMs = iconTelemetry.resolveLatencyLastMs,
            adsbIconResolveLatencyMaxMs = iconTelemetry.resolveLatencyMaxMs,
            adsbIconResolveLatencyAverageMs = iconTelemetry.resolveLatencyAverageMs,
            adsbDefaultMediumUnknownIconEnabled = defaultMediumUnknownIconEnabled
        )
    }

    fun onMapDetached() {
        adsbRenderState.pendingJob?.cancel()
        adsbRenderState.pendingJob = null
        lastOverlayFrontOrderSignature = null
        lastOverlayFrontOrderApplyMonoMs = 0L
        adsbIconTelemetryTracker.onMapDetached()
        stickyIconProjectionCache.clear()
    }

    fun bringTrafficOverlaysToFront() {
        val nowMs = nowMonoMs()
        if (shouldThrottleOverlayFrontOrderDuringInteraction(
                interactionActive = interactionActiveProvider(),
                lastAppliedMonoMs = lastOverlayFrontOrderApplyMonoMs,
                nowMonoMs = nowMs
            )
        ) {
            overlayFrontOrderSkippedCount += 1
            return
        }
        val currentSignature = captureOverlayFrontOrderSignature()
        if (currentSignature != null && currentSignature == lastOverlayFrontOrderSignature) {
            overlayFrontOrderSkippedCount += 1
            return
        }
        mapState.blueLocationOverlay?.bringToFront()
        bringOgnOverlaysToFront()
        mapState.adsbTrafficOverlay?.bringToFront()
        overlayFrontOrderApplyCount += 1
        lastOverlayFrontOrderApplyMonoMs = nowMs
        lastOverlayFrontOrderSignature = captureOverlayFrontOrderSignature()
    }

    fun flushDeferredAdsbRenderIfNeeded() {
        val pending = adsbRenderState.pendingJob ?: return
        pending.cancel()
        adsbRenderState.pendingJob = null
        val map = mapState.mapLibreMap ?: return
        renderAdsbNow(map)
    }

    private fun scheduleAdsbRender(forceImmediate: Boolean) {
        val map = mapState.mapLibreMap ?: return
        val intervalMs = resolveInteractionAwareIntervalMs(
            baseIntervalMs = 0L,
            interactionActive = interactionActiveProvider(),
            interactionFloorMs = ADSB_INTERACTION_MIN_RENDER_INTERVAL_MS
        )

        if (forceImmediate || intervalMs <= 0L) {
            adsbRenderState.pendingJob?.cancel()
            adsbRenderState.pendingJob = null
            renderAdsbNow(map)
            return
        }

        val nowMs = nowMonoMs()
        val elapsedMs = nowMs - adsbRenderState.lastRenderMonoMs
        if (elapsedMs >= intervalMs && adsbRenderState.pendingJob == null) {
            renderAdsbNow(map)
            return
        }

        if (adsbRenderState.pendingJob != null) return

        val remainingMs = (intervalMs - elapsedMs).coerceAtLeast(0L)
        adsbRenderState.pendingJob = coroutineScope.launch {
            delay(remainingMs)
            adsbRenderState.pendingJob = null
            val currentMap = mapState.mapLibreMap
            if (currentMap == null || currentMap !== map) return@launch
            renderAdsbNow(currentMap)
        }
    }

    private fun renderAdsbNow(map: MapLibreMap) {
        val renderMonoMs = nowMonoMs()
        val projectedStyleIds = stickyIconProjectionCache.projectStyleImageIds(
            targets = latestAdsbTargets,
            nowMonoMs = renderMonoMs,
            defaultMediumUnknownIconEnabled = defaultMediumUnknownIconEnabled
        )
        if (mapState.adsbTrafficOverlay == null) {
            mapState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
            mapState.adsbTrafficOverlay?.initialize()
        }
        adsbIconTelemetryTracker.onRenderedTargets(
            targets = latestAdsbTargets,
            nowMonoMs = renderMonoMs,
            iconStyleIdOverrides = projectedStyleIds
        )
        mapState.adsbTrafficOverlay?.render(
            targets = latestAdsbTargets,
            ownshipAltitudeMeters = latestAdsbOwnshipAltitudeMeters,
            unitsPreferences = latestAdsbUnitsPreferences,
            iconStyleIdOverrides = projectedStyleIds
        )
        adsbRenderState.lastRenderMonoMs = renderMonoMs
        bringTrafficOverlaysToFront()
    }

    private fun createAdsbTrafficOverlay(map: MapLibreMap): AdsbTrafficOverlay =
        adsbTrafficOverlayFactory(map, adsbIconSizePx).also { overlay ->
            overlay.setEmergencyFlashEnabled(adsbEmergencyFlashEnabled)
        }

    private fun captureOverlayFrontOrderSignature(): OverlayFrontOrderSignature? {
        val map = mapState.mapLibreMap ?: return null
        val style = map.style ?: return null
        return OverlayFrontOrderSignature(
            mapId = System.identityHashCode(map),
            styleId = System.identityHashCode(style),
            layerCount = style.layers.size,
            topLayerId = style.layers.lastOrNull()?.id,
            blueOverlayId = mapState.blueLocationOverlay?.let { System.identityHashCode(it) } ?: 0,
            ognOverlayId = mapState.ognTrafficOverlay?.let { System.identityHashCode(it) } ?: 0,
            ognTargetRingOverlayId = mapState.ognTargetRingOverlay?.let { System.identityHashCode(it) } ?: 0,
            ognTargetLineOverlayId = mapState.ognTargetLineOverlay?.let { System.identityHashCode(it) } ?: 0,
            adsbOverlayId = mapState.adsbTrafficOverlay?.let { System.identityHashCode(it) } ?: 0
        )
    }

    private data class OverlayFrontOrderSignature(
        val mapId: Int,
        val styleId: Int,
        val layerCount: Int,
        val topLayerId: String?,
        val blueOverlayId: Int,
        val ognOverlayId: Int,
        val ognTargetRingOverlayId: Int,
        val ognTargetLineOverlayId: Int,
        val adsbOverlayId: Int
    )

    private data class AdsbRenderThrottleState(
        var lastRenderMonoMs: Long = 0L,
        var pendingJob: Job? = null
    )
}
