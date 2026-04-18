package com.trust3.xcpro.livefollow.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.trust3.xcpro.livefollow.normalizeLiveFollowShareCode
import com.trust3.xcpro.livefollow.normalizeLiveFollowSessionId
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
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                requestedSessionId = sessionId,
                selectedTarget = null
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

    suspend fun handleAuthorizedWatchEntry(rawSessionId: String?) {
        handleAuthorizedWatchEntry(rawSessionId = rawSessionId, selectionHint = null)
    }

    suspend fun handleAuthorizedWatchEntry(
        rawSessionId: String?,
        selectionHint: LiveFollowWatchSelectionHint?
    ) {
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
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                requestedSessionId = sessionId,
                selectedTarget = selectionHint
            )
            return
        }
        routeFeedback.value = LiveFollowWatchRouteFeedback(
            requestedSessionId = sessionId,
            selectedTarget = selectionHint,
            isBusy = true
        )
        val result = useCase.joinAuthenticatedWatchSession(sessionId)
        routeFeedback.value = feedbackAfterCommand(
            requestedSessionId = sessionId,
            selectedTarget = selectionHint,
            result = result
        )
    }

    suspend fun handleWatchShareEntry(rawShareCode: String?) {
        handleWatchShareEntry(
            rawShareCode = rawShareCode,
            selectionHint = null
        )
    }

    suspend fun handleWatchShareEntry(
        rawShareCode: String?,
        selectionHint: LiveFollowWatchSelectionHint?
    ) {
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
        if (currentSession.shareCode == shareCode &&
            currentSession.role == LiveFollowSessionRole.WATCHER
        ) {
            routeFeedback.value = LiveFollowWatchRouteFeedback(
                requestedShareCode = shareCode,
                selectedTarget = selectionHint
            )
            return
        }
        routeFeedback.value = LiveFollowWatchRouteFeedback(
            requestedShareCode = shareCode,
            selectedTarget = selectionHint,
            isBusy = true
        )
        val result = useCase.joinWatchSessionByShareCode(shareCode)
        routeFeedback.value = feedbackAfterCommand(
            requestedShareCode = shareCode,
            selectedTarget = selectionHint,
            result = result
        )
    }

    fun clearWatchTarget() {
        if (routeFeedback.value.isBusy) return
        val sessionSnapshot = useCase.sessionState.value
        val hasActiveWatch = sessionSnapshot.sideEffectsAllowed &&
            sessionSnapshot.role == LiveFollowSessionRole.WATCHER &&
            sessionSnapshot.sessionId != null
        if (hasActiveWatch) {
            stopWatching()
            return
        }
        routeFeedback.value = LiveFollowWatchRouteFeedback()
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
                    selectedTarget = feedback.selectedTarget,
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
        selectedTarget: LiveFollowWatchSelectionHint? = null,
        result: LiveFollowCommandResult
    ): LiveFollowWatchRouteFeedback {
        return when (result) {
            LiveFollowCommandResult.Success ->
                LiveFollowWatchRouteFeedback(
                    requestedSessionId = requestedSessionId,
                    requestedShareCode = requestedShareCode,
                    selectedTarget = selectedTarget
                )

            else -> LiveFollowWatchRouteFeedback(
                requestedSessionId = requestedSessionId,
                requestedShareCode = requestedShareCode,
                selectedTarget = selectedTarget,
                message = liveFollowWatchCommandMessage(result)
            )
        }
    }
}
