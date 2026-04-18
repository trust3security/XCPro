package com.trust3.xcpro.livefollow.data.session

import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.model.liveFollowUnavailableTransport
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE =
    "LiveFollow session transport is unavailable in this transport-limited build."

@Singleton
class UnavailableLiveFollowSessionGateway @Inject constructor() : LiveFollowSessionGateway {
    private val mutableSessionState = MutableStateFlow(
        liveFollowGatewayIdleSnapshot(
            transportAvailability = liveFollowUnavailableTransport(
                LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
            )
        )
    )

    override val sessionState: StateFlow<LiveFollowSessionGatewaySnapshot> =
        mutableSessionState.asStateFlow()

    override suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult = fail()

    override suspend fun updatePilotVisibility(
        sessionId: String,
        visibility: LiveFollowSessionVisibility
    ): LiveFollowSessionGatewayResult = fail()

    override suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult = fail()

    override suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult = fail()

    override suspend fun joinAuthenticatedWatchSession(
        sessionId: String
    ): LiveFollowSessionGatewayResult = fail()

    override suspend fun joinWatchSessionByShareCode(
        shareCode: String
    ): LiveFollowSessionGatewayResult = fail()

    override suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult = fail()

    override suspend fun uploadPilotPosition(
        snapshot: LiveOwnshipSnapshot
    ): LiveFollowPilotPositionUploadResult {
        fail()
        return LiveFollowPilotPositionUploadResult.Failure(
            LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
        )
    }

    override suspend fun uploadPilotTask(
        snapshot: LiveFollowTaskSnapshot?
    ): LiveFollowPilotTaskUploadResult {
        fail()
        return LiveFollowPilotTaskUploadResult.Failure(
            LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
        )
    }

    private fun fail(): LiveFollowSessionGatewayResult {
        mutableSessionState.value = liveFollowGatewayIdleSnapshot(
            transportAvailability = liveFollowUnavailableTransport(
                LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
            ),
            lastError = LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
        )
        return LiveFollowSessionGatewayResult.Failure(LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE)
    }
}
