package com.example.xcpro.profiles.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.xcpro.profiles.AircraftType
import com.example.xcpro.profiles.ProfileCreationRequest
import com.example.xcpro.profiles.USER_SELECTABLE_AIRCRAFT_TYPES

@Composable
fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (ProfileCreationRequest) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedAircraft by remember { mutableStateOf<AircraftType?>(null) }
    var aircraftModel by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Aircraft Type")
                Spacer(modifier = Modifier.height(8.dp))

                USER_SELECTABLE_AIRCRAFT_TYPES.forEach { aircraft ->
                    RowWithRadio(
                        label = aircraft.displayName,
                        testTag = "create_profile_aircraft_type_${aircraft.name}",
                        icon = {
                            Icon(
                                imageVector = aircraft.icon(),
                                contentDescription = null,
                                modifier = Modifier.width(24.dp)
                            )
                        },
                        selected = selectedAircraft == aircraft,
                        onSelected = { selectedAircraft = aircraft }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = aircraftModel,
                    onValueChange = { aircraftModel = it },
                    label = { Text("Aircraft Model (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedAircraft?.let { aircraft ->
                        onCreate(
                            ProfileCreationRequest(
                                name = name,
                                aircraftType = aircraft,
                                aircraftModel = aircraftModel.takeIf { it.isNotBlank() },
                                description = description.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && selectedAircraft != null
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RowWithRadio(
    label: String,
    testTag: String? = null,
    icon: @Composable (() -> Unit)?,
    selected: Boolean,
    onSelected: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (testTag != null) {
                    Modifier.testTag(testTag)
                } else {
                    Modifier
                }
            )
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected
        )
        Spacer(modifier = Modifier.width(8.dp))
        icon?.invoke()
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}
