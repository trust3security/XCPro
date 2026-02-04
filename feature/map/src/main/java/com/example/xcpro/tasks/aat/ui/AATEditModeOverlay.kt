package com.example.xcpro.tasks.aat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.map.AATEditSession
import com.example.xcpro.tasks.aat.map.AATEditState
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * AAT Edit Mode Overlay - Phase 2 Visual Feedback
 *
 * Provides visual feedback and controls when editing AAT areas.
 * Shows edit state, target point info, and action buttons.
 *
 * Features:
 * - Animated transitions between states
 * - Area information display
 * - Target point coordinates and distance
 * - Save/Cancel/Reset controls
 * - Visual edit mode indicators
 *
 * Design:
 * - AAT green theme (#388E3C)
 * - Smooth animations
 * - Clear visual hierarchy
 * - Non-intrusive overlay
 */

@Composable
fun AATEditModeOverlay(
    editSession: AATEditSession,
    nowMs: Long,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    onResetToCenter: () -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation for edit mode appearance
    val editModeVisible by remember(editSession.isEditingArea) {
        derivedStateOf { editSession.isEditingArea }
    }

    AnimatedVisibility(
        visible = editModeVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeOut(),
        modifier = modifier
    ) {
        if (editSession.isEditingArea && editSession.focusedWaypoint != null) {
            EditModeOverlayContent(
                waypoint = editSession.focusedWaypoint!!,
                currentTargetPoint = editSession.currentTargetPoint!!,
                originalTargetPoint = editSession.originalTargetPoint!!,
                hasUnsavedChanges = editSession.hasUnsavedChanges,
                sessionDuration = editSession.sessionDurationMs(nowMs),
                onSaveChanges = onSaveChanges,
                onDiscardChanges = onDiscardChanges,
                onResetToCenter = onResetToCenter,
                onExitEditMode = onExitEditMode
            )
        }
    }
}

@Composable
private fun EditModeOverlayContent(
    waypoint: AATWaypoint,
    currentTargetPoint: AATLatLng,
    originalTargetPoint: AATLatLng,
    hasUnsavedChanges: Boolean,
    sessionDuration: Long,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    onResetToCenter: () -> Unit,
    onExitEditMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Edit mode header
        EditModeHeader(
            waypoint = waypoint,
            sessionDuration = sessionDuration
        )

        // Target point information
        TargetPointInfoCard(
            waypoint = waypoint,
            currentTargetPoint = currentTargetPoint,
            originalTargetPoint = originalTargetPoint
        )

        // Action buttons
        EditModeActionButtons(
            hasUnsavedChanges = hasUnsavedChanges,
            onSaveChanges = onSaveChanges,
            onDiscardChanges = onDiscardChanges,
            onResetToCenter = onResetToCenter,
            onExitEditMode = onExitEditMode
        )
    }
}

@Composable
private fun EditModeHeader(
    waypoint: AATWaypoint,
    sessionDuration: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF388E3C).copy(alpha = 0.9f) // AAT green
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing edit indicator
                    val pulseAnimation by rememberInfiniteTransition().animateFloat(
                        initialValue = 0.6f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(pulseAnimation)
                            .background(Color.White, CircleShape)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "EDITING: ${waypoint.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Tap outside to exit  Drag target point to move",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            // Session duration
            Text(
                text = "${sessionDuration / 1000}s",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun TargetPointInfoCard(
    waypoint: AATWaypoint,
    currentTargetPoint: AATLatLng,
    originalTargetPoint: AATLatLng
) {
    // Calculate distance from area center
    val distanceFromCenter = remember(currentTargetPoint, waypoint) {
        haversineDistance(
            waypoint.lat, waypoint.lon,
            currentTargetPoint.latitude, currentTargetPoint.longitude
        )
    }

    // Calculate distance moved from original
    val distanceMoved = remember(currentTargetPoint, originalTargetPoint) {
        haversineDistance(
            originalTargetPoint.latitude, originalTargetPoint.longitude,
            currentTargetPoint.latitude, currentTargetPoint.longitude
        )
    }

    val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0
    val remainingDistance = areaRadiusKm - distanceFromCenter

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = " Target Point Position",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF388E3C)
            )

            // Coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lat: ${String.format("%.6f", currentTargetPoint.latitude)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Lon: ${String.format("%.6f", currentTargetPoint.longitude)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Distance information
            InfoRow(
                label = "Distance from center:",
                value = "${String.format("%.2f", distanceFromCenter)} km",
                icon = Icons.Default.MyLocation
            )

            InfoRow(
                label = "Remaining area:",
                value = "${String.format("%.2f", remainingDistance)} km",
                icon = Icons.Default.RadioButtonUnchecked,
                valueColor = if (remainingDistance < 0.5) Color.Red else MaterialTheme.colorScheme.onSurface
            )

            if (distanceMoved > 0.01) { // 10 meters threshold
                InfoRow(
                    label = "Moved from original:",
                    value = "${String.format("%.2f", distanceMoved)} km",
                    icon = Icons.Default.OpenWith,
                    valueColor = Color(0xFF388E3C)
                )
            }

            // Progress bar for area usage
            LinearProgressIndicator(
                progress = { (distanceFromCenter / areaRadiusKm).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF388E3C),
                trackColor = Color(0xFF388E3C).copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF388E3C)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun EditModeActionButtons(
    hasUnsavedChanges: Boolean,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    onResetToCenter: () -> Unit,
    onExitEditMode: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Primary actions (Save/Cancel) - only show if changes exist
            AnimatedVisibility(
                visible = hasUnsavedChanges,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onSaveChanges,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF388E3C) // AAT green
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes")
                    }

                    OutlinedButton(
                        onClick = onDiscardChanges,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
            }

            // Secondary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onResetToCenter,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset Center")
                }

                OutlinedButton(
                    onClick = onExitEditMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exit Edit")
                }
            }
        }
    }
}

/**
 * Calculate distance between two points using Haversine formula
 */
private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusKm * c
}
