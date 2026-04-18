package com.trust3.xcpro.map.ui

import com.trust3.xcpro.livefollow.watch.LiveFollowMapRenderState
import com.trust3.xcpro.livefollow.watch.LiveFollowTaskRenderPolicy
import com.trust3.xcpro.livefollow.watch.LiveFollowWatchUiState
import com.trust3.xcpro.livefollow.model.LiveFollowTaskPoint
import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapLiveFollowRuntimeLayerTest {

    @Test
    fun blockedAmbiguousPolicy_doesNotAttachWatchedTask() {
        val taskAttachmentState = resolveMapLiveFollowTaskAttachmentState(
            uiState = LiveFollowWatchUiState(
                mapRenderState = LiveFollowMapRenderState(
                    taskRenderPolicy = LiveFollowTaskRenderPolicy.BLOCKED_AMBIGUOUS
                )
            ),
        )

        assertFalse(taskAttachmentState.attachTask)
        assertNull(taskAttachmentState.overlayState)
    }

    @Test
    fun readOnlyUnavailablePolicy_doesNotAttachWatchedTask() {
        val taskAttachmentState = resolveMapLiveFollowTaskAttachmentState(
            uiState = LiveFollowWatchUiState(
                mapRenderState = LiveFollowMapRenderState(
                    taskRenderPolicy = LiveFollowTaskRenderPolicy.READ_ONLY_UNAVAILABLE
                )
            ),
        )

        assertFalse(taskAttachmentState.attachTask)
        assertNull(taskAttachmentState.overlayState)
    }

    @Test
    fun availablePolicy_attachesWatchedTaskFromWatchState() {
        val attachmentState = resolveMapLiveFollowTaskAttachmentState(
            uiState = LiveFollowWatchUiState(
                selectedShareCode = "watch123",
                shareCode = "WATCH123",
                mapRenderState = LiveFollowMapRenderState(
                    isVisible = true,
                    shareCode = "watch123",
                    taskRenderPolicy = LiveFollowTaskRenderPolicy.AVAILABLE,
                    taskSnapshot = sampleWatchedTask()
                )
            )
        )

        assertEquals(true, attachmentState.attachTask)
        assertEquals("WATCH123", attachmentState.overlayState?.shareCode)
        assertEquals(2, attachmentState.overlayState?.points?.size)
    }

    @Test
    fun availablePolicy_hidesPreviousWatchedTaskUntilSelectionCatchesUp() {
        val overlayState = resolveMapLiveFollowWatchTaskOverlayState(
            LiveFollowWatchUiState(
                selectedShareCode = "NEW2222",
                shareCode = "NEW2222",
                mapRenderState = LiveFollowMapRenderState(
                    isVisible = true,
                    shareCode = "OLD1111",
                    taskRenderPolicy = LiveFollowTaskRenderPolicy.AVAILABLE,
                    taskSnapshot = sampleWatchedTask()
                )
            )
        )

        assertNull(overlayState)
    }

    @Test
    fun resolvedSelection_buildsWatchedPilotOverlayState_withNormalizedShareCode() {
        val overlayState = resolveMapLiveFollowWatchOverlayState(
            LiveFollowWatchUiState(
                selectedShareCode = " watch123 ",
                shareCode = "watch123",
                mapRenderState = LiveFollowMapRenderState(
                    isVisible = true,
                    shareCode = " watch123 ",
                    latitudeDeg = -33.9,
                    longitudeDeg = 151.2,
                    trackDeg = 182.0
                )
            )
        )

        requireNotNull(overlayState)
        assertEquals("WATCH123", overlayState.shareCode)
        assertEquals(-33.9, overlayState.latitudeDeg, 0.0)
        assertEquals(151.2, overlayState.longitudeDeg, 0.0)
        assertEquals(182.0, requireNotNull(overlayState.trackDeg), 0.0)
    }

    @Test
    fun switchingSelection_hidesPreviousPilotMarkerUntilWatchTargetResolves() {
        val overlayState = resolveMapLiveFollowWatchOverlayState(
            LiveFollowWatchUiState(
                selectedShareCode = "NEW2222",
                shareCode = "NEW2222",
                mapRenderState = LiveFollowMapRenderState(
                    isVisible = true,
                    shareCode = "OLD1111",
                    latitudeDeg = -33.9,
                    longitudeDeg = 151.2,
                    trackDeg = 182.0
                )
            )
        )

        assertNull(overlayState)
    }

    @Test
    fun resolvedSelection_requestsInitialFocusOncePerShareCode() {
        val uiState = LiveFollowWatchUiState(
            selectedShareCode = "WATCH123",
            shareCode = "WATCH123",
            mapRenderState = LiveFollowMapRenderState(
                isVisible = true,
                shareCode = "WATCH123",
                latitudeDeg = -33.9,
                longitudeDeg = 151.2
            )
        )

        val focusTarget = resolveMapLiveFollowFocusTarget(
            uiState = uiState,
            lastFocusedShareCode = null,
            watchedPilotFocusEpoch = 1
        )

        requireNotNull(focusTarget)
        assertEquals("WATCH123", focusTarget.shareCode)
        assertEquals(-33.9, focusTarget.latitudeDeg, 0.0)
        assertEquals(151.2, focusTarget.longitudeDeg, 0.0)
        assertNull(
            resolveMapLiveFollowFocusTarget(
                uiState = uiState,
                lastFocusedShareCode = "WATCH123",
                watchedPilotFocusEpoch = 1
            )
        )
    }

    @Test
    fun activeWatch_keepsWatchedPilotOverlayWhileLocalOwnshipPolicyTurnsOff() {
        val uiState = LiveFollowWatchUiState(
            selectedShareCode = "WATCH123",
            shareCode = "WATCH123",
            mapRenderState = LiveFollowMapRenderState(
                isVisible = true,
                shareCode = "WATCH123",
                latitudeDeg = -33.9,
                longitudeDeg = 151.2
            )
        )

        assertFalse(
            shouldRenderLocalOwnship(
                allowFlightSensorStart = true,
                watchMapRenderState = uiState.mapRenderState
            )
        )
        assertNotNull(resolveMapLiveFollowWatchOverlayState(uiState))
    }

    @Test
    fun focusWaitsForMapReadyUntilSelectionSessionAndCoordinatesHaveCaughtUp() {
        val selectionOnlyState = LiveFollowWatchUiState(
            selectedShareCode = "WATCH123",
            shareCode = "WATCH123"
        )
        val waitingForCoordinatesState = selectionOnlyState.copy(
            mapRenderState = LiveFollowMapRenderState(
                isVisible = true,
                shareCode = "WATCH123"
            )
        )
        val renderReadyState = selectionOnlyState.copy(
            mapRenderState = LiveFollowMapRenderState(
                isVisible = true,
                shareCode = "WATCH123",
                latitudeDeg = -33.9,
                longitudeDeg = 151.2
            )
        )

        assertNull(
            resolveMapLiveFollowFocusTarget(
                uiState = selectionOnlyState,
                lastFocusedShareCode = null,
                watchedPilotFocusEpoch = 0
            )
        )
        assertNull(
            resolveMapLiveFollowFocusTarget(
                uiState = waitingForCoordinatesState,
                lastFocusedShareCode = null,
                watchedPilotFocusEpoch = 0
            )
        )
        assertNull(
            resolveMapLiveFollowFocusTarget(
                uiState = renderReadyState,
                lastFocusedShareCode = null,
                watchedPilotFocusEpoch = 0
            )
        )

        val focusTarget = resolveMapLiveFollowFocusTarget(
            uiState = renderReadyState,
            lastFocusedShareCode = null,
            watchedPilotFocusEpoch = 1
        )

        assertNotNull(focusTarget)
        assertEquals("WATCH123", focusTarget?.shareCode)
        assertNull(
            resolveMapLiveFollowFocusTarget(
                uiState = renderReadyState,
                lastFocusedShareCode = focusTarget?.shareCode,
                watchedPilotFocusEpoch = 1
            )
        )
    }

    private fun sampleWatchedTask(): LiveFollowTaskSnapshot {
        return LiveFollowTaskSnapshot(
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
    }
}
