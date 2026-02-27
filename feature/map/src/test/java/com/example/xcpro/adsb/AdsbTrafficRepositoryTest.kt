package com.example.xcpro.adsb

import com.example.xcpro.adsb.domain.AdsbNetworkAvailabilityPort
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryTest {

    @Test
    fun rateLimitedResponse_entersBackingOffAndRecovers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.RateLimited(retryAfterSec = 120, remainingCredits = 9),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 8
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val backingOff = repository.snapshot.value.connectionState
        assertTrue(backingOff is AdsbConnectionState.BackingOff)
        assertEquals(120, (backingOff as AdsbConnectionState.BackingOff).retryAfterSec)

        advanceTimeBy(120_000L)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun transientNetworkErrors_applyBackoffThenRecover() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        advanceTimeBy(70_000L)
        runCurrent()

        assertTrue(provider.callCount >= 3)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun repeatedFailures_openCircuitBreaker_andDelayProbe() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(3, provider.callCount)

        advanceTimeBy(25_000L)
        runCurrent()
        assertEquals(3, provider.callCount)

        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(4, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun failedHalfOpenProbe_reopensCircuitBreaker_untilNextProbeWindow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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

        advanceTimeBy(50_000L)
        runCurrent()
        assertEquals(4, provider.callCount)

        advanceTimeBy(25_000L)
        runCurrent()
        assertEquals(4, provider.callCount)

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(5, provider.callCount)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun snapshotTelemetry_tracksFailureCountersAndRetryTimestamps() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
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
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val failedSnapshot = repository.snapshot.value
        assertEquals(1, failedSnapshot.consecutiveFailureCount)
        assertEquals(0L, failedSnapshot.lastFailureMonoMs)
        assertTrue((failedSnapshot.nextRetryMonoMs ?: 0L) >= 8_000L)

        advanceTimeBy(8_000L)
        runCurrent()
        val recoveredSnapshot = repository.snapshot.value
        assertTrue(recoveredSnapshot.connectionState is AdsbConnectionState.Active)
        assertEquals(0, recoveredSnapshot.consecutiveFailureCount)
        assertEquals(0L, recoveredSnapshot.lastFailureMonoMs)
        assertTrue(recoveredSnapshot.nextRetryMonoMs != null)

        repository.stop()
        runCurrent()
        assertEquals(null, repository.snapshot.value.nextRetryMonoMs)
        assertEquals(null, repository.snapshot.value.lastFailureMonoMs)
    }

    @Test
    fun dnsFailure_appliesOfflineRetryFloor() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.NetworkError(
                    kind = AdsbNetworkFailureKind.DNS,
                    message = "UnknownHostException"
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
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

        advanceTimeBy(14_000L)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun unexpectedProviderException_doesNotKillPollingLoop() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = ThrowThenSuccessProvider()
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
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun unexpectedLoopException_recoversAndContinuesPolling() = runTest {
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
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher,
            networkAvailabilityPort = ThrowingOnceNetworkAvailabilityPort()
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        advanceTimeBy(10_000L)
        runCurrent()
        assertTrue(provider.callCount >= 1)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun successResponse_keepsOnlyHighConfidenceAirborneTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "a1b2c3",
                                latitude = -33.8688,
                                longitude = 151.2093,
                                altitudeM = 30.48,
                                speedMps = 30.0
                            ),
                            state(
                                icao24 = "d4e5f6",
                                latitude = -33.8687,
                                longitude = 151.2094,
                                altitudeM = 300.0,
                                speedMps = 20.5778
                            ),
                            state(
                                icao24 = "abc123",
                                latitude = -33.8686,
                                longitude = 151.2095,
                                altitudeM = 300.0,
                                speedMps = 45.0
                            ),
                            state(
                                icao24 = "123abc",
                                latitude = -33.8685,
                                longitude = 151.2096,
                                altitudeM = 300.0,
                                speedMps = 45.0,
                                positionSource = 3
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = 12
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        assertEquals(1, repository.targets.value.size)
        assertEquals("abc123", repository.targets.value.first().id.raw)
        repository.stop()
    }

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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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
        val repository = AdsbTrafficRepositoryImpl(
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

    @Test
    fun centerUpdate_reselectsCachedTargetsWithoutWaitingForNextPoll() = runTest {
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
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)
        assertEquals(1, provider.callCount)

        repository.updateCenter(latitude = -34.1000, longitude = 150.8000)
        runCurrent()

        assertTrue(repository.targets.value.isEmpty())
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun ownshipOriginUpdate_recomputesDistanceWithoutNewFetch() = runTest {
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
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        val initialDistance = repository.targets.value.firstOrNull()?.distanceMeters ?: 0.0
        assertTrue(initialDistance < 100.0)
        assertEquals(1, provider.callCount)

        repository.updateOwnshipOrigin(latitude = -33.8600, longitude = 151.2000)
        runCurrent()

        val updatedDistance = repository.targets.value.firstOrNull()?.distanceMeters ?: 0.0
        assertTrue(updatedDistance > 500.0)
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun clearOwnshipOrigin_fallsBackToQueryCenterWithoutNewFetch() = runTest {
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

        repository.updateOwnshipOrigin(latitude = -33.8600, longitude = 151.2000)
        runCurrent()
        val ownshipDistance = repository.targets.value.firstOrNull()?.distanceMeters ?: 0.0
        assertTrue(ownshipDistance > 500.0)
        assertTrue(repository.targets.value.firstOrNull()?.usesOwnshipReference == true)
        assertTrue(repository.snapshot.value.usesOwnshipReference)

        repository.clearOwnshipOrigin()
        runCurrent()

        val fallbackDistance = repository.targets.value.firstOrNull()?.distanceMeters ?: 0.0
        assertTrue(fallbackDistance < 100.0)
        assertTrue(repository.targets.value.firstOrNull()?.usesOwnshipReference == false)
        assertTrue(repository.snapshot.value.usesOwnshipReference.not())
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun updateDisplayFilters_reselectsFromCacheAndUpdatesRadiusSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.7688,
                                longitude = 151.2093,
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
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(0, repository.targets.value.size)
        assertEquals(10, repository.snapshot.value.receiveRadiusKm)
        assertEquals(1, provider.callCount)

        repository.updateDisplayFilters(
            maxDistanceKm = 20,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0
        )
        runCurrent()

        assertEquals(1, repository.targets.value.size)
        assertEquals(20, repository.snapshot.value.receiveRadiusKm)
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun updateDisplayFilters_clampsMaxDistance_andPropagatesToProviderBbox() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = CapturingBboxProvider()
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )
        val centerLat = -33.8688
        val centerLon = 151.2093

        repository.updateCenter(latitude = centerLat, longitude = centerLon)
        repository.setEnabled(true)
        runCurrent()

        assertTrue(provider.capturedBboxes.isNotEmpty())
        assertBboxApproximatelyEquals(
            expected = AdsbGeoMath.computeBbox(
                centerLat = centerLat,
                centerLon = centerLon,
                radiusKm = ADSB_MAX_DISTANCE_DEFAULT_KM.toDouble()
            ),
            actual = provider.capturedBboxes[0]
        )

        repository.updateDisplayFilters(
            maxDistanceKm = ADSB_MAX_DISTANCE_MIN_KM - 100,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0
        )
        repository.reconnectNow()
        runCurrent()

        assertTrue(provider.capturedBboxes.size >= 2)
        assertBboxApproximatelyEquals(
            expected = AdsbGeoMath.computeBbox(
                centerLat = centerLat,
                centerLon = centerLon,
                radiusKm = ADSB_MAX_DISTANCE_MIN_KM.toDouble()
            ),
            actual = provider.capturedBboxes[1]
        )

        repository.updateDisplayFilters(
            maxDistanceKm = ADSB_MAX_DISTANCE_MAX_KM + 100,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0
        )
        repository.reconnectNow()
        runCurrent()

        assertTrue(provider.capturedBboxes.size >= 3)
        assertBboxApproximatelyEquals(
            expected = AdsbGeoMath.computeBbox(
                centerLat = centerLat,
                centerLon = centerLon,
                radiusKm = ADSB_MAX_DISTANCE_MAX_KM.toDouble()
            ),
            actual = provider.capturedBboxes[2]
        )
        repository.stop()
    }

    @Test
    fun ownshipAltitudeUpdate_appliesVerticalFilterWithoutNewFetch() = runTest {
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
                                altitudeM = 1_050.0,
                                speedMps = 40.0
                            ),
                            state(
                                icao24 = "def456",
                                latitude = -33.8686,
                                longitude = 151.2094,
                                altitudeM = 1_900.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.updateDisplayFilters(
            maxDistanceKm = 20,
            verticalAboveMeters = 100.0,
            verticalBelowMeters = 100.0
        )
        repository.setEnabled(true)
        runCurrent()

        // Ownship altitude unknown -> fail-open vertical filtering.
        assertEquals(2, repository.targets.value.size)
        assertEquals(2, repository.snapshot.value.withinVerticalCount)
        assertEquals(0, repository.snapshot.value.filteredByVerticalCount)
        assertEquals(1, provider.callCount)

        repository.updateOwnshipAltitudeMeters(1_000.0)
        runCurrent()

        assertEquals(1, repository.targets.value.size)
        assertEquals("abc123", repository.targets.value.first().id.raw)
        assertEquals(2, repository.snapshot.value.withinRadiusCount)
        assertEquals(1, repository.snapshot.value.withinVerticalCount)
        assertEquals(1, repository.snapshot.value.filteredByVerticalCount)
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun ownshipAltitudeJitter_isThrottledUntilMinInterval() = runTest {
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
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 1_100.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.updateDisplayFilters(
            maxDistanceKm = 20,
            verticalAboveMeters = 100.0,
            verticalBelowMeters = 100.0
        )
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        clock.setMonoMs(0L)
        repository.updateOwnshipAltitudeMeters(990.0)
        runCurrent()
        assertTrue(repository.targets.value.isEmpty())

        clock.setMonoMs(100L)
        repository.updateOwnshipAltitudeMeters(1_005.0)
        runCurrent()
        assertTrue(repository.targets.value.isEmpty())

        clock.setMonoMs(1_100L)
        repository.updateOwnshipAltitudeMeters(1_006.0)
        runCurrent()
        assertEquals(1, repository.targets.value.size)
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun tokenTransientFailure_usesAnonymousModeWithoutAuthFailedState() = runTest {
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
                fixedState = OpenSkyTokenAccessState.TransientFailure("UnknownHostException")
            ),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        assertEquals(AdsbAuthMode.Anonymous, repository.snapshot.value.authMode)
        repository.stop()
    }

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

    private class FakeTokenRepository(
        private var token: String? = null,
        private val hasCredentials: Boolean = false,
        private val fixedState: OpenSkyTokenAccessState? = null
    ) : OpenSkyTokenRepository {
        override suspend fun getTokenAccessState(): OpenSkyTokenAccessState {
            val fixed = fixedState
            if (fixed != null) return fixed
            val currentToken = token
            return when {
                !currentToken.isNullOrBlank() -> OpenSkyTokenAccessState.Available(currentToken)
                hasCredentials -> OpenSkyTokenAccessState.CredentialsRejected("test")
                else -> OpenSkyTokenAccessState.NoCredentials
            }
        }

        override suspend fun getValidTokenOrNull(): String? =
            (getTokenAccessState() as? OpenSkyTokenAccessState.Available)?.token

        override fun hasCredentials(): Boolean = hasCredentials || !token.isNullOrBlank()
        override fun invalidate() {
            token = null
        }
    }

    private class CapturingBboxProvider : AdsbProviderClient {
        val capturedBboxes = mutableListOf<BBox>()

        override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
            capturedBboxes += bbox
            return ProviderResult.Success(
                response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                httpCode = 200,
                remainingCredits = null
            )
        }
    }

    private fun assertBboxApproximatelyEquals(expected: BBox, actual: BBox) {
        assertEquals(expected.lamin, actual.lamin, 1e-6)
        assertEquals(expected.lomin, actual.lomin, 1e-6)
        assertEquals(expected.lamax, actual.lamax, 1e-6)
        assertEquals(expected.lomax, actual.lomax, 1e-6)
    }

    private class FakeNetworkAvailabilityPort(
        initialOnline: Boolean = true
    ) : AdsbNetworkAvailabilityPort {
        private val _isOnline = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = _isOnline

        fun setOnline(online: Boolean) {
            _isOnline.value = online
        }
    }

    private class ThrowingOnceNetworkAvailabilityPort : AdsbNetworkAvailabilityPort {
        private val delegate = MutableStateFlow(true)
        private var shouldThrow = true

        override val isOnline: StateFlow<Boolean>
            get() {
                if (shouldThrow) {
                    shouldThrow = false
                    throw IllegalStateException("Injected network availability failure")
                }
                return delegate
            }
    }

    private class DelayedSuccessProvider(
        private val delayMs: Long
    ) : AdsbProviderClient {
        var callCount: Int = 0
            private set

        override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
            callCount += 1
            delay(delayMs)
            return ProviderResult.Success(
                response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                httpCode = 200,
                remainingCredits = null
            )
        }
    }

    private class ThrowThenSuccessProvider : AdsbProviderClient {
        var callCount: Int = 0
            private set

        override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
            callCount += 1
            if (callCount == 1) {
                throw IllegalStateException("Injected failure")
            }
            return ProviderResult.Success(
                response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                httpCode = 200,
                remainingCredits = null
            )
        }
    }

    private class SequenceProvider(
        responses: List<ProviderResult>
    ) : AdsbProviderClient {
        private val queue = responses.toMutableList()
        var callCount: Int = 0
            private set

        override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
            callCount += 1
            if (queue.isEmpty()) {
                return ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = null, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            }
            return queue.removeAt(0)
        }
    }

    private fun state(
        icao24: String,
        latitude: Double,
        longitude: Double,
        altitudeM: Double?,
        speedMps: Double?,
        positionSource: Int? = 0
    ): OpenSkyStateVector = OpenSkyStateVector(
        icao24 = icao24,
        callsign = icao24.uppercase(),
        timePositionSec = 1_710_000_000L,
        lastContactSec = 1_710_000_001L,
        longitude = longitude,
        latitude = latitude,
        baroAltitudeM = altitudeM,
        velocityMps = speedMps,
        trueTrackDeg = 180.0,
        verticalRateMps = 0.0,
        geoAltitudeM = null,
        positionSource = positionSource,
        category = 2
    )
}
