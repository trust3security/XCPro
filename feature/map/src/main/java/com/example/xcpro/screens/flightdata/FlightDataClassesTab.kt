package com.example.ui1.screens.flightmgmt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui1.screens.AirspaceClassItem
import com.example.xcpro.airspace.AirspaceViewModel

@Composable
fun FlightDataClassesTab(
    sectionHeader: @Composable (String, String) -> Unit,
    airspaceClassCard: @Composable (AirspaceClassItem, (String) -> Unit) -> Unit
) {
    val viewModel: AirspaceViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            sectionHeader(
                "Airspace Classes",
                "${uiState.classItems.count { it.enabled }} visible"
            )
        }

        items<AirspaceClassItem>(uiState.classItems) { airspaceClass ->
            airspaceClassCard(airspaceClass) { className ->
                viewModel.toggleClass(className)
            }
        }

        if (uiState.classItems.isEmpty()) {
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

