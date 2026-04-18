package com.trust3.xcpro.ogn

import com.trust3.xcpro.core.time.FakeClock
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OgnThermalRepositoryAnchorTest {

    @Test
    fun confirmedHotspotStaysAtBestClimbAnchorWhenLaterSamplesAreWeaker() = runTest {
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
        val strongestLatitude = -35.1010
        val strongestLongitude = 149.1010
        emitThermalSamples(
            trafficRepository = trafficRepository,
            clock = clock,
            samples = listOf(
                sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = 0L, latitude = -35.1000, longitude = 149.1000, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = 10_000L, latitude = -35.1005, longitude = 149.1005, trackDegrees = 180.0),
                sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = 20_000L, latitude = strongestLatitude, longitude = strongestLongitude, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = 30_000L, latitude = -35.1015, longitude = 149.1015, trackDegrees = 180.0),
                sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = 40_000L, latitude = -35.1020, longitude = 149.1020, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.6, altitudeMeters = 1100.0, timestampMs = 50_000L, latitude = -35.1025, longitude = 149.1025, trackDegrees = 180.0)
            )
        ) { runCurrent() }
        runCurrent()

        val confirmedHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(strongestLatitude, confirmedHotspot?.latitude ?: Double.NaN, 1e-6)
        assertEquals(strongestLongitude, confirmedHotspot?.longitude ?: Double.NaN, 1e-6)
        assertEquals(OgnThermalHotspotState.ACTIVE, confirmedHotspot?.state)

        emitThermalSamples(
            trafficRepository = trafficRepository,
            clock = clock,
            samples = listOf(
                sampleTarget(climbMps = 1.4, altitudeMeters = 1120.0, timestampMs = 60_000L, latitude = -35.1030, longitude = 149.1030, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.3, altitudeMeters = 1140.0, timestampMs = 70_000L, latitude = -35.1035, longitude = 149.1035, trackDegrees = 180.0)
            )
        ) { runCurrent() }
        runCurrent()

        val activeHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(strongestLatitude, activeHotspot?.latitude ?: Double.NaN, 1e-6)
        assertEquals(strongestLongitude, activeHotspot?.longitude ?: Double.NaN, 1e-6)
        assertEquals(1.9, activeHotspot?.maxClimbRateMps ?: Double.NaN, 1e-6)

        clock.setMonoMs(120_000L)
        clock.setWallMs(120_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val finalizedHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(strongestLatitude, finalizedHotspot?.latitude ?: Double.NaN, 1e-6)
        assertEquals(strongestLongitude, finalizedHotspot?.longitude ?: Double.NaN, 1e-6)
        assertEquals(OgnThermalHotspotState.FINALIZED, finalizedHotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun confirmedHotspotReanchorsWhenLaterSampleHasStrongerClimb() = runTest {
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
        val strongerLatitude = -35.1030
        val strongerLongitude = 149.1030
        emitThermalSamples(
            trafficRepository = trafficRepository,
            clock = clock,
            samples = listOf(
                sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = 0L, latitude = -35.1000, longitude = 149.1000, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = 10_000L, latitude = -35.1005, longitude = 149.1005, trackDegrees = 180.0),
                sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = 20_000L, latitude = -35.1010, longitude = 149.1010, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = 30_000L, latitude = -35.1015, longitude = 149.1015, trackDegrees = 180.0),
                sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = 40_000L, latitude = -35.1020, longitude = 149.1020, trackDegrees = 0.0),
                sampleTarget(climbMps = 1.6, altitudeMeters = 1100.0, timestampMs = 50_000L, latitude = -35.1025, longitude = 149.1025, trackDegrees = 180.0),
                sampleTarget(climbMps = 2.3, altitudeMeters = 1120.0, timestampMs = 60_000L, latitude = strongerLatitude, longitude = strongerLongitude, trackDegrees = 0.0)
            )
        ) { runCurrent() }
        runCurrent()

        val hotspot = repository.hotspots.value.singleOrNull()
        assertEquals(strongerLatitude, hotspot?.latitude ?: Double.NaN, 1e-6)
        assertEquals(strongerLongitude, hotspot?.longitude ?: Double.NaN, 1e-6)
        assertEquals(2.3, hotspot?.maxClimbRateMps ?: Double.NaN, 1e-6)
        assertEquals(OgnThermalHotspotState.ACTIVE, hotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }
}
