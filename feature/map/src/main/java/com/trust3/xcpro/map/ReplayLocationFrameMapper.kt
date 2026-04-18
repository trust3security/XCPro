package com.trust3.xcpro.map

import com.trust3.xcpro.core.flight.RealTimeFlightData

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
