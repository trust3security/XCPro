package com.trust3.xcpro.tasks.racing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType

/**
 * Racing Finish Point Configuration UI
 *
 * Handles all UI for configuring racing finish points including:
 * - Finish Line
 * - Finish Cylinder
 *
 * Extracted from RacingTaskPointTypeSelector to maintain 500-line file limit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RacingFinishPointSelector(
    selectedFinishType: RacingFinishPointType,
    gateWidth: String,
    onFinishTypeChange: (RacingFinishPointType) -> Unit,
    onGateWidthChange: (String) -> Unit
) {
    // Button-based finish type selector
    RacingFinishPointButtonSelector(
        selectedType = selectedFinishType,
        onTypeChange = onFinishTypeChange
    )

    // Show parameter input for selected finish type
    when (selectedFinishType) {
        RacingFinishPointType.FINISH_LINE -> {
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
                        supportingText = { Text("Length of the finish line in kilometers (FAI standard: 12km)") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
        RacingFinishPointType.FINISH_CYLINDER -> {
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
                        label = { Text("Cylinder Radius (km)") },
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
                        supportingText = { Text("Radius of the finish cylinder in kilometers") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }
    }

    // Distance information for finish waypoint
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
                imageVector = Icons.Default.Flag,
                contentDescription = "Finish",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Final waypoint - Racing task ends here",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
