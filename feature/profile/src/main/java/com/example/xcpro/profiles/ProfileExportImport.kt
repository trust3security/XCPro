package com.example.xcpro.profiles

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xcpro.core.time.TimeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileExportDialog(
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit,
    onRequestExportJson: suspend () -> Result<String>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<Result<String>?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && exportResult?.isSuccess == true) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportResult!!.getOrNull()!!.toByteArray())
                }
                onExport(
                    if (profile != null) {
                        "Aircraft profile exported successfully."
                    } else {
                        "Aircraft profiles backup exported successfully."
                    }
                )
            } catch (error: Exception) {
                onExport("Failed to save file: ${error.message}")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile != null) "Export Aircraft Profile" else "Backup Aircraft Profiles")
        },
        text = {
            if (isExporting) {
                Text(
                    if (profile != null) {
                        "Preparing aircraft profile file..."
                    } else {
                        "Preparing aircraft profiles backup..."
                    }
                )
            } else {
                Text(
                    if (profile != null) {
                        "Save this aircraft profile as a portable JSON file."
                    } else {
                        "Save all aircraft profiles as one portable JSON backup."
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isExporting = true
                    coroutineScope.launch {
                        val result = onRequestExportJson()
                        exportResult = result
                        isExporting = false
                        if (result.isSuccess) {
                            documentLauncher.launch(
                                AircraftProfileFileNames.buildExportFileName(
                                    profile = profile,
                                    nowWallMs = TimeBridge.nowWallMs()
                                )
                            )
                        }
                    }
                },
                enabled = !isExporting
            ) {
                Text(if (profile != null) "Save File" else "Save All Profiles")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    exportResult?.let { result ->
        if (result.isFailure) {
            LaunchedEffect(result) {
                onExport("Export failed: ${result.exceptionOrNull()?.message}")
                exportResult = null
            }
        }
    }
}

@Composable
fun ProfileImportDialog(
    onDismiss: () -> Unit,
    onImportJson: (String, Boolean, ProfileSettingsImportScope, Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var keepCurrentActive by remember { mutableStateOf(true) }
    var settingsImportScope by remember {
        mutableStateOf(ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS)
    }
    var strictSettingsRestore by remember { mutableStateOf(false) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val json = readJsonFromUri(context, uri = uri)
                isImporting = false
                json.onSuccess { content ->
                    onImportJson(
                        content,
                        keepCurrentActive,
                        settingsImportScope,
                        strictSettingsRestore
                    )
                }.onFailure { error ->
                    onError("Failed to read file: ${error.message}")
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Aircraft Profile File") },
        text = {
            Column {
                if (isImporting) {
                    Text("Importing aircraft profile...")
                } else {
                    Text(
                        "Select an aircraft profile export, a managed *_bundle_latest.json " +
                            "snapshot, or another compatible profile JSON file."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = keepCurrentActive,
                            onCheckedChange = { keepCurrentActive = it },
                            enabled = !isImporting
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Keep current active profile")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Import settings scope")
                    ImportScopeOptionRow(
                        label = "Profile only",
                        selected = settingsImportScope == ProfileSettingsImportScope.PROFILES_ONLY,
                        onSelect = { settingsImportScope = ProfileSettingsImportScope.PROFILES_ONLY }
                    )
                    ImportScopeOptionRow(
                        label = "Profile + aircraft settings",
                        selected = settingsImportScope == ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS,
                        onSelect = {
                            settingsImportScope = ProfileSettingsImportScope.PROFILE_SCOPED_SETTINGS
                        }
                    )
                    ImportScopeOptionRow(
                        label = "All included settings",
                        selected = settingsImportScope == ProfileSettingsImportScope.FULL_BUNDLE,
                        onSelect = { settingsImportScope = ProfileSettingsImportScope.FULL_BUNDLE }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = strictSettingsRestore,
                            onCheckedChange = { strictSettingsRestore = it },
                            enabled = !isImporting
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Strict restore (fail on section errors)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { documentLauncher.launch(arrayOf("application/json")) },
                enabled = !isImporting
            ) {
                Text("Choose File")
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
private fun ImportScopeOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

private suspend fun readJsonFromUri(context: Context, uri: Uri): Result<String> {
    return runCatching {
        withContext(Dispatchers.IO) {
            val selectedJson = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("No readable content in selected file.")
            val indexPointer = ProfileBundleCodec.parseManagedIndexPointer(selectedJson)
            if (indexPointer != null) {
                val bundleHint = indexPointer.bundleFileName ?: "*_bundle_latest.json"
                error("Index-only backup file selected. Choose $bundleHint directly.")
            }
            selectedJson
        }
    }
}
