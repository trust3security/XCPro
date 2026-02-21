package com.example.xcpro.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.core.TaskType
import java.time.Duration

@Composable
fun RulesBTTab(
    uiState: TaskUiState,
    onSelect: (TaskType) -> Unit,
    onUpdateAATParameters: (Duration, Duration) -> Unit
) {
    val selected = uiState.taskType
    val currentWaypoints = uiState.task.waypoints

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Competition Task Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        RulesTaskTypeSwitchInfoCard(
            selected = selected,
            currentWaypoints = currentWaypoints
        )

        Spacer(Modifier.height(12.dp))

        RulesTaskTypeCard(
            taskType = TaskType.RACING,
            isSelected = selected == TaskType.RACING,
            onClick = { onSelect(TaskType.RACING) },
            title = "Racing Task",
            description = "Fixed course with turnpoints. Fastest wins.",
            icon = Icons.Default.Speed,
            color = RacingTaskColor
        )

        Spacer(Modifier.height(8.dp))

        RulesTaskTypeCard(
            taskType = TaskType.AAT,
            isSelected = selected == TaskType.AAT,
            onClick = { onSelect(TaskType.AAT) },
            title = "Assigned Area Task (AAT)",
            description = "Flexible course with area targets and minimum time.",
            icon = Icons.Default.LocationOn,
            color = AatTaskColor
        )

        Spacer(Modifier.height(16.dp))

        when (selected) {
            TaskType.RACING -> RulesRacingTaskParameters()
            TaskType.AAT -> RulesAatTaskParameters(uiState.task, onUpdateAATParameters)
        }
    }
}
