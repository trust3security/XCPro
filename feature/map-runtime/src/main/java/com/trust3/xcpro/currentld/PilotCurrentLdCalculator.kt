package com.trust3.xcpro.currentld

import com.trust3.xcpro.glider.StillAirSinkProvider
import com.trust3.xcpro.navigation.WaypointNavigationSnapshot
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.domain.FlightMetricsConstants
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.weather.wind.model.WindState
import com.trust3.xcpro.weather.wind.model.WindVector
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal data class PilotCurrentLdInput(
    val completeData: CompleteFlightData?,
    val windState: WindState,
    val flightState: FlyingState,
    val waypointNavigation: WaypointNavigationSnapshot
)

internal data class PilotCurrentLdState(
    val snapshot: PilotCurrentLdSnapshot = PilotCurrentLdSnapshot(),
    val lastProcessedSampleTimeMillis: Long = -1L,
    val windowSegments: List<PilotCurrentLdWindowSegment> = emptyList(),
    val lastIntegrationSample: PilotCurrentLdIntegrationSample? = null,
    val trackSamples: List<PilotCurrentLdTrackSample> = emptyList(),
    val lastValidDirectionDeg: Double? = null,
    val lastValidSnapshot: PilotCurrentLdSnapshot = PilotCurrentLdSnapshot(),
    val lastValidAtMillis: Long = 0L,
    val teGapStartMillis: Long? = null
)

internal data class PilotCurrentLdTrackSample(
    val timestampMillis: Long,
    val bearingDeg: Double
)

internal data class PilotCurrentLdIntegrationSample(
    val timestampMillis: Long,
    val effectiveForwardSpeedMs: Double,
    val sinkMs: Double
)

internal data class PilotCurrentLdWindowSegment(
    val endTimeMillis: Long,
    val durationMs: Long,
    val effectiveDistanceMeters: Double,
    val heightLostMeters: Double
)

internal class PilotCurrentLdCalculator(
    private val sinkProvider: StillAirSinkProvider
) {

    fun update(previous: PilotCurrentLdState, input: PilotCurrentLdInput): PilotCurrentLdState {
        val completeData = input.completeData ?: return previous
        val sampleTimeMillis = resolveSampleTimeMillis(completeData)
        if (sampleTimeMillis < 0L || sampleTimeMillis <= previous.lastProcessedSampleTimeMillis) {
            return previous
        }

        val straightContext = input.flightState.isFlying && !completeData.isCircling && !completeData.isTurning
        val trackSamples = updateTrackSamples(
            previous = previous.trackSamples,
            gps = completeData.gps,
            straightContext = straightContext,
            sampleTimeMillis = sampleTimeMillis
        )
        val smoothedTrackDeg = circularMeanDeg(trackSamples)
        val preferredDirectionDeg = selectGlideDirectionDeg(
            waypointNavigation = input.waypointNavigation,
            smoothedTrackDeg = smoothedTrackDeg,
            fallbackDirectionDeg = previous.lastValidDirectionDeg
        )
        val updatedDirectionDeg = if (straightContext && preferredDirectionDeg != null) {
            preferredDirectionDeg
        } else {
            previous.lastValidDirectionDeg
        }

        val airspeedCandidate = resolveAirspeedCandidate(
            completeData = completeData,
            flightState = input.flightState,
            straightContext = straightContext
        )
        val windUsable = isWindUsable(input.windState)
        val windAlongMs = if (windUsable && preferredDirectionDeg != null) {
            projectWindAlongMs(input.windState.vector, preferredDirectionDeg)
        } else {
            0.0
        }

        val measuredSinkMs = completeData.teVario?.value
            ?.takeIf { it.isFinite() }
            ?.let { -it }
            ?.takeIf { it > EPSILON_SINK_MS }
        val nonGlideTeState = completeData.teVario?.value
            ?.takeIf { it.isFinite() }
            ?.let { (-it) <= EPSILON_SINK_MS } == true

        val teMissing = completeData.teVario?.value?.isFinite() != true
        val nextTeGapStartMillis = when {
            measuredSinkMs != null -> null
            airspeedCandidate != null && teMissing -> previous.teGapStartMillis ?: sampleTimeMillis
            else -> null
        }

        val freshSourceCandidate = when {
            airspeedCandidate != null && measuredSinkMs != null -> {
                PilotCurrentLdSourceCandidate(
                    source = if (windUsable && preferredDirectionDeg != null) {
                        PilotCurrentLdSource.FUSED_WIND
                    } else {
                        PilotCurrentLdSource.FUSED_ZERO_WIND
                    },
                    effectiveForwardSpeedMs = airspeedCandidate.trueAirspeedMs + windAlongMs,
                    sinkMs = measuredSinkMs
                )
            }

            airspeedCandidate != null && teMissing && nextTeGapStartMillis != null &&
                sampleTimeMillis - nextTeGapStartMillis <= POLAR_FILL_MAX_GAP_MS -> {
                val polarSinkMs = sinkProvider.sinkAtSpeed(airspeedCandidate.indicatedAirspeedMs)
                    ?.takeIf { it.isFinite() && it > EPSILON_SINK_MS }
                polarSinkMs?.let {
                    PilotCurrentLdSourceCandidate(
                        source = PilotCurrentLdSource.POLAR_FILL,
                        effectiveForwardSpeedMs = airspeedCandidate.trueAirspeedMs + windAlongMs,
                        sinkMs = it
                    )
                }
            }

            else -> null
        }

        if (freshSourceCandidate != null) {
            val integrationSample = PilotCurrentLdIntegrationSample(
                timestampMillis = sampleTimeMillis,
                effectiveForwardSpeedMs = freshSourceCandidate.effectiveForwardSpeedMs,
                sinkMs = freshSourceCandidate.sinkMs
            )
            val windowSegments = updateWindowSegments(
                previousSegments = previous.windowSegments,
                previousSample = previous.lastIntegrationSample,
                currentSample = integrationSample
            )
            val filledDurationMs = windowSegments.sumOf { it.durationMs }
            val maybeFreshSnapshot = if (filledDurationMs >= MIN_PUBLISH_FILL_MS) {
                buildSnapshotFromWindow(
                    source = freshSourceCandidate.source,
                    windowSegments = windowSegments,
                    indicatedAirspeedMs = airspeedCandidate?.indicatedAirspeedMs,
                    completeData = completeData
                )
            } else {
                null
            }

            if (maybeFreshSnapshot != null) {
                return previous.copy(
                    snapshot = maybeFreshSnapshot,
                    lastProcessedSampleTimeMillis = sampleTimeMillis,
                    windowSegments = windowSegments,
                    lastIntegrationSample = integrationSample,
                    trackSamples = trackSamples,
                    lastValidDirectionDeg = updatedDirectionDeg,
                    lastValidSnapshot = maybeFreshSnapshot,
                    lastValidAtMillis = sampleTimeMillis,
                    teGapStartMillis = nextTeGapStartMillis
                )
            }

            return previous.copy(
                snapshot = heldOrNoneSnapshot(previous, sampleTimeMillis),
                lastProcessedSampleTimeMillis = sampleTimeMillis,
                windowSegments = windowSegments,
                lastIntegrationSample = integrationSample,
                trackSamples = trackSamples,
                lastValidDirectionDeg = updatedDirectionDeg,
                teGapStartMillis = nextTeGapStartMillis
            )
        }

        val groundFallbackSnapshot = if (
            straightContext &&
            !nonGlideTeState &&
            completeData.currentLDValid &&
            isPlausible(
                value = completeData.currentLD.toDouble(),
                indicatedAirspeedMs = completeData.indicatedAirspeed.value
            )
        ) {
            PilotCurrentLdSnapshot(
                pilotCurrentLD = completeData.currentLD,
                pilotCurrentLDValid = true,
                pilotCurrentLDSource = PilotCurrentLdSource.GROUND_FALLBACK
            )
        } else {
            null
        }

        if (groundFallbackSnapshot != null) {
            return previous.copy(
                snapshot = groundFallbackSnapshot,
                lastProcessedSampleTimeMillis = sampleTimeMillis,
                windowSegments = emptyList(),
                lastIntegrationSample = null,
                trackSamples = trackSamples,
                lastValidDirectionDeg = updatedDirectionDeg,
                lastValidSnapshot = groundFallbackSnapshot,
                lastValidAtMillis = sampleTimeMillis,
                teGapStartMillis = null
            )
        }

        return previous.copy(
            snapshot = heldOrNoneSnapshot(previous, sampleTimeMillis),
            lastProcessedSampleTimeMillis = sampleTimeMillis,
            windowSegments = emptyList(),
            lastIntegrationSample = null,
            trackSamples = trackSamples,
            lastValidDirectionDeg = updatedDirectionDeg,
            teGapStartMillis = null
        )
    }

    private fun resolveSampleTimeMillis(completeData: CompleteFlightData): Long {
        return completeData.gps?.timeForCalculationsMillis?.takeIf { it > 0L }
            ?: completeData.timestamp
    }

    private fun updateTrackSamples(
        previous: List<PilotCurrentLdTrackSample>,
        gps: GPSData?,
        straightContext: Boolean,
        sampleTimeMillis: Long
    ): List<PilotCurrentLdTrackSample> {
        val kept = previous
            .filter { sample -> sample.timestampMillis > sampleTimeMillis - TRACK_WINDOW_MS }
            .takeLast(MAX_TRACK_SAMPLES)
            .toMutableList()
        if (straightContext && gps?.isMoving == true && gps.bearing.isFinite()) {
            kept += PilotCurrentLdTrackSample(
                timestampMillis = sampleTimeMillis,
                bearingDeg = normalizeDegrees(gps.bearing)
            )
        }
        return kept.takeLast(MAX_TRACK_SAMPLES)
    }

    private fun selectGlideDirectionDeg(
        waypointNavigation: WaypointNavigationSnapshot,
        smoothedTrackDeg: Double?,
        fallbackDirectionDeg: Double?
    ): Double? {
        val routeBearing = waypointNavigation.bearingTrueDegrees
            .takeIf { waypointNavigation.valid && it.isFinite() }
        return routeBearing?.let(::normalizeDegrees)
            ?: smoothedTrackDeg
            ?: fallbackDirectionDeg
    }

    private fun resolveAirspeedCandidate(
        completeData: CompleteFlightData,
        flightState: FlyingState,
        straightContext: Boolean
    ): PilotCurrentLdAirspeedCandidate? {
        if (!flightState.isFlying || !straightContext) return null
        if (!completeData.tasValid || completeData.airspeedSource.equals(GPS_SOURCE_LABEL, ignoreCase = true)) {
            return null
        }
        val trueAirspeedMs = completeData.trueAirspeed.value
            .takeIf { it.isFinite() && it > MIN_TRUE_AIRSPEED_MS }
            ?: return null
        val indicatedAirspeedMs = completeData.indicatedAirspeed.value
            .takeIf { it.isFinite() && it > MIN_INDICATED_AIRSPEED_MS }
            ?: return null
        return PilotCurrentLdAirspeedCandidate(
            trueAirspeedMs = trueAirspeedMs,
            indicatedAirspeedMs = indicatedAirspeedMs
        )
    }

    private fun isWindUsable(windState: WindState): Boolean {
        return windState.vector != null &&
            windState.isAvailable &&
            !windState.stale &&
            windState.confidence >= FlightMetricsConstants.WIND_AIRSPEED_ENTER_CONF_MIN
    }

    private fun projectWindAlongMs(windVector: WindVector?, glideDirectionDeg: Double): Double {
        windVector ?: return 0.0
        val directionRad = Math.toRadians(normalizeDegrees(glideDirectionDeg))
        val east = sin(directionRad)
        val north = cos(directionRad)
        return (windVector.east * east) + (windVector.north * north)
    }

    private fun updateWindowSegments(
        previousSegments: List<PilotCurrentLdWindowSegment>,
        previousSample: PilotCurrentLdIntegrationSample?,
        currentSample: PilotCurrentLdIntegrationSample
    ): List<PilotCurrentLdWindowSegment> {
        val updated = previousSegments.toMutableList()
        if (previousSample != null) {
            val durationMs = currentSample.timestampMillis - previousSample.timestampMillis
            if (durationMs > 0L) {
                val durationSeconds = durationMs / 1_000.0
                val distanceMeters = average(previousSample.effectiveForwardSpeedMs, currentSample.effectiveForwardSpeedMs) *
                    durationSeconds
                val heightLostMeters = average(previousSample.sinkMs, currentSample.sinkMs) * durationSeconds
                updated += PilotCurrentLdWindowSegment(
                    endTimeMillis = currentSample.timestampMillis,
                    durationMs = durationMs,
                    effectiveDistanceMeters = distanceMeters,
                    heightLostMeters = heightLostMeters
                )
            }
        }
        return trimWindowSegments(updated, currentSample.timestampMillis)
    }

    private fun trimWindowSegments(
        segments: List<PilotCurrentLdWindowSegment>,
        currentTimeMillis: Long
    ): List<PilotCurrentLdWindowSegment> {
        val thresholdMillis = currentTimeMillis - WINDOW_DURATION_MS
        return buildList {
            segments.forEach { segment ->
                val segmentStart = segment.endTimeMillis - segment.durationMs
                if (segment.endTimeMillis <= thresholdMillis) {
                    return@forEach
                }
                if (segmentStart < thresholdMillis) {
                    val keptDurationMs = segment.endTimeMillis - thresholdMillis
                    val ratio = keptDurationMs.toDouble() / segment.durationMs.toDouble()
                    add(
                        segment.copy(
                            durationMs = keptDurationMs,
                            effectiveDistanceMeters = segment.effectiveDistanceMeters * ratio,
                            heightLostMeters = segment.heightLostMeters * ratio
                        )
                    )
                } else {
                    add(segment)
                }
            }
        }
    }

    private fun buildSnapshotFromWindow(
        source: PilotCurrentLdSource,
        windowSegments: List<PilotCurrentLdWindowSegment>,
        indicatedAirspeedMs: Double?,
        completeData: CompleteFlightData
    ): PilotCurrentLdSnapshot? {
        val effectiveDistanceMeters = windowSegments.sumOf { it.effectiveDistanceMeters }
        val heightLostMeters = windowSegments.sumOf { it.heightLostMeters }
        if (!effectiveDistanceMeters.isFinite() || !heightLostMeters.isFinite()) return null
        if (effectiveDistanceMeters <= 0.0 || heightLostMeters <= 0.0) return null
        val value = effectiveDistanceMeters / heightLostMeters
        if (!isPlausible(value, indicatedAirspeedMs)) return null
        return PilotCurrentLdSnapshot(
            pilotCurrentLD = value.toFloat(),
            pilotCurrentLDValid = true,
            pilotCurrentLDSource = source
        )
    }

    private fun isPlausible(value: Double, indicatedAirspeedMs: Double?): Boolean {
        if (!value.isFinite() || value < MIN_PLAUSIBLE_LD) return false
        val upper = buildPlausibilityUpperBound(indicatedAirspeedMs)
        return value <= upper
    }

    private fun buildPlausibilityUpperBound(indicatedAirspeedMs: Double?): Double {
        val currentLdUpper = indicatedAirspeedMs?.let { ias ->
            sinkProvider.ldAtSpeed(ias)
        }?.takeIf { it.isFinite() && it > 0.0 }
            ?.times(CURRENT_SPEED_LD_MULTIPLIER)
            ?: 0.0
        val bestLdUpper = sinkProvider.bestLd()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.times(BEST_LD_MULTIPLIER)
            ?: 0.0
        return max(max(currentLdUpper, bestLdUpper), ABSOLUTE_MAX_LD)
    }

    private fun heldOrNoneSnapshot(previous: PilotCurrentLdState, sampleTimeMillis: Long): PilotCurrentLdSnapshot {
        val lastValidAtMillis = previous.lastValidAtMillis
        if (lastValidAtMillis > 0L && sampleTimeMillis - lastValidAtMillis <= HOLD_TIMEOUT_MS) {
            return previous.lastValidSnapshot.copy(
                pilotCurrentLDSource = PilotCurrentLdSource.HELD
            )
        }
        return PilotCurrentLdSnapshot()
    }

    private fun circularMeanDeg(samples: List<PilotCurrentLdTrackSample>): Double? {
        if (samples.isEmpty()) return null
        var east = 0.0
        var north = 0.0
        samples.forEach { sample ->
            val rad = Math.toRadians(normalizeDegrees(sample.bearingDeg))
            east += sin(rad)
            north += cos(rad)
        }
        if (east == 0.0 && north == 0.0) return null
        return normalizeDegrees(Math.toDegrees(kotlin.math.atan2(east, north)))
    }

    private fun normalizeDegrees(value: Double): Double {
        var normalized = value % 360.0
        if (normalized < 0.0) normalized += 360.0
        return normalized
    }

    private fun average(first: Double, second: Double): Double = (first + second) / 2.0

    private companion object {
        const val WINDOW_DURATION_MS = 20_000L
        const val MIN_PUBLISH_FILL_MS = 8_000L
        const val HOLD_TIMEOUT_MS = 20_000L
        const val POLAR_FILL_MAX_GAP_MS = 3_000L
        const val TRACK_WINDOW_MS = 5_000L
        const val MAX_TRACK_SAMPLES = 5
        const val MIN_TRUE_AIRSPEED_MS = 5.0
        const val MIN_INDICATED_AIRSPEED_MS = 0.1
        const val EPSILON_SINK_MS = 0.15
        const val MIN_PLAUSIBLE_LD = 3.0
        const val ABSOLUTE_MAX_LD = 120.0
        const val CURRENT_SPEED_LD_MULTIPLIER = 2.5
        const val BEST_LD_MULTIPLIER = 2.0
        const val GPS_SOURCE_LABEL = "GPS"
    }
}

private data class PilotCurrentLdAirspeedCandidate(
    val trueAirspeedMs: Double,
    val indicatedAirspeedMs: Double
)

private data class PilotCurrentLdSourceCandidate(
    val source: PilotCurrentLdSource,
    val effectiveForwardSpeedMs: Double,
    val sinkMs: Double
)
