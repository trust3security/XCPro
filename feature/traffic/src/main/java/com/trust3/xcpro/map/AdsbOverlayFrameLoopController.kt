package com.trust3.xcpro.map

import android.view.Choreographer

internal class AdsbOverlayFrameLoopController(
    private val minRenderIntervalMs: Long,
    private val choreographer: Choreographer = Choreographer.getInstance()
) {
    private var frameScheduled: Boolean = false
    private var lastRenderedFrameMonoMs: Long = Long.MIN_VALUE

    fun onFrameDispatched() {
        frameScheduled = false
    }

    fun markFrameRendered(nowMonoMs: Long) {
        lastRenderedFrameMonoMs = nowMonoMs
    }

    fun resetRenderClock() {
        lastRenderedFrameMonoMs = Long.MIN_VALUE
    }

    fun shouldRenderFrame(nowMonoMs: Long): Boolean {
        val lastRendered = lastRenderedFrameMonoMs
        if (lastRendered == Long.MIN_VALUE) return true
        return nowMonoMs - lastRendered >= minRenderIntervalMs
    }

    fun schedule(frameCallback: Choreographer.FrameCallback): Boolean {
        if (frameScheduled) return false
        frameScheduled = true
        choreographer.postFrameCallback(frameCallback)
        return true
    }

    fun stop(frameCallback: Choreographer.FrameCallback) {
        if (!frameScheduled) return
        choreographer.removeFrameCallback(frameCallback)
        frameScheduled = false
    }
}
