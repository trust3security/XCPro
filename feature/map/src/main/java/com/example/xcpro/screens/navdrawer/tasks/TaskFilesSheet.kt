package com.example.xcpro.screens.navdrawer.tasks

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomAppBar
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.parseAirspaceClasses
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveSelectedClasses
import com.example.xcpro.saveWaypointFiles
import com.example.ui1.screens.AirspaceClassItem

@Composable
fun TaskAirspaceClassCard(
    airspaceClass: AirspaceClassItem,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
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
                        color = Color(airspaceClass.color.toColorInt())
                            .copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        width = 2.dp,
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier) {
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
    selectedClasses: MutableState<MutableMap<String, Boolean>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFFF5F5F5))
            .padding(bottom = 54.dp)
    ) {
        TaskFilesSheetHandle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                
                .background(Color.White, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            when (selectedItem) {
                "Airspace" -> AirspaceSection(
                    context = context,
                    mapLibreMap = mapLibreMap,
                    airspaceFilePickerLauncher = airspaceFilePickerLauncher,
                    errorMessage = errorMessage,
                    onErrorMessage = onErrorMessage,
                    selectedAirspaceFiles = selectedAirspaceFiles,
                    airspaceCheckedStates = airspaceCheckedStates
                )

                "Waypoints" -> WaypointSection(
                    context = context,
                    mapLibreMap = mapLibreMap,
                    waypointFilePickerLauncher = waypointFilePickerLauncher,
                    errorMessage = errorMessage,
                    onErrorMessage = onErrorMessage,
                    selectedWaypointFiles = selectedWaypointFiles,
                    waypointCheckedStates = waypointCheckedStates
                )

                "Classes" -> AirspaceClassesSection(
                    context = context,
                    mapLibreMap = mapLibreMap,
                    airspaceFiles = selectedAirspaceFiles,
                    selectedClasses = selectedClasses
                )

                else -> Text(
                    text = selectedItem ?: "No Item Selected",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 32.dp),
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
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskFilesSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .padding(top = 8.dp)
            .background(
                color = Color.Black.copy(alpha = 0.2f),
                shape = RoundedCornerShape(2.dp)
            )
    )
}

@Composable
private fun AirspaceSection(
    context: Context,
    mapLibreMap: MapLibreMap?,
    airspaceFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    errorMessage: String?,
    onErrorMessage: (String?) -> Unit,
    selectedAirspaceFiles: MutableList<Uri>,
    airspaceCheckedStates: MutableState<MutableMap<String, Boolean>>
) {
    SectionHeader(
        title = "Airspace Files",
        description = "Manage loaded airspace files and toggle active layers",
        actionLabel = "Add Airspace File",
        onAction = { airspaceFilePickerLauncher.launch("*/*") }
    )

    errorMessage?.let {
        ErrorBanner(message = it, onDismiss = { onErrorMessage(null) })
    }

    TaskSelectedFileList(
        files = selectedAirspaceFiles,
        onRemove = { uri ->
            selectedAirspaceFiles.remove(uri)
            saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
            loadAndApplyAirspace(context, mapLibreMap)
        }
    )

    val airspaceClasses = rememberAirspaceClasses(
        context = context,
        files = selectedAirspaceFiles,
        selectedStates = airspaceCheckedStates.value
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(airspaceClasses, key = { it.className }) { airspaceClass ->
            TaskAirspaceClassCard(
                airspaceClass = airspaceClass,
                onToggle = { className ->
                    airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                        put(className, !(get(className) ?: false))
                    }
                    saveSelectedClasses(context, airspaceCheckedStates.value)
                    loadAndApplyAirspace(context, mapLibreMap)
                }
            )
        }
    }
}

@Composable
private fun WaypointSection(
    context: Context,
    mapLibreMap: MapLibreMap?,
    waypointFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    errorMessage: String?,
    onErrorMessage: (String?) -> Unit,
    selectedWaypointFiles: MutableList<Uri>,
    waypointCheckedStates: MutableState<MutableMap<String, Boolean>>
) {
    SectionHeader(
        title = "Waypoint Files",
        description = "Import CUP files and toggle them on the map",
        actionLabel = "Add Waypoint File",
        onAction = { waypointFilePickerLauncher.launch("*/*") }
    )

    errorMessage?.let {
        ErrorBanner(message = it, onDismiss = { onErrorMessage(null) })
    }

    TaskSelectedFileList(
        files = selectedWaypointFiles,
        onRemove = { uri ->
            selectedWaypointFiles.remove(uri)
            saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
            loadAndApplyWaypoints(context, mapLibreMap, selectedWaypointFiles, waypointCheckedStates.value)
        }
    )
}

@Composable
private fun AirspaceClassesSection(
    context: Context,
    mapLibreMap: MapLibreMap?,
    airspaceFiles: List<Uri>,
    selectedClasses: MutableState<MutableMap<String, Boolean>>
) {
    SectionHeader(
        title = "Airspace Classes",
        description = "Toggle which airspace classes are visible on the map"
    )

    val airspaceClassItems = rememberAirspaceClasses(
        context = context,
        files = airspaceFiles,
        selectedStates = selectedClasses.value
    )
    val listState = rememberLazyListState()
    val isScrollable = listState.canScrollForward || listState.canScrollBackward

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            
            .padding(horizontal = 16.dp)
            .drawWithContent {
                drawContent()
                if (isScrollable) {
                    val firstVisible = listState.firstVisibleItemIndex
                    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                    val totalCount = listState.layoutInfo.totalItemsCount
                    val scrollFraction = if (totalCount > visibleCount) {
                        firstVisible.toFloat() / (totalCount - visibleCount)
                    } else 0f
                    val scrollbarHeight = size.height / totalCount.coerceAtLeast(1) * visibleCount
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
            TaskAirspaceClassCard(
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

@Composable
private fun SectionHeader(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = Material3Theme.typography.titleMedium,
            color = Material3Theme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = Material3Theme.typography.bodySmall,
            color = Material3Theme.colorScheme.onSurfaceVariant
        )
        actionLabel?.let { label ->
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onAction?.invoke() }) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color(0xFFFFE2E2), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = Material3Theme.typography.bodySmall,
            color = Color(0xFF8B0000),
            modifier = Modifier
        )
        Text(
            text = "Dismiss",
            style = Material3Theme.typography.labelSmall,
            color = Color(0xFF8B0000),
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable { onDismiss() }
        )
    }
}

@Composable
private fun TaskSelectedFileList(
    files: MutableList<Uri>,
    onRemove: (Uri) -> Unit
) {
    if (files.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        files.forEach { fileUri ->
            val fileName = fileUri.lastPathSegment ?: "Unknown file"
            androidx.compose.material.Surface(
                shape = RoundedCornerShape(10.dp),
                elevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fileName, style = MaterialTheme.typography.body1)
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.caption,
                        color = Color.Red,
                        modifier = Modifier.clickable { onRemove(fileUri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberAirspaceClasses(
    context: Context,
    files: List<Uri>,
    selectedStates: Map<String, Boolean>
): List<AirspaceClassItem> {
    return parseAirspaceClasses(context, files).map { className ->
        AirspaceClassItem(
            className = className,
            enabled = selectedStates[className] ?: false,
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
                "C" -> "Controlled - Radio required"
                "D" -> "Controlled - Radio required"
                "R" -> "Restricted airspace"
                "G" -> "General - Uncontrolled"
                "CTR" -> "Control Zone"
                "TMZ" -> "Transponder mandatory"
                else -> "Unknown class"
            }
        )
    }
}

