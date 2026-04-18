package com.trust3.xcpro.adsb

import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryRuntimeTransitionsTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun reconnectNow_usesCurrentClockForLastSuccessTimestamp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
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
            tokenRepository = FakeTokenRepository(token = "test-token"),
            clock = clock,
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)
        assertEquals(0L, repository.snapshot.value.lastSuccessMonoMs)

        clock.setMonoMs(45_000L)
        repository.reconnectNow()
        runCurrent()

        assertEquals(2, provider.callCount)
        assertEquals(45_000L, repository.snapshot.value.lastSuccessMonoMs)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun networkError_snapshotProjectsDegradedFailureFields() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = createAdsbTrafficRepository(
            providerClient = SequenceProvider(
                listOf(
                    ProviderResult.NetworkError(
                        kind = AdsbNetworkFailureKind.TIMEOUT,
                        message = "Timeout"
                    )
                )
            ),
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val snapshot = repository.snapshot.value
        assertTrue(snapshot.connectionState is AdsbConnectionState.Error)
        assertEquals("Socket timeout", snapshot.lastError)
        assertEquals(AdsbNetworkFailureKind.TIMEOUT, snapshot.lastNetworkFailureKind)
        assertEquals(1, snapshot.consecutiveFailureCount)
        assertEquals(0L, snapshot.lastFailureMonoMs)
        assertTrue((snapshot.nextRetryMonoMs ?: 0L) >= 8_000L)
        repository.stop()
    }

    @Test
    fun circuitBreakerOpen_transitionsThroughProbeBeforeRecovery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var callCount = 0
        val provider = object : AdsbProviderClient {
            override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
                callCount += 1
                return when (callCount) {
                    1, 2, 3 -> ProviderResult.NetworkError(
                        kind = AdsbNetworkFailureKind.TIMEOUT,
                        message = "Timeout"
                    )
                    else -> {
                        delay(5_000L)
                        ProviderResult.Success(
                            response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                            httpCode = 200,
                            remainingCredits = null
                        )
                    }
                }
            }
        }
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, callCount)

        advanceTimeBy(16_000L)
        runCurrent()
        assertEquals(3, callCount)
        assertEquals(ADSB_ERROR_CIRCUIT_BREAKER_OPEN, repository.snapshot.value.lastError)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(4, callCount)
        assertEquals(ADSB_ERROR_CIRCUIT_BREAKER_PROBE, repository.snapshot.value.lastError)

        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }
}

