package com.example.xcpro.screens.flightdata

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.WaypointParser
import com.example.ui1.screens.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val TAG = "WaypointHelpers"

/**
 * Waypoint Helpers Module
 *
 * Utility functions and helper composables for waypoint management.
 * Extracted from FlightDataWaypointsTab.kt for better modularity.
 */

// ==================== Utility Functions ====================

/**
 * Get waypoint count from a .cup file
 */
suspend fun getWaypointCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    try {
        WaypointParser.parseWaypointFile(context, uri).size
    } catch (e: Exception) {
        Log.e(TAG, "Error getting waypoint count: ${e.message}")
        0
    }
}

/**
 * Get all waypoints from enabled files
 */
suspend fun getAllWaypoints(
    context: Context,
    uris: List<Uri>,
    checkedStates: Map<String, Boolean>
): List<WaypointData> = withContext(Dispatchers.IO) {
    uris.flatMap { uri ->
        try {
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@flatMap emptyList()
            if (checkedStates[fileName] == true) {
                WaypointParser.parseWaypointFile(context, uri)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading waypoint file: ${e.message}")
            emptyList()
        }
    }
}

suspend fun buildWaypointFileItems(
    context: Context,
    files: List<Uri>,
    checkedStates: Map<String, Boolean>
): List<FileItem> = withContext(Dispatchers.IO) {
    files.map { uri ->
        val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
        val enabled = checkedStates[fileName] ?: false
        val count = getWaypointCount(context, uri)
        FileItem(
            name = fileName,
            enabled = enabled,
            count = count,
            status = if (enabled) "Loaded" else "Disabled",
            uri = uri
        )
    }
}

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

/**
 * Save home waypoint to persistent storage
 */
fun saveHomeWaypoint(context: Context, waypoint: WaypointData?) {
    try {
        val file = File(context.filesDir, "home_waypoint.json")
        if (waypoint == null) {
            file.delete()
            broadcastHomeWaypointChange(context, null)
            return
        }

        val json = JSONObject().apply {
            put("name", waypoint.name)
            put("code", waypoint.code)
            put("country", waypoint.country)
            put("latitude", waypoint.latitude)
            put("longitude", waypoint.longitude)
            put("elevation", waypoint.elevation)
            put("style", waypoint.style)
            put("runwayDirection", waypoint.runwayDirection)
            put("runwayLength", waypoint.runwayLength)
            put("frequency", waypoint.frequency)
            put("description", waypoint.description)
        }
        file.writeText(json.toString(2))

        broadcastHomeWaypointChange(context, waypoint.name)
        Log.d(TAG, "💾 Saved home waypoint: ${waypoint.name}")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving home waypoint: ${e.message}")
    }
}

/**
 * Load home waypoint from persistent storage
 */
fun loadHomeWaypoint(context: Context): WaypointData? {
    return try {
        val file = File(context.filesDir, "home_waypoint.json")
        if (!file.exists()) return null

        val json = JSONObject(file.readText())
        WaypointData(
            name = json.getString("name"),
            code = json.optString("code", ""),
            country = json.optString("country", ""),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            elevation = json.optString("elevation", ""),
            style = json.optInt("style", 1),
            runwayDirection = json.optString("runwayDirection").takeIf { it.isNotBlank() },
            runwayLength = json.optString("runwayLength").takeIf { it.isNotBlank() },
            frequency = json.optString("frequency").takeIf { it.isNotBlank() },
            description = json.optString("description").takeIf { it.isNotBlank() }
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error loading home waypoint: ${e.message}")
        null
    }
}

/**
 * Broadcast home waypoint change to shared preferences
 */
private fun broadcastHomeWaypointChange(context: Context, waypointName: String?) {
    val sharedPrefs = context.getSharedPreferences("HomeWaypointPrefs", Context.MODE_PRIVATE)
    sharedPrefs.edit()
        .putString("current_home_waypoint", waypointName)
        .putLong("last_updated", System.currentTimeMillis())
        .apply()
}

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
