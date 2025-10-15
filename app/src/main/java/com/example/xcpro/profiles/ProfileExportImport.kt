package com.example.xcpro.profiles

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
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
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
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
                        isActive = false, // Don't import as active
                        createdAt = System.currentTimeMillis(),
                        lastUsed = 0L
                    )
                }
                
                Result.success(importedProfiles)
            } catch (e: Exception) {
                Result.failure(Exception("Failed to parse profile data: ${e.message}"))
            }
        }
    }
    
    fun generateFileName(profile: UserProfile?): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
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
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
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
                    // Note: In a real implementation, you'd get all profiles from repository
                    val profiles = if (profile != null) listOf(profile) else emptyList()
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = if (profile != null) {
                            exportImport.exportProfile(profile)
                        } else {
                            exportImport.exportAllProfiles(profiles)
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
    onImport: (List<UserProfile>) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val exportImport = remember { ProfileExportImport(context) }
    var isImporting by remember { mutableStateOf(false) }
    
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val json = inputStream?.bufferedReader()?.use { it.readText() }
                
                if (json != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = exportImport.importProfiles(json)
                        isImporting = false
                        
                        if (result.isSuccess) {
                            onImport(result.getOrNull()!!)
                        } else {
                            onError("Import failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    isImporting = false
                    onError("Failed to read file")
                }
            } catch (e: Exception) {
                isImporting = false
                onError("Failed to read file: ${e.message}")
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Profiles") },
        text = {
            if (isImporting) {
                Text("Importing profiles...")
            } else {
                Text("Select a profile export file to import configurations.")
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