package com.example.xcpro.terrain

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerrainElevationRepositoryTest {

    @Test
    fun getElevationMeters_prefersOfflineSourceBeforeOnlineFallback() = runTest {
        val offline = FakeTerrainDataSource(terrain = 415.0)
        val online = FakeTerrainDataSource(terrain = 900.0)
        val repository = repository(
            offline = offline,
            online = online
        )

        val result = repository.getElevationMeters(-33.0, 151.0)

        assertEquals(415.0, result ?: Double.NaN, 1e-6)
        assertEquals(1, offline.callCount)
        assertEquals(0, online.callCount)
    }

    @Test
    fun getElevationMeters_fallsBackToOnlineSourceWhenOfflineUnavailable() = runTest {
        val offline = FakeTerrainDataSource(terrain = null)
        val online = FakeTerrainDataSource(terrain = 250.0)
        val repository = repository(
            offline = offline,
            online = online
        )

        val result = repository.getElevationMeters(-33.0, 151.0)

        assertEquals(250.0, result ?: Double.NaN, 1e-6)
        assertEquals(1, offline.callCount)
        assertEquals(1, online.callCount)
    }

    @Test
    fun getElevationMeters_usesCacheOnRepeatedLookup() = runTest {
        val offline = FakeTerrainDataSource(terrain = 300.0)
        val online = FakeTerrainDataSource(terrain = null)
        val repository = repository(
            offline = offline,
            online = online
        )

        val first = repository.getElevationMeters(-33.0, 151.0)
        val second = repository.getElevationMeters(-33.0, 151.0)

        assertEquals(300.0, first ?: Double.NaN, 1e-6)
        assertEquals(300.0, second ?: Double.NaN, 1e-6)
        assertEquals(1, offline.callCount)
        assertEquals(0, online.callCount)
    }

    @Test
    fun getElevationMeters_appliesBackoffAfterFailedLookup() = runTest {
        val clock = FakeClock(monoMs = 10_000L)
        val offline = FakeTerrainDataSource(terrain = null)
        val online = FakeTerrainDataSource(terrain = null)
        val repository = repository(
            offline = offline,
            online = online,
            clock = clock
        )

        val first = repository.getElevationMeters(-33.0, 151.0)
        clock.advanceMonoMs(1_000L)
        val second = repository.getElevationMeters(-33.0, 151.0)

        assertNull(first)
        assertNull(second)
        assertEquals(1, offline.callCount)
        assertEquals(1, online.callCount)
    }

    @Test
    fun getElevationMeters_sameCellRetryWaitsForRetryIntervalEvenAfterBackoffElapsed() = runTest {
        val clock = FakeClock(monoMs = 0L)
        val offline = MutableTerrainDataSource(terrain = null)
        val online = MutableTerrainDataSource(terrain = null)
        val repository = repository(
            offline = offline,
            online = online,
            clock = clock
        )

        val first = repository.getElevationMeters(-33.0, 151.0)
        clock.advanceMonoMs(1_000L)
        val second = repository.getElevationMeters(-33.0, 151.0)

        offline.terrain = 410.0
        clock.advanceMonoMs(29_000L)
        val third = repository.getElevationMeters(-33.0, 151.0)

        assertNull(first)
        assertNull(second)
        assertEquals(410.0, third ?: Double.NaN, 1e-6)
        assertEquals(2, offline.callCount)
        assertEquals(1, online.callCount)
    }

    @Test
    fun getElevationMeters_successAfterFailureWindow_resetsCircuitAndBackoffState() = runTest {
        val clock = FakeClock(monoMs = 0L)
        val offline = MutableTerrainDataSource(terrain = null)
        val online = MutableTerrainDataSource(terrain = null)
        val repository = repository(
            offline = offline,
            online = online,
            clock = clock
        )

        assertNull(repository.getElevationMeters(-33.00, 151.00))
        clock.advanceMonoMs(5_000L)
        assertNull(repository.getElevationMeters(-33.01, 151.00))
        clock.advanceMonoMs(10_000L)
        assertNull(repository.getElevationMeters(-33.02, 151.00))
        clock.advanceMonoMs(20_000L)
        assertNull(repository.getElevationMeters(-33.03, 151.00))
        clock.advanceMonoMs(40_000L)
        assertNull(repository.getElevationMeters(-33.04, 151.00))

        clock.advanceMonoMs(1_000L)
        assertNull(repository.getElevationMeters(-33.05, 151.00))
        assertEquals(5, offline.callCount)
        assertEquals(5, online.callCount)

        offline.terrain = 555.0
        clock.advanceMonoMs(79_000L)
        val recovered = repository.getElevationMeters(-33.05, 151.00)
        assertEquals(555.0, recovered ?: Double.NaN, 1e-6)
        assertEquals(6, offline.callCount)
        assertEquals(5, online.callCount)

        offline.terrain = null
        clock.advanceMonoMs(1_000L)
        assertNull(repository.getElevationMeters(-33.06, 151.00))
        assertEquals(7, offline.callCount)
        assertEquals(6, online.callCount)

        offline.terrain = 575.0
        clock.advanceMonoMs(5_000L)
        val afterReset = repository.getElevationMeters(-33.07, 151.00)
        assertEquals(575.0, afterReset ?: Double.NaN, 1e-6)
        assertEquals(8, offline.callCount)
        assertEquals(6, online.callCount)
    }

    private fun repository(
        offline: TerrainElevationDataSource,
        online: TerrainElevationDataSource,
        clock: FakeClock = FakeClock(monoMs = 0L)
    ) = TerrainElevationRepository(
        offlineDataSource = offline,
        onlineDataSource = online,
        clock = clock
    )

    private class FakeTerrainDataSource(
        private val terrain: Double?
    ) : TerrainElevationDataSource {
        var callCount: Int = 0
            private set

        override suspend fun getElevationMeters(lat: Double, lon: Double): Double? {
            callCount += 1
            return terrain
        }
    }

    private class MutableTerrainDataSource(
        var terrain: Double?
    ) : TerrainElevationDataSource {
        var callCount: Int = 0
            private set

        override suspend fun getElevationMeters(lat: Double, lon: Double): Double? {
            callCount += 1
            return terrain
        }
    }
}
