package com.example.xcpro.ogn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
private val KEY_OGN_ICON_SIZE_PX = intPreferencesKey("ogn_icon_size_px")
private val KEY_OGN_SHOW_THERMALS_ENABLED = booleanPreferencesKey("ogn_show_thermals_enabled")
private val KEY_OGN_OWN_FLARM_HEX = stringPreferencesKey("ogn_own_flarm_hex")
private val KEY_OGN_OWN_ICAO_HEX = stringPreferencesKey("ogn_own_icao_hex")

@Singleton
class OgnTrafficPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val enabledFlow: Flow<Boolean> = context.ognTrafficDataStore.data
        .map { preferences -> preferences[KEY_OGN_TRAFFIC_ENABLED] ?: false }
        .distinctUntilChanged()

    val iconSizePxFlow: Flow<Int> = context.ognTrafficDataStore.data
        .map { preferences ->
            clampOgnIconSizePx(
                preferences[KEY_OGN_ICON_SIZE_PX] ?: OGN_ICON_SIZE_DEFAULT_PX
            )
        }
        .distinctUntilChanged()

    val showThermalsEnabledFlow: Flow<Boolean> = context.ognTrafficDataStore.data
        .map { preferences -> preferences[KEY_OGN_SHOW_THERMALS_ENABLED] ?: false }
        .distinctUntilChanged()

    val ownFlarmHexFlow: Flow<String?> = context.ognTrafficDataStore.data
        .map { preferences ->
            normalizeOgnHex6OrNull(preferences[KEY_OGN_OWN_FLARM_HEX])
        }
        .distinctUntilChanged()

    val ownIcaoHexFlow: Flow<String?> = context.ognTrafficDataStore.data
        .map { preferences ->
            normalizeOgnHex6OrNull(preferences[KEY_OGN_OWN_ICAO_HEX])
        }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        context.ognTrafficDataStore.edit { preferences ->
            preferences[KEY_OGN_TRAFFIC_ENABLED] = enabled
        }
    }

    suspend fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        context.ognTrafficDataStore.edit { preferences ->
            preferences[KEY_OGN_ICON_SIZE_PX] = clamped
        }
    }

    suspend fun setShowThermalsEnabled(enabled: Boolean) {
        context.ognTrafficDataStore.edit { preferences ->
            preferences[KEY_OGN_SHOW_THERMALS_ENABLED] = enabled
        }
    }

    suspend fun setOwnFlarmHex(value: String?) {
        setNormalizedHexOrClear(
            key = KEY_OGN_OWN_FLARM_HEX,
            value = value
        )
    }

    suspend fun setOwnIcaoHex(value: String?) {
        setNormalizedHexOrClear(
            key = KEY_OGN_OWN_ICAO_HEX,
            value = value
        )
    }

    private suspend fun setNormalizedHexOrClear(
        key: Preferences.Key<String>,
        value: String?
    ) {
        val trimmed = value?.trim().orEmpty()
        val normalized = normalizeOgnHex6OrNull(trimmed)
        if (trimmed.isNotEmpty() && normalized == null) {
            // Invalid non-blank input is ignored to preserve previous valid persisted value.
            return
        }
        context.ognTrafficDataStore.edit { preferences ->
            if (normalized == null) {
                preferences.remove(key)
            } else {
                preferences[key] = normalized
            }
        }
    }
}
