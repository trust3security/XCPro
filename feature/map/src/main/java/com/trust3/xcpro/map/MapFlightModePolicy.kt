package com.trust3.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.common.flight.FlightMode

internal enum class MapFlightModeSource {
    REQUESTED,
    RUNTIME_OVERRIDE,
    FALLBACK_CRUISE
}

internal data class MapFlightModeUiState(
    val requestedMode: FlightMode,
    val runtimeOverrideMode: FlightMode?,
    val effectiveMode: FlightMode,
    val effectiveModeSource: MapFlightModeSource,
    val visibleModes: List<FlightMode>,
    val requestedModeVisible: Boolean,
    val runtimeOverrideVisible: Boolean
)

internal fun resolveMapFlightModeUiState(
    requestedMode: FlightMode,
    runtimeOverrideMode: FlightMode?,
    modeVisibilities: Map<FlightModeSelection, Boolean>
): MapFlightModeUiState {
    val visibleModes = orderedMapFlightModes.filter { mode ->
        when (mode.toPolicySelection()) {
            FlightModeSelection.CRUISE -> true
            FlightModeSelection.THERMAL -> modeVisibilities[FlightModeSelection.THERMAL] != false
            FlightModeSelection.FINAL_GLIDE -> modeVisibilities[FlightModeSelection.FINAL_GLIDE] != false
        }
    }
    val runtimeOverrideVisible = runtimeOverrideMode != null && runtimeOverrideMode in visibleModes
    val requestedModeVisible = requestedMode in visibleModes
    val effectiveMode = when {
        runtimeOverrideVisible -> runtimeOverrideMode ?: FlightMode.CRUISE
        requestedModeVisible -> requestedMode
        else -> FlightMode.CRUISE
    }
    val source = when {
        runtimeOverrideVisible -> MapFlightModeSource.RUNTIME_OVERRIDE
        requestedModeVisible -> MapFlightModeSource.REQUESTED
        else -> MapFlightModeSource.FALLBACK_CRUISE
    }
    return MapFlightModeUiState(
        requestedMode = requestedMode,
        runtimeOverrideMode = runtimeOverrideMode,
        effectiveMode = effectiveMode,
        effectiveModeSource = source,
        visibleModes = visibleModes,
        requestedModeVisible = requestedModeVisible,
        runtimeOverrideVisible = runtimeOverrideVisible
    )
}

internal val orderedMapFlightModes: List<FlightMode> =
    listOf(FlightMode.CRUISE, FlightMode.THERMAL, FlightMode.FINAL_GLIDE)

private fun FlightMode.toPolicySelection(): FlightModeSelection =
    when (this) {
        FlightMode.CRUISE -> FlightModeSelection.CRUISE
        FlightMode.THERMAL -> FlightModeSelection.THERMAL
        FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
    }
