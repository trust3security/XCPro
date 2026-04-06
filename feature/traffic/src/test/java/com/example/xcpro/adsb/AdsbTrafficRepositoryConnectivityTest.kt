package com.example.xcpro.adsb

import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryConnectivityTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun lowRemainingCredits_emptyScene_appliesBudgetFloorDelay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 40
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 39
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
        assertEquals(1, provider.callCount)

        advanceTimeBy(59_000L)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        repository.stop()
    }

    @Test
    fun anonymousNearbyTraffic_usesConservativePollingFloor() = runTest {
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
                    remainingCredits = 40
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 39
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
        assertEquals(1, provider.callCount)

        advanceTimeBy(29_000L)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        repository.stop()
    }

    @Test
    fun authenticatedNearbyTraffic_keepsFastPollingCadence() = runTest {
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
                    remainingCredits = 40
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 39
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(token = "token", hasCredentials = true),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(11_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        repository.stop()
    }

    @Test
    fun enableBeforeCenter_fetchesSoonAfterCenterIsProvided() = runTest {
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
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.setEnabled(true)
        runCurrent()
        assertEquals(0, provider.callCount)

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        advanceTimeBy(150L)
        runCurrent()

        assertTrue(provider.callCount >= 1)
        repository.stop()
    }

    @Test
    fun offlineAtStart_pausesPollingUntilNetworkReturns() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = false)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
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
        assertEquals(0, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        network.setOnline(true)
        runCurrent()
        assertTrue(provider.callCount >= 1)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun offlineRecovery_successUsesFreshMonoTimestampAfterWait() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = false)
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
        assertEquals(0, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        clock.setMonoMs(180_000L)
        advanceTimeBy(1_100L)
        runCurrent()

        network.setOnline(true)
        runCurrent()

        assertEquals(1, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        assertEquals(180_000L, repository.snapshot.value.lastSuccessMonoMs)
        assertTrue(repository.targets.value.firstOrNull()?.isStale == false)
        repository.stop()
    }

    @Test
    fun dnsFailureWhenNetworkDrops_waitsForOnlineAndResumesImmediately() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        var callCount = 0
        val provider = object : AdsbProviderClient {
            override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
                callCount += 1
                return if (callCount == 1) {
                    network.setOnline(false)
                    ProviderResult.NetworkError(
                        kind = AdsbNetworkFailureKind.DNS,
                        message = "UnknownHostException"
                    )
                } else {
                    ProviderResult.Success(
                        response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                        httpCode = 200,
                        remainingCredits = null
                    )
                }
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
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(1, callCount)

        network.setOnline(true)
        runCurrent()
        assertTrue(callCount >= 2)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun networkDropDuringDelay_pausesTimerAndResumesImmediatelyOnReconnect() = runTest {
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
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher,
            networkAvailabilityPort = network
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(5_000L)
        network.setOnline(false)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(5_000L)
        network.setOnline(true)
        runCurrent()
        assertEquals(2, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun networkDropDuringCircuitOpenWait_pausesProbeUntilReconnect() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = FakeNetworkAvailabilityPort(initialOnline = true)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.NetworkError(
                    kind = AdsbNetworkFailureKind.TIMEOUT,
                    message = "Timeout"
                ),
                ProviderResult.NetworkError(
                    kind = AdsbNetworkFailureKind.TIMEOUT,
                    message = "Timeout"
                ),
                ProviderResult.NetworkError(
                    kind = AdsbNetworkFailureKind.TIMEOUT,
                    message = "Timeout"
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
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
        assertEquals(1, provider.callCount)

        // Reach circuit-open state after third consecutive failure.
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(3, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        // Drop connectivity while circuit-open wait is active; probe must not execute offline.
        network.setOnline(false)
        runCurrent()
        advanceTimeBy(90_000L)
        runCurrent()
        assertEquals(3, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        // Probe should execute immediately after reconnect.
        network.setOnline(true)
        runCurrent()
        assertEquals(4, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun staleOfflineFlow_recoversFromFreshNetworkSnapshotWithoutRestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val network = SplitBrainNetworkAvailabilityPort(flowOnline = false, currentOnline = false)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
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
        assertEquals(0, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        network.setCurrentOnline(true)
        advanceTimeBy(1_100L)
        runCurrent()

        assertEquals(1, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        assertTrue(repository.snapshot.value.networkOnline)
        repository.stop()
    }

    private class SplitBrainNetworkAvailabilityPort(
        flowOnline: Boolean,
        currentOnline: Boolean
    ) : AdsbNetworkAvailabilityPort {
        private val _isOnline = MutableStateFlow(flowOnline)
        private var currentOnlineState = currentOnline

        override val isOnline: StateFlow<Boolean> = _isOnline

        override fun currentOnlineState(): Boolean = currentOnlineState

        fun setFlowOnline(online: Boolean) {
            _isOnline.value = online
        }

        fun setCurrentOnline(online: Boolean) {
            currentOnlineState = online
        }
    }

}

