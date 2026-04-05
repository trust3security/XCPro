package com.example.xcpro.map

const val DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS = 25_000_000L
const val DISPLAY_POSE_MIN_FRAME_INTERVAL_REPLAY_NS = 16_666_667L

internal fun displayPoseMinFrameIntervalNs(timeBase: DisplayClock.TimeBase?): Long =
    when (timeBase) {
        DisplayClock.TimeBase.REPLAY -> DISPLAY_POSE_MIN_FRAME_INTERVAL_REPLAY_NS
        DisplayClock.TimeBase.MONOTONIC,
        DisplayClock.TimeBase.WALL,
        null -> DISPLAY_POSE_MIN_FRAME_INTERVAL_LIVE_NS
    }
