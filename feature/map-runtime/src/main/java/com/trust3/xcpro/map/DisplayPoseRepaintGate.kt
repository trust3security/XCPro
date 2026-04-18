package com.trust3.xcpro.map

import android.os.Handler
import android.os.Looper
import com.trust3.xcpro.core.time.TimeBridge

internal class DisplayPoseRepaintGate(
    private val requestRepaint: () -> Unit,
    private val diagnostics: MapRenderSurfaceDiagnostics,
    private val monotonicNs: () -> Long = { TimeBridge.nowMonoMs() * 1_000_000L },
    private val scheduler: Scheduler = MainThreadScheduler()
) {
    private var lastDispatchNs: Long = NO_DISPATCH_NS
    private var pending: Boolean = false
    private var pendingMinIntervalNs: Long = 0L

    fun request(minIntervalNs: Long, forceImmediate: Boolean = false) {
        if (forceImmediate) {
            pending = false
            pendingMinIntervalNs = 0L
            scheduler.cancel()
            dispatch(nowNs = monotonicNs())
            return
        }
        pending = true
        pendingMinIntervalNs = minIntervalNs
        maybeDispatchOrSchedule()
    }

    fun onFrameRendered() {
        lastDispatchNs = monotonicNs()
        pending = false
        pendingMinIntervalNs = 0L
        scheduler.cancel()
    }

    fun clear() {
        lastDispatchNs = NO_DISPATCH_NS
        pending = false
        pendingMinIntervalNs = 0L
        scheduler.cancel()
    }

    private fun maybeDispatchOrSchedule() {
        if (!pending) return
        val intervalNs = pendingMinIntervalNs
        if (intervalNs <= 0L) {
            pending = false
            scheduler.cancel()
            dispatch(nowNs = monotonicNs())
            return
        }
        val nowNs = monotonicNs()
        val elapsedNs = if (lastDispatchNs < 0L) {
            Long.MAX_VALUE
        } else {
            nowNs - lastDispatchNs
        }
        if (elapsedNs >= intervalNs) {
            pending = false
            scheduler.cancel()
            dispatch(nowNs = nowNs)
            return
        }
        val delayNs = intervalNs - elapsedNs
        scheduler.schedule(
            delayMs = nanosToDelayMsCeil(delayNs)
        ) {
            maybeDispatchOrSchedule()
        }
    }

    private fun dispatch(nowNs: Long) {
        lastDispatchNs = nowNs
        diagnostics.recordRepaintDispatch()
        requestRepaint()
    }

    private companion object {
        private const val NO_DISPATCH_NS = -1L
    }

    private fun nanosToDelayMsCeil(delayNs: Long): Long {
        val delayMs = (delayNs + 999_999L) / 1_000_000L
        return if (delayMs <= 0L) 1L else delayMs
    }

    internal interface Scheduler {
        fun schedule(delayMs: Long, task: () -> Unit)
        fun cancel()
    }

    private class MainThreadScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())
        private var runnable: Runnable? = null

        override fun schedule(delayMs: Long, task: () -> Unit) {
            cancel()
            runnable = Runnable {
                runnable = null
                task()
            }.also { pendingRunnable ->
                handler.postDelayed(pendingRunnable, delayMs)
            }
        }

        override fun cancel() {
            runnable?.let(handler::removeCallbacks)
            runnable = null
        }
    }
}
