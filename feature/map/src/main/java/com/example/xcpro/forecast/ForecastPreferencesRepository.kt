package com.example.xcpro.forecast

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
private val KEY_FORECAST_SELECTED_PRIMARY_PARAMETER_ID = stringPreferencesKey("forecast_selected_primary_parameter_id")
private val KEY_FORECAST_SELECTED_PARAMETER_ID = stringPreferencesKey("forecast_selected_parameter_id")
private val KEY_FORECAST_SELECTED_TIME_UTC_MS = longPreferencesKey("forecast_selected_time_utc_ms")
private val KEY_FORECAST_SELECTED_REGION = stringPreferencesKey("forecast_selected_region")
private val KEY_FORECAST_AUTO_TIME_ENABLED = booleanPreferencesKey("forecast_auto_time_enabled")
private val KEY_FORECAST_FOLLOW_TIME_OFFSET_MINUTES = intPreferencesKey("forecast_follow_time_offset_minutes")
private val KEY_FORECAST_WIND_OVERLAY_SCALE = floatPreferencesKey("forecast_wind_overlay_scale")
private val KEY_FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED = booleanPreferencesKey("forecast_secondary_primary_overlay_enabled")
private val KEY_FORECAST_SELECTED_SECONDARY_PRIMARY_PARAMETER_ID = stringPreferencesKey("forecast_selected_secondary_primary_parameter_id")
private val KEY_FORECAST_WIND_OVERLAY_ENABLED = booleanPreferencesKey("forecast_wind_overlay_enabled")
private val KEY_FORECAST_SELECTED_WIND_PARAMETER_ID = stringPreferencesKey("forecast_selected_wind_parameter_id")
private val KEY_FORECAST_WIND_DISPLAY_MODE = stringPreferencesKey("forecast_wind_display_mode")

data class ForecastPreferences(
    val overlayEnabled: Boolean = false,
    val opacity: Float = FORECAST_OPACITY_DEFAULT,
    val windOverlayScale: Float = FORECAST_WIND_OVERLAY_SCALE_DEFAULT,
    val secondaryPrimaryOverlayEnabled: Boolean = FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED_DEFAULT,
    val windOverlayEnabled: Boolean = FORECAST_WIND_OVERLAY_ENABLED_DEFAULT,
    val windDisplayMode: ForecastWindDisplayMode = FORECAST_WIND_DISPLAY_MODE_DEFAULT,
    val selectedPrimaryParameterId: ForecastParameterId = DEFAULT_FORECAST_PARAMETER_ID,
    val selectedSecondaryPrimaryParameterId: ForecastParameterId = DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID,
    val selectedWindParameterId: ForecastParameterId = DEFAULT_FORECAST_WIND_PARAMETER_ID,
    val selectedTimeUtcMs: Long? = null,
    val selectedRegion: String = DEFAULT_FORECAST_REGION_CODE,
    val followTimeOffsetMinutes: Int = FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT,
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

    val windOverlayScaleFlow: Flow<Float> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.windOverlayScale }
        .distinctUntilChanged()

    val secondaryPrimaryOverlayEnabledFlow: Flow<Boolean> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.secondaryPrimaryOverlayEnabled }
        .distinctUntilChanged()

    val windOverlayEnabledFlow: Flow<Boolean> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.windOverlayEnabled }
        .distinctUntilChanged()

    val windDisplayModeFlow: Flow<ForecastWindDisplayMode> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.windDisplayMode }
        .distinctUntilChanged()

    val selectedPrimaryParameterIdFlow: Flow<ForecastParameterId> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedPrimaryParameterId }
        .distinctUntilChanged()

    val selectedSecondaryPrimaryParameterIdFlow: Flow<ForecastParameterId> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedSecondaryPrimaryParameterId }
        .distinctUntilChanged()

    val selectedWindParameterIdFlow: Flow<ForecastParameterId> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedWindParameterId }
        .distinctUntilChanged()

    val selectedParameterIdFlow: Flow<ForecastParameterId> = selectedPrimaryParameterIdFlow

    val selectedTimeUtcMsFlow: Flow<Long?> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedTimeUtcMs }
        .distinctUntilChanged()

    val selectedRegionFlow: Flow<String> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.selectedRegion }
        .distinctUntilChanged()

    val autoTimeEnabledFlow: Flow<Boolean> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.autoTimeEnabled }
        .distinctUntilChanged()

    val followTimeOffsetMinutesFlow: Flow<Int> = preferencesFlow
        .map { forecastPreferences -> forecastPreferences.followTimeOffsetMinutes }
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

    suspend fun setWindOverlayScale(scale: Float) {
        val clamped = clampForecastWindOverlayScale(scale)
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_WIND_OVERLAY_SCALE] = clamped
        }
    }

    suspend fun setSecondaryPrimaryOverlayEnabled(enabled: Boolean) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setWindOverlayEnabled(enabled: Boolean) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_WIND_OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setWindDisplayMode(mode: ForecastWindDisplayMode) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_WIND_DISPLAY_MODE] = mode.storageValue
        }
    }

    suspend fun setSelectedPrimaryParameterId(parameterId: ForecastParameterId) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_SELECTED_PRIMARY_PARAMETER_ID] = parameterId.value
        }
    }

    suspend fun setSelectedWindParameterId(parameterId: ForecastParameterId) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_SELECTED_WIND_PARAMETER_ID] = parameterId.value
        }
    }

    suspend fun setSelectedSecondaryPrimaryParameterId(parameterId: ForecastParameterId) {
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_SELECTED_SECONDARY_PRIMARY_PARAMETER_ID] = parameterId.value
        }
    }

    suspend fun setSelectedParameterId(parameterId: ForecastParameterId) {
        setSelectedPrimaryParameterId(parameterId)
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

    suspend fun setFollowTimeOffsetMinutes(offsetMinutes: Int) {
        val normalized = normalizeForecastFollowTimeOffsetMinutes(offsetMinutes)
        context.forecastDataStore.edit { preferences ->
            preferences[KEY_FORECAST_FOLLOW_TIME_OFFSET_MINUTES] = normalized
        }
    }

    private fun toForecastPreferences(preferences: Preferences): ForecastPreferences {
        val legacySelectedParameter = ForecastParameterId(
            preferences[KEY_FORECAST_SELECTED_PARAMETER_ID]
                ?.trim()
                .orEmpty()
                .ifBlank { DEFAULT_FORECAST_PARAMETER_ID.value }
        )
        val selectedPrimaryParameter = ForecastParameterId(
            preferences[KEY_FORECAST_SELECTED_PRIMARY_PARAMETER_ID]
                ?.trim()
                .orEmpty()
                .ifBlank {
                    if (isForecastWindParameterId(legacySelectedParameter)) {
                        DEFAULT_FORECAST_PARAMETER_ID.value
                    } else {
                        legacySelectedParameter.value
                    }
                }
        ).let { parameterId ->
            if (isForecastWindParameterId(parameterId)) {
                DEFAULT_FORECAST_PARAMETER_ID
            } else {
                parameterId
            }
        }
        val selectedSecondaryPrimaryParameter = ForecastParameterId(
            preferences[KEY_FORECAST_SELECTED_SECONDARY_PRIMARY_PARAMETER_ID]
                ?.trim()
                .orEmpty()
                .ifBlank {
                    DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID.value
                }
        ).let { parameterId ->
            if (isForecastWindParameterId(parameterId)) {
                DEFAULT_FORECAST_SECONDARY_PRIMARY_PARAMETER_ID
            } else {
                parameterId
            }
        }
        val selectedWindParameter = ForecastParameterId(
            preferences[KEY_FORECAST_SELECTED_WIND_PARAMETER_ID]
                ?.trim()
                .orEmpty()
                .ifBlank {
                    if (isForecastWindParameterId(legacySelectedParameter)) {
                        legacySelectedParameter.value
                    } else {
                        DEFAULT_FORECAST_WIND_PARAMETER_ID.value
                    }
                }
        ).let { parameterId ->
            if (isForecastWindParameterId(parameterId)) {
                parameterId
            } else {
                DEFAULT_FORECAST_WIND_PARAMETER_ID
            }
        }
        val selectedRegion = normalizeForecastRegionCode(
            preferences[KEY_FORECAST_SELECTED_REGION]
        )
        val windDisplayMode = ForecastWindDisplayMode.fromStorageValue(
            preferences[KEY_FORECAST_WIND_DISPLAY_MODE]
        )
        return ForecastPreferences(
            overlayEnabled = preferences[KEY_FORECAST_OVERLAY_ENABLED] ?: false,
            opacity = clampForecastOpacity(
                preferences[KEY_FORECAST_OPACITY] ?: FORECAST_OPACITY_DEFAULT
            ),
            windOverlayScale = clampForecastWindOverlayScale(
                preferences[KEY_FORECAST_WIND_OVERLAY_SCALE] ?: FORECAST_WIND_OVERLAY_SCALE_DEFAULT
            ),
            secondaryPrimaryOverlayEnabled =
                preferences[KEY_FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED]
                    ?: FORECAST_SECONDARY_PRIMARY_OVERLAY_ENABLED_DEFAULT,
            windOverlayEnabled = preferences[KEY_FORECAST_WIND_OVERLAY_ENABLED]
                ?: FORECAST_WIND_OVERLAY_ENABLED_DEFAULT,
            windDisplayMode = windDisplayMode,
            selectedPrimaryParameterId = selectedPrimaryParameter,
            selectedSecondaryPrimaryParameterId = selectedSecondaryPrimaryParameter,
            selectedWindParameterId = selectedWindParameter,
            selectedTimeUtcMs = preferences[KEY_FORECAST_SELECTED_TIME_UTC_MS],
            selectedRegion = selectedRegion,
            followTimeOffsetMinutes = normalizeForecastFollowTimeOffsetMinutes(
                preferences[KEY_FORECAST_FOLLOW_TIME_OFFSET_MINUTES]
                    ?: FORECAST_FOLLOW_TIME_OFFSET_MINUTES_DEFAULT
            ),
            autoTimeEnabled = preferences[KEY_FORECAST_AUTO_TIME_ENABLED] ?: FORECAST_AUTO_TIME_DEFAULT
        )
    }
}
