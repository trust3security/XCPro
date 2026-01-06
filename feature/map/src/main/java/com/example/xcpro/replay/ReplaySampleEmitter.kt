package com.example.xcpro.replay

import android.util.Log
import com.example.xcpro.sensors.SensorFusionRepository

internal class ReplaySampleEmitter(
    private val replaySensorSource: ReplaySensorSource,
    private val simConfig: ReplaySimConfig
) {
    private val noiseModel = ReplayNoiseModel(simConfig)
    private val headingResolver = ReplayHeadingResolver()
    private var lastGpsEmitTimestamp: Long = Long.MIN_VALUE
    val random = noiseModel.random

    fun reset() {
        noiseModel.reset()
        headingResolver.reset()
        lastGpsEmitTimestamp = Long.MIN_VALUE
    }

    fun emitSample(
        current: IgcPoint,
        previous: IgcPoint?,
        qnhHpa: Double,
        startTimestampMillis: Long,
        replayFusionRepository: SensorFusionRepository?
    ) {
        val movement = IgcReplayMath.groundVector(current, previous)
        val groundSpeed = movement.speedMs
        val trackDeg = headingResolver.resolve(movement)
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
            val gpsNoise = noiseModel.gpsAltitudeNoise(
                timestampMillis = current.timestampMillis,
                startTimestampMillis = startTimestampMillis
            )
            replaySensorSource.emitGps(
                latitude = current.latitude,
                longitude = current.longitude,
                altitude = gpsAltitude + gpsNoise,
                speed = groundSpeed,
                bearing = trackDeg.toDouble(),
                accuracy = 5f,
                timestamp = current.timestampMillis
            )
            lastGpsEmitTimestamp = current.timestampMillis
        }
        replaySensorSource.emitCompass(
            heading = trackDeg.toDouble(),
            accuracy = 3,
            timestamp = current.timestampMillis
        )
        val igcVario = IgcReplayMath.verticalSpeed(current, previous)
        Log.d(
            TAG,
            "REPLAY_SAMPLE ts=${current.timestampMillis} " +
                "igcVario=${"%.3f".format(igcVario)} gpsAlt=${"%.1f".format(gpsAltitude)} " +
                "pressAlt=${"%.1f".format(pressureAltitude)} gs=${"%.2f".format(groundSpeed)} " +
                "track=${"%.1f".format(trackDeg)}"
        )
        if (simConfig.mode == ReplayMode.REFERENCE) {
            replayFusionRepository?.updateReplayRealVario(igcVario, current.timestampMillis)
        }
    }

    private fun shouldEmitGps(timestampMillis: Long): Boolean {
        if (simConfig.mode != ReplayMode.REALTIME_SIM) return true
        if (lastGpsEmitTimestamp == Long.MIN_VALUE) return true
        return (timestampMillis - lastGpsEmitTimestamp) >= simConfig.gpsStepMs
    }

    companion object {
        private const val TAG = "ReplaySampleEmitter"
    }
}
