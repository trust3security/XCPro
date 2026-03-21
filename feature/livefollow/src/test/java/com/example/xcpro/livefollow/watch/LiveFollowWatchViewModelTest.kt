package com.example.xcpro.livefollow.watch

import com.example.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.watch.WatchTrafficSnapshot
import com.example.xcpro.livefollow.data.watch.stoppedWatchTrafficSnapshot
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveFollowWatchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalidBlankSessionId_doesNotCallJoin() = runTest {
        val sessionState = MutableStateFlow(sessionSnapshot())
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchEntry("   ")
        advanceUntilIdle()

        verify(useCase, never()).joinWatchSession(any())
        assertEquals("Invalid LiveFollow session id.", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun sameSessionReentry_doesNotDuplicateJoinOrLeave() = runTest {
        val sessionState = MutableStateFlow(
            sessionSnapshot(
                sessionId = "watch-1",
                role = LiveFollowSessionRole.WATCHER
            )
        )
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchEntry("watch-1")
        advanceUntilIdle()

        verify(useCase, never()).joinWatchSession(any())
        verify(useCase, never()).stopWatching()
        assertEquals("watch-1", viewModel.uiState.value.sessionId)
    }

    @Test
    fun replayBlockedJoin_isSurfacedThroughViewModel() = runTest {
        val sessionState = MutableStateFlow(
            sessionSnapshot(
                role = LiveFollowSessionRole.NONE,
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE,
                runtimeMode = LiveFollowRuntimeMode.REPLAY
            )
        )
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState).also {
            whenever(it.joinWatchSession("watch-2")).thenReturn(
                LiveFollowCommandResult.Rejected(LiveFollowReplayBlockReason.REPLAY_MODE)
            )
        }
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchEntry("watch-2")
        advanceUntilIdle()

        verify(useCase).joinWatchSession("watch-2")
        assertEquals("Replay Mode", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun invalidShareCode_doesNotCallShareJoin() = runTest {
        val sessionState = MutableStateFlow(sessionSnapshot())
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchShareEntry("bad")
        advanceUntilIdle()

        verify(useCase, never()).joinWatchSessionByShareCode(any())
        assertEquals("Invalid LiveFollow share code.", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun stopWatching_onlyCallsLeaveWhenExplicitlyRequested() = runTest {
        val sessionState = MutableStateFlow(
            sessionSnapshot(
                sessionId = "watch-3",
                role = LiveFollowSessionRole.WATCHER,
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE,
                runtimeMode = LiveFollowRuntimeMode.REPLAY
            )
        )
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState).also {
            whenever(it.stopWatching()).thenReturn(
                LiveFollowCommandResult.Rejected(LiveFollowReplayBlockReason.REPLAY_MODE)
            )
        }
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchEntry("watch-3")
        advanceUntilIdle()
        verify(useCase, never()).stopWatching()

        viewModel.stopWatching()
        advanceUntilIdle()

        verify(useCase).stopWatching()
        assertEquals("Replay Mode", viewModel.uiState.value.feedbackMessage)
    }

    private suspend fun mockUseCase(
        sessionState: MutableStateFlow<LiveFollowSessionSnapshot>,
        watchState: MutableStateFlow<WatchTrafficSnapshot>
    ): LiveFollowWatchUseCase {
        val useCase: LiveFollowWatchUseCase = mock()
        whenever(useCase.sessionState).thenReturn(sessionState)
        whenever(useCase.watchState).thenReturn(watchState)
        whenever(useCase.joinWatchSession(any())).thenReturn(LiveFollowCommandResult.Success)
        whenever(useCase.joinWatchSessionByShareCode(any())).thenReturn(LiveFollowCommandResult.Success)
        whenever(useCase.stopWatching()).thenReturn(LiveFollowCommandResult.Success)
        return useCase
    }

    private fun sessionSnapshot(
        sessionId: String? = null,
        role: LiveFollowSessionRole = LiveFollowSessionRole.NONE,
        sideEffectsAllowed: Boolean = true,
        replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE,
        runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE
    ): LiveFollowSessionSnapshot {
        return LiveFollowSessionSnapshot(
            sessionId = sessionId,
            role = role,
            lifecycle = if (role == LiveFollowSessionRole.WATCHER) {
                LiveFollowSessionLifecycle.ACTIVE
            } else {
                LiveFollowSessionLifecycle.IDLE
            },
            runtimeMode = runtimeMode,
            watchIdentity = null,
            directWatchAuthorized = false,
            transportAvailability = liveFollowAvailableTransport(),
            sideEffectsAllowed = sideEffectsAllowed,
            replayBlockReason = replayBlockReason,
            lastError = null
        )
    }
}
