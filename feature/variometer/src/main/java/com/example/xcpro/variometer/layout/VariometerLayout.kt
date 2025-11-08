package com.example.xcpro.variometer.layout

import androidx.compose.ui.geometry.Offset

/**
 * Immutable snapshot of the variometer widget layout persisted on disk.
 */
data class VariometerLayout(
    val offset: Offset,
    val sizePx: Float,
    val hasPersistedOffset: Boolean,
    val hasPersistedSize: Boolean
)

/**
 * UI-facing state exposed to Compose consumers via the Map layer ViewModel.
 */
data class VariometerUiState(
    val offset: Offset = Offset.Zero,
    val sizePx: Float = 0f,
    val isInitialized: Boolean = false
)
