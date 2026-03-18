package com.example.xcpro.livefollow.data.session

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE =
    "LiveFollow backend session gateway is unavailable in this Phase 3 build."

@Singleton
class UnavailableLiveFollowSessionGateway @Inject constructor() : LiveFollowSessionGateway {
    private val mutableSessionState = MutableStateFlow(liveFollowGatewayIdleSnapshot())

    override val sessionState: StateFlow<LiveFollowSessionGatewaySnapshot> =
        mutableSessionState.asStateFlow()

    override suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowSessionGatewayResult = fail()

    override suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult = fail()

    override suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult = fail()

    override suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult = fail()

    private fun fail(): LiveFollowSessionGatewayResult {
        mutableSessionState.value = liveFollowGatewayIdleSnapshot(
            lastError = LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
        )
        return LiveFollowSessionGatewayResult.Failure(LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE)
    }
}
