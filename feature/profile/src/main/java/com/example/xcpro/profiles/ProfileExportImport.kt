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
import java.text.SimpleDateFormat
import java.util.Locale

private fun buildExportFileName(profile: UserProfile?): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(TimeBridge.nowWallMs())
    return if (profile != null) {
        "profile_bundle_${profile.name.replace(" ", "_")}_$timestamp.json"
    } else {
        "profiles_bundle_$timestamp.json"
    }
}

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
                onExport("Profile bundle exported successfully.")
            } catch (error: Exception) {
                onExport("Failed to save bundle: ${error.message}")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile != null) "Export Profile Bundle" else "Export Profiles Bundle")
        },
        text = {
            if (isExporting) {
                Text("Preparing export bundle...")
            } else {
                Text(
                    if (profile != null) {
                        "Export this profile with its settings bundle."
                    } else {
                        "Export all profiles and settings as one bundle."
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
                            documentLauncher.launch(buildExportFileName(profile))
                        }
                    }
                },
                enabled = !isExporting
            ) {
                Text("Export")
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
    onImportJson: (String, Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var keepCurrentActive by remember { mutableStateOf(true) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val json = readJsonFromUri(context, uri = uri)
                isImporting = false
                json.onSuccess { content ->
                    onImportJson(content, keepCurrentActive)
                }.onFailure { error ->
                    onError("Failed to read file: ${error.message}")
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Profile Bundle") },
        text = {
            Column {
                if (isImporting) {
                    Text("Importing profile bundle...")
                } else {
                    Text(
                        "Select *_bundle_latest.json (recommended), a profile bundle export, " +
                            "or a compatible profile JSON file."
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
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { documentLauncher.launch(arrayOf("application/json")) },
                enabled = !isImporting
            ) {
                Text("Select File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
