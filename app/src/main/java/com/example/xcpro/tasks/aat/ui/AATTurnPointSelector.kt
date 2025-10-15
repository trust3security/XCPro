package com.example.xcpro.tasks.aat.ui

import com.example.xcpro.tasks.core.TaskWaypoint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.TaskManagerCoordinator

/**
 * AAT Turn Point Configuration UI
 *
 * Handles all UI for configuring AAT turn points including:
 * - AAT Cylinder
 * - AAT Sector
 * - AAT Keyhole
 *
 * Extracted from AATTaskPointTypeSelector to maintain 500-line file limit.
 */
@Composable
internal fun AATTurnPointSelector(
    selectedTurnType: AATTurnPointType,
    gateWidth: String,
    keyholeInnerRadius: String,
    keyholeAngle: String,
    sectorOuterRadius: String,
    waypoint: TaskWaypoint,
    nextWaypoint: TaskWaypoint?,
    taskManager: TaskManagerCoordinator,
    onTurnTypeChange: (AATTurnPointType) -> Unit,
    onGateWidthChange: (String) -> Unit,
    onKeyholeInnerRadiusChange: (String) -> Unit,
    onKeyholeAngleChange: (String) -> Unit,
    onSectorOuterRadiusChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "AAT Area Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        AATTurnPointButtonSelector(
            selectedType = selectedTurnType,
            onTypeSelected = onTurnTypeChange
        )

        // Parameters based on selected AAT turn type (Card-wrapped like Racing)
        when (selectedTurnType) {
            AATTurnPointType.AAT_CYLINDER -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var aatCylinderTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                        OutlinedTextField(
                            value = aatCylinderTextFieldValue,
                            onValueChange = { newValue ->
                                aatCylinderTextFieldValue = newValue
                                onGateWidthChange(newValue.text)
                            },
                            label = { Text("AAT Cylinder Radius (km)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && aatCylinderTextFieldValue.selection.collapsed) {
                                        aatCylinderTextFieldValue = aatCylinderTextFieldValue.copy(
                                            selection = TextRange(0, aatCylinderTextFieldValue.text.length)
                                        )
                                    }
                                },
                            singleLine = true,
                            supportingText = { Text("Radius of the AAT cylinder area in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
            AATTurnPointType.AAT_SECTOR -> {
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
                        var sectorRadiusTextFieldValue by remember { mutableStateOf(TextFieldValue(sectorOuterRadius)) }
                        var sectorAngleTextFieldValue by remember { mutableStateOf(TextFieldValue(keyholeAngle)) }

                        OutlinedTextField(
                            value = sectorRadiusTextFieldValue,
                            onValueChange = { newValue ->
                                sectorRadiusTextFieldValue = newValue
                                onSectorOuterRadiusChange(newValue.text)
                            },
                            label = { Text("AAT Sector Radius (km)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && sectorRadiusTextFieldValue.selection.collapsed) {
                                        sectorRadiusTextFieldValue = sectorRadiusTextFieldValue.copy(
                                            selection = TextRange(0, sectorRadiusTextFieldValue.text.length)
                                        )
                                    }
                                },
                            singleLine = true,
                            supportingText = { Text("Radius of the AAT sector area in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = sectorAngleTextFieldValue,
                            onValueChange = { newValue ->
                                sectorAngleTextFieldValue = newValue
                                onKeyholeAngleChange(newValue.text)
                            },
                            label = { Text("Sector Angle (degrees)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && sectorAngleTextFieldValue.selection.collapsed) {
                                        sectorAngleTextFieldValue = sectorAngleTextFieldValue.copy(
                                            selection = TextRange(0, sectorAngleTextFieldValue.text.length)
                                        )
                                    }
                                },
                            singleLine = true,
                            supportingText = { Text("Angle of the AAT sector in degrees") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
            AATTurnPointType.AAT_KEYHOLE -> {
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
                        var keyholeInnerTextFieldValue by remember { mutableStateOf(TextFieldValue(keyholeInnerRadius)) }
                        var keyholeOuterTextFieldValue by remember { mutableStateOf(TextFieldValue(sectorOuterRadius)) }
                        var keyholeAngleTextFieldValue by remember { mutableStateOf(TextFieldValue(keyholeAngle)) }

                        OutlinedTextField(
                            value = keyholeOuterTextFieldValue,
                            onValueChange = { newValue ->
                                keyholeOuterTextFieldValue = newValue
                                onSectorOuterRadiusChange(newValue.text)
                            },
                            label = { Text("Outer Sector Radius (km)") },
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
                            supportingText = { Text("Outer sector radius of the AAT keyhole in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = keyholeInnerTextFieldValue,
                            onValueChange = { newValue ->
                                keyholeInnerTextFieldValue = newValue
                                onKeyholeInnerRadiusChange(newValue.text)
                            },
                            label = { Text("Inner Cylinder Radius (km)") },
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
                            supportingText = { Text("Inner cylinder radius of the AAT keyhole in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

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
                            supportingText = { Text("Sector angle of the AAT keyhole in degrees") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
        }
    }
}


