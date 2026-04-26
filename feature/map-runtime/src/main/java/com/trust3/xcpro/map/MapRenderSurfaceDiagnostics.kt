package com.trust3.xcpro.map

import com.trust3.xcpro.core.time.TimeBridge
import java.util.EnumMap

/**
 * Debug-only render-surface diagnostics for the map host/render handoff.
 *
 * This is an injected runtime owner, not a singleton. It tracks bounded
 * counters and monotonic/replay timestamps only and must not become
 * application state.
 */
class MapRenderSurfaceDiagnostics(
    private val nowMonoMs: () -> Long = TimeBridge::nowMonoMs
) {
    companion object {
        const val LOG_TOKEN: String = "MAP_RENDER_SURFACE_DIAGNOSTICS"
    }

    enum class DisplayFrameDispatchReason(val key: String) {
        REPLAY_TIME_BASE("replay_time_base"),
        CONFIG_CHANGED("config_changed"),
        ACTIVE_WINDOW("active_window")
    }

    enum class DisplayFramePreDispatchSuppressionReason(val key: String) {
        UNSPECIFIED("unspecified"),
        LOCAL_OWNSHIP_DISABLED("local_ownship_disabled"),
        NO_RENDERABLE_INPUT("no_renderable_input"),
        ACTIVITY_EXPIRED("activity_expired")
    }

    enum class DisplayFrameRenderSkipReason(val key: String) {
        LOCAL_OWNSHIP_DISABLED("local_ownship_disabled"),
        NO_POSE("no_pose"),
        MAP_NOT_READY("map_not_ready"),
        MISSING_TIME_BASE("missing_time_base"),
        NOOP_DIFF("noop_diff")
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
    private var displayFrameDispatchAllowedCount: Long = 0L
    private var displayFramePreDispatchSuppressedCount: Long = 0L
    private var displayFrameRenderSkippedCount: Long = 0L
    private var displayFrameNoOpSkippedCount: Long = 0L
    private var displayPoseRenderAppliedCount: Long = 0L
    private var monotonicRawFixInputCount: Long = 0L
    private var wallRawFixInputCount: Long = 0L
    private var replayRawFixInputCount: Long = 0L
    private var lifecycleResumeForcedFrameCount: Long = 0L
    private var mapViewAttachCount: Long = 0L
    private var mapViewClearCount: Long = 0L
    private var mapViewSwapCount: Long = 0L

    private var lastRepaintRequestMonoMs: Long? = null
    private var lastRepaintDispatchMonoMs: Long? = null
    private var lastRenderFrameCallbackMonoMs: Long? = null
    private var lastRenderFrameDeliveredMonoMs: Long? = null
    private var lastFrameRenderedMonoMs: Long? = null
    private var lastMonotonicRawFixTimestampMs: Long? = null
    private var lastWallRawFixTimestampMs: Long? = null
    private var lastReplayRawFixTimestampMs: Long? = null
    private var lastLifecycleResumeForcedFrameMonoMs: Long? = null
    private var lastMapViewAttachMonoMs: Long? = null
    private var lastMapViewClearMonoMs: Long? = null

    private val dispatchAllowedReasons =
        EnumCounter(DisplayFrameDispatchReason.values())
    private val preDispatchSuppressionReasons =
        EnumCounter(DisplayFramePreDispatchSuppressionReason.values())
    private val renderSkipReasons =
        EnumCounter(DisplayFrameRenderSkipReason.values())
    private val liveRawFixInputIntervalBuckets = IntervalBucketCounts()
    private val wallRawFixInputIntervalBuckets = IntervalBucketCounts()
    private val replayRawFixInputIntervalBuckets = IntervalBucketCounts()
    private val renderFrameDeliveredIntervalBuckets = IntervalBucketCounts()
    private val frameRenderedIntervalBuckets = IntervalBucketCounts()

    @Synchronized
    fun reset() {
        repaintRequestCount = 0L
        forcedImmediateRepaintRequestCount = 0L
        repaintDispatchCount = 0L
        renderFrameCallbackCount = 0L
        postedDispatchScheduledCount = 0L
        postedDispatchDroppedCount = 0L
        immediateDispatchCount = 0L
        pendingDispatchClearedCount = 0L
        renderFrameDeliveredCount = 0L
        frameRenderedCount = 0L
        displayFrameDispatchAllowedCount = 0L
        displayFramePreDispatchSuppressedCount = 0L
        displayFrameRenderSkippedCount = 0L
        displayFrameNoOpSkippedCount = 0L
        displayPoseRenderAppliedCount = 0L
        monotonicRawFixInputCount = 0L
        wallRawFixInputCount = 0L
        replayRawFixInputCount = 0L
        lifecycleResumeForcedFrameCount = 0L
        mapViewAttachCount = 0L
        mapViewClearCount = 0L
        mapViewSwapCount = 0L
        lastRepaintRequestMonoMs = null
        lastRepaintDispatchMonoMs = null
        lastRenderFrameCallbackMonoMs = null
        lastRenderFrameDeliveredMonoMs = null
        lastFrameRenderedMonoMs = null
        lastMonotonicRawFixTimestampMs = null
        lastWallRawFixTimestampMs = null
        lastReplayRawFixTimestampMs = null
        lastLifecycleResumeForcedFrameMonoMs = null
        lastMapViewAttachMonoMs = null
        lastMapViewClearMonoMs = null
        dispatchAllowedReasons.clear()
        preDispatchSuppressionReasons.clear()
        renderSkipReasons.clear()
        liveRawFixInputIntervalBuckets.clear()
        wallRawFixInputIntervalBuckets.clear()
        replayRawFixInputIntervalBuckets.clear()
        renderFrameDeliveredIntervalBuckets.clear()
        frameRenderedIntervalBuckets.clear()
    }

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
        val nowMs = nowMonoMs()
        renderFrameDeliveredCount += 1L
        recordInterval(nowMs, lastRenderFrameDeliveredMonoMs, renderFrameDeliveredIntervalBuckets)
        lastRenderFrameDeliveredMonoMs = nowMs
    }

    @Synchronized
    fun recordFrameRendered() {
        val nowMs = nowMonoMs()
        frameRenderedCount += 1L
        recordInterval(nowMs, lastFrameRenderedMonoMs, frameRenderedIntervalBuckets)
        lastFrameRenderedMonoMs = nowMs
    }

    @Synchronized
    fun recordDisplayFrameDispatchAllowed(reason: DisplayFrameDispatchReason) {
        displayFrameDispatchAllowedCount += 1L
        dispatchAllowedReasons.record(reason)
    }

    @Synchronized
    fun recordDisplayFramePreDispatchSuppressed(
        reason: DisplayFramePreDispatchSuppressionReason =
            DisplayFramePreDispatchSuppressionReason.UNSPECIFIED
    ) {
        displayFramePreDispatchSuppressedCount += 1L
        preDispatchSuppressionReasons.record(reason)
    }

    @Synchronized
    fun recordDisplayFrameRenderSkipped(reason: DisplayFrameRenderSkipReason) {
        displayFrameRenderSkippedCount += 1L
        renderSkipReasons.record(reason)
    }

    @Synchronized
    fun recordDisplayFrameNoOpSkipped() {
        displayFrameNoOpSkippedCount += 1L
        displayFrameRenderSkippedCount += 1L
        renderSkipReasons.record(DisplayFrameRenderSkipReason.NOOP_DIFF)
    }

    @Synchronized
    fun recordDisplayPoseRenderApplied() {
        displayPoseRenderAppliedCount += 1L
    }

    @Synchronized
    fun recordRawFixInput(timeBase: DisplayClock.TimeBase, timestampMs: Long) {
        when (timeBase) {
            DisplayClock.TimeBase.MONOTONIC -> {
                monotonicRawFixInputCount += 1L
                recordInterval(timestampMs, lastMonotonicRawFixTimestampMs, liveRawFixInputIntervalBuckets)
                if (timestampMs > 0L) {
                    lastMonotonicRawFixTimestampMs = timestampMs
                }
            }
            DisplayClock.TimeBase.WALL -> {
                wallRawFixInputCount += 1L
                recordInterval(timestampMs, lastWallRawFixTimestampMs, wallRawFixInputIntervalBuckets)
                if (timestampMs > 0L) {
                    lastWallRawFixTimestampMs = timestampMs
                }
            }
            DisplayClock.TimeBase.REPLAY -> {
                replayRawFixInputCount += 1L
                recordInterval(timestampMs, lastReplayRawFixTimestampMs, replayRawFixInputIntervalBuckets)
                if (timestampMs > 0L) {
                    lastReplayRawFixTimestampMs = timestampMs
                }
            }
        }
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
            displayFrameDispatchAllowedCount = displayFrameDispatchAllowedCount,
            displayFramePreDispatchSuppressedCount = displayFramePreDispatchSuppressedCount,
            displayFrameRenderSkippedCount = displayFrameRenderSkippedCount,
            displayFrameNoOpSkippedCount = displayFrameNoOpSkippedCount,
            displayPoseRenderAppliedCount = displayPoseRenderAppliedCount,
            monotonicRawFixInputCount = monotonicRawFixInputCount,
            wallRawFixInputCount = wallRawFixInputCount,
            replayRawFixInputCount = replayRawFixInputCount,
            lifecycleResumeForcedFrameCount = lifecycleResumeForcedFrameCount,
            mapViewAttachCount = mapViewAttachCount,
            mapViewClearCount = mapViewClearCount,
            mapViewSwapCount = mapViewSwapCount,
            lastRepaintRequestMonoMs = lastRepaintRequestMonoMs,
            lastRepaintDispatchMonoMs = lastRepaintDispatchMonoMs,
            lastRenderFrameCallbackMonoMs = lastRenderFrameCallbackMonoMs,
            lastRenderFrameDeliveredMonoMs = lastRenderFrameDeliveredMonoMs,
            lastFrameRenderedMonoMs = lastFrameRenderedMonoMs,
            lastMonotonicRawFixTimestampMs = lastMonotonicRawFixTimestampMs,
            lastWallRawFixTimestampMs = lastWallRawFixTimestampMs,
            lastReplayRawFixTimestampMs = lastReplayRawFixTimestampMs,
            lastLifecycleResumeForcedFrameMonoMs = lastLifecycleResumeForcedFrameMonoMs,
            lastMapViewAttachMonoMs = lastMapViewAttachMonoMs,
            lastMapViewClearMonoMs = lastMapViewClearMonoMs,
            displayFrameDispatchAllowedReasons = dispatchAllowedReasons.snapshot(),
            displayFramePreDispatchSuppressionReasons = preDispatchSuppressionReasons.snapshot(),
            displayFrameRenderSkipReasons = renderSkipReasons.snapshot(),
            liveRawFixInputIntervalBuckets = liveRawFixInputIntervalBuckets.snapshot(),
            wallRawFixInputIntervalBuckets = wallRawFixInputIntervalBuckets.snapshot(),
            replayRawFixInputIntervalBuckets = replayRawFixInputIntervalBuckets.snapshot(),
            renderFrameDeliveredIntervalBuckets = renderFrameDeliveredIntervalBuckets.snapshot(),
            frameRenderedIntervalBuckets = frameRenderedIntervalBuckets.snapshot()
        )

    fun buildStatus(header: String = "MapRenderSurfaceDiagnostics Status"): String =
        MapRenderSurfaceDiagnosticsStatusFormatter.buildStatus(
            snapshot = snapshot(),
            header = header
        )

    fun buildCompactStatus(
        reason: String,
        token: String = LOG_TOKEN
    ): String =
        MapRenderSurfaceDiagnosticsStatusFormatter.buildCompactStatus(
            snapshot = snapshot(),
            reason = reason,
            token = token
        )

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
        val displayFrameDispatchAllowedCount: Long,
        val displayFramePreDispatchSuppressedCount: Long,
        val displayFrameRenderSkippedCount: Long,
        val displayFrameNoOpSkippedCount: Long,
        val displayPoseRenderAppliedCount: Long,
        val monotonicRawFixInputCount: Long,
        val wallRawFixInputCount: Long,
        val replayRawFixInputCount: Long,
        val lifecycleResumeForcedFrameCount: Long,
        val mapViewAttachCount: Long,
        val mapViewClearCount: Long,
        val mapViewSwapCount: Long,
        val lastRepaintRequestMonoMs: Long?,
        val lastRepaintDispatchMonoMs: Long?,
        val lastRenderFrameCallbackMonoMs: Long?,
        val lastRenderFrameDeliveredMonoMs: Long?,
        val lastFrameRenderedMonoMs: Long?,
        val lastMonotonicRawFixTimestampMs: Long?,
        val lastWallRawFixTimestampMs: Long?,
        val lastReplayRawFixTimestampMs: Long?,
        val lastLifecycleResumeForcedFrameMonoMs: Long?,
        val lastMapViewAttachMonoMs: Long?,
        val lastMapViewClearMonoMs: Long?,
        val displayFrameDispatchAllowedReasons: Map<DisplayFrameDispatchReason, Long>,
        val displayFramePreDispatchSuppressionReasons: Map<DisplayFramePreDispatchSuppressionReason, Long>,
        val displayFrameRenderSkipReasons: Map<DisplayFrameRenderSkipReason, Long>,
        val liveRawFixInputIntervalBuckets: IntervalBucketSnapshot,
        val wallRawFixInputIntervalBuckets: IntervalBucketSnapshot,
        val replayRawFixInputIntervalBuckets: IntervalBucketSnapshot,
        val renderFrameDeliveredIntervalBuckets: IntervalBucketSnapshot,
        val frameRenderedIntervalBuckets: IntervalBucketSnapshot
    )

    data class IntervalBucketSnapshot(
        val le16Ms: Long,
        val le25Ms: Long,
        val le50Ms: Long,
        val le100Ms: Long,
        val le200Ms: Long,
        val le500Ms: Long,
        val over500Ms: Long
    ) {
        fun toCompactString(): String =
            "le16=$le16Ms,le25=$le25Ms,le50=$le50Ms,le100=$le100Ms," +
                "le200=$le200Ms,le500=$le500Ms,over500=$over500Ms"
    }

    private class IntervalBucketCounts {
        private var le16Ms: Long = 0L
        private var le25Ms: Long = 0L
        private var le50Ms: Long = 0L
        private var le100Ms: Long = 0L
        private var le200Ms: Long = 0L
        private var le500Ms: Long = 0L
        private var over500Ms: Long = 0L

        fun clear() {
            le16Ms = 0L
            le25Ms = 0L
            le50Ms = 0L
            le100Ms = 0L
            le200Ms = 0L
            le500Ms = 0L
            over500Ms = 0L
        }

        fun record(intervalMs: Long) {
            when {
                intervalMs <= 16L -> le16Ms += 1L
                intervalMs <= 25L -> le25Ms += 1L
                intervalMs <= 50L -> le50Ms += 1L
                intervalMs <= 100L -> le100Ms += 1L
                intervalMs <= 200L -> le200Ms += 1L
                intervalMs <= 500L -> le500Ms += 1L
                else -> over500Ms += 1L
            }
        }

        fun snapshot(): IntervalBucketSnapshot =
            IntervalBucketSnapshot(
                le16Ms = le16Ms,
                le25Ms = le25Ms,
                le50Ms = le50Ms,
                le100Ms = le100Ms,
                le200Ms = le200Ms,
                le500Ms = le500Ms,
                over500Ms = over500Ms
            )
    }

    private class EnumCounter<E : Enum<E>>(private val values: Array<E>) {
        private val counts = EnumMap<E, Long>(values.first().javaClass)

        fun clear() {
            counts.clear()
        }

        fun record(value: E) {
            counts[value] = (counts[value] ?: 0L) + 1L
        }

        fun snapshot(): Map<E, Long> =
            values.associateWith { value -> counts[value] ?: 0L }
    }

    private fun recordInterval(
        timestampMs: Long,
        previousTimestampMs: Long?,
        buckets: IntervalBucketCounts
    ) {
        if (timestampMs <= 0L) return
        val previous = previousTimestampMs ?: return
        val intervalMs = timestampMs - previous
        if (intervalMs > 0L) {
            buckets.record(intervalMs)
        }
    }
}
