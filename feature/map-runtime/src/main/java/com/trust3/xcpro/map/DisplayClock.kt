package com.trust3.xcpro.map

import com.trust3.xcpro.core.time.TimeBridge

class DisplayClock {
    enum class TimeBase { MONOTONIC, WALL, REPLAY }

    var replaySpeedMultiplier: Double = 1.0
    private var timeBase: TimeBase = TimeBase.MONOTONIC
    private var lastFixTimestampMs: Long = 0L
    private var lastFixWallMs: Long = 0L

    fun updateFromFix(timestampMs: Long, base: TimeBase) {
        timeBase = base
        lastFixTimestampMs = timestampMs
        lastFixWallMs = TimeBridge.nowMonoMs()
    }

    fun nowMs(): Long {
        return when (timeBase) {
            TimeBase.MONOTONIC -> TimeBridge.nowMonoMs()
            TimeBase.WALL -> TimeBridge.nowWallMs()
            TimeBase.REPLAY -> {
                // Replay timestamps advance by wall-time elapsed (scaled) for smooth interpolation.
                val elapsedWall = (TimeBridge.nowMonoMs() - lastFixWallMs).coerceAtLeast(0L)
                lastFixTimestampMs + (elapsedWall * replaySpeedMultiplier).toLong()
            }
        }
    }

    fun clear() {
        timeBase = TimeBase.MONOTONIC
        lastFixTimestampMs = 0L
        lastFixWallMs = 0L
    }
}
