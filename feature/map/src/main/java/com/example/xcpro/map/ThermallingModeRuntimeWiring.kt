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
    private val replayActive: StateFlow<Boolean>,
    private val currentMode: StateFlow<FlightMode>,
    private val currentZoom: StateFlow<Float>,
    private val currentBaseStyle: StateFlow<String>,
    private val applyRuntimeFlightMode: (FlightMode) -> Unit,
    private val clearRuntimeFlightModeOverride: () -> Unit,
    private val applyZoom: (Float) -> Unit,
    private val applyContrastMap: (Boolean) -> Unit
) {
    private var isBound = false
    private var lastObservedZoom: Float = currentZoom.value
    private var replaySuppressionActive = false
    private var thermalVisibilitySuppressionActive = false

    fun bind() {
        if (isBound) return
        isBound = true

        combine(
            flightData,
            settings,
            thermalModeVisible,
            replayActive,
            currentMode
        ) { sample, modeSettings, modeVisible, replayRunning, mode ->
            RuntimeFrameSeed(
                isCircling = sample?.isCircling == true,
                settings = modeSettings,
                thermalModeVisible = modeVisible,
                replayActive = replayRunning,
                currentMode = mode
            )
        }.combine(currentZoom) { seed, zoom ->
            RuntimeFrame(
                isCircling = seed.isCircling,
                settings = seed.settings,
                thermalModeVisible = seed.thermalModeVisible,
                replayActive = seed.replayActive,
                currentMode = seed.currentMode,
                currentZoom = zoom,
                currentBaseStyle = currentBaseStyle.value
            )
        }.onEach { frame ->
            if (frame.replayActive) {
                if (!replaySuppressionActive) {
                    replaySuppressionActive = true
                    controller.reset()
                    clearRuntimeFlightModeOverride()
                    applyContrastMap(false)
                }
                lastObservedZoom = frame.currentZoom
                return@onEach
            }
            replaySuppressionActive = false
            if (!frame.thermalModeVisible && !thermalVisibilitySuppressionActive) {
                thermalVisibilitySuppressionActive = true
                clearRuntimeFlightModeOverride()
            } else if (frame.thermalModeVisible) {
                thermalVisibilitySuppressionActive = false
            }
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
            applyActions(actions, frame.currentBaseStyle)
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

    private fun applyActions(actions: List<ThermallingModeAction>, currentBaseStyle: String) {
        actions.forEach { action ->
            when (action) {
                is ThermallingModeAction.SwitchFlightMode -> {
                    if (action.mode == FlightMode.THERMAL) {
                        applyRuntimeFlightMode(action.mode)
                    } else {
                        clearRuntimeFlightModeOverride()
                    }
                }
                is ThermallingModeAction.SetZoom -> applyZoom(action.zoom)
                is ThermallingModeAction.SetContrastMapEnabled -> {
                    if (action.enabled && MapStyleCatalog.isSatellite(currentBaseStyle)) {
                        return@forEach
                    }
                    applyContrastMap(action.enabled)
                }
            }
        }
    }

    private data class RuntimeFrame(
        val isCircling: Boolean,
        val settings: ThermallingModeSettings,
        val thermalModeVisible: Boolean,
        val replayActive: Boolean,
        val currentMode: FlightMode,
        val currentZoom: Float,
        val currentBaseStyle: String
    )

    private data class RuntimeFrameSeed(
        val isCircling: Boolean,
        val settings: ThermallingModeSettings,
        val thermalModeVisible: Boolean,
        val replayActive: Boolean,
        val currentMode: FlightMode
    )
}
