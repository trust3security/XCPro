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
private val KEY_OGN_RECEIVE_RADIUS_KM = intPreferencesKey("ogn_receive_radius_km")
private val KEY_OGN_AUTO_RECEIVE_RADIUS_ENABLED =
    booleanPreferencesKey("ogn_auto_receive_radius_enabled")
private val KEY_OGN_DISPLAY_UPDATE_MODE = stringPreferencesKey("ogn_display_update_mode")
private val KEY_OGN_SHOW_SCIA_ENABLED = booleanPreferencesKey("ogn_show_scia_enabled")
private val KEY_OGN_SHOW_THERMALS_ENABLED = booleanPreferencesKey("ogn_show_thermals_enabled")
private val KEY_OGN_THERMAL_RETENTION_HOURS = intPreferencesKey("ogn_thermal_retention_hours")
private val KEY_OGN_HOTSPOTS_DISPLAY_PERCENT = intPreferencesKey("ogn_hotspots_display_percent")
private val KEY_OGN_TARGET_ENABLED = booleanPreferencesKey("ogn_target_enabled")
private val KEY_OGN_TARGET_AIRCRAFT_KEY = stringPreferencesKey("ogn_target_aircraft_key")
private val KEY_OGN_OWN_FLARM_HEX = stringPreferencesKey("ogn_own_flarm_hex")
private val KEY_OGN_OWN_ICAO_HEX = stringPreferencesKey("ogn_own_icao_hex")
private val KEY_OGN_CLIENT_CALLSIGN = stringPreferencesKey("ogn_client_callsign")

@Singleton
class OgnTrafficPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(context.ognTrafficDataStore)

    val enabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_OGN_TRAFFIC_ENABLED] ?: false }
        .distinctUntilChanged()

    val iconSizePxFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            clampOgnIconSizePx(
                preferences[KEY_OGN_ICON_SIZE_PX] ?: OGN_ICON_SIZE_DEFAULT_PX
            )
        }
        .distinctUntilChanged()

    val receiveRadiusKmFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            clampOgnReceiveRadiusKm(
                preferences[KEY_OGN_RECEIVE_RADIUS_KM] ?: OGN_RECEIVE_RADIUS_DEFAULT_KM
            )
        }
        .distinctUntilChanged()

    val autoReceiveRadiusEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_OGN_AUTO_RECEIVE_RADIUS_ENABLED] ?: false }
        .distinctUntilChanged()

    val displayUpdateModeFlow: Flow<OgnDisplayUpdateMode> = dataStore.data
        .map { preferences ->
            OgnDisplayUpdateMode.fromStorage(preferences[KEY_OGN_DISPLAY_UPDATE_MODE])
        }
        .distinctUntilChanged()

    val showSciaEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_OGN_SHOW_SCIA_ENABLED] ?: false }
        .distinctUntilChanged()

    val showThermalsEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_OGN_SHOW_THERMALS_ENABLED] ?: false }
        .distinctUntilChanged()

    val thermalRetentionHoursFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            clampOgnThermalRetentionHours(
                preferences[KEY_OGN_THERMAL_RETENTION_HOURS] ?: OGN_THERMAL_RETENTION_DEFAULT_HOURS
            )
        }
        .distinctUntilChanged()

    val hotspotsDisplayPercentFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            clampOgnHotspotsDisplayPercent(
                preferences[KEY_OGN_HOTSPOTS_DISPLAY_PERCENT] ?: OGN_HOTSPOTS_DISPLAY_PERCENT_DEFAULT
            )
        }
        .distinctUntilChanged()

    val targetEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_OGN_TARGET_ENABLED] ?: false }
        .distinctUntilChanged()

    val targetAircraftKeyFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            normalizeOgnAircraftKeyOrNull(preferences[KEY_OGN_TARGET_AIRCRAFT_KEY])
        }
        .distinctUntilChanged()

    val ownFlarmHexFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            normalizeOgnHex6OrNull(preferences[KEY_OGN_OWN_FLARM_HEX])
        }
        .distinctUntilChanged()

    val ownIcaoHexFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            normalizeOgnHex6OrNull(preferences[KEY_OGN_OWN_ICAO_HEX])
        }
        .distinctUntilChanged()

    val clientCallsignFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            normalizeOgnClientCallsignOrNull(preferences[KEY_OGN_CLIENT_CALLSIGN])
        }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_TRAFFIC_ENABLED] = enabled
        }
    }

    suspend fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnIconSizePx(iconSizePx)
        dataStore.edit { preferences ->
            preferences[KEY_OGN_ICON_SIZE_PX] = clamped
        }
    }

    suspend fun setReceiveRadiusKm(radiusKm: Int) {
        val clamped = clampOgnReceiveRadiusKm(radiusKm)
        dataStore.edit { preferences ->
            preferences[KEY_OGN_RECEIVE_RADIUS_KM] = clamped
        }
    }

    suspend fun setAutoReceiveRadiusEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_AUTO_RECEIVE_RADIUS_ENABLED] = enabled
        }
    }

    suspend fun setDisplayUpdateMode(mode: OgnDisplayUpdateMode) {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_DISPLAY_UPDATE_MODE] = mode.storageValue
        }
    }

    suspend fun setShowSciaEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_SHOW_SCIA_ENABLED] = enabled
        }
    }

    suspend fun setOverlayAndSciaEnabled(
        overlayEnabled: Boolean,
        showSciaEnabled: Boolean
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_TRAFFIC_ENABLED] = overlayEnabled
            preferences[KEY_OGN_SHOW_SCIA_ENABLED] = showSciaEnabled
        }
    }

    suspend fun setShowThermalsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_SHOW_THERMALS_ENABLED] = enabled
        }
    }

    suspend fun setThermalRetentionHours(hours: Int) {
        val clamped = clampOgnThermalRetentionHours(hours)
        dataStore.edit { preferences ->
            preferences[KEY_OGN_THERMAL_RETENTION_HOURS] = clamped
        }
    }

    suspend fun setHotspotsDisplayPercent(percent: Int) {
        val clamped = clampOgnHotspotsDisplayPercent(percent)
        dataStore.edit { preferences ->
            preferences[KEY_OGN_HOTSPOTS_DISPLAY_PERCENT] = clamped
        }
    }

    suspend fun setTargetSelection(enabled: Boolean, aircraftKey: String?) {
        val normalizedAircraftKey = normalizeOgnAircraftKeyOrNull(aircraftKey)
        if (enabled && normalizedAircraftKey == null) {
            // Preserve current state if the caller requests enabling without a valid key.
            return
        }
        dataStore.edit { preferences ->
            if (!enabled || normalizedAircraftKey == null) {
                preferences[KEY_OGN_TARGET_ENABLED] = false
                preferences.remove(KEY_OGN_TARGET_AIRCRAFT_KEY)
            } else {
                preferences[KEY_OGN_TARGET_ENABLED] = true
                preferences[KEY_OGN_TARGET_AIRCRAFT_KEY] = normalizedAircraftKey
            }
        }
    }

    suspend fun clearTargetSelection() {
        dataStore.edit { preferences ->
            preferences[KEY_OGN_TARGET_ENABLED] = false
            preferences.remove(KEY_OGN_TARGET_AIRCRAFT_KEY)
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

    suspend fun setClientCallsign(value: String) {
        val normalized = normalizeOgnClientCallsignOrNull(value) ?: return
        dataStore.edit { preferences ->
            preferences[KEY_OGN_CLIENT_CALLSIGN] = normalized
        }
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
        dataStore.edit { preferences ->
            if (normalized == null) {
                preferences.remove(key)
            } else {
                preferences[key] = normalized
            }
        }
    }
}
