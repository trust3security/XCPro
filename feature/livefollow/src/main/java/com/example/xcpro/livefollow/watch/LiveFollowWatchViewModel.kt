package com.example.xcpro.livefollow.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.session.LiveFollowWatchLookupType
import com.example.xcpro.livefollow.normalizeLiveFollowShareCode
import com.example.xcpro.livefollow.normalizeLiveFollowSessionId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LiveFollowWatchViewModel @Inject constructor(
    private val useCase: LiveFollowWatchUseCase
) : ViewModel() {
    private val routeFeedback = MutableStateFlow(LiveFollowWatchRouteFeedback())
    private val mutableUiState = MutableStateFlow(LiveFollowWatchUiState())

    val uiState: StateFlow<LiveFollowWatchUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                useCase.sessionState,
                useCase.watchState,
                routeFeedback
            ) { session, watchSnapshot, feedback ->
                buildLiveFollowWatchUiState(
                    session = session,
                    watchSnapshot = watchSnapshot,
                    feedback = feedback
                )
            }.collectLatest { state ->
                mutableUiState.value = state
            }
        }
    }

    suspend fun handleWatchEntry(rawSessionId: String?) {
        val sessionId = normalizeLiveFollowSessionId(rawSessionId)
        if (sessionId == null) {
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                message = "Invalid LiveFollow session id."
            )
            return
        }
        if (routeFeedback.value.isBusy && routeFeedback.value.requestedSessionId == sessionId) {
            return
        }
        val currentSession = useCase.sessionState.value
        if (currentSession.matchesWatchSessionIdEntry(sessionId)) {
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                requestedSessionId = sessionId
            )
            return
        }
        routeFeedback.value = LiveFollowWatchRouteFeedback(
            requestedSessionId = sessionId,
            isBusy = true
        )
        val result = useCase.joinWatchSession(sessionId)
        routeFeedback.value = feedbackAfterCommand(
            requestedSessionId = sessionId,
            result = result
        )
    }

    suspend fun handleWatchShareEntry(rawShareCode: String?) {
        val shareCode = normalizeLiveFollowShareCode(rawShareCode)
        if (shareCode == null) {
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                message = "Invalid LiveFollow share code."
            )
            return
        }
        if (routeFeedback.value.isBusy && routeFeedback.value.requestedShareCode == shareCode) {
            return
        }
        val currentSession = useCase.sessionState.value
        if (currentSession.matchesWatchShareCodeEntry(shareCode)) {
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                requestedShareCode = shareCode
            )
            return
        }
        routeFeedback.value = LiveFollowWatchRouteFeedback(
            requestedShareCode = shareCode,
            isBusy = true
        )
        val result = useCase.joinWatchSessionByShareCode(shareCode)
        routeFeedback.value = feedbackAfterCommand(
            requestedShareCode = shareCode,
            result = result
        )
    }

    fun stopWatching() {
        if (routeFeedback.value.isBusy) return
        viewModelScope.launch {
            val sessionSnapshot = useCase.sessionState.value
            val sessionId = sessionSnapshot.sessionId
            val shareCode = sessionSnapshot.shareCode
            routeFeedback.update { feedback ->
                feedback.copy(
                    requestedSessionId = sessionId ?: feedback.requestedSessionId,
                    requestedShareCode = shareCode ?: feedback.requestedShareCode,
                    isBusy = true,
                    message = null
                )
            }
            val result = useCase.stopWatching()
            routeFeedback.value = when (result) {
                LiveFollowCommandResult.Success -> LiveFollowWatchRouteFeedback()
                else -> feedbackAfterCommand(
                    requestedSessionId = sessionId,
                    requestedShareCode = shareCode,
                    result = result
                )
            }
        }
    }

    fun dismissFeedback() {
        if (useCase.sessionState.value.sessionId != null) return
        routeFeedback.value = LiveFollowWatchRouteFeedback()
    }

    private fun feedbackAfterCommand(
        requestedSessionId: String? = null,
        requestedShareCode: String? = null,
        result: LiveFollowCommandResult
    ): LiveFollowWatchRouteFeedback {
        return when (result) {
            LiveFollowCommandResult.Success ->
                LiveFollowWatchRouteFeedback(
                    requestedSessionId = requestedSessionId,
                    requestedShareCode = requestedShareCode
                )

            else -> LiveFollowWatchRouteFeedback(
                requestedSessionId = requestedSessionId,
                requestedShareCode = requestedShareCode,
                message = liveFollowWatchCommandMessage(result)
            )
        }
    }
}

private fun LiveFollowSessionSnapshot.matchesWatchSessionIdEntry(
    sessionId: String
): Boolean {
    if (role != LiveFollowSessionRole.WATCHER) return false
    if (this.sessionId != sessionId) return false
    return watchLookup?.type != LiveFollowWatchLookupType.SHARE_CODE
}

private fun LiveFollowSessionSnapshot.matchesWatchShareCodeEntry(
    shareCode: String
): Boolean {
    if (role != LiveFollowSessionRole.WATCHER) return false
    if (this.shareCode != shareCode) return false
    return watchLookup?.type == LiveFollowWatchLookupType.SHARE_CODE
}
