package com.example.xcpro.ogn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val OGN_DATASTORE_NAME = "ogn_traffic_preferences"
private val Context.ognTrafficDataStore: DataStore<Preferences> by preferencesDataStore(name = OGN_DATASTORE_NAME)
private val KEY_OGN_TRAFFIC_ENABLED = booleanPreferencesKey("ogn_traffic_enabled")

@Singleton
class OgnTrafficPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val enabledFlow: Flow<Boolean> = context.ognTrafficDataStore.data
        .map { preferences -> preferences[KEY_OGN_TRAFFIC_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        context.ognTrafficDataStore.edit { preferences ->
            preferences[KEY_OGN_TRAFFIC_ENABLED] = enabled
        }
    }
}
