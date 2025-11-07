package com.example.ui1.screens.flightmgmt

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xcpro.common.waypoint.WaypointData
import com.google.gson.Gson

object HomeWaypointManager {

    private const val HOME_WAYPOINT_PREFS = "home_waypoint_prefs"
    private const val HOME_WAYPOINT_KEY = "home_waypoint"
    private const val HOME_WAYPOINT_BROADCAST = "com.example.xcpro.HOME_WAYPOINT_CHANGED"

    fun saveHomeWaypoint(context: Context, waypoint: WaypointData?) {
        val prefs = context.getSharedPreferences(HOME_WAYPOINT_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (waypoint != null) {
            val gson = Gson()
            val waypointJson = gson.toJson(waypoint)
            editor.putString(HOME_WAYPOINT_KEY, waypointJson)
        } else {
            editor.remove(HOME_WAYPOINT_KEY)
        }

        editor.apply()

        // Broadcast the change
        broadcastHomeWaypointChange(context, waypoint?.name)
    }

    private fun broadcastHomeWaypointChange(context: Context, waypointName: String?) {
        val intent = Intent(HOME_WAYPOINT_BROADCAST).apply {
            putExtra("waypoint_name", waypointName)
        }
        context.sendBroadcast(intent)
    }

    fun loadHomeWaypoint(context: Context): WaypointData? {
        val prefs = context.getSharedPreferences(HOME_WAYPOINT_PREFS, Context.MODE_PRIVATE)
        val waypointJson = prefs.getString(HOME_WAYPOINT_KEY, null)

        return if (waypointJson != null) {
            try {
                val gson = Gson()
                gson.fromJson(waypointJson, WaypointData::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

@Composable
fun HomeWaypointSelector(
    waypoints: List<WaypointData>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSelector by remember { mutableStateOf(false) }
    var currentHomeWaypoint by remember { mutableStateOf(HomeWaypointManager.loadHomeWaypoint(context)) }

    LaunchedEffect(Unit) {
        currentHomeWaypoint = HomeWaypointManager.loadHomeWaypoint(context)
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSelector = !showSelector },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home",
                        tint = if (currentHomeWaypoint != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Home Waypoint",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = currentHomeWaypoint?.name ?: "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Icon(
                    imageVector = if (showSelector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (showSelector) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (showSelector) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Home Waypoint",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Choose a waypoint to set as your home location for navigation reference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Clear selection option
                    HomeWaypointItem(
                        waypoint = null,
                        isSelected = currentHomeWaypoint == null,
                        onClick = {
                            HomeWaypointManager.saveHomeWaypoint(context, null)
                            currentHomeWaypoint = null
                            showSelector = false
                        }
                    )

                    if (waypoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(waypoints.take(20)) { waypoint ->
                                HomeWaypointItem(
                                    waypoint = waypoint,
                                    isSelected = currentHomeWaypoint?.name == waypoint.name,
                                    onClick = {
                                        HomeWaypointManager.saveHomeWaypoint(context, waypoint)
                                        currentHomeWaypoint = waypoint
                                        showSelector = false
                                    }
                                )
                            }
                        }

                        if (waypoints.size > 20) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Showing first 20 waypoints. Use search to find more.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No waypoints available. Load waypoint files first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWaypointItem(
    waypoint: WaypointData?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (waypoint == null) {
                                Color.Gray
                            } else if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (waypoint == null) Icons.Default.Clear else Icons.Default.Home,
                        contentDescription = null,
                        tint = if (isSelected || waypoint == null) Color.White else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (waypoint != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = waypoint.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        Text(
                            text = "${String.format("%.4f", waypoint.latitude)}, ${String.format("%.4f", waypoint.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            }
                        )
                    }
                } else {
                    Text(
                        text = "No home waypoint",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        }
                    )
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
