package com.example.ui1.screens.flightmgmt

import android.content.Context
import android.net.Uri
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui1.screens.AirspaceClassItem
import com.example.ui1.screens.FileItem
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveSelectedClasses
import com.example.xcpro.validateOpenAirFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_AIRSPACE_FILE_SIZE_MB = 5

@Composable
fun FlightDataAirspaceTab(
    selectedAirspaceFiles: SnapshotStateList<Uri>,
    airspaceCheckedStates: SnapshotStateMap<String, Boolean>,
    airspaceClassStates: SnapshotStateMap<String, Boolean>,
    onShowDeleteDialog: (String) -> Unit,
    onErrorMessage: (String) -> Unit,
    scope: CoroutineScope,
    addFileButton: @Composable (String, () -> Unit) -> Unit,
    sectionHeader: @Composable (String, String) -> Unit,
    fileItemCard: @Composable (FileItem, String, (String) -> Unit, (String) -> Unit) -> Unit,
    airspaceClassCard: @Composable (AirspaceClassItem, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current

    val uiState by remember {
        derivedStateOf {
            val fileItems = buildAirspaceFileItems(context, selectedAirspaceFiles, airspaceCheckedStates)
            val enabledFiles = fileItems.filter { it.enabled }.map { it.uri }
            val classItems = buildAirspaceClassItems(context, enabledFiles, airspaceClassStates)
            AirspaceTabDerivedState(fileItems, enabledFiles, classItems)
        }
    }

    LaunchedEffect(selectedAirspaceFiles.size, airspaceCheckedStates.size) {
        refreshAvailableAirspaceClasses(
            context = context,
            selectedFiles = selectedAirspaceFiles,
            checkedStates = airspaceCheckedStates,
            classStates = airspaceClassStates,
            scope = scope
        )
    }

    val toggleFile: (String) -> Unit = remember(context, selectedAirspaceFiles, airspaceCheckedStates) {
        { fileName ->
            val newValue = !(airspaceCheckedStates[fileName] ?: false)
            airspaceCheckedStates[fileName] = newValue
            scope.launch {
                withContext(Dispatchers.IO) {
                    saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.toMap())
                }
                refreshAvailableAirspaceClasses(
                    context,
                    selectedAirspaceFiles,
                    airspaceCheckedStates,
                    airspaceClassStates,
                    scope
                )
            }
        }
    }

    val toggleClass: (String) -> Unit = remember(context, airspaceClassStates) {
        { className ->
            val newValue = !(airspaceClassStates[className] ?: true)
            airspaceClassStates[className] = newValue
            scope.launch(Dispatchers.IO) {
                saveSelectedClasses(context, airspaceClassStates.toMap())
            }
        }
    }

    val airspaceFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = importAirspaceFile(context, uri)) {
                is AirspaceImportResult.Success -> {
                    if (selectedAirspaceFiles.none { it.namePart() == result.fileName }) {
                        selectedAirspaceFiles.add(Uri.fromFile(File(context.filesDir, result.fileName)))
                    }
                    airspaceCheckedStates[result.fileName] = true
                    withContext(Dispatchers.IO) {
                        saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.toMap())
                    }
                    refreshAvailableAirspaceClasses(
                        context,
                        selectedAirspaceFiles,
                        airspaceCheckedStates,
                        airspaceClassStates,
                        scope
                    )
                }

                is AirspaceImportResult.Failure -> onErrorMessage(result.reason)
            }
        }
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

private suspend fun importAirspaceFile(
    context: Context,
    uri: Uri
): AirspaceImportResult = withContext(Dispatchers.IO) {
    runCatching {
        val fileName = copyFileToInternalStorage(context, uri)
        val file = File(context.filesDir, fileName)
        val sizeInMb = file.length() / (1024 * 1024)
        if (sizeInMb > MAX_AIRSPACE_FILE_SIZE_MB) {
            file.delete()
            return@runCatching AirspaceImportResult.Failure(
                "File too large (${sizeInMb}MB). Maximum size is ${MAX_AIRSPACE_FILE_SIZE_MB}MB."
            )
        }

        if (!fileName.endsWith(".txt", ignoreCase = true)) {
            file.delete()
            return@runCatching AirspaceImportResult.Failure(
                "Only .txt files are supported for airspace files."
            )
        }

        val (isValid, message) = validateOpenAirFile(file.readText())
        if (!isValid) {
            file.delete()
            return@runCatching AirspaceImportResult.Failure("Invalid file format: $message")
        }

        AirspaceImportResult.Success(fileName)
    }.getOrElse { error ->
        AirspaceImportResult.Failure(error.message ?: "Unknown error while processing file.")
    }
}

private data class AirspaceTabDerivedState(
    val fileItems: List<FileItem>,
    val enabledFiles: List<Uri>,
    val classItems: List<AirspaceClassItem>
)

private sealed interface AirspaceImportResult {
    data class Success(val fileName: String) : AirspaceImportResult
    data class Failure(val reason: String) : AirspaceImportResult
}

private fun Uri.namePart(): String =
    lastPathSegment?.substringAfterLast("/") ?: "Unknown"

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
