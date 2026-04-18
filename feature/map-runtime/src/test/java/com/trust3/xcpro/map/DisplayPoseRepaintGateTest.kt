package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPoseRepaintGateTest {

    @Test
    fun firstRequest_dispatchesImmediately() {
        val fixture = RepaintGateFixture()

        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)

        assertEquals(1, fixture.repaintCount)
    }

    @Test
    fun rapidRequestsWithinCadence_coalesceToOneScheduledRepaint() {
        val fixture = RepaintGateFixture()

        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)
        fixture.clockNs += 5_000_000L
        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)
        fixture.clockNs += 5_000_000L
        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)

        assertEquals(1, fixture.repaintCount)
        assertEquals(15L, fixture.scheduler.lastDelayMs)

        fixture.clockNs += 15_000_000L
        fixture.scheduler.runPending()

        assertEquals(2, fixture.repaintCount)
    }

    @Test
    fun frameRendered_clearsPendingScheduledRepaint() {
        val fixture = RepaintGateFixture()

        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)
        fixture.clockNs += 5_000_000L
        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)

        fixture.clockNs += 5_000_000L
        fixture.gate.onFrameRendered()
        fixture.clockNs += 20_000_000L
        fixture.scheduler.runPending()

        assertEquals(1, fixture.repaintCount)
    }

    @Test
    fun forceImmediate_bypassesCadenceWindow() {
        val fixture = RepaintGateFixture()

        fixture.gate.request(minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS)
        fixture.clockNs += 5_000_000L
        fixture.gate.request(
            minIntervalNs = DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS,
            forceImmediate = true
        )

        assertEquals(2, fixture.repaintCount)
    }

    private class RepaintGateFixture {
        var repaintCount: Int = 0
        var clockNs: Long = 0L
        val scheduler = RecordingScheduler()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { clockNs / 1_000_000L })
        val gate = DisplayPoseRepaintGate(
            requestRepaint = { repaintCount += 1 },
            diagnostics = diagnostics,
            monotonicNs = { clockNs },
            scheduler = scheduler
        )
    }

    private class RecordingScheduler : DisplayPoseRepaintGate.Scheduler {
        private var task: (() -> Unit)? = null
        var lastDelayMs: Long? = null
            private set

        override fun schedule(delayMs: Long, task: () -> Unit) {
            lastDelayMs = delayMs
            this.task = task
        }

        override fun cancel() {
            task = null
            lastDelayMs = null
        }

        fun runPending() {
            val pendingTask = task
            task = null
            lastDelayMs = null
            pendingTask?.invoke()
        }
    }
}
