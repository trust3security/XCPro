package com.example.ui1.screens.flightmgmt

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui1.screens.AirspaceClassItem
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.saveSelectedClasses

private const val TAG = "FlightClassesTab"

@Composable
fun FlightDataClassesTab(
    airspaceClassItems: List<AirspaceClassItem>,
    selectedClasses: SnapshotStateMap<String, Boolean>,
    onSelectedClassesChanged: (SnapshotStateMap<String, Boolean>) -> Unit,
    sectionHeader: @Composable (String, String) -> Unit,
    airspaceClassCard: @Composable (AirspaceClassItem, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val orientationPreferences = remember { MapOrientationPreferences(context) }
    var currentOrientationMode by remember { mutableStateOf(orientationPreferences.getOrientationMode()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            sectionHeader("Map Orientation", "Current: ${currentOrientationMode.name.replace("_", " ")}")
        }

        item {
            MapOrientationCard(currentOrientationMode) { newMode ->
                currentOrientationMode = newMode
                orientationPreferences.setOrientationMode(newMode)
                Log.d(TAG, "Map orientation changed to $newMode")
            }
        }

        item {
            sectionHeader(
                "Airspace Classes",
                "${airspaceClassItems.count { it.enabled }} visible"
            )
        }

        items<AirspaceClassItem>(airspaceClassItems) { airspaceClass ->
            airspaceClassCard(airspaceClass) { className ->
                val newValue = !(selectedClasses[className] ?: true)
                selectedClasses[className] = newValue
                saveSelectedClasses(context, selectedClasses.toMap())
                onSelectedClassesChanged(selectedClasses)
                Log.d(TAG, "Airspace class $className is now ${if (newValue) "enabled" else "disabled"}")
            }
        }

        if (airspaceClassItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "Enable airspace files to see available classes",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MapOrientationCard(
    currentMode: MapOrientationMode,
    onModeChanged: (MapOrientationMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Map Orientation Mode",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose how the map rotates relative to your movement:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OrientationOption(
                title = "North Up",
                description = "Map always shows north at the top",
                selected = currentMode == MapOrientationMode.NORTH_UP,
                onSelect = { onModeChanged(MapOrientationMode.NORTH_UP) }
            )

            OrientationOption(
                title = "Track Up",
                description = "Map rotates based on GPS track (course over ground)",
                selected = currentMode == MapOrientationMode.TRACK_UP,
                onSelect = { onModeChanged(MapOrientationMode.TRACK_UP) }
            )

            OrientationOption(
                title = "Heading Up",
                description = "Map rotates based on magnetic compass heading",
                selected = currentMode == MapOrientationMode.HEADING_UP,
                onSelect = { onModeChanged(MapOrientationMode.HEADING_UP) }
            )
        }
    }
}

@Composable
private fun OrientationOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
