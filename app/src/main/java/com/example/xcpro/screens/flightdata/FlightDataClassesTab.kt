package com.example.ui1.screens.flightmgmt

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xcpro.saveSelectedClasses
import com.example.ui1.screens.AirspaceClassItem
import com.example.xcpro.MapOrientationMode
import com.example.xcpro.MapOrientationPreferences

private const val TAG = "FlightClassesTab"

@Composable
fun FlightDataClassesTab(
    airspaceClassItems: List<AirspaceClassItem>,
    selectedClasses: MutableMap<String, Boolean>,
    onSelectedClassesChanged: (MutableMap<String, Boolean>) -> Unit,
    // ✅ Pass shared components as parameters
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
        // Map Orientation Section
        item {
            sectionHeader(
                "Map Orientation",
                "Current: ${currentOrientationMode.name.replace("_", " ")}"
            )
        }

        item {
            MapOrientationCard(
                currentMode = currentOrientationMode,
                onModeChanged = { newMode ->
                    currentOrientationMode = newMode
                    orientationPreferences.setOrientationMode(newMode)
                    Log.d(TAG, "🧭 Map orientation changed to: $newMode")
                }
            )
        }

        // Section Header
        item {
            sectionHeader(
                "Airspace Classes",
                "${airspaceClassItems.count { it.enabled }} visible"
            )
        }

        // Airspace Classes List
        items(airspaceClassItems) { airspaceClass ->
            airspaceClassCard(airspaceClass) { className ->
                Log.d(TAG, "🏷️ Toggling airspace class: $className")
                val newClasses = selectedClasses.toMutableMap().apply {
                    put(className, !(get(className) ?: false))
                }
                onSelectedClassesChanged(newClasses)
                saveSelectedClasses(context, newClasses)
                Log.d(TAG, "✅ Airspace class $className is now ${if (newClasses[className] == true) "enabled" else "disabled"}")
            }
        }

        // Show message if no classes available
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

            // North Up Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentMode == MapOrientationMode.NORTH_UP,
                    onClick = { onModeChanged(MapOrientationMode.NORTH_UP) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "North Up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Map always shows north at the top",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Track Up Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentMode == MapOrientationMode.TRACK_UP,
                    onClick = { onModeChanged(MapOrientationMode.TRACK_UP) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Track Up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Map rotates based on GPS track (course over ground)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Heading Up Option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentMode == MapOrientationMode.HEADING_UP,
                    onClick = { onModeChanged(MapOrientationMode.HEADING_UP) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Heading Up",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Map rotates based on magnetic compass heading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}