package com.trust3.xcpro.ogn

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

enum class OgnConnectionIssue {
    UNEXPECTED_STREAM_END,
    OFFLINE_WAIT,
    LOGIN_UNVERIFIED,
    STALL_TIMEOUT,
    TRANSPORT_ERROR
}

data class OgnTrafficSnapshot(
    val targets: List<OgnTrafficTarget>,
    val suppressedTargetIds: Set<String> = emptySet(),
    val connectionState: OgnConnectionState,
    val connectionIssue: OgnConnectionIssue? = null,
    val lastError: String?,
    val subscriptionCenterLat: Double?,
    val subscriptionCenterLon: Double?,
    val receiveRadiusKm: Int,
    val ddbCacheAgeMs: Long?,
    val reconnectBackoffMs: Long?,
    val lastReconnectWallMs: Long?,
    val networkOnline: Boolean = true,
    val activeSubscriptionCenterLat: Double? = null,
    val activeSubscriptionCenterLon: Double? = null,
    val droppedOutOfOrderSourceFrames: Long = 0L,
    val droppedImplausibleMotionFrames: Long = 0L
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
    val lastSeenMillis: Long,
    val distanceMeters: Double? = null,
    val sourceTimestampWallMs: Long? = null,
    val addressType: OgnAddressType = OgnAddressType.UNKNOWN,
    val addressHex: String? = deviceIdHex,
    val canonicalKey: String = canonicalOgnTargetKey(
        type = addressType,
        addressHex = addressHex,
        fallbackId = id
    )
) {
    fun isStale(nowMillis: Long, staleAfterMillis: Long): Boolean =
        nowMillis - lastSeenMillis > staleAfterMillis
}
