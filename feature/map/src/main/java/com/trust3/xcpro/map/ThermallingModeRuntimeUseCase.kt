package com.trust3.xcpro.map

import com.trust3.xcpro.thermalling.ThermallingModeAction
import com.trust3.xcpro.thermalling.ThermallingModeCoordinator
import com.trust3.xcpro.thermalling.ThermallingModeInput
import com.trust3.xcpro.thermalling.ThermallingModePreferencesRepository
import com.trust3.xcpro.thermalling.ThermallingModeSettings
import com.trust3.xcpro.thermalling.ThermallingModeState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface ThermallingModeRuntimeController {
    fun update(input: ThermallingModeInput): List<ThermallingModeAction>
    fun onUserZoomChanged(currentZoom: Float, settings: ThermallingModeSettings)
    fun state(): ThermallingModeState
    fun reset()
}

class ThermallingModeRuntimeUseCase @Inject constructor(
    private val preferencesRepository: ThermallingModePreferencesRepository,
    private val coordinator: ThermallingModeCoordinator
) : ThermallingModeRuntimeController {
    val settingsFlow: Flow<ThermallingModeSettings> = preferencesRepository.settingsFlow

    override fun update(input: ThermallingModeInput): List<ThermallingModeAction> =
        coordinator.update(input)

    override fun onUserZoomChanged(currentZoom: Float, settings: ThermallingModeSettings) {
        coordinator.onUserZoomChanged(currentZoom, settings)
    }

    override fun state(): ThermallingModeState = coordinator.state()

    override fun reset() {
        coordinator.reset()
    }
}
