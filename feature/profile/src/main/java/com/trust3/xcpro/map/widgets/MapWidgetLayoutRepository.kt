package com.trust3.xcpro.map.widgets

import android.content.Context
import com.trust3.xcpro.core.common.geometry.OffsetPx
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapWidgetLayoutRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readOffset(profileId: String, widgetId: MapWidgetId): OffsetPx? {
        val resolvedProfileId = resolveProfileId(profileId)
        val key = keyPrefix(widgetId)
        val scopedKey = scopedKeyPrefix(resolvedProfileId, widgetId)
        val scopedXKey = "${scopedKey}_x"
        val scopedYKey = "${scopedKey}_y"
        if (prefs.contains(scopedXKey) && prefs.contains(scopedYKey)) {
            return OffsetPx(
                x = prefs.getFloat(scopedXKey, 0f),
                y = prefs.getFloat(scopedYKey, 0f)
            )
        }
        val legacyXKey = "${key}_x"
        val legacyYKey = "${key}_y"
        if (!isLegacyFallbackEligible(resolvedProfileId)) return null
        if (!prefs.contains(legacyXKey) || !prefs.contains(legacyYKey)) return null
        val migrated = OffsetPx(
            x = prefs.getFloat(legacyXKey, 0f),
            y = prefs.getFloat(legacyYKey, 0f)
        )
        saveOffset(resolvedProfileId, widgetId, migrated)
        return migrated
    }

    fun saveOffset(profileId: String, widgetId: MapWidgetId, offset: OffsetPx) {
        val scopedKey = scopedKeyPrefix(resolveProfileId(profileId), widgetId)
        prefs.edit()
            .putFloat("${scopedKey}_x", offset.x)
            .putFloat("${scopedKey}_y", offset.y)
            .apply()
    }

    fun readSizePx(profileId: String, widgetId: MapWidgetId): Float? {
        val resolvedProfileId = resolveProfileId(profileId)
        val key = keyPrefix(widgetId)
        val scopedKey = scopedKeyPrefix(resolvedProfileId, widgetId)
        val scopedSizeKey = "${scopedKey}_size"
        if (prefs.contains(scopedSizeKey)) {
            return prefs.getFloat(scopedSizeKey, 0f)
        }
        val legacySizeKey = "${key}_size"
        if (!isLegacyFallbackEligible(resolvedProfileId)) return null
        if (!prefs.contains(legacySizeKey)) return null
        val migrated = prefs.getFloat(legacySizeKey, 0f)
        saveSizePx(resolvedProfileId, widgetId, migrated)
        return migrated
    }

    fun saveSizePx(profileId: String, widgetId: MapWidgetId, sizePx: Float) {
        val scopedKey = scopedKeyPrefix(resolveProfileId(profileId), widgetId)
        prefs.edit()
            .putFloat("${scopedKey}_size", sizePx)
            .apply()
    }

    fun deleteProfileLayout(profileId: String) {
        val resolvedProfileId = resolveProfileId(profileId)
        val editor = prefs.edit()
        MapWidgetId.entries.forEach { widgetId ->
            val scopedKey = scopedKeyPrefix(resolvedProfileId, widgetId)
            editor.remove("${scopedKey}_x")
            editor.remove("${scopedKey}_y")
            editor.remove("${scopedKey}_size")
        }
        editor.apply()
    }

    fun readOffset(widgetId: MapWidgetId): OffsetPx? =
        readOffset(DEFAULT_PROFILE_ID, widgetId)

    fun saveOffset(widgetId: MapWidgetId, offset: OffsetPx) {
        saveOffset(DEFAULT_PROFILE_ID, widgetId, offset)
    }

    fun readSizePx(widgetId: MapWidgetId): Float? =
        readSizePx(DEFAULT_PROFILE_ID, widgetId)

    fun saveSizePx(widgetId: MapWidgetId, sizePx: Float) {
        saveSizePx(DEFAULT_PROFILE_ID, widgetId, sizePx)
    }

    private fun scopedKeyPrefix(profileId: String, widgetId: MapWidgetId): String {
        return "profile_${profileId}_${keyPrefix(widgetId)}"
    }

    private fun resolveProfileId(profileId: String): String {
        return profileId.trim().ifBlank { DEFAULT_PROFILE_ID }
    }

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
    }

    private fun keyPrefix(widgetId: MapWidgetId): String = when (widgetId) {
        MapWidgetId.SIDE_HAMBURGER -> KEY_SIDE_HAMBURGER
        MapWidgetId.FLIGHT_MODE -> KEY_FLIGHT_MODE
        MapWidgetId.SETTINGS_SHORTCUT -> KEY_SETTINGS_SHORTCUT
        MapWidgetId.BALLAST -> KEY_BALLAST
    }

    private companion object {
        private const val PREFS_NAME = "MapPrefs"
        private const val DEFAULT_PROFILE_ID = "default-profile"
        private const val KEY_SIDE_HAMBURGER = "side_hamburger"
        private const val KEY_FLIGHT_MODE = "flight_mode_menu"
        private const val KEY_SETTINGS_SHORTCUT = "settings_shortcut"
        private const val KEY_BALLAST = "ballast_pill"
    }
}
