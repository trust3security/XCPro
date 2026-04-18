package com.trust3.xcpro.map

enum class MapFollowCameraMotion {
    MOVE,
    ANIMATE
}

class MapFollowCameraMotionPolicy {
    fun resolveContinuousFollowMotion(
        timeBase: DisplayClock.TimeBase?
    ): MapFollowCameraMotion = when (timeBase) {
        DisplayClock.TimeBase.MONOTONIC,
        DisplayClock.TimeBase.REPLAY,
        DisplayClock.TimeBase.WALL,
        null -> MapFollowCameraMotion.MOVE
    }
}
