package com.trust3.xcpro.weather.rain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val WEATHER_OVERLAY_DATASTORE_NAME = "weather_overlay_preferences"
private val Context.weatherOverlayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = WEATHER_OVERLAY_DATASTORE_NAME
)
private val KEY_WEATHER_RAIN_ENABLED = booleanPreferencesKey("weather_rain_enabled")
private val KEY_WEATHER_RAIN_OPACITY = floatPreferencesKey("weather_rain_opacity")
// Keep legacy key name for storage compatibility with existing installs.
private val KEY_WEATHER_RAIN_ANIMATE_PAST_WINDOW =
    booleanPreferencesKey("weather_rain_animate_past_ten_minutes")
private val KEY_WEATHER_RAIN_ANIMATION_WINDOW =
    stringPreferencesKey("weather_rain_animation_window")
private val KEY_WEATHER_RAIN_ANIMATION_SPEED =
    stringPreferencesKey("weather_rain_animation_speed")
private val KEY_WEATHER_RAIN_TRANSITION_QUALITY =
    stringPreferencesKey("weather_rain_transition_quality")
private val KEY_WEATHER_RAIN_FRAME_MODE = stringPreferencesKey("weather_rain_frame_mode")
private val KEY_WEATHER_RAIN_MANUAL_FRAME_INDEX =
    intPreferencesKey("weather_rain_manual_frame_index")
private val KEY_WEATHER_RAIN_SMOOTH = booleanPreferencesKey("weather_rain_smooth")
private val KEY_WEATHER_RAIN_SNOW = booleanPreferencesKey("weather_rain_snow")

@Singleton
class WeatherOverlayPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        // Legacy OpenWeather credential store is no longer used by rain overlays.
        runCatching { context.deleteSharedPreferences(LEGACY_WEATHER_KEY_PREFS_NAME) }
    }

    val preferencesFlow: Flow<WeatherOverlayPreferences> = context.weatherOverlayDataStore.data
        .map(::toPreferences)
        .distinctUntilChanged()

    val enabledFlow: Flow<Boolean> = preferencesFlow
        .map { preferences -> preferences.enabled }
        .distinctUntilChanged()

    val opacityFlow: Flow<Float> = preferencesFlow
        .map { preferences -> preferences.opacity }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_ENABLED] = enabled
        }
    }

    suspend fun setOpacity(opacity: Float) {
        val clamped = clampWeatherRainOpacity(opacity)
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_OPACITY] = clamped
        }
    }

    suspend fun setAnimatePastWindow(enabled: Boolean) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_ANIMATE_PAST_WINDOW] = enabled
        }
    }

    suspend fun setAnimationSpeed(speed: WeatherRainAnimationSpeed) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_ANIMATION_SPEED] = speed.storageKey
        }
    }

    suspend fun setAnimationWindow(window: WeatherRainAnimationWindow) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_ANIMATION_WINDOW] = window.storageKey
        }
    }

    suspend fun setTransitionQuality(quality: WeatherRainTransitionQuality) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_TRANSITION_QUALITY] = quality.storageKey
        }
    }

    suspend fun setFrameMode(mode: WeatherRadarFrameMode) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_FRAME_MODE] = mode.storageKey
        }
    }

    suspend fun setManualFrameIndex(index: Int) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_MANUAL_FRAME_INDEX] = index.coerceAtLeast(0)
        }
    }

    suspend fun setSmoothEnabled(enabled: Boolean) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_SMOOTH] = enabled
        }
    }

    suspend fun setSnowEnabled(enabled: Boolean) {
        context.weatherOverlayDataStore.edit { preferences ->
            preferences[KEY_WEATHER_RAIN_SNOW] = enabled
        }
    }

    private fun toPreferences(preferences: Preferences): WeatherOverlayPreferences =
        WeatherOverlayPreferences(
            enabled = preferences[KEY_WEATHER_RAIN_ENABLED] ?: false,
            opacity = clampWeatherRainOpacity(
                preferences[KEY_WEATHER_RAIN_OPACITY] ?: WEATHER_RAIN_OPACITY_DEFAULT
            ),
            animatePastWindow = preferences[KEY_WEATHER_RAIN_ANIMATE_PAST_WINDOW] ?: false,
            animationWindow = WeatherRainAnimationWindow.fromStorage(
                preferences[KEY_WEATHER_RAIN_ANIMATION_WINDOW]
            ),
            animationSpeed = WeatherRainAnimationSpeed.fromStorage(
                preferences[KEY_WEATHER_RAIN_ANIMATION_SPEED]
            ),
            transitionQuality = WeatherRainTransitionQuality.fromStorage(
                preferences[KEY_WEATHER_RAIN_TRANSITION_QUALITY]
            ),
            frameMode = WeatherRadarFrameMode.fromStorage(
                preferences[KEY_WEATHER_RAIN_FRAME_MODE]
            ),
            manualFrameIndex = (preferences[KEY_WEATHER_RAIN_MANUAL_FRAME_INDEX] ?: 0)
                .coerceAtLeast(0),
            renderOptions = WeatherRadarRenderOptions(
                smooth = preferences[KEY_WEATHER_RAIN_SMOOTH] ?: true,
                snow = preferences[KEY_WEATHER_RAIN_SNOW] ?: true
            )
        )

    private companion object {
        private const val LEGACY_WEATHER_KEY_PREFS_NAME = "weather_api_key_credentials"
    }
}
