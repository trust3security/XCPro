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
import com.example.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import com.example.xcpro.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LiveFollowPilotViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun explicitTransportAvailability_disablesStartWithoutUiInference() = runTest {
        val sessionState = MutableStateFlow(
            LiveFollowSessionSnapshot(
                sessionId = null,
                role = LiveFollowSessionRole.NONE,
                lifecycle = LiveFollowSessionLifecycle.IDLE,
                runtimeMode = LiveFollowRuntimeMode.LIVE,
                watchIdentity = null,
                directWatchAuthorized = false,
                transportAvailability = liveFollowUnavailableTransport(
                    "LiveFollow session transport is unavailable in this transport-limited build."
                ),
                sideEffectsAllowed = true,
                replayBlockReason = LiveFollowReplayBlockReason.NONE,
                lastError = null
            )
        )
        val ownshipSnapshot = MutableStateFlow(sampleOwnshipSnapshot())
        val useCase: LiveFollowPilotUseCase = mock()
        whenever(useCase.sessionState).thenReturn(sessionState)
        whenever(useCase.ownshipSnapshot).thenReturn(ownshipSnapshot)

        val viewModel = LiveFollowPilotViewModel(useCase)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canStartSharing)
        assertEquals("Unavailable", viewModel.uiState.value.sessionTransportLabel)
        assertEquals(
            "LiveFollow session transport is unavailable in this transport-limited build.",
            viewModel.uiState.value.statusMessage
        )
    }

    private fun sampleOwnshipSnapshot(): LiveOwnshipSnapshot {
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
