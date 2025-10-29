package com.example.xcpro.map

import com.example.xcpro.WaypointData
import com.example.xcpro.common.units.UnitsPreferences

data class MapUiState(
    val unitsPreferences: UnitsPreferences = UnitsPreferences(),
    val waypoints: List<WaypointData> = emptyList(),
    val isLoadingWaypoints: Boolean = false,
    val waypointError: String? = null
)

sealed interface MapUiEvent {
    data object RefreshWaypoints : MapUiEvent
}

sealed interface MapUiEffect {
    data class ShowToast(val message: String) : MapUiEffect
}
