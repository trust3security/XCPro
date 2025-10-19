package com.example.ui1.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.material3.DrawerState
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.parseAirspaceClasses
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveSelectedClasses
import com.example.xcpro.saveWaypointFiles
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints

@Composable
fun TaskAirspaceClassCardView(
    airspaceClass: AirspaceClassItem,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE5E5E5)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Color(android.graphics.Color.parseColor(airspaceClass.color))
                            .copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        2.dp,
                        Color.Black.copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Class ${airspaceClass.className}",
                    color = Color.Black
                )
                Text(
                    text = airspaceClass.description,
                    color = Color(0xFF666666)
                )
            }

            Checkbox(
                checked = airspaceClass.enabled,
                onCheckedChange = { onToggle(airspaceClass.className) }
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TaskFilesBottomSheetContent(
    selectedItem: String?,
    onSelectItem: (String) -> Unit,
    context: Context,
    mapLibreMap: MapLibreMap?,
    airspaceFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    waypointFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    errorMessage: String?,
    onErrorMessage: (String?) -> Unit,
    selectedAirspaceFiles: MutableList<Uri>,
    airspaceCheckedStates: MutableState<MutableMap<String, Boolean>>,
    selectedWaypointFiles: MutableList<Uri>,
    waypointCheckedStates: MutableState<MutableMap<String, Boolean>>,
    selectedClasses: MutableState<MutableMap<String, Boolean>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFFF5F5F5))
            .padding(bottom = 54.dp)
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(top = 8.dp)
                .background(Color.Gray, shape = RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
        )

        when (selectedItem) {
            "Airspace" -> {
                Button(
                    onClick = { airspaceFilePickerLauncher.launch("text/plain") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Select Airspace Files")
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
                val listState = rememberLazyListState()
                val isScrollable = listState.canScrollForward || listState.canScrollBackward
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .drawWithContent {
                            drawContent()
                            if (isScrollable) {
                                val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
                                val totalItemCount = listState.layoutInfo.totalItemsCount
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
                                    saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                                    val newClasses = parseAirspaceClasses(context, selectedAirspaceFiles)
                                    selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                        keys.retainAll(newClasses)
                                    }
                                    saveSelectedClasses(context, selectedClasses.value)
                                    loadAndApplyAirspace(context, mapLibreMap)
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = fileName.take(20).let { if (it.length >= 20) "$it..." else it },
                                modifier = Modifier.weight(1f),
                                color = Color.Black
                            )
                            IconButton(onClick = {
                                selectedAirspaceFiles.remove(fileUri)
                                airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                    remove(fileName)
                                }
                                saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                                val newClasses = parseAirspaceClasses(context, selectedAirspaceFiles)
                                selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                    keys.retainAll(newClasses)
                                }
                                saveSelectedClasses(context, selectedClasses.value)
                                loadAndApplyAirspace(context, mapLibreMap)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = Color.Black
                                )
                            }
                        }
                    }
                }
            }
            "Waypoints" -> {
                Button(
                    onClick = { waypointFilePickerLauncher.launch("application/octet-stream") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Select Waypoint Files") }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
                val listState = rememberLazyListState()
                val isScrollable = listState.canScrollForward || listState.canScrollBackward
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .drawWithContent {
                            drawContent()
                            if (isScrollable) {
                                val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
                                val totalItemCount = listState.layoutInfo.totalItemsCount
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
                    items(selectedWaypointFiles, key = { it.toString() }) { fileUri ->
                        val fileName = fileUri.lastPathSegment?.substringAfterLast("/") ?: "Unknown file"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = waypointCheckedStates.value[fileName] ?: false,
                                onCheckedChange = {
                                    waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                                        put(fileName, it)
                                    }
                                    saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                                    loadAndApplyWaypoints(context, mapLibreMap, selectedWaypointFiles, waypointCheckedStates.value)
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = fileName.take(20).let { if (it.length >= 20) "$it..." else it },
                                modifier = Modifier.weight(1f),
                                color = Color.Black
                            )
                            IconButton(onClick = {
                                selectedWaypointFiles.remove(fileUri)
                                waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                                    remove(fileName)
                                }
                                saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                                loadAndApplyWaypoints(context, mapLibreMap, selectedWaypointFiles, waypointCheckedStates.value)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file",
                                    tint = Color.Black
                                )
                            }
                        }
                    }
                }
            }
            "Classes" -> {
                val classes = parseAirspaceClasses(context, selectedAirspaceFiles)
                if (classes.isEmpty()) {
                    Text(
                        text = "No airspace classes available. Please add airspace files.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                } else {
                    val airspaceClassItems = classes.map { className ->
                        AirspaceClassItem(
                            className = className,
                            enabled = selectedClasses.value[className] ?: false,
                            color = when (className) {
                                "A" -> "#FF0000"
                                "C" -> "#FF6600"
                                "D" -> "#0066FF"
                                "R" -> "#FF0000"
                                "G" -> "#00AA00"
                                "CTR" -> "#9900FF"
                                "TMZ" -> "#FFFF00"
                                else -> "#888888"
                            },
                            description = when (className) {
                                "A" -> "Controlled - IFR only"
                                "C" -> "Controlled - Radio req"
                                "D" -> "Controlled - Radio req"
                                "R" -> "Restricted"
                                "G" -> "General - Uncontrolled"
                                "CTR" -> "Control Zone"
                                "TMZ" -> "Transponder Mandatory"
                                else -> "Unknown class"
                            }
                        )
                    }

                    val listState = rememberLazyListState()
                    val isScrollable = listState.canScrollForward || listState.canScrollBackward
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .drawWithContent {
                                drawContent()
                                if (isScrollable) {
                                    val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                    val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
                                    val totalItemCount = listState.layoutInfo.totalItemsCount
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
                        items(airspaceClassItems, key = { it.className }) { airspaceClass ->
                            TaskAirspaceClassCardView(
                                airspaceClass = airspaceClass,
                                onToggle = { className ->
                                    selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                        put(className, !(get(className) ?: false))
                                    }
                                    saveSelectedClasses(context, selectedClasses.value)
                                    loadAndApplyAirspace(context, mapLibreMap)
                                }
                            )
                        }
                    }
                }
            }
            else -> {
                Text(
                    text = selectedItem ?: "No Item Selected",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun TaskFilesBottomBar(
    selectedItem: String?,
    onItemClick: (String) -> Unit
) {
    BottomAppBar(
        modifier = Modifier.height(54.dp),
        elevation = 8.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            listOf("Airspace", "Waypoints", "Classes").forEach { itemName ->
                Column(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clickable { onItemClick(itemName) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = itemName,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Text(
                        text = itemName,
                        style = androidx.compose.material.MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            }
        }
    }
}
