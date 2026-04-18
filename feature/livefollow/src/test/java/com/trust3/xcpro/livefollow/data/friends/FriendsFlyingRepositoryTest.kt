package com.trust3.xcpro.livefollow.data.friends

import com.trust3.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.trust3.xcpro.livefollow.model.LiveFollowActivePilot
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsFlyingRepositoryTest {

    @Test
    fun refresh_sortsActiveBeforeStale_andNewestFirst() = runTest {
        val scope = repoScope()
        try {
            val runtimeSource = FakeOwnshipSnapshotSource()
            val dataSource = FakeActivePilotsDataSource(
                ActivePilotsFetchResult.Success(
                    listOf(
                        pilot(shareCode = "STALE01", status = "stale", lastPositionWallMs = 100L),
                        pilot(shareCode = "ACTIVE1", status = "active", lastPositionWallMs = 200L),
                        pilot(shareCode = "ACTIVE2", status = "active", lastPositionWallMs = 300L)
                    )
                )
            )
            val repository = FriendsFlyingRepository(
                scope = scope,
                runtimeModeSource = runtimeSource,
                dataSource = dataSource
            )

            repository.refresh()
            advanceUntilIdle()

            assertEquals(1, dataSource.fetchCalls)
            assertEquals(
                listOf("ACTIVE2", "ACTIVE1", "STALE01"),
                repository.state.value.items.map { it.shareCode }
            )
            assertTrue(repository.state.value.sideEffectsAllowed)
            assertEquals(liveFollowAvailableTransport(), repository.state.value.transportAvailability)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun replayTransition_clearsLoadedItems_andBlocksFurtherRefresh() = runTest {
        val scope = repoScope()
        try {
            val runtimeSource = FakeOwnshipSnapshotSource()
            val dataSource = FakeActivePilotsDataSource(
                ActivePilotsFetchResult.Success(listOf(pilot()))
            )
            val repository = FriendsFlyingRepository(
                scope = scope,
                runtimeModeSource = runtimeSource,
                dataSource = dataSource
            )

            repository.refresh()
            advanceUntilIdle()
            assertEquals(1, repository.state.value.items.size)

            runtimeSource.mutableRuntimeMode.value = LiveFollowRuntimeMode.REPLAY
            advanceUntilIdle()

            assertTrue(repository.state.value.items.isEmpty())
            assertFalse(repository.state.value.sideEffectsAllowed)
            assertEquals(
                LiveFollowReplayBlockReason.REPLAY_MODE,
                repository.state.value.replayBlockReason
            )

            repository.refresh()
            advanceUntilIdle()

            assertEquals(1, dataSource.fetchCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun refresh_emptySuccess_keepsNeutralBrowseState() = runTest {
        val scope = repoScope()
        try {
            val runtimeSource = FakeOwnshipSnapshotSource()
            val dataSource = FakeActivePilotsDataSource(
                ActivePilotsFetchResult.Success(emptyList())
            )
            val repository = FriendsFlyingRepository(
                scope = scope,
                runtimeModeSource = runtimeSource,
                dataSource = dataSource
            )

            repository.refresh()
            advanceUntilIdle()

            assertEquals(1, dataSource.fetchCalls)
            assertTrue(repository.state.value.items.isEmpty())
            assertEquals(null, repository.state.value.lastError)
            assertTrue(repository.state.value.sideEffectsAllowed)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun pilot(
        shareCode: String = "WATCH123",
        status: String = "active",
        lastPositionWallMs: Long? = 200L
    ): LiveFollowActivePilot {
        return LiveFollowActivePilot(
            sessionId = "watch-1",
            shareCode = shareCode,
            status = status,
            displayLabel = shareCode,
            lastPositionWallMs = lastPositionWallMs,
            latest = null
        )
    }

    private class FakeOwnshipSnapshotSource(
        override val snapshot: StateFlow<com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot?> =
            MutableStateFlow(null)
    ) : LiveOwnshipSnapshotSource {
        val mutableRuntimeMode = MutableStateFlow(LiveFollowRuntimeMode.LIVE)
        override val runtimeMode: StateFlow<LiveFollowRuntimeMode> = mutableRuntimeMode
    }

    private class FakeActivePilotsDataSource(
        private val result: ActivePilotsFetchResult
    ) : ActivePilotsDataSource {
        var fetchCalls: Int = 0

        override suspend fun fetchActivePilots(): ActivePilotsFetchResult {
            fetchCalls += 1
            return result
        }
    }
}
