package com.example.xcpro.livefollow.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
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
        if (currentSession.sessionId == sessionId &&
            currentSession.role == LiveFollowSessionRole.WATCHER
        ) {
            routeFeedback.value = LiveFollowWatchRouteFeedback(requestedSessionId = sessionId)
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

    fun stopWatching() {
        if (routeFeedback.value.isBusy) return
        viewModelScope.launch {
            val sessionId = useCase.sessionState.value.sessionId
            routeFeedback.update { feedback ->
                feedback.copy(
                    requestedSessionId = sessionId ?: feedback.requestedSessionId,
                    isBusy = true,
                    message = null
                )
            }
            val result = useCase.stopWatching()
            routeFeedback.value = when (result) {
                LiveFollowCommandResult.Success -> LiveFollowWatchRouteFeedback()
                else -> feedbackAfterCommand(
                    requestedSessionId = sessionId,
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
        requestedSessionId: String?,
        result: LiveFollowCommandResult
    ): LiveFollowWatchRouteFeedback {
        return when (result) {
            LiveFollowCommandResult.Success ->
                LiveFollowWatchRouteFeedback(requestedSessionId = requestedSessionId)

            else -> LiveFollowWatchRouteFeedback(
                requestedSessionId = requestedSessionId,
                message = liveFollowWatchCommandMessage(result)
            )
        }
    }
}
