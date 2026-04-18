package com.trust3.xcpro.igc.domain

import com.trust3.xcpro.igc.domain.IgcSamplingState.Position
import kotlin.math.roundToInt

/**
 * Applies fix validity and fallback policy used for B-record generation.
 */
class IgcBRecordValidationPolicy(
    private val config: Config = Config()
) {

    data class Config(
        val positionStaleMs: Long = 5_000L,
        val altitudeStaleMs: Long = 8_000L,
        val maxHorizontalAccuracyMeters: Double = 50.0
    ) {
        init {
            require(positionStaleMs > 0L) { "positionStaleMs must be > 0" }
            require(altitudeStaleMs > 0L) { "altitudeStaleMs must be > 0" }
            require(maxHorizontalAccuracyMeters > 0.0) {
                "maxHorizontalAccuracyMeters must be > 0"
            }
        }
    }

    enum class Reason {
        POSITION_MISSING,
        POSITION_STALE,
        POSITION_LOW_ACCURACY,
        POSITION_FALLBACK_LAST_VALID,
        GNSS_ALTITUDE_MISSING,
        GNSS_ALTITUDE_STALE,
        PRESSURE_ALTITUDE_MISSING,
        PRESSURE_ALTITUDE_STALE
    }

    enum class PositionSource {
        CURRENT,
        LAST_VALID_FALLBACK,
        NONE
    }

    enum class PressureAltitudeSource {
        CURRENT,
        LAST_VALID_FALLBACK,
        ZERO_FALLBACK
    }

    enum class GnssAltitudeSource {
        CURRENT,
        ZERO_FALLBACK
    }

    data class Outcome(
        val canEmit: Boolean,
        val fixValidity: IgcRecordFormatter.FixValidity,
        val latitudeDegrees: Double?,
        val longitudeDegrees: Double?,
        val pressureAltitudeMeters: Int?,
        val gnssAltitudeMeters: Int?,
        val positionSource: PositionSource,
        val pressureAltitudeSource: PressureAltitudeSource,
        val gnssAltitudeSource: GnssAltitudeSource,
        val reasons: Set<Reason>
    )

    fun evaluate(
        sample: IgcLiveSample,
        lastValidPosition: Position?,
        lastValidPressureAltitudeMeters: Int?
    ): Outcome {
        val reasons = linkedSetOf<Reason>()

        val samplePosition = normalizedPosition(sample)
        val samplePositionAgeMs = sample.gpsWallTimeMs?.let { sample.sampleWallTimeMs - it }
        val isPositionFresh = samplePosition != null &&
            samplePositionAgeMs != null &&
            samplePositionAgeMs in 0..config.positionStaleMs
        val isPositionAccurate = sample.horizontalAccuracyMeters?.let {
            it <= config.maxHorizontalAccuracyMeters
        } ?: true

        val position: Position?
        val positionSource: PositionSource
        var validity = IgcRecordFormatter.FixValidity.A
        if (samplePosition != null && isPositionFresh && isPositionAccurate) {
            position = samplePosition
            positionSource = PositionSource.CURRENT
        } else {
            if (samplePosition == null) {
                reasons += Reason.POSITION_MISSING
            } else {
                if (!isPositionFresh) reasons += Reason.POSITION_STALE
                if (!isPositionAccurate) reasons += Reason.POSITION_LOW_ACCURACY
            }
            if (lastValidPosition != null) {
                position = lastValidPosition
                positionSource = PositionSource.LAST_VALID_FALLBACK
                reasons += Reason.POSITION_FALLBACK_LAST_VALID
                validity = IgcRecordFormatter.FixValidity.V
            } else {
                return Outcome(
                    canEmit = false,
                    fixValidity = IgcRecordFormatter.FixValidity.V,
                    latitudeDegrees = null,
                    longitudeDegrees = null,
                    pressureAltitudeMeters = null,
                    gnssAltitudeMeters = null,
                    positionSource = PositionSource.NONE,
                    pressureAltitudeSource = PressureAltitudeSource.ZERO_FALLBACK,
                    gnssAltitudeSource = GnssAltitudeSource.ZERO_FALLBACK,
                    reasons = reasons
                )
            }
        }

        val samplePressureAltitude = sample.pressureAltitudeMeters
            ?.takeIf { it.isFinite() }
            ?.roundToInt()
            ?.coerceIn(MIN_SIGNED_ALTITUDE, MAX_UNSIGNED_ALTITUDE)
        val samplePressureAgeMs = sample.baroWallTimeMs?.let { sample.sampleWallTimeMs - it }
        val pressureAltitudeFresh = samplePressureAgeMs == null || samplePressureAgeMs in 0..config.altitudeStaleMs
        val pressureAltitudeMeters: Int
        val pressureSource: PressureAltitudeSource
        if (samplePressureAltitude != null && pressureAltitudeFresh) {
            pressureAltitudeMeters = samplePressureAltitude
            pressureSource = PressureAltitudeSource.CURRENT
        } else if (lastValidPressureAltitudeMeters != null) {
            pressureAltitudeMeters = lastValidPressureAltitudeMeters
                .coerceIn(MIN_SIGNED_ALTITUDE, MAX_UNSIGNED_ALTITUDE)
            pressureSource = PressureAltitudeSource.LAST_VALID_FALLBACK
            validity = IgcRecordFormatter.FixValidity.V
            reasons += if (samplePressureAltitude == null) {
                Reason.PRESSURE_ALTITUDE_MISSING
            } else {
                Reason.PRESSURE_ALTITUDE_STALE
            }
        } else {
            pressureAltitudeMeters = 0
            pressureSource = PressureAltitudeSource.ZERO_FALLBACK
            validity = IgcRecordFormatter.FixValidity.V
            reasons += if (samplePressureAltitude == null) {
                Reason.PRESSURE_ALTITUDE_MISSING
            } else {
                Reason.PRESSURE_ALTITUDE_STALE
            }
        }

        val sampleGnssAltitude = sample.gnssAltitudeMeters
            ?.takeIf { it.isFinite() }
            ?.roundToInt()
            ?.coerceIn(MIN_SIGNED_ALTITUDE, MAX_UNSIGNED_ALTITUDE)
        val sampleGnssAgeMs = sample.gpsWallTimeMs?.let { sample.sampleWallTimeMs - it }
        val gnssAltitudeFresh = sampleGnssAgeMs != null && sampleGnssAgeMs in 0..config.altitudeStaleMs
        val gnssAltitudeMeters: Int
        val gnssSource: GnssAltitudeSource
        if (sampleGnssAltitude != null && gnssAltitudeFresh) {
            gnssAltitudeMeters = sampleGnssAltitude
            gnssSource = GnssAltitudeSource.CURRENT
        } else {
            gnssAltitudeMeters = 0
            gnssSource = GnssAltitudeSource.ZERO_FALLBACK
            validity = IgcRecordFormatter.FixValidity.V
            reasons += if (sampleGnssAltitude == null) {
                Reason.GNSS_ALTITUDE_MISSING
            } else {
                Reason.GNSS_ALTITUDE_STALE
            }
        }

        return Outcome(
            canEmit = true,
            fixValidity = validity,
            latitudeDegrees = position.latitudeDegrees,
            longitudeDegrees = position.longitudeDegrees,
            pressureAltitudeMeters = pressureAltitudeMeters,
            gnssAltitudeMeters = gnssAltitudeMeters,
            positionSource = positionSource,
            pressureAltitudeSource = pressureSource,
            gnssAltitudeSource = gnssSource,
            reasons = reasons
        )
    }

    private fun normalizedPosition(sample: IgcLiveSample): Position? {
        val latitude = sample.latitudeDegrees ?: return null
        val longitude = sample.longitudeDegrees ?: return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0) return null
        if (longitude !in -180.0..180.0) return null
        return Position(latitude, longitude)
    }

    private companion object {
        private const val MIN_SIGNED_ALTITUDE = -9_999
        private const val MAX_UNSIGNED_ALTITUDE = 99_999
    }
}
