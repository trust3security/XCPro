package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.sensors.GPSData

class LocationFeedAdapter {

    data class RawFixEnvelope(
        val fix: DisplayPoseSmoother.RawFix,
        val timeBase: DisplayClock.TimeBase
    )

    fun fromGps(location: GPSData, orientation: OrientationData): RawFixEnvelope {
        val timeBase = if (location.monotonicTimestampMillis > 0L) {
            DisplayClock.TimeBase.MONOTONIC
        } else {
            DisplayClock.TimeBase.WALL
        }
        val timestampMs = if (timeBase == DisplayClock.TimeBase.MONOTONIC) {
            location.monotonicTimestampMillis
        } else {
            location.timestamp
        }
        val fix = DisplayPoseSmoother.RawFix(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMs = location.speed.value,
            trackDeg = location.bearing,
            headingDeg = orientation.headingDeg,
            accuracyM = location.accuracy.toDouble(),
            bearingAccuracyDeg = location.bearingAccuracyDeg,
            speedAccuracyMs = location.speedAccuracyMs,
            timestampMs = timestampMs,
            orientationMode = orientation.mode
        )
        return RawFixEnvelope(fix, timeBase)
    }

    fun fromFlightData(liveData: RealTimeFlightData, orientation: OrientationData): RawFixEnvelope {
        val fix = DisplayPoseSmoother.RawFix(
            latitude = liveData.latitude,
            longitude = liveData.longitude,
            speedMs = liveData.groundSpeed,
            trackDeg = liveData.track,
            headingDeg = orientation.headingDeg,
            accuracyM = liveData.accuracy,
            bearingAccuracyDeg = null,
            speedAccuracyMs = null,
            timestampMs = liveData.timestamp,
            orientationMode = orientation.mode
        )
        return RawFixEnvelope(fix, DisplayClock.TimeBase.REPLAY)
    }
}

