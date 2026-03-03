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
class AdsbTrafficRepositoryLifecycleAndEmergencyTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun tokenCredentialRejection_setsAuthFailedMode() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(
                fixedState = OpenSkyTokenAccessState.CredentialsRejected("HTTP 401")
            ),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        assertEquals(AdsbAuthMode.AuthFailed, repository.snapshot.value.authMode)
        repository.stop()
    }

    @Test
    fun rapidDisableEnable_keepsPollingActive() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = DelayedSuccessProvider(delayMs = 3_000L)
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)

        repository.setEnabled(false)
        repository.setEnabled(true)
        runCurrent()

        advanceTimeBy(4_000L)
        runCurrent()
        assertTrue(repository.isEnabled.value)

        advanceTimeBy(31_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        repository.stop()
    }

    @Test
    fun emergencyAudio_redWithoutEmergencyRisk_doesNotTriggerAlert() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8688,
                                longitude = 151.2200,
                                altitudeM = 500.0,
                                speedMps = 40.0,
                                trueTrackDeg = null
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_010L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8688,
                                longitude = 151.2140,
                                altitudeM = 500.0,
                                speedMps = 40.0,
                                trueTrackDeg = null
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = true,
            cooldownMs = 30_000L
        )
        val featureFlags = AdsbEmergencyAudioFeatureFlags().apply {
            emergencyAudioEnabled = true
        }
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
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

            val snapshot = repository.snapshot.value
            assertTrue(repository.targets.value.isNotEmpty())
            assertEquals(AdsbProximityTier.RED, repository.targets.value.first().proximityTier)
            assertFalse(repository.targets.value.first().isEmergencyCollisionRisk)
            assertEquals(0, snapshot.emergencyAudioAlertTriggerCount)
            assertEquals(AdsbEmergencyAudioAlertState.IDLE, snapshot.emergencyAudioState)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_emergencyOnlyCooldownAndRetriggerBehavior() = runTest {
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
        val featureFlags = AdsbEmergencyAudioFeatureFlags().apply {
            emergencyAudioEnabled = true
        }
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            // Use authenticated mode so test cadence follows the 10s hot poll interval.
            tokenRepository = FakeTokenRepository(token = "test-token"),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
            emergencyAudioFeatureFlags = featureFlags
        )

        try {
            runCurrent()
            repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
            repository.updateOwnshipOrigin(latitude = -33.8688, longitude = 151.2093)
            repository.setEnabled(true)
            runCurrent() // t=0 first sample (no trend yet)

            clock.setMonoMs(10_000L)
            advanceTimeBy(10_000L)
            runCurrent() // t=10 second sample ingested; alert still idle on this tick
            val idleAfterSecondSample = repository.snapshot.value
            assertEquals(AdsbEmergencyAudioAlertState.IDLE, idleAfterSecondSample.emergencyAudioState)
            assertEquals(0, idleAfterSecondSample.emergencyAudioAlertTriggerCount)

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent() // t=20 active then cooldown within same poll cycle
            val cooldownStart = repository.snapshot.value
            assertEquals(AdsbEmergencyAudioAlertState.COOLDOWN, cooldownStart.emergencyAudioState)
            assertEquals(1, cooldownStart.emergencyAudioAlertTriggerCount)
            assertTrue(cooldownStart.emergencyAudioCooldownRemainingMs > 0L)

            clock.setMonoMs(30_000L)
            advanceTimeBy(10_000L)
            runCurrent() // t=30 cooldown continues
            val cooldownContinue = repository.snapshot.value
            assertEquals(AdsbEmergencyAudioAlertState.COOLDOWN, cooldownContinue.emergencyAudioState)
            assertEquals(1, cooldownContinue.emergencyAudioAlertTriggerCount)

            clock.setMonoMs(40_000L)
            advanceTimeBy(10_000L)
            runCurrent() // t=40 emergency present but blocked by cooldown
            val blocked = repository.snapshot.value
            assertEquals(AdsbEmergencyAudioAlertState.COOLDOWN, blocked.emergencyAudioState)
            assertEquals(1, blocked.emergencyAudioAlertTriggerCount)
            assertEquals(1, blocked.emergencyAudioCooldownBlockEpisodeCount)

            clock.setMonoMs(50_000L)
            advanceTimeBy(10_000L)
            runCurrent() // t=50 cooldown elapsed, emergency still present -> re-alert
            val reAlert = repository.snapshot.value
            assertEquals(AdsbEmergencyAudioAlertState.ACTIVE, reAlert.emergencyAudioState)
            assertEquals(2, reAlert.emergencyAudioAlertTriggerCount)
            assertEquals(1, reAlert.emergencyAudioCooldownBlockEpisodeCount)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_runtimeSettingEnable_immediatelyArmsPolicy() = runTest {
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
                    longitude = 151.2133,
                    trueTrackDeg = 270.0
                )
            )
        )
        val settingsPort = FakeEmergencyAudioSettingsPort(
            enabled = false,
            cooldownMs = 30_000L
        )
        val featureFlags = AdsbEmergencyAudioFeatureFlags().apply {
            emergencyAudioEnabled = true
        }
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = FakeNetworkAvailabilityPort(),
            emergencyAudioSettingsPort = settingsPort,
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

            val disabledSnapshot = repository.snapshot.value
            assertEquals(0, disabledSnapshot.emergencyAudioAlertTriggerCount)
            assertEquals(AdsbEmergencyAudioAlertState.DISABLED, disabledSnapshot.emergencyAudioState)

            settingsPort.setEnabled(true)
            runCurrent()

            val armedSnapshot = repository.snapshot.value
            assertEquals(AdsbEmergencyAudioAlertState.IDLE, armedSnapshot.emergencyAudioState)
            assertEquals(0, armedSnapshot.emergencyAudioAlertTriggerCount)
            assertTrue(armedSnapshot.emergencyAudioEnabledBySetting)

            clock.setMonoMs(20_000L)
            advanceTimeBy(10_000L)
            runCurrent()

            val enabledSnapshot = repository.snapshot.value
            assertTrue(
                enabledSnapshot.emergencyAudioState != AdsbEmergencyAudioAlertState.DISABLED
            )
            assertTrue(enabledSnapshot.emergencyAudioEnabledBySetting)
        } finally {
            repository.stop()
            runCurrent()
        }
    }

    @Test
    fun emergencyAudio_runtimeSettingOffOn_sameContinuousEmergency_doesNotDuplicateAlert() = runTest {
        val firstRun = runRepositoryEmergencyAudioOffOnScenario()
        val secondRun = runRepositoryEmergencyAudioOffOnScenario()

        assertEquals(firstRun, secondRun)
        // OFF->ON while emergency remains continuous must not emit a duplicate alert.
        assertEquals(1, firstRun.outputEvents.size)
        assertEquals(1, firstRun.finalSnapshot.emergencyAudioAlertTriggerCount)
        assertEquals(1, firstRun.timeline.maxOf { it.triggerCount })
    }

}
