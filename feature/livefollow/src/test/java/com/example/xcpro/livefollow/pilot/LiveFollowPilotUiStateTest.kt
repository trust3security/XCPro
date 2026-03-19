package com.example.xcpro.livefollow.pilot

import com.example.xcpro.livefollow.data.session.LiveFollowSessionLifecycle
import com.example.xcpro.livefollow.data.session.LiveFollowSessionRole
import com.example.xcpro.livefollow.data.session.LiveFollowSessionSnapshot
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowValueQuality
import com.example.xcpro.livefollow.model.LiveFollowValueState
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
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
    fun activePilotSession_enablesStopSharing() {
        val uiState = buildLiveFollowPilotUiState(
            session = sessionSnapshot(
                sessionId = "pilot-1",
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE
            ),
            ownshipSnapshot = ownshipSnapshot(),
            actionState = LiveFollowPilotActionState()
        )

        assertFalse(uiState.canStartSharing)
        assertTrue(uiState.canStopSharing)
        assertEquals("pilot-1", uiState.sessionId)
    }

    private fun sessionSnapshot(
        sessionId: String? = null,
        role: LiveFollowSessionRole = LiveFollowSessionRole.NONE,
        lifecycle: LiveFollowSessionLifecycle = LiveFollowSessionLifecycle.IDLE,
        runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
        sideEffectsAllowed: Boolean = true,
        replayBlockReason: LiveFollowReplayBlockReason = LiveFollowReplayBlockReason.NONE
    ): LiveFollowSessionSnapshot {
        return LiveFollowSessionSnapshot(
            sessionId = sessionId,
            role = role,
            lifecycle = lifecycle,
            runtimeMode = runtimeMode,
            watchIdentity = null,
            directWatchAuthorized = false,
            sideEffectsAllowed = sideEffectsAllowed,
            replayBlockReason = replayBlockReason,
            lastError = null
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
