package com.example.xcpro.livefollow.friends

import com.example.xcpro.livefollow.data.friends.FriendsFlyingSnapshot
import com.example.xcpro.livefollow.model.LiveFollowActivePilot
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsFlyingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onSheetShown_refreshesOnce() = runTest {
        val useCase: FriendsFlyingUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                FriendsFlyingSnapshot(transportAvailability = liveFollowAvailableTransport())
            )
        )

        val viewModel = FriendsFlyingViewModel(useCase)
        advanceUntilIdle()

        viewModel.onSheetShown()
        viewModel.onSheetShown()
        advanceUntilIdle()

        verify(useCase, times(1)).refresh()
    }

    @Test
    fun selectPilot_emitsShareCodeWatchHandoffEvent() = runTest {
        val useCase: FriendsFlyingUseCase = mock()
        whenever(useCase.state).thenReturn(
            MutableStateFlow(
                FriendsFlyingSnapshot(
                    items = listOf(
                        LiveFollowActivePilot(
                            sessionId = "watch-1",
                            shareCode = "WATCH123",
                            status = "active",
                            displayLabel = "Pilot One",
                            lastPositionWallMs = 100L,
                            latest = null
                        )
                    ),
                    transportAvailability = liveFollowAvailableTransport()
                )
            )
        )

        val viewModel = FriendsFlyingViewModel(useCase)
        advanceUntilIdle()

        val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.selectPilot("watch123")

        assertEquals(
            FriendsFlyingEvent.OpenWatch("WATCH123"),
            eventDeferred.await()
        )
    }
}
