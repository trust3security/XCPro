package com.example.xcpro.map.config

import com.example.xcpro.map.BuildConfig
/**
 * Centralized switches for map feature behavior that needs to differ between
 * production and tests. Keep mutable flags here (rather than spread across
 * call sites) so instrumentation and unit tests can safely override behavior
 * without touching production code paths.
 */
object MapFeatureFlags {
    /**
     * Controls whether [MapScreenViewModel] should call
     * [com.example.xcpro.tasks.TaskManagerCoordinator.loadSavedTasks] during init.
     * Unit tests can disable this to avoid heavy persistence lookups.
     */
    @Volatile
    var loadSavedTasksOnInit: Boolean = true

    /**
     * Shows the developer-only vario demo FAB on the map screen when true.
     */
    @Volatile
    var showVarioDemoFab: Boolean = BuildConfig.DEBUG

    /**
     * Pixel threshold for map location jitter suppression.
     */
    @Volatile
    var locationJitterThresholdPx: Float = 0.5f

    /**
     * Window size for glider offset averaging.
     */
    @Volatile
    var locationOffsetHistorySize: Int = 30

    /**
     * Debug override: allow device heading even when stationary (ignores isFlying/speed gate).
     */
    @Volatile
    var allowHeadingWhileStationary: Boolean = false
}
