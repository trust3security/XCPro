package com.example.xcpro.map

/**
 * UI-only display mode for the glider marker.
 *
 * SMOOTHED: default DisplayPoseSmoother path.
 * RAW_REPLAY: raw replay fix (no smoothing/prediction) for visual parity.
 */
enum class DisplayPoseMode {
    SMOOTHED,
    RAW_REPLAY
}
