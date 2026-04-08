package com.example.xcpro.map

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

    fun fromReplayFrame(
        replayFrame: ReplayLocationFrame,
        orientation: OrientationData
    ): RawFixEnvelope {
        val fix = DisplayPoseSmoother.RawFix(
            latitude = replayFrame.latitude,
            longitude = replayFrame.longitude,
            speedMs = replayFrame.groundSpeedMs,
            trackDeg = replayFrame.trackDeg,
            headingDeg = orientation.headingDeg,
            accuracyM = replayFrame.accuracyMeters,
            bearingAccuracyDeg = null,
            speedAccuracyMs = null,
            timestampMs = replayFrame.replayTimestampMs,
            orientationMode = orientation.mode
        )
        return RawFixEnvelope(fix, DisplayClock.TimeBase.REPLAY)
    }
}
