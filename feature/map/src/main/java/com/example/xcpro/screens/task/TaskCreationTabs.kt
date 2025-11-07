package com.example.xcpro

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class TaskCreationTab(
    val label: String,
    val icon: ImageVector
) {
    Turnpoints("Turn Points", Icons.Filled.Place),
    Rules("Rules", Icons.AutoMirrored.Filled.Rule),
    Manage("Manage", Icons.Filled.Settings),
    Placeholder("XXX", Icons.Filled.Star)
}

@Composable
internal fun TaskCreationTabs(
    activeTab: TaskCreationTab,
    onTabSelected: (TaskCreationTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TaskCreationTab.values().forEach { tab ->
            TaskTab(
                tab = tab,
                isSelected = tab == activeTab,
                onClick = onTabSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TaskTab(
    tab: TaskCreationTab,
    isSelected: Boolean,
    onClick: (TaskCreationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(tab) },
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = "${tab.label} tab",
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
