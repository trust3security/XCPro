package com.example.xcpro.map

import com.example.xcpro.WaypointData
import com.example.xcpro.common.units.UnitsPreferences

data class MapUiState(
    val unitsPreferences: UnitsPreferences = UnitsPreferences(),
    val waypoints: List<WaypointData> = emptyList(),
    val isLoadingWaypoints: Boolean = false,
    val waypointError: String? = null,
    val isUiEditMode: Boolean = false,
    val isDrawerOpen: Boolean = false
)

sealed interface MapUiEvent {
    data object RefreshWaypoints : MapUiEvent
    data object ToggleUiEditMode : MapUiEvent
    data class SetUiEditMode(val enabled: Boolean) : MapUiEvent
    data object ToggleDrawer : MapUiEvent
    data class SetDrawerOpen(val isOpen: Boolean) : MapUiEvent
}

sealed interface MapUiEffect {
    data class ShowToast(val message: String) : MapUiEffect
    data object OpenDrawer : MapUiEffect
    data object CloseDrawer : MapUiEffect
}
