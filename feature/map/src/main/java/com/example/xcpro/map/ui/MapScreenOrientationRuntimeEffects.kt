package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.MapOrientationManager

internal fun applyOrientationFlightModeSelection(
    orientationManager: MapOrientationManager,
    currentFlightModeSelection: FlightModeSelection
) {
    orientationManager.setFlightMode(currentFlightModeSelection)
}

@Composable
internal fun MapScreenOrientationRuntimeEffects(
    currentFlightModeSelection: FlightModeSelection,
    orientationManager: MapOrientationManager
) {
    LaunchedEffect(currentFlightModeSelection) {
        applyOrientationFlightModeSelection(
            orientationManager = orientationManager,
            currentFlightModeSelection = currentFlightModeSelection
        )
    }
}
