package com.example.xcpro.adsb

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AdsbTrafficRepositoryTestBase {

    protected fun runRepositoryProximityTransitionScenario(): List<RepositoryTransitionSnapshot> {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                successState(
                    timeSec = 1_710_000_000L,
                    latitude = -33.8688,
                    longitude = 151.2200
                ),
                successState(
                    timeSec = 1_710_000_010L,
                    latitude = -33.8688,
                    longitude = 151.2140
                ),
                successState(
                    timeSec = 1_710_000_020L,
                    latitude = -33.8688,
                    longitude = 151.2145
                ),
                successState(
                    timeSec = 1_710_000_030L,
                    latitude = -33.8688,
                    longitude = 151.2145
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
        repository.updateOwnshipMotion(trackDeg = 90.0, speedMps = 20.0)
        repository.setEnabled(true)
        scheduler.runCurrent()
        val timeline = mutableListOf(snapshotOf(repository))

        clock.setMonoMs(10_000L)
        scheduler.advanceTimeBy(10_000L)
        scheduler.runCurrent()
        timeline += snapshotOf(repository)

        clock.setMonoMs(20_000L)
        scheduler.advanceTimeBy(10_000L)
        scheduler.runCurrent()
        timeline += snapshotOf(repository)

        clock.setMonoMs(30_000L)
        scheduler.advanceTimeBy(10_000L)
        scheduler.runCurrent()
        timeline += snapshotOf(repository)

        repository.stop()
        scheduler.runCurrent()
        return timeline
    }

    protected fun runRepositoryProximityDeEscalationScenario(): List<RepositoryTransitionSnapshot> {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                successState(
                    timeSec = 1_710_000_000L,
                    latitude = -33.8688,
                    longitude = 151.2200
                ),
                successState(
                    timeSec = 1_710_000_010L,
                    latitude = -33.8688,
                    longitude = 151.2140
                ),
                successState(
                    timeSec = 1_710_000_020L,
                    latitude = -33.8688,
                    longitude = 151.2145
                ),
                successState(
                    timeSec = 1_710_000_030L,
                    latitude = -33.8688,
                    longitude = 151.2145
                ),
                successState(
                    timeSec = 1_710_000_040L,
                    latitude = -33.8688,
                    longitude = 151.2420
                ),
                successState(
                    timeSec = 1_710_000_050L,
                    latitude = -33.8688,
                    longitude = 151.2420
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
        repository.updateOwnshipMotion(trackDeg = 90.0, speedMps = 20.0)
        repository.setEnabled(true)
        scheduler.runCurrent()
        val timeline = mutableListOf(snapshotOf(repository))
        fun advanceAndSnapshot(monoMs: Long) {
            clock.setMonoMs(monoMs)
            scheduler.advanceTimeBy(10_000L)
            scheduler.runCurrent()
            timeline += snapshotOf(repository)
        }
        advanceAndSnapshot(10_000L)
        advanceAndSnapshot(20_000L)
        advanceAndSnapshot(30_000L)

        advanceAndSnapshot(40_000L)
        advanceAndSnapshot(50_000L)

        repository.stop()
        scheduler.runCurrent()
        return timeline
    }

    protected fun runRepositoryEmergencyAudioOffOnScenario(): RepositoryEmergencyAudioRun {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                successState(
                    timeSec = 1_710_000_000L,
                    latitude = -33.8688,
                    longitude = 151.2200,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_010L,
                    latitude = -33.8688,
                    longitude = 151.2133,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_020L,
                    latitude = -33.8688,
                    longitude = 151.2132,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_030L,
                    latitude = -33.8688,
                    longitude = 151.2131,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_040L,
                    latitude = -33.8688,
                    longitude = 151.2148,
                    trueTrackDeg = null
                ),
                successState(
                    timeSec = 1_710_000_050L,
                    latitude = -33.8688,
                    longitude = 151.2130,
                    trueTrackDeg = 270.0
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = true,
            cooldownMs = 30_000L
        )
        val outputPort = FakeEmergencyAudioOutputPort()
        val featureFlags = AdsbEmergencyAudioFeatureFlags.bootstrap(
            emergencyAudioEnabled = true
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(token = "test-token"),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
            emergencyAudioOutputPort = outputPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        val timeline = mutableListOf<RepositoryEmergencyAudioTracePoint>()
        try {
            scheduler.runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipMotion(trackDeg = 90.0, speedMps = 20.0)
            repository.updateOwnshipAltitudeMeters(0.0)
            repository.setEnabled(true)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(0L, repository, outputPort.events.size)

            clock.setMonoMs(10_000L)
            scheduler.advanceTimeBy(10_000L)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(10_000L, repository, outputPort.events.size)

            settingsPort.setEnabled(false)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(15_000L, repository, outputPort.events.size)

            clock.setMonoMs(20_000L)
            scheduler.advanceTimeBy(10_000L)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(20_000L, repository, outputPort.events.size)

            settingsPort.setEnabled(true)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(25_000L, repository, outputPort.events.size)

            clock.setMonoMs(30_000L)
            scheduler.advanceTimeBy(10_000L)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(30_000L, repository, outputPort.events.size)

            clock.setMonoMs(40_000L)
            scheduler.advanceTimeBy(10_000L)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(40_000L, repository, outputPort.events.size)

            clock.setMonoMs(50_000L)
            scheduler.advanceTimeBy(10_000L)
            scheduler.runCurrent()
            timeline += emergencyTracePoint(50_000L, repository, outputPort.events.size)
        } finally {
            repository.stop()
            scheduler.runCurrent()
        }
        return RepositoryEmergencyAudioRun(
            timeline = timeline,
            outputEvents = outputPort.events.toList(),
            finalSnapshot = repository.snapshot.value
        )
    }

    protected fun successState(
        timeSec: Long,
        latitude: Double,
        longitude: Double,
        trueTrackDeg: Double? = 180.0,
        timePositionSec: Long = 1_710_000_000L,
        lastContactSec: Long = 1_710_000_001L
    ): ProviderResult.Success = ProviderResult.Success(
        response = OpenSkyResponse(
            timeSec = timeSec,
            states = listOf(
                state(
                    icao24 = "abc123",
                    latitude = latitude,
                    longitude = longitude,
                    altitudeM = 500.0,
                    speedMps = 40.0,
                    trueTrackDeg = trueTrackDeg,
                    timePositionSec = timePositionSec,
                    lastContactSec = lastContactSec
                )
            )
        ),
        httpCode = 200,
        remainingCredits = null
    )

    protected fun snapshotOf(repository: AdsbTrafficRepositoryImpl): RepositoryTransitionSnapshot {
        val target = repository.targets.value.firstOrNull()
        return RepositoryTransitionSnapshot(
            proximityTierCode = target?.proximityTier?.code,
            isClosing = target?.isClosing
        )
    }

    protected data class RepositoryTransitionSnapshot(
        val proximityTierCode: Int?,
        val isClosing: Boolean?
    )

    protected data class RepositoryEmergencyAudioTracePoint(
        val atMonoMs: Long,
        val state: AdsbEmergencyAudioAlertState,
        val triggerCount: Int,
        val blockEpisodes: Int,
        val lastAlertMonoMs: Long?,
        val activeTargetId: String?,
        val outputEventsCount: Int
    )

    protected data class RepositoryEmergencyAudioRun(
        val timeline: List<RepositoryEmergencyAudioTracePoint>,
        val outputEvents: List<EmergencyOutputEvent>,
        val finalSnapshot: AdsbTrafficSnapshot
    )

    protected fun emergencyTracePoint(
        atMonoMs: Long,
        repository: AdsbTrafficRepositoryImpl,
        outputEventsCount: Int
    ): RepositoryEmergencyAudioTracePoint {
        val snapshot = repository.snapshot.value
        return RepositoryEmergencyAudioTracePoint(
            atMonoMs = atMonoMs,
            state = snapshot.emergencyAudioState,
            triggerCount = snapshot.emergencyAudioAlertTriggerCount,
            blockEpisodes = snapshot.emergencyAudioCooldownBlockEpisodeCount,
            lastAlertMonoMs = snapshot.emergencyAudioLastAlertMonoMs,
            activeTargetId = snapshot.emergencyAudioActiveTargetId,
            outputEventsCount = outputEventsCount
        )
    }

    protected fun assertBboxApproximatelyEquals(expected: BBox, actual: BBox) {
        assertEquals(expected.lamin, actual.lamin, 1e-6)
        assertEquals(expected.lomin, actual.lomin, 1e-6)
        assertEquals(expected.lamax, actual.lamax, 1e-6)
        assertEquals(expected.lomax, actual.lomax, 1e-6)
    }
}
