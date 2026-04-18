package com.trust3.xcpro.adsb

import com.trust3.xcpro.adsb.metadata.domain.MetadataAvailability
import com.trust3.xcpro.adsb.metadata.domain.MetadataSyncState

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
    val usesOwnshipReference: Boolean,
    val proximityTier: AdsbProximityTier,
    val proximityReason: AdsbProximityReason,
    val isClosing: Boolean,
    val closingRateMps: Double?,
    val isEmergencyCollisionRisk: Boolean,
    val isEmergencyAudioEligible: Boolean,
    val emergencyAudioIneligibilityReason: AdsbEmergencyAudioIneligibilityReason? = null,
    val isCirclingEmergencyRedRule: Boolean,
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
    val metadataSyncState: MetadataSyncState,
    val positionAgeSec: Int = ageSec,
    val contactAgeSec: Int? = null,
    val isPositionStale: Boolean = isStale,
    val positionTimestampEpochSec: Long? = null,
    val effectivePositionEpochSec: Long? = null,
    val positionFreshnessSource: AdsbPositionFreshnessSource =
        AdsbPositionFreshnessSource.RECEIVED_MONO_FALLBACK
)

