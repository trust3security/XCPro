package com.example.xcpro.map

import com.example.xcpro.core.time.TimeBridge

/**
 * Debug-only render-surface diagnostics for the map host/render handoff.
 *
 * This is an injected runtime owner, not a singleton. It tracks bounded
 * counters and monotonic timestamps only and must not become application state.
 */
class MapRenderSurfaceDiagnostics(
    private val nowMonoMs: () -> Long = TimeBridge::nowMonoMs
) {
    companion object {
        const val LOG_TOKEN: String = "MAP_RENDER_SURFACE_DIAGNOSTICS"
    }

    private var repaintRequestCount: Long = 0L
    private var forcedImmediateRepaintRequestCount: Long = 0L
    private var repaintDispatchCount: Long = 0L
    private var renderFrameCallbackCount: Long = 0L
    private var postedDispatchScheduledCount: Long = 0L
    private var postedDispatchDroppedCount: Long = 0L
    private var immediateDispatchCount: Long = 0L
    private var pendingDispatchClearedCount: Long = 0L
    private var renderFrameDeliveredCount: Long = 0L
    private var frameRenderedCount: Long = 0L
    private var displayFramePreDispatchSuppressedCount: Long = 0L
    private var displayFrameNoOpSkippedCount: Long = 0L
    private var lifecycleResumeForcedFrameCount: Long = 0L
    private var mapViewAttachCount: Long = 0L
    private var mapViewClearCount: Long = 0L
    private var mapViewSwapCount: Long = 0L

    private var lastRepaintRequestMonoMs: Long? = null
    private var lastRepaintDispatchMonoMs: Long? = null
    private var lastRenderFrameCallbackMonoMs: Long? = null
    private var lastRenderFrameDeliveredMonoMs: Long? = null
    private var lastFrameRenderedMonoMs: Long? = null
    private var lastLifecycleResumeForcedFrameMonoMs: Long? = null
    private var lastMapViewAttachMonoMs: Long? = null
    private var lastMapViewClearMonoMs: Long? = null

    @Synchronized
    fun recordRepaintRequest(forceImmediate: Boolean) {
        repaintRequestCount += 1L
        if (forceImmediate) {
            forcedImmediateRepaintRequestCount += 1L
        }
        lastRepaintRequestMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordRepaintDispatch() {
        repaintDispatchCount += 1L
        lastRepaintDispatchMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordRenderFrameCallback() {
        renderFrameCallbackCount += 1L
        lastRenderFrameCallbackMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordPostedDispatchScheduled() {
        postedDispatchScheduledCount += 1L
    }

    @Synchronized
    fun recordPostedDispatchDropped() {
        postedDispatchDroppedCount += 1L
    }

    @Synchronized
    fun recordImmediateDispatch() {
        immediateDispatchCount += 1L
    }

    @Synchronized
    fun recordPendingDispatchCleared() {
        pendingDispatchClearedCount += 1L
    }

    @Synchronized
    fun recordRenderFrameDelivered() {
        renderFrameDeliveredCount += 1L
        lastRenderFrameDeliveredMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordFrameRendered() {
        frameRenderedCount += 1L
        lastFrameRenderedMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordDisplayFramePreDispatchSuppressed() {
        displayFramePreDispatchSuppressedCount += 1L
    }

    @Synchronized
    fun recordDisplayFrameNoOpSkipped() {
        displayFrameNoOpSkippedCount += 1L
    }

    @Synchronized
    fun recordLifecycleResumeForcedFrame() {
        lifecycleResumeForcedFrameCount += 1L
        lastLifecycleResumeForcedFrameMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordMapViewAttached(swapped: Boolean) {
        mapViewAttachCount += 1L
        if (swapped) {
            mapViewSwapCount += 1L
        }
        lastMapViewAttachMonoMs = nowMonoMs()
    }

    @Synchronized
    fun recordMapViewCleared() {
        mapViewClearCount += 1L
        lastMapViewClearMonoMs = nowMonoMs()
    }

    @Synchronized
    fun snapshot(): Snapshot =
        Snapshot(
            repaintRequestCount = repaintRequestCount,
            forcedImmediateRepaintRequestCount = forcedImmediateRepaintRequestCount,
            repaintDispatchCount = repaintDispatchCount,
            renderFrameCallbackCount = renderFrameCallbackCount,
            postedDispatchScheduledCount = postedDispatchScheduledCount,
            postedDispatchDroppedCount = postedDispatchDroppedCount,
            immediateDispatchCount = immediateDispatchCount,
            pendingDispatchClearedCount = pendingDispatchClearedCount,
            renderFrameDeliveredCount = renderFrameDeliveredCount,
            frameRenderedCount = frameRenderedCount,
            displayFramePreDispatchSuppressedCount = displayFramePreDispatchSuppressedCount,
            displayFrameNoOpSkippedCount = displayFrameNoOpSkippedCount,
            lifecycleResumeForcedFrameCount = lifecycleResumeForcedFrameCount,
            mapViewAttachCount = mapViewAttachCount,
            mapViewClearCount = mapViewClearCount,
            mapViewSwapCount = mapViewSwapCount,
            lastRepaintRequestMonoMs = lastRepaintRequestMonoMs,
            lastRepaintDispatchMonoMs = lastRepaintDispatchMonoMs,
            lastRenderFrameCallbackMonoMs = lastRenderFrameCallbackMonoMs,
            lastRenderFrameDeliveredMonoMs = lastRenderFrameDeliveredMonoMs,
            lastFrameRenderedMonoMs = lastFrameRenderedMonoMs,
            lastLifecycleResumeForcedFrameMonoMs = lastLifecycleResumeForcedFrameMonoMs,
            lastMapViewAttachMonoMs = lastMapViewAttachMonoMs,
            lastMapViewClearMonoMs = lastMapViewClearMonoMs
        )

    fun buildStatus(header: String = "MapRenderSurfaceDiagnostics Status"): String {
        val snapshot = snapshot()
        return buildString {
            append(header)
            append(":\n")
            append("- Repaint Requests: ${snapshot.repaintRequestCount}\n")
            append("- Forced Immediate Repaint Requests: ${snapshot.forcedImmediateRepaintRequestCount}\n")
            append("- Repaint Dispatches: ${snapshot.repaintDispatchCount}\n")
            append("- Render Frame Callbacks: ${snapshot.renderFrameCallbackCount}\n")
            append("- Posted Dispatch Scheduled: ${snapshot.postedDispatchScheduledCount}\n")
            append("- Posted Dispatch Dropped: ${snapshot.postedDispatchDroppedCount}\n")
            append("- Immediate Dispatches: ${snapshot.immediateDispatchCount}\n")
            append("- Pending Dispatch Cleared: ${snapshot.pendingDispatchClearedCount}\n")
            append("- Render Frames Delivered: ${snapshot.renderFrameDeliveredCount}\n")
            append("- Frames Rendered: ${snapshot.frameRenderedCount}\n")
            append("- Display Frame Pre-Dispatch Suppressed: ${snapshot.displayFramePreDispatchSuppressedCount}\n")
            append("- Display Frame No-Op Skips: ${snapshot.displayFrameNoOpSkippedCount}\n")
            append("- Lifecycle Resume Forced Frames: ${snapshot.lifecycleResumeForcedFrameCount}\n")
            append("- MapView Attach Count: ${snapshot.mapViewAttachCount}\n")
            append("- MapView Clear Count: ${snapshot.mapViewClearCount}\n")
            append("- MapView Swap Count: ${snapshot.mapViewSwapCount}\n")
            append("- Last Repaint Request Mono Ms: ${snapshot.lastRepaintRequestMonoMs}\n")
            append("- Last Repaint Dispatch Mono Ms: ${snapshot.lastRepaintDispatchMonoMs}\n")
            append("- Last Render Callback Mono Ms: ${snapshot.lastRenderFrameCallbackMonoMs}\n")
            append("- Last Render Delivered Mono Ms: ${snapshot.lastRenderFrameDeliveredMonoMs}\n")
            append("- Last Frame Rendered Mono Ms: ${snapshot.lastFrameRenderedMonoMs}\n")
            append("- Last Lifecycle Resume Forced Frame Mono Ms: ${snapshot.lastLifecycleResumeForcedFrameMonoMs}\n")
            append("- Last MapView Attach Mono Ms: ${snapshot.lastMapViewAttachMonoMs}\n")
            append("- Last MapView Clear Mono Ms: ${snapshot.lastMapViewClearMonoMs}\n")
        }
    }

    fun buildCompactStatus(
        reason: String,
        token: String = LOG_TOKEN
    ): String {
        val snapshot = snapshot()
        return buildString {
            append(token)
            append(" reason=").append(reason)
            append(" repaint_requests=").append(snapshot.repaintRequestCount)
            append(" forced_immediate_repaint_requests=")
                .append(snapshot.forcedImmediateRepaintRequestCount)
            append(" repaint_dispatches=").append(snapshot.repaintDispatchCount)
            append(" render_frame_callbacks=").append(snapshot.renderFrameCallbackCount)
            append(" posted_dispatch_scheduled=").append(snapshot.postedDispatchScheduledCount)
            append(" posted_dispatch_dropped=").append(snapshot.postedDispatchDroppedCount)
            append(" immediate_dispatches=").append(snapshot.immediateDispatchCount)
            append(" pending_dispatch_cleared=").append(snapshot.pendingDispatchClearedCount)
            append(" render_frames_delivered=").append(snapshot.renderFrameDeliveredCount)
            append(" frames_rendered=").append(snapshot.frameRenderedCount)
            append(" display_frame_pre_dispatch_suppressed=")
                .append(snapshot.displayFramePreDispatchSuppressedCount)
            append(" display_frame_noop_skips=").append(snapshot.displayFrameNoOpSkippedCount)
            append(" lifecycle_resume_forced_frames=")
                .append(snapshot.lifecycleResumeForcedFrameCount)
            append(" mapview_attach_count=").append(snapshot.mapViewAttachCount)
            append(" mapview_clear_count=").append(snapshot.mapViewClearCount)
            append(" mapview_swap_count=").append(snapshot.mapViewSwapCount)
            append(" last_repaint_request_mono_ms=").append(snapshot.lastRepaintRequestMonoMs)
            append(" last_repaint_dispatch_mono_ms=").append(snapshot.lastRepaintDispatchMonoMs)
            append(" last_render_callback_mono_ms=").append(snapshot.lastRenderFrameCallbackMonoMs)
            append(" last_render_delivered_mono_ms=").append(snapshot.lastRenderFrameDeliveredMonoMs)
            append(" last_frame_rendered_mono_ms=").append(snapshot.lastFrameRenderedMonoMs)
            append(" last_lifecycle_resume_forced_frame_mono_ms=")
                .append(snapshot.lastLifecycleResumeForcedFrameMonoMs)
            append(" last_mapview_attach_mono_ms=").append(snapshot.lastMapViewAttachMonoMs)
            append(" last_mapview_clear_mono_ms=").append(snapshot.lastMapViewClearMonoMs)
        }
    }

    data class Snapshot(
        val repaintRequestCount: Long,
        val forcedImmediateRepaintRequestCount: Long,
        val repaintDispatchCount: Long,
        val renderFrameCallbackCount: Long,
        val postedDispatchScheduledCount: Long,
        val postedDispatchDroppedCount: Long,
        val immediateDispatchCount: Long,
        val pendingDispatchClearedCount: Long,
        val renderFrameDeliveredCount: Long,
        val frameRenderedCount: Long,
        val displayFramePreDispatchSuppressedCount: Long,
        val displayFrameNoOpSkippedCount: Long,
        val lifecycleResumeForcedFrameCount: Long,
        val mapViewAttachCount: Long,
        val mapViewClearCount: Long,
        val mapViewSwapCount: Long,
        val lastRepaintRequestMonoMs: Long?,
        val lastRepaintDispatchMonoMs: Long?,
        val lastRenderFrameCallbackMonoMs: Long?,
        val lastRenderFrameDeliveredMonoMs: Long?,
        val lastFrameRenderedMonoMs: Long?,
        val lastLifecycleResumeForcedFrameMonoMs: Long?,
        val lastMapViewAttachMonoMs: Long?,
        val lastMapViewClearMonoMs: Long?
    )
}
