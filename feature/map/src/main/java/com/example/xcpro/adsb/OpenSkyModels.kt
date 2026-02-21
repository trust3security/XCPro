package com.example.xcpro.adsb

data class OpenSkyResponse(
    val timeSec: Long?,
    val states: List<OpenSkyStateVector>
)

data class OpenSkyStateVector(
    val icao24: String,
    val callsign: String?,
    val timePositionSec: Long?,
    val lastContactSec: Long?,
    val longitude: Double?,
    val latitude: Double?,
    val baroAltitudeM: Double?,
    val velocityMps: Double?,
    val trueTrackDeg: Double?,
    val verticalRateMps: Double?,
    val geoAltitudeM: Double?,
    val positionSource: Int?,
    val category: Int?
) {
    val altitudeM: Double?
        get() = geoAltitudeM ?: baroAltitudeM
}

data class OpenSkyTokenResponse(
    val accessToken: String,
    val expiresInSec: Long?
)

data class OpenSkyClientCredentials(
    val clientId: String,
    val clientSecret: String
)
