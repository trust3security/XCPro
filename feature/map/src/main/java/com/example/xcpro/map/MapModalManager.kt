package com.example.xcpro.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.components.AirspaceSettingsContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized modal management for MapScreen
 * Handles Airspace Settings Modal and other modal overlays
 */
class MapModalManager(
    internal val mapState: MapScreenState
) {
    private val _showAirspaceSettings = MutableStateFlow(false)
    val showAirspaceSettings: StateFlow<Boolean> = _showAirspaceSettings.asStateFlow()

    private val _showGeneralSettings = MutableStateFlow(false)
    val showGeneralSettings: StateFlow<Boolean> = _showGeneralSettings.asStateFlow()

    private fun setModalState(
        showGeneralSettings: Boolean,
        showAirspaceSettings: Boolean
    ) {
        _showGeneralSettings.value = showGeneralSettings
        _showAirspaceSettings.value = showAirspaceSettings
    }

    /**
     * Show airspace settings modal
     */
    fun showAirspaceSettingsModal() {
        setModalState(
            showGeneralSettings = false,
            showAirspaceSettings = true
        )
    }

    /**
     * Hide airspace settings modal
     */
    fun hideAirspaceSettingsModal() {
        _showAirspaceSettings.value = false
    }

    fun showGeneralSettingsModal() {
        setModalState(
            showGeneralSettings = true,
            showAirspaceSettings = false
        )
    }

    fun hideGeneralSettingsModal() {
        _showGeneralSettings.value = false
    }

    /**
     * Handle back gesture - close current modal
     */
    fun handleBackGesture(): Boolean {
        return when {
            _showGeneralSettings.value -> {
                hideGeneralSettingsModal()
                true
            }
            _showAirspaceSettings.value -> {
                hideAirspaceSettingsModal()
                true
            }
            else -> false
        }
    }

    /**
     * Check if any modal is currently open
     */
    fun isAnyModalOpen(): Boolean {
        return _showGeneralSettings.value || _showAirspaceSettings.value
    }

    /**
     * Get status for debugging
     */
    fun getModalStatus(): String {
        return buildString {
            append("MapModalManager Status:\n")
            append("- General Settings: ${if (_showGeneralSettings.value) "Open" else "Closed"}\n")
            append("- Airspace Settings: ${if (_showAirspaceSettings.value) "Open" else "Closed"}\n")
        }
    }
}

/**
 * Compose components for modal UI
 */
object MapModalUI {

    /**
     * Airspace Settings Modal component
     */
    @Composable
    fun AirspaceSettingsModalOverlay(
        modalManager: MapModalManager,
        modifier: Modifier = Modifier
    ) {
        val showAirspaceSettings by modalManager.showAirspaceSettings.collectAsStateWithLifecycle()
        if (showAirspaceSettings) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .zIndex(80f)
                    .pointerInput(Unit) { detectTapGestures {} }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.6f)
                        .align(Alignment.Center)
                        .background(Color.White.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    AirspaceSettingsContent(
                        onDismiss = { modalManager.hideAirspaceSettingsModal() }
                    )
                }
            }
        }
    }
}
