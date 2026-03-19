package com.example.xcpro.livefollow.watch

import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.data.watch.WatchAircraftSnapshot
import com.example.xcpro.livefollow.data.watch.WatchTrafficSnapshot
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.example.xcpro.livefollow.model.LiveFollowSourceType
import com.example.xcpro.livefollow.toDisplayLabel
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.example.xcpro.livefollow.state.LiveFollowSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFollowWatchUiStateTest {

    @Test
    fun watchStateMappings_coverRequiredFollowerStates() {
        val expectations = listOf(
            LiveFollowSessionState.WAITING to Pair("Waiting for watch data", LiveFollowTaskRenderPolicy.HIDDEN),
            LiveFollowSessionState.AMBIGUOUS to Pair("Identity is ambiguous", LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS),
            LiveFollowSessionState.LIVE_OGN to Pair("Watching via OGN", LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE),
            LiveFollowSessionState.LIVE_DIRECT to Pair("Watching via direct source", LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE),
            LiveFollowSessionState.STALE to Pair("Watch data is stale", LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE),
            LiveFollowSessionState.OFFLINE to Pair("Watch data is offline", LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE),
            LiveFollowSessionState.STOPPED to Pair("No active watch session", LiveFollowTaskRenderPolicy.HIDDEN)
        )

        expectations.forEach { (state, expectation) ->
            val uiState = buildLiveFollowWatchUiState(
                session = sessionSnapshot(
                    role = if (state == LiveFollowSessionState.STOPPED) {
                        LiveFollowSessionRole.NONE
                    } else {
                        LiveFollowSessionRole.WATCHER
                    }
                ),
                watchSnapshot = watchSnapshot(state),
                feedback = LiveFollowWatchRouteFeedback(
                    requestedSessionId = "watch-1"
                )
            )

            assertEquals(expectation.first, uiState.headline)
            assertEquals(state.name.toDisplayLabel(), uiState.stateLabel)
            assertEquals(expectation.second, uiState.mapRenderState.taskRenderPolicy)
        }
    }

    @Test
    fun activeWatchState_remainsVisibleWithoutAnyOrdinaryOverlayPreferenceInput() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(),
            watchSnapshot = watchSnapshot(LiveFollowSessionState.LIVE_OGN),
            feedback = LiveFollowWatchRouteFeedback()
        )

        assertTrue(uiState.visible)
        assertEquals("Watching via OGN", uiState.headline)
        assertEquals("Ogn", uiState.sourceLabel)
    }

    private fun sessionSnapshot(
        role: LiveFollowSessionRole = LiveFollowSessionRole.WATCHER
    ): LiveFollowSessionSnapshot {
        return LiveFollowSessionSnapshot(
            sessionId = "watch-1",
            role = role,
            lifecycle = LiveFollowSessionLifecycle.ACTIVE,
            runtimeMode = LiveFollowRuntimeMode.LIVE,
            watchIdentity = null,
            directWatchAuthorized = true,
            sideEffectsAllowed = true,
            replayBlockReason = LiveFollowReplayBlockReason.NONE,
            lastError = null
        )
    }

    private fun watchSnapshot(
        state: LiveFollowSessionState
    ): WatchTrafficSnapshot {
        val activeSource = when (state) {
            LiveFollowSessionState.LIVE_OGN -> LiveFollowSourceType.OGN
            LiveFollowSessionState.LIVE_DIRECT -> LiveFollowSourceType.DIRECT
            else -> null
        }
        return WatchTrafficSnapshot(
            sourceState = state,
            activeSource = activeSource,
            aircraft = if (activeSource != null) {
                WatchAircraftSnapshot(
                    latitudeDeg = -33.9,
                    longitudeDeg = 151.2,
                    altitudeMslMeters = 500.0,
                    groundSpeedMs = 12.0,
                    trackDeg = 180.0,
                    verticalSpeedMs = 1.0,
                    fixMonoMs = 10_000L,
                    fixWallMs = 20_000L,
                    canonicalIdentity = LiveFollowAircraftIdentity.create(
                        type = LiveFollowAircraftIdentityType.FLARM,
                        rawValue = "AB12CD",
                        verified = true
                    ),
                    displayLabel = "TARGET-1"
                )
            } else {
                null
            },
            ageMs = 5_000L,
            ognEligibility = LiveFollowSourceEligibility.UNAVAILABLE,
            directEligibility = LiveFollowSourceEligibility.UNAVAILABLE,
            identityResolution = null
        )
    }
}
