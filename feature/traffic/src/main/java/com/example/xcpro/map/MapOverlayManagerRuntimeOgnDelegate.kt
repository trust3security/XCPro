package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

class MapOverlayManagerRuntimeOgnDelegate(
    private val runtimeState: TrafficOverlayRuntimeState,
    private val coroutineScope: CoroutineScope,
    private val context: Context,
    private val ognTrafficOverlayFactory: OgnTrafficOverlayFactory,
    private val ognTargetRingOverlayFactory: OgnTargetRingOverlayFactory,
    private val ognTargetLineOverlayFactory: OgnTargetLineOverlayFactory,
    private val ognOwnshipTargetBadgeOverlayFactory: OgnOwnshipTargetBadgeOverlayFactory,
    private val ognThermalOverlayFactory: OgnThermalOverlayFactory,
    private val ognGliderTrailOverlayFactory: OgnGliderTrailOverlayFactory,
    private val ognSelectedThermalOverlayFactory: OgnSelectedThermalOverlayFactory,
    private val bringTrafficOverlaysToFront: () -> Unit,
    private val satelliteContrastIconsEnabled: () -> Boolean,
    private val normalizeOwnshipAltitudeForRender: (Double?) -> Double?,
    private val nowMonoMs: () -> Long
) {
    private var latestOgnTargets: List<OgnTrafficTarget> = emptyList()
    private var latestSelectedTargetKey: String? = null
    private var latestOgnOwnshipAltitudeMeters: Double? = null
    private var latestOgnAltitudeUnit: AltitudeUnit = AltitudeUnit.METERS
    private var latestOgnUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var latestOgnThermalHotspots: List<OgnThermalHotspot> = emptyList()
    private var latestOgnGliderTrailSegments: List<OgnGliderTrailSegment> = emptyList()
    private var latestOgnGliderTrailSignature: Int = gliderTrailSegmentIdentitySignature(emptyList())
    private var latestSelectedThermalContext: SelectedOgnThermalOverlayContext? = null
    private var latestTargetEnabled: Boolean = false
    private var latestResolvedTarget: OgnTrafficTarget? = null
    private var latestOwnshipLocation: OverlayCoordinate? = null
    private var latestTargetOwnshipAltitudeMeters: Double? = null
    private var latestTargetAltitudeUnit: AltitudeUnit = AltitudeUnit.METERS
    private var latestTargetUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var ognIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
    private var ognViewportZoom: Float? = null
    private var renderedOgnIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
    private var ognDisplayUpdateMode: OgnDisplayUpdateMode = OgnDisplayUpdateMode.DEFAULT
    private val ognTrafficRenderState = OgnRenderThrottleState()
    private val ognTargetVisualsRenderState = OgnRenderThrottleState()
    private val ognThermalRenderState = OgnRenderThrottleState()
    private val ognTrailRenderState = OgnRenderThrottleState()
    private val ognSelectedThermalRenderState = OgnRenderThrottleState()
    private var mapInteractionActive: Boolean = false

    fun initializeTrafficOverlays(map: MapLibreMap?) {
        if (map == null) return
        cancelPendingRenders()
        renderedOgnIconSizePx = resolveRenderedOgnIconSizePx()
        runtimeState.ognOwnshipTargetBadgeOverlay?.cleanup()
        runtimeState.ognTargetLineOverlay?.cleanup()
        runtimeState.ognTargetRingOverlay?.cleanup()
        runtimeState.ognTrafficOverlay?.cleanup()
        runtimeState.ognTrafficOverlay = createOgnTrafficOverlay(map)
        runtimeState.ognTrafficOverlay?.initialize()
        runtimeState.ognTrafficOverlay?.render(
            targets = latestOgnTargets,
            selectedTargetKey = latestSelectedTargetKey,
            ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
            altitudeUnit = latestOgnAltitudeUnit,
            unitsPreferences = latestOgnUnitsPreferences
        )
        runtimeState.ognTargetRingOverlay = createTargetRingOverlay(map)
        runtimeState.ognTargetRingOverlay?.initialize()
        runtimeState.ognTargetRingOverlay?.render(
            enabled = latestTargetEnabled,
            target = latestResolvedTarget
        )

        runtimeState.ognTargetLineOverlay = ognTargetLineOverlayFactory(map)
        runtimeState.ognTargetLineOverlay?.initialize()
        runtimeState.ognTargetLineOverlay?.render(
            enabled = latestTargetEnabled,
            ownshipLocation = latestOwnshipLocation,
            target = latestResolvedTarget
        )
        runtimeState.ognOwnshipTargetBadgeOverlay = ognOwnshipTargetBadgeOverlayFactory(map)
        runtimeState.ognOwnshipTargetBadgeOverlay?.initialize()
        runtimeState.ognOwnshipTargetBadgeOverlay?.render(
            enabled = latestTargetEnabled,
            ownshipLocation = latestOwnshipLocation,
            target = latestResolvedTarget,
            ownshipAltitudeMeters = latestTargetOwnshipAltitudeMeters,
            altitudeUnit = latestTargetAltitudeUnit,
            unitsPreferences = latestTargetUnitsPreferences
        )

        runtimeState.ognThermalOverlay?.cleanup()
        runtimeState.ognThermalOverlay = ognThermalOverlayFactory(map)
        runtimeState.ognThermalOverlay?.initialize()
        runtimeState.ognThermalOverlay?.render(latestOgnThermalHotspots)

        runtimeState.ognGliderTrailOverlay?.cleanup()
        runtimeState.ognGliderTrailOverlay = ognGliderTrailOverlayFactory(map)
        runtimeState.ognGliderTrailOverlay?.initialize()
        runtimeState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)

        runtimeState.ognSelectedThermalOverlay?.cleanup()
        runtimeState.ognSelectedThermalOverlay = ognSelectedThermalOverlayFactory(map)
        runtimeState.ognSelectedThermalOverlay?.initialize()
        runtimeState.ognSelectedThermalOverlay?.render(latestSelectedThermalContext)

        bringTrafficOverlaysToFront()
        val nowMonoMs = nowMonoMs()
        ognTrafficRenderState.lastRenderMonoMs = nowMonoMs
        ognTargetVisualsRenderState.lastRenderMonoMs = nowMonoMs
        ognThermalRenderState.lastRenderMonoMs = nowMonoMs
        ognTrailRenderState.lastRenderMonoMs = nowMonoMs
        ognSelectedThermalRenderState.lastRenderMonoMs = nowMonoMs
    }

    fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        if (ognDisplayUpdateMode == mode) return
        ognDisplayUpdateMode = mode
        cancelPendingRenders()
        renderTargetsNow()
        renderTargetVisualsNow()
        renderThermalsNow()
        renderTrailsNow()
        renderSelectedThermalNow()
        val nowMonoMs = nowMonoMs()
        ognTrafficRenderState.lastRenderMonoMs = nowMonoMs
        ognTargetVisualsRenderState.lastRenderMonoMs = nowMonoMs
        ognThermalRenderState.lastRenderMonoMs = nowMonoMs
        ognTrailRenderState.lastRenderMonoMs = nowMonoMs
        ognSelectedThermalRenderState.lastRenderMonoMs = nowMonoMs
    }

    fun setMapInteractionActive(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
        if (!active) flushDeferredRenders()
    }

    fun updateTrafficTargets(
        targets: List<OgnTrafficTarget>,
        selectedTargetKey: String? = null,
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
        val sameSelectedTargetKey = latestSelectedTargetKey == selectedTargetKey
        if (
            sameTargets &&
            sameSelectedTargetKey &&
            sameOwnshipAltitude &&
            sameAltitudeUnit &&
            sameUnitsPreferences &&
            !forceImmediate &&
            runtimeState.ognTrafficOverlay != null
        ) {
            return
        }
        latestOgnTargets = targets
        latestSelectedTargetKey = selectedTargetKey
        latestOgnOwnshipAltitudeMeters = normalizedOwnshipAltitude
        latestOgnAltitudeUnit = altitudeUnit
        latestOgnUnitsPreferences = unitsPreferences
        scheduleRender(
            state = ognTrafficRenderState,
            forceImmediate = forceImmediate || targets.isEmpty()
        ) {
            renderTargetsNow()
        }
    }

    fun updateTargetVisuals(
        enabled: Boolean,
        resolvedTarget: OgnTrafficTarget?,
        ownshipLocation: OverlayCoordinate?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences,
        forceImmediate: Boolean = false
    ) {
        val sameEnabled = latestTargetEnabled == enabled
        val sameResolvedTarget = latestResolvedTarget == resolvedTarget
        val sameOwnshipLocation = latestOwnshipLocation == ownshipLocation
        val normalizedOwnshipAltitude = normalizeOwnshipAltitudeForRender(ownshipAltitudeMeters)
        val sameOwnshipAltitude = latestTargetOwnshipAltitudeMeters == normalizedOwnshipAltitude
        val sameAltitudeUnit = latestTargetAltitudeUnit == altitudeUnit
        val sameUnitsPreferences = latestTargetUnitsPreferences == unitsPreferences
        if (
            sameEnabled &&
            sameResolvedTarget &&
            sameOwnshipLocation &&
            sameOwnshipAltitude &&
            sameAltitudeUnit &&
            sameUnitsPreferences &&
            !forceImmediate &&
            runtimeState.ognTargetRingOverlay != null &&
            runtimeState.ognTargetLineOverlay != null &&
            runtimeState.ognOwnshipTargetBadgeOverlay != null
        ) {
            return
        }
        latestTargetEnabled = enabled
        latestResolvedTarget = resolvedTarget
        latestOwnshipLocation = ownshipLocation
        latestTargetOwnshipAltitudeMeters = normalizedOwnshipAltitude
        latestTargetAltitudeUnit = altitudeUnit
        latestTargetUnitsPreferences = unitsPreferences
        scheduleRender(
            state = ognTargetVisualsRenderState,
            forceImmediate = forceImmediate || !enabled || resolvedTarget == null || ownshipLocation == null
        ) {
            renderTargetVisualsNow()
        }
    }

    fun updateThermalHotspots(hotspots: List<OgnThermalHotspot>, forceImmediate: Boolean = false) {
        val sameHotspots = latestOgnThermalHotspots == hotspots
        if (sameHotspots && !forceImmediate && runtimeState.ognThermalOverlay != null) return
        latestOgnThermalHotspots = hotspots
        scheduleRender(state = ognThermalRenderState, forceImmediate = forceImmediate || hotspots.isEmpty()) {
            renderThermalsNow()
        }
    }

    fun updateGliderTrailSegments(segments: List<OgnGliderTrailSegment>, forceImmediate: Boolean = false) {
        val incomingSignature = gliderTrailSegmentIdentitySignature(segments)
        val sameSegments = latestOgnGliderTrailSegments.size == segments.size &&
            latestOgnGliderTrailSignature == incomingSignature &&
            sameGliderTrailSegmentsByIdentity(latestOgnGliderTrailSegments, segments)
        if (sameSegments && !forceImmediate && runtimeState.ognGliderTrailOverlay != null) return
        latestOgnGliderTrailSegments = segments
        latestOgnGliderTrailSignature = incomingSignature
        scheduleRender(state = ognTrailRenderState, forceImmediate = forceImmediate || segments.isEmpty()) { renderTrailsNow() }
    }

    fun updateSelectedThermalContext(
        context: SelectedOgnThermalOverlayContext?,
        forceImmediate: Boolean = false
    ) {
        val sameContext = latestSelectedThermalContext == context
        if (sameContext && !forceImmediate && runtimeState.ognSelectedThermalOverlay != null) return
        latestSelectedThermalContext = context
        scheduleRender(state = ognSelectedThermalRenderState, forceImmediate = forceImmediate || context == null) {
            renderSelectedThermalNow()
        }
    }

    fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        val sizeChanged = ognIconSizePx != clamped
        ognIconSizePx = clamped
        if (!sizeChanged && renderedOgnIconSizePx == resolveRenderedOgnIconSizePx()) return
        applyResolvedIconSizeToLiveOverlays()
    }

    fun setViewportZoom(zoomLevel: Float) {
        val normalizedZoom = zoomLevel.takeIf { it.isFinite() } ?: return
        if (ognViewportZoom == normalizedZoom) return
        ognViewportZoom = normalizedZoom
        runtimeState.ognTrafficOverlay?.setViewportZoom(normalizedZoom)
        applyResolvedIconSizeToLiveOverlays()
    }

    fun invalidateProjection(forceImmediate: Boolean = false) {
        if (latestOgnTargets.isEmpty()) return
        syncViewportZoomFromMapIfAvailable()
        scheduleRender(
            state = ognTrafficRenderState,
            forceImmediate = forceImmediate,
            intervalMsOverride = TRAFFIC_PROJECTION_INVALIDATION_MIN_RENDER_INTERVAL_MS
        ) {
            renderTargetsNow()
        }
    }

    fun findTargetAt(tap: LatLng): String? =
        runtimeState.ognTargetRingOverlay?.findTargetAt(tap)?.takeUnless { it.isBlank() }
            ?: runtimeState.ognTrafficOverlay?.findTargetAt(tap)

    fun findThermalHotspotAt(tap: LatLng): String? = runtimeState.ognThermalOverlay?.findTargetAt(tap)

    fun bringOverlaysToFront() {
        runtimeState.ognTargetLineOverlay?.bringToFront(); runtimeState.ognTrafficOverlay?.bringToFront()
        runtimeState.ognTargetRingOverlay?.bringToFront(); runtimeState.ognOwnshipTargetBadgeOverlay?.bringToFront()
    }

    fun applySatelliteContrastIcons(enabled: Boolean) {
        runtimeState.ognTrafficOverlay?.setUseSatelliteContrastIcons(enabled)
        if (runtimeState.ognTrafficOverlay != null || latestOgnTargets.isNotEmpty()) {
            updateTrafficTargets(
                targets = latestOgnTargets,
                selectedTargetKey = latestSelectedTargetKey,
                ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
                altitudeUnit = latestOgnAltitudeUnit,
                unitsPreferences = latestOgnUnitsPreferences,
                forceImmediate = true
            )
        }
    }

    fun onMapDetached() {
        mapInteractionActive = false
        ognViewportZoom = null
        renderedOgnIconSizePx = ognIconSizePx
        cancelPendingRenders()
    }

    fun statusSnapshot(): OgnOverlayStatusSnapshot = OgnOverlayStatusSnapshot(
        displayUpdateMode = ognDisplayUpdateMode,
        targetsCount = latestOgnTargets.size,
        thermalHotspotsCount = latestOgnThermalHotspots.size,
        gliderTrailSegmentsCount = latestOgnGliderTrailSegments.size,
        targetEnabled = latestTargetEnabled,
        targetResolved = latestResolvedTarget != null
    )

    private fun createOgnTrafficOverlay(map: MapLibreMap): OgnTrafficOverlayHandle =
        ognTrafficOverlayFactory(
            context,
            map,
            renderedOgnIconSizePx,
            satelliteContrastIconsEnabled()
        ).also { overlay ->
            ognViewportZoom?.let(overlay::setViewportZoom)
        }

    private fun createTargetRingOverlay(map: MapLibreMap): OgnTargetRingOverlayHandle = ognTargetRingOverlayFactory(map, renderedOgnIconSizePx)

    private fun renderTargetsNow() {
        val map = runtimeState.mapLibreMap ?: return
        if (runtimeState.ognTrafficOverlay == null) {
            renderedOgnIconSizePx = resolveRenderedOgnIconSizePx()
            runtimeState.ognTrafficOverlay = createOgnTrafficOverlay(map)
            runtimeState.ognTrafficOverlay?.initialize()
        }
        runtimeState.ognTrafficOverlay?.render(
            targets = latestOgnTargets,
            selectedTargetKey = latestSelectedTargetKey,
            ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
            altitudeUnit = latestOgnAltitudeUnit,
            unitsPreferences = latestOgnUnitsPreferences
        )
    }

    private fun renderThermalsNow() {
        val map = runtimeState.mapLibreMap ?: return
        if (runtimeState.ognThermalOverlay == null) {
            runtimeState.ognThermalOverlay = ognThermalOverlayFactory(map)
            runtimeState.ognThermalOverlay?.initialize()
        }
        runCatching {
            runtimeState.ognThermalOverlay?.render(latestOgnThermalHotspots)
        }.onFailure { throwable ->
            Log.e(TAG, "OGN thermal overlay render failed: ${throwable.message}", throwable)
        }
    }

    private fun renderTrailsNow() {
        val map = runtimeState.mapLibreMap ?: return
        if (runtimeState.ognGliderTrailOverlay == null) {
            runtimeState.ognGliderTrailOverlay = ognGliderTrailOverlayFactory(map)
            runtimeState.ognGliderTrailOverlay?.initialize()
        }
        runCatching {
            runtimeState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to render OGN glider trails: ${throwable.message}", throwable)
        }
    }

    private fun renderSelectedThermalNow() {
        val map = runtimeState.mapLibreMap ?: return
        if (runtimeState.ognSelectedThermalOverlay == null) {
            runtimeState.ognSelectedThermalOverlay = ognSelectedThermalOverlayFactory(map)
            runtimeState.ognSelectedThermalOverlay?.initialize()
        }
        runCatching {
            runtimeState.ognSelectedThermalOverlay?.render(latestSelectedThermalContext)
        }.onFailure { throwable ->
            AppLogger.e(TAG, "Failed to render selected OGN thermal context: ${throwable.message}", throwable)
        }
    }

    private fun renderTargetVisualsNow() {
        val map = runtimeState.mapLibreMap ?: return
        if (runtimeState.ognTargetRingOverlay == null) {
            renderedOgnIconSizePx = resolveRenderedOgnIconSizePx()
            runtimeState.ognTargetRingOverlay = createTargetRingOverlay(map)
            runtimeState.ognTargetRingOverlay?.initialize()
        }
        if (runtimeState.ognTargetLineOverlay == null) {
            runtimeState.ognTargetLineOverlay = ognTargetLineOverlayFactory(map)
            runtimeState.ognTargetLineOverlay?.initialize()
        }
        if (runtimeState.ognOwnshipTargetBadgeOverlay == null) {
            runtimeState.ognOwnshipTargetBadgeOverlay = ognOwnshipTargetBadgeOverlayFactory(map)
            runtimeState.ognOwnshipTargetBadgeOverlay?.initialize()
        }
        runCatching {
            runtimeState.ognTargetRingOverlay?.render(
                enabled = latestTargetEnabled,
                target = latestResolvedTarget
            )
            runtimeState.ognTargetLineOverlay?.render(
                enabled = latestTargetEnabled,
                ownshipLocation = latestOwnshipLocation,
                target = latestResolvedTarget
            )
            runtimeState.ognOwnshipTargetBadgeOverlay?.render(
                enabled = latestTargetEnabled,
                ownshipLocation = latestOwnshipLocation,
                target = latestResolvedTarget,
                ownshipAltitudeMeters = latestTargetOwnshipAltitudeMeters,
                altitudeUnit = latestTargetAltitudeUnit,
                unitsPreferences = latestTargetUnitsPreferences
            )
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to render OGN target visuals: ${throwable.message}", throwable)
        }
    }

    private fun applyResolvedIconSizeToLiveOverlays() {
        val resolvedSizePx = resolveRenderedOgnIconSizePx()
        if (renderedOgnIconSizePx == resolvedSizePx) return
        renderedOgnIconSizePx = resolvedSizePx
        runtimeState.ognTrafficOverlay?.setIconSizePx(resolvedSizePx)
        runtimeState.ognTargetRingOverlay?.setIconSizePx(resolvedSizePx)
    }

    private fun syncViewportZoomFromMapIfAvailable() {
        val liveZoom = runtimeState.mapLibreMap?.cameraPosition?.zoom?.toFloat()
            ?.takeIf { it.isFinite() }
            ?: return
        if (ognViewportZoom == liveZoom) return
        ognViewportZoom = liveZoom
        runtimeState.ognTrafficOverlay?.setViewportZoom(liveZoom)
        applyResolvedIconSizeToLiveOverlays()
    }

    private fun resolveRenderedOgnIconSizePx(): Int = resolveOgnTrafficViewportSizing(
        baseIconSizePx = ognIconSizePx,
        zoomLevel = ognViewportZoom
    ).renderedIconSizePx

    private fun scheduleRender(
        state: OgnRenderThrottleState,
        forceImmediate: Boolean,
        intervalMsOverride: Long? = null,
        renderNow: () -> Unit
    ) {
        val map = runtimeState.mapLibreMap ?: return
        val intervalMs = intervalMsOverride ?: resolveInteractionAwareIntervalMs(
            baseIntervalMs = ognDisplayUpdateMode.renderIntervalMs,
            interactionActive = mapInteractionActive,
            interactionFloorMs = OGN_INTERACTION_MIN_RENDER_INTERVAL_MS
        )

        if (forceImmediate || intervalMs <= 0L) {
            state.pendingJob?.cancel()
            state.pendingJob = null
            state.pendingDueMonoMs = Long.MAX_VALUE
            renderNow()
            state.lastRenderMonoMs = nowMonoMs()
            return
        }

        val nowMonoMs = nowMonoMs()
        val elapsedMs = nowMonoMs - state.lastRenderMonoMs
        if (elapsedMs >= intervalMs && state.pendingJob == null) {
            renderNow()
            state.lastRenderMonoMs = nowMonoMs
            return
        }

        val remainingMs = (intervalMs - elapsedMs).coerceAtLeast(0L)
        val scheduledDueMonoMs = nowMonoMs + remainingMs
        if (state.pendingJob != null && state.pendingDueMonoMs <= scheduledDueMonoMs) return
        state.pendingJob?.cancel()
        state.pendingJob = coroutineScope.launch {
            delay(remainingMs)
            state.pendingJob = null
            state.pendingDueMonoMs = Long.MAX_VALUE
            if (runtimeState.mapLibreMap != map || runtimeState.mapLibreMap == null) return@launch
            renderNow()
            state.lastRenderMonoMs = nowMonoMs()
        }
        state.pendingDueMonoMs = scheduledDueMonoMs
    }

    private fun flushDeferredRenders() {
        val nowMonoMs = nowMonoMs()
        val states = listOf(
            ognTrafficRenderState to ::renderTargetsNow,
            ognTargetVisualsRenderState to ::renderTargetVisualsNow,
            ognThermalRenderState to ::renderThermalsNow,
            ognTrailRenderState to ::renderTrailsNow,
            ognSelectedThermalRenderState to ::renderSelectedThermalNow
        )
        states.forEach { (state, renderNow) ->
            val pending = state.pendingJob ?: return@forEach
            pending.cancel()
            state.pendingJob = null
            state.pendingDueMonoMs = Long.MAX_VALUE
            renderNow()
            state.lastRenderMonoMs = nowMonoMs
        }
    }

    private fun cancelPendingRenders() {
        ognTrafficRenderState.pendingJob?.cancel()
        ognTrafficRenderState.pendingJob = null
        ognTrafficRenderState.pendingDueMonoMs = Long.MAX_VALUE
        ognTargetVisualsRenderState.pendingJob?.cancel()
        ognTargetVisualsRenderState.pendingJob = null
        ognTargetVisualsRenderState.pendingDueMonoMs = Long.MAX_VALUE
        ognThermalRenderState.pendingJob?.cancel()
        ognThermalRenderState.pendingJob = null
        ognThermalRenderState.pendingDueMonoMs = Long.MAX_VALUE
        ognTrailRenderState.pendingJob?.cancel()
        ognTrailRenderState.pendingJob = null
        ognTrailRenderState.pendingDueMonoMs = Long.MAX_VALUE
        ognSelectedThermalRenderState.pendingJob?.cancel()
        ognSelectedThermalRenderState.pendingJob = null
        ognSelectedThermalRenderState.pendingDueMonoMs = Long.MAX_VALUE
    }

    private companion object {
        private const val TAG = "MapOverlayManager"
    }
}
