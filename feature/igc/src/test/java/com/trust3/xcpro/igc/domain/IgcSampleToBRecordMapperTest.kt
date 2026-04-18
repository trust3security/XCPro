package com.trust3.xcpro.igc.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcSampleToBRecordMapperTest {

    private val mapper = IgcSampleToBRecordMapper()
    private val formatter = IgcRecordFormatter()

    @Test
    fun map_validSample_emitsARecord_andUpdatesState() {
        val result = mapper.map(
            sample = sample(
                sampleWallTimeMs = 1_000L,
                gpsWallTimeMs = 1_000L,
                latitudeDegrees = -33.865,
                longitudeDegrees = 151.209,
                pressureAltitudeMeters = 850.0,
                gnssAltitudeMeters = 900.0,
                indicatedAirspeedMs = 20.0,
                trueAirspeedMs = 22.0
            ),
            state = IgcSamplingState()
        )

        val bRecord = requireNotNull(result.bRecord)
        assertEquals(IgcRecordFormatter.FixValidity.A, bRecord.fixValidity)
        assertEquals(-33.865, bRecord.latitudeDegrees, 0.000001)
        assertEquals(151.209, bRecord.longitudeDegrees, 0.000001)
        assertNotNull(result.nextState.lastValidPosition)
        assertEquals(850, result.nextState.lastValidPressureAltitudeMeters)

        val line = formatter.formatB(
            record = bRecord,
            definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
        )
        assertEquals('A', line[24])
        assertEquals(41, line.length)
    }

    @Test
    fun map_missingPositionWithoutFallback_skipsEmission() {
        val result = mapper.map(
            sample = sample(
                sampleWallTimeMs = 2_000L,
                gpsWallTimeMs = 2_000L,
                latitudeDegrees = null,
                longitudeDegrees = null
            ),
            state = IgcSamplingState()
        )

        assertNull(result.bRecord)
        assertTrue(result.validationReasons.contains(IgcBRecordValidationPolicy.Reason.POSITION_MISSING))
    }

    @Test
    fun map_missingPositionWithFallback_reusesLastKnown_andMarksV() {
        val initialState = IgcSamplingState(
            lastValidPosition = IgcSamplingState.Position(
                latitudeDegrees = -33.800,
                longitudeDegrees = 151.100
            ),
            lastValidPressureAltitudeMeters = 700
        )
        val result = mapper.map(
            sample = sample(
                sampleWallTimeMs = 3_000L,
                gpsWallTimeMs = 3_000L,
                latitudeDegrees = null,
                longitudeDegrees = null,
                pressureAltitudeMeters = null,
                gnssAltitudeMeters = null
            ),
            state = initialState
        )

        val bRecord = requireNotNull(result.bRecord)
        assertEquals(IgcRecordFormatter.FixValidity.V, bRecord.fixValidity)
        assertEquals(-33.800, bRecord.latitudeDegrees, 0.0)
        assertEquals(151.100, bRecord.longitudeDegrees, 0.0)
        assertEquals(700, bRecord.pressureAltitudeMeters)
        assertEquals(0, bRecord.gnssAltitudeMeters)
    }

    @Test
    fun map_gnssAltitudeMissing_emitsZeroGnss_andMarksV() {
        val result = mapper.map(
            sample = sample(
                sampleWallTimeMs = 4_000L,
                gpsWallTimeMs = 4_000L,
                gnssAltitudeMeters = null
            ),
            state = IgcSamplingState()
        )

        val bRecord = requireNotNull(result.bRecord)
        assertEquals(IgcRecordFormatter.FixValidity.V, bRecord.fixValidity)
        assertEquals(0, bRecord.gnssAltitudeMeters)
        assertTrue(
            result.validationReasons.contains(IgcBRecordValidationPolicy.Reason.GNSS_ALTITUDE_MISSING)
        )
    }

    @Test
    fun map_longFlightSimulation_threeHoursEquivalent_emitsDeterministicSequence() {
        val cadencePolicy = IgcBRecordCadencePolicy(
            config = IgcBRecordCadencePolicy.Config(intervalSeconds = 1)
        )
        val startWallTimeMs = Instant.parse("2026-03-09T01:00:00Z").toEpochMilli()
        var state = IgcSamplingState()
        var emitted = 0
        var previousLine: String? = null

        repeat(10_800) { index ->
            val wallTimeMs = startWallTimeMs + index * 1_000L
            val dropPosition = index % 900 == 0 && index > 0
            val liveSample = if (dropPosition) {
                sample(
                    sampleWallTimeMs = wallTimeMs,
                    gpsWallTimeMs = wallTimeMs,
                    latitudeDegrees = null,
                    longitudeDegrees = null,
                    gnssAltitudeMeters = null
                )
            } else {
                sample(
                    sampleWallTimeMs = wallTimeMs,
                    gpsWallTimeMs = wallTimeMs,
                    latitudeDegrees = -33.900 + index * 0.00001,
                    longitudeDegrees = 151.200 + index * 0.00001
                )
            }
            if (!cadencePolicy.shouldEmit(wallTimeMs, state.lastEmissionWallTimeMs)) {
                return@repeat
            }

            val mapped = mapper.map(liveSample, state)
            state = mapped.nextState.copy(lastEmissionWallTimeMs = wallTimeMs)
            val bRecord = mapped.bRecord ?: return@repeat
            val line = formatter.formatB(
                record = bRecord,
                definitions = IgcRecordFormatter.IAS_TAS_EXTENSIONS
            )
            if (previousLine != null) {
                assertTrue(line.substring(1, 7) >= previousLine!!.substring(1, 7))
            }
            previousLine = line
            emitted += 1
        }

        assertEquals(10_800, emitted)
    }

    @Test
    fun map_malformedSample_rejectedByErrorPolicy() {
        val result = mapper.map(
            sample = sample(
                sampleWallTimeMs = 5_000L,
                gpsWallTimeMs = 5_000L,
                latitudeDegrees = Double.NaN
            ),
            state = IgcSamplingState()
        )

        assertNull(result.bRecord)
        assertTrue(
            result.rejectedErrors.contains(IgcSampleStreamErrorPolicy.Error.NON_FINITE_LATITUDE)
        )
    }

    private fun sample(
        sampleWallTimeMs: Long,
        gpsWallTimeMs: Long?,
        latitudeDegrees: Double? = -33.865,
        longitudeDegrees: Double? = 151.209,
        pressureAltitudeMeters: Double? = 850.0,
        gnssAltitudeMeters: Double? = 900.0,
        indicatedAirspeedMs: Double? = 20.0,
        trueAirspeedMs: Double? = 22.0
    ): IgcLiveSample = IgcLiveSample(
        sampleWallTimeMs = sampleWallTimeMs,
        gpsWallTimeMs = gpsWallTimeMs,
        baroWallTimeMs = sampleWallTimeMs,
        latitudeDegrees = latitudeDegrees,
        longitudeDegrees = longitudeDegrees,
        horizontalAccuracyMeters = 5.0,
        pressureAltitudeMeters = pressureAltitudeMeters,
        gnssAltitudeMeters = gnssAltitudeMeters,
        indicatedAirspeedMs = indicatedAirspeedMs,
        trueAirspeedMs = trueAirspeedMs
    )
}
