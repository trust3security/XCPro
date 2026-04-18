package com.trust3.xcpro.livefollow.data.session

import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import kotlinx.coroutines.flow.StateFlow

interface LiveFollowSessionGateway {
    val sessionState: StateFlow<LiveFollowSessionGatewaySnapshot>

    suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult

    suspend fun updatePilotVisibility(
        sessionId: String,
        visibility: LiveFollowSessionVisibility
    ): LiveFollowSessionGatewayResult

    suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun joinAuthenticatedWatchSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun joinWatchSessionByShareCode(
        shareCode: String
    ): LiveFollowSessionGatewayResult

    suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun uploadPilotPosition(
        snapshot: LiveOwnshipSnapshot
    ): LiveFollowPilotPositionUploadResult

    suspend fun uploadPilotTask(
        snapshot: LiveFollowTaskSnapshot?
    ): LiveFollowPilotTaskUploadResult
}

data class LiveFollowSessionGatewaySnapshot(
    val sessionId: String?,
    val ownerUserId: String? = null,
    val role: LiveFollowSessionRole,
    val lifecycle: LiveFollowSessionLifecycle,
    val visibility: LiveFollowSessionVisibility? = null,
    val watchIdentity: LiveFollowIdentityProfile?,
    val directWatchAuthorized: Boolean,
    val transportAvailability: LiveFollowTransportAvailability,
    val lastError: String?,
    val shareCode: String? = null,
    val watchLookup: LiveFollowWatchLookup? = null
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

sealed interface LiveFollowPilotTaskUploadResult {
    data object Uploaded : LiveFollowPilotTaskUploadResult

    data class Skipped(
        val reason: LiveFollowPilotTaskSkipReason
    ) : LiveFollowPilotTaskUploadResult

    data class Failure(
        val message: String
    ) : LiveFollowPilotTaskUploadResult
}

enum class LiveFollowPilotTaskSkipReason {
    NOT_PILOT_SESSION,
    MISSING_CREDENTIALS,
    MISSING_REQUIRED_FIELDS,
    UNCHANGED_TASK
}

fun liveFollowGatewayIdleSnapshot(
    lastError: String? = null,
    transportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport()
): LiveFollowSessionGatewaySnapshot = LiveFollowSessionGatewaySnapshot(
    sessionId = null,
    ownerUserId = null,
    role = LiveFollowSessionRole.NONE,
    lifecycle = LiveFollowSessionLifecycle.IDLE,
    visibility = null,
    watchIdentity = null,
    directWatchAuthorized = false,
    transportAvailability = transportAvailability,
    lastError = lastError,
    shareCode = null,
    watchLookup = null
)
