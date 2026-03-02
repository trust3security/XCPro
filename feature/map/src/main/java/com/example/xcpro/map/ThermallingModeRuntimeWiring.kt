package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.thermalling.ThermallingModeAction
import com.example.xcpro.thermalling.ThermallingModeInput
import com.example.xcpro.thermalling.ThermallingModePhase
import com.example.xcpro.thermalling.ThermallingModeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class ThermallingModeRuntimeWiring(
    private val scope: CoroutineScope,
    private val controller: ThermallingModeRuntimeController,
    private val settings: StateFlow<ThermallingModeSettings>,
    private val flightData: StateFlow<CompleteFlightData?>,
    private val thermalModeVisible: StateFlow<Boolean>,
    private val currentMode: StateFlow<FlightMode>,
    private val currentZoom: StateFlow<Float>,
    private val applyFlightMode: (FlightMode) -> Unit,
    private val applyZoom: (Float) -> Unit
) {
    private var isBound = false
    private var lastObservedZoom: Float = currentZoom.value

    fun bind() {
        if (isBound) return
        isBound = true

        combine(
            flightData,
            settings,
            thermalModeVisible,
            currentMode,
            currentZoom
        ) { sample, modeSettings, modeVisible, mode, zoom ->
            RuntimeFrame(
                isCircling = sample?.isCircling == true,
                settings = modeSettings,
                thermalModeVisible = modeVisible,
                currentMode = mode,
                currentZoom = zoom
            )
        }.onEach { frame ->
            maybeRememberManualZoom(frame.currentZoom, frame.settings)
            val actions = controller.update(
                input = ThermallingModeInput(
                    isCircling = frame.isCircling,
                    settings = frame.settings,
                    thermalModeVisible = frame.thermalModeVisible,
                    currentMode = frame.currentMode,
                    currentZoom = frame.currentZoom
                )
            )
            applyActions(actions)
        }.launchIn(scope)
    }

    private fun maybeRememberManualZoom(
        zoom: Float,
        settings: ThermallingModeSettings
    ) {
        if (zoom == lastObservedZoom) return
        if (controller.state().phase == ThermallingModePhase.ACTIVE) {
            controller.onUserZoomChanged(currentZoom = zoom, settings = settings)
        }
        lastObservedZoom = zoom
    }

    private fun applyActions(actions: List<ThermallingModeAction>) {
        actions.forEach { action ->
            when (action) {
                is ThermallingModeAction.SwitchFlightMode -> applyFlightMode(action.mode)
                is ThermallingModeAction.SetZoom -> applyZoom(action.zoom)
            }
        }
    }

    private data class RuntimeFrame(
        val isCircling: Boolean,
        val settings: ThermallingModeSettings,
        val thermalModeVisible: Boolean,
        val currentMode: FlightMode,
        val currentZoom: Float
    )
}
