package com.trust3.xcpro.replay

import com.trust3.xcpro.weather.wind.data.ReplayAirspeedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySampleEmitterTest {

    @Test
    fun qnh_changes_reconstructed_indicated_airspeed_from_true_only_sample() {
        val replaySensorSource = ReplaySensorSource()
        val replayAirspeedRepository = ReplayAirspeedRepository()
        val emitter = ReplaySampleEmitter(
            replaySensorSource = replaySensorSource,
            replayAirspeedRepository = replayAirspeedRepository,
            simConfig = ReplaySimConfig()
        )

        val start = IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 2_000.0,
            pressureAltitude = 2_000.0,
            indicatedAirspeedKmh = null,
            trueAirspeedKmh = 120.0
        )
        val next = start.copy(timestampMillis = 2_000L)
        val movement = MovementSnapshot(
            speedMs = 20.0,
            distanceMeters = 20.0,
            east = 0.0,
            north = 20.0
        )

        emitter.emitSample(
            current = start,
            previous = null,
            qnhHpa = 990.0,
            startTimestampMillis = start.timestampMillis,
            replayFusionRepository = null,
            movementOverride = movement
        )
        val lowQnh = requireNotNull(replayAirspeedRepository.airspeedFlow.value)

        emitter.emitSample(
            current = next,
            previous = start,
            qnhHpa = 1030.0,
            startTimestampMillis = start.timestampMillis,
            replayFusionRepository = null,
            movementOverride = movement
        )
        val highQnh = requireNotNull(replayAirspeedRepository.airspeedFlow.value)

        assertEquals(lowQnh.trueMs, highQnh.trueMs, 1e-6)
        assertTrue(highQnh.indicatedMs > lowQnh.indicatedMs)
    }

    @Test
    fun bothAirspeedsPresent_convertsIasAndTasFromKmhToMs() {
        val replaySensorSource = ReplaySensorSource()
        val replayAirspeedRepository = ReplayAirspeedRepository()
        val emitter = ReplaySampleEmitter(
            replaySensorSource = replaySensorSource,
            replayAirspeedRepository = replayAirspeedRepository,
            simConfig = ReplaySimConfig()
        )
        val point = IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 0.0,
            pressureAltitude = 0.0,
            indicatedAirspeedKmh = 108.0,
            trueAirspeedKmh = 144.0
        )

        emitter.emitSample(
            current = point,
            previous = null,
            qnhHpa = 1013.25,
            startTimestampMillis = point.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )
        val sample = requireNotNull(replayAirspeedRepository.airspeedFlow.value)

        assertEquals(108.0 * KMH_TO_MS, sample.indicatedMs, 1e-6)
        assertEquals(144.0 * KMH_TO_MS, sample.trueMs, 1e-6)
    }

    @Test
    fun iasOnly_convertsInputFromKmhToMs() {
        val replaySensorSource = ReplaySensorSource()
        val replayAirspeedRepository = ReplayAirspeedRepository()
        val emitter = ReplaySampleEmitter(
            replaySensorSource = replaySensorSource,
            replayAirspeedRepository = replayAirspeedRepository,
            simConfig = ReplaySimConfig()
        )
        val point = IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 0.0,
            pressureAltitude = 0.0,
            indicatedAirspeedKmh = 90.0,
            trueAirspeedKmh = null
        )

        emitter.emitSample(
            current = point,
            previous = null,
            qnhHpa = 1013.25,
            startTimestampMillis = point.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )
        val sample = requireNotNull(replayAirspeedRepository.airspeedFlow.value)
        val expectedMs = 90.0 * KMH_TO_MS

        assertEquals(expectedMs, sample.indicatedMs, 1e-6)
        assertEquals(expectedMs, sample.trueMs, 1e-6)
    }

    @Test
    fun tasOnly_convertsInputFromKmhToMs() {
        val replaySensorSource = ReplaySensorSource()
        val replayAirspeedRepository = ReplayAirspeedRepository()
        val emitter = ReplaySampleEmitter(
            replaySensorSource = replaySensorSource,
            replayAirspeedRepository = replayAirspeedRepository,
            simConfig = ReplaySimConfig()
        )
        val point = IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 0.0,
            pressureAltitude = 0.0,
            indicatedAirspeedKmh = null,
            trueAirspeedKmh = 126.0
        )

        emitter.emitSample(
            current = point,
            previous = null,
            qnhHpa = 1013.25,
            startTimestampMillis = point.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )
        val sample = requireNotNull(replayAirspeedRepository.airspeedFlow.value)
        val expectedMs = 126.0 * KMH_TO_MS

        assertEquals(expectedMs, sample.trueMs, 1e-6)
        assertEquals(expectedMs, sample.indicatedMs, 1e-6)
    }

    @Test
    fun nullIasAndTas_resetsAirspeedSample() {
        val replaySensorSource = ReplaySensorSource()
        val replayAirspeedRepository = ReplayAirspeedRepository()
        val emitter = ReplaySampleEmitter(
            replaySensorSource = replaySensorSource,
            replayAirspeedRepository = replayAirspeedRepository,
            simConfig = ReplaySimConfig()
        )
        val validPoint = IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 0.0,
            pressureAltitude = 0.0,
            indicatedAirspeedKmh = 100.0,
            trueAirspeedKmh = 110.0
        )
        val nullPoint = validPoint.copy(
            timestampMillis = 2_000L,
            indicatedAirspeedKmh = null,
            trueAirspeedKmh = null
        )

        emitter.emitSample(
            current = validPoint,
            previous = null,
            qnhHpa = 1013.25,
            startTimestampMillis = validPoint.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )
        assertTrue(replayAirspeedRepository.airspeedFlow.value != null)

        emitter.emitSample(
            current = nullPoint,
            previous = validPoint,
            qnhHpa = 1013.25,
            startTimestampMillis = validPoint.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )

        assertNull(replayAirspeedRepository.airspeedFlow.value)
    }

    @Test
    fun nonFiniteIasOrTas_resetsAirspeedSample() {
        val replaySensorSource = ReplaySensorSource()
        val replayAirspeedRepository = ReplayAirspeedRepository()
        val emitter = ReplaySampleEmitter(
            replaySensorSource = replaySensorSource,
            replayAirspeedRepository = replayAirspeedRepository,
            simConfig = ReplaySimConfig()
        )
        val validPoint = IgcPoint(
            timestampMillis = 1_000L,
            latitude = 0.0,
            longitude = 0.0,
            gpsAltitude = 0.0,
            pressureAltitude = 0.0,
            indicatedAirspeedKmh = 100.0,
            trueAirspeedKmh = 110.0
        )
        val invalidPoint = validPoint.copy(
            timestampMillis = 2_000L,
            indicatedAirspeedKmh = Double.NaN,
            trueAirspeedKmh = null
        )

        emitter.emitSample(
            current = validPoint,
            previous = null,
            qnhHpa = 1013.25,
            startTimestampMillis = validPoint.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )
        assertTrue(replayAirspeedRepository.airspeedFlow.value != null)

        emitter.emitSample(
            current = invalidPoint,
            previous = validPoint,
            qnhHpa = 1013.25,
            startTimestampMillis = validPoint.timestampMillis,
            replayFusionRepository = null,
            movementOverride = MOVEMENT
        )

        assertNull(replayAirspeedRepository.airspeedFlow.value)
    }

    private companion object {
        private const val KMH_TO_MS = 1000.0 / 3600.0
        private val MOVEMENT = MovementSnapshot(
            speedMs = 20.0,
            distanceMeters = 20.0,
            east = 0.0,
            north = 20.0
        )
    }
}
