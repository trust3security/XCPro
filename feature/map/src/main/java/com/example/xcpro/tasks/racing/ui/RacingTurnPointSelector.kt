package com.example.xcpro.tasks.racing.ui

import com.example.xcpro.tasks.core.TaskWaypoint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.TaskManagerCoordinator

/**
 * Racing Turn Point Configuration UI
 *
 * Handles all UI for configuring racing turn points including:
 * - Cylinder
 * - Keyhole
 * - FAI Quadrant
 *
 * Extracted from RacingTaskPointTypeSelector to maintain 500-line file limit.
 */
@Composable
internal fun RacingTurnPointSelector(
    selectedTurnType: RacingTurnPointType,
    gateWidth: String,
    keyholeInnerRadius: String,
    keyholeAngle: String,
    faiQuadrantOuterRadius: String,
    waypoint: TaskWaypoint,
    nextWaypoint: TaskWaypoint?,
    taskManager: TaskManagerCoordinator,
    onTurnTypeChange: (RacingTurnPointType) -> Unit,
    onGateWidthChange: (String) -> Unit,
    onKeyholeInnerRadiusChange: (String) -> Unit,
    onKeyholeAngleChange: (String) -> Unit,
    onFAIQuadrantOuterRadiusChange: (String) -> Unit
) {
    // Use the button-based selector we created earlier
    RacingTurnPointButtonSelector(
        selectedType = selectedTurnType,
        onTypeChange = onTurnTypeChange
    )

    // Show parameter input for selected turn type
    when (selectedTurnType) {
        RacingTurnPointType.TURN_POINT_CYLINDER -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var turnCylinderTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                    OutlinedTextField(
                        value = turnCylinderTextFieldValue,
                        onValueChange = { newValue ->
                            turnCylinderTextFieldValue = newValue
                            onGateWidthChange(newValue.text)
                        },
                        label = { Text("Cylinder Radius (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && turnCylinderTextFieldValue.selection.collapsed) {
                                    turnCylinderTextFieldValue = turnCylinderTextFieldValue.copy(
                                        selection = TextRange(0, turnCylinderTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Radius of the turn point cylinder in kilometers (FAI standard: 0.5km)") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
        RacingTurnPointType.KEYHOLE -> {
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
                    Text(
                        text = "🔑 Configurable Keyhole Parameters",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Outer Radius (Sector part)
                    var keyholeOuterTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }
                    OutlinedTextField(
                        value = keyholeOuterTextFieldValue,
                        onValueChange = { newValue ->
                            keyholeOuterTextFieldValue = newValue
                            onGateWidthChange(newValue.text)
                        },
                        label = { Text("Outer Radius - Sector (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && keyholeOuterTextFieldValue.selection.collapsed) {
                                    keyholeOuterTextFieldValue = keyholeOuterTextFieldValue.copy(
                                        selection = TextRange(0, keyholeOuterTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Outer radius (default: 5km)") },
                        shape = RoundedCornerShape(20.dp)
                    )

                    // Inner Radius (Cylinder part)
                    var keyholeInnerTextFieldValue by remember { mutableStateOf(TextFieldValue(keyholeInnerRadius)) }
                    OutlinedTextField(
                        value = keyholeInnerTextFieldValue,
                        onValueChange = { newValue ->
                            keyholeInnerTextFieldValue = newValue
                            onKeyholeInnerRadiusChange(newValue.text)
                        },
                        label = { Text("Inner Radius - Cylinder (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && keyholeInnerTextFieldValue.selection.collapsed) {
                                    keyholeInnerTextFieldValue = keyholeInnerTextFieldValue.copy(
                                        selection = TextRange(0, keyholeInnerTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Inner cylinder radius (default: 0.5km)") },
                        shape = RoundedCornerShape(20.dp)
                    )

                    // Sector Angle
                    var keyholeAngleTextFieldValue by remember { mutableStateOf(TextFieldValue(keyholeAngle)) }
                    OutlinedTextField(
                        value = keyholeAngleTextFieldValue,
                        onValueChange = { newValue ->
                            keyholeAngleTextFieldValue = newValue
                            onKeyholeAngleChange(newValue.text)
                        },
                        label = { Text("Sector Angle (degrees)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && keyholeAngleTextFieldValue.selection.collapsed) {
                                    keyholeAngleTextFieldValue = keyholeAngleTextFieldValue.copy(
                                        selection = TextRange(0, keyholeAngleTextFieldValue.text.length)
                                    )
                                }
                            },
                        singleLine = true,
                        supportingText = { Text("Sector angle in degrees (default: 90°)") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
        RacingTurnPointType.FAI_QUADRANT -> {
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
                    Text(
                        text = "🔶 Configurable FAI Quadrant Parameters",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Display Radius configuration
                    var faiQuadrantOuterRadiusTextFieldValue by remember {
                        mutableStateOf(TextFieldValue(faiQuadrantOuterRadius))
                    }
                    OutlinedTextField(
                        value = faiQuadrantOuterRadiusTextFieldValue,
                        onValueChange = { newValue ->
                            faiQuadrantOuterRadiusTextFieldValue = newValue
                            onFAIQuadrantOuterRadiusChange(newValue.text)
                        },
                        label = { Text("Sector Radius (km)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && faiQuadrantOuterRadiusTextFieldValue.selection.collapsed) {
                                    faiQuadrantOuterRadiusTextFieldValue = faiQuadrantOuterRadiusTextFieldValue.copy(
                                        selection = TextRange(0, faiQuadrantOuterRadiusTextFieldValue.text.length)
                                    )
                                }
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = { Text("Sector radius for FAI quadrant (default: 10km, XCSoar parity).") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }

    // Distance to next turnpoint
    if (nextWaypoint != null) {
        val distance: Double = remember(nextWaypoint) {
            taskManager.calculateSimpleSegmentDistance(waypoint, nextWaypoint)
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
                    text = "Distance to next turnpoint: ${String.format("%.1f", distance)} km",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


