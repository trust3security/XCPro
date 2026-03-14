package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.StateFlow

internal data class MapScreenHotPathBindings(
    val currentZoom: StateFlow<Float>,
    val currentLocation: StateFlow<MapLocationUiModel?>,
    val orientationFlow: StateFlow<OrientationData>
)

@Composable
internal fun rememberMapScreenHotPathBindings(
    mapViewModel: MapScreenViewModel,
    orientationManager: MapOrientationManager
): MapScreenHotPathBindings = remember(mapViewModel, orientationManager) {
    MapScreenHotPathBindings(
        currentZoom = mapViewModel.mapState.currentZoom,
        currentLocation = mapViewModel.mapLocation,
        orientationFlow = orientationManager.orientationFlow
    )
}
