package com.example.xcpro.variometer.layout

import com.example.xcpro.core.common.geometry.OffsetPx

/**
 * Immutable snapshot of the variometer widget layout persisted on disk.
 */
data class VariometerLayout(
    val offset: OffsetPx,
    val sizePx: Float,
    val hasPersistedOffset: Boolean,
    val hasPersistedSize: Boolean
)

/**
 * UI-facing state exposed to Compose consumers via the Map layer ViewModel.
 */
data class VariometerUiState(
    val offset: OffsetPx = OffsetPx.Zero,
    val sizePx: Float = 0f,
    val isInitialized: Boolean = false
)
