package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.model.MapLocationUiModel

class LocationFeedAdapter {

    data class RawFixEnvelope(
        val fix: DisplayPoseSmoother.RawFix,
        val timeBase: DisplayClock.TimeBase
    )

    fun fromGps(location: MapLocationUiModel, orientation: OrientationData): RawFixEnvelope {
        val timeBase = if (location.monotonicTimestampMs > 0L) {
            DisplayClock.TimeBase.MONOTONIC
        } else {
            DisplayClock.TimeBase.WALL
        }
        val timestampMs = if (timeBase == DisplayClock.TimeBase.MONOTONIC) {
            location.monotonicTimestampMs
        } else {
            location.timestampMs
        }
        val fix = DisplayPoseSmoother.RawFix(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMs = location.speedMs,
            trackDeg = location.bearingDeg,
            headingDeg = orientation.headingDeg,
            accuracyM = location.accuracyMeters,
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
