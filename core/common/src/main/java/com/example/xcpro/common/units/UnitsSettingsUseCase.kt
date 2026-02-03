package com.example.xcpro.common.units

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class UnitsSettingsUseCase @Inject constructor(
    private val repository: UnitsRepository
) {
    val unitsFlow: Flow<UnitsPreferences> = repository.unitsFlow

    suspend fun setUnits(preferences: UnitsPreferences) {
        repository.setUnits(preferences)
    }

    suspend fun setAltitude(unit: AltitudeUnit) {
        repository.setAltitude(unit)
    }

    suspend fun setVerticalSpeed(unit: VerticalSpeedUnit) {
        repository.setVerticalSpeed(unit)
    }

    suspend fun setSpeed(unit: SpeedUnit) {
        repository.setSpeed(unit)
    }

    suspend fun setDistance(unit: DistanceUnit) {
        repository.setDistance(unit)
    }

    suspend fun setPressure(unit: PressureUnit) {
        repository.setPressure(unit)
    }

    suspend fun setTemperature(unit: TemperatureUnit) {
        repository.setTemperature(unit)
    }
}
