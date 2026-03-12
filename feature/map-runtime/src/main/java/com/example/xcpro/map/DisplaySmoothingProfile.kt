package com.example.xcpro.map

/**
 * Preset smoothing profiles for live display.
 */
enum class DisplaySmoothingProfile(val config: DisplayPoseSmoothingConfig) {
    SMOOTH(DisplayPoseSmoothingConfig()),
    RESPONSIVE(
        DisplayPoseSmoothingConfig(
            posSmoothMs = 150.0,
            headingSmoothMs = 120.0,
            deadReckonLimitMs = 250L,
            staleFixTimeoutMs = DisplayPoseSmoothingConfig.DEFAULT_STALE_FIX_TIMEOUT_MS
        )
    )
}
