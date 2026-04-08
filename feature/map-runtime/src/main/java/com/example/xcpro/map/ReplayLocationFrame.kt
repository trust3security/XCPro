package com.example.xcpro.map

data class ReplayLocationFrame(
    val latitude: Double,
    val longitude: Double,
    val groundSpeedMs: Double,
    val trackDeg: Double,
    val accuracyMeters: Double,
    val gpsAltitudeMeters: Double,
    val replayTimestampMs: Long
)
