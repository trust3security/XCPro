package com.example.xcpro.map

import com.example.xcpro.thermalling.ThermallingModeAction
import com.example.xcpro.thermalling.ThermallingModeCoordinator
import com.example.xcpro.thermalling.ThermallingModeInput
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.thermalling.ThermallingModeSettings
import com.example.xcpro.thermalling.ThermallingModeState
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
