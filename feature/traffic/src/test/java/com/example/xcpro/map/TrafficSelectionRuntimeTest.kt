package com.example.xcpro.map

import com.example.xcpro.adsb.metadata.FakeMetadataRepository
import com.example.xcpro.adsb.metadata.FakeSyncRepository
import com.example.xcpro.adsb.metadata.target
import com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficSelectionRuntimeTest {

    @Test
    fun trafficSelectionState_implementsSelectionPort_andUpdatesAdsbSelectionThroughNamedMethod() = runTest {
        val selectionPort: TrafficSelectionPort = createSelectionState(
            scope = backgroundScope,
            rawAdsbTargets = MutableStateFlow(listOf(target("abc123"))),
            ognTargets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList()),
            thermalHotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList()),
            scheduler = testScheduler
        )
        val selectedId = Icao24.from("abc123") ?: error("invalid test ICAO24")

        selectionPort.setSelectedAdsbId(selectedId)
        advanceUntilIdle()

        assertEquals(selectedId, selectionPort.selectedAdsbId.value)
        val selectionState = selectionPort as MapTrafficSelectionState
        assertEquals(selectedId, selectionState.selectedAdsbId.value)
    }

    @Test
    fun trafficSelectionState_updatesOgnSelectionThroughNamedMethod() = runTest {
        val selectionState = createSelectionState(
            scope = backgroundScope,
            rawAdsbTargets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList()),
            ognTargets = MutableStateFlow(listOf(sampleOgnTarget("FLARM:AB12CD"))),
            thermalHotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList()),
            scheduler = testScheduler
        )

        selectionState.setSelectedOgnId("flarm:ab12cd")
        advanceUntilIdle()

        assertEquals("flarm:ab12cd", selectionState.selectedOgnId.value)
    }

    @Test
    fun trafficSelectionState_updatesThermalSelectionThroughNamedMethod() = runTest {
        val selectionState = createSelectionState(
            scope = backgroundScope,
            rawAdsbTargets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList()),
            ognTargets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList()),
            thermalHotspots = MutableStateFlow(listOf(sampleThermalHotspot("thermal-1"))),
            scheduler = testScheduler
        )

        selectionState.setSelectedThermalId("thermal-1")
        advanceUntilIdle()

        assertEquals("thermal-1", selectionState.selectedThermalId.value)
        assertEquals("thermal-1", selectionState.selectedOgnThermalId.value)
    }

    private fun createSelectionState(
        scope: CoroutineScope,
        rawAdsbTargets: MutableStateFlow<List<AdsbTrafficUiModel>>,
        ognTargets: MutableStateFlow<List<OgnTrafficTarget>>,
        thermalHotspots: MutableStateFlow<List<OgnThermalHotspot>>,
        scheduler: TestCoroutineScheduler
    ): MapTrafficSelectionState {
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = FakeMetadataRepository(emptyMap()),
            metadataSyncRepository = FakeSyncRepository(metadataSyncStateIdle()),
            ioDispatcher = StandardTestDispatcher(scheduler)
        )

        return createTrafficSelectionState(
            scope = scope,
            adsbMetadataEnrichmentUseCase = useCase,
            rawAdsbTargets = rawAdsbTargets,
            ognTargets = ognTargets,
            thermalHotspots = thermalHotspots
        )
    }

    private fun sampleOgnTarget(id: String): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "OGNTEST",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 1200.0,
        trackDegrees = 180.0,
        groundSpeedMps = 40.0,
        verticalSpeedMps = 1.1,
        deviceIdHex = "ABC123",
        signalDb = 12.0,
        displayLabel = id,
        identity = null,
        rawComment = "sample",
        rawLine = "sample line",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L
    )

    private fun sampleThermalHotspot(id: String): OgnThermalHotspot = OgnThermalHotspot(
        id = id,
        sourceTargetId = "source",
        sourceLabel = "source",
        latitude = -35.0,
        longitude = 149.0,
        startedAtMonoMs = 1_000L,
        startedAtWallMs = 1_000L,
        updatedAtMonoMs = 2_000L,
        updatedAtWallMs = 2_000L,
        startAltitudeMeters = 900.0,
        maxAltitudeMeters = 1200.0,
        maxAltitudeAtMonoMs = 2_000L,
        maxClimbRateMps = 2.3,
        averageClimbRateMps = 1.6,
        averageBottomToTopClimbRateMps = 1.2,
        snailColorIndex = 15,
        state = OgnThermalHotspotState.ACTIVE
    )
}
