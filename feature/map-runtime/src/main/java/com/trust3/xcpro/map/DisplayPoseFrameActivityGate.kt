package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.core.time.TimeBridge
import kotlin.math.abs
import kotlin.math.ceil

internal class DisplayPoseFrameActivityGate(
    private val nowMonoMs: () -> Long = TimeBridge::nowMonoMs
) {
    enum class DecisionReason {
        REPLAY_TIME_BASE,
        CONFIG_CHANGED,
        ACTIVE_WINDOW,
        NO_RENDERABLE_INPUT,
        ACTIVITY_EXPIRED
    }

    data class DispatchDecision(
        val shouldDispatch: Boolean,
        val reason: DecisionReason
    )

    private var activeUntilMonoMs: Long = 0L
    private var lastOrientation: OrientationData? = null
    private var lastConfigKey: ConfigKey? = null

    fun clear() {
        activeUntilMonoMs = 0L
        lastOrientation = null
        lastConfigKey = null
    }

    fun markFixReceived(profile: DisplaySmoothingProfile) {
        activate(profile)
    }

    fun updateOrientation(
        orientation: OrientationData,
        profile: DisplaySmoothingProfile,
        hasRenderableInput: Boolean
    ) {
        val previous = lastOrientation
        lastOrientation = orientation
        if (!hasRenderableInput) {
            return
        }
        if (hasMeaningfulOrientationChange(previous, orientation)) {
            activate(profile)
        }
    }

    fun shouldDispatch(
        hasRenderableInput: Boolean,
        timeBase: DisplayClock.TimeBase?,
        mode: DisplayPoseMode,
        smoothingProfile: DisplaySmoothingProfile
    ): Boolean =
        evaluateDispatch(
            hasRenderableInput = hasRenderableInput,
            timeBase = timeBase,
            mode = mode,
            smoothingProfile = smoothingProfile
        ).shouldDispatch

    fun evaluateDispatch(
        hasRenderableInput: Boolean,
        timeBase: DisplayClock.TimeBase?,
        mode: DisplayPoseMode,
        smoothingProfile: DisplaySmoothingProfile
    ): DispatchDecision {
        if (timeBase == DisplayClock.TimeBase.REPLAY) {
            lastConfigKey = ConfigKey(timeBase, mode, smoothingProfile)
            return DispatchDecision(
                shouldDispatch = true,
                reason = DecisionReason.REPLAY_TIME_BASE
            )
        }

        val configKey = ConfigKey(timeBase, mode, smoothingProfile)
        val configChanged = configKey != lastConfigKey
        lastConfigKey = configKey

        if (!hasRenderableInput) {
            return DispatchDecision(
                shouldDispatch = false,
                reason = DecisionReason.NO_RENDERABLE_INPUT
            )
        }
        if (configChanged) {
            activate(smoothingProfile)
            return DispatchDecision(
                shouldDispatch = true,
                reason = DecisionReason.CONFIG_CHANGED
            )
        }
        return if (nowMonoMs() <= activeUntilMonoMs) {
            DispatchDecision(
                shouldDispatch = true,
                reason = DecisionReason.ACTIVE_WINDOW
            )
        } else {
            DispatchDecision(
                shouldDispatch = false,
                reason = DecisionReason.ACTIVITY_EXPIRED
            )
        }
    }

    private fun activate(profile: DisplaySmoothingProfile) {
        val deadlineMs = nowMonoMs() + settleWindowMs(profile)
        if (deadlineMs > activeUntilMonoMs) {
            activeUntilMonoMs = deadlineMs
        }
    }

    private fun settleWindowMs(profile: DisplaySmoothingProfile): Long {
        val config = profile.config
        return maxOf(
            ceil(config.posSmoothMs).toLong(),
            ceil(config.headingSmoothMs).toLong(),
            config.deadReckonLimitMs
        )
    }

    private fun hasMeaningfulOrientationChange(
        previous: OrientationData?,
        current: OrientationData
    ): Boolean {
        val last = previous ?: return true
        if (last.mode != current.mode) return true
        if (last.isValid != current.isValid) return true
        if (last.bearingSource != current.bearingSource) return true
        if (last.headingValid != current.headingValid) return true
        if (last.headingSource != current.headingSource) return true
        if (!sameAngle(last.bearing, current.bearing)) return true
        if (current.headingValid && !sameAngle(last.headingDeg, current.headingDeg)) return true
        return false
    }

    private fun sameAngle(first: Double, second: Double): Boolean {
        val normalized = ((second - first + 540.0) % 360.0) - 180.0
        return abs(normalized) <= ANGLE_EPS_DEG
    }

    private data class ConfigKey(
        val timeBase: DisplayClock.TimeBase?,
        val mode: DisplayPoseMode,
        val smoothingProfile: DisplaySmoothingProfile
    )

    private companion object {
        private const val ANGLE_EPS_DEG = 0.5
    }
}
