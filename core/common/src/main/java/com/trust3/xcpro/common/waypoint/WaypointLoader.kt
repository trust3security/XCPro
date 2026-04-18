package com.trust3.xcpro.common.waypoint

fun interface WaypointLoader {
    suspend fun load(): List<WaypointData>
}
