package com.trust3.xcpro.igc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcBRecordValidationPolicyTest {

    private val policy = IgcBRecordValidationPolicy(
        config = IgcBRecordValidationPolicy.Config(
            positionStaleMs = 5_000L,
            altitudeStaleMs = 8_000L,
            maxHorizontalAccuracyMeters = 30.0
        )
    )

    @Test
    fun evaluate_freshAndAccurateSample_isA() {
        val outcome = policy.evaluate(
            sample = sample(sampleWallTimeMs = 10_000L, gpsWallTimeMs = 9_000L, accuracyMeters = 5.0),
            lastValidPosition = null,
            lastValidPressureAltitudeMeters = null
        )

        assertTrue(outcome.canEmit)
        assertEquals(IgcRecordFormatter.FixValidity.A, outcome.fixValidity)
        assertEquals(IgcBRecordValidationPolicy.PositionSource.CURRENT, outcome.positionSource)
    }

    @Test
    fun evaluate_lowAccuracySample_usesFallbackAndMarksV() {
        val outcome = policy.evaluate(
            sample = sample(sampleWallTimeMs = 10_000L, gpsWallTimeMs = 9_000L, accuracyMeters = 100.0),
            lastValidPosition = IgcSamplingState.Position(-33.8, 151.1),
            lastValidPressureAltitudeMeters = 700
        )

        assertTrue(outcome.canEmit)
        assertEquals(IgcRecordFormatter.FixValidity.V, outcome.fixValidity)
        assertEquals(
            IgcBRecordValidationPolicy.PositionSource.LAST_VALID_FALLBACK,
            outcome.positionSource
        )
        assertTrue(outcome.reasons.contains(IgcBRecordValidationPolicy.Reason.POSITION_LOW_ACCURACY))
    }

    @Test
    fun evaluate_stalePositionWithoutFallback_cannotEmit() {
        val outcome = policy.evaluate(
            sample = sample(sampleWallTimeMs = 10_000L, gpsWallTimeMs = 1_000L, accuracyMeters = 5.0),
            lastValidPosition = null,
            lastValidPressureAltitudeMeters = null
        )

        assertFalse(outcome.canEmit)
        assertEquals(IgcBRecordValidationPolicy.PositionSource.NONE, outcome.positionSource)
        assertTrue(outcome.reasons.contains(IgcBRecordValidationPolicy.Reason.POSITION_STALE))
    }

    @Test
    fun evaluate_missingGnssAltitude_setsZeroAndMarksV() {
        val outcome = policy.evaluate(
            sample = sample(sampleWallTimeMs = 10_000L, gpsWallTimeMs = 9_000L, gnssAltitudeMeters = null),
            lastValidPosition = null,
            lastValidPressureAltitudeMeters = null
        )

        assertTrue(outcome.canEmit)
        assertEquals(IgcRecordFormatter.FixValidity.V, outcome.fixValidity)
        assertEquals(0, outcome.gnssAltitudeMeters)
        assertEquals(IgcBRecordValidationPolicy.GnssAltitudeSource.ZERO_FALLBACK, outcome.gnssAltitudeSource)
    }

    @Test
    fun evaluate_missingPressureAltitude_usesLastPressureFallback() {
        val outcome = policy.evaluate(
            sample = sample(sampleWallTimeMs = 10_000L, gpsWallTimeMs = 9_000L, pressureAltitudeMeters = null),
            lastValidPosition = IgcSamplingState.Position(-33.9, 151.2),
            lastValidPressureAltitudeMeters = 1_234
        )

        assertTrue(outcome.canEmit)
        assertEquals(1_234, outcome.pressureAltitudeMeters)
        assertEquals(
            IgcBRecordValidationPolicy.PressureAltitudeSource.LAST_VALID_FALLBACK,
            outcome.pressureAltitudeSource
        )
        assertEquals(IgcRecordFormatter.FixValidity.V, outcome.fixValidity)
    }

    private fun sample(
        sampleWallTimeMs: Long,
        gpsWallTimeMs: Long,
        accuracyMeters: Double = 5.0,
        pressureAltitudeMeters: Double? = 900.0,
        gnssAltitudeMeters: Double? = 950.0
    ): IgcLiveSample = IgcLiveSample(
        sampleWallTimeMs = sampleWallTimeMs,
        gpsWallTimeMs = gpsWallTimeMs,
        baroWallTimeMs = sampleWallTimeMs,
        latitudeDegrees = -33.9,
        longitudeDegrees = 151.2,
        horizontalAccuracyMeters = accuracyMeters,
        pressureAltitudeMeters = pressureAltitudeMeters,
        gnssAltitudeMeters = gnssAltitudeMeters,
        indicatedAirspeedMs = 20.0,
        trueAirspeedMs = 21.0
    )
}
