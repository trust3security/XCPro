package com.example.xcpro.common.waypoint

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class HomeWaypointRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(HOME_WAYPOINT_PREFS, Context.MODE_PRIVATE)
    }

    fun getHomeWaypointName(): String? {
        val current = prefs.getString(HOME_WAYPOINT_KEY, null)
        return current ?: loadHomeWaypoint(appContext)?.name
    }

    fun observeHomeWaypointName(): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HOME_WAYPOINT_KEY) {
                trySend(prefs.getString(HOME_WAYPOINT_KEY, null))
            }
        }
        trySend(prefs.getString(HOME_WAYPOINT_KEY, null) ?: loadHomeWaypoint(appContext)?.name)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private companion object {
        private const val HOME_WAYPOINT_PREFS = "HomeWaypointPrefs"
        private const val HOME_WAYPOINT_KEY = "current_home_waypoint"
    }
}
