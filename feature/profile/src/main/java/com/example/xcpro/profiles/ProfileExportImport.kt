package com.example.xcpro.profiles

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileExportDialog(
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit,
    onRequestExportBundle: suspend () -> Result<ProfileBundleExportArtifact>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<Result<ProfileBundleExportArtifact>?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && exportResult?.isSuccess == true) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportResult!!.getOrNull()!!.bundleJson.toByteArray())
                }
                onExport(
                    if (profile != null) {
                        "Profile file saved successfully."
                    } else {
                        "All profiles backup saved successfully."
                    }
                )
            } catch (error: Exception) {
                onExport(
                    if (profile != null) {
                        "Failed to save profile file: ${error.message}"
                    } else {
                        "Failed to save all profiles backup: ${error.message}"
                    }
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile != null) "Save Profile File" else "Save All Profiles")
        },
        text = {
            if (isExporting) {
                Text(
                    if (profile != null) {
                        "Preparing profile file..."
                    } else {
                        "Preparing all profiles backup..."
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
                        val result = onRequestExportBundle()
                        exportResult = result
                        isExporting = false
                        val exportArtifact = result.getOrNull()
                        if (exportArtifact != null) {
                            documentLauncher.launch(exportArtifact.suggestedFileName)
                        }
                    }
                },
                enabled = !isExporting
            ) {
                Text(if (profile != null) "Save Profile File" else "Save All Profiles")
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
                onExport(
                    if (profile != null) {
                        "Failed to save profile file: ${result.exceptionOrNull()?.message}"
                    } else {
                        "Failed to save all profiles backup: ${result.exceptionOrNull()?.message}"
                    }
                )
                exportResult = null
            }
        }
    }
}

@Composable
fun ProfileImportDialog(
    canKeepCurrentActive: Boolean,
    onDismiss: () -> Unit,
    onRequestPreview: suspend (String) -> Result<ProfileBundlePreview>,
    onImportJson: (String, Boolean, ProfileNameCollisionPolicy) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var keepCurrentActive by remember(canKeepCurrentActive) {
        mutableStateOf(false)
    }
    var nameCollisionPolicy by remember {
        mutableStateOf(ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX)
    }
    var selectedImportFile by remember { mutableStateOf<SelectedImportFile?>(null) }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val json = readJsonFromUri(context, uri = uri)
                json.onSuccess { content ->
                    onRequestPreview(content)
                        .onSuccess { preview ->
                            selectedImportFile = SelectedImportFile(
                                json = content,
                                preview = preview
                            )
                            nameCollisionPolicy = ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX
                            keepCurrentActive = false
                        }
                        .onFailure { error ->
                            onError("Failed to preview profile file: ${error.message}")
                        }
                }.onFailure { error ->
                    onError("Failed to load profile file: ${error.message}")
                }
                isImporting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Profile File") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isImporting) {
                    Text("Loading profile preview...")
                } else if (selectedImportFile == null) {
                    Text("Choose a profile JSON file to preview before import.")
                    Text(
                        "Compatible backup JSON files can also be opened here, but only aircraft profile settings are restored in this flow."
                    )
                } else {
                    ProfileImportPreviewContent(
                        preview = selectedImportFile!!.preview,
                        canKeepCurrentActive = canKeepCurrentActive,
                        keepCurrentActive = keepCurrentActive,
                        onKeepCurrentActiveChange = { keepCurrentActive = it },
                        nameCollisionPolicy = nameCollisionPolicy,
                        onNameCollisionPolicyChange = { nameCollisionPolicy = it },
                        onChooseDifferentFile = { documentLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val previewedFile = selectedImportFile
                    if (previewedFile == null) {
                        documentLauncher.launch(arrayOf("application/json"))
                    } else {
                        onImportJson(
                            previewedFile.json,
                            keepCurrentActive,
                            nameCollisionPolicy
                        )
                    }
                },
                enabled = !isImporting
            ) {
                Text(if (selectedImportFile == null) "Choose File" else "Import")
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
private fun ProfileImportPreviewContent(
    preview: ProfileBundlePreview,
    canKeepCurrentActive: Boolean,
    keepCurrentActive: Boolean,
    onKeepCurrentActiveChange: (Boolean) -> Unit,
    nameCollisionPolicy: ProfileNameCollisionPolicy,
    onNameCollisionPolicyChange: (ProfileNameCollisionPolicy) -> Unit,
    onChooseDifferentFile: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Review this file before importing.")
        Text(
            "Profiles in file: ${preview.profiles.size}",
        )
        preview.profiles.take(3).forEach { profile ->
            val matchSuffix = if (profile.matchesExistingProfileName) {
                " (matches an existing profile name)"
            } else {
                ""
            }
            Text(
                "${profile.name} - ${profile.aircraftType.displayName}" +
                    (profile.aircraftModel?.let { " ($it)" } ?: "") +
                    matchSuffix
            )
        }
        if (preview.profiles.size > 3) {
            Text("+${preview.profiles.size - 3} more profile(s)")
        }
        PreviewField("Source", preview.sourceFormat.displayLabel())
        PreviewField("Schema version", preview.schemaVersion)
        preview.displayExportedAt()?.let { PreviewField("Exported", it) }
        PreviewField("Settings", preview.settingsSummary())
        preview.preferredActiveProfileName?.let { activeName ->
            PreviewField("Active in file", activeName)
        }
        if (preview.ignoredGlobalSectionIds.isNotEmpty()) {
            Text(
                "Backup-only settings in this file will be ignored here: " +
                    preview.ignoredGlobalSectionIds.size
            )
        }
        if (preview.unknownSectionIds.isNotEmpty()) {
            Text(
                "Unknown settings sections will be ignored: ${preview.unknownSectionIds.size}"
            )
        }
        TextButton(onClick = onChooseDifferentFile) {
            Text("Choose Different File")
        }
        Text("If the file name already exists in XCPro")
        ImportOptionRow(
            label = "Import as new",
            supportingText = "Keep the existing profile and import this one with a safe suffix.",
            selected = nameCollisionPolicy == ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX,
            onSelect = { onNameCollisionPolicyChange(ProfileNameCollisionPolicy.KEEP_BOTH_SUFFIX) }
        )
        ImportOptionRow(
            label = "Replace matching profile",
            supportingText = "Reuse the existing profile entry when the name matches.",
            selected = nameCollisionPolicy == ProfileNameCollisionPolicy.REPLACE_EXISTING,
            onSelect = { onNameCollisionPolicyChange(ProfileNameCollisionPolicy.REPLACE_EXISTING) }
        )
        if (canKeepCurrentActive) {
            Text("After import")
            ImportOptionRow(
                label = "Activate imported profile",
                supportingText = "Switch to the imported profile immediately after import.",
                selected = !keepCurrentActive,
                onSelect = { onKeepCurrentActiveChange(false) }
            )
            ImportOptionRow(
                label = "Keep current active profile",
                supportingText = "Import the file without switching away from the current profile.",
                selected = keepCurrentActive,
                onSelect = { onKeepCurrentActiveChange(true) }
            )
        } else {
            Text("No active profile is selected, so the imported profile will become active.")
        }
    }
}

@Composable
private fun ImportOptionRow(
    label: String,
    supportingText: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label)
            Text(supportingText)
        }
    }
}

@Composable
private fun PreviewField(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label)
        Text(value)
    }
}

@Composable
fun ProfileImportResultDialog(
    result: ProfileBundleImportResult,
    profiles: List<UserProfile>,
    onDismiss: () -> Unit
) {
    val importResult = result.profileImportResult
    val appliedSectionCount = result.settingsRestoreResult.appliedSections.size
    val failedSectionLabels = result.settingsRestoreResult.failedSections.keys
        .sorted()
        .map(::profileSettingsSectionLabel)
    val activeProfileName = profiles
        .firstOrNull { it.id == importResult.activeProfileAfter }
        ?.getDisplayName()
    val hasWarnings = importResult.failures.isNotEmpty() || failedSectionLabels.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (hasWarnings) "Import Completed with Warnings" else "Import Complete")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(formatProfileImportFeedback(importResult))
                PreviewField("Source", result.sourceFormat.displayLabel())
                val settingsSummary = when {
                    appliedSectionCount > 0 && failedSectionLabels.isEmpty() ->
                        "Restored $appliedSectionCount aircraft settings section(s)."
                    appliedSectionCount > 0 ->
                        "Restored $appliedSectionCount aircraft settings section(s) with warnings."
                    failedSectionLabels.isNotEmpty() ->
                        "No aircraft settings were restored successfully."
                    else -> "No aircraft settings were restored."
                }
                PreviewField("Settings", settingsSummary)
                activeProfileName?.let { name ->
                    val activeSummary = if (
                        importResult.activeProfileAfter != null &&
                        importResult.activeProfileAfter != importResult.activeProfileBefore
                    ) {
                        "Active after import: $name"
                    } else {
                        "Current active profile: $name"
                    }
                    PreviewField("Activation", activeSummary)
                }
                if (failedSectionLabels.isNotEmpty()) {
                    Text(
                        "Failed settings sections: " + failedSectionLabels.joinToString(", ")
                    )
                }
                if (importResult.failures.isNotEmpty()) {
                    val failurePreview = importResult.failures
                        .take(2)
                        .joinToString("; ") { it.detail }
                    Text("Skipped profiles: $failurePreview")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {}
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

private data class SelectedImportFile(
    val json: String,
    val preview: ProfileBundlePreview
)

private fun ProfileBundlePreview.displayExportedAt(): String? {
    if (!exportedAtLabel.isNullOrBlank()) return exportedAtLabel
    val wallMs = exportedAtWallMs ?: return null
    return runCatching {
        Instant.ofEpochMilli(wallMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrNull()
}

private fun ProfileBundlePreview.settingsSummary(): String {
    return when {
        hasRestorableAircraftSettings -> "Aircraft profile settings"
        else -> "Profile metadata only"
    }
}

private fun ProfileBundleSourceFormat.displayLabel(): String {
    return when (this) {
        ProfileBundleSourceFormat.BUNDLE_V2 -> "Aircraft profile / bundle JSON"
        ProfileBundleSourceFormat.LEGACY_PROFILE_EXPORT_V1 -> "Legacy profile export"
        ProfileBundleSourceFormat.BACKUP_PROFILE_DOCUMENT_V1 -> "Managed backup profile document"
    }
}

private fun profileSettingsSectionLabel(sectionId: String): String {
    return when (sectionId) {
        ProfileSettingsSectionIds.CARD_PREFERENCES -> "Flight data cards"
        ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES -> "Flight mode settings"
        ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES -> "Look and feel"
        ProfileSettingsSectionIds.THEME_PREFERENCES -> "Theme"
        ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT -> "Map widget layout"
        ProfileSettingsSectionIds.VARIOMETER_WIDGET_LAYOUT -> "Variometer widget layout"
        ProfileSettingsSectionIds.GLIDER_CONFIG -> "Glider configuration"
        ProfileSettingsSectionIds.UNITS_PREFERENCES -> "Units"
        ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES -> "Map style"
        ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES -> "Snail trail"
        ProfileSettingsSectionIds.ORIENTATION_PREFERENCES -> "Orientation"
        ProfileSettingsSectionIds.QNH_PREFERENCES -> "QNH"
        else -> sectionId
    }
}
