package com.trust3.xcpro.livefollow.data.ownship

import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.livesource.LiveSourceKind
import com.trust3.xcpro.livesource.LiveSourceStatePort
import com.trust3.xcpro.livesource.ResolvedLiveSourceState
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowValueState
import com.trust3.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.GPSData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlightDataLiveOwnshipSnapshotSourceTest {

    @Test
    fun snapshot_mapsLiveOwnshipFieldsFromFlightDataRepository() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val liveSourceState = MutableStateFlow(ResolvedLiveSourceState(kind = LiveSourceKind.PHONE))
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            liveSourceStatePort = liveSourceStatePort(liveSourceState),
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.update(
            data = sampleFlightData(
                fixMonoMs = 12_345L,
                wallMs = 98_765L,
                gpsAccuracyMeters = 6f
            )
        )
        advanceUntilIdle()

        val snapshot = source.snapshot.value
        assertNotNull(snapshot)
        assertEquals(LiveSourceKind.PHONE, source.liveSourceKind.value)
        assertEquals(LiveFollowRuntimeMode.LIVE, source.runtimeMode.value)
        assertEquals(LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME, snapshot?.sourceLabel)
        assertEquals(12_345L, snapshot?.fixMonoMs)
        assertEquals(98_765L, snapshot?.fixWallMs)
        assertEquals(151.25, snapshot?.pressureAltitudeMslMeters)
        assertEquals(152.5, snapshot?.gpsAltitudeMslMeters)
        assertEquals(45.0, snapshot?.aglMeters)
        assertEquals(13.2, snapshot?.groundSpeedMs)
        assertEquals(182.0, snapshot?.trackDeg)
        assertEquals(1.8, snapshot?.verticalSpeedMs)
        assertEquals(LiveFollowValueState.VALID, snapshot?.positionQuality?.state)
        assertEquals(LiveFollowConfidence.HIGH, snapshot?.positionQuality?.confidence)
        assertEquals(LiveFollowAircraftIdentityType.FLARM, snapshot?.canonicalIdentity?.type)
        assertEquals("FLARM:AB12CD", snapshot?.canonicalIdentity?.canonicalKey)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snapshot_usesReplayLabelWhenReplaySourceIsActive() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val liveSourceState = MutableStateFlow(ResolvedLiveSourceState(kind = LiveSourceKind.PHONE))
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            liveSourceStatePort = liveSourceStatePort(liveSourceState),
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.setActiveSource(FlightDataRepository.Source.REPLAY)
        repository.update(
            data = sampleFlightData(fixMonoMs = 4_321L),
            source = FlightDataRepository.Source.REPLAY
        )
        advanceUntilIdle()

        assertEquals(LiveFollowRuntimeMode.REPLAY, source.runtimeMode.value)
        assertEquals(LiveSourceKind.PHONE, source.liveSourceKind.value)
        assertEquals(LiveOwnshipSourceLabel.REPLAY_RUNTIME, source.snapshot.value?.sourceLabel)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snapshot_dropsSamplesWithoutMonotonicFixTime() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val liveSourceState = MutableStateFlow(ResolvedLiveSourceState(kind = LiveSourceKind.PHONE))
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            liveSourceStatePort = liveSourceStatePort(liveSourceState),
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.update(
            data = sampleFlightData(fixMonoMs = 0L, wallMs = 22_000L)
        )
        advanceUntilIdle()

        assertNull(source.snapshot.value)
        assertEquals(LiveFollowRuntimeMode.LIVE, source.runtimeMode.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snapshot_omitsAglWhenAglTimestampIsUnknown() = runTest {
        val scope = repoScope()
        try {
        val repository = FlightDataRepository()
        val liveSourceState = MutableStateFlow(ResolvedLiveSourceState(kind = LiveSourceKind.PHONE))
        val source = FlightDataLiveOwnshipSnapshotSource(
            scope = scope,
            flightDataRepository = repository,
            liveSourceStatePort = liveSourceStatePort(liveSourceState),
            ownFlarmHexFlow = MutableStateFlow("AB12CD"),
            ownIcaoHexFlow = MutableStateFlow("EF34AB")
        )

        repository.update(
            data = sampleFlightData(
                fixMonoMs = 12_345L,
                aglTimestampMonoMs = 0L
            )
        )
        advanceUntilIdle()

        assertNull(source.snapshot.value?.aglMeters)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun liveSourceKind_tracksSimulatorWhileReplayLabelStaysReplayAuthoritative() = runTest {
        val scope = repoScope()
        try {
            val repository = FlightDataRepository()
            val liveSourceState = MutableStateFlow(
                ResolvedLiveSourceState(kind = LiveSourceKind.SIMULATOR_CONDOR2)
            )
            val source = FlightDataLiveOwnshipSnapshotSource(
                scope = scope,
                flightDataRepository = repository,
                liveSourceStatePort = liveSourceStatePort(liveSourceState),
                ownFlarmHexFlow = MutableStateFlow("AB12CD"),
                ownIcaoHexFlow = MutableStateFlow("EF34AB")
            )

            repository.setActiveSource(FlightDataRepository.Source.REPLAY)
            repository.update(
                data = sampleFlightData(fixMonoMs = 6_543L),
                source = FlightDataRepository.Source.REPLAY
            )
            advanceUntilIdle()

            assertEquals(LiveFollowRuntimeMode.REPLAY, source.runtimeMode.value)
            assertEquals(LiveSourceKind.SIMULATOR_CONDOR2, source.liveSourceKind.value)
            assertEquals(LiveOwnshipSourceLabel.REPLAY_RUNTIME, source.snapshot.value?.sourceLabel)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun liveSourceStatePort(
        stateFlow: MutableStateFlow<ResolvedLiveSourceState>
    ): LiveSourceStatePort = object : LiveSourceStatePort {
        override val state: StateFlow<ResolvedLiveSourceState> = stateFlow

        override fun refreshAndGetState(): ResolvedLiveSourceState = state.value
    }

    private fun sampleFlightData(
        fixMonoMs: Long,
        wallMs: Long = 12_000L,
        gpsAccuracyMeters: Float = 6f,
        aglTimestampMonoMs: Long = fixMonoMs
    ): CompleteFlightData {
        return CompleteFlightData(
            gps = GPSData(
                position = GeoPoint(latitude = -33.9, longitude = 151.2),
                altitude = AltitudeM(152.5),
                speed = SpeedMs(13.2),
                bearing = 182.0,
                accuracy = gpsAccuracyMeters,
                timestamp = wallMs,
                monotonicTimestampMillis = fixMonoMs
            ),
            baro = null,
            compass = null,
            baroAltitude = AltitudeM(151.25),
            qnh = PressureHpa(1012.8),
            isQNHCalibrated = true,
            verticalSpeed = VerticalSpeedMs(1.8),
            pressureAltitude = AltitudeM(149.0),
            baroGpsDelta = AltitudeM(-1.25),
            baroConfidence = ConfidenceLevel.HIGH,
            qnhCalibrationAgeSeconds = 5,
            agl = AltitudeM(45.0),
            aglTimestampMonoMs = aglTimestampMonoMs,
            thermalAverage = VerticalSpeedMs(0.7),
            currentLD = 32.0f,
            netto = VerticalSpeedMs(0.6),
            timestamp = wallMs,
            dataQuality = "GPS+BARO",
            varioValid = true
        )
    }
}
