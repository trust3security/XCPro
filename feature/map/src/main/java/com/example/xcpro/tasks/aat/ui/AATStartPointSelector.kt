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
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.TaskManagerCoordinator

/**
 * AAT Start Point Configuration UI
 *
 * Handles all UI for configuring AAT start points including:
 * - AAT Start Line
 * - AAT Start Cylinder
 * - AAT Start Sector
 *
 * Extracted from AATTaskPointTypeSelector to maintain 500-line file limit.
 */
@Composable
internal fun AATStartPointSelector(
    selectedStartType: AATStartPointType,
    gateWidth: String,
    waypoint: TaskWaypoint,
    nextWaypoint: TaskWaypoint?,
    taskManager: TaskManagerCoordinator,
    onStartTypeChange: (AATStartPointType) -> Unit,
    onGateWidthChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Start Point Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        AATStartPointButtonSelector(
            selectedType = selectedStartType,
            onTypeSelected = onStartTypeChange
        )

        // Gate width parameter for start types (Card-wrapped like Racing)
        when (selectedStartType) {
            AATStartPointType.AAT_START_LINE -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = gateWidth,
                            onValueChange = onGateWidthChange,
                            label = { Text("Start Line Length (km)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("Length of the AAT start line in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
            AATStartPointType.AAT_START_CYLINDER -> {
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
                            label = { Text("Start Cylinder Radius (km)") },
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
                            supportingText = { Text("Radius of the AAT start cylinder in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
            AATStartPointType.AAT_START_SECTOR -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var aatStartSectorTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                        OutlinedTextField(
                            value = aatStartSectorTextFieldValue,
                            onValueChange = { newValue ->
                                aatStartSectorTextFieldValue = newValue
                                onGateWidthChange(newValue.text)
                            },
                            label = { Text("AAT Sector Radius (km)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && aatStartSectorTextFieldValue.selection.collapsed) {
                                        aatStartSectorTextFieldValue = aatStartSectorTextFieldValue.copy(
                                            selection = TextRange(0, aatStartSectorTextFieldValue.text.length)
                                        )
                                    }
                                },
                            singleLine = true,
                            supportingText = { Text("Radius of the AAT start sector in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
        }
    }
}


