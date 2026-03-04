package com.example.xcpro.adsb.metadata.domain

import com.example.xcpro.adsb.AdsbSelectedTargetDetails
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.common.di.IoDispatcher
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbMetadataEnrichmentUseCase @Inject constructor(
    private val aircraftMetadataRepository: AircraftMetadataRepository,
    private val metadataSyncRepository: AircraftMetadataSyncRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    fun targetsWithMetadata(
        adsbTargets: Flow<List<AdsbTrafficUiModel>>
    ): Flow<List<AdsbTrafficUiModel>> {
        val lookupOrder = adsbTargets
            .map(::prioritizedLookupOrder)
            .distinctUntilChanged()

        val metadataByIcao24 = combine(
            lookupOrder,
            aircraftMetadataRepository.metadataRevision
        ) { orderedIcao24s, _ ->
            orderedIcao24s
        }.mapLatest { orderedIcao24s ->
            if (orderedIcao24s.isEmpty()) return@mapLatest emptyMap()
            withContext(ioDispatcher) {
                aircraftMetadataRepository.getMetadataFor(orderedIcao24s)
            }
        }

        return combine(adsbTargets, metadataByIcao24) { targets, metadata ->
            applyMetadataToTargets(targets = targets, metadataByIcao24 = metadata)
        }
    }

    fun selectedTargetDetails(
        selectedIcao24: Flow<Icao24?>,
        adsbTargets: Flow<List<AdsbTrafficUiModel>>
    ): Flow<AdsbSelectedTargetDetails?> {
        val selectedTarget = combine(selectedIcao24, adsbTargets) { selected, targets ->
            selected?.let { id -> targets.firstOrNull { it.id == id } }
        }
        val selectedIcao24Raw = selectedTarget
            .map { target -> target?.id?.raw }
            .distinctUntilChanged()

        val selectedMetadata = combine(
            selectedIcao24Raw,
            aircraftMetadataRepository.metadataRevision
        ) { selectedRawIcao24, _ ->
            selectedRawIcao24
        }.mapLatest { selectedRawIcao24 ->
            if (selectedRawIcao24 == null) {
                return@mapLatest null
            }
            val metadata = withContext(ioDispatcher) {
                aircraftMetadataRepository.getMetadataFor(listOf(selectedRawIcao24))[selectedRawIcao24]
            }
            SelectedMetadataLookup(
                selectedRawIcao24 = selectedRawIcao24,
                metadata = metadata
            )
        }

        return combine(
            selectedTarget,
            selectedMetadata,
            metadataSyncRepository.syncState
        ) { target, metadataLookup, syncState ->
            if (target == null) {
                return@combine null
            }
            val metadata = metadataLookup
                ?.takeIf { it.selectedRawIcao24 == target.id.raw }
                ?.metadata

            val availability = when {
                metadata != null -> MetadataAvailability.Ready
                syncState is MetadataSyncState.Running || syncState is MetadataSyncState.Scheduled ->
                    MetadataAvailability.SyncInProgress

                syncState is MetadataSyncState.Failed ->
                    MetadataAvailability.Unavailable(syncState.reason)

                else -> MetadataAvailability.Missing
            }

            AdsbSelectedTargetDetails(
                id = target.id,
                callsign = target.callsign,
                lat = target.lat,
                lon = target.lon,
                altitudeM = target.altitudeM,
                speedMps = target.speedMps,
                trackDeg = target.trackDeg,
                climbMps = target.climbMps,
                ageSec = target.ageSec,
                isStale = target.isStale,
                distanceMeters = target.distanceMeters,
                bearingDegFromUser = target.bearingDegFromUser,
                usesOwnshipReference = target.usesOwnshipReference,
                proximityTier = target.proximityTier,
                proximityReason = target.proximityReason,
                isClosing = target.isClosing,
                closingRateMps = target.closingRateMps,
                isEmergencyCollisionRisk = target.isEmergencyCollisionRisk,
                isEmergencyAudioEligible = target.isEmergencyAudioEligible,
                isCirclingEmergencyRedRule = target.isCirclingEmergencyRedRule,
                positionSource = target.positionSource,
                category = target.category,
                lastContactEpochSec = target.lastContactEpochSec,
                registration = metadata?.registration,
                typecode = metadata?.typecode,
                model = metadata?.model,
                manufacturerName = metadata?.manufacturerName,
                owner = metadata?.owner,
                operator = metadata?.operator,
                operatorCallsign = metadata?.operatorCallsign,
                icaoAircraftType = metadata?.icaoAircraftType,
                metadataAvailability = availability,
                metadataSyncState = syncState
            )
        }.distinctUntilChanged()
    }

    private fun prioritizedLookupOrder(targets: List<AdsbTrafficUiModel>): List<String> {
        if (targets.isEmpty()) return emptyList()

        val highPriority = LinkedHashSet<String>(targets.size)
        val regular = LinkedHashSet<String>(targets.size)
        val metadataHinted = LinkedHashSet<String>(targets.size)
        for (target in targets) {
            val hasMetadataHint =
                !target.metadataTypecode.isNullOrBlank() ||
                    !target.metadataIcaoAircraftType.isNullOrBlank()
            val category = target.category
            val categoryIsAmbiguous = category == null || category in AMBIGUOUS_CATEGORY_VALUES
            val categoryIsNonFixedWing = category in NON_FIXED_WING_CATEGORY_VALUES
            when {
                hasMetadataHint -> metadataHinted += target.id.raw
                categoryIsAmbiguous || categoryIsNonFixedWing -> highPriority += target.id.raw
                else -> {
                    regular += target.id.raw
                }
            }
        }

        return buildList(highPriority.size + regular.size + metadataHinted.size) {
            addAll(highPriority.sorted())
            addAll(regular.sorted())
            addAll(metadataHinted.sorted())
        }
    }

    private fun applyMetadataToTargets(
        targets: List<AdsbTrafficUiModel>,
        metadataByIcao24: Map<String, AircraftMetadata>
    ): List<AdsbTrafficUiModel> {
        if (targets.isEmpty()) return emptyList()
        return targets.map { target ->
            val metadata = metadataByIcao24[target.id.raw]
            val resolvedTypecode = metadata?.typecode
            val resolvedIcaoAircraftType = metadata?.icaoAircraftType
            if (
                target.metadataTypecode == resolvedTypecode &&
                target.metadataIcaoAircraftType == resolvedIcaoAircraftType
            ) {
                target
            } else {
                target.copy(
                    metadataTypecode = resolvedTypecode,
                    metadataIcaoAircraftType = resolvedIcaoAircraftType
                )
            }
        }
    }

    private data class SelectedMetadataLookup(
        val selectedRawIcao24: String,
        val metadata: AircraftMetadata?
    )

    private companion object {
        val AMBIGUOUS_CATEGORY_VALUES = setOf(0, 1, 13)
        val NON_FIXED_WING_CATEGORY_VALUES = setOf(8, 9, 10, 11, 12, 14)
    }
}
