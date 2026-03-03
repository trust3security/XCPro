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
class AdsbTrafficRepositoryFilterAndAuthTest : AdsbTrafficRepositoryTestBase() {

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
    fun proximityTierTransitions_areDeterministicAcrossRepositoryRuns() = runTest {
        val firstRun = runRepositoryProximityTransitionScenario()
        val secondRun = runRepositoryProximityTransitionScenario()

        assertEquals("firstRun=$firstRun secondRun=$secondRun", firstRun, secondRun)
        assertEquals(4, firstRun.size)
        assertEquals(AdsbProximityTier.RED.code, firstRun[0].proximityTierCode)
        assertEquals(AdsbProximityTier.RED.code, firstRun[1].proximityTierCode)
        assertEquals(AdsbProximityTier.RED.code, firstRun[2].proximityTierCode)
        assertEquals(AdsbProximityTier.GREEN.code, firstRun[3].proximityTierCode)
        assertEquals(false, firstRun.last().isClosing)
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

}
