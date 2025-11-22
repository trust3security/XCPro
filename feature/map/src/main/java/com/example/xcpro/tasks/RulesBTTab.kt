package com.example.xcpro.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.util.Locale
import com.example.xcpro.tasks.TaskManagerCoordinator

@Composable
fun RulesBTTab(
    selected: TaskType,
    onSelect: (TaskType) -> Unit,
    taskManager: TaskManagerCoordinator? = null,
    taskViewModel: TaskSheetViewModel? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // Task Type Selection
        Text(
            text = "Competition Task Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Enhanced waypoint preservation info with smart conversion feedback
        taskManager?.let { tm ->
            val currentWaypoints = tm.currentTask.waypoints
            if (currentWaypoints.isNotEmpty()) {
                val customizedWaypoints = currentWaypoints.filter { it.hasCustomizations }
                val standardizedWaypoints = currentWaypoints.size - customizedWaypoints.size

                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Header with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Task Type Switching",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Current task summary
                        Text(
                            text = "Current ${tm.taskType.name} task: ${currentWaypoints.size} waypoints",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (customizedWaypoints.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "✅ ${customizedWaypoints.size} waypoint${if (customizedWaypoints.size > 1) "s" else ""} with custom settings will be preserved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (standardizedWaypoints > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "🔄 ${standardizedWaypoints} waypoint${if (standardizedWaypoints > 1) "s" else ""} will use new task type defaults",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Standardized defaults info
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = "Standardized Defaults",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Start Lines:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "10km",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Finish Cylinders:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "3km",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        
        // Task Type Cards
        TaskTypeCard(
            taskType = TaskType.RACING,
            isSelected = selected == TaskType.RACING,
            onClick = {
                onSelect(TaskType.RACING)
                taskViewModel?.onSetTaskType(TaskType.RACING)
            },
            title = "Racing Task",
            description = "Fixed course with turnpoints. Fastest wins.",
            icon = Icons.Default.Speed,
            color = Color(0xFF2196F3) // Blue
        )
        
        Spacer(Modifier.height(8.dp))
        
        TaskTypeCard(
            taskType = TaskType.AAT,
            isSelected = selected == TaskType.AAT,
            onClick = {
                onSelect(TaskType.AAT)
                taskViewModel?.onSetTaskType(TaskType.AAT)
            },
            title = "Assigned Area Task (AAT)",
            description = "Flexible course with area targets and minimum time.",
            icon = Icons.Default.LocationOn,
            color = Color(0xFF4CAF50) // Green
        )
        
        Spacer(Modifier.height(8.dp))
        
        Spacer(Modifier.height(16.dp))

        // Task-specific parameters
        when (selected) {
            TaskType.RACING -> RacingTaskParameters()
            TaskType.AAT -> AATTaskParameters(taskManager)
        }
    }
}

@Composable
private fun TaskTypeCard(
    taskType: TaskType,
    isSelected: Boolean,
    onClick: () -> Unit,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                color.copy(alpha = 0.1f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, color)
        else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = if (isSelected) color else color.copy(alpha = 0.3f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun RacingTaskParameters() {
    ParameterSection(
        title = "Racing Task Rules",
        icon = Icons.Default.Speed,
        color = Color(0xFF2196F3)
    ) {
        ParameterItem(
            label = "Start Type",
            value = "Start Line / Start Circle"
        )
        ParameterItem(
            label = "Turnpoints",
            value = "Fixed cylinders (500m radius)"
        )
        ParameterItem(
            label = "Finish",
            value = "Finish line or cylinder"
        )
        ParameterItem(
            label = "Scoring",
            value = "Speed: Distance ÷ Time"
        )
    }
}

@Composable
private fun AATTaskParameters(taskManager: TaskManagerCoordinator?) {
    // Initialize from AAT manager only when current task type is AAT
    val isAAT = taskManager?.taskType == TaskType.AAT
    val aatManager = if (isAAT) taskManager?.getAATTaskManager() else null
    val initialMinTime = aatManager?.currentAATTask?.minimumTime?.toHours()?.toFloat() ?: 3.0f
    val initialMaxTime = aatManager?.currentAATTask?.maximumTime?.toHours()?.toFloat() ?: 4.0f
    
    var minTime by remember { mutableStateOf(initialMinTime) }
    var maxTime by remember { mutableStateOf(initialMaxTime) }
    
    ParameterSection(
        title = "AAT Task Parameters",
        icon = Icons.Default.LocationOn,
        color = Color(0xFF4CAF50)
    ) {
        // Minimum time slider
        Column {
            Text(
                text = "Minimum Time: ${String.format(Locale.US, "%.1f", minTime)} hours",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = minTime,
                onValueChange = { 
                    minTime = it
                    taskManager?.updateAATParameters(
                        Duration.ofMinutes((minTime * 60).toLong()),
                        Duration.ofMinutes((maxTime * 60).toLong())
                    )
                },
                valueRange = 1.0f..6.0f,
                steps = 49 // 0.1 hour increments
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Maximum time slider
        Column {
            Text(
                text = "Maximum Time: ${String.format(Locale.US, "%.1f", maxTime)} hours",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = maxTime,
                onValueChange = { 
                    maxTime = it
                    taskManager?.updateAATParameters(
                        Duration.ofMinutes((minTime * 60).toLong()),
                        Duration.ofMinutes((maxTime * 60).toLong())
                    )
                },
                valueRange = 2.0f..8.0f,
                steps = 59 // 0.1 hour increments
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        ParameterItem(
            label = "Areas",
            value = "Cylinders/Sectors (10km+ radius)"
        )
        ParameterItem(
            label = "Strategy",
            value = "Maximize distance within time"
        )
        ParameterItem(
            label = "Scoring",
            value = "Distance handicapped by time"
        )
    }
}

@Composable
private fun ParameterSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

@Composable
private fun ParameterItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}




