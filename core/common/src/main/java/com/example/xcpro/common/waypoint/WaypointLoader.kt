package com.example.xcpro.common.waypoint

fun interface WaypointLoader {
    suspend fun load(): List<WaypointData>
}
