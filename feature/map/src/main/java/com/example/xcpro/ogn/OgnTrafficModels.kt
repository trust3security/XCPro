package com.example.xcpro.ogn

data class OgnTrafficIdentity(
    val registration: String?,
    val competitionNumber: String?,
    val aircraftModel: String?,
    val tracked: Boolean?,
    val identified: Boolean?,
    val aircraftTypeCode: Int?
)

enum class OgnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class OgnTrafficSnapshot(
    val targets: List<OgnTrafficTarget>,
    val connectionState: OgnConnectionState,
    val lastError: String?,
    val subscriptionCenterLat: Double?,
    val subscriptionCenterLon: Double?,
    val receiveRadiusKm: Int,
    val ddbCacheAgeMs: Long?,
    val reconnectBackoffMs: Long?,
    val lastReconnectWallMs: Long?,
    val activeSubscriptionCenterLat: Double? = null,
    val activeSubscriptionCenterLon: Double? = null
)

data class OgnTrafficTarget(
    val id: String,
    val callsign: String,
    val destination: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val trackDegrees: Double?,
    val groundSpeedMps: Double?,
    val verticalSpeedMps: Double?,
    val deviceIdHex: String?,
    val signalDb: Double?,
    val displayLabel: String,
    val identity: OgnTrafficIdentity?,
    val rawComment: String?,
    val rawLine: String,
    val timestampMillis: Long,
    val lastSeenMillis: Long
) {
    fun isStale(nowMillis: Long, staleAfterMillis: Long): Boolean =
        nowMillis - lastSeenMillis > staleAfterMillis
}
