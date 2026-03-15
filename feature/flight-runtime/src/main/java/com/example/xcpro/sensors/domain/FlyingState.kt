package com.example.xcpro.sensors.domain

/**
 * Minimal flying state snapshot for consumers that need gating (e.g. circling).
 */
data class FlyingState(
    val isFlying: Boolean = false,
    val onGround: Boolean = false
)
