package com.example.xcpro.tasks

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.waypoint.SearchWaypoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "RecentWaypoints"
private const val KEY_RECENT_WAYPOINTS = "recent_waypoints"

@Singleton
class RecentWaypointsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecentWaypoints(): List<SearchWaypoint> = parseWaypoints(
        prefs.getString(KEY_RECENT_WAYPOINTS, null)
    )

    fun observeRecentWaypoints(): Flow<List<SearchWaypoint>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == KEY_RECENT_WAYPOINTS) {
                trySend(parseWaypoints(prefs.getString(KEY_RECENT_WAYPOINTS, null)))
            }
        }
        trySend(parseWaypoints(prefs.getString(KEY_RECENT_WAYPOINTS, null)))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun setRecentWaypoints(waypoints: List<SearchWaypoint>) {
        val json = JSONArray()
        waypoints.forEach { waypoint ->
            val waypointJson = JSONObject().apply {
                put("id", waypoint.id)
                put("title", waypoint.title)
                put("subtitle", waypoint.subtitle)
                put("lat", waypoint.lat)
                put("lon", waypoint.lon)
            }
            json.put(waypointJson)
        }
        prefs.edit()
            .putString(KEY_RECENT_WAYPOINTS, json.toString())
            .apply()
    }

    private fun parseWaypoints(jsonString: String?): List<SearchWaypoint> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(jsonString)
            val waypoints = mutableListOf<SearchWaypoint>()
            for (i in 0 until json.length()) {
                val waypointJson = json.getJSONObject(i)
                waypoints.add(
                    SearchWaypoint(
                        id = waypointJson.getString("id"),
                        title = waypointJson.getString("title"),
                        subtitle = waypointJson.getString("subtitle"),
                        lat = waypointJson.getDouble("lat"),
                        lon = waypointJson.getDouble("lon")
                    )
                )
            }
            waypoints
        }.getOrDefault(emptyList())
    }
}
