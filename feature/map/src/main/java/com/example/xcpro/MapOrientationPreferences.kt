package com.example.xcpro

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.common.units.UnitsConverter
import kotlin.math.abs

class MapOrientationPreferences(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "map_orientation_prefs"
        private const val KEY_ORIENTATION_MODE = "orientation_mode"
        private const val KEY_CRUISE_ORIENTATION = "orientation_mode_cruise"
        private const val KEY_CIRCLING_ORIENTATION = "orientation_mode_circling"
        private const val KEY_AUTO_RESET_ENABLED = "auto_reset_enabled"
        private const val KEY_AUTO_RESET_TIMEOUT = "auto_reset_timeout_seconds"
        private const val KEY_MIN_SPEED_THRESHOLD = "min_speed_threshold_kt"
        private const val KEY_MIN_SPEED_IS_MS = "min_speed_threshold_is_ms"
        private const val KEY_GLIDER_SCREEN_PERCENT = "glider_screen_percent"
        private const val KEY_BEARING_SMOOTHING = "bearing_smoothing_enabled"
        private const val KEY_MAP_SHIFT_BIAS_MODE = "map_shift_bias_mode"
        private const val KEY_MAP_SHIFT_BIAS_STRENGTH = "map_shift_bias_strength"

        // Default values
        private const val DEFAULT_ORIENTATION_MODE = "TRACK_UP"
        private const val DEFAULT_AUTO_RESET_ENABLED = true
        private const val DEFAULT_AUTO_RESET_TIMEOUT = 10 // seconds
        private const val LEGACY_MIN_SPEED_THRESHOLD_KT = 2.0 // Old default (pre-2026-01-09)
        private val LEGACY_MIN_SPEED_THRESHOLD_MS =
            UnitsConverter.knotsToMs(LEGACY_MIN_SPEED_THRESHOLD_KT)
        private const val DEFAULT_MIN_SPEED_THRESHOLD_MS = 2.0 // Default threshold (2 m/s)
        private const val DEFAULT_GLIDER_SCREEN_PERCENT = 35 // Approx current 65% from top
        private const val DEFAULT_BEARING_SMOOTHING = true
        private const val DEFAULT_MAP_SHIFT_BIAS_MODE = "NONE"
        private const val DEFAULT_MAP_SHIFT_BIAS_STRENGTH = 1.0
    }

    init {
        migrateLegacyOrientationMode()
        migrateRemovedWindUpMode()
        migrateMinSpeedThresholdToMeters()
        migrateMinSpeedThresholdDefault()
    }

    private fun migrateLegacyOrientationMode() {
        val hasCruise = preferences.contains(KEY_CRUISE_ORIENTATION)
        val hasCircling = preferences.contains(KEY_CIRCLING_ORIENTATION)
        if (hasCruise && hasCircling) {
            return
        }

        val legacyMode = preferences.getString(KEY_ORIENTATION_MODE, DEFAULT_ORIENTATION_MODE)
        val resolved = try {
            MapOrientationMode.valueOf(legacyMode ?: DEFAULT_ORIENTATION_MODE)
        } catch (_: IllegalArgumentException) {
            MapOrientationMode.TRACK_UP
        }

        preferences.edit()
            .putString(KEY_CRUISE_ORIENTATION, resolved.name)
            .putString(KEY_CIRCLING_ORIENTATION, resolved.name)
            .remove(KEY_ORIENTATION_MODE)
            .apply()
    }

    private fun readMode(key: String): MapOrientationMode {
        val stored = preferences.getString(key, DEFAULT_ORIENTATION_MODE)
        return try {
            MapOrientationMode.valueOf(stored ?: DEFAULT_ORIENTATION_MODE)
        } catch (_: IllegalArgumentException) {
            MapOrientationMode.TRACK_UP
        }
    }

    private fun writeMode(key: String, mode: MapOrientationMode) {
        preferences.edit()
            .putString(key, mode.name)
            .apply()
    }

    fun getOrientationMode(): MapOrientationMode = getCruiseOrientationMode()

    fun setOrientationMode(mode: MapOrientationMode) {
        setCruiseOrientationMode(mode)
        setCirclingOrientationMode(mode)
    }

    fun getCruiseOrientationMode(): MapOrientationMode = readMode(KEY_CRUISE_ORIENTATION)

    fun setCruiseOrientationMode(mode: MapOrientationMode) = writeMode(KEY_CRUISE_ORIENTATION, mode)

    fun getCirclingOrientationMode(): MapOrientationMode = readMode(KEY_CIRCLING_ORIENTATION)

    fun setCirclingOrientationMode(mode: MapOrientationMode) = writeMode(KEY_CIRCLING_ORIENTATION, mode)

    fun isAutoResetEnabled(): Boolean {
        return preferences.getBoolean(KEY_AUTO_RESET_ENABLED, DEFAULT_AUTO_RESET_ENABLED)
    }

    fun setAutoResetEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_AUTO_RESET_ENABLED, enabled)
            .apply()
    }

    fun getAutoResetTimeoutSeconds(): Int {
        return preferences.getInt(KEY_AUTO_RESET_TIMEOUT, DEFAULT_AUTO_RESET_TIMEOUT)
    }

    fun setAutoResetTimeoutSeconds(seconds: Int) {
        val clampedSeconds = seconds.coerceIn(5, 60) // Between 5 and 60 seconds
        preferences.edit()
            .putInt(KEY_AUTO_RESET_TIMEOUT, clampedSeconds)
            .apply()
    }

    fun getMinSpeedThreshold(): Double {
        return preferences.getFloat(
            KEY_MIN_SPEED_THRESHOLD,
            DEFAULT_MIN_SPEED_THRESHOLD_MS.toFloat()
        ).toDouble()
    }

    fun setMinSpeedThreshold(speedKnots: Double) {
        val clampedSpeed = speedKnots.coerceIn(0.0, 20.0) // Between 0 and 20 knots
        val speedMs = UnitsConverter.knotsToMs(clampedSpeed)
        preferences.edit()
            .putFloat(KEY_MIN_SPEED_THRESHOLD, speedMs.toFloat())
            .putBoolean(KEY_MIN_SPEED_IS_MS, true)
            .apply()
    }

    fun isBearingSmoothingEnabled(): Boolean {
        return preferences.getBoolean(KEY_BEARING_SMOOTHING, DEFAULT_BEARING_SMOOTHING)
    }

    fun setBearingSmoothingEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_BEARING_SMOOTHING, enabled)
            .apply()
    }

    fun resetToDefaults() {
        preferences.edit()
            .putString(KEY_CRUISE_ORIENTATION, DEFAULT_ORIENTATION_MODE)
            .putString(KEY_CIRCLING_ORIENTATION, DEFAULT_ORIENTATION_MODE)
            .putBoolean(KEY_AUTO_RESET_ENABLED, DEFAULT_AUTO_RESET_ENABLED)
            .putInt(KEY_AUTO_RESET_TIMEOUT, DEFAULT_AUTO_RESET_TIMEOUT)
            .putFloat(KEY_MIN_SPEED_THRESHOLD, DEFAULT_MIN_SPEED_THRESHOLD_MS.toFloat())
            .putBoolean(KEY_MIN_SPEED_IS_MS, true)
            .putInt(KEY_GLIDER_SCREEN_PERCENT, DEFAULT_GLIDER_SCREEN_PERCENT)
            .putBoolean(KEY_BEARING_SMOOTHING, DEFAULT_BEARING_SMOOTHING)
            .putString(KEY_MAP_SHIFT_BIAS_MODE, DEFAULT_MAP_SHIFT_BIAS_MODE)
            .putFloat(KEY_MAP_SHIFT_BIAS_STRENGTH, DEFAULT_MAP_SHIFT_BIAS_STRENGTH.toFloat())
            .apply()
    }

    fun getAllSettings(): Map<String, Any> {
        return mapOf(
            "cruiseOrientation" to getCruiseOrientationMode().name,
            "circlingOrientation" to getCirclingOrientationMode().name,
            "autoResetEnabled" to isAutoResetEnabled(),
            "autoResetTimeoutSeconds" to getAutoResetTimeoutSeconds(),
            "minSpeedThreshold" to getMinSpeedThreshold(),
            "gliderScreenPercent" to getGliderScreenPercent(),
            "bearingSmoothingEnabled" to isBearingSmoothingEnabled(),
            "mapShiftBiasMode" to getMapShiftBiasMode().name,
            "mapShiftBiasStrength" to getMapShiftBiasStrength()
        )
    }

    fun exportSettings(): String {
        val settings = getAllSettings()
        return settings.entries.joinToString(separator = "\n") { "${it.key}: ${it.value}" }
    }

    private fun migrateMinSpeedThresholdToMeters() {
        if (preferences.getBoolean(KEY_MIN_SPEED_IS_MS, false)) {
            return
        }

        val legacyKnots = preferences
            .getFloat(KEY_MIN_SPEED_THRESHOLD, LEGACY_MIN_SPEED_THRESHOLD_KT.toFloat())
            .toDouble()

        val convertedMs = UnitsConverter.knotsToMs(legacyKnots).toFloat()
        preferences.edit()
            .putFloat(KEY_MIN_SPEED_THRESHOLD, convertedMs)
            .putBoolean(KEY_MIN_SPEED_IS_MS, true)
            .apply()
    }

    private fun migrateRemovedWindUpMode() {
        migrateWindUpValue(KEY_CRUISE_ORIENTATION)
        migrateWindUpValue(KEY_CIRCLING_ORIENTATION)
    }

    private fun migrateWindUpValue(key: String): Boolean {
        if (!preferences.contains(key)) {
            return false
        }
        val stored = preferences.getString(key, DEFAULT_ORIENTATION_MODE) ?: DEFAULT_ORIENTATION_MODE
        if (stored != "WIND_UP") {
            return false
        }
        preferences.edit()
            .putString(key, MapOrientationMode.TRACK_UP.name)
            .apply()
        return true
    }

    private fun migrateMinSpeedThresholdDefault() {
        if (!preferences.contains(KEY_MIN_SPEED_THRESHOLD)) {
            return
        }
        if (!preferences.getBoolean(KEY_MIN_SPEED_IS_MS, false)) {
            return
        }
        val stored = preferences
            .getFloat(KEY_MIN_SPEED_THRESHOLD, DEFAULT_MIN_SPEED_THRESHOLD_MS.toFloat())
            .toDouble()
        if (abs(stored - LEGACY_MIN_SPEED_THRESHOLD_MS) < 1e-3) {
            preferences.edit()
                .putFloat(KEY_MIN_SPEED_THRESHOLD, DEFAULT_MIN_SPEED_THRESHOLD_MS.toFloat())
                .apply()
        }
    }

    fun getGliderScreenPercent(): Int {
        return preferences.getInt(KEY_GLIDER_SCREEN_PERCENT, DEFAULT_GLIDER_SCREEN_PERCENT)
            .coerceIn(10, 50)
    }

    fun setGliderScreenPercent(percentFromBottom: Int) {
        val clamped = percentFromBottom.coerceIn(10, 50)
        preferences.edit()
            .putInt(KEY_GLIDER_SCREEN_PERCENT, clamped)
            .apply()
    }

    fun getMapShiftBiasMode(): MapShiftBiasMode {
        val stored = preferences.getString(KEY_MAP_SHIFT_BIAS_MODE, DEFAULT_MAP_SHIFT_BIAS_MODE)
        return try {
            MapShiftBiasMode.valueOf(stored ?: DEFAULT_MAP_SHIFT_BIAS_MODE)
        } catch (_: IllegalArgumentException) {
            MapShiftBiasMode.NONE
        }
    }

    fun setMapShiftBiasMode(mode: MapShiftBiasMode) {
        preferences.edit()
            .putString(KEY_MAP_SHIFT_BIAS_MODE, mode.name)
            .apply()
    }

    fun getMapShiftBiasStrength(): Double {
        return preferences.getFloat(
            KEY_MAP_SHIFT_BIAS_STRENGTH,
            DEFAULT_MAP_SHIFT_BIAS_STRENGTH.toFloat()
        ).toDouble().coerceIn(0.0, 1.0)
    }

    fun setMapShiftBiasStrength(strength: Double) {
        val clamped = strength.coerceIn(0.0, 1.0)
        preferences.edit()
            .putFloat(KEY_MAP_SHIFT_BIAS_STRENGTH, clamped.toFloat())
            .apply()
    }

    internal fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    internal fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
