package com.trust3.xcpro.map

import com.trust3.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.trust3.xcpro.adsb.AdsbTrafficRepository
import com.trust3.xcpro.adsb.AdsbTrafficSnapshot
import com.trust3.xcpro.adsb.AdsbTrafficUiModel
import com.trust3.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.trust3.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
import com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState
import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.ogn.OgnDisplayUpdateMode
import com.trust3.xcpro.ogn.OgnGliderTrailRepository
import com.trust3.xcpro.ogn.OgnGliderTrailSegment
import com.trust3.xcpro.ogn.SelectedOgnThermalContext
import com.trust3.xcpro.ogn.OgnThermalHotspot
import com.trust3.xcpro.ogn.OgnThermalRepository
import com.trust3.xcpro.ogn.OgnTrafficPreferencesRepository
import com.trust3.xcpro.ogn.OgnTrafficRepository
import com.trust3.xcpro.ogn.OgnTrafficSnapshot
import com.trust3.xcpro.ogn.OgnTrafficTarget
import com.trust3.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.trust3.xcpro.ogn.buildOgnSelectionLookup
import com.trust3.xcpro.ogn.observeSelectedOgnThermalContext
import com.trust3.xcpro.ogn.selectionLookupContainsOgnKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class OgnTrafficUseCase @Inject constructor(
    private val repository: OgnTrafficRepository,
    private val preferencesRepository: OgnTrafficPreferencesRepository,
    private val thermalRepository: OgnThermalRepository,
    private val gliderTrailRepository: OgnGliderTrailRepository,
    private val trailSelectionRepository: OgnTrailSelectionPreferencesRepository,
    private val clock: Clock
) : OgnTrafficFacade {
    override val targets: StateFlow<List<OgnTrafficTarget>> = repository.targets
    override val suppressedTargetIds: StateFlow<Set<String>> = repository.suppressedTargetIds
    override val snapshot: StateFlow<OgnTrafficSnapshot> = repository.snapshot
    override val isStreamingEnabled: StateFlow<Boolean> = repository.isEnabled
    override val overlayEnabled: Flow<Boolean> = preferencesRepository.enabledFlow
    override val iconSizePx: Flow<Int> = preferencesRepository.iconSizePxFlow
    override val displayUpdateMode: Flow<OgnDisplayUpdateMode> = preferencesRepository.displayUpdateModeFlow
    override val showSciaEnabled: Flow<Boolean> = preferencesRepository.showSciaEnabledFlow
    override val targetEnabled: Flow<Boolean> = preferencesRepository.targetEnabledFlow
    override val targetAircraftKey: Flow<String?> = preferencesRepository.targetAircraftKeyFlow
    override val thermalHotspots: StateFlow<List<OgnThermalHotspot>> = thermalRepository.hotspots
    override val showThermalsEnabled: Flow<Boolean> = preferencesRepository.showThermalsEnabledFlow
    override val gliderTrailSegments: Flow<List<OgnGliderTrailSegment>> = combine(
        gliderTrailRepository.segments,
        trailSelectionRepository.selectedAircraftKeysFlow
    ) { segments, selectedKeys ->
        val lookup = buildOgnSelectionLookup(selectedKeys)
        if (lookup.normalizedSelectedKeys.isEmpty()) {
            emptyList()
        } else {
            val filtered = ArrayList<OgnGliderTrailSegment>(segments.size)
            for (segment in segments) {
                if (!selectionLookupContainsOgnKey(lookup = lookup, candidateKey = segment.sourceTargetId)) {
                    continue
                }
                filtered += segment
            }
            filtered
        }
    }.distinctUntilChanged(::sameOgnGliderTrailSegmentsByIdentity)

    override fun selectedThermalContext(
        selectedThermalId: Flow<String?>
    ): Flow<SelectedOgnThermalContext?> = observeSelectedOgnThermalContext(
        selectedThermalId = selectedThermalId,
        hotspots = thermalRepository.hotspots,
        rawSegments = gliderTrailRepository.segments,
        clock = clock
    )

    override fun setStreamingEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        repository.updateCenter(latitude, longitude)
    }

    override fun updateAutoReceiveRadiusContext(
        zoomLevel: Float,
        groundSpeedMs: Double,
        isFlying: Boolean
    ) {
        repository.updateAutoReceiveRadiusContext(
            zoomLevel = zoomLevel,
            groundSpeedMs = groundSpeedMs,
            isFlying = isFlying
        )
    }

    override suspend fun setOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setEnabled(enabled)
    }

    override suspend fun setIconSizePx(iconSizePx: Int) {
        preferencesRepository.setIconSizePx(iconSizePx)
    }

    override suspend fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        preferencesRepository.setDisplayUpdateMode(mode)
    }

    override suspend fun setShowSciaEnabled(enabled: Boolean) {
        preferencesRepository.setShowSciaEnabled(enabled)
    }

    override suspend fun setOverlayAndShowSciaEnabled(
        overlayEnabled: Boolean,
        showSciaEnabled: Boolean
    ) {
        preferencesRepository.setOverlayAndSciaEnabled(
            overlayEnabled = overlayEnabled,
            showSciaEnabled = showSciaEnabled
        )
    }

    override suspend fun setShowThermalsEnabled(enabled: Boolean) {
        preferencesRepository.setShowThermalsEnabled(enabled)
    }

    override suspend fun setTargetSelection(enabled: Boolean, aircraftKey: String?) {
        preferencesRepository.setTargetSelection(enabled = enabled, aircraftKey = aircraftKey)
    }

    override suspend fun clearTargetSelection() {
        preferencesRepository.clearTargetSelection()
    }

    override fun stop() {
        repository.stop()
    }
}

private fun sameOgnGliderTrailSegmentsByIdentity(
    previous: List<OgnGliderTrailSegment>,
    current: List<OgnGliderTrailSegment>
): Boolean {
    if (previous === current) return true
    if (previous.size != current.size) return false
    for (index in previous.indices) {
        if (previous[index].id != current[index].id) {
            return false
        }
    }
    return true
}

class AdsbTrafficUseCase @Inject constructor(
    private val repository: AdsbTrafficRepository,
    private val preferencesRepository: AdsbTrafficPreferencesRepository,
    private val metadataSyncRepository: AircraftMetadataSyncRepository,
    private val metadataSyncScheduler: AircraftMetadataSyncScheduler
) : AdsbTrafficFacade {
    override val targets: StateFlow<List<AdsbTrafficUiModel>> = repository.targets
    override val snapshot: StateFlow<AdsbTrafficSnapshot> = repository.snapshot
    override val isStreamingEnabled: StateFlow<Boolean> = repository.isEnabled
    override val overlayEnabled: Flow<Boolean> = preferencesRepository.enabledFlow
    override val iconSizePx: Flow<Int> = preferencesRepository.iconSizePxFlow
    override val emergencyFlashEnabled: Flow<Boolean> = preferencesRepository.emergencyFlashEnabledFlow
    override val defaultMediumUnknownIconEnabled: Flow<Boolean> = combine(
        preferencesRepository.defaultMediumUnknownIconEnabledFlow,
        preferencesRepository.defaultMediumUnknownIconRollbackLatchedFlow
    ) { enabled, rollbackLatched ->
        enabled && !rollbackLatched
    }.distinctUntilChanged()
    override val defaultMediumUnknownIconRollbackReason: Flow<String?> =
        preferencesRepository.defaultMediumUnknownIconRollbackReasonFlow
    override val maxDistanceKm: Flow<Int> = preferencesRepository.maxDistanceKmFlow
    override val verticalAboveMeters: Flow<Double> = preferencesRepository.verticalAboveMetersFlow
    override val verticalBelowMeters: Flow<Double> = preferencesRepository.verticalBelowMetersFlow
    override val metadataSyncState: StateFlow<MetadataSyncState> = metadataSyncRepository.syncState

    override fun setStreamingEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    override fun clearTargets() {
        repository.clearTargets()
    }

    override fun updateCenter(latitude: Double, longitude: Double) {
        repository.updateCenter(latitude, longitude)
    }

    override fun updateOwnshipOrigin(latitude: Double, longitude: Double) =
        repository.updateOwnshipOrigin(latitude, longitude)

    override fun updateOwnshipMotion(trackDeg: Double?, speedMps: Double?) =
        repository.updateOwnshipMotion(trackDeg, speedMps)

    override fun clearOwnshipOrigin() = repository.clearOwnshipOrigin()

    override fun updateOwnshipAltitudeMeters(altitudeMeters: Double?) {
        repository.updateOwnshipAltitudeMeters(altitudeMeters)
    }

    override fun updateOwnshipCirclingContext(
        isCircling: Boolean,
        circlingFeatureEnabled: Boolean
    ) {
        repository.updateOwnshipCirclingContext(
            isCircling = isCircling,
            circlingFeatureEnabled = circlingFeatureEnabled
        )
    }

    override fun updateDisplayFilters(
        maxDistanceKm: Int,
        verticalAboveMeters: Double,
        verticalBelowMeters: Double
    ) {
        repository.updateDisplayFilters(
            maxDistanceKm = maxDistanceKm,
            verticalAboveMeters = verticalAboveMeters,
            verticalBelowMeters = verticalBelowMeters
        )
    }

    override suspend fun setOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setEnabled(enabled)
        metadataSyncScheduler.onOverlayPreferenceChanged(enabled)
    }

    override suspend fun setIconSizePx(iconSizePx: Int) {
        preferencesRepository.setIconSizePx(iconSizePx)
    }

    override suspend fun setDefaultMediumUnknownIconEnabled(enabled: Boolean) {
        preferencesRepository.setDefaultMediumUnknownIconEnabled(enabled)
    }

    override suspend fun latchDefaultMediumUnknownIconRollback(reason: String) {
        preferencesRepository.latchDefaultMediumUnknownIconRollback(reason)
    }

    override suspend fun clearDefaultMediumUnknownIconRollback() {
        preferencesRepository.clearDefaultMediumUnknownIconRollback()
    }

    override suspend fun bootstrapMetadataSync() {
        val enabled = overlayEnabled.first()
        metadataSyncScheduler.bootstrapForOverlayPreference(enabled)
    }

    override fun stop() {
        repository.stop()
    }
}
