package com.example.xcpro.ogn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val OGN_TRAIL_SELECTION_DATASTORE_NAME = "ogn_trail_selection_preferences"
private val Context.ognTrailSelectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = OGN_TRAIL_SELECTION_DATASTORE_NAME
)
private val KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS = stringSetPreferencesKey(
    "ogn_trail_selected_aircraft_keys"
)

@Singleton
class OgnTrailSelectionPreferencesRepository constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(context.ognTrailSelectionDataStore)

    val selectedAircraftKeysFlow: Flow<Set<String>> = dataStore.data
        .map { preferences ->
            preferences[KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS]
                ?.mapNotNull(::normalizeOgnAircraftKeyOrNull)
                ?.toSet()
                ?: emptySet()
        }
        .distinctUntilChanged()

    suspend fun setAircraftSelected(aircraftKey: String, selected: Boolean) {
        val normalizedKey = normalizeOgnAircraftKeyOrNull(aircraftKey) ?: return
        val keyLookup = buildOgnSelectionLookup(setOf(normalizedKey))

        dataStore.edit { preferences ->
            val updated = preferences[KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS]
                ?.toMutableSet()
                ?: mutableSetOf()
            updated.removeAll { stored ->
                val normalizedStored = normalizeOgnAircraftKeyOrNull(stored) ?: return@removeAll false
                selectionLookupContainsOgnKey(
                    lookup = keyLookup,
                    candidateKey = normalizedStored
                )
            }
            if (selected) {
                updated.add(normalizedKey)
            }
            preferences[KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS] = updated
        }
    }

    suspend fun removeAircraftKeys(aircraftKeys: Set<String>) {
        if (aircraftKeys.isEmpty()) return
        val normalizedKeys = aircraftKeys.mapNotNull(::normalizeOgnAircraftKeyOrNull).toSet()
        if (normalizedKeys.isEmpty()) return
        val removalLookup = buildOgnSelectionLookup(normalizedKeys)

        dataStore.edit { preferences ->
            val updated = preferences[KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS]
                ?.toMutableSet()
                ?: mutableSetOf()
            updated.removeAll { stored ->
                val normalizedStored = normalizeOgnAircraftKeyOrNull(stored) ?: return@removeAll false
                selectionLookupContainsOgnKey(
                    lookup = removalLookup,
                    candidateKey = normalizedStored
                )
            }
            preferences[KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS] = updated
        }
    }

    suspend fun clearSelectedAircraft() {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_TRAIL_SELECTED_AIRCRAFT_KEYS] = emptySet()
        }
    }
}
