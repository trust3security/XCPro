package com.example.xcpro.replay

import android.util.Log
import com.example.xcpro.sensors.SensorFusionRepository
import com.example.xcpro.sensors.domain.FlightMetricsConstants
import com.example.xcpro.weather.wind.data.ReplayAirspeedRepository
import kotlin.math.pow
import kotlin.math.sqrt

internal class ReplaySampleEmitter(
    private val replaySensorSource: ReplaySensorSource,
    private val replayAirspeedRepository: ReplayAirspeedRepository,
    private val simConfig: ReplaySimConfig
) {
    private val noiseModel = ReplayNoiseModel(simConfig)
    private val headingResolver = ReplayHeadingResolver()
    private var lastGpsEmitTimestamp: Long = Long.MIN_VALUE
    private var lastGpsPoint: IgcPoint? = null
    private var lastResolvedHeadingDeg: Float? = null
    val random = noiseModel.random

    fun reset() {
        noiseModel.reset()
        headingResolver.reset()
        lastGpsEmitTimestamp = Long.MIN_VALUE
        lastGpsPoint = null
        lastResolvedHeadingDeg = null
        replayAirspeedRepository.reset()
    }

    fun emitSample(
        current: IgcPoint,
        previous: IgcPoint?,
        qnhHpa: Double,
        startTimestampMillis: Long,
        replayFusionRepository: SensorFusionRepository?,
        movementOverride: MovementSnapshot? = null
    ) {
        val movement = movementOverride ?: IgcReplayMath.groundVector(current, previous)
        val groundSpeed = movement.speedMs
        val fallbackTrackDeg = if (movementOverride != null) {
            movement.bearingDeg
        } else {
            headingResolver.resolve(movement)
        }
        val gpsAltitude = current.gpsAltitude

        val pressureAltitude = current.pressureAltitude ?: gpsAltitude
        val pressureHPa = IgcReplayMath.altitudeToPressure(pressureAltitude, qnhHpa)
        val pressureNoise = noiseModel.baroNoise(
            timestampMillis = current.timestampMillis,
            startTimestampMillis = startTimestampMillis
        )
        replaySensorSource.emitBaro(
            pressureHPa = pressureHPa + pressureNoise,
            timestamp = current.timestampMillis
        )

        if (shouldEmitGps(current.timestampMillis)) {
            val gpsMovement = movementOverride
                ?: IgcReplayMath.groundVector(current, lastGpsPoint ?: previous ?: current)
            val gpsTrackDeg = gpsMovement.bearingDeg
            val gpsSpeed = gpsMovement.speedMs
            val gpsNoise = noiseModel.gpsAltitudeNoise(
                timestampMillis = current.timestampMillis,
                startTimestampMillis = startTimestampMillis
            )
            replaySensorSource.emitGps(
                latitude = current.latitude,
                longitude = current.longitude,
                altitude = gpsAltitude + gpsNoise,
                speed = gpsSpeed,
                bearing = gpsTrackDeg.toDouble(),
                accuracy = simConfig.gpsAccuracyMeters,
                timestamp = current.timestampMillis
            )
            lastGpsEmitTimestamp = current.timestampMillis
            lastGpsPoint = current
            lastResolvedHeadingDeg = gpsTrackDeg
        }
        val headingDeg = (lastResolvedHeadingDeg ?: fallbackTrackDeg).toDouble()
        replaySensorSource.emitCompass(
            heading = headingDeg,
            accuracy = 3,
            timestamp = current.timestampMillis
        )
        emitAirspeedSample(current, qnhHpa)
        val igcVario = IgcReplayMath.verticalSpeed(current, previous)
        Log.d(
            TAG,
            "REPLAY_SAMPLE ts=${current.timestampMillis} " +
                "igcVario=${"%.3f".format(igcVario)} gpsAlt=${"%.1f".format(gpsAltitude)} " +
                "pressAlt=${"%.1f".format(pressureAltitude)} gs=${"%.2f".format(groundSpeed)} " +
                "track=${"%.1f".format(headingDeg)}"
        )
        if (simConfig.mode == ReplayMode.REFERENCE) {
            replayFusionRepository?.updateReplayRealVario(igcVario, current.timestampMillis)
        }
    }

    private fun shouldEmitGps(timestampMillis: Long): Boolean {
        val stepMs = simConfig.gpsStepMs
        if (stepMs <= 0L) return true
        if (lastGpsEmitTimestamp == Long.MIN_VALUE) return true
        return (timestampMillis - lastGpsEmitTimestamp) >= stepMs
    }

    private fun emitAirspeedSample(point: IgcPoint, qnhHpa: Double) {
        val indicatedKmh = point.indicatedAirspeedKmh
        val trueKmh = point.trueAirspeedKmh
        if (indicatedKmh == null && trueKmh == null) {
            replayAirspeedRepository.reset()
            return
        }

        val altitudeMeters = (point.pressureAltitude ?: point.gpsAltitude).takeIf { it.isFinite() } ?: 0.0
        val densityRatio = computeDensityRatio(altitudeMeters, qnhHpa)
        val indicatedMs: Double
        val trueMs: Double

        if (indicatedKmh != null && trueKmh != null) {
            indicatedMs = kmhToMs(indicatedKmh)
            trueMs = kmhToMs(trueKmh)
        } else if (indicatedKmh != null) {
            indicatedMs = kmhToMs(indicatedKmh)
            trueMs = if (densityRatio > 0.0) indicatedMs / sqrt(densityRatio) else indicatedMs
        } else {
            trueMs = kmhToMs(trueKmh!!)
            indicatedMs = if (densityRatio > 0.0) trueMs * sqrt(densityRatio) else trueMs
        }

        if (!indicatedMs.isFinite() || !trueMs.isFinite()) {
            replayAirspeedRepository.reset()
            return
        }

        replayAirspeedRepository.emitAirspeed(
            trueMs = trueMs,
            indicatedMs = indicatedMs,
            timestampMillis = point.timestampMillis,
            valid = true
        )
    }

    private fun kmhToMs(valueKmh: Double): Double = valueKmh * KMH_TO_MS

    private fun computeDensityRatio(altitudeMeters: Double, qnhHpa: Double): Double {
        val tempSeaLevelK = FlightMetricsConstants.SEA_LEVEL_TEMP_CELSIUS + 273.15
        val theta = 1.0 + (FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M * altitudeMeters) / tempSeaLevelK
        if (theta <= 0.0) return 0.0
        val exponent = (-FlightMetricsConstants.GRAVITY /
            (FlightMetricsConstants.GAS_CONSTANT * FlightMetricsConstants.TEMP_LAPSE_RATE_C_PER_M)) - 1.0
        val standardDensityRatio = theta.pow(exponent)
        val qnhRatio = (qnhHpa / FlightMetricsConstants.SEA_LEVEL_PRESSURE_HPA)
            .takeIf { it.isFinite() && it > 0.0 }
            ?: 1.0
        return standardDensityRatio * qnhRatio
    }

    companion object {
        private const val TAG = "ReplaySampleEmitter"
        private const val KMH_TO_MS = 1000.0 / 3600.0
    }
}
