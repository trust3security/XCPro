package com.example.xcpro.sensors

import com.example.xcpro.audio.VarioAudioSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the fused sensor pipeline. Implementations own sensor flows, filtering, and
 * downstream flight metrics while presenting read-only data streams to the rest of the app.
 */
interface SensorFusionRepository {
    val flightDataFlow: StateFlow<CompleteFlightData?>
    val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?>
    val audioSettings: StateFlow<VarioAudioSettings>

    fun updateAudioSettings(settings: VarioAudioSettings)
    fun setManualQnh(qnhHPa: Double)
    fun resetQnhToStandard()
    fun requestAutoQnhCalibration()
    fun setMacCreadySetting(value: Double)
    fun setMacCreadyRisk(value: Double)
    fun updateReplayRealVario(realVarioMs: Double?)
    fun stop()
}
