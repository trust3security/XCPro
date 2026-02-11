package com.example.xcpro.map

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.components.AirspaceSettingsContent

/**
 * Centralized modal management for MapScreen
 * Handles Airspace Settings Modal and other modal overlays
 */
class MapModalManager(
    internal val mapState: MapScreenState
) {
    companion object {
        private const val TAG = "MapModalManager"
    }

    // Modal state
    var showAirspaceSettings by mutableStateOf(false)
        private set

    /**
     * Show airspace settings modal
     */
    fun showAirspaceSettingsModal() {
        showAirspaceSettings = true
        Log.d(TAG, "Airspace settings modal opened")
    }

    /**
     * Hide airspace settings modal
     */
    fun hideAirspaceSettingsModal() {
        showAirspaceSettings = false
        Log.d(TAG, "Airspace settings modal closed")
    }

    /**
     * Handle back gesture - close current modal
     */
    fun handleBackGesture(): Boolean {
        return when {
            showAirspaceSettings -> {
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
        return showAirspaceSettings
    }

    /**
     * Get status for debugging
     */
    fun getModalStatus(): String {
        return buildString {
            append("MapModalManager Status:\n")
            append("- Airspace Settings: ${if (showAirspaceSettings) "Open" else "Closed"}\n")
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
        if (modalManager.showAirspaceSettings) {
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
