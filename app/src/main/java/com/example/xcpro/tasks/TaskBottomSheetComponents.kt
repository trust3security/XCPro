package com.example.xcpro.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xcpro.WaypointData
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

@Composable
fun MinimizedContent(task: Task, taskManager: TaskManagerCoordinator) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskMinimizedIndicator(
            taskType = taskManager.taskType,
            waypointCount = task.waypoints.size
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${taskManager.taskType} Task",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${task.waypoints.size} waypoints",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun TaskMinimizedIndicator(
    taskType: TaskType,
    waypointCount: Int
) {
    Text(
        text = "$waypointCount",
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .wrapContentSize(),
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun FilesBTTab(
    taskManager: TaskManagerCoordinator,
    currentQNH: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cupFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath)
            val files = if (downloadsDir.exists()) {
                downloadsDir.listFiles { file ->
                    file.extension.lowercase() == "cup"
                }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            cupFiles = files.sortedByDescending { it.lastModified() }
        } catch (e: Exception) {
            cupFiles = emptyList()
        } finally {
            isLoading = false
        }
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
                items(cupFiles) { file ->
                    CUPTaskFileCard(
                        file = file,
                        onImport = { selectedFile ->
                            // Import logic would go here
                            android.widget.Toast.makeText(
                                context,
                                "Importing ${selectedFile.name}...",
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
private fun CUPTaskFileCard(
    file: File,
    onImport: (File) -> Unit
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
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Size: ${String.format("%.1f KB", file.length() / 1024.0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Text(
                        text = "Modified: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(file.lastModified()))}",
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
                        onClick = { onImport(file) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }

                    OutlinedButton(
                        onClick = { TaskFileOperations.shareTask(context, file.name) },
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

@Composable
private fun WaypointPreviewCard(
    waypoint: TaskWaypoint,
    index: Int,
    total: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        when (index) {
                            0 -> Color(0xFF4CAF50) // Start - Green
                            total - 1 -> Color(0xFFF44336) // Finish - Red
                            else -> Color(0xFF2196F3) // Turnpoint - Blue
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (waypoint.subtitle.isNotEmpty()) {
                    Text(
                        text = waypoint.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${String.format("%.5f", waypoint.lat)}, ${String.format("%.5f", waypoint.lon)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

            Text(
                text = when (index) {
                    0 -> "START"
                    total - 1 -> "FINISH"
                    else -> "TP${index}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}