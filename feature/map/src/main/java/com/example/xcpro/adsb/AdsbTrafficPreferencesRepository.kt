package com.example.xcpro.adsb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val ADSB_DATASTORE_NAME = "adsb_traffic_preferences"
private val Context.adsbTrafficDataStore: DataStore<Preferences> by preferencesDataStore(
    name = ADSB_DATASTORE_NAME
)
private val KEY_ADSB_TRAFFIC_ENABLED = booleanPreferencesKey("adsb_traffic_enabled")
private val KEY_ADSB_ICON_SIZE_PX = intPreferencesKey("adsb_icon_size_px")
private val KEY_ADSB_MAX_DISTANCE_KM = intPreferencesKey("adsb_max_distance_km")
private val KEY_ADSB_VERTICAL_ABOVE_M = doublePreferencesKey("adsb_vertical_above_m")
private val KEY_ADSB_VERTICAL_BELOW_M = doublePreferencesKey("adsb_vertical_below_m")
private val KEY_ADSB_EMERGENCY_AUDIO_ENABLED = booleanPreferencesKey("adsb_emergency_audio_enabled")
private val KEY_ADSB_EMERGENCY_AUDIO_COOLDOWN_MS = longPreferencesKey("adsb_emergency_audio_cooldown_ms")

@Singleton
class AdsbTrafficPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) : AdsbEmergencyAudioSettingsPort {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(context.adsbTrafficDataStore)

    val enabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_ADSB_TRAFFIC_ENABLED] ?: false }
        .distinctUntilChanged()

    val iconSizePxFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            clampAdsbIconSizePx(
                preferences[KEY_ADSB_ICON_SIZE_PX] ?: ADSB_ICON_SIZE_DEFAULT_PX
            )
        }
        .distinctUntilChanged()

    val maxDistanceKmFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            clampAdsbMaxDistanceKm(
                preferences[KEY_ADSB_MAX_DISTANCE_KM] ?: ADSB_MAX_DISTANCE_DEFAULT_KM
            )
        }
        .distinctUntilChanged()

    val verticalAboveMetersFlow: Flow<Double> = dataStore.data
        .map { preferences ->
            clampAdsbVerticalFilterMeters(
                preferences[KEY_ADSB_VERTICAL_ABOVE_M] ?: ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
            )
        }
        .distinctUntilChanged()

    val verticalBelowMetersFlow: Flow<Double> = dataStore.data
        .map { preferences ->
            clampAdsbVerticalFilterMeters(
                preferences[KEY_ADSB_VERTICAL_BELOW_M] ?: ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
            )
        }
        .distinctUntilChanged()

    override val emergencyAudioEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_ADSB_EMERGENCY_AUDIO_ENABLED] ?: false }
        .distinctUntilChanged()

    override val emergencyAudioCooldownMsFlow: Flow<Long> = dataStore.data
        .map { preferences ->
            val stored = preferences[KEY_ADSB_EMERGENCY_AUDIO_COOLDOWN_MS]
                ?: ADSB_EMERGENCY_AUDIO_DEFAULT_COOLDOWN_MS
            stored.coerceIn(
                ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS,
                ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS
            )
        }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_TRAFFIC_ENABLED] = enabled
        }
    }

    suspend fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampAdsbIconSizePx(iconSizePx)
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_ICON_SIZE_PX] = clamped
        }
    }

    suspend fun setMaxDistanceKm(maxDistanceKm: Int) {
        val clamped = clampAdsbMaxDistanceKm(maxDistanceKm)
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_MAX_DISTANCE_KM] = clamped
        }
    }

    suspend fun setVerticalAboveMeters(aboveMeters: Double) {
        val clamped = clampAdsbVerticalFilterMeters(aboveMeters)
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_VERTICAL_ABOVE_M] = clamped
        }
    }

    suspend fun setVerticalBelowMeters(belowMeters: Double) {
        val clamped = clampAdsbVerticalFilterMeters(belowMeters)
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_VERTICAL_BELOW_M] = clamped
        }
    }

    suspend fun setEmergencyAudioEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_EMERGENCY_AUDIO_ENABLED] = enabled
        }
    }

    suspend fun setEmergencyAudioCooldownMs(cooldownMs: Long) {
        val normalized = cooldownMs.coerceIn(
            ADSB_EMERGENCY_AUDIO_MIN_COOLDOWN_MS,
            ADSB_EMERGENCY_AUDIO_MAX_COOLDOWN_MS
        )
        dataStore.edit { preferences ->
            preferences[KEY_ADSB_EMERGENCY_AUDIO_COOLDOWN_MS] = normalized
        }
    }
}
