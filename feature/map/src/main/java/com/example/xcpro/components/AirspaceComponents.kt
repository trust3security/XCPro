package com.example.xcpro.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.loadAndApplyAirspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AirspaceComponents"

@Composable
fun AirspaceSettingsContent(
    mapLibreMap: MapLibreMap?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedAirspaceFiles = remember { mutableStateListOf<Uri>() }
    val airspaceCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    val selectedClasses = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val airspaceRepository = remember(context) { AirspaceRepository(context) }

    // Load saved airspace files and checkbox states
    LaunchedEffect(Unit) {
        val (airspaceFiles, airspaceChecks) = airspaceRepository.loadAirspaceFiles()
        selectedAirspaceFiles.clear()
        selectedAirspaceFiles.addAll(airspaceFiles)
        airspaceCheckedStates.value = airspaceChecks
        selectedClasses.value = airspaceRepository.loadSelectedClasses() ?: mutableMapOf()
    }

    val airspaceFilePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                coroutineScope.launch {
                    try {
                        val fileName = copyFileToInternalStorage(context, it)
                        if (!fileName.endsWith(".txt", ignoreCase = true)) {
                            errorMessage = "Only .txt files are supported for airspace files."
                            Log.e(TAG, "Selected file is not a .txt file: $fileName")
                            return@launch
                        }
                        if (!selectedAirspaceFiles.any { file -> file.lastPathSegment?.substringAfterLast("/") == fileName }) {
                            selectedAirspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                            airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                put(fileName, false)
                            }
                            airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                            val newClasses = airspaceRepository.parseClasses(selectedAirspaceFiles)
                            selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                newClasses.forEach { put(it, it == "R" || it == "D") }
                            }
                            airspaceRepository.saveSelectedClasses(selectedClasses.value)
                            loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                            errorMessage = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error copying file: ${e.message}")
                        errorMessage = "Error copying file: ${e.message}"
                    }
                }
            }
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Airspace Settings",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                try {
                    airspaceFilePickerLauncher.launch("text/plain")
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No activity found to handle file picker: ${e.message}")
                    errorMessage = "No file picker available on this device."
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Select Airspace Files")
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
        val fileListState = rememberLazyListState()
        val isFileScrollable = fileListState.canScrollForward || fileListState.canScrollBackward
        LazyColumn(
            state = fileListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .let { mod ->
                    if (isFileScrollable) {
                        mod.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        ).padding(8.dp)
                    } else {
                        mod
                    }
                }
        ) {
            items(selectedAirspaceFiles) { uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown File"
                val isChecked = airspaceCheckedStates.value[fileName] ?: false
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                        put(fileName, checked)
                                    }
                                    coroutineScope.launch {
                                        airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                                        loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        selectedAirspaceFiles.removeIf { it.lastPathSegment?.substringAfterLast("/") == fileName }
                                        airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                            remove(fileName)
                                        }
                                        airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                                        val newClasses = airspaceRepository.parseClasses(selectedAirspaceFiles)
                                        selectedClasses.value = newClasses.associateWith { it == "R" || it == "D" }.toMutableMap()
                                        airspaceRepository.saveSelectedClasses(selectedClasses.value)
                                        loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                                        val file = File(context.filesDir, fileName)
                                        if (file.exists()) {
                                            withContext(Dispatchers.IO) { file.delete() }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "Airspace Classes",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        val classListState = rememberLazyListState()
        val isClassScrollable = classListState.canScrollForward || classListState.canScrollBackward
        LazyColumn(
            state = classListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .let { mod ->
                    if (isClassScrollable) {
                        mod.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        ).padding(8.dp)
                    } else {
                        mod
                    }
                }
        ) {
            items(selectedClasses.value.keys.toList()) { className ->
                val isSelected = selectedClasses.value[className] ?: false
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Class $className",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                    put(className, checked)
                                }
                                coroutineScope.launch {
                                    airspaceRepository.saveSelectedClasses(selectedClasses.value)
                                    loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                                }
                            }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    }
}

