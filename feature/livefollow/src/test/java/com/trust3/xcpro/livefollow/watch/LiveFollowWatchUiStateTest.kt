package com.trust3.xcpro.livefollow.watch

import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.trust3.xcpro.livefollow.data.watch.WatchAircraftSnapshot
import com.trust3.xcpro.livefollow.data.watch.WatchTrafficSnapshot
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.trust3.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.trust3.xcpro.livefollow.model.LiveFollowTaskPoint
import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.trust3.xcpro.livefollow.model.LiveFollowSourceType
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.trust3.xcpro.livefollow.liveFollowTaskAttachmentMessage
import com.trust3.xcpro.livefollow.toDisplayLabel
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.trust3.xcpro.livefollow.state.LiveFollowSessionState
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
            LiveFollowSessionState.STALE to Pair("Pilot is stale", LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE),
            LiveFollowSessionState.OFFLINE to Pair("Pilot is unavailable", LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE),
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
        assertEquals("Active", uiState.panelStatusLabel)
        assertEquals(LiveFollowWatchPanelTone.ACTIVE, uiState.panelStatusTone)
        assertEquals("500 m MSL", uiState.panelAltitudeLabel)
        assertEquals("12 m/s", uiState.panelSpeedLabel)
        assertEquals("180 deg", uiState.panelHeadingLabel)
        assertEquals("Updated 5 s ago", uiState.panelFreshnessLabel)
        assertEquals("TARGET1", uiState.mapRenderState.shareCode)
    }

    @Test
    fun directTransportAvailability_isShownFromOwnerState() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(),
            watchSnapshot = watchSnapshot(
                state = LiveFollowSessionState.LIVE_OGN,
                directTransportAvailability = liveFollowUnavailableTransport(
                    "Direct watch transport is unavailable in this transport-limited build."
                )
            ),
            feedback = LiveFollowWatchRouteFeedback(requestedSessionId = "watch-1")
        )

        assertEquals("Unavailable", uiState.directTransportLabel)
        assertEquals(
            "Direct watch transport is unavailable in this transport-limited build.",
            uiState.directTransportMessage
        )
    }

    @Test
    fun liveDirectState_withoutWatchedTask_reportsTaskUnavailable() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(),
            watchSnapshot = watchSnapshot(LiveFollowSessionState.LIVE_DIRECT),
            feedback = LiveFollowWatchRouteFeedback()
        )

        assertEquals("45 m", uiState.panelAglLabel)
        assertEquals(
            LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE,
            uiState.mapRenderState.taskRenderPolicy
        )
        assertEquals(
            "Watched pilot has no shared task right now.",
            liveFollowTaskAttachmentMessage(uiState.mapRenderState.taskRenderPolicy)
        )
    }

    @Test
    fun resolvedWatchState_doesNotBackfillStaleSelectionHintAfterTaskClear() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(shareCode = "WATCH123"),
            watchSnapshot = watchSnapshot(LiveFollowSessionState.LIVE_DIRECT),
            feedback = LiveFollowWatchRouteFeedback(
                requestedShareCode = "WATCH123",
                selectedTarget = LiveFollowWatchSelectionHint(
                    shareCode = "WATCH123",
                    displayLabel = "Pilot One",
                    statusLabel = "Stale",
                    altitudeLabel = "900 m MSL",
                    speedLabel = "15 m/s",
                    headingLabel = "190 deg",
                    recencyLabel = "Updated 2 min ago",
                    isStale = true
                )
            )
        )

        assertEquals("Active", uiState.panelStatusLabel)
        assertEquals(LiveFollowWatchPanelTone.ACTIVE, uiState.panelStatusTone)
        assertEquals("500 m MSL", uiState.panelAltitudeLabel)
        assertEquals("12 m/s", uiState.panelSpeedLabel)
        assertEquals("180 deg", uiState.panelHeadingLabel)
        assertEquals("Updated 5 s ago", uiState.panelFreshnessLabel)
        assertEquals(
            LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE,
            uiState.mapRenderState.taskRenderPolicy
        )
    }

    @Test
    fun liveDirectState_withWatchedTask_marksTaskAsAvailable() {
        val watchedTask = LiveFollowTaskSnapshot(
            taskName = "task-alpha",
            points = listOf(
                LiveFollowTaskPoint(
                    order = 0,
                    latitudeDeg = -33.9,
                    longitudeDeg = 151.2,
                    radiusMeters = 10_000.0,
                    name = "Start",
                    type = "START_LINE"
                ),
                LiveFollowTaskPoint(
                    order = 1,
                    latitudeDeg = -33.8,
                    longitudeDeg = 151.3,
                    radiusMeters = 500.0,
                    name = "TP1",
                    type = "TURN_POINT_CYLINDER"
                )
            )
        )
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(),
            watchSnapshot = watchSnapshot(
                state = LiveFollowSessionState.LIVE_DIRECT,
                task = watchedTask
            ),
            feedback = LiveFollowWatchRouteFeedback()
        )

        assertEquals(LiveFollowTaskRenderPolicy.AVAILABLE, uiState.mapRenderState.taskRenderPolicy)
        assertEquals("task-alpha", uiState.mapRenderState.taskSnapshot?.taskName)
        assertEquals(2, uiState.mapRenderState.taskSnapshot?.points?.size)
        assertEquals(null, liveFollowTaskAttachmentMessage(uiState.mapRenderState.taskRenderPolicy))
    }

    @Test
    fun staleWatchState_marksCompactPanelAsWarning() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(),
            watchSnapshot = watchSnapshot(LiveFollowSessionState.STALE),
            feedback = LiveFollowWatchRouteFeedback()
        )

        assertEquals("Stale", uiState.panelStatusLabel)
        assertEquals(LiveFollowWatchPanelTone.WARNING, uiState.panelStatusTone)
        assertEquals(null, uiState.panelAltitudeLabel)
        assertEquals(null, uiState.panelSpeedLabel)
    }

    @Test
    fun requestedPilotSelection_updatesPanelBeforeJoinStateCatchesUp() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(
                shareCode = "OLD1111",
                role = LiveFollowSessionRole.WATCHER
            ),
            watchSnapshot = watchSnapshot(LiveFollowSessionState.LIVE_OGN),
            feedback = LiveFollowWatchRouteFeedback(
                requestedShareCode = "NEW2222",
                selectedTarget = LiveFollowWatchSelectionHint(
                    shareCode = "NEW2222",
                    displayLabel = "Pilot Two",
                    statusLabel = "Active",
                    altitudeLabel = "900 m MSL",
                    speedLabel = "15 m/s",
                    headingLabel = "190 deg",
                    recencyLabel = "Updated 12 s ago",
                    isStale = false
                ),
                isBusy = true
            )
        )

        assertEquals("NEW2222", uiState.selectedShareCode)
        assertEquals("NEW2222", uiState.shareCode)
        assertEquals("Pilot Two", uiState.aircraftLabel)
        assertEquals("Active", uiState.panelStatusLabel)
        assertEquals("900 m MSL", uiState.panelAltitudeLabel)
        assertEquals(null, uiState.panelAglLabel)
        assertEquals("15 m/s", uiState.panelSpeedLabel)
        assertEquals("Updated 12 s ago", uiState.panelFreshnessLabel)
        assertEquals("190 deg", uiState.panelHeadingLabel)
    }

    @Test
    fun offlineWatchState_surfacesUnavailablePanelMessaging() {
        val uiState = buildLiveFollowWatchUiState(
            session = sessionSnapshot(),
            watchSnapshot = watchSnapshot(LiveFollowSessionState.OFFLINE),
            feedback = LiveFollowWatchRouteFeedback()
        )

        assertEquals("Pilot is unavailable", uiState.headline)
        assertEquals("Unavailable", uiState.panelStatusLabel)
        assertEquals("This pilot is no longer live right now.", uiState.detail)
    }

    private fun sessionSnapshot(
        shareCode: String? = "TARGET1",
        role: LiveFollowSessionRole = LiveFollowSessionRole.WATCHER
    ): LiveFollowSessionSnapshot {
        return LiveFollowSessionSnapshot(
            sessionId = "watch-1",
            role = role,
            lifecycle = LiveFollowSessionLifecycle.ACTIVE,
            runtimeMode = LiveFollowRuntimeMode.LIVE,
            watchIdentity = null,
            directWatchAuthorized = true,
            transportAvailability = liveFollowAvailableTransport(),
            sideEffectsAllowed = true,
            replayBlockReason = LiveFollowReplayBlockReason.NONE,
            lastError = null,
            shareCode = shareCode
        )
    }

    private fun watchSnapshot(
        state: LiveFollowSessionState,
        directTransportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport(),
        task: LiveFollowTaskSnapshot? = null
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
                    aglMeters = if (activeSource == LiveFollowSourceType.DIRECT) 45.0 else null,
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
            directTransportAvailability = directTransportAvailability,
            identityResolution = null,
            task = task
        )
    }
}
