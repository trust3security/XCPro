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
        return combine(adsbTargets, aircraftMetadataRepository.metadataRevision) { targets, _ ->
            targets
        }.mapLatest { targets ->
            if (targets.isEmpty()) {
                return@mapLatest emptyList()
            }
            val lookupOrder = prioritizedLookupOrder(targets)
            val metadataByIcao24 = withContext(ioDispatcher) {
                aircraftMetadataRepository.getMetadataFor(lookupOrder)
            }
            targets.map { target ->
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
    }

    fun selectedTargetDetails(
        selectedIcao24: Flow<Icao24?>,
        adsbTargets: Flow<List<AdsbTrafficUiModel>>
    ): Flow<AdsbSelectedTargetDetails?> {
        val selectedTarget = combine(selectedIcao24, adsbTargets) { selected, targets ->
            selected?.let { id -> targets.firstOrNull { it.id == id } }
        }

        return combine(
            selectedTarget,
            metadataSyncRepository.syncState,
            aircraftMetadataRepository.metadataRevision
        ) { target, syncState, _ ->
            target to syncState
        }.mapLatest { (target, syncState) ->
            if (target == null) {
                return@mapLatest null
            }

            val metadata = withContext(ioDispatcher) {
                aircraftMetadataRepository.getMetadataFor(listOf(target.id.raw))[target.id.raw]
            }

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
        }
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
            addAll(highPriority)
            addAll(regular)
            addAll(metadataHinted)
        }
    }

    private companion object {
        val AMBIGUOUS_CATEGORY_VALUES = setOf(0, 1, 13)
        val NON_FIXED_WING_CATEGORY_VALUES = setOf(8, 9, 10, 11, 12, 14)
    }
}
