package com.example.xcpro.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TaskPanelCategoryHost(
    uiState: TaskUiState,
    taskViewModel: TaskSheetViewModel,
    manageContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(TaskCategory.MANAGE) }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = TaskCategory.values().indexOf(selectedCategory),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp
        ) {
            TaskCategory.values().forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (category) {
                                    TaskCategory.MANAGE -> Icons.Default.Settings
                                    TaskCategory.RULES -> Icons.Default.Policy
                                    TaskCategory.FILES -> Icons.Default.Folder
                                    TaskCategory.FOUR -> Icons.Default.Star
                                    TaskCategory.FIVE -> Icons.Default.Favorite
                                },
                                contentDescription = null
                            )
                            Text(category.label)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedCategory) {
            TaskCategory.MANAGE -> manageContent()
            TaskCategory.RULES -> {
                RulesBTTab(
                    uiState = uiState,
                    onSelect = taskViewModel::onSetTaskType,
                    onUpdateAATParameters = taskViewModel::onUpdateAATParameters,
                    onUpdateRacingStartRules = taskViewModel::onUpdateRacingStartRules,
                    onUpdateRacingFinishRules = taskViewModel::onUpdateRacingFinishRules,
                    onUpdateRacingValidationRules = taskViewModel::onUpdateRacingValidationRules
                )
            }
            TaskCategory.FILES -> FilesBTTab(taskViewModel = taskViewModel)
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${selectedCategory.label} - Coming Soon",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
