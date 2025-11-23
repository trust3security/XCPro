package com.example.xcpro.tasks

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Bottom sheet state enum for task UI components
 * Temporarily extracted to allow compilation without full UI implementation
 */
enum class BottomSheetState {
    MINIMIZED,
    HALF_EXPANDED,
    FULLY_EXPANDED
}

/**
 * Task minimized indicator - shows current waypoint and navigation controls
 * Displays as a pill-shaped card at the top of the screen when task bottom sheet is hidden
 */
@Composable
fun TaskMinimizedIndicator(
    task: Task,
    taskManager: TaskManagerCoordinator,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentGPSLocation: Pair<Double, Double>? = null // Real-time GPS position for live distance updates
) {
    if (task.waypoints.isNotEmpty()) {
        // Get current waypoint name and truncate if too long
        val currentWaypoint = taskManager.getCurrentLegWaypoint()
        val waypointName = currentWaypoint?.title ?: "Unknown"
        val displayName = if (waypointName.length > 18) {
            "${waypointName.take(15)}..."
        } else {
            waypointName
        }

        Card(
            onClick = onClick,
            modifier = modifier
                .widthIn(max = 300.dp) // Increased by 25% from 240dp for better readability
                .padding(horizontal = 5.dp, vertical = 10.dp), // Increased padding for better touch target
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.3f) // 70% transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(50.dp) // Pill shape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous leg button - LEFT EDGE
                Box(
                    modifier = Modifier
                        .size(60.dp) // Increased by 25% from 48dp
                        .clickable { taskManager.setActiveLeg(taskManager.currentLeg - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = "Previous Leg",
                        tint = Color.Black,
                        modifier = Modifier.size(45.dp) // Increased by 25% from 36dp
                    )
                }

                // Center content - show waypoint name and leg distance
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Determine waypoint color based on position
                    val waypointColor = when {
                        taskManager.currentLeg == 0 -> Color(0xFF4CAF50) // Green for Start
                        taskManager.currentLeg == task.waypoints.size - 1 -> Color(0xFFF44336) // Red for Finish
                        else -> Color(0xFF2196F3) // Blue for Turn Points
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge, // Increased from bodyMedium for better readability
                        color = waypointColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Show real-time distance to current waypoint (updates live as pilot flies)
                    val distanceToWaypoint = if (currentGPSLocation != null) {
                        taskManager.calculateDistanceToCurrentWaypoint(
                            currentGPSLocation.first,   // Current GPS latitude
                            currentGPSLocation.second   // Current GPS longitude
                        )
                    } else null

                    // Display live distance (e.g., "12.3 km" - updates every second)
                    if (distanceToWaypoint != null && distanceToWaypoint > 0) {
                        Text(
                            text = "${"%.1f".format(Locale.getDefault(), distanceToWaypoint)} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Next leg button - RIGHT EDGE
                Box(
                    modifier = Modifier
                        .size(60.dp) // Increased by 25% from 48dp
                        .clickable { taskManager.setActiveLeg(taskManager.currentLeg + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = "Next Leg",
                        tint = Color.Black,
                        modifier = Modifier.size(45.dp) // Increased by 25% from 36dp
                    )
                }
            }
        }
    }
}

/**
 * Temporary stub for TaskStatsSection
 */
@androidx.compose.runtime.Composable
fun TaskStatsSection(
    task: Task,
    taskType: TaskType,
    taskManager: TaskManagerCoordinator
) {
    androidx.compose.material3.Text("Task statistics: ${task.waypoints.size} waypoints")
}

