package com.trust3.xcpro.livefollow.friends

import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.livefollow.account.signedOutAccountSnapshot
import com.trust3.xcpro.livefollow.data.following.FollowingLiveSnapshot
import com.trust3.xcpro.livefollow.data.friends.FriendsFlyingSnapshot
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.testing.MainDispatcherRule
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
    fun onSheetShown_refreshesPublicAndFollowingOnce() = runTest {
        val useCase: FriendsFlyingUseCase = mock()
        val clock = FakeClock(wallMs = 1_000L)
        whenever(useCase.publicState).thenReturn(
            MutableStateFlow(
                FriendsFlyingSnapshot(transportAvailability = liveFollowAvailableTransport())
            )
        )
        whenever(useCase.followingState).thenReturn(
            MutableStateFlow(
                FollowingLiveSnapshot(transportAvailability = liveFollowAvailableTransport())
            )
        )
        whenever(useCase.accountState).thenReturn(
            MutableStateFlow(signedOutAccountSnapshot())
        )

        val viewModel = FriendsFlyingViewModel(useCase, clock)
        advanceUntilIdle()

        viewModel.onSheetShown()
        viewModel.onSheetShown()
        advanceUntilIdle()

        verify(useCase, times(1)).refreshPublic()
        verify(useCase, times(1)).refreshFollowing()
    }

    @Test
    fun selectPilot_emitsWatchHandoffEvent() = runTest {
        val useCase: FriendsFlyingUseCase = mock()
        val clock = FakeClock(wallMs = 1_000L)
        whenever(useCase.publicState).thenReturn(
            MutableStateFlow(
                FriendsFlyingSnapshot(transportAvailability = liveFollowAvailableTransport())
            )
        )
        whenever(useCase.followingState).thenReturn(
            MutableStateFlow(
                FollowingLiveSnapshot(transportAvailability = liveFollowAvailableTransport())
            )
        )
        whenever(useCase.accountState).thenReturn(
            MutableStateFlow(signedOutAccountSnapshot())
        )

        val viewModel = FriendsFlyingViewModel(useCase, clock)
        advanceUntilIdle()

        val selection = FriendsFlyingPilotSelection(
            watchKey = "WATCH123",
            watchTargetType = FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE,
            shareCode = "WATCH123",
            displayLabel = "Pilot One",
            statusLabel = "Active",
            altitudeLabel = null,
            speedLabel = null,
            headingLabel = null,
            recencyLabel = "Updated < 1 s ago",
            isStale = false
        )
        val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        viewModel.selectPilot(selection)

        assertEquals(
            FriendsFlyingEvent.OpenWatch(selection),
            eventDeferred.await()
        )
    }
}
