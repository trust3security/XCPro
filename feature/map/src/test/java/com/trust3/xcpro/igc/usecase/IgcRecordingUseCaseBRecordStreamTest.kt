package com.trust3.xcpro.igc.usecase

import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.igc.IgcRecordingActionSink
import com.trust3.xcpro.igc.data.IgcFinalizeResult
import com.trust3.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.trust3.xcpro.igc.domain.IgcSessionStateMachine
import com.trust3.xcpro.map.buildCompleteFlightData
import com.trust3.xcpro.map.defaultGps
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.domain.FlyingState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IgcRecordingUseCaseBRecordStreamTest {

    @Test
    fun emitsBRecordLine_whenRecordingAndLiveSampleArrives() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L)
        val flightStateFlow = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val useCase = buildIgcRecordingUseCase(
            flightStateSource = flightStateSource(flightStateFlow),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = InMemorySnapshotStore(),
            defaultDispatcher = dispatcher,
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 1_000L,
                finalizeTimeoutMs = 10_000L
            )
        )

        clock.setMonoMs(1_000L)
        flightStateFlow.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(2_000L)
        flightStateFlow.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()

        var emittedLine: String? = null
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            emittedLine = withTimeout(2_000L) {
                useCase.bRecordLines.first()
            }
        }
        runCurrent()

        flightDataRepository.update(
            data = liveFlightData(timestampMs = 10_000L),
            source = FlightDataRepository.Source.LIVE
        )
        advanceUntilIdle()
        collectJob.join()

        assertNotNull(emittedLine)
        assertTrue(emittedLine!!.startsWith("B"))
        assertEquals('A', emittedLine!![24])
        assertEquals(41, emittedLine!!.length)
    }

    @Test
    fun forwardsBRecordToActionSink_whenRecording() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L)
        val flightStateFlow = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val sink = CapturingSink()
        val useCase = buildIgcRecordingUseCase(
            flightStateSource = flightStateSource(flightStateFlow),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = InMemorySnapshotStore(),
            defaultDispatcher = dispatcher,
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 1_000L,
                finalizeTimeoutMs = 10_000L
            ),
            recordingActionSink = sink
        )

        clock.setMonoMs(1_000L)
        flightStateFlow.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(2_000L)
        flightStateFlow.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()

        flightDataRepository.update(
            data = liveFlightData(timestampMs = 10_000L),
            source = FlightDataRepository.Source.LIVE
        )
        advanceUntilIdle()

        assertEquals(1, sink.bRecords.size)
        assertTrue(sink.bRecords.single().second.startsWith("B"))
        assertEquals(10_000L, sink.bRecords.single().third)
    }

    @Test
    fun emitsBRecordLine_whenIasIsUnavailableButTasRemainsFinite() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L)
        val flightStateFlow = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val useCase = buildIgcRecordingUseCase(
            flightStateSource = flightStateSource(flightStateFlow),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = InMemorySnapshotStore(),
            defaultDispatcher = dispatcher,
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 1_000L,
                finalizeTimeoutMs = 10_000L
            )
        )

        clock.setMonoMs(1_000L)
        flightStateFlow.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(2_000L)
        flightStateFlow.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()

        var emittedLine: String? = null
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            emittedLine = withTimeout(2_000L) {
                useCase.bRecordLines.first()
            }
        }
        runCurrent()

        flightDataRepository.update(
            data = liveFlightData(timestampMs = 10_000L).copy(
                indicatedAirspeed = SpeedMs(Double.NaN),
                trueAirspeed = SpeedMs(26.0)
            ),
            source = FlightDataRepository.Source.LIVE
        )
        advanceUntilIdle()
        collectJob.join()

        assertNotNull(emittedLine)
        assertTrue(emittedLine!!.startsWith("B"))
    }

    @Test
    fun dropoutNoPosition_reusesLastPosition_andMarksV() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L)
        val flightStateFlow = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val useCase = buildIgcRecordingUseCase(
            flightStateSource = flightStateSource(flightStateFlow),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = InMemorySnapshotStore(),
            defaultDispatcher = dispatcher,
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 1_000L,
                finalizeTimeoutMs = 10_000L
            )
        )

        clock.setMonoMs(1_000L)
        flightStateFlow.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(2_000L)
        flightStateFlow.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()

        val lines = mutableListOf<String>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(2_000L) {
                useCase.bRecordLines.collect { line ->
                    lines += line
                    if (lines.size >= 2) {
                        cancel()
                    }
                }
            }
        }
        runCurrent()

        flightDataRepository.update(
            data = liveFlightData(timestampMs = 10_000L),
            source = FlightDataRepository.Source.LIVE
        )
        flightDataRepository.update(
            data = liveFlightData(timestampMs = 12_000L, gpsAvailable = false),
            source = FlightDataRepository.Source.LIVE
        )
        advanceUntilIdle()
        collectJob.join()

        assertEquals(2, lines.size)
        val first = lines[0]
        val second = lines[1]
        assertEquals(first.substring(7, 24), second.substring(7, 24))
        assertEquals('V', second[24])
    }

    @Test
    fun doesNotEmitWhenNotRecording() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L)
        val flightStateFlow = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val useCase = buildIgcRecordingUseCase(
            flightStateSource = flightStateSource(flightStateFlow),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = InMemorySnapshotStore(),
            defaultDispatcher = dispatcher,
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 1_000L,
                finalizeTimeoutMs = 10_000L
            )
        )

        flightDataRepository.update(
            data = liveFlightData(timestampMs = 10_000L),
            source = FlightDataRepository.Source.LIVE
        )
        advanceUntilIdle()

        val line = withTimeoutOrNull(200L) {
            useCase.bRecordLines.first()
        }
        assertNull(line)
    }

    private fun liveFlightData(
        timestampMs: Long,
        gpsAvailable: Boolean = true
    ): CompleteFlightData {
        val gps = if (gpsAvailable) {
            defaultGps(
                latitude = -33.865,
                longitude = 151.209,
                altitudeMeters = 905.0,
                speedMs = 22.0,
                accuracyMeters = 5f,
                timestampMillis = timestampMs
            )
        } else {
            null
        }
        return buildCompleteFlightData(
            gps = gps,
            timestampMillis = timestampMs
        ).copy(
            pressureAltitude = AltitudeM(850.0),
            indicatedAirspeed = SpeedMs(24.0),
            trueAirspeed = SpeedMs(26.0)
        )
    }

    private fun flightStateSource(
        source: MutableStateFlow<FlyingState>
    ): FlightStateSource = object : FlightStateSource {
        override val flightState = source
    }

    private class InMemorySnapshotStore : IgcSessionStateSnapshotStore {
        private var snapshot: IgcSessionStateMachine.Snapshot? = null
        override fun saveSnapshot(snapshot: IgcSessionStateMachine.Snapshot) {
            this.snapshot = snapshot
        }

        override fun loadSnapshot(): IgcSessionStateMachine.Snapshot? = snapshot

        override fun clearSnapshot() {
            snapshot = null
        }
    }

    private class CapturingSink : IgcRecordingActionSink {
        val bRecords = mutableListOf<Triple<Long, String, Long>>()

        override fun onSessionArmed(monoTimeMs: Long) = Unit
        override fun onStartRecording(sessionId: Long, preFlightGroundWindowMs: Long) = Unit
        override fun onFinalizeRecording(sessionId: Long, postFlightGroundWindowMs: Long): IgcFinalizeResult {
            return IgcFinalizeResult.Failure(
                code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                message = "not used in test"
            )
        }
        override fun onMarkCompleted(sessionId: Long) = Unit
        override fun onMarkFailed(sessionId: Long, reason: String) = Unit
        override fun onBRecord(sessionId: Long, line: String, sampleWallTimeMs: Long) {
            bRecords += Triple(sessionId, line, sampleWallTimeMs)
        }
        override fun onTaskEvent(sessionId: Long, payload: String) = Unit
        override fun onSystemEvent(sessionId: Long, payload: String) = Unit
    }
}
