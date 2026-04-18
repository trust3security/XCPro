package com.trust3.xcpro.adsb

import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryAuthAndEnableQueueTest : AdsbTrafficRepositoryTestBase() {

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
        val repository = createAdsbTrafficRepository(
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
    fun queuedMutations_beforeEnable_areAppliedInOrderForInitialPoll() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = CapturingBboxProvider()
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )
        val initialCenterLat = -33.7000
        val initialCenterLon = 151.5000
        val finalCenterLat = -34.1000
        val finalCenterLon = 150.9000
        val finalDistanceKm = 30

        repository.updateCenter(latitude = initialCenterLat, longitude = initialCenterLon)
        repository.updateDisplayFilters(
            maxDistanceKm = finalDistanceKm,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0
        )
        repository.updateCenter(latitude = finalCenterLat, longitude = finalCenterLon)
        repository.updateOwnshipOrigin(latitude = finalCenterLat, longitude = finalCenterLon)
        repository.setEnabled(true)
        runCurrent()

        assertEquals(finalCenterLat, repository.snapshot.value.centerLat ?: Double.NaN, 1e-6)
        assertEquals(finalCenterLon, repository.snapshot.value.centerLon ?: Double.NaN, 1e-6)
        assertEquals(finalDistanceKm, repository.snapshot.value.receiveRadiusKm)
        assertTrue(repository.snapshot.value.usesOwnshipReference)
        assertTrue(provider.capturedBboxes.isNotEmpty())
        assertBboxApproximatelyEquals(
            expected = AdsbGeoMath.computeBbox(
                centerLat = finalCenterLat,
                centerLon = finalCenterLon,
                radiusKm = finalDistanceKm.toDouble()
            ),
            actual = provider.capturedBboxes.first()
        )
        repository.stop()
    }
}

