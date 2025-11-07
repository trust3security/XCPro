package com.example.xcpro.common.waypoint

import android.content.Context
import android.util.Log
import java.io.File
import org.json.JSONObject

private const val TAG = "WaypointData"

data class WaypointData(
    val name: String,
    val code: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: String,
    val style: Int,
    val runwayDirection: String?,
    val runwayLength: String?,
    val frequency: String?,
    val description: String?
) {
    fun getStyleDescription(): String = when (style) {
        1 -> "Normal"
        2 -> "Airfield Grass"
        3 -> "Outlanding"
        4 -> "Glider Site"
        5 -> "Airfield Solid"
        6 -> "Mountain Pass"
        7 -> "Mountain Top"
        8 -> "Sender"
        9 -> "VOR"
        10 -> "NDB"
        11 -> "Cool Tower"
        12 -> "Dam"
        13 -> "Tunnel"
        14 -> "Bridge"
        15 -> "Power Plant"
        16 -> "Castle"
        17 -> "Intersection"
        else -> "Unknown"
    }

    fun getTypeIcon(): String = when (style) {
        2, 4, 5 -> "✈️"
        3 -> "🛬"
        6, 7 -> "⛰️"
        9, 10 -> "📡"
        12 -> "🏞️"
        15 -> "⚡"
        16 -> "🏰"
        else -> "📍"
    }

    fun getFormattedCoordinates(): String =
        "${String.format("%.4f", latitude)}, ${String.format("%.4f", longitude)}"
}

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
        Log.d(TAG, "Saved home waypoint: ${waypoint.name}")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving home waypoint: ${e.message}")
    }
}

fun loadHomeWaypoint(context: Context): WaypointData? {
    return try {
        val file = File(context.filesDir, "home_waypoint.json")
        if (!file.exists()) {
            null
        } else {
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
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading home waypoint: ${e.message}")
        null
    }
}

private fun broadcastHomeWaypointChange(context: Context, waypointName: String?) {
    val sharedPrefs = context.getSharedPreferences("HomeWaypointPrefs", Context.MODE_PRIVATE)
    sharedPrefs.edit()
        .putString("current_home_waypoint", waypointName)
        .putLong("last_updated", System.currentTimeMillis())
        .apply()
}

data class SearchWaypoint(
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double
)

fun WaypointData.toSearchWaypoint(): SearchWaypoint = SearchWaypoint(
    id = if (code.isNotBlank()) code else name,
    title = if (code.isNotBlank()) "$name ($code)" else name,
    subtitle = "${getTypeIcon()}  ${getFormattedCoordinates()}",
    lat = latitude,
    lon = longitude
)
