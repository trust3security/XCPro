package com.example.xcpro.livefollow.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun FriendsFlyingPilotRow(
    pilot: FriendsFlyingPilotRowUiModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (pilot.isStale && !isSelected) 0.82f else 1f)
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        border = if (isSelected) {
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            )
        } else {
            null
        }
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            headlineContent = {
                Text(
                    text = pilot.displayLabel,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendsFlyingStatusBadge(
                            label = pilot.statusLabel,
                            isStale = pilot.isStale
                        )
                        if (isSelected) {
                            FriendsFlyingSelectionBadge()
                        }
                        pilot.altitudeLabel?.let { altitudeLabel ->
                            Text(
                                text = altitudeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    buildPilotSecondaryText(pilot)?.let { secondaryText ->
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pilot.isStale) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open live watch",
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        )
    }
}

@Composable
private fun FriendsFlyingStatusBadge(
    label: String,
    isStale: Boolean
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = if (isStale) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isStale) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FriendsFlyingSelectionBadge() {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondary
    ) {
        Text(
            text = "Watching",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun buildPilotSecondaryText(
    pilot: FriendsFlyingPilotRowUiModel
): String? {
    return listOfNotNull(
        pilot.recencyLabel,
        pilot.detailLabel
    ).takeIf { it.isNotEmpty() }?.joinToString(separator = " / ")
}
