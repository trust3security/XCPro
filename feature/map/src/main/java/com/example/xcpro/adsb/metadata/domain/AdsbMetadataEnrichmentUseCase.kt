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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbMetadataEnrichmentUseCase @Inject constructor(
    private val aircraftMetadataRepository: AircraftMetadataRepository,
    private val metadataSyncRepository: AircraftMetadataSyncRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    fun selectedTargetDetails(
        selectedIcao24: Flow<Icao24?>,
        adsbTargets: Flow<List<AdsbTrafficUiModel>>
    ): Flow<AdsbSelectedTargetDetails?> {
        val selectedTarget = combine(selectedIcao24, adsbTargets) { selected, targets ->
            selected?.let { id -> targets.firstOrNull { it.id == id } }
        }

        return combine(selectedTarget, metadataSyncRepository.syncState) { target, syncState ->
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
}
