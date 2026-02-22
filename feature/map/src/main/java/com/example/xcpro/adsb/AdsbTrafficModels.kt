package com.example.xcpro.adsb

internal const val ADSB_ERROR_OFFLINE = "Network unavailable"
internal const val ADSB_ERROR_CIRCUIT_BREAKER_OPEN = "ADS-B paused after repeated failures"
internal const val ADSB_ERROR_CIRCUIT_BREAKER_PROBE = "ADS-B retry probe in progress"

@JvmInline
value class Icao24(val raw: String) {
    companion object {
        fun from(value: String?): Icao24? {
            val sanitized = value
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[0-9a-f]{6}")) }
            return sanitized?.let(::Icao24)
        }
    }
}

data class AdsbTarget(
    val id: Icao24,
    val callsign: String?,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val speedMps: Double?,
    val trackDeg: Double?,
    val climbMps: Double?,
    val positionSource: Int?,
    val category: Int?,
    val lastContactEpochSec: Long?,
    val receivedMonoMs: Long
)

data class AdsbTrafficUiModel(
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
    val usesOwnshipReference: Boolean = true,
    val positionSource: Int?,
    val category: Int?,
    val lastContactEpochSec: Long?,
    val isEmergencyCollisionRisk: Boolean = false,
    val metadataTypecode: String? = null,
    val metadataIcaoAircraftType: String? = null
)

sealed interface AdsbConnectionState {
    data object Disabled : AdsbConnectionState
    data object Active : AdsbConnectionState
    data class BackingOff(val retryAfterSec: Int) : AdsbConnectionState
    data class Error(val message: String) : AdsbConnectionState
}

enum class AdsbAuthMode {
    Anonymous,
    Authenticated,
    AuthFailed
}

data class AdsbTrafficSnapshot(
    val targets: List<AdsbTrafficUiModel>,
    val connectionState: AdsbConnectionState,
    val authMode: AdsbAuthMode = AdsbAuthMode.Anonymous,
    val centerLat: Double?,
    val centerLon: Double?,
    val usesOwnshipReference: Boolean = false,
    val receiveRadiusKm: Int,
    val fetchedCount: Int,
    val withinRadiusCount: Int,
    val withinVerticalCount: Int = 0,
    val filteredByVerticalCount: Int = 0,
    val cappedCount: Int = 0,
    val displayedCount: Int,
    val lastHttpStatus: Int?,
    val remainingCredits: Int?,
    val lastPollMonoMs: Long?,
    val lastSuccessMonoMs: Long?,
    val lastError: String?,
    val lastNetworkFailureKind: AdsbNetworkFailureKind? = null,
    val consecutiveFailureCount: Int = 0,
    val nextRetryMonoMs: Long? = null,
    val lastFailureMonoMs: Long? = null
)

data class AdsbAuth(
    val bearerToken: String?
)
