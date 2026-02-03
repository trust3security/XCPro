package com.example.xcpro.map

import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class MapStyleUseCase @Inject constructor(
    private val repository: MapStyleRepository
) {
    fun initialStyle(): String = repository.initialStyle()

    suspend fun saveStyle(style: String) {
        repository.saveStyle(style)
    }
}

class UnitsPreferencesUseCase @Inject constructor(
    private val repository: UnitsRepository
) {
    val unitsFlow: Flow<UnitsPreferences> = repository.unitsFlow
}

class GliderConfigUseCase @Inject constructor(
    private val repository: GliderConfigRepository
) {
    val config = repository.config
}

class FlightDataUseCase @Inject constructor(
    private val repository: FlightDataRepository
) {
    val flightData: StateFlow<CompleteFlightData?> = repository.flightData
    val activeSource: StateFlow<FlightDataRepository.Source> = repository.activeSource
}

class WindStateUseCase @Inject constructor(
    private val repository: WindSensorFusionRepository
) {
    val windState: StateFlow<WindState> = repository.windState
}

class QnhUseCase @Inject constructor(
    private val repository: QnhRepository
) {
    val calibrationState = repository.calibrationState

    suspend fun setManualQnh(hpa: Double) {
        repository.setManualQnh(hpa)
    }
}
