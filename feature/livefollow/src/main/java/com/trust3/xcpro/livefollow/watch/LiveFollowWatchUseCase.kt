package com.trust3.xcpro.livefollow.watch

import com.trust3.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRepository
import com.trust3.xcpro.livefollow.data.watch.WatchTrafficRepository
import javax.inject.Inject

class LiveFollowWatchUseCase @Inject constructor(
    private val sessionRepository: LiveFollowSessionRepository,
    private val watchTrafficRepository: WatchTrafficRepository
) {
    val sessionState = sessionRepository.state
    val watchState = watchTrafficRepository.state

    suspend fun joinWatchSession(sessionId: String): LiveFollowCommandResult {
        return sessionRepository.joinWatchSession(sessionId)
    }

    suspend fun joinAuthenticatedWatchSession(sessionId: String): LiveFollowCommandResult {
        return sessionRepository.joinAuthenticatedWatchSession(sessionId)
    }

    suspend fun joinWatchSessionByShareCode(shareCode: String): LiveFollowCommandResult {
        return sessionRepository.joinWatchSessionByShareCode(shareCode)
    }

    suspend fun stopWatching(): LiveFollowCommandResult = sessionRepository.leaveSession()
}
