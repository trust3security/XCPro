// Role: Persist trail settings and expose a flow for map state hydration.
// Invariants: Stored values are always valid enums; defaults apply on parse failure.
package com.example.xcpro.map.trail

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * SharedPreferences-backed storage for trail settings.
 */
@Singleton
class MapTrailPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val settingsFlow: Flow<TrailSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LENGTH ||
                key == KEY_TYPE ||
                key == KEY_WIND_DRIFT ||
                key == KEY_SCALING
            ) {
                trySend(readSettings())
            }
        }
        trySend(readSettings())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getSettings(): TrailSettings = readSettings()

    fun setTrailLength(length: TrailLength) {
        prefs.edit()
            .putString(KEY_LENGTH, length.name)
            .apply()
    }

    fun setTrailType(type: TrailType) {
        prefs.edit()
            .putString(KEY_TYPE, type.name)
            .apply()
    }

    fun setWindDriftEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_WIND_DRIFT, enabled)
            .apply()
    }

    fun setScalingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SCALING, enabled)
            .apply()
    }

    fun setSettings(settings: TrailSettings) {
        prefs.edit()
            .putString(KEY_LENGTH, settings.length.name)
            .putString(KEY_TYPE, settings.type.name)
            .putBoolean(KEY_WIND_DRIFT, settings.windDriftEnabled)
            .putBoolean(KEY_SCALING, settings.scalingEnabled)
            .apply()
    }

    fun resetToDefaults() {
        setSettings(TrailSettings())
    }

    private fun readSettings(): TrailSettings = TrailSettings(
        length = readLength(),
        type = readType(),
        windDriftEnabled = prefs.getBoolean(KEY_WIND_DRIFT, DEFAULT_WIND_DRIFT),
        scalingEnabled = prefs.getBoolean(KEY_SCALING, DEFAULT_SCALING)
    )

    private fun readLength(): TrailLength {
        val stored = prefs.getString(KEY_LENGTH, DEFAULT_LENGTH.name) ?: DEFAULT_LENGTH.name
        if (stored == "NONE") {
            return TrailLength.OFF
        }
        return runCatching { TrailLength.valueOf(stored) }.getOrDefault(DEFAULT_LENGTH)
    }

    private fun readType(): TrailType {
        val stored = prefs.getString(KEY_TYPE, DEFAULT_TYPE.name) ?: DEFAULT_TYPE.name
        return runCatching { TrailType.valueOf(stored) }.getOrDefault(DEFAULT_TYPE)
    }

    companion object {
        private const val PREFS_NAME = "map_trail_prefs"
        private const val KEY_LENGTH = "trail_length"
        private const val KEY_TYPE = "trail_type"
        private const val KEY_WIND_DRIFT = "trail_wind_drift_enabled"
        private const val KEY_SCALING = "trail_scaling_enabled"

        private val DEFAULT_LENGTH = TrailLength.LONG
        private val DEFAULT_TYPE = TrailType.VARIO_1
        private const val DEFAULT_WIND_DRIFT = true
        private const val DEFAULT_SCALING = true
    }
}
