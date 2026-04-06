package com.example.xcpro.thermalling

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val THERMALLING_DATASTORE_NAME = "thermalling_mode_preferences"
private val Context.thermallingModeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = THERMALLING_DATASTORE_NAME
)
private val KEY_ENABLED = booleanPreferencesKey("thermalling_enabled")
private val KEY_SWITCH_TO_THERMAL_MODE = booleanPreferencesKey("thermalling_switch_to_thermal_mode")
private val KEY_ZOOM_ONLY_FALLBACK_WHEN_THERMAL_HIDDEN =
    booleanPreferencesKey("thermalling_zoom_only_fallback_when_thermal_hidden")
private val KEY_ENTER_DELAY_SECONDS = intPreferencesKey("thermalling_enter_delay_seconds")
private val KEY_EXIT_DELAY_SECONDS = intPreferencesKey("thermalling_exit_delay_seconds")
private val KEY_APPLY_ZOOM_ON_ENTER = booleanPreferencesKey("thermalling_apply_zoom_on_enter")
private val KEY_APPLY_CONTRAST_MAP_ON_ENTER =
    booleanPreferencesKey("thermalling_apply_contrast_map_on_enter")
private val KEY_THERMAL_ZOOM_LEVEL = floatPreferencesKey("thermalling_thermal_zoom_level")
private val KEY_REMEMBER_MANUAL_THERMAL_ZOOM_IN_SESSION =
    booleanPreferencesKey("thermalling_remember_manual_zoom_in_session")
private val KEY_RESTORE_PREVIOUS_MODE_ON_EXIT =
    booleanPreferencesKey("thermalling_restore_previous_mode_on_exit")
private val KEY_RESTORE_PREVIOUS_ZOOM_ON_EXIT =
    booleanPreferencesKey("thermalling_restore_previous_zoom_on_exit")

@Singleton
class ThermallingModePreferencesRepository constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(context.thermallingModeDataStore)

    val settingsFlow: Flow<ThermallingModeSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map(::toSettings)
        .distinctUntilChanged()

    val enabledFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.enabled }
        .distinctUntilChanged()

    val switchToThermalModeFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.switchToThermalMode }
        .distinctUntilChanged()

    val zoomOnlyFallbackWhenThermalHiddenFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.zoomOnlyFallbackWhenThermalHidden }
        .distinctUntilChanged()

    val enterDelaySecondsFlow: Flow<Int> = settingsFlow
        .map { settings -> settings.enterDelaySeconds }
        .distinctUntilChanged()

    val exitDelaySecondsFlow: Flow<Int> = settingsFlow
        .map { settings -> settings.exitDelaySeconds }
        .distinctUntilChanged()

    val applyZoomOnEnterFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.applyZoomOnEnter }
        .distinctUntilChanged()

    val applyContrastMapOnEnterFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.applyContrastMapOnEnter }
        .distinctUntilChanged()

    val thermalZoomLevelFlow: Flow<Float> = settingsFlow
        .map { settings -> settings.thermalZoomLevel }
        .distinctUntilChanged()

    val rememberManualThermalZoomInSessionFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.rememberManualThermalZoomInSession }
        .distinctUntilChanged()

    val restorePreviousModeOnExitFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.restorePreviousModeOnExit }
        .distinctUntilChanged()

    val restorePreviousZoomOnExitFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.restorePreviousZoomOnExit }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = enabled
        }
    }

    suspend fun setSwitchToThermalMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SWITCH_TO_THERMAL_MODE] = enabled
        }
    }

    suspend fun setZoomOnlyFallbackWhenThermalHidden(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ZOOM_ONLY_FALLBACK_WHEN_THERMAL_HIDDEN] = enabled
        }
    }

    suspend fun setEnterDelaySeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_ENTER_DELAY_SECONDS] = clampThermallingDelaySeconds(seconds)
        }
    }

    suspend fun setExitDelaySeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_EXIT_DELAY_SECONDS] = clampThermallingDelaySeconds(seconds)
        }
    }

    suspend fun setApplyZoomOnEnter(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_APPLY_ZOOM_ON_ENTER] = enabled
        }
    }

    suspend fun setApplyContrastMapOnEnter(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_APPLY_CONTRAST_MAP_ON_ENTER] = enabled
        }
    }

    suspend fun setThermalZoomLevel(zoomLevel: Float) {
        dataStore.edit { preferences ->
            preferences[KEY_THERMAL_ZOOM_LEVEL] = clampThermallingZoomLevel(zoomLevel)
        }
    }

    suspend fun setRememberManualThermalZoomInSession(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_REMEMBER_MANUAL_THERMAL_ZOOM_IN_SESSION] = enabled
        }
    }

    suspend fun setRestorePreviousModeOnExit(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_RESTORE_PREVIOUS_MODE_ON_EXIT] = enabled
        }
    }

    suspend fun setRestorePreviousZoomOnExit(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_RESTORE_PREVIOUS_ZOOM_ON_EXIT] = enabled
        }
    }

    private fun toSettings(preferences: Preferences): ThermallingModeSettings {
        return ThermallingModeSettings(
            enabled = preferences[KEY_ENABLED] ?: THERMALLING_ENABLED_DEFAULT,
            switchToThermalMode = preferences[KEY_SWITCH_TO_THERMAL_MODE]
                ?: THERMALLING_SWITCH_TO_THERMAL_MODE_DEFAULT,
            zoomOnlyFallbackWhenThermalHidden = preferences[KEY_ZOOM_ONLY_FALLBACK_WHEN_THERMAL_HIDDEN]
                ?: THERMALLING_ZOOM_ONLY_FALLBACK_WHEN_THERMAL_HIDDEN_DEFAULT,
            enterDelaySeconds = clampThermallingDelaySeconds(
                preferences[KEY_ENTER_DELAY_SECONDS] ?: THERMALLING_ENTER_DELAY_DEFAULT_SECONDS
            ),
            exitDelaySeconds = clampThermallingDelaySeconds(
                preferences[KEY_EXIT_DELAY_SECONDS] ?: THERMALLING_EXIT_DELAY_DEFAULT_SECONDS
            ),
            applyZoomOnEnter = preferences[KEY_APPLY_ZOOM_ON_ENTER]
                ?: THERMALLING_APPLY_ZOOM_ON_ENTER_DEFAULT,
            applyContrastMapOnEnter = preferences[KEY_APPLY_CONTRAST_MAP_ON_ENTER]
                ?: THERMALLING_APPLY_CONTRAST_MAP_ON_ENTER_DEFAULT,
            thermalZoomLevel = clampThermallingZoomLevel(
                preferences[KEY_THERMAL_ZOOM_LEVEL] ?: THERMALLING_ZOOM_LEVEL_DEFAULT
            ),
            rememberManualThermalZoomInSession =
                preferences[KEY_REMEMBER_MANUAL_THERMAL_ZOOM_IN_SESSION]
                    ?: THERMALLING_REMEMBER_MANUAL_ZOOM_IN_SESSION_DEFAULT,
            restorePreviousModeOnExit = preferences[KEY_RESTORE_PREVIOUS_MODE_ON_EXIT]
                ?: THERMALLING_RESTORE_PREVIOUS_MODE_ON_EXIT_DEFAULT,
            restorePreviousZoomOnExit = preferences[KEY_RESTORE_PREVIOUS_ZOOM_ON_EXIT]
                ?: THERMALLING_RESTORE_PREVIOUS_ZOOM_ON_EXIT_DEFAULT
        )
    }
}
