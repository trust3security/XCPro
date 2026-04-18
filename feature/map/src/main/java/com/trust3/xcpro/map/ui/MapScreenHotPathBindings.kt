package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.StateFlow

internal data class MapScreenHotPathBindings(
    val currentZoom: StateFlow<Float>,
    val currentLocation: StateFlow<MapLocationUiModel?>,
    val orientationFlow: StateFlow<OrientationData>
)

@Composable
internal fun rememberMapScreenHotPathBindings(
    mapViewModel: MapScreenViewModel,
    orientationFlow: StateFlow<OrientationData>
): MapScreenHotPathBindings = remember(mapViewModel, orientationFlow) {
    MapScreenHotPathBindings(
        currentZoom = mapViewModel.mapState.currentZoom,
        currentLocation = mapViewModel.mapLocation,
        orientationFlow = orientationFlow
    )
}
