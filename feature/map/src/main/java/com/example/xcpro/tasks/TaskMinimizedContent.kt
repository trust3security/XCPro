package com.example.xcpro.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$waypointCount",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
