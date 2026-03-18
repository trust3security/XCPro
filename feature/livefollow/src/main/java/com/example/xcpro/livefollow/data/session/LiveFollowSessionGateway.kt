package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import kotlinx.coroutines.flow.StateFlow

interface LiveFollowSessionGateway {
    val sessionState: StateFlow<LiveFollowSessionGatewaySnapshot>

    suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult

    suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult

    suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult
}

data class LiveFollowSessionGatewaySnapshot(
    val sessionId: String?,
    val role: LiveFollowSessionRole,
    val lifecycle: LiveFollowSessionLifecycle,
    val watchIdentity: LiveFollowIdentityProfile?,
    val directWatchAuthorized: Boolean,
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

fun liveFollowGatewayIdleSnapshot(
    lastError: String? = null
): LiveFollowSessionGatewaySnapshot = LiveFollowSessionGatewaySnapshot(
    sessionId = null,
    role = LiveFollowSessionRole.NONE,
    lifecycle = LiveFollowSessionLifecycle.IDLE,
    watchIdentity = null,
    directWatchAuthorized = false,
    lastError = lastError
)
