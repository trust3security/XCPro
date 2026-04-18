package com.trust3.xcpro.screens.diagnostics

import com.trust3.xcpro.sensors.SensorFusionRepository
import com.trust3.xcpro.sensors.VarioDiagnosticsSample
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class VarioDiagnosticsUseCase @Inject constructor(
    private val sensorFusionRepository: SensorFusionRepository
) {
    val diagnosticsFlow: StateFlow<VarioDiagnosticsSample?> = sensorFusionRepository.diagnosticsFlow
}
