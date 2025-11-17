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

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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

