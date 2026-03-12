package com.example.xcpro.tasks.domain.model

/**
 * Lightweight UI payload describing a single target-bearing task point.
 */
data class TaskTargetSnapshot(
    val index: Int,
    val id: String,
    val name: String,
    val allowsTarget: Boolean,
    val targetParam: Double,
    val isLocked: Boolean,
    val target: GeoPoint?
)
