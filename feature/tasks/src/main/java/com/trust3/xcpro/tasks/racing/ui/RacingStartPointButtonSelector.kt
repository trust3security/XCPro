package com.trust3.xcpro.tasks.racing.ui

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
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType

/**
 * Button-based start point type selector for racing tasks.
 * Replaces dropdown-based selection with horizontal buttons for better UX.
 *
 * Features:
 * - Single-tap selection (vs dropdown  tap  select)
 * - Visual icons for each start point type
 * - Immediate visual feedback
 * - Better space utilization in bottom sheet
 * - Consistent with modern mobile UI patterns
 */
@Composable
fun RacingStartPointButtonSelector(
    selectedType: RacingStartPointType,
    onTypeChange: (RacingStartPointType) -> Unit,
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

        // Horizontal button row for start point type selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            //  Line Button
            StartPointTypeButton(
                type = RacingStartPointType.START_LINE,
                icon = Icons.Default.LinearScale, // Line icon
                emoji = "",
                selected = selectedType == RacingStartPointType.START_LINE,
                onClick = { onTypeChange(RacingStartPointType.START_LINE) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            //  Cylinder Button
            StartPointTypeButton(
                type = RacingStartPointType.START_CYLINDER,
                icon = Icons.Default.RadioButtonUnchecked, // Circle icon
                emoji = "",
                selected = selectedType == RacingStartPointType.START_CYLINDER,
                onClick = { onTypeChange(RacingStartPointType.START_CYLINDER) },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            //  FAI Sector Button
            StartPointTypeButton(
                type = RacingStartPointType.FAI_START_SECTOR,
                icon = Icons.Default.ChangeHistory, // Triangle icon for sector
                emoji = "",
                selected = selectedType == RacingStartPointType.FAI_START_SECTOR,
                onClick = { onTypeChange(RacingStartPointType.FAI_START_SECTOR) },
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
private fun StartPointTypeButton(
    type: RacingStartPointType,
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
                        RacingStartPointType.START_LINE -> "Line"
                        RacingStartPointType.START_CYLINDER -> "Cylinder"
                        RacingStartPointType.FAI_START_SECTOR -> "FAI Sector"
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
