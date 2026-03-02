package com.example.xcpro.map.widgets

import androidx.lifecycle.ViewModel
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.OffsetPx
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MapWidgetLayoutViewModel @Inject constructor(
    private val useCase: MapWidgetLayoutUseCase
) : ViewModel() {

    private val _offsets = MutableStateFlow<MapWidgetOffsets?>(null)
    val offsets: StateFlow<MapWidgetOffsets?> = _offsets.asStateFlow()

    fun loadLayout(screenWidthPx: Float, screenHeightPx: Float, density: DensityScale) {
        _offsets.value = useCase.loadLayout(screenWidthPx, screenHeightPx, density)
    }

    fun updateOffset(
        widgetId: MapWidgetId,
        offset: OffsetPx,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        val current = _offsets.value
        if (current == null) {
            useCase.saveOffset(widgetId, offset)
            return
        }
        _offsets.value = useCase.commitOffset(
            current = current,
            widgetId = widgetId,
            offset = offset,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
    }

    fun updateSize(
        widgetId: MapWidgetId,
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: DensityScale
    ) {
        val current = _offsets.value
        if (current == null) {
            useCase.saveSizePx(widgetId, sizePx)
            return
        }
        _offsets.value = useCase.commitSize(
            current = current,
            widgetId = widgetId,
            sizePx = sizePx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
    }
}
