package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.UnitsConverter

internal fun ReplayLocationFrame.hasValidDisplayCoordinate(): Boolean =
    latitude != 0.0 &&
        longitude != 0.0 &&
        isValidDisplayCoordinate(latitude, longitude)

internal fun isValidDisplayCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude >= -90.0 &&
        latitude <= 90.0 &&
        longitude >= -180.0 &&
        longitude <= 180.0

internal fun ReplayLocationFrame.buildReplayFrameDebugMessage(): String {
    val groundSpeedKnots = String.format("%.1f", UnitsConverter.msToKnots(groundSpeedMs))
    return "Replay frame: lat=$latitude, lon=$longitude, " +
        "accuracy=${accuracyMeters}, gpsAlt=${gpsAltitudeMeters}m, " +
        "gs=${groundSpeedKnots}kt, track=$trackDeg"
}

internal fun ReplayLocationFrame.buildInvalidReplayFrameDebugMessage(): String =
    "Replay feed: invalid coordinates (lat=$latitude, lon=$longitude)"
