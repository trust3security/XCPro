package com.example.xcpro.adsb

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryEmergencyOutputTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun emergencyAudio_repositoryTrace_isDeterministicAcrossRuns() = runTest {
        val firstRun = runRepositoryEmergencyAudioOffOnScenario()
        val secondRun = runRepositoryEmergencyAudioOffOnScenario()

        assertEquals(firstRun.timeline, secondRun.timeline)
        assertEquals(firstRun.finalSnapshot, secondRun.finalSnapshot)
        assertEquals(firstRun.outputEvents, secondRun.outputEvents)
    }

    @Test
    fun emergencyAudio_masterFlagEnabled_emitsOutputOnEligibleTriggersOnly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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
                    longitude = 151.2140,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_020L,
                    latitude = -33.8688,
                    longitude = 151.2145,
                    trueTrackDeg = null
                ),
                successState(
                    timeSec = 1_710_000_030L,
                    latitude = -33.8688,
                    longitude = 151.2135,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_040L,
                    latitude = -33.8688,
                    longitude = 151.2134,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_050L,
                    latitude = -33.8688,
                    longitude = 151.2133,
                    trueTrackDeg = 270.0
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = true,
            cooldownMs = 30_000L
        )
        val outputPort = FakeEmergencyAudioOutputPort()
        val featureFlags = AdsbEmergencyAudioFeatureFlags().apply {
            emergencyAudioEnabled = true
        }
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(token = "test-token"),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
            emergencyAudioOutputPort = outputPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.setEnabled(true)
            runCurrent()

            clock.setMonoMs(10_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(30_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(40_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(50_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            assertEquals(2, outputPort.events.size)
            assertEquals(20_000L, outputPort.events[0].triggerMonoMs)
            assertEquals("abc123", outputPort.events[0].emergencyTargetId)
            assertEquals(50_000L, outputPort.events[1].triggerMonoMs)
            assertEquals("abc123", outputPort.events[1].emergencyTargetId)
            assertEquals(2, repository.snapshot.value.emergencyAudioAlertTriggerCount)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_shadowModeOnly_keepsTelemetryButSuppressesOutput() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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
                    longitude = 151.2140,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_020L,
                    latitude = -33.8688,
                    longitude = 151.2137,
                    trueTrackDeg = 270.0
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = true,
            cooldownMs = 30_000L
        )
        val outputPort = FakeEmergencyAudioOutputPort()
        val featureFlags = AdsbEmergencyAudioFeatureFlags().apply {
            emergencyAudioEnabled = false
            emergencyAudioShadowMode = true
        }
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(token = "test-token"),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
            emergencyAudioOutputPort = outputPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.setEnabled(true)
            runCurrent()

            clock.setMonoMs(10_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            assertEquals(1, repository.snapshot.value.emergencyAudioAlertTriggerCount)
            assertTrue(repository.snapshot.value.emergencyAudioFeatureGateOn)
            assertTrue(outputPort.events.isEmpty())
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_outputFailure_doesNotBreakRepositoryStateFlow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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
                    longitude = 151.2140,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_020L,
                    latitude = -33.8688,
                    longitude = 151.2137,
                    trueTrackDeg = 270.0
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = true,
            cooldownMs = 30_000L
        )
        val outputPort = FakeEmergencyAudioOutputPort(throwOnPlay = true)
        val featureFlags = AdsbEmergencyAudioFeatureFlags().apply {
            emergencyAudioEnabled = true
        }
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(token = "test-token"),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
            emergencyAudioOutputPort = outputPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.setEnabled(true)
            runCurrent()

            clock.setMonoMs(10_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            assertEquals(1, outputPort.events.size)
            assertEquals(1, repository.snapshot.value.emergencyAudioAlertTriggerCount)
            assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

}
