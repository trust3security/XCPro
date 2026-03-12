package com.example.xcpro.map

import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.qnh.QnhCalibrationState

data class MapUiState(
    val unitsPreferences: UnitsPreferences = UnitsPreferences(),
    val waypoints: List<WaypointData> = emptyList(),
    val isLoadingWaypoints: Boolean = false,
    val waypointError: MapWaypointError? = null,
    val isUiEditMode: Boolean = false,
    val isDrawerOpen: Boolean = false,
    val hideBallastPill: Boolean = false,
    val qnhCalibrationState: QnhCalibrationState = QnhCalibrationState.Idle,
    val weGlideUploadPrompt: WeGlideUploadPromptUiState? = null
)

data class WeGlideUploadPromptUiState(
    val localFlightId: String,
    val fileName: String,
    val profileName: String?,
    val aircraftName: String
)

sealed interface MapUiEvent {
    data object RefreshWaypoints : MapUiEvent
    data object ToggleUiEditMode : MapUiEvent
    data class SetUiEditMode(val enabled: Boolean) : MapUiEvent
    data object ToggleDrawer : MapUiEvent
    data object OpenDrawer : MapUiEvent
    data class SetDrawerOpen(val isOpen: Boolean) : MapUiEvent
}

sealed interface MapUiEffect {
    data class ShowToast(val message: String) : MapUiEffect
    data object OpenDrawer : MapUiEffect
    data object CloseDrawer : MapUiEffect
}
