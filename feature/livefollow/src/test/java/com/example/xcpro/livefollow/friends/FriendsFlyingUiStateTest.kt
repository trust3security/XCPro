package com.example.xcpro.livefollow.friends

import com.example.xcpro.livefollow.data.friends.FriendsFlyingSnapshot
import com.example.xcpro.livefollow.model.LiveFollowActivePilot
import com.example.xcpro.livefollow.model.LiveFollowActivePilotPoint
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsFlyingUiStateTest {

    @Test
    fun buildFriendsFlyingUiState_mapsPilotsIntoBottomSheetRows() {
        val uiState = buildFriendsFlyingUiState(
            FriendsFlyingSnapshot(
                items = listOf(
                    LiveFollowActivePilot(
                        sessionId = "watch-1",
                        shareCode = "WATCH123",
                        status = "active",
                        displayLabel = "Pilot One",
                        lastPositionWallMs = 200L,
                        latest = LiveFollowActivePilotPoint(
                            latitudeDeg = -33.91,
                            longitudeDeg = 151.21,
                            altitudeMslMeters = 510.0,
                            groundSpeedMs = 13.0,
                            headingDeg = 185.0,
                            fixWallMs = 200L
                        )
                    )
                ),
                transportAvailability = liveFollowAvailableTransport()
            )
        )

        assertEquals("Friends Flying", uiState.title)
        assertEquals(1, uiState.pilots.size)
        assertEquals("Pilot One", uiState.pilots[0].displayLabel)
        assertEquals("Active", uiState.pilots[0].statusLabel)
        assertEquals("510 m MSL / 13 m/s / 185 deg", uiState.pilots[0].summary)
        assertEquals("-33.9100, 151.2100", uiState.pilots[0].latestSampleLabel)
        assertEquals("Tap a pilot to open the existing watch flow.", uiState.message)
        assertTrue(uiState.canRefresh)
    }

    @Test
    fun buildFriendsFlyingUiState_surfacesEmptyListState() {
        val uiState = buildFriendsFlyingUiState(
            FriendsFlyingSnapshot(
                items = emptyList(),
                transportAvailability = liveFollowAvailableTransport()
            )
        )

        assertTrue(uiState.pilots.isEmpty())
        assertEquals("No public pilots are flying right now.", uiState.message)
        assertTrue(uiState.canRefresh)
    }

    @Test
    fun buildFriendsFlyingUiState_surfacesReplayBlockedState() {
        val uiState = buildFriendsFlyingUiState(
            FriendsFlyingSnapshot(
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE,
                transportAvailability = liveFollowAvailableTransport()
            )
        )

        assertTrue(uiState.pilots.isEmpty())
        assertEquals("Friends Flying is unavailable during replay.", uiState.message)
        assertFalse(uiState.canRefresh)
    }
}
