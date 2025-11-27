package com.example.xcpro.sensors.domain

internal object FlightMetricsConstants {
    const val DEFAULT_QNH_HPA = 1013.25
    const val AVERAGE_WINDOW_SECONDS = 30
    const val NETTO_DISPLAY_WINDOW_MS = 5_000L
    const val DISPLAY_VAR_CLAMP = 7.0
    const val DISPLAY_SMOOTH_TIME_S = 0.6
    const val DISPLAY_DECAY_FACTOR = 0.9
    const val MIN_SINK_FOR_IAS_MS = 0.15
    const val IAS_SCAN_MIN_MS = 8.0
    const val IAS_SCAN_MAX_MS = 80.0
    const val IAS_SCAN_STEP_MS = 0.5
    const val SEA_LEVEL_PRESSURE_HPA = 1013.25
    const val SEA_LEVEL_TEMP_CELSIUS = 15.0
    const val TEMP_LAPSE_RATE_C_PER_M = -0.0065
    const val GAS_CONSTANT = 287.05
    const val GRAVITY = 9.80665
    const val SPEED_HOLD_MS = 10_000L
    const val MIN_MOVING_SPEED_MS = 0.5
    const val VARIO_SPIKE_THRESHOLD_MS = 10.0
    const val QNH_JUMP_THRESHOLD_HPA = 0.5
}
