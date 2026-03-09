package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal class MapOverlayManagerRuntimeOgnDelegate(
    private val mapState: MapScreenState,
    private val coroutineScope: CoroutineScope,
    private val ognTrafficOverlayFactory: (MapLibreMap, Int, Boolean) -> OgnTrafficOverlay,
    private val ognTargetRingOverlayFactory: (MapLibreMap, Int) -> OgnTargetRingOverlay,
    private val ognTargetLineOverlayFactory: (MapLibreMap) -> OgnTargetLineOverlay,
    private val ognThermalOverlayFactory: (MapLibreMap) -> OgnThermalOverlay,
    private val ognGliderTrailOverlayFactory: (MapLibreMap) -> OgnGliderTrailOverlay,
    private val bringTrafficOverlaysToFront: () -> Unit,
    private val satelliteContrastIconsEnabled: () -> Boolean,
    private val normalizeOwnshipAltitudeForRender: (Double?) -> Double?,
    private val nowMonoMs: () -> Long
) {
    private var latestOgnTargets: List<OgnTrafficTarget> = emptyList()
    private var latestOgnOwnshipAltitudeMeters: Double? = null
    private var latestOgnAltitudeUnit: AltitudeUnit = AltitudeUnit.METERS
    private var latestOgnUnitsPreferences: UnitsPreferences = UnitsPreferences()
    private var latestOgnThermalHotspots: List<OgnThermalHotspot> = emptyList()
    private var latestOgnGliderTrailSegments: List<OgnGliderTrailSegment> = emptyList()
    private var latestOgnGliderTrailSignature: Int = gliderTrailSegmentIdentitySignature(emptyList())
    private var latestTargetEnabled: Boolean = false
    private var latestResolvedTarget: OgnTrafficTarget? = null
    private var latestOwnshipLocation: MapLocationUiModel? = null
    private var ognIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
    private var ognDisplayUpdateMode: OgnDisplayUpdateMode = OgnDisplayUpdateMode.DEFAULT
    private val ognTrafficRenderState = OgnRenderThrottleState()
    private val ognTargetVisualsRenderState = OgnRenderThrottleState()
    private val ognThermalRenderState = OgnRenderThrottleState()
    private val ognTrailRenderState = OgnRenderThrottleState()
    private var mapInteractionActive: Boolean = false

    fun initializeTrafficOverlays(map: MapLibreMap?) {
        if (map == null) return
        cancelPendingRenders()
        mapState.ognTargetLineOverlay?.cleanup()
        mapState.ognTargetRingOverlay?.cleanup()
        mapState.ognTrafficOverlay?.cleanup()
        mapState.ognTrafficOverlay = createOgnTrafficOverlay(map)
        mapState.ognTrafficOverlay?.initialize()
        mapState.ognTrafficOverlay?.render(
            targets = latestOgnTargets,
            ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
            altitudeUnit = latestOgnAltitudeUnit,
            unitsPreferences = latestOgnUnitsPreferences
        )
        mapState.ognTargetRingOverlay = ognTargetRingOverlayFactory(map, ognIconSizePx)
        mapState.ognTargetRingOverlay?.initialize()
        mapState.ognTargetRingOverlay?.render(
            enabled = latestTargetEnabled,
            target = latestResolvedTarget
        )

        mapState.ognTargetLineOverlay = ognTargetLineOverlayFactory(map)
        mapState.ognTargetLineOverlay?.initialize()
        mapState.ognTargetLineOverlay?.render(
            enabled = latestTargetEnabled,
            ownshipLocation = latestOwnshipLocation,
            target = latestResolvedTarget
        )

        mapState.ognThermalOverlay?.cleanup()
        mapState.ognThermalOverlay = ognThermalOverlayFactory(map)
        mapState.ognThermalOverlay?.initialize()
        mapState.ognThermalOverlay?.render(latestOgnThermalHotspots)

        mapState.ognGliderTrailOverlay?.cleanup()
        mapState.ognGliderTrailOverlay = ognGliderTrailOverlayFactory(map)
        mapState.ognGliderTrailOverlay?.initialize()
        mapState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)

        bringTrafficOverlaysToFront()
        val nowMonoMs = nowMonoMs()
        ognTrafficRenderState.lastRenderMonoMs = nowMonoMs
        ognTargetVisualsRenderState.lastRenderMonoMs = nowMonoMs
        ognThermalRenderState.lastRenderMonoMs = nowMonoMs
        ognTrailRenderState.lastRenderMonoMs = nowMonoMs
    }

    fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        if (ognDisplayUpdateMode == mode) return
        ognDisplayUpdateMode = mode
        cancelPendingRenders()
        renderTargetsNow()
        renderTargetVisualsNow()
        renderThermalsNow()
        renderTrailsNow()
        val nowMonoMs = nowMonoMs()
        ognTrafficRenderState.lastRenderMonoMs = nowMonoMs
        ognTargetVisualsRenderState.lastRenderMonoMs = nowMonoMs
        ognThermalRenderState.lastRenderMonoMs = nowMonoMs
        ognTrailRenderState.lastRenderMonoMs = nowMonoMs
    }

    fun setMapInteractionActive(active: Boolean) {
        if (mapInteractionActive == active) return
        mapInteractionActive = active
        if (!active) {
            flushDeferredRenders()
        }
    }

    fun updateTrafficTargets(
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
        ownshipLocation: MapLocationUiModel?,
        forceImmediate: Boolean = false
    ) {
        val sameEnabled = latestTargetEnabled == enabled
        val sameResolvedTarget = latestResolvedTarget == resolvedTarget
        val sameOwnshipLocation = latestOwnshipLocation == ownshipLocation
        if (sameEnabled &&
            sameResolvedTarget &&
            sameOwnshipLocation &&
            !forceImmediate &&
            mapState.ognTargetRingOverlay != null &&
            mapState.ognTargetLineOverlay != null
        ) {
            return
        }
        latestTargetEnabled = enabled
        latestResolvedTarget = resolvedTarget
        latestOwnshipLocation = ownshipLocation
        scheduleRender(
            state = ognTargetVisualsRenderState,
            forceImmediate = forceImmediate || !enabled || resolvedTarget == null || ownshipLocation == null
        ) {
            renderTargetVisualsNow()
        }
    }

    fun updateThermalHotspots(hotspots: List<OgnThermalHotspot>, forceImmediate: Boolean = false) {
        val sameHotspots = latestOgnThermalHotspots == hotspots
        if (sameHotspots && !forceImmediate && mapState.ognThermalOverlay != null) return
        latestOgnThermalHotspots = hotspots
        scheduleRender(
            state = ognThermalRenderState,
            forceImmediate = forceImmediate || hotspots.isEmpty()
        ) {
            renderThermalsNow()
        }
    }

    fun updateGliderTrailSegments(segments: List<OgnGliderTrailSegment>, forceImmediate: Boolean = false) {
        val incomingSignature = gliderTrailSegmentIdentitySignature(segments)
        val sameSegments = latestOgnGliderTrailSegments.size == segments.size &&
            latestOgnGliderTrailSignature == incomingSignature &&
            sameGliderTrailSegmentsByIdentity(latestOgnGliderTrailSegments, segments)
        if (sameSegments && !forceImmediate && mapState.ognGliderTrailOverlay != null) return
        latestOgnGliderTrailSegments = segments
        latestOgnGliderTrailSignature = incomingSignature
        scheduleRender(
            state = ognTrailRenderState,
            forceImmediate = forceImmediate || segments.isEmpty()
        ) {
            renderTrailsNow()
        }
    }

    fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        ognIconSizePx = clamped
        mapState.ognTrafficOverlay?.setIconSizePx(clamped)
        mapState.ognTargetRingOverlay?.setIconSizePx(clamped)
    }

    fun findTargetAt(tap: LatLng): String? {
        val ringTarget = mapState.ognTargetRingOverlay?.findTargetAt(tap)
        if (!ringTarget.isNullOrBlank()) return ringTarget
        return mapState.ognTrafficOverlay?.findTargetAt(tap)
    }

    fun findThermalHotspotAt(tap: LatLng): String? = mapState.ognThermalOverlay?.findTargetAt(tap)

    fun bringOverlaysToFront() {
        mapState.ognTargetLineOverlay?.bringToFront()
        mapState.ognTrafficOverlay?.bringToFront()
        mapState.ognTargetRingOverlay?.bringToFront()
    }

    fun applySatelliteContrastIcons(enabled: Boolean) {
        mapState.ognTrafficOverlay?.setUseSatelliteContrastIcons(enabled)
        if (mapState.ognTrafficOverlay != null || latestOgnTargets.isNotEmpty()) {
            updateTrafficTargets(
                targets = latestOgnTargets,
                ownshipAltitudeMeters = latestOgnOwnshipAltitudeMeters,
                altitudeUnit = latestOgnAltitudeUnit,
                unitsPreferences = latestOgnUnitsPreferences,
                forceImmediate = true
            )
        }
    }

    fun onMapDetached() {
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

    private fun createOgnTrafficOverlay(map: MapLibreMap): OgnTrafficOverlay =
        ognTrafficOverlayFactory(
            map,
            ognIconSizePx,
            satelliteContrastIconsEnabled()
        )

    private fun renderTargetsNow() {
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

    private fun renderThermalsNow() {
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognThermalOverlay == null) {
            mapState.ognThermalOverlay = ognThermalOverlayFactory(map)
            mapState.ognThermalOverlay?.initialize()
        }
        runCatching {
            mapState.ognThermalOverlay?.render(latestOgnThermalHotspots)
        }.onFailure { throwable ->
            Log.e(TAG, "OGN thermal overlay render failed: ${throwable.message}", throwable)
        }
    }

    private fun renderTrailsNow() {
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognGliderTrailOverlay == null) {
            mapState.ognGliderTrailOverlay = ognGliderTrailOverlayFactory(map)
            mapState.ognGliderTrailOverlay?.initialize()
        }
        runCatching {
            mapState.ognGliderTrailOverlay?.render(latestOgnGliderTrailSegments)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to render OGN glider trails: ${throwable.message}", throwable)
        }
    }

    private fun renderTargetVisualsNow() {
        val map = mapState.mapLibreMap ?: return
        if (mapState.ognTargetRingOverlay == null) {
            mapState.ognTargetRingOverlay = ognTargetRingOverlayFactory(map, ognIconSizePx)
            mapState.ognTargetRingOverlay?.initialize()
        }
        if (mapState.ognTargetLineOverlay == null) {
            mapState.ognTargetLineOverlay = ognTargetLineOverlayFactory(map)
            mapState.ognTargetLineOverlay?.initialize()
        }
        runCatching {
            mapState.ognTargetRingOverlay?.render(
                enabled = latestTargetEnabled,
                target = latestResolvedTarget
            )
            mapState.ognTargetLineOverlay?.render(
                enabled = latestTargetEnabled,
                ownshipLocation = latestOwnshipLocation,
                target = latestResolvedTarget
            )
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to render OGN target visuals: ${throwable.message}", throwable)
        }
    }

    private fun scheduleRender(
        state: OgnRenderThrottleState,
        forceImmediate: Boolean,
        renderNow: () -> Unit
    ) {
        val map = mapState.mapLibreMap ?: return
        val intervalMs = resolveInteractionAwareIntervalMs(
            baseIntervalMs = ognDisplayUpdateMode.renderIntervalMs,
            interactionActive = mapInteractionActive,
            interactionFloorMs = OGN_INTERACTION_MIN_RENDER_INTERVAL_MS
        )

        if (forceImmediate || intervalMs <= 0L) {
            state.pendingJob?.cancel()
            state.pendingJob = null
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

        if (state.pendingJob != null) return

        val remainingMs = (intervalMs - elapsedMs).coerceAtLeast(0L)
        state.pendingJob = coroutineScope.launch {
            delay(remainingMs)
            state.pendingJob = null
            if (mapState.mapLibreMap != map || mapState.mapLibreMap == null) return@launch
            renderNow()
            state.lastRenderMonoMs = nowMonoMs()
        }
    }

    private fun flushDeferredRenders() {
        val nowMonoMs = nowMonoMs()
        val states = listOf(
            ognTrafficRenderState to ::renderTargetsNow,
            ognTargetVisualsRenderState to ::renderTargetVisualsNow,
            ognThermalRenderState to ::renderThermalsNow,
            ognTrailRenderState to ::renderTrailsNow
        )
        states.forEach { (state, renderNow) ->
            val pending = state.pendingJob ?: return@forEach
            pending.cancel()
            state.pendingJob = null
            renderNow()
            state.lastRenderMonoMs = nowMonoMs
        }
    }

    private fun cancelPendingRenders() {
        ognTrafficRenderState.pendingJob?.cancel()
        ognTrafficRenderState.pendingJob = null
        ognTargetVisualsRenderState.pendingJob?.cancel()
        ognTargetVisualsRenderState.pendingJob = null
        ognThermalRenderState.pendingJob?.cancel()
        ognThermalRenderState.pendingJob = null
        ognTrailRenderState.pendingJob?.cancel()
        ognTrailRenderState.pendingJob = null
    }

    private companion object {
        private const val TAG = "MapOverlayManager"
    }
}
