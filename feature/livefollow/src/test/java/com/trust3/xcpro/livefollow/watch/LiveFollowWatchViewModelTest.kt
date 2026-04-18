package com.trust3.xcpro.livefollow.watch

import com.trust3.xcpro.livefollow.data.session.LiveFollowCommandResult
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.trust3.xcpro.livefollow.data.watch.WatchTrafficSnapshot
import com.trust3.xcpro.livefollow.data.watch.stoppedWatchTrafficSnapshot
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.trust3.xcpro.testing.MainDispatcherRule
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
    fun validShareCode_handsOffThroughExistingShareWatchJoin() = runTest {
        val sessionState = MutableStateFlow(sessionSnapshot())
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchShareEntry("watch123")
        advanceUntilIdle()

        verify(useCase).joinWatchSessionByShareCode("WATCH123")
        assertEquals("WATCH123", viewModel.uiState.value.shareCode)
    }

    @Test
    fun selectionHint_updatesUiImmediatelyWhileJoinRuns() = runTest {
        val sessionState = MutableStateFlow(
            sessionSnapshot(
                sessionId = "watch-1",
                shareCode = "OLD1111",
                role = LiveFollowSessionRole.WATCHER
            )
        )
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchShareEntry(
            rawShareCode = "watch123",
            selectionHint = LiveFollowWatchSelectionHint(
                shareCode = "WATCH123",
                displayLabel = "Pilot One",
                statusLabel = "Active",
                altitudeLabel = "510 m MSL",
                speedLabel = "13 m/s",
                headingLabel = "185 deg",
                recencyLabel = "Updated 12 s ago",
                isStale = false
            )
        )
        advanceUntilIdle()

        verify(useCase).joinWatchSessionByShareCode("WATCH123")
        assertEquals("WATCH123", viewModel.uiState.value.selectedShareCode)
        assertEquals("Pilot One", viewModel.uiState.value.aircraftLabel)
        assertEquals("510 m MSL", viewModel.uiState.value.panelAltitudeLabel)
        assertEquals("13 m/s", viewModel.uiState.value.panelSpeedLabel)
        assertEquals("185 deg", viewModel.uiState.value.panelHeadingLabel)
        assertEquals("Updated 12 s ago", viewModel.uiState.value.panelFreshnessLabel)
    }

    @Test
    fun clearWatchTarget_withoutActiveWatch_resetsSelectedPilotState() = runTest {
        val sessionState = MutableStateFlow(sessionSnapshot())
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.handleWatchShareEntry(
            rawShareCode = "watch123",
            selectionHint = LiveFollowWatchSelectionHint(
                shareCode = "WATCH123",
                displayLabel = "Pilot One",
                statusLabel = "Active",
                altitudeLabel = null,
                speedLabel = null,
                headingLabel = null,
                recencyLabel = "Updated 12 s ago",
                isStale = false
            )
        )
        advanceUntilIdle()

        viewModel.clearWatchTarget()
        advanceUntilIdle()

        verify(useCase, never()).stopWatching()
        assertEquals(null, viewModel.uiState.value.selectedShareCode)
        assertEquals(false, viewModel.uiState.value.visible)
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

    @Test
    fun clearWatchTarget_withActiveWatch_usesExistingStopPath() = runTest {
        val sessionState = MutableStateFlow(
            sessionSnapshot(
                sessionId = "watch-3",
                shareCode = "WATCH123",
                role = LiveFollowSessionRole.WATCHER
            )
        )
        val watchState = MutableStateFlow(stoppedWatchTrafficSnapshot())
        val useCase = mockUseCase(sessionState, watchState)
        val viewModel = LiveFollowWatchViewModel(useCase)
        advanceUntilIdle()

        viewModel.clearWatchTarget()
        advanceUntilIdle()

        verify(useCase).stopWatching()
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
        shareCode: String? = null,
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
            lastError = null,
            shareCode = shareCode
        )
    }
}
