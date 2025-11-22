package com.example.xcpro.sensors

internal object FlightDataConstants {
    const val TAG = "FlightDataCalculator"
    const val LOG_THERMAL_METRICS = false
    const val DEFAULT_MACCREADY = 0.0

    // History sizes
    const val MAX_LOCATION_HISTORY = 20
    const val MAX_VSPEED_HISTORY = 10

    // L/D calculation
    const val LD_CALCULATION_INTERVAL = 5000L // ms

    // QNH jump suppression
    const val QNH_JUMP_THRESHOLD_HPA = 0.8
    const val QNH_ALTITUDE_JUMP_THRESHOLD_METERS = 5.0
    const val QNH_CALIBRATION_ACCURACY_THRESHOLD = 8.0
    const val VARIO_VALIDITY_MS = 500L
    const val REPLAY_VARIO_MAX_AGE_MS = 5_000L
}
