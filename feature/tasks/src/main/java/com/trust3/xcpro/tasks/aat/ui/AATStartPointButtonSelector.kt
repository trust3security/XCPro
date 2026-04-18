package com.trust3.xcpro.tasks.aat.ui

import com.trust3.xcpro.tasks.core.TaskWaypoint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.tasks.aat.models.AATStartPointType

/**
 * AAT Start Point Button Selector
 * Professional FilterChip selector matching Racing UI pattern exactly
 */
@Composable
fun AATStartPointButtonSelector(
    selectedType: AATStartPointType,
    onTypeSelected: (AATStartPointType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Start Point Type",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        // Horizontal FilterChip row for AAT start point type selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            //  AAT Start Line Button
            AATStartPointTypeChip(
                type = AATStartPointType.AAT_START_LINE,
                icon = Icons.Default.LinearScale, // Line icon
                emoji = "",
                selected = selectedType == AATStartPointType.AAT_START_LINE,
                onClick = { onTypeSelected(AATStartPointType.AAT_START_LINE) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            //  AAT Start Cylinder Button
            AATStartPointTypeChip(
                type = AATStartPointType.AAT_START_CYLINDER,
                icon = Icons.Default.RadioButtonUnchecked, // Circle icon
                emoji = "",
                selected = selectedType == AATStartPointType.AAT_START_CYLINDER,
                onClick = { onTypeSelected(AATStartPointType.AAT_START_CYLINDER) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            //  AAT Start Sector Button
            AATStartPointTypeChip(
                type = AATStartPointType.AAT_START_SECTOR,
                icon = Icons.Default.ChangeHistory, // Triangle icon for sector
                emoji = "",
                selected = selectedType == AATStartPointType.AAT_START_SECTOR,
                onClick = { onTypeSelected(AATStartPointType.AAT_START_SECTOR) },
                modifier = Modifier.weight(1f)
            )
        }

        // Description card for selected type
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Description",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedType.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AATStartPointTypeChip(
    type: AATStartPointType,
    icon: ImageVector,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                // Emoji + Icon combination for better recognition
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Type name (shortened for space)
                Text(
                    text = when (type) {
                        AATStartPointType.AAT_START_LINE -> "Line"
                        AATStartPointType.AAT_START_CYLINDER -> "Cylinder"
                        AATStartPointType.AAT_START_SECTOR -> "AAT Sector"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
            }
        },
        modifier = modifier
            .heightIn(min = 72.dp), // Ensure enough height for content
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = if (selected)
                MaterialTheme.colorScheme.primary  // Dark blue for selected
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),  // Light blue for unselected
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp
        )
    )
}
