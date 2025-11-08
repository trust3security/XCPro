package com.example.xcpro.screens.overlays

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.profiles.FlightModeIndicator
import com.example.xcpro.tasks.TaskMapOverlay
import com.example.xcpro.tasks.TaskManagerCoordinator
import org.maplibre.android.maps.MapLibreMap

@Composable
fun MapOverlayManager(
    mapLibreMap: MapLibreMap?,
    taskManager: TaskManagerCoordinator,
    currentMode: com.example.xcpro.common.flight.FlightMode,
    onModeChange: (com.example.xcpro.common.flight.FlightMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // Task Map Overlay (shows task course lines, turnpoints, etc.)
        TaskMapOverlay(
            taskManager = taskManager,
            mapLibreMap = mapLibreMap,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1.5f)
        )

        // Flight Mode Indicator (Top Left)
        FlightModeIndicator(
            currentMode = currentMode,
            onModeChange = onModeChange,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp)
                .zIndex(5f)
        )

        // ❌ REMOVED: Duplicate compass widget (MapScreen.kt has the active compass implementation)
    }
}

@Composable
fun OverlayVisibilityController(
    showTaskOverlay: Boolean = true,
    showCompass: Boolean = true,
    showFlightModeIndicator: Boolean = true,
    content: @Composable (OverlayVisibility) -> Unit
) {
    val visibility = remember(showTaskOverlay, showCompass, showFlightModeIndicator) {
        OverlayVisibility(
            taskOverlay = showTaskOverlay,
            compass = showCompass,
            flightModeIndicator = showFlightModeIndicator
        )
    }

    content(visibility)
}

data class OverlayVisibility(
    val taskOverlay: Boolean = true,
    val compass: Boolean = true,
    val flightModeIndicator: Boolean = true
)

@Composable
fun ConditionalMapOverlayManager(
    mapLibreMap: MapLibreMap?,
    taskManager: TaskManagerCoordinator,
    currentMode: com.example.xcpro.common.flight.FlightMode,
    onModeChange: (com.example.xcpro.common.flight.FlightMode) -> Unit,
    visibility: OverlayVisibility = OverlayVisibility(),
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // Task Map Overlay - conditionally shown
        if (visibility.taskOverlay) {
            TaskMapOverlay(
                taskManager = taskManager,
                mapLibreMap = mapLibreMap,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1.5f)
            )
        }

        // Flight Mode Indicator - conditionally shown
        if (visibility.flightModeIndicator) {
            FlightModeIndicator(
                currentMode = currentMode,
                onModeChange = onModeChange,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp)
                    .zIndex(5f)
            )
        }

        // ❌ REMOVED: Duplicate compass widget (MapScreen.kt has the active compass implementation)
    }
}


