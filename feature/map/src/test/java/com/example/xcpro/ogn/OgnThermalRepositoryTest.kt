package com.example.xcpro.ogn

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OgnThermalRepositoryTest {

    @Test
    fun detectsThermalHotspotAfterSustainedClimb() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        val hotspot = repository.hotspots.value.singleOrNull()
        assertNotNull(hotspot)
        hotspot!!
        assertEquals("ABCD01", hotspot.sourceTargetId)
        assertEquals(OgnThermalHotspotState.ACTIVE, hotspot.state)
        assertEquals(1000.0, hotspot.startAltitudeMeters ?: Double.NaN, 0.1)
        assertEquals(1080.0, hotspot.maxAltitudeMeters ?: Double.NaN, 0.1)
        assertTrue(hotspot.maxClimbRateMps >= 1.8)
        assertTrue(hotspot.snailColorIndex >= 9)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun finalizesHotspotWhenTargetDisappearsBeyondTimeout() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        clock.setMonoMs(120_000L)
        clock.setWallMs(120_000L)
        trafficRepository.targets.value = emptyList()
        runCurrent()

        val hotspot = repository.hotspots.value.singleOrNull()
        assertNotNull(hotspot)
        assertEquals(OgnThermalHotspotState.FINALIZED, hotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    @Test
    fun staleTargetFinalizesWhileOtherTargetsContinueUpdating() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val trafficRepository = FakeOgnTrafficRepository()
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )
        val activeTargetId = "AAAA01"
        val staleTargetId = "BBBB02"

        for (timestampMs in listOf(0L, 10_000L, 20_000L, 30_000L)) {
            val altitudeStep = (timestampMs / 10_000L) * 20.0
            val active = sampleTarget(
                id = activeTargetId,
                climbMps = 1.6,
                altitudeMeters = 1000.0 + altitudeStep,
                timestampMs = timestampMs,
                latitude = -35.10,
                longitude = 149.10
            )
            val stale = sampleTarget(
                id = staleTargetId,
                climbMps = 1.5,
                altitudeMeters = 900.0 + altitudeStep,
                timestampMs = timestampMs,
                latitude = -35.20,
                longitude = 149.20
            )
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(active, stale)
            runCurrent()
        }

        val staleActive = repository.hotspots.value
            .firstOrNull { it.sourceTargetId == staleTargetId }
        assertEquals(OgnThermalHotspotState.ACTIVE, staleActive?.state)

        val frozenStaleTarget = sampleTarget(
            id = staleTargetId,
            climbMps = 1.5,
            altitudeMeters = 960.0,
            timestampMs = 30_000L,
            latitude = -35.20,
            longitude = 149.20
        )
        for (timestampMs in listOf(40_000L, 50_000L, 60_000L)) {
            val altitudeStep = (timestampMs / 10_000L) * 20.0
            val active = sampleTarget(
                id = activeTargetId,
                climbMps = 1.7,
                altitudeMeters = 1000.0 + altitudeStep,
                timestampMs = timestampMs,
                latitude = -35.10,
                longitude = 149.10
            )
            clock.setMonoMs(timestampMs)
            clock.setWallMs(timestampMs)
            trafficRepository.targets.value = listOf(active, frozenStaleTarget)
            runCurrent()
        }

        val staleHotspot = repository.hotspots.value
            .firstOrNull { it.sourceTargetId == staleTargetId }
        val activeHotspot = repository.hotspots.value
            .firstOrNull { it.sourceTargetId == activeTargetId }
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
        val repository = OgnThermalRepositoryImpl(
            ognTrafficRepository = trafficRepository,
            clock = clock,
            dispatcher = dispatcher
        )

        emitClimbSamples(trafficRepository, clock) { runCurrent() }
        runCurrent()

        val activeHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(OgnThermalHotspotState.ACTIVE, activeHotspot?.state)

        clock.setMonoMs(61_000L)
        clock.setWallMs(61_000L)
        advanceTimeBy(20_000L)
        runCurrent()

        val finalizedHotspot = repository.hotspots.value.singleOrNull()
        assertEquals(OgnThermalHotspotState.FINALIZED, finalizedHotspot?.state)

        shutdownRepository(trafficRepository)
        runCurrent()
    }

    private fun emitClimbSamples(
        trafficRepository: FakeOgnTrafficRepository,
        clock: FakeClock,
        advance: () -> Unit
    ) {
        val samples = listOf(
            sampleTarget(climbMps = 1.2, altitudeMeters = 1000.0, timestampMs = 0L),
            sampleTarget(climbMps = 1.6, altitudeMeters = 1020.0, timestampMs = 10_000L),
            sampleTarget(climbMps = 1.9, altitudeMeters = 1040.0, timestampMs = 20_000L),
            sampleTarget(climbMps = 1.8, altitudeMeters = 1060.0, timestampMs = 30_000L),
            sampleTarget(climbMps = 1.7, altitudeMeters = 1080.0, timestampMs = 40_000L)
        )

        for (target in samples) {
            clock.setMonoMs(target.lastSeenMillis)
            clock.setWallMs(target.lastSeenMillis)
            trafficRepository.targets.value = listOf(target)
            advance()
        }
    }

    private fun sampleTarget(
        id: String = "ABCD01",
        climbMps: Double,
        altitudeMeters: Double,
        timestampMs: Long,
        latitude: Double = -35.1,
        longitude: Double = 149.1
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = id,
        destination = "APRS",
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        trackDegrees = 120.0,
        groundSpeedMps = 26.0,
        verticalSpeedMps = climbMps,
        deviceIdHex = id.takeLast(6),
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
