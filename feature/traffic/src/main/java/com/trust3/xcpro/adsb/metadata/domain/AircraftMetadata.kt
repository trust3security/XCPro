package com.trust3.xcpro.adsb.metadata.domain

data class AircraftMetadata(
    val icao24: String,
    val registration: String?,
    val typecode: String?,
    val model: String?,
    val manufacturerName: String?,
    val owner: String?,
    val operator: String?,
    val operatorCallsign: String?,
    val icaoAircraftType: String?
)

