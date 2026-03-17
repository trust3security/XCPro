package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun target(rawIcao24: String, category: Int = 2): AdsbTrafficUiModel {
    val id = Icao24.from(rawIcao24) ?: error("invalid ICAO24")
    return AdsbTrafficUiModel(
        id = id,
        callsign = rawIcao24.uppercase(),
        lat = -33.86,
        lon = 151.20,
        altitudeM = 1000.0,
        speedMps = 70.0,
        trackDeg = 180.0,
        climbMps = 0.5,
        ageSec = 2,
        isStale = false,
        distanceMeters = 1200.0,
        bearingDegFromUser = 90.0,
        positionSource = 0,
        category = category,
        lastContactEpochSec = 1_710_000_000L
    )
}

internal class FakeMetadataRepository(
    values: Map<String, AircraftMetadata>
) : AircraftMetadataRepository {
    override val metadataRevision = MutableStateFlow(0L)
    override val lookupProgressRevision = MutableStateFlow(0L)
    private val metadataByIcao24 = values.toMutableMap()
    var lastLookupOrder: List<String> = emptyList()
        private set
    var lookupCallCount: Int = 0
        private set

    override suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata> {
        lookupCallCount += 1
        lastLookupOrder = icao24s
        return metadataByIcao24.filterKeys { it in icao24s }
    }

    fun upsertMetadata(metadata: AircraftMetadata) {
        metadataByIcao24[metadata.icao24] = metadata
        metadataRevision.value = metadataRevision.value + 1L
    }

    fun advanceLookupProgress() {
        lookupProgressRevision.value = lookupProgressRevision.value + 1L
    }
}

internal class FakeSyncRepository(initialState: MetadataSyncState) : AircraftMetadataSyncRepository {
    private val mutableState = MutableStateFlow(initialState)
    override val syncState: StateFlow<MetadataSyncState> = mutableState

    override suspend fun onScheduled() {
        mutableState.value = MetadataSyncState.Scheduled
    }

    override suspend fun onPausedByUser() {
        mutableState.value = MetadataSyncState.PausedByUser(lastSuccessWallMs = null)
    }

    override suspend fun runSyncNow(): MetadataSyncRunResult = MetadataSyncRunResult.Skipped
}
