package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.abs

data class WindStoreResult(
    val vector: WindVector,
    val quality: Int,
    val timestampMillis: Long,
    val source: WindSource
)

class WindStore(
    private val measurementList: WindMeasurementList = WindMeasurementList(),
    private val altitudeDeltaThreshold: Double = 100.0
) {

    private var lastAltitude: Double = Double.NaN
    private var updated = false
    private var lastUpdateClock: Long = 0L

    fun reset() {
        measurementList.reset()
        lastAltitude = Double.NaN
        updated = false
        lastUpdateClock = 0L
    }

    fun slotMeasurement(
        timestampMillis: Long,
        altitudeMeters: Double,
        vector: WindVector,
        quality: Int,
        source: WindSource
    ) {
        measurementList.addMeasurement(timestampMillis, vector, altitudeMeters, quality, source)
        lastUpdateClock = timestampMillis
        updated = true
    }

    fun evaluate(currentTimeMillis: Long, altitudeMeters: Double): WindStoreResult? {
        val altitudeChanged = if (lastAltitude.isNaN()) {
            true
        } else {
            abs(altitudeMeters - lastAltitude) > altitudeDeltaThreshold
        }

        if (!updated && !altitudeChanged) {
            return null
        }

        val weighted = measurementList.getWeightedWind(currentTimeMillis, altitudeMeters) ?: return null

        lastAltitude = altitudeMeters
        updated = false

        return WindStoreResult(
            vector = weighted.vector,
            quality = weighted.approximateQuality,
            timestampMillis = if (lastUpdateClock != 0L) lastUpdateClock else currentTimeMillis,
            source = weighted.source
        )
    }
}
