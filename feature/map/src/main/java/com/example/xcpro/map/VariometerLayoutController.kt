package com.example.xcpro.map
/**
 * Variometer widget layout controller.
 * Invariants: layout state is persisted to widget prefs and exposed read-only.
 */


import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import com.example.xcpro.variometer.layout.VariometerUiState
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns variometer widget layout state and persistence.
 * Invariants: state is exposed as StateFlow and persisted only via widget preferences.
 */
internal class VariometerLayoutController(
    sharedPreferences: SharedPreferences
) {
    private val repository = VariometerWidgetRepository(sharedPreferences)
    private val _state = MutableStateFlow(VariometerUiState())
    val state: StateFlow<VariometerUiState> = _state.asStateFlow()

    fun ensureLayout(
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        if (_state.value.isInitialized) return
        val centeredOffset = Offset(
            x = ((screenWidthPx - defaultSizePx) / 2f).coerceAtLeast(0f),
            y = ((screenHeightPx - defaultSizePx) / 2f).coerceAtLeast(0f)
        )
        val persistedLayout = repository.load(centeredOffset, defaultSizePx)
        val sanitizedSize = persistedLayout.sizePx.coerceIn(minSizePx, maxSizePx)
        val targetOffset = if (persistedLayout.hasPersistedOffset) {
            persistedLayout.offset
        } else {
            centeredOffset
        }
        val boundedOffset = clampOffset(targetOffset, sanitizedSize, screenWidthPx, screenHeightPx)
        _state.value = VariometerUiState(
            offset = boundedOffset,
            sizePx = sanitizedSize,
            isInitialized = true
        )
        if (!persistedLayout.hasPersistedOffset) {
            repository.saveOffset(boundedOffset)
        }
        if (!persistedLayout.hasPersistedSize) {
            repository.saveSize(sanitizedSize)
        }
    }

    fun onOffsetCommitted(
        offset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        if (!_state.value.isInitialized) return
        val clamped = clampOffset(offset, _state.value.sizePx, screenWidthPx, screenHeightPx)
        _state.update { it.copy(offset = clamped) }
        repository.saveOffset(clamped)
    }

    fun onSizeCommitted(
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        if (!_state.value.isInitialized) return
        val clampedSize = sizePx.coerceIn(minSizePx, maxSizePx)
        val clampedOffset = clampOffset(_state.value.offset, clampedSize, screenWidthPx, screenHeightPx)
        _state.update { it.copy(sizePx = clampedSize, offset = clampedOffset) }
        repository.saveSize(clampedSize)
        repository.saveOffset(clampedOffset)
    }

    private fun clampOffset(
        offset: Offset,
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): Offset {
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)
        return Offset(
            x = offset.x.coerceIn(0f, maxX),
            y = offset.y.coerceIn(0f, maxY)
        )
    }
}
