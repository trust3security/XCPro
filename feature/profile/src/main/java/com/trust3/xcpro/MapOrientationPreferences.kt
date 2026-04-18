package com.trust3.xcpro

import android.content.Context
import android.content.SharedPreferences
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.map.domain.MapShiftBiasMode
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.core.common.profiles.ProfileSettingsProfileIds
import kotlin.math.abs

class MapOrientationPreferences(context: Context) {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private var activeProfileId: String = DEFAULT_PROFILE_ID

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
        internal val DEFAULT_PROFILE_ID = ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID

        internal fun resolveProfileId(profileId: String?): String {
            return ProfileSettingsProfileIds.canonicalOrDefault(profileId)
        }
    }

    init {
        migrateLegacyOrientationMode()
        migrateRemovedWindUpMode()
        migrateMinSpeedThresholdToMeters()
        migrateMinSpeedThresholdDefault()
    }

    fun setActiveProfileId(profileId: String) {
        activeProfileId = resolveProfileId(profileId)
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

    private fun readMode(
        key: String,
        profileId: String = activeProfileId
    ): MapOrientationMode {
        val stored = getScopedStringOrLegacy(
            profileId = profileId,
            key = key,
            defaultValue = DEFAULT_ORIENTATION_MODE
        )
        return try {
            MapOrientationMode.valueOf(stored ?: DEFAULT_ORIENTATION_MODE)
        } catch (_: IllegalArgumentException) {
            MapOrientationMode.TRACK_UP
        }
    }

    private fun writeMode(
        key: String,
        mode: MapOrientationMode,
        profileId: String = activeProfileId
    ) {
        preferences.edit()
            .putString(scopedKey(profileId, key), mode.name)
            .apply()
    }

    fun getOrientationMode(): MapOrientationMode = getCruiseOrientationMode()

    fun setOrientationMode(mode: MapOrientationMode) {
        setCruiseOrientationMode(mode)
        setCirclingOrientationMode(mode)
    }

    fun getCruiseOrientationMode(profileId: String = activeProfileId): MapOrientationMode =
        readMode(KEY_CRUISE_ORIENTATION, profileId)

    fun setCruiseOrientationMode(
        mode: MapOrientationMode,
        profileId: String = activeProfileId
    ) = writeMode(KEY_CRUISE_ORIENTATION, mode, profileId)

    fun getCirclingOrientationMode(profileId: String = activeProfileId): MapOrientationMode =
        readMode(KEY_CIRCLING_ORIENTATION, profileId)

    fun setCirclingOrientationMode(
        mode: MapOrientationMode,
        profileId: String = activeProfileId
    ) = writeMode(KEY_CIRCLING_ORIENTATION, mode, profileId)

    fun isAutoResetEnabled(profileId: String = activeProfileId): Boolean =
        getScopedBooleanOrLegacy(profileId, KEY_AUTO_RESET_ENABLED, DEFAULT_AUTO_RESET_ENABLED)

    fun setAutoResetEnabled(enabled: Boolean, profileId: String = activeProfileId) {
        preferences.edit()
            .putBoolean(scopedKey(profileId, KEY_AUTO_RESET_ENABLED), enabled)
            .apply()
    }

    fun getAutoResetTimeoutSeconds(profileId: String = activeProfileId): Int =
        getScopedIntOrLegacy(profileId, KEY_AUTO_RESET_TIMEOUT, DEFAULT_AUTO_RESET_TIMEOUT)

    fun setAutoResetTimeoutSeconds(seconds: Int, profileId: String = activeProfileId) {
        val clampedSeconds = seconds.coerceIn(5, 60) // Between 5 and 60 seconds
        preferences.edit()
            .putInt(scopedKey(profileId, KEY_AUTO_RESET_TIMEOUT), clampedSeconds)
            .apply()
    }

    fun getMinSpeedThreshold(profileId: String = activeProfileId): Double =
        getScopedFloatOrLegacy(
            profileId = profileId,
            key = KEY_MIN_SPEED_THRESHOLD,
            defaultValue = DEFAULT_MIN_SPEED_THRESHOLD_MS.toFloat()
        ).toDouble()

    fun setMinSpeedThreshold(speedKnots: Double, profileId: String = activeProfileId) {
        val clampedSpeed = speedKnots.coerceIn(0.0, 20.0) // Between 0 and 20 knots
        val speedMs = UnitsConverter.knotsToMs(clampedSpeed)
        preferences.edit()
            .putFloat(scopedKey(profileId, KEY_MIN_SPEED_THRESHOLD), speedMs.toFloat())
            .putBoolean(scopedKey(profileId, KEY_MIN_SPEED_IS_MS), true)
            .apply()
    }

    fun isBearingSmoothingEnabled(profileId: String = activeProfileId): Boolean =
        getScopedBooleanOrLegacy(profileId, KEY_BEARING_SMOOTHING, DEFAULT_BEARING_SMOOTHING)

    fun setBearingSmoothingEnabled(enabled: Boolean, profileId: String = activeProfileId) {
        preferences.edit()
            .putBoolean(scopedKey(profileId, KEY_BEARING_SMOOTHING), enabled)
            .apply()
    }

    fun resetToDefaults(profileId: String = activeProfileId) {
        preferences.edit()
            .putString(scopedKey(profileId, KEY_CRUISE_ORIENTATION), DEFAULT_ORIENTATION_MODE)
            .putString(scopedKey(profileId, KEY_CIRCLING_ORIENTATION), DEFAULT_ORIENTATION_MODE)
            .putBoolean(scopedKey(profileId, KEY_AUTO_RESET_ENABLED), DEFAULT_AUTO_RESET_ENABLED)
            .putInt(scopedKey(profileId, KEY_AUTO_RESET_TIMEOUT), DEFAULT_AUTO_RESET_TIMEOUT)
            .putFloat(
                scopedKey(profileId, KEY_MIN_SPEED_THRESHOLD),
                DEFAULT_MIN_SPEED_THRESHOLD_MS.toFloat()
            )
            .putBoolean(scopedKey(profileId, KEY_MIN_SPEED_IS_MS), true)
            .putInt(
                scopedKey(profileId, KEY_GLIDER_SCREEN_PERCENT),
                DEFAULT_GLIDER_SCREEN_PERCENT
            )
            .putBoolean(
                scopedKey(profileId, KEY_BEARING_SMOOTHING),
                DEFAULT_BEARING_SMOOTHING
            )
            .putString(scopedKey(profileId, KEY_MAP_SHIFT_BIAS_MODE), DEFAULT_MAP_SHIFT_BIAS_MODE)
            .putFloat(
                scopedKey(profileId, KEY_MAP_SHIFT_BIAS_STRENGTH),
                DEFAULT_MAP_SHIFT_BIAS_STRENGTH.toFloat()
            )
            .apply()
    }

    fun getAllSettings(profileId: String = activeProfileId): Map<String, Any> {
        return mapOf(
            "cruiseOrientation" to getCruiseOrientationMode(profileId).name,
            "circlingOrientation" to getCirclingOrientationMode(profileId).name,
            "autoResetEnabled" to isAutoResetEnabled(profileId),
            "autoResetTimeoutSeconds" to getAutoResetTimeoutSeconds(profileId),
            "minSpeedThreshold" to getMinSpeedThreshold(profileId),
            "gliderScreenPercent" to getGliderScreenPercent(profileId),
            "bearingSmoothingEnabled" to isBearingSmoothingEnabled(profileId),
            "mapShiftBiasMode" to getMapShiftBiasMode(profileId).name,
            "mapShiftBiasStrength" to getMapShiftBiasStrength(profileId)
        )
    }

    fun exportSettings(profileId: String = activeProfileId): String {
        val settings = getAllSettings(profileId)
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

    fun getGliderScreenPercent(profileId: String = activeProfileId): Int {
        return getScopedIntOrLegacy(
            profileId,
            KEY_GLIDER_SCREEN_PERCENT,
            DEFAULT_GLIDER_SCREEN_PERCENT
        )
            .coerceIn(10, 50)
    }

    fun setGliderScreenPercent(
        percentFromBottom: Int,
        profileId: String = activeProfileId
    ) {
        val clamped = percentFromBottom.coerceIn(10, 50)
        preferences.edit()
            .putInt(scopedKey(profileId, KEY_GLIDER_SCREEN_PERCENT), clamped)
            .apply()
    }

    fun getMapShiftBiasMode(profileId: String = activeProfileId): MapShiftBiasMode {
        val stored = getScopedStringOrLegacy(
            profileId = profileId,
            key = KEY_MAP_SHIFT_BIAS_MODE,
            defaultValue = DEFAULT_MAP_SHIFT_BIAS_MODE
        )
        return try {
            MapShiftBiasMode.valueOf(stored ?: DEFAULT_MAP_SHIFT_BIAS_MODE)
        } catch (_: IllegalArgumentException) {
            MapShiftBiasMode.NONE
        }
    }

    fun setMapShiftBiasMode(mode: MapShiftBiasMode, profileId: String = activeProfileId) {
        preferences.edit()
            .putString(scopedKey(profileId, KEY_MAP_SHIFT_BIAS_MODE), mode.name)
            .apply()
    }

    fun getMapShiftBiasStrength(profileId: String = activeProfileId): Double {
        return getScopedFloatOrLegacy(
            profileId = profileId,
            key = KEY_MAP_SHIFT_BIAS_STRENGTH,
            defaultValue = DEFAULT_MAP_SHIFT_BIAS_STRENGTH.toFloat()
        ).toDouble().coerceIn(0.0, 1.0)
    }

    fun setMapShiftBiasStrength(strength: Double, profileId: String = activeProfileId) {
        val clamped = strength.coerceIn(0.0, 1.0)
        preferences.edit()
            .putFloat(scopedKey(profileId, KEY_MAP_SHIFT_BIAS_STRENGTH), clamped.toFloat())
            .apply()
    }

    fun clearProfile(profileId: String) {
        val resolvedProfileId = resolveProfileId(profileId)
        preferences.edit()
            .remove(scopedKey(resolvedProfileId, KEY_CRUISE_ORIENTATION))
            .remove(scopedKey(resolvedProfileId, KEY_CIRCLING_ORIENTATION))
            .remove(scopedKey(resolvedProfileId, KEY_AUTO_RESET_ENABLED))
            .remove(scopedKey(resolvedProfileId, KEY_AUTO_RESET_TIMEOUT))
            .remove(scopedKey(resolvedProfileId, KEY_MIN_SPEED_THRESHOLD))
            .remove(scopedKey(resolvedProfileId, KEY_MIN_SPEED_IS_MS))
            .remove(scopedKey(resolvedProfileId, KEY_GLIDER_SCREEN_PERCENT))
            .remove(scopedKey(resolvedProfileId, KEY_BEARING_SMOOTHING))
            .remove(scopedKey(resolvedProfileId, KEY_MAP_SHIFT_BIAS_MODE))
            .remove(scopedKey(resolvedProfileId, KEY_MAP_SHIFT_BIAS_STRENGTH))
            .apply()
    }

    internal fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    internal fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun scopedKey(profileId: String, key: String): String =
        "profile_${resolveProfileId(profileId)}_$key"

    private fun getScopedStringOrLegacy(
        profileId: String,
        key: String,
        defaultValue: String
    ): String? {
        val scoped = preferences.getString(scopedKey(profileId, key), null)
        return scoped ?: preferences.getString(key, defaultValue)
    }

    private fun getScopedBooleanOrLegacy(
        profileId: String,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        val scopedKey = scopedKey(profileId, key)
        return if (preferences.contains(scopedKey)) {
            preferences.getBoolean(scopedKey, defaultValue)
        } else {
            preferences.getBoolean(key, defaultValue)
        }
    }

    private fun getScopedIntOrLegacy(
        profileId: String,
        key: String,
        defaultValue: Int
    ): Int {
        val scopedKey = scopedKey(profileId, key)
        return if (preferences.contains(scopedKey)) {
            preferences.getInt(scopedKey, defaultValue)
        } else {
            preferences.getInt(key, defaultValue)
        }
    }

    private fun getScopedFloatOrLegacy(
        profileId: String,
        key: String,
        defaultValue: Float
    ): Float {
        val scopedKey = scopedKey(profileId, key)
        return if (preferences.contains(scopedKey)) {
            preferences.getFloat(scopedKey, defaultValue)
        } else {
            preferences.getFloat(key, defaultValue)
        }
    }
}
