package com.trust3.xcpro.flightdata

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.example.dfcards.FlightModeSelection

private const val PREFS_NAME = "FlightMgmtPrefs"
private const val KEY_LAST_ACTIVE_TAB = "last_active_tab"

@Singleton
class FlightMgmtPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastActiveTab(): String =
        prefs.getString(KEY_LAST_ACTIVE_TAB, "screens") ?: "screens"

    fun observeLastActiveTab(): Flow<String> = stringFlow(KEY_LAST_ACTIVE_TAB, "screens")

    fun setLastActiveTab(tab: String) {
        prefs.edit().putString(KEY_LAST_ACTIVE_TAB, tab).apply()
    }

    fun getLastFlightMode(profileId: String): FlightModeSelection {
        val stored = prefs.getString(profileModeKey(profileId), null)
        return stored?.let { runCatching { FlightModeSelection.valueOf(it) }.getOrNull() }
            ?: FlightModeSelection.CRUISE
    }

    fun observeLastFlightMode(profileId: String): Flow<FlightModeSelection> =
        stringFlow(profileModeKey(profileId), FlightModeSelection.CRUISE.name)
            .mapToFlightMode()

    fun setLastFlightMode(profileId: String, mode: FlightModeSelection) {
        prefs.edit().putString(profileModeKey(profileId), mode.name).apply()
    }

    fun clearProfile(profileId: String) {
        prefs.edit().remove(profileModeKey(profileId)).apply()
    }

    private fun profileModeKey(profileId: String): String =
        "profile_${profileId}_last_flight_mode"

    private fun stringFlow(key: String, defaultValue: String): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(prefs.getString(key, defaultValue) ?: defaultValue)
            }
        }
        trySend(prefs.getString(key, defaultValue) ?: defaultValue)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun Flow<String>.mapToFlightMode(): Flow<FlightModeSelection> = map { value ->
        runCatching { FlightModeSelection.valueOf(value) }.getOrElse { FlightModeSelection.CRUISE }
    }
}
