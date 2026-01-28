package com.example.xcpro.tasks.racing.ui

import com.example.xcpro.tasks.core.TaskWaypoint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.racing.models.RacingTurnPointType

/**
 * Button-based turnpoint type selector for racing tasks.
 * Replaces dropdown-based selection with horizontal buttons for better UX.
 *
 * Features:
 * - Single-tap selection (vs dropdown  tap  select)
 * - Visual icons for each turnpoint type
 * - Immediate visual feedback
 * - Better space utilization in bottom sheet
 * - Consistent with modern mobile UI patterns
 */
@Composable
fun RacingTurnPointButtonSelector(
    selectedType: RacingTurnPointType,
    onTypeChange: (RacingTurnPointType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Turn Point Type",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        // Horizontal button row for turnpoint type selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            //  Cylinder Button
            TurnPointTypeButton(
                type = RacingTurnPointType.TURN_POINT_CYLINDER,
                icon = Icons.Default.RadioButtonUnchecked, // Circle icon
                emoji = "",
                selected = selectedType == RacingTurnPointType.TURN_POINT_CYLINDER,
                onClick = { onTypeChange(RacingTurnPointType.TURN_POINT_CYLINDER) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            //  FAI Quadrant Button
            TurnPointTypeButton(
                type = RacingTurnPointType.FAI_QUADRANT,
                icon = Icons.Default.ChangeHistory, // Triangle icon for sector
                emoji = "",
                selected = selectedType == RacingTurnPointType.FAI_QUADRANT,
                onClick = { onTypeChange(RacingTurnPointType.FAI_QUADRANT) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            //  Keyhole Button
            TurnPointTypeButton(
                type = RacingTurnPointType.KEYHOLE,
                icon = Icons.Default.Lock, // Key/lock icon for keyhole
                emoji = "",
                selected = selectedType == RacingTurnPointType.KEYHOLE,
                onClick = { onTypeChange(RacingTurnPointType.KEYHOLE) },
                modifier = Modifier.weight(1f)
            )
        }

        // Description text for selected type
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
private fun TurnPointTypeButton(
    type: RacingTurnPointType,
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
                        RacingTurnPointType.TURN_POINT_CYLINDER -> "Cylinder"
                        RacingTurnPointType.FAI_QUADRANT -> "FAI Quadrant"
                        RacingTurnPointType.KEYHOLE -> "Keyhole"
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
