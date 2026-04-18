package com.trust3.xcpro.igc.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * Converts validated live samples into deterministic IGC B-record domain objects.
 */
class IgcSampleToBRecordMapper(
    private val validationPolicy: IgcBRecordValidationPolicy = IgcBRecordValidationPolicy(),
    private val errorPolicy: IgcSampleStreamErrorPolicy = IgcSampleStreamErrorPolicy()
) {

    data class MappingResult(
        val bRecord: IgcRecordFormatter.BRecord?,
        val nextState: IgcSamplingState,
        val rejectedErrors: Set<IgcSampleStreamErrorPolicy.Error> = emptySet(),
        val validationReasons: Set<IgcBRecordValidationPolicy.Reason> = emptySet()
    )

    fun map(
        sample: IgcLiveSample,
        state: IgcSamplingState
    ): MappingResult {
        val validation = errorPolicy.validate(sample)
        if (!validation.isAccepted) {
            return MappingResult(
                bRecord = null,
                nextState = state,
                rejectedErrors = validation.errors
            )
        }
        if (state.lastEmissionWallTimeMs != null && sample.sampleWallTimeMs <= state.lastEmissionWallTimeMs) {
            return MappingResult(
                bRecord = null,
                nextState = state
            )
        }

        val outcome = validationPolicy.evaluate(
            sample = sample,
            lastValidPosition = state.lastValidPosition,
            lastValidPressureAltitudeMeters = state.lastValidPressureAltitudeMeters
        )
        if (!outcome.canEmit) {
            return MappingResult(
                bRecord = null,
                nextState = state,
                validationReasons = outcome.reasons
            )
        }

        val timeUtc = utcLocalTime(sample.sampleWallTimeMs)
        val latitude = requireNotNull(outcome.latitudeDegrees) { "latitude missing when canEmit=true" }
        val longitude = requireNotNull(outcome.longitudeDegrees) { "longitude missing when canEmit=true" }
        val extensionValues = buildExtensionValues(sample)

        val bRecord = IgcRecordFormatter.BRecord(
            timeUtc = timeUtc,
            latitudeDegrees = latitude,
            longitudeDegrees = longitude,
            fixValidity = outcome.fixValidity,
            pressureAltitudeMeters = outcome.pressureAltitudeMeters,
            gnssAltitudeMeters = outcome.gnssAltitudeMeters,
            extensionValues = extensionValues
        )

        val updatedState = state.copy(
            lastValidPosition = when (outcome.positionSource) {
                IgcBRecordValidationPolicy.PositionSource.CURRENT -> {
                    IgcSamplingState.Position(latitude, longitude)
                }
                else -> state.lastValidPosition
            },
            lastValidPressureAltitudeMeters = when (outcome.pressureAltitudeSource) {
                IgcBRecordValidationPolicy.PressureAltitudeSource.CURRENT -> outcome.pressureAltitudeMeters
                else -> state.lastValidPressureAltitudeMeters
            }
        )

        return MappingResult(
            bRecord = bRecord,
            nextState = updatedState,
            validationReasons = outcome.reasons
        )
    }

    private fun buildExtensionValues(sample: IgcLiveSample): Map<String, Int> {
        val values = linkedMapOf<String, Int>()
        sample.indicatedAirspeedMs
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { iasMs -> values["IAS"] = toKmhRounded(iasMs) }
        sample.trueAirspeedMs
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { tasMs -> values["TAS"] = toKmhRounded(tasMs) }
        return values
    }

    private fun toKmhRounded(speedMs: Double): Int = (speedMs * METERS_PER_SECOND_TO_KMH).roundToInt()

    private fun utcLocalTime(wallTimeMs: Long): LocalTime {
        return Instant.ofEpochMilli(wallTimeMs)
            .atOffset(ZoneOffset.UTC)
            .toLocalTime()
    }

    private companion object {
        private const val METERS_PER_SECOND_TO_KMH = 3.6
    }
}
