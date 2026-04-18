package com.trust3.xcpro.tasks

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized task feature switches (injected for test overrides).
 */
@Singleton
class TaskFeatureFlags @Inject constructor() {
    @Volatile
    var enableRacingNavigation: Boolean = true

    @Volatile
    var enableRacingAutoAdvance: Boolean = true
}
