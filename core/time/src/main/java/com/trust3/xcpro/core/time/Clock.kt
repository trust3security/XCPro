package com.trust3.xcpro.core.time

import android.os.SystemClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

interface Clock {
    fun nowMonoMs(): Long
    fun nowWallMs(): Long
}

class SystemClockProvider @Inject constructor() : Clock {
    override fun nowMonoMs(): Long = SystemClock.elapsedRealtime()

    override fun nowWallMs(): Long = System.currentTimeMillis()
}

class DefaultClockProvider @Inject constructor() : Clock {
    override fun nowMonoMs(): Long = SystemClock.elapsedRealtime()

    override fun nowWallMs(): Long = System.currentTimeMillis()
}

class FakeClock(
    private var monoMs: Long = 0L,
    private var wallMs: Long = 0L
) : Clock {
    override fun nowMonoMs(): Long = monoMs

    override fun nowWallMs(): Long = wallMs

    fun advanceMonoMs(deltaMs: Long) {
        monoMs += deltaMs
    }

    fun advanceWallMs(deltaMs: Long) {
        wallMs += deltaMs
    }

    fun setMonoMs(value: Long) {
        monoMs = value
    }

    fun setWallMs(value: Long) {
        wallMs = value
    }
}

object TimeBridge {
    fun nowMonoMs(): Long = SystemClock.elapsedRealtime()

    fun nowWallMs(): Long = System.currentTimeMillis()

    fun nowUptimeMs(): Long = SystemClock.uptimeMillis()

    fun nowLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowWallMs()), ZoneId.systemDefault())
}
