package com.example.xcpro.profiles.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcpro.profiles.AircraftType

@Composable
internal fun ProfileFirstLaunchSetupCard(
    isLoading: Boolean,
    onCompleteFirstLaunch: (AircraftType) -> Unit,
    onImportProfiles: () -> Unit,
    storageNamespaceLabel: String?,
    modifier: Modifier = Modifier
) {
    var selectedAircraft by remember { mutableStateOf<AircraftType?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "XCPro",
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Choose Your Aircraft Type",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Select the aircraft you fly most often. XCPro will create the canonical default profile from this choice.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                firstLaunchAircraftChoices().forEach { aircraftType ->
                    AircraftTypeOptionRow(
                        aircraftType = aircraftType,
                        selected = selectedAircraft == aircraftType,
                        onSelected = { selectedAircraft = aircraftType }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    selectedAircraft?.let(onCompleteFirstLaunch)
                },
                enabled = selectedAircraft != null && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Default Profile")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onImportProfiles,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load Profile File")
            }

            if (!storageNamespaceLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Profiles are scoped to app package: $storageNamespaceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AircraftTypeOptionRow(
    aircraftType: AircraftType,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onSelected,
                    role = Role.RadioButton
                )
                .testTag("first_launch_option_${aircraftType.name}")
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = aircraftType.icon(),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(aircraftType.displayName)
        }
    }
}

private fun firstLaunchAircraftChoices(): List<AircraftType> = listOf(
    AircraftType.SAILPLANE,
    AircraftType.PARAGLIDER,
    AircraftType.HANG_GLIDER
)
