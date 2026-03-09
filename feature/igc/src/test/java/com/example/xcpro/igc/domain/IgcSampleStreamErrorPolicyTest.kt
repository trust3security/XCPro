package com.example.xcpro.igc.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcSampleStreamErrorPolicyTest {

    private val policy = IgcSampleStreamErrorPolicy()

    @Test
    fun validate_acceptsWellFormedSample() {
        val result = policy.validate(
            sample(
                latitudeDegrees = -33.865,
                longitudeDegrees = 151.209,
                horizontalAccuracyMeters = 8.0
            )
        )

        assertTrue(result.isAccepted)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validate_rejectsMalformedCoordinatesAndAccuracy() {
        val result = policy.validate(
            sample(
                latitudeDegrees = 95.0,
                longitudeDegrees = Double.NaN,
                horizontalAccuracyMeters = -1.0
            )
        )

        assertFalse(result.isAccepted)
        assertTrue(result.errors.contains(IgcSampleStreamErrorPolicy.Error.LATITUDE_OUT_OF_RANGE))
        assertTrue(result.errors.contains(IgcSampleStreamErrorPolicy.Error.NON_FINITE_LONGITUDE))
        assertTrue(result.errors.contains(IgcSampleStreamErrorPolicy.Error.NEGATIVE_HORIZONTAL_ACCURACY))
    }

    @Test
    fun validate_rejectsNonPositiveSampleTimestamp() {
        val result = policy.validate(
            sample(sampleWallTimeMs = 0L)
        )

        assertFalse(result.isAccepted)
        assertTrue(result.errors.contains(IgcSampleStreamErrorPolicy.Error.NON_POSITIVE_SAMPLE_TIMESTAMP))
    }

    private fun sample(
        sampleWallTimeMs: Long = 1_000L,
        latitudeDegrees: Double? = -33.9,
        longitudeDegrees: Double? = 151.2,
        horizontalAccuracyMeters: Double? = 5.0
    ): IgcLiveSample = IgcLiveSample(
        sampleWallTimeMs = sampleWallTimeMs,
        gpsWallTimeMs = sampleWallTimeMs,
        baroWallTimeMs = sampleWallTimeMs,
        latitudeDegrees = latitudeDegrees,
        longitudeDegrees = longitudeDegrees,
        horizontalAccuracyMeters = horizontalAccuracyMeters,
        pressureAltitudeMeters = 500.0,
        gnssAltitudeMeters = 510.0,
        indicatedAirspeedMs = 25.0,
        trueAirspeedMs = 26.0
    )
}
