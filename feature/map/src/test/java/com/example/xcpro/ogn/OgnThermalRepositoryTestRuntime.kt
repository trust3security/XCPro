package com.example.xcpro.ogn

import com.example.xcpro.core.time.FakeClock
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
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

    @Test
    fun publishesOnlyBestHotspotPerArea() = runTest {
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

        val tracks = listOf(0.0, 180.0, 0.0, 180.0, 0.0, 180.0)
        for (index in tracks.indices) {
            val timestampMs = index * 10_000L
            val altitude = 1000.0 + (index * 20.0)
            val weak = sampleTarget(
                id = "WEAK01",
                climbMps = 1.2,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = -35.1000,
                longitude = 149.1000,
                trackDegrees = tracks[index]
            )
            val strong = sampleTarget(
                id = "BEST01",
                climbMps = 2.4,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = -35.1008,
                longitude = 149.1008,
                trackDegrees = tracks[index]
            )
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(weak, strong)
            runCurrent()
        }

        val hotspots = repository.hotspots.value
        assertEquals(1, hotspots.size)
        assertTrue(hotspots.first().sourceTargetId.endsWith(":BEST01"))

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun areaWinnerPrefersActiveRecentHotspotOverStrongerStaleFinalizedHotspot() = runTest {
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

        emitClimbSamplesForTarget(
            trafficRepository = trafficRepository,
            clock = clock,
            id = "STR001",
            baseTimestampMs = 0L,
            latitude = -35.1000,
            longitude = 149.1000,
            climbRates = listOf(2.8, 2.9, 2.7, 2.8, 2.6, 2.7)
        ) { runCurrent() }
        runCurrent()

        clock.setMonoMs(120_000L)
        clock.setWallMs(120_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val strongFinalized = repository.hotspots.value
            .firstOrNull { it.sourceTargetId.endsWith(":STR001") }
        assertEquals(OgnThermalHotspotState.FINALIZED, strongFinalized?.state)

        val staleTimestampMs = 500_000L
        clock.setMonoMs(staleTimestampMs)
        clock.setWallMs(staleTimestampMs)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = "DUM001",
                climbMps = 0.0,
                altitudeMeters = 900.0,
                timestampMs = staleTimestampMs,
                latitude = -33.0,
                longitude = 148.0,
                trackDegrees = 0.0
            )
        )
        runCurrent()

        emitClimbSamplesForTarget(
            trafficRepository = trafficRepository,
            clock = clock,
            id = "FRE001",
            baseTimestampMs = staleTimestampMs,
            latitude = -35.1008,
            longitude = 149.1008,
            climbRates = listOf(1.4, 1.5, 1.4, 1.3, 1.4, 1.5)
        ) { runCurrent() }
        runCurrent()

        val hotspots = repository.hotspots.value
        assertEquals(1, hotspots.size)
        assertTrue(hotspots.single().sourceTargetId.endsWith(":FRE001"))
        assertEquals(OgnThermalHotspotState.ACTIVE, hotspots.single().state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun areaWinnerPrefersRecentFinalizedHotspotOverStrongerStaleFinalizedHotspot() = runTest {
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

        emitClimbSamplesForTarget(
            trafficRepository = trafficRepository,
            clock = clock,
            id = "STR002",
            baseTimestampMs = 0L,
            latitude = -35.2000,
            longitude = 149.2000,
            climbRates = listOf(2.9, 2.8, 2.7, 2.8, 2.9, 2.8)
        ) { runCurrent() }
        runCurrent()

        clock.setMonoMs(120_000L)
        clock.setWallMs(120_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val staleTimestampMs = 500_000L
        clock.setMonoMs(staleTimestampMs)
        clock.setWallMs(staleTimestampMs)
        trafficRepository.targets.value = listOf(
            sampleTarget(
                id = "DUM002",
                climbMps = 0.0,
                altitudeMeters = 900.0,
                timestampMs = staleTimestampMs,
                latitude = -33.0,
                longitude = 148.0,
                trackDegrees = 0.0
            )
        )
        runCurrent()

        emitClimbSamplesForTarget(
            trafficRepository = trafficRepository,
            clock = clock,
            id = "FRE002",
            baseTimestampMs = staleTimestampMs,
            latitude = -35.2008,
            longitude = 149.2008,
            climbRates = listOf(1.4, 1.5, 1.4, 1.3, 1.4, 1.5)
        ) { runCurrent() }
        runCurrent()

        val finalizeRecentAtMs = 570_000L
        clock.setMonoMs(finalizeRecentAtMs)
        clock.setWallMs(finalizeRecentAtMs)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val hotspots = repository.hotspots.value
        assertEquals(1, hotspots.size)
        assertTrue(hotspots.single().sourceTargetId.endsWith(":FRE002"))
        assertEquals(OgnThermalHotspotState.FINALIZED, hotspots.single().state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun sourceTimestampResetStillAllowsNewThermalTracking() = runTest {
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

        val firstEpochStartWallMs = Instant.parse("2026-02-24T10:00:00Z").toEpochMilli()
        for (index in 0..5) {
            val sourceTimestampMs = firstEpochStartWallMs + index * 10_000L
            val monotonicTimestampMs = 100_000L + index * 10_000L
            val track = if (index % 2 == 0) 0.0 else 180.0
            clock.setMonoMs(monotonicTimestampMs)
            clock.setWallMs(sourceTimestampMs)
            trafficRepository.targets.value = listOf(
                sampleTarget(
                    id = "RST001",
                    climbMps = 1.7,
                    altitudeMeters = 1000.0 + index * 20.0,
                    timestampMs = sourceTimestampMs,
                    trackDegrees = track
                )
            )
            runCurrent()
        }

        val afterFirstEpoch = repository.hotspots.value
        assertTrue(afterFirstEpoch.any { it.id.endsWith("RST001-thermal-1") })

        clock.setMonoMs(220_000L)
        clock.setWallMs(firstEpochStartWallMs + 220_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val resetEpochStartSourceMs = 1_000L
        for (index in 0..5) {
            val sourceTimestampMs = resetEpochStartSourceMs + index * 10_000L
            val monotonicTimestampMs = 300_000L + index * 10_000L
            val wallTimestampMs = firstEpochStartWallMs + 300_000L + index * 10_000L
            val track = if (index % 2 == 0) 0.0 else 180.0
            clock.setMonoMs(monotonicTimestampMs)
            clock.setWallMs(wallTimestampMs)
            trafficRepository.targets.value = listOf(
                sampleTarget(
                    id = "RST001",
                    climbMps = 1.8,
                    altitudeMeters = 1200.0 + index * 20.0,
                    timestampMs = sourceTimestampMs,
                    trackDegrees = track
                )
            )
            runCurrent()
        }

        val afterResetEpoch = repository.hotspots.value
        assertTrue(afterResetEpoch.any { it.id.endsWith("RST001-thermal-2") })
        assertTrue(afterResetEpoch.any { it.state == OgnThermalHotspotState.ACTIVE })

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun publishesOnlyBestHotspotAcrossAdjacentLegacyGridCells() = runTest {
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

        val baseLatitude = -35.1
        val (weakLon, strongLon) = adjacentLegacyGridCellLongitudes(
            baseLatitude = baseLatitude,
            nearLongitude = 149.1
        )

        val tracks = listOf(0.0, 180.0, 0.0, 180.0, 0.0, 180.0)
        for (index in tracks.indices) {
            val timestampMs = index * 10_000L
            val altitude = 1000.0 + (index * 20.0)
            val weak = sampleTarget(
                id = "EDGEW1",
                climbMps = 1.2,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = baseLatitude,
                longitude = weakLon,
                trackDegrees = tracks[index]
            )
            val strong = sampleTarget(
                id = "EDGES1",
                climbMps = 2.4,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = baseLatitude,
                longitude = strongLon,
                trackDegrees = tracks[index]
            )
            clock.setMonoMs(200_000L + timestampMs)
            clock.setWallMs(200_000L + timestampMs)
            trafficRepository.targets.value = listOf(weak, strong)
            runCurrent()
        }

        val hotspots = repository.hotspots.value
        assertEquals(1, hotspots.size)
        assertTrue(hotspots.first().sourceTargetId.endsWith(":EDGES1"))

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun hotspotsDisplayPercent_keepsOnlyStrongestTopShare() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val thermalRetentionHoursFlow = MutableStateFlow(OGN_THERMAL_RETENTION_DEFAULT_HOURS)
        val hotspotsDisplayPercentFlow = MutableStateFlow(34)
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            thermalRetentionHoursFlow = thermalRetentionHoursFlow,
            hotspotsDisplayPercentFlow = hotspotsDisplayPercentFlow,
            localZoneId = ZoneId.of("UTC"),
            dispatcher = dispatcher
        )

        val tracks = listOf(0.0, 180.0, 0.0, 180.0, 0.0, 180.0)
        for (index in tracks.indices) {
            val timestampMs = index * 10_000L
            val altitude = 1000.0 + (index * 20.0)
            val strongest = sampleTarget(
                id = "STR001",
                climbMps = 2.8,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = -35.0000,
                longitude = 149.0000,
                trackDegrees = tracks[index]
            )
            val medium = sampleTarget(
                id = "MED001",
                climbMps = 2.0,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = -35.0200,
                longitude = 149.0200,
                trackDegrees = tracks[index]
            )
            val weak = sampleTarget(
                id = "WEK001",
                climbMps = 1.2,
                altitudeMeters = altitude,
                timestampMs = timestampMs,
                latitude = -35.0400,
                longitude = 149.0400,
                trackDegrees = tracks[index]
            )
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(strongest, medium, weak)
            runCurrent()
        }

        val top34 = repository.hotspots.value
        assertEquals(2, top34.size)
        assertTrue(top34.any { it.sourceTargetId.endsWith(":STR001") })
        assertTrue(top34.any { it.sourceTargetId.endsWith(":MED001") })

        hotspotsDisplayPercentFlow.value = 5
        runCurrent()

        val top5 = repository.hotspots.value
        assertEquals(1, top5.size)
        assertTrue(top5.single().sourceTargetId.endsWith(":STR001"))

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    private fun emitClimbSamples(
        trafficRepository: FakeOgnTrafficRepository,
        clock: FakeClock,
        baseTimestampMs: Long = 0L,
        advance: () -> Unit
    ) {
        val samples = listOf(
            sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = baseTimestampMs + 0L, trackDegrees = 0.0),
            sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = baseTimestampMs + 10_000L, trackDegrees = 180.0),
            sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = baseTimestampMs + 20_000L, trackDegrees = 0.0),
            sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = baseTimestampMs + 30_000L, trackDegrees = 180.0),
            sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = baseTimestampMs + 40_000L, trackDegrees = 0.0),
            sampleTarget(climbMps = 1.6, altitudeMeters = 1100.0, timestampMs = baseTimestampMs + 50_000L, trackDegrees = 180.0)
        )

        for (target in samples) {
            clock.setMonoMs(target.lastSeenMillis)
            clock.setWallMs(target.lastSeenMillis)
            trafficRepository.targets.value = listOf(target)
            advance()
        }
    }

    private fun emitClimbSamplesForTarget(
        trafficRepository: FakeOgnTrafficRepository,
        clock: FakeClock,
        id: String,
        baseTimestampMs: Long,
        latitude: Double,
        longitude: Double,
        climbRates: List<Double>,
        advance: () -> Unit
    ) {
        for (index in climbRates.indices) {
            val timestampMs = baseTimestampMs + index * 10_000L
            val trackDegrees = if (index % 2 == 0) 0.0 else 180.0
            val altitudeMeters = 1000.0 + index * 20.0
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(
                sampleTarget(
                    id = id,
                    climbMps = climbRates[index],
                    altitudeMeters = altitudeMeters,
                    timestampMs = timestampMs,
                    latitude = latitude,
                    longitude = longitude,
                    trackDegrees = trackDegrees
                )
            )
            advance()
        }
    }

    private fun emitLowTurnClimbSamples(
        trafficRepository: FakeOgnTrafficRepository,
        clock: FakeClock,
        advance: () -> Unit
    ) {
        val samples = listOf(
            sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = 0L, trackDegrees = 0.0),
            sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = 10_000L, trackDegrees = 180.0),
            sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = 20_000L, trackDegrees = 0.0),
            sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = 30_000L, trackDegrees = 180.0),
            sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = 40_000L, trackDegrees = 0.0)
        )
        for (target in samples) {
            clock.setMonoMs(target.lastSeenMillis)
            clock.setWallMs(target.lastSeenMillis)
            trafficRepository.targets.value = listOf(target)
            advance()
        }
    }

    private fun clearConfirmedTrackerHotspotIds(repository: OgnThermalRepositoryImpl) {
        val trackerMapField = OgnThermalRepositoryImpl::class.java
            .getDeclaredField("trackerByTargetId")
        trackerMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val trackerMap = trackerMapField.get(repository) as MutableMap<String, Any>
        for (tracker in trackerMap.values) {
            val confirmedField = tracker.javaClass.getDeclaredField("confirmed")
            confirmedField.isAccessible = true
            if (!confirmedField.getBoolean(tracker)) continue
            val hotspotIdField = tracker.javaClass.getDeclaredField("hotspotId")
            hotspotIdField.isAccessible = true
            hotspotIdField.set(tracker, null)
        }
    }

    private fun sampleTarget(
        id: String = "ABCD01",
        climbMps: Double,
        altitudeMeters: Double,
        timestampMs: Long,
        latitude: Double = -35.1,
        longitude: Double = 149.1,
        trackDegrees: Double = 120.0
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = id,
        destination = "APRS",
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        trackDegrees = trackDegrees,
        groundSpeedMps = 26.0,
        verticalSpeedMps = climbMps,
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

    private fun adjacentLegacyGridCellLongitudes(
        baseLatitude: Double,
        nearLongitude: Double
    ): Pair<Double, Double> {
        val metersPerDegreeLongitude =
            111_320.0 * abs(cos(Math.toRadians(baseLatitude))).coerceAtLeast(1.0e-6)
        val nearLongitudeMeters = nearLongitude * metersPerDegreeLongitude
        val nextBoundaryMeters = (floor(nearLongitudeMeters / 700.0) + 1.0) * 700.0
        val boundaryLongitude = nextBoundaryMeters / metersPerDegreeLongitude
        return (boundaryLongitude - 0.0001) to (boundaryLongitude + 0.0001)
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

