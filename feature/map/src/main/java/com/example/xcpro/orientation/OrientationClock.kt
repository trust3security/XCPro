package com.example.xcpro.orientation

import android.os.SystemClock
import javax.inject.Inject

interface OrientationClock {
    fun nowMonoMs(): Long
    fun nowWallMs(): Long
}

class SystemOrientationClock @Inject constructor() : OrientationClock {
    override fun nowMonoMs(): Long = SystemClock.elapsedRealtime()

    override fun nowWallMs(): Long = System.currentTimeMillis()
}