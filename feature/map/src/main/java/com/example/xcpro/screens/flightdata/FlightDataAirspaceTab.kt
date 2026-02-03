package com.example.ui1.screens.flightmgmt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui1.screens.AirspaceClassItem
import com.example.ui1.screens.FileItem
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.airspace.AirspaceViewModel
import com.example.xcpro.map.ui.documentRefForUri

@Composable
fun FlightDataAirspaceTab(
    onShowDeleteDialog: (String) -> Unit,
    onErrorMessage: (String) -> Unit,
    addFileButton: @Composable (String, () -> Unit) -> Unit,
    sectionHeader: @Composable (String, String) -> Unit,
    fileItemCard: @Composable (FileItem, String, (String) -> Unit, (String) -> Unit) -> Unit,
    airspaceClassCard: @Composable (AirspaceClassItem, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val viewModel: AirspaceViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { onErrorMessage(it) }
    }

    val toggleFile: (String) -> Unit = remember(viewModel) {
        { fileName -> viewModel.toggleFile(fileName) }
    }

    val toggleClass: (String) -> Unit = remember(viewModel) {
        { className -> viewModel.toggleClass(className) }
    }

    val airspaceFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importFile(documentRefForUri(context, uri))
    }

    val launchPicker = remember { { airspaceFilePickerLauncher.launch("text/plain") } }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            addFileButton("airspace", launchPicker)
        }

        when {
            uiState.fileItems.isEmpty() -> {
                item {
                    AirspaceEmptyState(onAddFile = launchPicker)
                }
            }

            uiState.enabledFiles.isEmpty() -> {
                item {
                    sectionHeader("Airspace Files", "")
                }
                items(uiState.fileItems) { file ->
                    fileItemCard(file, "airspace", toggleFile, onShowDeleteDialog)
                }
                item {
                    AirspaceFilesDisabledState(totalFiles = uiState.fileItems.size)
                }
            }

            uiState.classItems.isEmpty() -> {
                item {
                    sectionHeader("Airspace Files", "${uiState.enabledFiles.size} active")
                }
                items(uiState.fileItems) { file ->
                    fileItemCard(file, "airspace", toggleFile, onShowDeleteDialog)
                }
                item {
                    AirspaceNoClassesState()
                }
            }

            else -> {
                item {
                    sectionHeader("Airspace Files", "${uiState.enabledFiles.size} active")
                }
                items(uiState.fileItems) { file ->
                    fileItemCard(file, "airspace", toggleFile, onShowDeleteDialog)
                }

                if (uiState.classItems.isNotEmpty()) {
                    item {
                        sectionHeader(
                            "Airspace Classes",
                            "${uiState.classItems.count { it.enabled }} visible"
                        )
                    }
                    items(uiState.classItems) { airspaceClass ->
                        airspaceClassCard(airspaceClass, toggleClass)
                    }
                }
            }
        }
    }
}

@Composable
private fun AirspaceEmptyState(onAddFile: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Add airspace files to visualize controlled zones on the map.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = "Supported format: OpenAir .txt files with AC/AN/AL/AH/DP entries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(onClick = onAddFile) {
                Text("Add Airspace File")
            }
        }
    }
}

@Composable
private fun AirspaceFilesDisabledState(totalFiles: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "All $totalFiles file(s) are currently disabled.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "Toggle the switch beside a file to load its airspace classes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun AirspaceNoClassesState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "No airspace classes detected in the enabled files.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "Confirm the files are valid OpenAir downloads and contain AC and AN sections.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
