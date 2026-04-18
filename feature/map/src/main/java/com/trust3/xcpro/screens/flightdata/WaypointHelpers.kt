package com.trust3.xcpro.screens.flightdata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.common.waypoint.WaypointData

private const val TAG = "WaypointHelpers"

/**
 * Waypoint Helpers Module
 *
 * Utility functions and helper composables for waypoint management.
 * Extracted from FlightDataWaypointsTab.kt for better modularity.
 */

// ==================== Utility Functions ====================

/**
 * Filter waypoints by search query
 */
fun filterWaypoints(waypoints: List<WaypointData>, query: String): List<WaypointData> {
    if (query.isBlank()) return waypoints

    return waypoints.filter { waypoint ->
        waypoint.name.contains(query, ignoreCase = true) ||
                waypoint.code.contains(query, ignoreCase = true) ||
                waypoint.description?.contains(query, ignoreCase = true) == true ||
                waypoint.getStyleDescription().contains(query, ignoreCase = true) ||
                waypoint.country.contains(query, ignoreCase = true)
    }
}

// Home waypoint persistence moved to HomeWaypointRepository (SSOT).

// ==================== Helper Composables ====================

/**
 * Instruction step composable for onboarding cards
 */
@Composable
fun InstructionStep(
    step: String,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            color = color.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

/**
 * Loading card composable
 */
@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
