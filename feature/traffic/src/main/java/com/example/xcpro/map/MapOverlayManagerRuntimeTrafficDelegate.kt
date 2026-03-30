package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapOverlayManagerRuntimeTrafficDelegate(
    private val runtimeState: TrafficOverlayRuntimeState,
    private val coroutineScope: CoroutineScope,
    private val adsbTrafficOverlayFactory: AdsbTrafficOverlayFactory,
    private val context: Context,
    private val interactionActiveProvider: () -> Boolean,
    private val bringOgnOverlaysToFront: () -> Unit,
    private val nowMonoMs: () -> Long
) {
    private var latestAdsbTargets: List<AdsbTrafficUiModel> = emptyList()
    private var latestSelectedTargetId: Icao24? = null
    private var latestAdsbOwnshipAltitudeMeters: Double? = null
    private var latestAdsbUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var adsbIconSizePx: Int = ADSB_ICON_SIZE_DEFAULT_PX
    private var adsbViewportZoom: Float? = null
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
        adsbRenderState.pendingDueMonoMs = Long.MAX_VALUE
        runtimeState.adsbTrafficOverlay?.cleanup()
        if (map == null) return
        runtimeState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
        runtimeState.adsbTrafficOverlay?.initialize()
        runtimeState.adsbTrafficOverlay?.setViewportZoom(resolveAdsbViewportZoom(map))
        val projectedStyleIds = projectedAdsbStyleIds(nowMonoMs())
        runtimeState.adsbTrafficOverlay?.render(
            targets = latestAdsbTargets,
            selectedTargetId = latestSelectedTargetId,
            ownshipAltitudeMeters = latestAdsbOwnshipAltitudeMeters,
            unitsPreferences = latestAdsbUnitsPreferences,
            iconStyleIdOverrides = projectedStyleIds
        )
        adsbRenderState.lastRenderMonoMs = nowMonoMs()
        bringTrafficOverlaysToFront()
    }

    fun updateAdsbTrafficTargets(
        targets: List<AdsbTrafficUiModel>,
        selectedTargetId: Icao24? = null,
        ownshipAltitudeMeters: Double?,
        unitsPreferences: UnitsPreferences,
        normalizeOwnshipAltitudeForRender: (Double?) -> Double?
    ) {
        val previouslyHadNoTargets = latestAdsbTargets.isEmpty()
        val normalizedOwnshipAltitude = normalizeOwnshipAltitudeForRender(ownshipAltitudeMeters)
        val sameTargets = latestAdsbTargets == targets
        val sameSelectedTarget = latestSelectedTargetId == selectedTargetId
        val sameOwnshipAltitude = latestAdsbOwnshipAltitudeMeters == normalizedOwnshipAltitude
        val sameUnitsPreferences = latestAdsbUnitsPreferences == unitsPreferences
        if (
            sameTargets &&
            sameSelectedTarget &&
            sameOwnshipAltitude &&
            sameUnitsPreferences &&
            runtimeState.adsbTrafficOverlay != null
        ) {
            return
        }
        latestAdsbTargets = targets
        latestSelectedTargetId = selectedTargetId
        latestAdsbOwnshipAltitudeMeters = normalizedOwnshipAltitude
        latestAdsbUnitsPreferences = unitsPreferences
        scheduleAdsbRender(forceImmediate = targets.isEmpty() || previouslyHadNoTargets)
    }

    fun setAdsbIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        adsbIconSizePx = clamped
        runtimeState.adsbTrafficOverlay?.setIconSizePx(clamped)
    }

    fun setAdsbViewportZoom(zoomLevel: Float) {
        val normalizedZoom = zoomLevel.takeIf { it.isFinite() } ?: return
        val zoomChanged = adsbViewportZoom != normalizedZoom
        adsbViewportZoom = normalizedZoom
        runtimeState.adsbTrafficOverlay?.setViewportZoom(normalizedZoom)
        if (zoomChanged && latestAdsbTargets.isNotEmpty()) {
            scheduleAdsbRender(forceImmediate = true)
        }
    }

    fun invalidateProjection(forceImmediate: Boolean = false) {
        if (latestAdsbTargets.isEmpty()) return
        syncViewportZoomFromMapIfAvailable()
        scheduleAdsbRender(
            forceImmediate = forceImmediate,
            intervalMsOverride = TRAFFIC_PROJECTION_INVALIDATION_MIN_RENDER_INTERVAL_MS
        )
    }

    fun setAdsbEmergencyFlashEnabled(enabled: Boolean) {
        adsbEmergencyFlashEnabled = enabled
        runtimeState.adsbTrafficOverlay?.setEmergencyFlashEnabled(enabled)
    }

    fun setAdsbDefaultMediumUnknownIconEnabled(enabled: Boolean) {
        if (defaultMediumUnknownIconEnabled == enabled) return
        defaultMediumUnknownIconEnabled = enabled
        scheduleAdsbRender(forceImmediate = true)
    }

    fun findAdsbTargetAt(tap: LatLng): Icao24? {
        return runtimeState.adsbTrafficOverlay?.findTargetAt(tap)
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
        adsbRenderState.pendingDueMonoMs = Long.MAX_VALUE
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
        runtimeState.bringBlueLocationOverlayToFront()
        bringOgnOverlaysToFront()
        runtimeState.adsbTrafficOverlay?.bringToFront()
        overlayFrontOrderApplyCount += 1
        lastOverlayFrontOrderApplyMonoMs = nowMs
        lastOverlayFrontOrderSignature = captureOverlayFrontOrderSignature()
    }

    fun flushDeferredAdsbRenderIfNeeded() {
        val pending = adsbRenderState.pendingJob ?: return
        pending.cancel()
        adsbRenderState.pendingJob = null
        adsbRenderState.pendingDueMonoMs = Long.MAX_VALUE
        val map = runtimeState.mapLibreMap ?: return
        renderAdsbNow(map)
    }

    private fun scheduleAdsbRender(
        forceImmediate: Boolean,
        intervalMsOverride: Long? = null
    ) {
        val map = runtimeState.mapLibreMap ?: return
        val intervalMs = intervalMsOverride ?: resolveInteractionAwareIntervalMs(
            baseIntervalMs = 0L,
            interactionActive = interactionActiveProvider(),
            interactionFloorMs = ADSB_INTERACTION_MIN_RENDER_INTERVAL_MS
        )

        if (forceImmediate || intervalMs <= 0L) {
            adsbRenderState.pendingJob?.cancel()
            adsbRenderState.pendingJob = null
            adsbRenderState.pendingDueMonoMs = Long.MAX_VALUE
            renderAdsbNow(map)
            return
        }

        val nowMs = nowMonoMs()
        val elapsedMs = nowMs - adsbRenderState.lastRenderMonoMs
        if (elapsedMs >= intervalMs && adsbRenderState.pendingJob == null) {
            renderAdsbNow(map)
            return
        }

        val remainingMs = (intervalMs - elapsedMs).coerceAtLeast(0L)
        val scheduledDueMonoMs = nowMs + remainingMs
        if (adsbRenderState.pendingJob != null && adsbRenderState.pendingDueMonoMs <= scheduledDueMonoMs) {
            return
        }

        adsbRenderState.pendingJob?.cancel()
        adsbRenderState.pendingJob = coroutineScope.launch {
            delay(remainingMs)
            adsbRenderState.pendingJob = null
            adsbRenderState.pendingDueMonoMs = Long.MAX_VALUE
            val currentMap = runtimeState.mapLibreMap
            if (currentMap == null || currentMap !== map) return@launch
            renderAdsbNow(currentMap)
        }
        adsbRenderState.pendingDueMonoMs = scheduledDueMonoMs
    }

    private fun renderAdsbNow(map: MapLibreMap) {
        val renderMonoMs = nowMonoMs()
        val projectedStyleIds = projectedAdsbStyleIds(renderMonoMs)
        if (runtimeState.adsbTrafficOverlay == null) {
            runtimeState.adsbTrafficOverlay = createAdsbTrafficOverlay(map)
            runtimeState.adsbTrafficOverlay?.initialize()
            runtimeState.adsbTrafficOverlay?.setViewportZoom(resolveAdsbViewportZoom(map))
        }
        adsbIconTelemetryTracker.onRenderedTargets(
            targets = latestAdsbTargets,
            nowMonoMs = renderMonoMs,
            iconStyleIdOverrides = projectedStyleIds
        )
        runtimeState.adsbTrafficOverlay?.render(
            targets = latestAdsbTargets,
            selectedTargetId = latestSelectedTargetId,
            ownshipAltitudeMeters = latestAdsbOwnshipAltitudeMeters,
            unitsPreferences = latestAdsbUnitsPreferences,
            iconStyleIdOverrides = projectedStyleIds
        )
        adsbRenderState.lastRenderMonoMs = renderMonoMs
        bringTrafficOverlaysToFront()
    }

    private fun createAdsbTrafficOverlay(map: MapLibreMap): AdsbTrafficOverlayHandle =
        adsbTrafficOverlayFactory(context, map, adsbIconSizePx).also { overlay ->
            overlay.setEmergencyFlashEnabled(adsbEmergencyFlashEnabled)
        }

    private fun resolveAdsbViewportZoom(map: MapLibreMap): Float =
        adsbViewportZoom
            ?: map.cameraPosition?.zoom?.toFloat()?.takeIf { it.isFinite() }
            ?: ADSB_VIEWPORT_ZOOM_FALLBACK

    private fun syncViewportZoomFromMapIfAvailable() {
        val liveZoom = runtimeState.mapLibreMap?.cameraPosition?.zoom?.toFloat()
            ?.takeIf { it.isFinite() }
            ?: return
        if (adsbViewportZoom == liveZoom) return
        adsbViewportZoom = liveZoom
        runtimeState.adsbTrafficOverlay?.setViewportZoom(liveZoom)
    }

    private fun projectedAdsbStyleIds(renderMonoMs: Long): Map<String, String> =
        stickyIconProjectionCache.projectStyleImageIds(
            targets = latestAdsbTargets,
            nowMonoMs = renderMonoMs,
            defaultMediumUnknownIconEnabled = defaultMediumUnknownIconEnabled
        )

    private fun captureOverlayFrontOrderSignature(): OverlayFrontOrderSignature? {
        val map = runtimeState.mapLibreMap ?: return null
        val style = map.style ?: return null
        return OverlayFrontOrderSignature(
            mapId = System.identityHashCode(map),
            styleId = System.identityHashCode(style),
            layerCount = style.layers.size,
            topLayerId = style.layers.lastOrNull()?.id,
            blueOverlayId = 0,
            ognOverlayId = runtimeState.ognTrafficOverlay?.let { System.identityHashCode(it) } ?: 0,
            ognTargetRingOverlayId = runtimeState.ognTargetRingOverlay?.let { System.identityHashCode(it) } ?: 0,
            ognTargetLineOverlayId = runtimeState.ognTargetLineOverlay?.let { System.identityHashCode(it) } ?: 0,
            ognOwnshipTargetBadgeOverlayId = runtimeState.ognOwnshipTargetBadgeOverlay
                ?.let { System.identityHashCode(it) }
                ?: 0,
            adsbOverlayId = runtimeState.adsbTrafficOverlay?.let { System.identityHashCode(it) } ?: 0
        )
    }

    private companion object {
        private const val ADSB_VIEWPORT_ZOOM_FALLBACK = 10f
    }
}
