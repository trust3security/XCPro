package com.example.xcpro.map.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.ui1.icons.LocationSailplane
import org.maplibre.android.camera.CameraUpdateFactory

@Composable
fun MapActionButtons(
    mapState: MapScreenState,
    taskManager: TaskManagerCoordinator,
    taskScreenManager: MapTaskScreenManager,
    currentLocation: GPSData?,
    onToggleDistanceCircles: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                top = 24.dp,
                start = 24.dp,
                end = 24.dp,
                bottom = 80.dp
            )
            // ❌ REMOVED: .zIndex(50f) was blocking hamburger menu at zIndex(4f)
            // Individual buttons have their own z-index values as needed
    ) {
        // Recenter Button
        if (mapState.showRecenterButton && currentLocation != null) {
            RecenterButton(
                mapState = mapState,
                currentLocation = currentLocation,
                context = context,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // Return Button
        if (mapState.showReturnButton) {
            ReturnButton(
                mapState = mapState,
                onReturn = onReturn,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Distance Circles Button - lower z-index when bottom sheet is open
        DistanceCirclesButton(
            mapState = mapState,
            onToggle = onToggleDistanceCircles,
            isBottomSheetVisible = taskScreenManager.showTaskBottomSheet || taskScreenManager.showTaskScreen,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun RecenterButton(
    mapState: MapScreenState,
    currentLocation: GPSData,
    context: Context,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 16.dp)
            .size(48.dp)
    ) {
        FloatingActionButton(
            onClick = {
                Log.d("MapActionButtons", "📍 Recenter button clicked")
                mapState.mapLibreMap?.let { map ->
                    val currentPosition = map.cameraPosition
                    val newCameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(currentLocation.latLng)
                        .zoom(currentPosition.zoom)
                        .bearing(currentPosition.bearing)
                        .tilt(currentPosition.tilt)
                        .build()

                    // Position user at 65% from top
                    val screenHeight = context.resources.displayMetrics.heightPixels
                    val topPadding = (screenHeight * 0.35).toInt()
                    val padding = intArrayOf(0, topPadding, 0, 0)

                    map.animateCamera(CameraUpdateFactory.newCameraPosition(newCameraPosition), 800)
                    map.setPadding(padding[0], padding[1], padding[2], padding[3])

                    mapState.showRecenterButton = false
                    Log.d("MapActionButtons", "✅ Recentered to current location")
                }
            },
            modifier = Modifier.matchParentSize(),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = LocationSailplane,
                contentDescription = "Recenter",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ReturnButton(
    mapState: MapScreenState,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(top = 16.dp, end = 16.dp)
            .size(48.dp)
    ) {
        FloatingActionButton(
            onClick = {
                Log.d("MapActionButtons", "🔄 Return button clicked")
                onReturn()
                mapState.showReturnButton = false
            },
            modifier = Modifier.matchParentSize(),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Return to Previous",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun DistanceCirclesButton(
    mapState: MapScreenState,
    onToggle: () -> Unit,
    isBottomSheetVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(bottom = 80.dp, end = 8.dp) // ✅ Moved up and closer to right edge
            .size(48.dp)
            .zIndex(if (isBottomSheetVisible) 10f else 50f) // Lower z-index when bottom sheet is visible
    ) {
        FloatingActionButton(
            onClick = {
                Log.d("MapActionButtons", "⭕ Distance circles button clicked")
                onToggle()
            },
            modifier = Modifier.matchParentSize(),
            containerColor = if (mapState.showDistanceCircles) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) // ✅ 30% more transparent when enabled
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) // ✅ 50% more transparent when disabled
            },
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            ThreeCirclesIcon(
                color = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ThreeCirclesIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val strokeWidth = 2.dp.toPx()

        // Draw 3 concentric circles with increasing radii
        val maxRadius = size.minDimension / 2f - strokeWidth

        // Inner circle (33% of max radius)
        drawCircle(
            color = color,
            radius = maxRadius * 0.33f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // Middle circle (66% of max radius)
        drawCircle(
            color = color,
            radius = maxRadius * 0.66f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // Outer circle (100% of max radius)
        drawCircle(
            color = color,
            radius = maxRadius,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )
    }
}