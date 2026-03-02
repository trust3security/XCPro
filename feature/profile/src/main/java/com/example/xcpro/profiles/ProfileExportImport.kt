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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xcpro.core.time.TimeBridge
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.gson.GsonBuilder
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

data class ProfileExport(
    val version: String = "1.0",
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        .format(TimeBridge.nowWallMs()),
    val profiles: List<UserProfile>
)

class ProfileExportImport(private val context: Context) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    
    suspend fun exportProfile(profile: UserProfile): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val export = ProfileExport(profiles = listOf(profile))
                val json = gson.toJson(export)
                Result.success(json)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun exportAllProfiles(profiles: List<UserProfile>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (profiles.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("No profiles available to export.")
                    )
                }
                val export = ProfileExport(profiles = profiles)
                val json = gson.toJson(export)
                Result.success(json)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun importProfiles(json: String): Result<List<UserProfile>> {
        return withContext(Dispatchers.IO) {
            try {
                val export = gson.fromJson(json, ProfileExport::class.java)
                
                // Validate import version compatibility
                if (export.version != "1.0") {
                    return@withContext Result.failure(Exception("Unsupported profile export version: ${export.version}"))
                }
                
                // Generate new IDs for imported profiles to avoid conflicts
                val importedProfiles = export.profiles.map { profile ->
                    profile.copy(
                        id = UUID.randomUUID().toString(),
                        isActive = false // Don't import as active
                    )
                }
                
                Result.success(importedProfiles)
            } catch (e: Exception) {
                Result.failure(Exception("Failed to parse profile data: ${e.message}"))
            }
        }
    }
    
    fun generateFileName(profile: UserProfile?): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(TimeBridge.nowWallMs())
        return if (profile != null) {
            "profile_${profile.name.replace(" ", "_")}_$timestamp.json"
        } else {
            "all_profiles_$timestamp.json"
        }
    }
}

@Composable
fun ProfileExportDialog(
    profile: UserProfile?,
    allProfiles: List<UserProfile> = emptyList(),
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportImport = remember { ProfileExportImport(context) }
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
                onExport("Profile exported successfully!")
            } catch (e: Exception) {
                onExport("Failed to save file: ${e.message}")
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile != null) "Export Profile" else "Export All Profiles")
        },
        text = {
            if (isExporting) {
                Text("Preparing export...")
            } else {
                Text(
                    if (profile != null) {
                        "Export profile '${profile.name}' to share or backup your configuration."
                    } else {
                        "Export all profiles to backup your configurations."
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isExporting = true
                    
                    coroutineScope.launch {
                        val result = if (profile != null) {
                            exportImport.exportProfile(profile)
                        } else {
                            exportImport.exportAllProfiles(allProfiles)
                        }
                        exportResult = result
                        isExporting = false
                        
                        if (result.isSuccess) {
                            documentLauncher.launch(exportImport.generateFileName(profile))
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
    onImport: (List<UserProfile>, Boolean) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportImport = remember { ProfileExportImport(context) }
    var isImporting by remember { mutableStateOf(false) }
    var keepCurrentActive by remember { mutableStateOf(true) }
    
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val json = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                    }
                }.getOrElse { error ->
                    isImporting = false
                    onError("Failed to read file: ${error.message}")
                    return@launch
                }

                if (json == null) {
                    isImporting = false
                    onError("Failed to read file")
                    return@launch
                }

                val result = exportImport.importProfiles(json)
                isImporting = false

                if (result.isSuccess) {
                    onImport(result.getOrNull()!!, keepCurrentActive)
                } else {
                    onError("Import failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Profiles") },
        text = {
            Column {
                if (isImporting) {
                    Text("Importing profiles...")
                } else {
                    Text("Select a profile export file to import configurations.")
                    Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                    Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = keepCurrentActive,
                            onCheckedChange = { keepCurrentActive = it },
                            enabled = !isImporting
                        )
                        Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                        Text("Keep current active profile")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    documentLauncher.launch(arrayOf("application/json"))
                },
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
