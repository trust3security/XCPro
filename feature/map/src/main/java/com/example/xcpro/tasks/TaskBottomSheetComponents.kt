package com.example.xcpro.tasks

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.WaypointData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.xcpro.tasks.MinimizedContent
import com.example.xcpro.tasks.WaypointPreviewCard

enum class TaskCategory(val label: String) {
    MANAGE("Manage"),
    RULES("Rules"),
    FILES("Files"),
    FOUR("Four"),
    FIVE("Five")
}

private fun getCategoryColor(category: TaskCategory): Color {
    return when (category) {
        TaskCategory.MANAGE -> Color(0xFF4CAF50)  // Green
        TaskCategory.RULES -> Color(0xFF2196F3)   // Blue
        TaskCategory.FILES -> Color(0xFFFF9800)   // Orange
        TaskCategory.FOUR -> Color(0xFF9C27B0)    // Purple
        TaskCategory.FIVE -> Color(0xFFF44336)    // Red
    }
}

private data class CupDownloadEntry(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long
)

@Composable
fun FilesBTTab(
    taskManager: TaskManagerCoordinator,
    currentQNH: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cupFiles by remember { mutableStateOf<List<CupDownloadEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        loadError = null
        val result = runCatching {
            withContext(Dispatchers.IO) {
                queryCupDownloads(context)
            }
        }
        if (result.isSuccess) {
            cupFiles = result.getOrDefault(emptyList())
        } else {
            loadError = result.exceptionOrNull()?.message
            cupFiles = emptyList()
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Task Files",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loadError ?: "Unable to load Downloads",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        } else if (cupFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No task files found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Text(
                    text = "Copy .cup files to Downloads folder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { TaskFileOperations.showImportInfo(context) }
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Help")
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = cupFiles,
                    key = { it.uri.toString() }
                ) { entry ->
                    CupTaskFileCard(
                        entry = entry,
                        onImport = { selected ->
                            // Import logic would go here
                            android.widget.Toast.makeText(
                                context,
                                "Importing ${selected.displayName}...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CupTaskFileCard(
    entry: CupDownloadEntry,
    onImport: (CupDownloadEntry) -> Unit
) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showActions = !showActions },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = formatFileSize(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    val modifiedLabel = if (entry.lastModifiedEpochMillis > 0) {
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(Date(entry.lastModifiedEpochMillis))
                    } else {
                        "Unknown"
                    }
                    Text(
                        text = "Modified: $modifiedLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Icon(
                    imageVector = if (showActions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showActions) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (showActions) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onImport(entry) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }

                    OutlinedButton(
                        onClick = {
                            TaskFileOperations.shareTask(context, entry.uri, entry.displayName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Size: Unknown"
    val kiloBytes = bytes / 1024.0
    return if (kiloBytes < 1024) {
        "Size: ${String.format(Locale.getDefault(), "%.1f KB", kiloBytes)}"
    } else {
        val megaBytes = kiloBytes / 1024.0
        "Size: ${String.format(Locale.getDefault(), "%.2f MB", megaBytes)}"
    }
}

private fun queryCupDownloads(context: Context): List<CupDownloadEntry> {
    val resolver = context.contentResolver
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Downloads._ID,
        MediaStore.Downloads.DISPLAY_NAME,
        MediaStore.Downloads.SIZE,
        MediaStore.Downloads.DATE_MODIFIED
    )
    val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("%.cup")
    val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

    val entries = mutableListOf<CupDownloadEntry>()
    resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
        val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

        while (cursor.moveToNext()) {
            val name = cursor.getString(nameColumn) ?: continue
            val id = cursor.getLong(idColumn)
            val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
            val modifiedSeconds = cursor.getLong(modifiedColumn).coerceAtLeast(0L)

            entries += CupDownloadEntry(
                uri = ContentUris.withAppendedId(collection, id),
                displayName = name,
                sizeBytes = size,
                lastModifiedEpochMillis = if (modifiedSeconds > 0) modifiedSeconds * 1000 else 0L
            )
        }
    }

    return entries
}

@Composable
fun TaskPreviewContent(task: Task, taskManager: TaskManagerCoordinator) {
    val context = LocalContext.current
    var showQRDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Task Preview",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (task.waypoints.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No waypoints in task",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(task.waypoints.size) { index ->
                    val waypoint = task.waypoints[index]
                    WaypointPreviewCard(
                        waypoint = waypoint,
                        index = index,
                        total = task.waypoints.size
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { TaskFileOperations.exportTaskToDownloads(context, taskManager) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }

                OutlinedButton(
                    onClick = { TaskFileOperations.exportTaskWithSharing(context, taskManager) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = { showQRDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("QR")
                }
            }
        }
    }

    if (showQRDialog) {
        QRCodeDialog(
            task = task,
            taskManager = taskManager,
            onDismiss = { showQRDialog = false }
        )
    }
}

