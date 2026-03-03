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
class AdsbTrafficRepositoryResilienceTest : AdsbTrafficRepositoryTestBase() {

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

}
