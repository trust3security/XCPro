package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.example.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import kotlinx.coroutines.flow.StateFlow

interface LiveFollowSessionGateway {
    val sessionState: StateFlow<LiveFollowSessionGatewaySnapshot>

    suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult

    suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun uploadPilotPosition(
        snapshot: LiveOwnshipSnapshot
    ): LiveFollowPilotPositionUploadResult
}

data class LiveFollowSessionGatewaySnapshot(
    val sessionId: String?,
    val role: LiveFollowSessionRole,
    val lifecycle: LiveFollowSessionLifecycle,
    val watchIdentity: LiveFollowIdentityProfile?,
    val directWatchAuthorized: Boolean,
    val transportAvailability: LiveFollowTransportAvailability,
    val lastError: String?
)

sealed interface LiveFollowSessionGatewayResult {
    data class Success(
        val snapshot: LiveFollowSessionGatewaySnapshot
    ) : LiveFollowSessionGatewayResult

    data class Failure(
        val message: String
    ) : LiveFollowSessionGatewayResult
}

sealed interface LiveFollowPilotPositionUploadResult {
    data object Uploaded : LiveFollowPilotPositionUploadResult

    data class Skipped(
        val reason: LiveFollowPilotPositionSkipReason
    ) : LiveFollowPilotPositionUploadResult

    data class Failure(
        val message: String
    ) : LiveFollowPilotPositionUploadResult
}

enum class LiveFollowPilotPositionSkipReason {
    NOT_PILOT_SESSION,
    MISSING_CREDENTIALS,
    MISSING_REQUIRED_FIELDS,
    NON_INCREASING_TIMESTAMP
}

fun liveFollowGatewayIdleSnapshot(
    lastError: String? = null,
    transportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport()
): LiveFollowSessionGatewaySnapshot = LiveFollowSessionGatewaySnapshot(
    sessionId = null,
    role = LiveFollowSessionRole.NONE,
    lifecycle = LiveFollowSessionLifecycle.IDLE,
    watchIdentity = null,
    directWatchAuthorized = false,
    transportAvailability = transportAvailability,
    lastError = lastError
)
