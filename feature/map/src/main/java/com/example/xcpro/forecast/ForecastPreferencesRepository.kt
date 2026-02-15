package com.example.xcpro.forecast

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val FORECAST_DATASTORE_NAME = "forecast_preferences"
private val Context.forecastDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FORECAST_DATASTORE_NAME
)
private val KEY_FORECAST_OVERLAY_ENABLED = booleanPreferencesKey("forecast_overlay_enabled")
private val KEY_FORECAST_OPACITY = floatPreferencesKey("forecast_opacity")
private val KEY_FORECAST_SELECTED_PARAMETER_ID = stringPreferencesKey("forecast_selected_parameter_id")
private val KEY_FORECAST_SELECTED_TIME_UTC_MS = longPreferencesKey("forecast_selected_time_utc_ms")
private val KEY_FORECAST_SELECTED_REGION = stringPreferencesKey("forecast_selected_region")
private val KEY_FORECAST_AUTO_TIME_ENABLED = booleanPreferencesKey("forecast_auto_time_enabled")

data class ForecastPreferences(
    val overlayEnabled: Boolean = false,
    val opacity: Float = FORECAST_OPACITY_DEFAULT,
    val selectedParameterId: ForecastParameterId = DEFAULT_FORECAST_PARAMETER_ID,
    val selectedTimeUtcMs: Long? = null,
    val selectedRegion: String = DEFAULT_FORECAST_REGION_CODE,
    val autoTimeEnabled: Boolean = FORECAST_AUTO_TIME_DEFAULT
)

@Singleton
class ForecastPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val preferencesFlow: Flow<ForecastPreferences> = context.forecastDataStore.data
        .map(::toForecastPreferences)
        .distinctUntilChanged()

    val overlayEnabledFlow: Flow<Boolean> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.overlayEnabled }
        .distinctUntilChanged()

    val opacityFlow: Flow<Float> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.opacity }
        .distinctUntilChanged()

    val selectedParameterIdFlow: Flow<ForecastParameterId> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedParameterId }
        .distinctUntilChanged()

    val selectedTimeUtcMsFlow: Flow<Long?> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedTimeUtcMs }
        .distinctUntilChanged()

    val selectedRegionFlow: Flow<String> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedRegion }
        .distinctUntilChanged()

    val autoTimeEnabledFlow: Flow<Boolean> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.autoTimeEnabled }
        .distinctUntilChanged()

    suspend fun currentPreferences(): ForecastPreferences = preferencesFlow.first()

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setOpacity(opacity: Float) {
        val clamped = clampForecastOpacity(opacity)
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_OPACITY] = clamped
        }
    }

    suspend fun setSelectedParameterId(parameterId: ForecastParameterId) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_SELECTED_PARAMETER_ID] = parameterId.value
        }
    }

    suspend fun setSelectedTimeUtcMs(timeUtcMs: Long?) {
        context.forecastDataStore.edit { preferences ->
            if (timeUtcMs == null) {
                preferences.remove(KEY_FORECAST_SELECTED_TIME_UTC_MS)
            } else {
                preferences[KEY_FORECAST_SELECTED_TIME_UTC_MS] = timeUtcMs
            }
        }
    }

    suspend fun setSelectedRegion(regionCode: String) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_SELECTED_REGION] = normalizeForecastRegionCode(regionCode)
        }
    }

    suspend fun setAutoTimeEnabled(enabled: Boolean) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_AUTO_TIME_ENABLED] = enabled
            if (enabled) {
                preferences.remove(KEY_FORECAST_SELECTED_TIME_UTC_MS)
            }
        }
    }

    private fun toForecastPreferences(preferences: Preferences): ForecastPreferences {
        val selectedParameter = ForecastParameterId(
            preferences[KEY_FORECAST_SELECTED_PARAMETER_ID]
                ?.trim()
                .orEmpty()
                .ifBlank { DEFAULT_FORECAST_PARAMETER_ID.value }
        )
        val selectedRegion = normalizeForecastRegionCode(
            preferences[KEY_FORECAST_SELECTED_REGION]
        )
        return ForecastPreferences(
            overlayEnabled = preferences[KEY_FORECAST_OVERLAY_ENABLED] ?: false,
            opacity = clampForecastOpacity(
                preferences[KEY_FORECAST_OPACITY] ?: FORECAST_OPACITY_DEFAULT
            ),
            selectedParameterId = selectedParameter,
            selectedTimeUtcMs = preferences[KEY_FORECAST_SELECTED_TIME_UTC_MS],
            selectedRegion = selectedRegion,
            autoTimeEnabled = preferences[KEY_FORECAST_AUTO_TIME_ENABLED] ?: FORECAST_AUTO_TIME_DEFAULT
        )
    }
}
