package com.example.hawkwind.model
data class InstantWind(val windSpeed: Float, val windDirDeg: Float, val wVerticalMs: Float)
data class WindUiCfg(val avgWindowSec: Int = 5, val maxWindForScale: Float = 40f, val turbulencePreset: String = "Normal", val units: String = "Knots", val loggingEnabled: Boolean = false)
data class WindUiState(val instant: InstantWind = InstantWind(0f,0f,0f), val avg: InstantWind = InstantWind(0f,0f,0f), val confidence: Int = 0, val ui: WindUiCfg = WindUiCfg())
data class VarioUiCfg(val scaleMs: Double = 5.0)
data class VarioUiState(val tekVzMs: Double = 0.0, val ekfVzMs: Double = 0.0, val ui: VarioUiCfg = VarioUiCfg())
data class AhrsUiState(val rollDeg: Double = 0.0, val pitchDeg: Double = 0.0, val yawDeg: Double = 0.0, val turnRateDegs: Double = 0.0, val gLoad: Double = 1.0, val windDirDeg: Double = 0.0, val windSpeedKt: Double = 0.0)
