package com.example.xcpro.livefollow.watch

import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRepository
import com.example.xcpro.livefollow.data.watch.WatchTrafficRepository
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

    suspend fun joinWatchSessionByShareCode(shareCode: String): LiveFollowCommandResult {
        return sessionRepository.joinWatchSessionByShareCode(shareCode)
    }

    suspend fun stopWatching(): LiveFollowCommandResult = sessionRepository.leaveSession()
}
