package com.trust3.xcpro.ogn

import com.trust3.xcpro.core.time.FakeClock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OgnThermalRepositoryTest {

    @Test
    fun detectsThermalHotspotAfterSustainedClimb() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        val hotspot = repository.hotspots.value.singleOrNull()
        assertEquals("UNK:ABCD01", hotspot?.sourceTargetId)
        assertEquals(OgnThermalHotspotState.ACTIVE, hotspot?.state)
        assertEquals(1000.0, hotspot?.startAltitudeMeters ?: Double.NaN, 0.1)
        assertEquals(1100.0, hotspot?.maxAltitudeMeters ?: Double.NaN, 0.1)
        assertTrue((hotspot?.maxClimbRateMps ?: 0.0) >= 1.8)
        assertTrue((hotspot?.snailColorIndex ?: 0) >= 9)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun recoversWhenConfirmedTrackerHotspotIdIsMissing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        clearConfirmedTrackerHotspotIds(repository)

        val nextTimestampMs = 60_000L
        clock.setMonoMs(nextTimestampMs)
        clock.setWallMs(nextTimestampMs)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = "ABCD01",
                climbMps = 1.4,
                altitudeMeters = 1120.0,
                timestampMs = nextTimestampMs,
                trackDegrees = 0.0
            )
        )
        runCurrent()

        val hotspot = repository.hotspots.value.singleOrNull()
        assertTrue(hotspot != null)
        assertTrue(hotspot?.id?.isNotBlank() == true)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun finalizesHotspotWhenTargetDisappearsBeyondTimeout() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        clock.setMonoMs(120_000L)
        clock.setWallMs(120_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val hotspot = repository.hotspots.value.singleOrNull()
        assertEquals(OgnThermalHotspotState.FINALIZED, hotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun staleTargetFinalizesWhileOtherTargetsContinueUpdating() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )
        val activeTargetId = "AAAA01"
        val staleTargetId = "BBBB02"
        val activeTargetKey = "UNK:$activeTargetId"
        val staleTargetKey = "UNK:$staleTargetId"

        for (index in 0..5) {
            val timestampMs = index * 10_000L
            val altitudeStep = index * 20.0
            val track = if (index % 2 == 0) 0.0 else 180.0
            val active = sampleTarget(
                id = activeTargetId,
                climbMps = 1.6,
                altitudeMeters = 1000.0 + altitudeStep,
                timestampMs = timestampMs,
                latitude = -35.10,
                longitude = 149.10,
                trackDegrees = track
            )
            val stale = sampleTarget(
                id = staleTargetId,
                climbMps = 1.5,
                altitudeMeters = 900.0 + altitudeStep,
                timestampMs = timestampMs,
                latitude = -35.20,
                longitude = 149.20,
                trackDegrees = track
            )
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(active, stale)
            runCurrent()
        }

        val staleActive = repository.hotspots.value
            .firstOrNull { it.sourceTargetId == staleTargetKey }
        assertEquals(OgnThermalHotspotState.ACTIVE, staleActive?.state)

        val frozenStaleTarget = sampleTarget(
            id = staleTargetId,
            climbMps = 1.5,
            altitudeMeters = 1000.0,
            timestampMs = 50_000L,
            latitude = -35.20,
            longitude = 149.20,
            trackDegrees = 180.0
        )
        for (timestampMs in listOf(60_000L, 70_000L, 80_000L)) {
            val altitudeStep = (timestampMs / 10_000L) * 20.0
            val track = if ((timestampMs / 10_000L) % 2L == 0L) 0.0 else 180.0
            val active = sampleTarget(
                id = activeTargetId,
                climbMps = 1.7,
                altitudeMeters = 1000.0 + altitudeStep,
                timestampMs = timestampMs,
                latitude = -35.10,
                longitude = 149.10,
                trackDegrees = track
            )
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(active, frozenStaleTarget)
            runCurrent()
        }

        val staleHotspot = repository.hotspots.value
            .firstOrNull { it.sourceTargetId == staleTargetKey }
        val activeHotspot = repository.hotspots.value
            .firstOrNull { it.sourceTargetId == activeTargetKey }
        assertEquals(OgnThermalHotspotState.FINALIZED, staleHotspot?.state)
        assertEquals(OgnThermalHotspotState.ACTIVE, activeHotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun finalizesHotspotWhenStreamIsQuietWithoutFurtherTargetEmissions() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        val activeHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(OgnThermalHotspotState.ACTIVE, activeHotspot?.state)

        clock.setMonoMs(71_000L)
        clock.setWallMs(71_000L)
        advanceTimeBy(20_000L)
        runCurrent()

        val finalizedHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(OgnThermalHotspotState.FINALIZED, finalizedHotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun suppressedTargetKeysPurgeExistingHotspots() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()
        assertTrue(repository.hotspots.value.isNotEmpty())

        trafficRepository.suppressedTargetIds.value = setOf("UNK:ABCD01")
        runCurrent()

        assertTrue(repository.hotspots.value.isEmpty())

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun requiresTurnAbove730DegreesToConfirmThermal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitLowTurnClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        assertTrue(repository.hotspots.value.isEmpty())

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun averageClimbMetricsExcludeInitialFullTurn() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        val samples = listOf(
            sampleTarget(climbMps = 0.8, altitudeMeters = 1000.0, timestampMs = 0L, trackDegrees = 0.0),
            sampleTarget(climbMps = 0.8, altitudeMeters = 1008.0, timestampMs = 10_000L, trackDegrees = 180.0),
            sampleTarget(climbMps = 0.8, altitudeMeters = 1016.0, timestampMs = 20_000L, trackDegrees = 0.0),
            sampleTarget(climbMps = 2.4, altitudeMeters = 1040.0, timestampMs = 30_000L, trackDegrees = 180.0),
            sampleTarget(climbMps = 2.4, altitudeMeters = 1064.0, timestampMs = 40_000L, trackDegrees = 0.0),
            sampleTarget(climbMps = 2.4, altitudeMeters = 1088.0, timestampMs = 50_000L, trackDegrees = 180.0)
        )
        for (target in samples) {
            clock.setMonoMs(target.lastSeenMillis)
            clock.setWallMs(target.lastSeenMillis)
            trafficRepository.targets.value = listOf(target)
            runCurrent()
        }

        val hotspot = repository.hotspots.value.singleOrNull()
        assertEquals(2.4, hotspot?.averageClimbRateMps ?: Double.NaN, 1e-6)
        assertEquals(2.4, hotspot?.averageBottomToTopClimbRateMps ?: Double.NaN, 1e-6)
        assertEquals(
            climbRateToSnailColorIndex(2.4),
            hotspot?.snailColorIndex ?: -1
        )

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun retentionOneHourPrunesOldHotspots() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(1)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()
        assertTrue(repository.hotspots.value.isNotEmpty())

        trafficRepository.setEnabled(false)
        runCurrent()

        val oldAgeTimestampMs = 3_700_000L
        clock.setMonoMs(oldAgeTimestampMs)
        clock.setWallMs(oldAgeTimestampMs)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = "TICK01",
                climbMps = 0.0,
                altitudeMeters = 900.0,
                timestampMs = oldAgeTimestampMs,
                trackDegrees = 0.0
            )
        )
        runCurrent()

        assertTrue(repository.hotspots.value.isEmpty())
    }

    @Test
    fun retentionAllDayPrunesAtLocalMidnight() = runTest {
        val previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val baseWallMs = Instant.parse("2026-02-24T23:50:00Z").toEpochMilli()
            val clock = FakeClock(monoMs = baseWallMs, wallMs = baseWallMs)
            val trafficRepository = FakeOgnTrafficRepository()
            val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_ALL_DAY_HOURS)
            val repository = OgnThermalRepositoryImpl(
                ognTrafficRepository = trafficRepository,
                clock = clock,
                thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
            )

            emitClimbSamples(
                trafficRepository = trafficRepository,
                clock = clock,
                baseTimestampMs = baseWallMs
            ) { runCurrent() }
            runCurrent()
            assertTrue(repository.hotspots.value.isNotEmpty())

            val justBeforeMidnight = Instant.parse("2026-02-24T23:59:59Z").toEpochMilli()
            clock.setMonoMs(justBeforeMidnight)
            clock.setWallMs(justBeforeMidnight)
            trafficRepository.targets.value = listOf(
                sampleTarget(
                    id = "TICK02",
                    climbMps = 0.0,
                    altitudeMeters = 900.0,
                    timestampMs = justBeforeMidnight,
                    trackDegrees = 0.0
                )
            )
            runCurrent()
            assertTrue(repository.hotspots.value.isNotEmpty())

            val midnight = Instant.parse("2026-02-25T00:00:00Z").toEpochMilli()
            clock.setMonoMs(midnight)
            clock.setWallMs(midnight)
            trafficRepository.targets.value = listOf(
                sampleTarget(
                    id = "TICK03",
                    climbMps = 0.0,
                    altitudeMeters = 900.0,
                    timestampMs = midnight,
                    trackDegrees = 0.0
                )
            )
            runCurrent()

            assertTrue(repository.hotspots.value.isEmpty())
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }
}
