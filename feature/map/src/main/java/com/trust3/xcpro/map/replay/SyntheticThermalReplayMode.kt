package com.trust3.xcpro.map.replay

internal enum class SyntheticThermalReplayMode {
    NONE,
    CLEAN,
    WIND_NOISY;

    val isActive: Boolean
        get() = this != NONE
}
