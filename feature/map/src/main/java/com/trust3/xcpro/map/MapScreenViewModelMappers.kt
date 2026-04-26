package com.trust3.xcpro.map

import com.trust3.xcpro.livesource.LiveSourceStatus
import com.trust3.xcpro.map.model.GpsStatusUiModel
import com.trust3.xcpro.map.model.MapLocationUiModel
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.GpsStatus

internal fun LiveSourceStatus.toUiModel(): GpsStatusUiModel = when (this) {
    LiveSourceStatus.PhoneReady,
    LiveSourceStatus.CondorReady -> GpsStatusUiModel.Ok(ageMs = 0L, accuracyMeters = null)

    is LiveSourceStatus.PhoneDegraded -> when (reason) {
        com.trust3.xcpro.livesource.PhoneLiveDegradedReason.LOCATION_PERMISSION_MISSING ->
            GpsStatusUiModel.NoPermission

        com.trust3.xcpro.livesource.PhoneLiveDegradedReason.LOCATION_PROVIDER_DISABLED ->
            GpsStatusUiModel.Disabled

        com.trust3.xcpro.livesource.PhoneLiveDegradedReason.PLATFORM_RUNTIME_UNAVAILABLE ->
            GpsStatusUiModel.Searching
    }

    is LiveSourceStatus.CondorDegraded -> when (reason) {
        com.trust3.xcpro.simulator.CondorLiveDegradedReason.STALE_STREAM ->
            GpsStatusUiModel.CondorStale

        com.trust3.xcpro.simulator.CondorLiveDegradedReason.TRANSPORT_ERROR ->
            GpsStatusUiModel.CondorTransportError

        com.trust3.xcpro.simulator.CondorLiveDegradedReason.DISCONNECTED ->
            GpsStatusUiModel.CondorDisconnected
    }
}

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
