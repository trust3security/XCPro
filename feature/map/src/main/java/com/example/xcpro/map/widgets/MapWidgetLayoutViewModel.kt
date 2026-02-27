package com.example.xcpro.map.widgets

import androidx.lifecycle.ViewModel
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class MapWidgetLayoutViewModel @Inject constructor(
    private val useCase: MapWidgetLayoutUseCase
) : ViewModel() {

    private val _offsets = MutableStateFlow<MapWidgetOffsets?>(null)
    val offsets: StateFlow<MapWidgetOffsets?> = _offsets.asStateFlow()

    fun loadLayout(screenWidthPx: Float, screenHeightPx: Float, density: DensityScale) {
        _offsets.value = useCase.loadLayout(screenWidthPx, screenHeightPx, density)
    }

    fun updateOffset(widgetId: MapWidgetId, offset: OffsetPx) {
        _offsets.update { current ->
            current?.let { updateOffsets(it, widgetId, offset) }
        }
        useCase.saveOffset(widgetId, offset)
    }

    private fun updateOffsets(
        current: MapWidgetOffsets,
        widgetId: MapWidgetId,
        offset: OffsetPx
    ): MapWidgetOffsets = when (widgetId) {
        MapWidgetId.SIDE_HAMBURGER -> current.copy(sideHamburger = offset)
        MapWidgetId.FLIGHT_MODE -> current.copy(flightMode = offset)
        MapWidgetId.SETTINGS_SHORTCUT -> current.copy(settingsShortcut = offset)
        MapWidgetId.BALLAST -> current.copy(ballast = offset)
    }
}
