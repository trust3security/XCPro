package com.trust3.xcpro.weather.wind.domain

import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindVector
import kotlin.math.abs

data class WindStoreResult(
    val vector: WindVector,
    val quality: Int,
    val clockMillis: Long,
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
    private var lastUpdateWall: Long = 0L

    fun reset() {
        measurementList.reset()
        lastAltitude = Double.NaN
        updated = false
        lastUpdateClock = 0L
        lastUpdateWall = 0L
    }

    fun slotMeasurement(
        clockMillis: Long,
        timestampMillis: Long,
        altitudeMeters: Double,
        vector: WindVector,
        quality: Int,
        source: WindSource,
        clearExisting: Boolean = false
    ) {
        if (clearExisting) {
            measurementList.reset()
            updated = false
            lastUpdateClock = 0L
            lastUpdateWall = 0L
        }
        measurementList.addMeasurement(clockMillis, vector, altitudeMeters, quality, source)
        lastUpdateClock = clockMillis
        lastUpdateWall = timestampMillis
        updated = true
    }

    fun evaluate(currentClockMillis: Long, altitudeMeters: Double): WindStoreResult? {
        val altitudeChanged = if (lastAltitude.isNaN()) {
            true
        } else {
            abs(altitudeMeters - lastAltitude) > altitudeDeltaThreshold
        }

        if (!updated && !altitudeChanged) {
            return null
        }

        val weighted = measurementList.getWeightedWind(currentClockMillis, altitudeMeters) ?: return null

        lastAltitude = altitudeMeters
        updated = false

        return WindStoreResult(
            vector = weighted.vector,
            quality = weighted.approximateQuality,
            clockMillis = if (lastUpdateClock != 0L) lastUpdateClock else currentClockMillis,
            timestampMillis = lastUpdateWall,
            source = weighted.source
        )
    }
}
