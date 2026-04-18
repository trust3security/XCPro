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
        diagnostics.recordDisplayFramePreDispatchSuppressed()
        diagnostics.recordDisplayFrameNoOpSkipped()
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
        assertEquals(1L, snapshot.displayFramePreDispatchSuppressedCount)
        assertEquals(1L, snapshot.displayFrameNoOpSkippedCount)
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
        assertTrue(status.contains("display_frame_noop_skips=1"))
        assertTrue(!status.contains("\n"))
    }
}
