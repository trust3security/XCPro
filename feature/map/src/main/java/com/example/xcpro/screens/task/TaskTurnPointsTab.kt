package com.example.xcpro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TurnPointsTab(
    task: SoaringTask,
    onTaskUpdated: (SoaringTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TaskNameCard(
                taskName = task.name,
                onNameChanged = { newName ->
                    onTaskUpdated(task.copy(name = newName))
                }
            )
        }

        item {
            TaskTypeSelector(
                selectedType = task.type,
                onTypeSelected = { newType ->
                    onTaskUpdated(task.copy(type = newType))
                }
            )
        }

        item {
            QuickTaskCreationCard(
                currentTask = task,
                onTaskUpdated = onTaskUpdated
            )
        }

        if (task.waypoints.isNotEmpty()) {
            item {
                CurrentWaypointsCard(task.waypoints)
            }
        }

        item {
            WaypointSearchCard()
        }
    }
}

@Composable
private fun TaskNameCard(
    taskName: String,
    onNameChanged: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Task Name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = taskName,
                onValueChange = onNameChanged,
                label = { Text("Enter task name.") },
                placeholder = { Text("Lake Keepit Triangle") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TaskTypeSelector(
    selectedType: TaskType,
    onTypeSelected: (TaskType) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Task Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TaskType.values().forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = type == selectedType,
                        onClick = { onTypeSelected(type) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTaskCreationCard(
    currentTask: SoaringTask,
    onTaskUpdated: (SoaringTask) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "3-Touch Task Creation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Build your task in three simple steps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickStepButton(
                    step = "1",
                    label = "Start",
                    onClick = {
                        val sampleWaypoint = Waypoint(
                            name = "Start Point",
                            code = "START",
                            latitude = -30.87,
                            longitude = 150.52,
                            elevation = "1000ft"
                        )
                        val taskWaypoint = TaskWaypoint(sampleWaypoint, WaypointRole.START)
                        val updatedTask = currentTask.copy(
                            waypoints = currentTask.waypoints + taskWaypoint
                        )
                        onTaskUpdated(updatedTask)
                    }
                )
                QuickStepButton(
                    step = "2",
                    label = "Turn",
                    onClick = {
                        val sampleWaypoint = Waypoint(
                            name = "Turn Point",
                            code = "TURN",
                            latitude = -30.90,
                            longitude = 150.55,
                            elevation = "2000ft"
                        )
                        val taskWaypoint = TaskWaypoint(sampleWaypoint, WaypointRole.TURNPOINT)
                        val updatedTask = currentTask.copy(
                            waypoints = currentTask.waypoints + taskWaypoint
                        )
                        onTaskUpdated(updatedTask)
                    }
                )
                QuickStepButton(
                    step = "3",
                    label = "Finish",
                    onClick = {
                        val sampleWaypoint = Waypoint(
                            name = "Finish Point",
                            code = "FINISH",
                            latitude = -30.85,
                            longitude = 150.50,
                            elevation = "1500ft"
                        )
                        val taskWaypoint = TaskWaypoint(sampleWaypoint, WaypointRole.FINISH)
                        val updatedTask = currentTask.copy(
                            waypoints = currentTask.waypoints + taskWaypoint
                        )
                        onTaskUpdated(updatedTask)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickStepButton(
    step: String,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(80.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(step, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CurrentWaypointsCard(waypoints: List<TaskWaypoint>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Task Waypoints (${waypoints.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            waypoints.forEach { taskWaypoint ->
                Text(
                    text = "${taskWaypoint.role.displayName}: ${taskWaypoint.waypoint.name}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun WaypointSearchCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Search Waypoints",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = "",
                onValueChange = { /* TODO: Search waypoints */ },
                label = { Text("Search waypoints.") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Waypoint search will be integrated with your existing .cup files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
