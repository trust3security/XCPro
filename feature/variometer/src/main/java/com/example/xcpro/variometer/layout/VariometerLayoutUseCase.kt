package com.example.xcpro.variometer.layout

import com.example.xcpro.core.common.geometry.OffsetPx
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns variometer widget layout state and persistence.
 * Invariants: state is exposed as StateFlow and persisted only via widget preferences.
 */
class VariometerLayoutUseCase @Inject constructor(
    private val repository: VariometerWidgetRepository
) {
    private val _state = MutableStateFlow(VariometerUiState())
    val state: StateFlow<VariometerUiState> = _state.asStateFlow()
    private var activeProfileId: String = VariometerWidgetRepository.DEFAULT_PROFILE_ID

    fun setActiveProfileId(profileId: String) {
        val resolved = profileId.trim().ifBlank { VariometerWidgetRepository.DEFAULT_PROFILE_ID }
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        _state.value = VariometerUiState()
    }

    fun ensureLayout(
        screenWidthPx: Float,
        screenHeightPx: Float,
        defaultSizePx: Float,
        minSizePx: Float,
        maxSizePx: Float
    ) {
        if (_state.value.isInitialized) return
        val centeredOffset = OffsetPx(
            x = ((screenWidthPx - defaultSizePx) / 2f).coerceAtLeast(0f),
            y = ((screenHeightPx - defaultSizePx) / 2f).coerceAtLeast(0f)
        )
        val persistedLayout = repository.load(
            profileId = activeProfileId,
            defaultOffset = centeredOffset,
            defaultSizePx = defaultSizePx
        )
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
            repository.saveOffset(activeProfileId, boundedOffset)
        }
        if (!persistedLayout.hasPersistedSize) {
            repository.saveSize(activeProfileId, sanitizedSize)
        }
    }

    fun onOffsetCommitted(
        offset: OffsetPx,
        screenWidthPx: Float,
        screenHeightPx: Float
    ) {
        if (!_state.value.isInitialized) return
        val clamped = clampOffset(offset, _state.value.sizePx, screenWidthPx, screenHeightPx)
        _state.update { it.copy(offset = clamped) }
        repository.saveOffset(activeProfileId, clamped)
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
        repository.saveSize(activeProfileId, clampedSize)
        repository.saveOffset(activeProfileId, clampedOffset)
    }

    private fun clampOffset(
        offset: OffsetPx,
        sizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float
    ): OffsetPx {
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)
        return OffsetPx(
            x = offset.x.coerceIn(0f, maxX),
            y = offset.y.coerceIn(0f, maxY)
        )
    }
}
