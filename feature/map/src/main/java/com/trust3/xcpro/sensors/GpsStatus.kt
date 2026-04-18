package com.trust3.xcpro.sensors

sealed class GpsStatus {
    object NoPermission : GpsStatus()
    object Disabled : GpsStatus()
    object Searching : GpsStatus()
    data class LostFix(val ageMs: Long) : GpsStatus()
    data class Ok(val ageMs: Long, val accuracyMeters: Float?) : GpsStatus()
}
