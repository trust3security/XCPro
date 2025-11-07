package com.example.xcpro

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.orientation.MapOrientationMode

class MapOrientationPreferences(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "map_orientation_prefs"
        private const val KEY_ORIENTATION_MODE = "orientation_mode"
        private const val KEY_AUTO_RESET_ENABLED = "auto_reset_enabled"
        private const val KEY_AUTO_RESET_TIMEOUT = "auto_reset_timeout_seconds"
        private const val KEY_MIN_SPEED_THRESHOLD = "min_speed_threshold_kt"
        private const val KEY_BEARING_SMOOTHING = "bearing_smoothing_enabled"

        // Default values
        private const val DEFAULT_ORIENTATION_MODE = "TRACK_UP"
        private const val DEFAULT_AUTO_RESET_ENABLED = true
        private const val DEFAULT_AUTO_RESET_TIMEOUT = 10 // seconds
        private const val DEFAULT_MIN_SPEED_THRESHOLD = 0.0 // knots
        private const val DEFAULT_BEARING_SMOOTHING = true
    }

    fun getOrientationMode(): MapOrientationMode {
        val modeString = preferences.getString(KEY_ORIENTATION_MODE, DEFAULT_ORIENTATION_MODE)
        return try {
            MapOrientationMode.valueOf(modeString ?: DEFAULT_ORIENTATION_MODE)
        } catch (e: IllegalArgumentException) {
            // Fallback to default if invalid value stored
            MapOrientationMode.NORTH_UP
        }
    }

    fun setOrientationMode(mode: MapOrientationMode) {
        preferences.edit()
            .putString(KEY_ORIENTATION_MODE, mode.name)
            .apply()
    }

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
        return preferences.getFloat(KEY_MIN_SPEED_THRESHOLD, DEFAULT_MIN_SPEED_THRESHOLD.toFloat()).toDouble()
    }

    fun setMinSpeedThreshold(speedKnots: Double) {
        val clampedSpeed = speedKnots.coerceIn(0.0, 20.0) // Between 0 and 20 knots
        preferences.edit()
            .putFloat(KEY_MIN_SPEED_THRESHOLD, clampedSpeed.toFloat())
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
            .putString(KEY_ORIENTATION_MODE, DEFAULT_ORIENTATION_MODE)
            .putBoolean(KEY_AUTO_RESET_ENABLED, DEFAULT_AUTO_RESET_ENABLED)
            .putInt(KEY_AUTO_RESET_TIMEOUT, DEFAULT_AUTO_RESET_TIMEOUT)
            .putFloat(KEY_MIN_SPEED_THRESHOLD, DEFAULT_MIN_SPEED_THRESHOLD.toFloat())
            .putBoolean(KEY_BEARING_SMOOTHING, DEFAULT_BEARING_SMOOTHING)
            .apply()
    }

    fun getAllSettings(): Map<String, Any> {
        return mapOf(
            "orientationMode" to getOrientationMode().name,
            "autoResetEnabled" to isAutoResetEnabled(),
            "autoResetTimeoutSeconds" to getAutoResetTimeoutSeconds(),
            "minSpeedThreshold" to getMinSpeedThreshold(),
            "bearingSmoothingEnabled" to isBearingSmoothingEnabled()
        )
    }

    fun exportSettings(): String {
        val settings = getAllSettings()
        return settings.entries.joinToString(separator = "\n") { "${it.key}: ${it.value}" }
    }
}
