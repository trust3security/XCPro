package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRenderSurfaceDiagnosticsTest {

    @Test
    fun snapshot_tracksCountsAndTimestamps() {
        var nowMonoMs = 100L
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { nowMonoMs })

        diagnostics.recordRepaintRequest(forceImmediate = false)
        nowMonoMs = 101L
        diagnostics.recordRepaintRequest(forceImmediate = true)
        nowMonoMs = 102L
        diagnostics.recordRepaintDispatch()
        nowMonoMs = 103L
        diagnostics.recordRenderFrameCallback()
        diagnostics.recordPostedDispatchScheduled()
        diagnostics.recordPostedDispatchDropped()
        diagnostics.recordImmediateDispatch()
        diagnostics.recordPendingDispatchCleared()
        nowMonoMs = 104L
        diagnostics.recordRenderFrameDelivered()
        nowMonoMs = 105L
        diagnostics.recordFrameRendered()
        diagnostics.recordDisplayFrameDispatchAllowed(
            MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.ACTIVE_WINDOW
        )
        diagnostics.recordDisplayFramePreDispatchSuppressed(
            MapRenderSurfaceDiagnostics.DisplayFramePreDispatchSuppressionReason.ACTIVITY_EXPIRED
        )
        diagnostics.recordDisplayFrameRenderSkipped(
            MapRenderSurfaceDiagnostics.DisplayFrameRenderSkipReason.NO_POSE
        )
        diagnostics.recordDisplayFrameNoOpSkipped()
        diagnostics.recordDisplayPoseRenderApplied()
        nowMonoMs = 106L
        diagnostics.recordLifecycleResumeForcedFrame()
        nowMonoMs = 107L
        diagnostics.recordMapViewAttached(swapped = false)
        nowMonoMs = 108L
        diagnostics.recordMapViewAttached(swapped = true)
        nowMonoMs = 109L
        diagnostics.recordMapViewCleared()

        val snapshot = diagnostics.snapshot()

        assertEquals(2L, snapshot.repaintRequestCount)
        assertEquals(1L, snapshot.forcedImmediateRepaintRequestCount)
        assertEquals(1L, snapshot.repaintDispatchCount)
        assertEquals(1L, snapshot.renderFrameCallbackCount)
        assertEquals(1L, snapshot.postedDispatchScheduledCount)
        assertEquals(1L, snapshot.postedDispatchDroppedCount)
        assertEquals(1L, snapshot.immediateDispatchCount)
        assertEquals(1L, snapshot.pendingDispatchClearedCount)
        assertEquals(1L, snapshot.renderFrameDeliveredCount)
        assertEquals(1L, snapshot.frameRenderedCount)
        assertEquals(1L, snapshot.displayFrameDispatchAllowedCount)
        assertEquals(1L, snapshot.displayFramePreDispatchSuppressedCount)
        assertEquals(2L, snapshot.displayFrameRenderSkippedCount)
        assertEquals(1L, snapshot.displayFrameNoOpSkippedCount)
        assertEquals(1L, snapshot.displayPoseRenderAppliedCount)
        assertEquals(1L, snapshot.lifecycleResumeForcedFrameCount)
        assertEquals(2L, snapshot.mapViewAttachCount)
        assertEquals(1L, snapshot.mapViewSwapCount)
        assertEquals(1L, snapshot.mapViewClearCount)
        assertEquals(101L, snapshot.lastRepaintRequestMonoMs)
        assertEquals(102L, snapshot.lastRepaintDispatchMonoMs)
        assertEquals(103L, snapshot.lastRenderFrameCallbackMonoMs)
        assertEquals(104L, snapshot.lastRenderFrameDeliveredMonoMs)
        assertEquals(105L, snapshot.lastFrameRenderedMonoMs)
        assertEquals(106L, snapshot.lastLifecycleResumeForcedFrameMonoMs)
        assertEquals(108L, snapshot.lastMapViewAttachMonoMs)
        assertEquals(109L, snapshot.lastMapViewClearMonoMs)
        assertEquals(
            1L,
            snapshot.displayFrameDispatchAllowedReasons[
                MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.ACTIVE_WINDOW
            ]
        )
        assertEquals(
            1L,
            snapshot.displayFramePreDispatchSuppressionReasons[
                MapRenderSurfaceDiagnostics.DisplayFramePreDispatchSuppressionReason.ACTIVITY_EXPIRED
            ]
        )
        assertEquals(
            1L,
            snapshot.displayFrameRenderSkipReasons[
                MapRenderSurfaceDiagnostics.DisplayFrameRenderSkipReason.NO_POSE
            ]
        )
        assertEquals(
            1L,
            snapshot.displayFrameRenderSkipReasons[
                MapRenderSurfaceDiagnostics.DisplayFrameRenderSkipReason.NOOP_DIFF
            ]
        )
    }

    @Test
    fun buildStatus_includesKeyCounters() {
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 42L })

        diagnostics.recordRepaintRequest(forceImmediate = true)
        diagnostics.recordRepaintDispatch()
        diagnostics.recordMapViewAttached(swapped = false)

        val status = diagnostics.buildStatus()

        assertTrue(status.contains("MapRenderSurfaceDiagnostics Status:"))
        assertTrue(status.contains("Repaint Requests: 1"))
        assertTrue(status.contains("Repaint Dispatches: 1"))
        assertTrue(status.contains("MapView Attach Count: 1"))
    }

    @Test
    fun buildCompactStatus_isSingleLineAndHarvestable() {
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 84L })

        diagnostics.recordRepaintRequest(forceImmediate = true)
        diagnostics.recordRepaintDispatch()
        diagnostics.recordRenderFrameCallback()
        diagnostics.recordFrameRendered()
        diagnostics.recordDisplayFramePreDispatchSuppressed()
        diagnostics.recordDisplayFrameNoOpSkipped()

        val status = diagnostics.buildCompactStatus(reason = "on_stop")

        assertTrue(status.startsWith("${MapRenderSurfaceDiagnostics.LOG_TOKEN} reason=on_stop"))
        assertTrue(status.contains("repaint_requests=1"))
        assertTrue(status.contains("forced_immediate_repaint_requests=1"))
        assertTrue(status.contains("render_frame_callbacks=1"))
        assertTrue(status.contains("frames_rendered=1"))
        assertTrue(status.contains("display_frame_pre_dispatch_suppressed=1"))
        assertTrue(status.contains("display_frame_pre_dispatch_suppression_reasons="))
        assertTrue(status.contains("display_frame_render_skip_reasons="))
        assertTrue(status.contains("display_frame_noop_skips=1"))
        assertTrue(!status.contains("\n"))
    }

    @Test
    fun snapshot_tracksRawFixInputsByTimeBase() {
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 1_000L })

        diagnostics.recordRawFixInput(DisplayClock.TimeBase.MONOTONIC, timestampMs = 1_000L)
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.MONOTONIC, timestampMs = 1_200L)
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.MONOTONIC, timestampMs = 1_250L)
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.WALL, timestampMs = 10_000L)
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.WALL, timestampMs = 10_060L)
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.REPLAY, timestampMs = 20_000L)
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.REPLAY, timestampMs = 20_100L)

        val snapshot = diagnostics.snapshot()
        assertEquals(3L, snapshot.monotonicRawFixInputCount)
        assertEquals(2L, snapshot.wallRawFixInputCount)
        assertEquals(2L, snapshot.replayRawFixInputCount)
        assertEquals(1L, snapshot.liveRawFixInputIntervalBuckets.le50Ms)
        assertEquals(1L, snapshot.liveRawFixInputIntervalBuckets.le200Ms)
        assertEquals(1L, snapshot.wallRawFixInputIntervalBuckets.le100Ms)
        assertEquals(1L, snapshot.replayRawFixInputIntervalBuckets.le100Ms)
        assertEquals(1_250L, snapshot.lastMonotonicRawFixTimestampMs)
        assertEquals(10_060L, snapshot.lastWallRawFixTimestampMs)
        assertEquals(20_100L, snapshot.lastReplayRawFixTimestampMs)
    }

    @Test
    fun snapshot_tracksFrameGapBuckets() {
        var nowMonoMs = 100L
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { nowMonoMs })

        diagnostics.recordRenderFrameDelivered()
        nowMonoMs = 125L
        diagnostics.recordRenderFrameDelivered()
        nowMonoMs = 180L
        diagnostics.recordRenderFrameDelivered()

        nowMonoMs = 200L
        diagnostics.recordFrameRendered()
        nowMonoMs = 250L
        diagnostics.recordFrameRendered()

        val snapshot = diagnostics.snapshot()
        assertEquals(1L, snapshot.renderFrameDeliveredIntervalBuckets.le25Ms)
        assertEquals(1L, snapshot.renderFrameDeliveredIntervalBuckets.le100Ms)
        assertEquals(1L, snapshot.frameRenderedIntervalBuckets.le50Ms)
    }

    @Test
    fun reset_clearsRunScopedCounters() {
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 1_000L })

        diagnostics.recordDisplayFrameDispatchAllowed(
            MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.CONFIG_CHANGED
        )
        diagnostics.recordRawFixInput(DisplayClock.TimeBase.REPLAY, timestampMs = 2_000L)
        diagnostics.recordDisplayPoseRenderApplied()

        diagnostics.reset()

        val snapshot = diagnostics.snapshot()
        assertEquals(0L, snapshot.displayFrameDispatchAllowedCount)
        assertEquals(0L, snapshot.replayRawFixInputCount)
        assertEquals(0L, snapshot.displayPoseRenderAppliedCount)
        assertEquals(
            0L,
            snapshot.displayFrameDispatchAllowedReasons[
                MapRenderSurfaceDiagnostics.DisplayFrameDispatchReason.CONFIG_CHANGED
            ]
        )
    }
}
