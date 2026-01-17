package com.example.xcpro.map

import android.os.SystemClock

class DisplayClock {
    enum class TimeBase { MONOTONIC, WALL, REPLAY }

    var replaySpeedMultiplier: Double = 1.0
    private var timeBase: TimeBase = TimeBase.MONOTONIC
    private var lastFixTimestampMs: Long = 0L
    private var lastFixWallMs: Long = 0L

    fun updateFromFix(timestampMs: Long, base: TimeBase) {
        timeBase = base
        lastFixTimestampMs = timestampMs
        lastFixWallMs = SystemClock.elapsedRealtime()
    }

    fun nowMs(): Long {
        return when (timeBase) {
            TimeBase.MONOTONIC -> SystemClock.elapsedRealtime()
            TimeBase.WALL -> System.currentTimeMillis()
            TimeBase.REPLAY -> {
                // Replay timestamps advance by wall-time elapsed (scaled) for smooth interpolation.
                val elapsedWall = (SystemClock.elapsedRealtime() - lastFixWallMs).coerceAtLeast(0L)
                lastFixTimestampMs + (elapsedWall * replaySpeedMultiplier).toLong()
            }
        }
    }
}

