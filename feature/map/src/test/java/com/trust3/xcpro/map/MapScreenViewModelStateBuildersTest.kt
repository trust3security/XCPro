package com.trust3.xcpro.map

import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.replay.Selection
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelStateBuildersTest {

    @Test
    fun createMergedAdsbTargetsState_appliesMetadataForMatchingIds() = runTest {
        val id = Icao24.from("abc123") ?: error("invalid test id")
        val rawTargets = MutableStateFlow(
            listOf(
                sampleTarget(
                    id = id,
                    metadataTypecode = null,
                    metadataIcaoAircraftType = null
                )
            )
        )
        val enrichedTargets = MutableStateFlow(
            listOf(
                sampleTarget(
                    id = id,
                    metadataTypecode = "A3",
                    metadataIcaoAircraftType = "B738"
                )
            )
        )

        val mergedState = createMergedAdsbTargetsState(
            scope = backgroundScope,
            rawAdsbTargets = rawTargets,
            enrichedAdsbTargets = enrichedTargets
        )
        runCurrent()

        val merged = mergedState.value.single()
        assertEquals("A3", merged.metadataTypecode)
        assertEquals("B738", merged.metadataIcaoAircraftType)
    }

    @Test
    fun createMergedAdsbTargetsState_keepsRawWhenNoMetadataMatch() = runTest {
        val rawId = Icao24.from("abc123") ?: error("invalid raw id")
        val metadataId = Icao24.from("def456") ?: error("invalid metadata id")
        val rawTarget = sampleTarget(
            id = rawId,
            metadataTypecode = null,
            metadataIcaoAircraftType = null
        )
        val rawTargets = MutableStateFlow(listOf(rawTarget))
        val enrichedTargets = MutableStateFlow(
            listOf(
                sampleTarget(
                    id = metadataId,
                    metadataTypecode = "A4",
                    metadataIcaoAircraftType = "A359"
                )
            )
        )

        val mergedState = createMergedAdsbTargetsState(
            scope = backgroundScope,
            rawAdsbTargets = rawTargets,
            enrichedAdsbTargets = enrichedTargets
        )
        runCurrent()

        assertEquals(listOf(rawTarget), mergedState.value)
    }

    @Test
    fun createReplaySensorGateStates_tracksSelectionAndStatus() = runTest {
        val replaySession = MutableStateFlow(SessionState())
        val gates = createReplaySensorGateStates(
            scope = backgroundScope,
            replaySessionState = replaySession
        )
        runCurrent()

        assertFalse(gates.suppressLiveGps.value)
        assertTrue(gates.allowSensorStart.value)

        replaySession.value = SessionState(
            selection = Selection(DocumentRef(uri = "content://test/replay.igc")),
            status = SessionStatus.PLAYING
        )
        runCurrent()

        assertTrue(gates.suppressLiveGps.value)
        assertFalse(gates.allowSensorStart.value)
    }

    @Test
    fun createCardHydrationReadyState_requiresContainerAndLiveData() = runTest {
        val containerReady = MutableStateFlow(false)
        val liveDataReady = MutableStateFlow(false)
        val hydrationReady = createCardHydrationReadyState(
            scope = backgroundScope,
            containerReady = containerReady,
            liveDataReady = liveDataReady
        )
        runCurrent()

        assertFalse(hydrationReady.value)

        containerReady.value = true
        runCurrent()
        assertFalse(hydrationReady.value)

        liveDataReady.value = true
        runCurrent()
        assertTrue(hydrationReady.value)
    }

    @Test
    fun createOverlayOwnshipAltitudeState_quantizesAndDedupesWithinBucket() = runTest {
        val repository = FlightDataRepository()
        val overlayAltitudeState = createOverlayOwnshipAltitudeState(
            scope = backgroundScope,
            flightData = repository.flightData
        )
        runCurrent()

        repository.update(buildCompleteFlightData(gps = defaultGps(altitudeMeters = 1_001.1)))
        runCurrent()
        assertEquals(1_002.0, overlayAltitudeState.value ?: Double.NaN, 0.0)

        repository.update(buildCompleteFlightData(gps = defaultGps(altitudeMeters = 1_001.8)))
        runCurrent()
        assertEquals(1_002.0, overlayAltitudeState.value ?: Double.NaN, 0.0)

        repository.update(buildCompleteFlightData(gps = defaultGps(altitudeMeters = 1_003.2)))
        runCurrent()
        assertEquals(1_004.0, overlayAltitudeState.value ?: Double.NaN, 0.0)
    }

    @Test
    fun createOverlayOwnshipAltitudeState_fallsBackToBaroAndHandlesNull() = runTest {
        val repository = FlightDataRepository()
        val overlayAltitudeState = createOverlayOwnshipAltitudeState(
            scope = backgroundScope,
            flightData = repository.flightData
        )
        runCurrent()

        repository.update(buildCompleteFlightData(gps = null, baroAltitudeMeters = 1_113.1))
        runCurrent()
        assertEquals(1_114.0, overlayAltitudeState.value ?: Double.NaN, 0.0)

        repository.update(null)
        runCurrent()
        assertNull(overlayAltitudeState.value)
    }

    @Test
    fun toCardFlightModeSelection_mapsAllModes() {
        assertEquals(
            com.example.dfcards.FlightModeSelection.CRUISE,
            FlightMode.CRUISE.toCardFlightModeSelection()
        )
        assertEquals(
            com.example.dfcards.FlightModeSelection.THERMAL,
            FlightMode.THERMAL.toCardFlightModeSelection()
        )
        assertEquals(
            com.example.dfcards.FlightModeSelection.FINAL_GLIDE,
            FlightMode.FINAL_GLIDE.toCardFlightModeSelection()
        )
    }

    private fun sampleTarget(
        id: Icao24,
        metadataTypecode: String?,
        metadataIcaoAircraftType: String?
    ): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = id,
        callsign = "TEST01",
        lat = -35.0,
        lon = 149.0,
        altitudeM = 1500.0,
        speedMps = 70.0,
        trackDeg = 180.0,
        climbMps = 0.8,
        ageSec = 1,
        isStale = false,
        distanceMeters = 1200.0,
        bearingDegFromUser = 220.0,
        positionSource = 0,
        category = 3,
        lastContactEpochSec = null,
        metadataTypecode = metadataTypecode,
        metadataIcaoAircraftType = metadataIcaoAircraftType
    )
}
