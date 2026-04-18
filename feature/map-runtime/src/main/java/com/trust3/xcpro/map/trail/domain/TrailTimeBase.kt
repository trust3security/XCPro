package com.trust3.xcpro.map.trail.domain

/**
 * Explicit time base for trail timestamps.
 */
enum class TrailTimeBase {
    LIVE_MONOTONIC,
    LIVE_WALL,
    REPLAY_IGC
}
