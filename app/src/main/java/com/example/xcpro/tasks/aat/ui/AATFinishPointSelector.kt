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
import com.example.xcpro.tasks.aat.models.AATFinishPointType

/**
 * AAT Finish Point Configuration UI
 *
 * Handles all UI for configuring AAT finish points including:
 * - AAT Finish Line
 * - AAT Finish Cylinder
 *
 * Extracted from AATTaskPointTypeSelector to maintain 500-line file limit.
 */
@Composable
internal fun AATFinishPointSelector(
    selectedFinishType: AATFinishPointType,
    gateWidth: String,
    onFinishTypeChange: (AATFinishPointType) -> Unit,
    onGateWidthChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Finish Point Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        AATFinishPointButtonSelector(
            selectedType = selectedFinishType,
            onTypeSelected = onFinishTypeChange
        )

        // Gate width parameter for finish types (Card-wrapped like Racing)
        when (selectedFinishType) {
            AATFinishPointType.AAT_FINISH_LINE -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var finishLineTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                        OutlinedTextField(
                            value = finishLineTextFieldValue,
                            onValueChange = { newValue ->
                                finishLineTextFieldValue = newValue
                                onGateWidthChange(newValue.text)
                            },
                            label = { Text("Finish Line Length (km)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && finishLineTextFieldValue.selection.collapsed) {
                                        finishLineTextFieldValue = finishLineTextFieldValue.copy(
                                            selection = TextRange(0, finishLineTextFieldValue.text.length)
                                        )
                                    }
                                },
                            singleLine = true,
                            supportingText = { Text("Length of the AAT finish line in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
            AATFinishPointType.AAT_FINISH_CYLINDER -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var finishCylinderTextFieldValue by remember { mutableStateOf(TextFieldValue(gateWidth)) }

                        OutlinedTextField(
                            value = finishCylinderTextFieldValue,
                            onValueChange = { newValue ->
                                finishCylinderTextFieldValue = newValue
                                onGateWidthChange(newValue.text)
                            },
                            label = { Text("Finish Cylinder Radius (km)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused && finishCylinderTextFieldValue.selection.collapsed) {
                                        finishCylinderTextFieldValue = finishCylinderTextFieldValue.copy(
                                            selection = TextRange(0, finishCylinderTextFieldValue.text.length)
                                        )
                                    }
                                },
                            singleLine = true,
                            supportingText = { Text("Radius of the AAT finish cylinder in kilometers") },
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
        }
    }
}
