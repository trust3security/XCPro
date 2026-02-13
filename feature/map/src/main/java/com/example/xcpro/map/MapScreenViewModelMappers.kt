package com.example.xcpro.map

import com.example.xcpro.map.model.GpsStatusUiModel
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.GpsStatus

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
