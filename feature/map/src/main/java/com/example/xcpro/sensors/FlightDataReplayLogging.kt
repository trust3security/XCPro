package com.example.xcpro.sensors

import android.util.Log

internal fun logReplayBaroSample(
    tag: String,
    baroTimestamp: Long,
    pressureHpa: Double,
    smoothedPressure: Double,
    baroAltitude: Double,
    displayAltitude: Double,
    verticalSpeed: Double,
    deltaTime: Double,
    gpsAltitude: Double,
    gpsSpeed: Double,
    validUntil: Long
) {
    Log.d(
        tag,
        "REPLAY_BARO ts=$baroTimestamp p=${"%.2f".format(pressureHpa)} " +
            "pSmooth=${"%.2f".format(smoothedPressure)} alt=${"%.1f".format(baroAltitude)} " +
            "dispAlt=${"%.1f".format(displayAltitude)} vs=${"%.3f".format(verticalSpeed)} " +
            "dt=${"%.3f".format(deltaTime)} gpsAlt=${"%.1f".format(gpsAltitude)} " +
            "gs=${"%.2f".format(gpsSpeed)} validUntil=$validUntil"
    )
}

internal fun logReplayGpsSample(
    tag: String,
    latitude: Double,
    longitude: Double,
    altitude: Double,
    groundSpeed: Double,
    bearing: Double,
    timestamp: Long
) {
    Log.d(
        tag,
        "REPLY_GPS_SAMPLE lat=$latitude, lon=$longitude " +
            "alt=$altitude gs=$groundSpeed track=$bearing ts=$timestamp"
    )
}