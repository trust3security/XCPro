package com.trust3.xcpro.livefollow.friends

import com.trust3.xcpro.livefollow.account.signedInAccountSnapshot
import com.trust3.xcpro.livefollow.account.signedOutAccountSnapshot
import com.trust3.xcpro.livefollow.data.following.FollowingLiveSnapshot
import com.trust3.xcpro.livefollow.data.friends.FriendsFlyingSnapshot
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionVisibility
import com.trust3.xcpro.livefollow.model.LiveFollowActivePilot
import com.trust3.xcpro.livefollow.model.LiveFollowActivePilotPoint
import com.trust3.xcpro.livefollow.model.LiveFollowFollowingPilot
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsFlyingUiStateTest {

    @Test
    fun buildFriendsFlyingUiState_mapsPublicPilotsIntoPublicTabRows() {
        val uiState = buildFriendsFlyingUiState(
            publicSnapshot = FriendsFlyingSnapshot(
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
            ),
            nowWallMs = 12_200L
        )

        val row = uiState.publicTab.pilots.single()
        assertEquals("Friends Flying", uiState.title)
        assertEquals("Public 1 / Following 0", uiState.pilotCountLabel)
        assertEquals(FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE, row.watchTargetType)
        assertEquals("WATCH123", row.watchKey)
        assertEquals("Pilot One", row.displayLabel)
        assertEquals("Active", row.statusLabel)
        assertEquals("510 m MSL", row.altitudeLabel)
        assertEquals("Updated 12 s ago", row.recencyLabel)
        assertEquals("13 m/s", row.speedLabel)
        assertEquals("185 deg", row.headingLabel)
        assertEquals("13 m/s / 185 deg", row.detailLabel)
        assertFalse(row.isStale)
        assertEquals("Tap a public pilot to watch live.", uiState.publicTab.message)
        assertTrue(uiState.publicTab.canRefresh)
        assertFalse(uiState.publicTab.isError)
    }

    @Test
    fun buildFriendsFlyingUiState_promptsForSignInOnFollowingTabWhenSignedOut() {
        val uiState = buildFriendsFlyingUiState(
            publicSnapshot = FriendsFlyingSnapshot(
                transportAvailability = liveFollowAvailableTransport()
            ),
            followingSnapshot = FollowingLiveSnapshot(
                signInRequired = true,
                transportAvailability = liveFollowAvailableTransport()
            ),
            accountSnapshot = signedOutAccountSnapshot(),
            nowWallMs = 0L
        )

        assertTrue(uiState.followingTab.pilots.isEmpty())
        assertTrue(uiState.followingTab.signInRequired)
        assertEquals(
            "Sign in to see pilots you follow who are live.",
            uiState.followingTab.message
        )
        assertFalse(uiState.followingTab.canRefresh)
    }

    @Test
    fun buildFriendsFlyingUiState_mapsFollowingPilotsIntoAuthorizedRows() {
        val uiState = buildFriendsFlyingUiState(
            publicSnapshot = FriendsFlyingSnapshot(
                transportAvailability = liveFollowAvailableTransport()
            ),
            followingSnapshot = FollowingLiveSnapshot(
                items = listOf(
                    LiveFollowFollowingPilot(
                        sessionId = "follow-1",
                        userId = "user-1",
                        visibility = LiveFollowSessionVisibility.FOLLOWERS,
                        shareCode = null,
                        status = "active",
                        displayLabel = "Pilot Two",
                        lastPositionWallMs = 10_000L,
                        latest = LiveFollowActivePilotPoint(
                            latitudeDeg = -33.91,
                            longitudeDeg = 151.21,
                            altitudeMslMeters = 510.0,
                            groundSpeedMs = 13.0,
                            headingDeg = 185.0,
                            fixWallMs = 10_000L
                        )
                    )
                ),
                isSignedIn = true,
                signInRequired = false,
                transportAvailability = liveFollowAvailableTransport()
            ),
            accountSnapshot = signedInAccountSnapshot(),
            nowWallMs = 22_000L
        )

        val row = uiState.followingTab.pilots.single()
        assertEquals("Public 0 / Following 1", uiState.pilotCountLabel)
        assertEquals(
            FriendsFlyingWatchTargetType.AUTHENTICATED_SESSION_ID,
            row.watchTargetType
        )
        assertEquals("follow-1", row.watchKey)
        assertEquals("follow-1", row.sessionId)
        assertEquals("Pilot Two", row.displayLabel)
        assertEquals("Followers / 13 m/s / 185 deg", row.detailLabel)
        assertEquals(
            "Tap a followed pilot to open authorized live watch.",
            uiState.followingTab.message
        )
        assertTrue(uiState.followingTab.canRefresh)
        assertFalse(uiState.followingTab.signInRequired)
    }

    @Test
    fun buildFriendsFlyingUiState_surfacesReplayBlockedPublicTabState() {
        val uiState = buildFriendsFlyingUiState(
            publicSnapshot = FriendsFlyingSnapshot(
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE,
                transportAvailability = liveFollowAvailableTransport()
            ),
            nowWallMs = 0L
        )

        assertTrue(uiState.publicTab.pilots.isEmpty())
        assertEquals(
            "Friends Flying is unavailable during replay.",
            uiState.publicTab.message
        )
        assertFalse(uiState.publicTab.canRefresh)
    }

    @Test
    fun filterFriendsFlyingPilots_matchesDisplayShareAndSessionIds() {
        val pilots = listOf(
            FriendsFlyingPilotRowUiModel(
                watchKey = "WATCH123",
                watchTargetType = FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE,
                sessionId = "public-session",
                shareCode = "WATCH123",
                displayLabel = "Pilot One",
                statusLabel = "Active",
                altitudeLabel = null,
                recencyLabel = null,
                speedLabel = null,
                headingLabel = null,
                detailLabel = null,
                isStale = false
            ),
            FriendsFlyingPilotRowUiModel(
                watchKey = "auth-session",
                watchTargetType = FriendsFlyingWatchTargetType.AUTHENTICATED_SESSION_ID,
                sessionId = "auth-session",
                shareCode = null,
                displayLabel = "Pilot Two",
                statusLabel = "Active",
                altitudeLabel = null,
                recencyLabel = null,
                speedLabel = null,
                headingLabel = null,
                detailLabel = null,
                isStale = false
            )
        )

        assertEquals(
            listOf("Pilot One"),
            filterFriendsFlyingPilots(pilots, "pilot one").map { it.displayLabel }
        )
        assertEquals(
            listOf("WATCH123"),
            filterFriendsFlyingPilots(pilots, "watch").mapNotNull { it.shareCode }
        )
        assertEquals(
            listOf("auth-session"),
            filterFriendsFlyingPilots(pilots, "auth-session").mapNotNull { it.sessionId }
        )
    }
}
