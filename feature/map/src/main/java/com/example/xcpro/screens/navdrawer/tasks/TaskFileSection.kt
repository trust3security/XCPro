package com.example.xcpro.screens.navdrawer.tasks

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xcpro.airspace.AirspaceUiState
import com.example.xcpro.flightdata.WaypointsUiState

@Composable
fun WaypointSection(
    waypointFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    uiState: WaypointsUiState,
    onDeleteFile: (String) -> Unit,
    onClearError: () -> Unit
) {
    SectionHeader(
        title = "Waypoint Files",
        description = "Import CUP files and toggle them on the map",
        actionLabel = "Add Waypoint File",
        onAction = { waypointFilePickerLauncher.launch("*/*") }
    )

    uiState.errorMessage?.let {
        ErrorBanner(message = it, onDismiss = onClearError)
    }

    TaskSelectedFileList(
        files = uiState.files,
        onRemove = { document -> onDeleteFile(document.fileName()) }
    )
}

@Composable
fun AirspaceSection(
    airspaceFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    uiState: AirspaceUiState,
    onDeleteFile: (String) -> Unit,
    onClearError: () -> Unit
) {
    SectionHeader(
        title = "Airspace Files",
        description = "Import TXT files and toggle them on the map",
        actionLabel = "Add Airspace File",
        onAction = { airspaceFilePickerLauncher.launch("*/*") }
    )

    uiState.errorMessage?.let {
        ErrorBanner(message = it, onDismiss = onClearError)
    }

    TaskSelectedFileList(
        files = uiState.files,
        onRemove = { document -> onDeleteFile(document.fileName()) }
    )
}

@Composable
fun AirspaceClassesSection(
    uiState: AirspaceUiState,
    onToggle: (String) -> Unit
) {
    SectionHeader(
        title = "Airspace Classes",
        description = "Toggle which airspace classes are visible on the map"
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
        items(uiState.classItems, key = { it.className }) { airspaceClass ->
            TaskAirspaceClassCard(
                airspaceClass = airspaceClass,
                onToggle = onToggle
            )
        }
    }
}

@Composable
fun SectionHeader(
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
