package com.example.xcpro.adsb

import com.example.xcpro.adsb.metadata.domain.MetadataAvailability
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState

data class AdsbSelectedTargetDetails(
    val id: Icao24,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val trackDeg: Double?,
    val climbMps: Double?,
    val ageSec: Int,
    val isStale: Boolean,
    val distanceMeters: Double,
    val bearingDegFromUser: Double,
    val positionSource: Int?,
    val category: Int?,
    val lastContactEpochSec: Long?,
    val registration: String?,
    val typecode: String?,
    val model: String?,
    val manufacturerName: String?,
    val owner: String?,
    val operator: String?,
    val operatorCallsign: String?,
    val icaoAircraftType: String?,
    val metadataAvailability: MetadataAvailability,
    val metadataSyncState: MetadataSyncState
)

