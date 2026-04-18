package com.trust3.xcpro.adsb

import com.trust3.xcpro.core.time.FakeClock
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
class AdsbTrafficRepositoryOfflineTransitionTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun authFailedMode_survivesOfflineRecovery_withoutBlockingPolling() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(
                fixedState = OpenSkyTokenAccessState.CredentialsRejected("HTTP 401")
            ),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)
        assertEquals(AdsbAuthMode.AuthFailed, repository.snapshot.value.authMode)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)

        network.setOnline(false)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, provider.callCount)

        network.setOnline(true)
        runCurrent()
        assertEquals(2, provider.callCount)
        assertEquals(AdsbAuthMode.AuthFailed, repository.snapshot.value.authMode)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun rapidOfflineOnlineFlapping_pausesWhileOffline_andResumesOnEachReconnect() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        var callCount = 0
        val provider = object : AdsbProviderClient {
            override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
                callCount += 1
                return ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            }
        }
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, callCount)

        network.setOnline(false)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, callCount)

        network.setOnline(true)
        runCurrent()
        assertEquals(2, callCount)

        network.setOnline(false)
        runCurrent()
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(2, callCount)

        network.setOnline(true)
        runCurrent()
        assertEquals(3, callCount)
        assertEquals(2, repository.snapshot.value.networkOfflineTransitionCount)
        assertEquals(2, repository.snapshot.value.networkOnlineTransitionCount)
        assertEquals(true, repository.snapshot.value.networkOnline)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun extendedOfflineOnlineFlapping_countsTransitionsAndConverges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        var callCount = 0
        val provider = object : AdsbProviderClient {
            override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
                callCount += 1
                return ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            }
        }
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, callCount)

        repeat(5) {
            network.setOnline(false)
            runCurrent()
            advanceTimeBy(30_000L)
            runCurrent()

            network.setOnline(true)
            runCurrent()
        }

        assertEquals(6, callCount)
        assertEquals(5, repository.snapshot.value.networkOfflineTransitionCount)
        assertEquals(5, repository.snapshot.value.networkOnlineTransitionCount)
        assertEquals(true, repository.snapshot.value.networkOnline)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun networkTransitionTelemetry_tracksOfflineOnlineAndDwell() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val initial = repository.snapshot.value
        assertEquals(true, initial.networkOnline)
        assertEquals(0, initial.networkOfflineTransitionCount)
        assertEquals(0, initial.networkOnlineTransitionCount)
        assertEquals(null, initial.lastNetworkTransitionMonoMs)
        assertEquals(0L, initial.currentOfflineDwellMs)

        advanceTimeBy(5_000L)
        clock.setMonoMs(1_000L)
        network.setOnline(false)
        runCurrent()

        val offline = repository.snapshot.value
        assertEquals(false, offline.networkOnline)
        assertEquals(1, offline.networkOfflineTransitionCount)
        assertEquals(0, offline.networkOnlineTransitionCount)
        assertEquals(1_000L, offline.lastNetworkTransitionMonoMs)
        assertEquals(0L, offline.currentOfflineDwellMs)

        clock.setMonoMs(4_000L)
        advanceTimeBy(1_100L)
        runCurrent()
        assertEquals(false, repository.snapshot.value.networkOnline)
        assertEquals(1, repository.snapshot.value.networkOfflineTransitionCount)
        assertEquals(3_000L, repository.snapshot.value.currentOfflineDwellMs)

        clock.setMonoMs(5_000L)
        network.setOnline(true)
        runCurrent()

        val recovered = repository.snapshot.value
        assertEquals(true, recovered.networkOnline)
        assertEquals(1, recovered.networkOfflineTransitionCount)
        assertEquals(1, recovered.networkOnlineTransitionCount)
        assertEquals(5_000L, recovered.lastNetworkTransitionMonoMs)
        assertEquals(0L, recovered.currentOfflineDwellMs)
        assertTrue(recovered.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun offlineWait_progressesStaleThenExpiry_withoutAdditionalFetches() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)
        assertEquals(1, repository.targets.value.size)
        assertTrue(repository.targets.value.first().isStale.not())

        network.setOnline(false)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        clock.setMonoMs(61_000L)
        advanceTimeBy(1_100L)
        runCurrent()
        assertEquals(1, provider.callCount)
        assertTrue(repository.targets.value.firstOrNull()?.isStale == true)

        clock.setMonoMs(121_000L)
        advanceTimeBy(1_100L)
        runCurrent()
        assertEquals(1, provider.callCount)
        assertTrue(repository.targets.value.isEmpty())
        repository.stop()
    }

    @Test
    fun centerUpdateWhileOffline_purgesExpiredTargetsImmediately() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)
        assertEquals(1, repository.targets.value.size)

        network.setOnline(false)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        clock.setMonoMs(121_000L)
        repository.updateCenter(latitude = -33.9000, longitude = 151.2500)
        runCurrent()

        assertEquals(1, provider.callCount)
        assertTrue(repository.targets.value.isEmpty())
        repository.stop()
    }

    @Test
    fun disableStreaming_preservesTargets_untilExplicitClear() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.setEnabled(false)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.clearTargets()
        runCurrent()
        assertTrue(repository.targets.value.isEmpty())
    }

    @Test
    fun disabledState_centerAndOwnshipUpdates_doNotReselectCachedTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.setEnabled(false)
        runCurrent()
        repository.updateCenter(latitude = -34.2000, longitude = 150.5000)
        repository.updateOwnshipOrigin(latitude = -34.2000, longitude = 150.5000)
        repository.updateOwnshipAltitudeMeters(3_000.0)
        runCurrent()

        assertEquals(1, repository.targets.value.size)
        assertEquals("abc123", repository.targets.value.first().id.raw)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun reenable_afterDeferredCenterUpdate_reselectsImmediatelyFromCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.setEnabled(false)
        runCurrent()
        repository.updateCenter(latitude = -34.2000, longitude = 150.5000)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.setEnabled(true)
        runCurrent()

        assertTrue(repository.targets.value.isEmpty())
        repository.stop()
    }

}

