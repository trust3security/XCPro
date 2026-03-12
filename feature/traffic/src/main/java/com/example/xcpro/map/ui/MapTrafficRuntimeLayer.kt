package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.OgnTrailSelectionViewModel
import com.example.xcpro.map.TrafficMapCoordinate

data class MapTrafficRuntimeState(
    val contentUiState: MapTrafficContentUiState,
    val onTrailAircraftSelectionChanged: (String, Boolean) -> Unit
)

@Composable
fun rememberMapTrafficRuntimeState(
    traffic: MapTrafficUiBinding,
    debugPanelsEnabled: Boolean
): MapTrafficRuntimeState {
    val ognTrailSelectionViewModel: OgnTrailSelectionViewModel = hiltViewModel()
    val selectedTrailAircraftKeys = ognTrailSelectionViewModel.selectedTrailAircraftKeys
        .collectAsStateWithLifecycle()
    val contentUiState = rememberMapTrafficContentUiState(
        traffic = traffic,
        selectedTrailAircraftKeys = selectedTrailAircraftKeys.value,
        debugPanelsEnabled = debugPanelsEnabled
    )
    val onTrailAircraftSelectionChanged = remember(ognTrailSelectionViewModel) {
        ognTrailSelectionViewModel::setTrailAircraftSelected
    }
    return remember(contentUiState, onTrailAircraftSelectionChanged) {
        MapTrafficRuntimeState(
            contentUiState = contentUiState,
            onTrailAircraftSelectionChanged = onTrailAircraftSelectionChanged
        )
    }
}

@Composable
fun BoxScope.MapTrafficRuntimeLayer(
    traffic: MapTrafficUiBinding,
    runtimeState: MapTrafficRuntimeState,
    ownshipCoordinate: TrafficMapCoordinate?,
    unitsPreferences: UnitsPreferences,
    trafficActions: MapTrafficUiActions
) {
    MapTrafficPanelsAndSheetsLayer(
        traffic = traffic,
        uiState = runtimeState.contentUiState,
        ownshipCoordinate = ownshipCoordinate,
        unitsPreferences = unitsPreferences,
        onTrailAircraftSelectionChanged = runtimeState.onTrailAircraftSelectionChanged,
        trafficActions = trafficActions
    )
}
