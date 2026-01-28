package com.example.xcpro.screens.navdrawer

import com.example.xcpro.MapOrientationSettings
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasMode
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class OrientationSettingsUseCase @Inject constructor(
    private val repository: MapOrientationSettingsRepository
) {
    val settingsFlow: StateFlow<MapOrientationSettings> = repository.settingsFlow

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
