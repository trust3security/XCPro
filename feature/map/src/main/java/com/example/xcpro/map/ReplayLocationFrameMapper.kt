package com.example.xcpro.map

import com.example.xcpro.core.flight.RealTimeFlightData

internal fun RealTimeFlightData.toReplayLocationFrame(): ReplayLocationFrame =
    ReplayLocationFrame(
        latitude = latitude,
        longitude = longitude,
        groundSpeedMs = groundSpeed,
        trackDeg = track,
        accuracyMeters = accuracy,
        gpsAltitudeMeters = gpsAltitude,
        replayTimestampMs = timestamp
    )
