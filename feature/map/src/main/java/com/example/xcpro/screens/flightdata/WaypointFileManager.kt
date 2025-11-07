package com.example.ui1.screens.flightmgmt

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.xcpro.common.waypoint.WaypointData

private const val TAG = "WaypointFileManager"

object WaypointFileManager {

    fun getAllWaypoints(
        context: Context,
        uris: List<Uri>,
        checkedStates: Map<String, Boolean>
    ): List<WaypointData> {
        val allWaypoints = mutableListOf<WaypointData>()

        try {
            uris.forEachIndexed { index, uri ->
                val isChecked = checkedStates["file_$index"] ?: false
                if (isChecked) {
                    val waypoints = WaypointParser.parseWaypointsFromUri(context, uri)
                    allWaypoints.addAll(waypoints)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading waypoints from URIs", e)
        }

        return allWaypoints
    }

    fun filterWaypoints(waypoints: List<WaypointData>, query: String): List<WaypointData> {
        if (query.isBlank()) return waypoints

        val searchQuery = query.lowercase().trim()

        return waypoints.filter { waypoint ->
            waypoint.name.lowercase().contains(searchQuery) ||
            waypoint.code.lowercase().contains(searchQuery) ||
            waypoint.country.lowercase().contains(searchQuery) ||
            waypoint.description?.lowercase()?.contains(searchQuery) == true
        }
    }

    fun searchWaypoints(
        context: Context,
        uris: List<Uri>,
        checkedStates: Map<String, Boolean>,
        query: String
    ): List<WaypointData> {
        val allWaypoints = getAllWaypoints(context, uris, checkedStates)
        return filterWaypoints(allWaypoints, query)
    }

    fun getFileWaypointCounts(context: Context, uris: List<Uri>): List<Int> {
        return uris.map { uri ->
            try {
                WaypointParser.getWaypointCount(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting waypoint count for URI: $uri", e)
                0
            }
        }
    }

    fun getTotalWaypointCount(
        context: Context,
        uris: List<Uri>,
        checkedStates: Map<String, Boolean>
    ): Int {
        return uris.mapIndexed { index, uri ->
            val isChecked = checkedStates["file_$index"] ?: false
            if (isChecked) {
                try {
                    WaypointParser.getWaypointCount(context, uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting waypoint count for checked file at index $index", e)
                    0
                }
            } else {
                0
            }
        }.sum()
    }
}
