package com.example.xcpro.screens.navdrawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


data class OrientationSettingsUiState(
    val cruiseMode: MapOrientationMode = MapOrientationMode.TRACK_UP,
    val circlingMode: MapOrientationMode = MapOrientationMode.TRACK_UP,
    val gliderScreenPercent: Int = 35,
    val mapShiftBiasMode: MapShiftBiasMode = MapShiftBiasMode.NONE,
    val mapShiftBiasStrength: Double = 1.0
)

@HiltViewModel
class OrientationSettingsViewModel @Inject constructor(
    private val repository: MapOrientationSettingsRepository
) : ViewModel() {
    val uiState: StateFlow<OrientationSettingsUiState> = repository.settingsFlow
        .map { settings ->
            OrientationSettingsUiState(
                cruiseMode = settings.cruiseMode,
                circlingMode = settings.circlingMode,
                gliderScreenPercent = settings.gliderScreenPercent,
                mapShiftBiasMode = settings.mapShiftBiasMode,
                mapShiftBiasStrength = settings.mapShiftBiasStrength
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OrientationSettingsUiState()
        )

    fun setCruiseMode(mode: MapOrientationMode) {
        repository.setCruiseOrientationMode(mode)
    }

    fun setCirclingMode(mode: MapOrientationMode) {
        repository.setCirclingOrientationMode(mode)
    }

    fun setGliderScreenPercent(percentFromBottom: Int) {
        repository.setGliderScreenPercent(percentFromBottom)
    }

    fun setMapShiftBiasMode(mode: MapShiftBiasMode) {
        repository.setMapShiftBiasMode(mode)
    }

    fun setMapShiftBiasStrength(strength: Double) {
        repository.setMapShiftBiasStrength(strength)
    }
}