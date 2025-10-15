// app/src/main/java/com/example/baseui1/TaskCreation.kt
package com.example.xcpro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.*

// ================================
// WAYPOINT DATA CLASS (simple version)
// ================================

data class Waypoint(
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: String
)

// ================================
// TASK DATA MODELS
// ================================

data class SoaringTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: TaskType = TaskType.RACING,
    val waypoints: List<TaskWaypoint> = emptyList(),
    val distance: Double = 0.0,
    val created: LocalDateTime = LocalDateTime.now()
)

enum class TaskType(val displayName: String) {
    RACING("Racing Task"),
    AAT("Assigned Area Task")
}

data class TaskWaypoint(
    val waypoint: Waypoint,
    val role: WaypointRole,
    val radius: Double = 500.0
)

enum class WaypointRole(val displayName: String) {
    START("Start"),
    TURNPOINT("Turnpoint"),
    FINISH("Finish")
}

// ================================
// MAIN UI SCREEN
// ================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreation(
    navController: NavHostController,
    drawerState: DrawerState
) {
    var activeTab by remember { mutableStateOf("turnpoints") }
    var currentTask by remember { mutableStateOf(SoaringTask()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, // Blue background
                    titleContentColor = MaterialTheme.colorScheme.onPrimary, // White title
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, // White back button
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary // White action icons
                ),
                title = {
                    Text(
                        text = "Create Task",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            navController.popBackStack()
                            drawerState.open()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back and Open Drawer"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate("map") {
                            popUpTo("map") { inclusive = false }
                        }
                    }) {
                        Icon(Icons.Default.Map, contentDescription = "Go to Map")
                    }
                }
            )
        }
    ) { padding ->
        // Rest of your existing content...
        // Rest of your existing content...
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Navigation — styled like Flight Data Mgmt's ModernTabItem
            TaskCreationTabs(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )

            // Tab Content
            when (activeTab) {
                "turnpoints" -> TurnPointsTab(
                    task = currentTask,
                    onTaskUpdated = { currentTask = it }
                )
                "rules" -> RulesTab(
                    task = currentTask,
                    onTaskUpdated = { currentTask = it }
                )
                "manage" -> ManageTab(
                    task = currentTask
                )
                "xxx" -> XXXTab() // Add this new tab
            }
        }
    }
}

// ================================
// TAB NAVIGATION
// ================================

@Composable
private fun TaskCreationTabs(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TaskTab(
            id = "turnpoints",
            label = "Turn Points",
            isSelected = activeTab == "turnpoints",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
        TaskTab(
            id = "rules",
            label = "Rules",
            isSelected = activeTab == "rules",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
        TaskTab(
            id = "manage",
            label = "Manage",
            isSelected = activeTab == "manage",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
        TaskTab(
            id = "xxx",
            label = "XXX",
            isSelected = activeTab == "xxx",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TaskTab(
    id: String,
    label: String,
    isSelected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(id) },
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Add icon for consistency with flight mode tabs
            Icon(
                imageVector = when(id) {
                    "turnpoints" -> Icons.Default.Place
                    "rules" -> Icons.Default.Rule
                    "manage" -> Icons.Default.Settings
                    "xxx" -> Icons.Default.Star
                    else -> Icons.Default.Star
                },
                contentDescription = "$label tab",
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "0", // You can add counts later
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
// ================================
// TURN POINTS TAB
// ================================

@Composable
private fun TurnPointsTab(
    task: SoaringTask,
    onTaskUpdated: (SoaringTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Task Name Input
        item {
            TaskNameCard(
                taskName = task.name,
                onNameChanged = { newName ->
                    onTaskUpdated(task.copy(name = newName))
                }
            )
        }

        // Task Type Selection
        item {
            TaskTypeSelector(
                selectedType = task.type,
                onTypeSelected = { newType ->
                    onTaskUpdated(task.copy(type = newType))
                }
            )
        }

        // Quick Task Creation
        item {
            QuickTaskCreationCard(
                currentTask = task,
                onTaskUpdated = onTaskUpdated
            )
        }

        // Current Waypoints
        if (task.waypoints.isNotEmpty()) {
            item {
                CurrentWaypointsCard(task.waypoints)
            }
        }

        // Waypoint Search
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

// ================================
// XXX TAB (Placeholder)
// ================================

@Composable
private fun XXXTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "XXX Content",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Placeholder content for XXX tab")
                }
            }
        }
    }
}

// ================================
// RULES TAB
// ================================

@Composable
private fun RulesTab(
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Task Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Task Type: ${task.type.displayName}")
                    Text("Start Method: Gate")
                    Text("Finish Method: Line")
                    Text("Max Start Height: 1000ft AGL")
                }
            }
        }
    }
}

// ================================
// MANAGE TAB
// ================================

@Composable
private fun ManageTab(task: SoaringTask) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Task Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (task.name.isNotBlank()) {
                        Text("Task: ${task.name}")
                        Text("Waypoints: ${task.waypoints.size}")

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Button(
                        onClick = { /* TODO: Save task */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Task")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { /* TODO: Export to .cup */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to .CUP")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { /* TODO: Load existing task */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load Existing Task")
                    }
                }
            }
        }
    }
}