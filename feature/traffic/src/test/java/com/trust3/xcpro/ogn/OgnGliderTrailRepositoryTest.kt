package com.trust3.xcpro.ogn

import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OgnGliderTrailRepositoryTest {

    @Test
    fun emitsSegmentForFreshSamplesWithFiniteVario() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        clock.setMonoMs(1_000L)
        trafficRepository.targets.value = listOf(sampleTarget(timestampMs = 1_000L, latitude = -35.0, longitude = 149.0))
        runCurrent()

        clock.setMonoMs(11_000L)
        trafficRepository.targets.value = listOf(sampleTarget(timestampMs = 11_000L, latitude = -35.0004, longitude = 149.0004))
        runCurrent()

        val segment = repository.segments.value.singleOrNull()
        assertTrue(segment != null)
        assertEquals("UNK:ABCD01", segment?.sourceTargetId)
        assertTrue((segment?.widthPx ?: 0f) > OGN_TRAIL_ZERO_WIDTH_PX)

        clock.setMonoMs(2_000_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()
        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun emitsSegmentForFreshSamplesWithMissingVario_usingNeutralFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        clock.setMonoMs(1_000L)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                timestampMs = 1_000L,
                latitude = -35.0,
                longitude = 149.0,
                verticalSpeedMps = null
            )
        )
        runCurrent()

        clock.setMonoMs(11_000L)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                timestampMs = 11_000L,
                latitude = -35.0004,
                longitude = 149.0004,
                verticalSpeedMps = null
            )
        )
        runCurrent()

        val segment = repository.segments.value.singleOrNull()
        assertTrue(segment != null)
        assertEquals(OGN_TRAIL_ZERO_WIDTH_PX, segment?.widthPx ?: 0f, 1e-6f)
        assertEquals(OGN_THERMAL_SNAIL_COLOR_COUNT / 2, segment?.colorIndex ?: -1)

        clock.setMonoMs(2_000_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()
        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun duplicateSourceTimestampsDoNotCreateDuplicateSegments() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        val first = sampleTarget(timestampMs = 5_000L, latitude = -35.0, longitude = 149.0)
        val second = sampleTarget(timestampMs = 15_000L, latitude = -35.0003, longitude = 149.0002)
        val duplicateSecond = sampleTarget(timestampMs = 15_000L, latitude = -35.0003, longitude = 149.0002)

        clock.setMonoMs(first.lastSeenMillis)
        trafficRepository.targets.value = listOf(first)
        runCurrent()

        clock.setMonoMs(second.lastSeenMillis)
        trafficRepository.targets.value = listOf(second)
        runCurrent()

        clock.setMonoMs(16_000L)
        trafficRepository.targets.value = listOf(duplicateSecond)
        runCurrent()

        assertEquals(1, repository.segments.value.size)

        clock.setMonoMs(2_000_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()
        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun historyPrunesWhenNewInputArrivesAfterRetentionWindow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        val first = sampleTarget(timestampMs = 0L, latitude = -35.0, longitude = 149.0)
        val second = sampleTarget(timestampMs = 10_000L, latitude = -35.0004, longitude = 149.0004)

        clock.setMonoMs(0L)
        trafficRepository.targets.value = listOf(first)
        runCurrent()
        clock.setMonoMs(10_000L)
        trafficRepository.targets.value = listOf(second)
        runCurrent()
        assertEquals(1, repository.segments.value.size)

        // 20-minute history window + 1 ms; new input triggers deterministic pruning.
        clock.setMonoMs(1_210_001L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        assertTrue(repository.segments.value.isEmpty())

        clock.setMonoMs(2_000_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()
        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun historyPrunesViaHousekeepingWhenUpstreamIsQuiet() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        val first = sampleTarget(timestampMs = 0L, latitude = -35.0, longitude = 149.0)
        val second = sampleTarget(timestampMs = 10_000L, latitude = -35.0004, longitude = 149.0004)

        clock.setMonoMs(0L)
        trafficRepository.targets.value = listOf(first)
        runCurrent()
        clock.setMonoMs(10_000L)
        trafficRepository.targets.value = listOf(second)
        runCurrent()
        assertEquals(1, repository.segments.value.size)

        // Advance virtual delay to the first housekeeping deadline (sample retention).
        // The injected monotonic clock is moved far ahead before the callback runs,
        // so housekeeping prunes expired segments even with no new upstream targets.
        clock.setMonoMs(2_000_000L)
        advanceTimeBy(180_001L)
        runCurrent()

        assertTrue(repository.segments.value.isEmpty())

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun shortStepsAccumulateUntilDistanceThresholdThenEmit() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        val first = sampleTarget(timestampMs = 1_000L, latitude = -35.00000, longitude = 149.00000)
        val shortStep = sampleTarget(timestampMs = 11_000L, latitude = -35.00005, longitude = 149.00000)
        val cumulativeStep = sampleTarget(timestampMs = 21_000L, latitude = -35.00018, longitude = 149.00000)

        clock.setMonoMs(first.lastSeenMillis)
        trafficRepository.targets.value = listOf(first)
        runCurrent()

        clock.setMonoMs(shortStep.lastSeenMillis)
        trafficRepository.targets.value = listOf(shortStep)
        runCurrent()
        assertTrue(repository.segments.value.isEmpty())

        clock.setMonoMs(cumulativeStep.lastSeenMillis)
        trafficRepository.targets.value = listOf(cumulativeStep)
        runCurrent()

        val segment = repository.segments.value.singleOrNull()
        assertTrue(segment != null)
        assertEquals(first.latitude, segment?.startLatitude ?: 0.0, 1e-9)
        assertEquals(cumulativeStep.latitude, segment?.endLatitude ?: 0.0, 1e-9)

        clock.setMonoMs(2_000_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()
        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun noFreshSamplesAcrossManyTargets_doNotAddSegments() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        clock.setMonoMs(1_000L)
        trafficRepository.targets.value = (1..40).map { index ->
            sampleTarget(
                id = "ID${index.toString().padStart(4, '0')}",
                timestampMs = 1_000L,
                latitude = -35.0 - (index * 0.0001),
                longitude = 149.0 + (index * 0.0001)
            )
        }
        runCurrent()

        clock.setMonoMs(2_000L)
        trafficRepository.targets.value = (1..40).map { index ->
            sampleTarget(
                id = "ID${index.toString().padStart(4, '0')}",
                timestampMs = 1_000L,
                latitude = -35.0 - (index * 0.0001),
                longitude = 149.0 + (index * 0.0001)
            )
        }
        runCurrent()

        assertTrue(repository.segments.value.isEmpty())

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun normalizesTargetIdForSegmentSourceAndConsecutiveSampling() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        clock.setMonoMs(1_000L)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = " abcd01 ",
                timestampMs = 1_000L,
                latitude = -35.0000,
                longitude = 149.0000
            )
        )
        runCurrent()

        clock.setMonoMs(11_000L)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = "ABCD01",
                timestampMs = 11_000L,
                latitude = -35.0004,
                longitude = 149.0004
            )
        )
        runCurrent()

        val segment = repository.segments.value.singleOrNull()
        assertTrue(segment != null)
        assertEquals("UNK:ABCD01", segment?.sourceTargetId)
        assertEquals("UNK:ABCD01:11000", segment?.id)

        clock.setMonoMs(2_000_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()
        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun suppressedTargetKeys_purgeExistingTrailSegments() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnGliderTrailRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        clock.setMonoMs(1_000L)
        trafficRepository.targets.value = listOf(
            sampleTarget(timestampMs = 1_000L, latitude = -35.0, longitude = 149.0)
        )
        runCurrent()
        clock.setMonoMs(11_000L)
        trafficRepository.targets.value = listOf(
            sampleTarget(timestampMs = 11_000L, latitude = -35.0004, longitude = 149.0004)
        )
        runCurrent()
        assertEquals(1, repository.segments.value.size)

        trafficRepository.suppressedTargetIds.value = setOf("UNK:ABCD01")
        runCurrent()

        assertTrue(repository.segments.value.isEmpty())

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    private fun sampleTarget(
        id: String = "ABCD01",
        timestampMs: Long,
        latitude: Double,
        longitude: Double,
        verticalSpeedMps: Double? = 2.2
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = id,
        destination = "APRS",
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = 1000.0,
        trackDegrees = 120.0,
        groundSpeedMps = 26.0,
        verticalSpeedMps = verticalSpeedMps,
        deviceIdHex = normalizeOgnHex6OrNull(id),
        signalDb = 12.0,
        displayLabel = id,
        identity = null,
        rawComment = "",
        rawLine = "",
        timestampMillis = timestampMs,
        lastSeenMillis = timestampMs
    )

    private fun shutdownRepository(trafficRepository: FakeOgnTrafficRepository) {
        trafficRepository.setEnabled(false)
    }

    private class FakeOgnTrafficRepository : OgnTrafficRepository {
        override val targets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList())
        override val suppressedTargetIds = MutableStateFlow<Set<String>>(emptySet())
        override val snapshot = MutableStateFlow(
            OgnTrafficSnapshot(
                targets = emptyList(),
                connectionState = OgnConnectionState.DISCONNECTED,
                lastError = null,
                subscriptionCenterLat = null,
                subscriptionCenterLon = null,
                receiveRadiusKm = 150,
                ddbCacheAgeMs = null,
                reconnectBackoffMs = null,
                lastReconnectWallMs = null
            )
        )
        override val isEnabled = MutableStateFlow(true)

        override fun setEnabled(enabled: Boolean) {
            isEnabled.value = enabled
        }

        override fun updateCenter(latitude: Double, longitude: Double) = Unit

        override fun start() {
            setEnabled(true)
        }

        override fun stop() {
            setEnabled(false)
        }
    }
}
