package com.example.xcpro.tasks

/**
 * Centralized task feature switches.
 */
object TaskFeatureFlags {
    @Volatile
    var enableRacingNavigation: Boolean = true

    @Volatile
    var enableRacingAutoAdvance: Boolean = true
}
