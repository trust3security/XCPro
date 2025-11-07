package com.example.xcpro.common.waypoint

import android.content.Context

fun interface WaypointLoader {
    suspend fun load(context: Context): List<WaypointData>
}
