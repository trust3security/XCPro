package com.example.xcpro.xcprov1.bluetooth

/**
 * Represents a parsed GPS fix emitted by the Garmin GLO 2 receiver.
 */
data class GloGpsFix(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val groundSpeedMps: Double?,
    val trackDegrees: Double?,
    val timestampMillis: Long,
    val hdop: Double?,
    val vdop: Double?,
    val satellites: Int?,
    val fixQuality: Int?
) {
    val isValid: Boolean
        get() = latitude != 0.0 || longitude != 0.0

    val isMoving: Boolean
        get() = (groundSpeedMps ?: 0.0) > 0.5
}

/**
 * Connection status for the Garmin GLO 2 link.
 */
sealed class GloStatus {
    object Idle : GloStatus()
    object Discovering : GloStatus()
    data class Connecting(val deviceName: String) : GloStatus()
    data class Connected(val deviceName: String) : GloStatus()
    data class Disconnected(val reason: String?) : GloStatus()
    data class Error(val message: String, val throwable: Throwable? = null) : GloStatus()
}
