package com.trust3.xcpro.screens.navdrawer

import com.trust3.xcpro.MapOrientationSettings
import com.trust3.xcpro.MapOrientationSettingsRepository
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.map.domain.MapShiftBiasMode
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
