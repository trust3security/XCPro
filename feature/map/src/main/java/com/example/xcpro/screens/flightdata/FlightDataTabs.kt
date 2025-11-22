package com.example.ui1.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FlightMgmtTabs(
    activeTab: String,
    waypointCount: Int,
    airspaceCount: Int,
    screensCount: Int,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModernTabItem(
            id = "screens",
            icon = Icons.Filled.Star,
            label = "Screens",
            count = screensCount,
            isSelected = activeTab == "screens",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
        ModernTabItem(
            id = "waypoints",
            icon = Icons.Filled.Navigation,
            label = "Waypoints",
            count = waypointCount,
            isSelected = activeTab == "waypoints",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
        ModernTabItem(
            id = "airspace",
            icon = Icons.Filled.Security,
            label = "Airspace",
            count = airspaceCount,
            isSelected = activeTab == "airspace",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
        ModernTabItem(
            id = "classes",
            icon = Icons.Filled.Layers,
            label = "Classes",
            count = 0,
            isSelected = activeTab == "classes",
            onClick = onTabSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ModernTabItem(
    id: String,
    icon: ImageVector,
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick(id) },
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
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
            androidx.compose.material3.Icon(
                imageVector = icon,
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
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
