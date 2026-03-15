package com.example.xcpro.map.trail.domain

/**
 * Explicit time base for trail timestamps.
 */
enum class TrailTimeBase {
    LIVE_MONOTONIC,
    LIVE_WALL,
    REPLAY_IGC
}
