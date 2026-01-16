package com.example.xcpro.screens.airspace

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.loadAndApplyAirspace
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import java.io.File

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
                            Log.e("AirspaceSettings", "Selected file is not a .txt file: $fileName")
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
                        Log.e("AirspaceSettings", "Error copying file: ${e.message}")
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
                    Log.e("AirspaceSettings", "No activity found to handle file picker: ${e.message}")
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
                .drawWithContent {
                    drawContent()
                    if (isFileScrollable) {
                        val firstVisibleItemIndex = fileListState.firstVisibleItemIndex
                        val visibleItemCount = fileListState.layoutInfo.visibleItemsInfo.size
                        val totalItemCount = fileListState.layoutInfo.totalItemsCount
                        val scrollFraction = if (totalItemCount > 0) {
                            firstVisibleItemIndex.toFloat() / (totalItemCount - visibleItemCount).coerceAtLeast(1)
                        } else 0f
                        val scrollbarHeight = size.height / totalItemCount.coerceAtLeast(1) * visibleItemCount
                        val scrollbarOffsetY = scrollFraction * (size.height - scrollbarHeight)
                        drawRect(
                            color = Color.Gray.copy(alpha = 0.5f),
                            topLeft = Offset(size.width - 8.dp.toPx(), scrollbarOffsetY),
                            size = Size(8.dp.toPx(), scrollbarHeight.coerceAtLeast(8.dp.toPx()))
                        )
                    }
                }
        ) {
            items(selectedAirspaceFiles, key = { it.toString() }) { fileUri ->
                val fileName = fileUri.lastPathSegment?.substringAfterLast("/") ?: "Unknown file"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = airspaceCheckedStates.value[fileName] ?: false,
                        onCheckedChange = {
                            airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                put(fileName, it)
                            }
                            coroutineScope.launch {
                                airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                                loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = fileName.take(20).let {
                            if (it.length >= 20) "$it..." else it
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        coroutineScope.launch {
                            selectedAirspaceFiles.remove(fileUri)
                            airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                remove(fileName)
                            }
                            airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                            val newClasses = airspaceRepository.parseClasses(selectedAirspaceFiles)
                            selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                keys.retainAll(newClasses)
                            }
                            airspaceRepository.saveSelectedClasses(selectedClasses.value)
                            loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove file"
                        )
                    }
                }
            }
        }

        Text(
            text = "Select Airspace Classes to Display",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

    val classListState = rememberLazyListState()
    val isClassScrollable = classListState.canScrollForward || classListState.canScrollBackward
    val classes by produceState(initialValue = emptyList<String>(), selectedAirspaceFiles) {
        value = airspaceRepository.parseClasses(selectedAirspaceFiles)
    }
        if (classes.isEmpty()) {
            Text(
                text = "No airspace classes available. Please add airspace files.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                state = classListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .drawWithContent {
                        drawContent()
                        if (isClassScrollable) {
                            val firstVisibleItemIndex = classListState.firstVisibleItemIndex
                            val visibleItemCount = classListState.layoutInfo.visibleItemsInfo.size
                            val totalItemCount = classListState.layoutInfo.totalItemsCount
                            val scrollFraction = if (totalItemCount > 0) {
                                firstVisibleItemIndex.toFloat() / (totalItemCount - visibleItemCount).coerceAtLeast(1)
                            } else 0f

                            val scrollbarHeight = size.height / totalItemCount.coerceAtLeast(1) * visibleItemCount
                            val scrollbarOffsetY = scrollFraction * (size.height - scrollbarHeight)
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.5f),
                                topLeft = Offset(size.width - 8.dp.toPx(), scrollbarOffsetY),
                                size = Size(8.dp.toPx(), scrollbarHeight.coerceAtLeast(8.dp.toPx()))
                            )
                        }
                    }
            ) {
                items(classes, key = { it }) { airspaceClass ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedClasses.value[airspaceClass] ?: false,
                            onCheckedChange = {
                                selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                    put(airspaceClass, it)
                                }
                                coroutineScope.launch {
                                    airspaceRepository.saveSelectedClasses(selectedClasses.value)
                                    loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = airspaceClass,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Close")
        }
    }
}
