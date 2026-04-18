package com.trust3.xcpro.orientation

import com.trust3.xcpro.core.time.Clock
import com.trust3.xcpro.core.time.DefaultClockProvider
import javax.inject.Inject

interface OrientationClock {
    fun nowMonoMs(): Long
    fun nowWallMs(): Long
}

class SystemOrientationClock @Inject constructor(
    private val clock: Clock
) : OrientationClock {
    constructor() : this(DefaultClockProvider())

    override fun nowMonoMs(): Long = clock.nowMonoMs()

    override fun nowWallMs(): Long = clock.nowWallMs()
}
