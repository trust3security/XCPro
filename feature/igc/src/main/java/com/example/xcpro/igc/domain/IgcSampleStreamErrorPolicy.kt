package com.example.xcpro.igc.domain

/**
 * Rejects malformed live samples before mapping to B records.
 */
class IgcSampleStreamErrorPolicy {

    enum class Error {
        NON_POSITIVE_SAMPLE_TIMESTAMP,
        NON_POSITIVE_GPS_TIMESTAMP,
        NON_POSITIVE_BARO_TIMESTAMP,
        NON_FINITE_LATITUDE,
        NON_FINITE_LONGITUDE,
        LATITUDE_OUT_OF_RANGE,
        LONGITUDE_OUT_OF_RANGE,
        NON_FINITE_HORIZONTAL_ACCURACY,
        NEGATIVE_HORIZONTAL_ACCURACY,
        NON_FINITE_PRESSURE_ALTITUDE,
        NON_FINITE_GNSS_ALTITUDE,
        NON_FINITE_IAS,
        NON_FINITE_TAS
    }

    data class ValidationResult(
        val isAccepted: Boolean,
        val errors: Set<Error>
    )

    fun validate(sample: IgcLiveSample): ValidationResult {
        val errors = linkedSetOf<Error>()

        if (sample.sampleWallTimeMs <= 0L) {
            errors += Error.NON_POSITIVE_SAMPLE_TIMESTAMP
        }
        sample.gpsWallTimeMs?.let { gpsWallTimeMs ->
            if (gpsWallTimeMs <= 0L) {
                errors += Error.NON_POSITIVE_GPS_TIMESTAMP
            }
        }
        sample.baroWallTimeMs?.let { baroWallTimeMs ->
            if (baroWallTimeMs <= 0L) {
                errors += Error.NON_POSITIVE_BARO_TIMESTAMP
            }
        }
        sample.latitudeDegrees?.let { latitude ->
            if (!latitude.isFinite()) {
                errors += Error.NON_FINITE_LATITUDE
            } else if (latitude !in -90.0..90.0) {
                errors += Error.LATITUDE_OUT_OF_RANGE
            }
        }
        sample.longitudeDegrees?.let { longitude ->
            if (!longitude.isFinite()) {
                errors += Error.NON_FINITE_LONGITUDE
            } else if (longitude !in -180.0..180.0) {
                errors += Error.LONGITUDE_OUT_OF_RANGE
            }
        }
        sample.horizontalAccuracyMeters?.let { accuracy ->
            if (!accuracy.isFinite()) {
                errors += Error.NON_FINITE_HORIZONTAL_ACCURACY
            } else if (accuracy < 0.0) {
                errors += Error.NEGATIVE_HORIZONTAL_ACCURACY
            }
        }
        sample.pressureAltitudeMeters?.let { altitude ->
            if (!altitude.isFinite()) {
                errors += Error.NON_FINITE_PRESSURE_ALTITUDE
            }
        }
        sample.gnssAltitudeMeters?.let { altitude ->
            if (!altitude.isFinite()) {
                errors += Error.NON_FINITE_GNSS_ALTITUDE
            }
        }
        sample.indicatedAirspeedMs?.let { iasMs ->
            if (!iasMs.isFinite()) {
                errors += Error.NON_FINITE_IAS
            }
        }
        sample.trueAirspeedMs?.let { tasMs ->
            if (!tasMs.isFinite()) {
                errors += Error.NON_FINITE_TAS
            }
        }

        return ValidationResult(
            isAccepted = errors.isEmpty(),
            errors = errors
        )
    }
}
