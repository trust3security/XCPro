package com.trust3.xcpro.livefollow.pilot

import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.trust3.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.trust3.xcpro.livefollow.model.LiveFollowValueQuality
import com.trust3.xcpro.livefollow.model.LiveFollowValueState
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.trust3.xcpro.livefollow.model.liveFollowAvailableTransport
import com.trust3.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.trust3.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.trust3.xcpro.livefollow.state.LiveFollowRuntimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFollowPilotUiStateTest {

    @Test
    fun readyLiveSession_enablesStartSharing() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertTrue(uiState.canStartSharing)
        assertFalse(uiState.canStopSharing)
        assertEquals("Idle", uiState.lifecycleLabel)
        assertEquals(LiveFollowPilotShareIndicatorState.STOPPED, uiState.shareIndicatorState)
    }

    @Test
    fun replayBlockedSession_disablesPilotActions() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.REPLAY_MODE,
                runtimeMode = LiveFollowRuntimeMode.REPLAY
            ),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertFalse(uiState.canStartSharing)
        assertFalse(uiState.canStopSharing)
        assertEquals("Replay Mode", uiState.replayBlockReasonLabel)
        assertEquals("LiveFollow sharing is blocked during replay.", uiState.statusMessage)
    }

    @Test
    fun simulatorBlockedSession_showsSimulatorSpecificStatus() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(
                sideEffectsAllowed = false,
                replayBlockReason = LiveFollowReplayBlockReason.SIMULATOR_SOURCE
            ),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertEquals("Simulator Source", uiState.replayBlockReasonLabel)
        assertEquals(
            "LiveFollow sharing is blocked while a simulator source is active.",
            uiState.statusMessage
        )
    }

    @Test
    fun activePilotSession_enablesStopSharing() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(
                sessionId = "pilot-1",
                shareCode = "SHARE123",
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE
            ),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertFalse(uiState.canStartSharing)
        assertTrue(uiState.canStopSharing)
        assertEquals("pilot-1", uiState.sessionId)
        assertEquals("SHARE123", uiState.shareCode)
        assertTrue(uiState.canCopyShareCode)
        assertEquals(LiveFollowPilotShareIndicatorState.LIVE, uiState.shareIndicatorState)
    }

    @Test
    fun unavailableTransport_disablesStartAndShowsTransportStatus() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(
                transportAvailability = liveFollowUnavailableTransport(
                    "LiveFollow session transport is unavailable in this transport-limited build."
                )
            ),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertFalse(uiState.canStartSharing)
        assertEquals("Unavailable", uiState.sessionTransportLabel)
        assertEquals(
            "LiveFollow session transport is unavailable in this transport-limited build.",
            uiState.statusMessage
        )
    }

    @Test
    fun startingSession_showsStartingIndicator() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(
                lifecycle = LiveFollowSessionLifecycle.STARTING
            ),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertEquals(LiveFollowPilotShareIndicatorState.STARTING, uiState.shareIndicatorState)
    }

    @Test
    fun failedStartAction_showsFailedIndicator() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState(
                commandMessage = "Start failed.",
                lastShareCommandFailed = true
            )
        )

        assertEquals(LiveFollowPilotShareIndicatorState.FAILED, uiState.shareIndicatorState)
        assertEquals("Start failed.", uiState.statusMessage)
    }

    private fun sessionSnapshot(
        sessionId: String? = null,
        shareCode: String? = null,
        role: LiveFollowSessionRole = LiveFollowSessionRole.NONE,
        lifecycle: LiveFollowSessionLifecycle = LiveFollowSessionLifecycle.IDLE,
        runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
        sideEffectsAllowed: Boolean = true,
        replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE,
        transportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport()
    ): LiveFollowSessionSnapshot {
        return LiveFollowSessionSnapshot(
            sessionId = sessionId,
            role = role,
            lifecycle = lifecycle,
            runtimeMode = runtimeMode,
            watchIdentity = null,
            directWatchAuthorized = false,
            transportAvailability = transportAvailability,
            sideEffectsAllowed = sideEffectsAllowed,
            replayBlockReason = replayBlockReason,
            lastError = null,
            shareCode = shareCode
        )
    }

    private fun ownshipSnapshot(): LiveOwnshipSnapshot {
        return LiveOwnshipSnapshot(
            latitudeDeg = -33.9,
            longitudeDeg = 151.2,
            gpsAltitudeMslMeters = 500.0,
            pressureAltitudeMslMeters = 495.0,
            groundSpeedMs = 12.0,
            trackDeg = 180.0,
            verticalSpeedMs = 1.2,
            fixMonoMs = 10_000L,
            fixWallMs = 20_000L,
            positionQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            verticalQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            canonicalIdentity = LiveFollowAircraftIdentity.create(
                type = LiveFollowAircraftIdentityType.FLARM,
                rawValue = "AB12CD",
                verified = true
            ),
            sourceLabel = LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME
        )
    }
}
