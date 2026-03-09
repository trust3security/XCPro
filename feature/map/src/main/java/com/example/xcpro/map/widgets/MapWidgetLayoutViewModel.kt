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
    private var activeProfileId: String = DEFAULT_PROFILE_ID

    fun setProfileId(profileId: String) {
        val resolved = profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        _offsets.value = null
    }

    fun loadLayout(screenWidthPx: Float, screenHeightPx: Float, density: DensityScale) {
        _offsets.value = useCase.loadLayout(
            profileId = activeProfileId,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
    }

    fun updateOffset(
        widgetId: MapWidgetId,
        offset: OffsetPx,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        val current = _offsets.value
        if (current == null) {
            useCase.saveOffset(activeProfileId, widgetId, offset)
            return
        }
        _offsets.value = useCase.commitOffset(
            profileId = activeProfileId,
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
            useCase.saveSizePx(activeProfileId, widgetId, sizePx)
            return
        }
        _offsets.value = useCase.commitSize(
            profileId = activeProfileId,
            current = current,
            widgetId = widgetId,
            sizePx = sizePx,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
    }

    private companion object {
        private const val DEFAULT_PROFILE_ID = "default-profile"
    }
}
