package com.trust3.xcpro.map

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
    ),
    CADENCE_BRIDGE(
        DisplayPoseSmoothingConfig(
            posSmoothMs = 260.0,
            headingSmoothMs = 220.0,
            deadReckonLimitMs = 1_200L,
            staleFixTimeoutMs = DisplayPoseSmoothingConfig.DEFAULT_STALE_FIX_TIMEOUT_MS,
            frameActiveWindowMs = 1_300L
        )
    )
}
