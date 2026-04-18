package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapFollowCameraMotionPolicyTest {

    private val policy = MapFollowCameraMotionPolicy()

    @Test
    fun resolveContinuousFollowMotion_returnsMove_forLiveTracking() {
        val motion = policy.resolveContinuousFollowMotion(DisplayClock.TimeBase.MONOTONIC)

        assertEquals(MapFollowCameraMotion.MOVE, motion)
    }

    @Test
    fun resolveContinuousFollowMotion_returnsMove_forReplayTracking() {
        val motion = policy.resolveContinuousFollowMotion(DisplayClock.TimeBase.REPLAY)

        assertEquals(MapFollowCameraMotion.MOVE, motion)
    }
}
