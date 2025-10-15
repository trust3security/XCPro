package com.example.xcpro.tasks.racing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.TaskWaypoint
import com.example.xcpro.tasks.TaskManagerCoordinator

private const val TAG = "RacingStartPointSelector"

/**
 * Racing Start Point Configuration UI
 *
 * Handles all UI for configuring racing start points including:
 * - Start Line
 * - Start Cylinder
 * - FAI Start Sector
 *
 * Extracted from RacingTaskPointTypeSelector to maintain 500-line file limit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RacingStartPointSelector(
    selectedStartType: RacingStartPointType,
    gateWidth: String,
    waypoint: TaskWaypoint,
    nextWaypoint: TaskWaypoint?,
    taskManager: TaskManagerCoordinator,
    onStartTypeChange: (RacingStartPointType) -> Unit,
    onGateWidthChange: (String) -> Unit
) {
    Log.d(TAG, "🚀 Rendering Racing Start Point selector - selectedStartType: ${selectedStartType.displayName}")

    Text(
        text = "Racing Start Point Configuration",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )

    // Button-based start type selector
    RacingStartPointButtonSelector(
        selectedType = selectedStartType,
        onTypeChange = onStartTypeChange
    )

    // Show parameter input for selected type
    when (selectedStartType) {
        RacingStartPointType.START_LINE -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var startLineTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                    OutlinedTextField(
                        value = startLineTextFieldValue,
                        onValueChange = { newValue ->
                            startLineTextFieldValue = newValue
                            onGateWidthChange(newValue.text)
                        },
                        label = { Text("Start Line Length (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && startLineTextFieldValue.selection.collapsed) {
                                    startLineTextFieldValue = startLineTextFieldValue.copy(
                                        selection = TextRange(0, startLineTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Length of the start line in kilometers") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
        RacingStartPointType.START_CYLINDER -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var startCylinderTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                    OutlinedTextField(
                        value = startCylinderTextFieldValue,
                        onValueChange = { newValue ->
                            startCylinderTextFieldValue = newValue
                            onGateWidthChange(newValue.text)
                        },
                        label = { Text("Cylinder Radius (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && startCylinderTextFieldValue.selection.collapsed) {
                                    startCylinderTextFieldValue = startCylinderTextFieldValue.copy(
                                        selection = TextRange(0, startCylinderTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Radius of the start cylinder in kilometers") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
        RacingStartPointType.FAI_START_SECTOR -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var faiStartSectorTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                    OutlinedTextField(
                        value = faiStartSectorTextFieldValue,
                        onValueChange = { newValue ->
                            faiStartSectorTextFieldValue = newValue
                            onGateWidthChange(newValue.text)
                        },
                        label = { Text("Sector Radius (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && faiStartSectorTextFieldValue.selection.collapsed) {
                                    faiStartSectorTextFieldValue = faiStartSectorTextFieldValue.copy(
                                        selection = TextRange(0, faiStartSectorTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Radius of the FAI start sector in kilometers (FAI standard: 1km)") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }

    // Distance to next turnpoint
    if (nextWaypoint != null) {
        val distance by remember(selectedStartType, gateWidth, nextWaypoint) {
            derivedStateOf<Double> {
                if (selectedStartType == RacingStartPointType.START_LINE) {
                    val optimalPoint = taskManager.calculateOptimalStartLineCrossingPoint(waypoint, nextWaypoint)
                    taskManager.haversineDistance(optimalPoint.first, optimalPoint.second, nextWaypoint.lat, nextWaypoint.lon)
                } else {
                    taskManager.calculateSimpleSegmentDistance(waypoint, nextWaypoint)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = "Distance",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Distance to next turnpoint: ${String.format("%.1f", distance)} km" +
                        if (selectedStartType == RacingStartPointType.START_LINE) " (optimal crossing)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
