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
class AdsbTrafficRepositoryEmergencyRolloutAndRollbackTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun emergencyAudio_rolloutPort_masterGateControlsRuntimeOutput() = runTest {
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
                    longitude = 151.2135,
                    trueTrackDeg = 270.0
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = true,
            cooldownMs = 30_000L
        )
        val rolloutPort = FakeEmergencyAudioRolloutPort(
            masterEnabled = false,
            shadowModeEnabled = false
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
            emergencyAudioRolloutPort = rolloutPort,
            emergencyAudioOutputPort = outputPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipMotion(trackDeg = 90.0, speedMps = 20.0)
            repository.updateOwnshipAltitudeMeters(0.0)
            repository.setEnabled(true)
            runCurrent()

            clock.setMonoMs(10_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            assertTrue(outputPort.events.isEmpty())
            assertFalse(repository.snapshot.value.emergencyAudioMasterRolloutEnabled)

            rolloutPort.setMasterEnabled(true)
            runCurrent()

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            assertEquals(1, outputPort.events.size)
            assertTrue(repository.snapshot.value.emergencyAudioMasterRolloutEnabled)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_kpiRollbackLatch_disablesMasterOutputAfterThresholdBreach() = runTest {
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
                    longitude = 151.2135,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_030L,
                    latitude = -33.8688,
                    longitude = 151.2134,
                    trueTrackDeg = 270.0
                ),
                successState(
                    timeSec = 1_710_000_040L,
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
        val rolloutPort = FakeEmergencyAudioRolloutPort(
            masterEnabled = true,
            shadowModeEnabled = false
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
            emergencyAudioRolloutPort = rolloutPort,
            emergencyAudioOutputPort = outputPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipMotion(trackDeg = 90.0, speedMps = 20.0)
            repository.updateOwnshipAltitudeMeters(0.0)
            repository.setEnabled(true)
            runCurrent()

            clock.setMonoMs(10_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, outputPort.events.size)

            settingsPort.setEnabled(false)
            runCurrent()
            settingsPort.setEnabled(true)
            runCurrent()
            settingsPort.setEnabled(false)
            runCurrent()

            val latchedSnapshot = repository.snapshot.value
            assertTrue(latchedSnapshot.emergencyAudioRollbackLatched)
            assertEquals("disable_within_5min_rate", latchedSnapshot.emergencyAudioRollbackReason)
            assertFalse(latchedSnapshot.emergencyAudioMasterRolloutEnabled)

            settingsPort.setEnabled(true)
            runCurrent()
            clock.setMonoMs(30_000L)
            advanceTimeBy(10_000L)
            runCurrent()
            assertEquals(1, outputPort.events.size)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_rolloutPort_overridesBootstrapConfig_without_mutating_seed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val featureFlags = AdsbEmergencyAudioFeatureFlags.bootstrap(
            emergencyAudioEnabled = true,
            emergencyAudioShadowMode = true
        )
        val rolloutPort = FakeEmergencyAudioRolloutPort(
            masterEnabled = false,
            shadowModeEnabled = false
        )
        val repository = createAdsbTrafficRepository(
            providerClient = SequenceProvider(emptyList()),
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioRolloutPort = rolloutPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()

            assertFalse(repository.snapshot.value.emergencyAudioMasterRolloutEnabled)
            assertFalse(repository.snapshot.value.emergencyAudioShadowModeEnabled)
            assertTrue(featureFlags.emergencyAudioEnabled)
            assertTrue(featureFlags.emergencyAudioShadowMode)

            rolloutPort.setMasterEnabled(true)
            rolloutPort.setShadowModeEnabled(true)
            runCurrent()

            assertTrue(repository.snapshot.value.emergencyAudioMasterRolloutEnabled)
            assertTrue(repository.snapshot.value.emergencyAudioShadowModeEnabled)
            assertTrue(featureFlags.emergencyAudioEnabled)
            assertTrue(featureFlags.emergencyAudioShadowMode)
        } finally {
            repository.stop()
            runCurrent()
        }
    }
}

