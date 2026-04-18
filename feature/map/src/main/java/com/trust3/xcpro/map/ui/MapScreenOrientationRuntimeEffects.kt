package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.example.dfcards.FlightModeSelection

@Composable
internal fun MapScreenOrientationRuntimeEffects(
    currentFlightModeSelection: FlightModeSelection,
    onApplyOrientationFlightModeSelection: (FlightModeSelection) -> Unit
) {
    val applyOrientationFlightModeSelection = rememberUpdatedState(onApplyOrientationFlightModeSelection)
    LaunchedEffect(currentFlightModeSelection) {
        applyOrientationFlightModeSelection.value(currentFlightModeSelection)
    }
}
