package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.TemperatureUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsSettingsUseCase
import com.example.xcpro.common.units.VerticalSpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UnitsSettingsViewModel @Inject constructor(
    private val useCase: UnitsSettingsUseCase
) : ViewModel() {

    val units: StateFlow<UnitsPreferences> = useCase.unitsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UnitsPreferences()
        )

    fun setProfileId(profileId: String) {
        useCase.setActiveProfileId(profileId)
    }

    fun setAltitude(unit: AltitudeUnit) {
        viewModelScope.launch { useCase.setAltitude(unit) }
    }

    fun setVerticalSpeed(unit: VerticalSpeedUnit) {
        viewModelScope.launch { useCase.setVerticalSpeed(unit) }
    }

    fun setSpeed(unit: SpeedUnit) {
        viewModelScope.launch { useCase.setSpeed(unit) }
    }

    fun setDistance(unit: DistanceUnit) {
        viewModelScope.launch { useCase.setDistance(unit) }
    }

    fun setPressure(unit: PressureUnit) {
        viewModelScope.launch { useCase.setPressure(unit) }
    }

    fun setTemperature(unit: TemperatureUnit) {
        viewModelScope.launch { useCase.setTemperature(unit) }
    }
}
