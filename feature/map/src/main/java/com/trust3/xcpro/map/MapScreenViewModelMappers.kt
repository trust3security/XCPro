package com.trust3.xcpro.map

import com.trust3.xcpro.map.model.GpsStatusUiModel
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.GpsStatus

internal fun GpsStatus.toUiModel(): GpsStatusUiModel = when (this) {
    GpsStatus.NoPermission -> GpsStatusUiModel.NoPermission
    GpsStatus.Disabled -> GpsStatusUiModel.Disabled
    GpsStatus.Searching -> GpsStatusUiModel.Searching
    is GpsStatus.LostFix -> GpsStatusUiModel.LostFix(ageMs = ageMs)
    is GpsStatus.Ok -> GpsStatusUiModel.Ok(
        ageMs = ageMs,
        accuracyMeters = accuracyMeters
    )
}

internal fun GPSData.toUiModel(): MapLocationUiModel =
    MapLocationUiModel(
        latitude = position.latitude,
        longitude = position.longitude,
        speedMs = speed.value,
        bearingDeg = bearing,
        accuracyMeters = accuracy.toDouble(),
        bearingAccuracyDeg = bearingAccuracyDeg,
        speedAccuracyMs = speedAccuracyMs,
        timestampMs = timestamp,
        monotonicTimestampMs = monotonicTimestampMillis
    )
