package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.core.time.TimeBridge
import kotlin.math.abs
import kotlin.math.ceil

internal class DisplayPoseFrameActivityGate(
    private val nowMonoMs: () -> Long = TimeBridge::nowMonoMs
) {
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
    ): Boolean {
        if (timeBase == DisplayClock.TimeBase.REPLAY) {
            lastConfigKey = ConfigKey(timeBase, mode, smoothingProfile)
            return true
        }

        val configKey = ConfigKey(timeBase, mode, smoothingProfile)
        val configChanged = configKey != lastConfigKey
        lastConfigKey = configKey

        if (!hasRenderableInput) {
            return false
        }
        if (configChanged) {
            activate(smoothingProfile)
            return true
        }
        return nowMonoMs() <= activeUntilMonoMs
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
