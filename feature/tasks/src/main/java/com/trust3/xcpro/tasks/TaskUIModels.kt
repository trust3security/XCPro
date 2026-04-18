package com.trust3.xcpro.tasks

import java.time.Duration

/**
 * UI-specific task models for Rules bottom sheet tab
 * These are simple stubs to allow UI compilation without complex task logic
 */

/**
 * Stub AATTask for UI purposes only
 */
data class AATTask(
    val minimumTime: Duration = Duration.ofHours(3),
    val maximumTime: Duration? = Duration.ofHours(6)
)

