package com.example.xcpro.variometer.layout

import android.content.Context
import com.example.xcpro.core.common.geometry.OffsetPx
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Persistence adapter for the variometer overlay layout.
 *
 * Keeps compatibility with legacy keys (`variometer_*`) while persisting under
 * the newer "uilevo" namespace.
 */
class VariometerWidgetRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(profileId: String, defaultOffset: OffsetPx, defaultSizePx: Float): VariometerLayout {
        val resolvedProfileId = resolveProfileId(profileId)
        val scopedOffsetX = scopedKey(resolvedProfileId, KEY_OFFSET_X)
        val scopedOffsetY = scopedKey(resolvedProfileId, KEY_OFFSET_Y)
        val scopedSize = scopedKey(resolvedProfileId, KEY_SIZE)
        val hasScopedOffset = prefs.contains(scopedOffsetX) && prefs.contains(scopedOffsetY)
        val hasScopedSize = prefs.contains(scopedSize)
        val hasLegacyOffset = isLegacyFallbackEligible(resolvedProfileId) && (
            (prefs.contains(KEY_OFFSET_X) && prefs.contains(KEY_OFFSET_Y)) ||
                (prefs.contains(LEGACY_OFFSET_X) && prefs.contains(LEGACY_OFFSET_Y))
            )
        val hasLegacySize = isLegacyFallbackEligible(resolvedProfileId) && (
            prefs.contains(KEY_SIZE) || prefs.contains(LEGACY_SIZE)
            )

        val offsetX = readFloat(scopedOffsetX)
            ?: legacyFallbackFloat(resolvedProfileId, KEY_OFFSET_X)
            ?: legacyFallbackFloat(resolvedProfileId, LEGACY_OFFSET_X)
            ?: defaultOffset.x
        val offsetY = readFloat(scopedOffsetY)
            ?: legacyFallbackFloat(resolvedProfileId, KEY_OFFSET_Y)
            ?: legacyFallbackFloat(resolvedProfileId, LEGACY_OFFSET_Y)
            ?: defaultOffset.y
        val sizePx = readFloat(scopedSize)
            ?: legacyFallbackFloat(resolvedProfileId, KEY_SIZE)
            ?: legacyFallbackFloat(resolvedProfileId, LEGACY_SIZE)
            ?: defaultSizePx

        val hasOffset = hasScopedOffset || hasLegacyOffset
        val hasSize = hasScopedSize || hasLegacySize
        if (!hasScopedOffset) {
            saveOffset(resolvedProfileId, OffsetPx(offsetX, offsetY))
        }
        if (!hasScopedSize) {
            saveSize(resolvedProfileId, sizePx)
        }
        return VariometerLayout(
            offset = OffsetPx(offsetX, offsetY),
            sizePx = sizePx,
            hasPersistedOffset = hasOffset,
            hasPersistedSize = hasSize
        )
    }

    fun saveOffset(profileId: String, offset: OffsetPx) {
        val resolvedProfileId = resolveProfileId(profileId)
        prefs.edit()
            .putFloat(scopedKey(resolvedProfileId, KEY_OFFSET_X), offset.x)
            .putFloat(scopedKey(resolvedProfileId, KEY_OFFSET_Y), offset.y)
            .apply()
    }

    fun saveSize(profileId: String, sizePx: Float) {
        val resolvedProfileId = resolveProfileId(profileId)
        prefs.edit()
            .putFloat(scopedKey(resolvedProfileId, KEY_SIZE), sizePx)
            .apply()
    }

    fun deleteProfileLayout(profileId: String) {
        val resolvedProfileId = resolveProfileId(profileId)
        prefs.edit()
            .remove(scopedKey(resolvedProfileId, KEY_OFFSET_X))
            .remove(scopedKey(resolvedProfileId, KEY_OFFSET_Y))
            .remove(scopedKey(resolvedProfileId, KEY_SIZE))
            .apply()
    }

    fun load(defaultOffset: OffsetPx, defaultSizePx: Float): VariometerLayout =
        load(DEFAULT_PROFILE_ID, defaultOffset, defaultSizePx)

    fun saveOffset(offset: OffsetPx) {
        saveOffset(DEFAULT_PROFILE_ID, offset)
    }

    fun saveSize(sizePx: Float) {
        saveSize(DEFAULT_PROFILE_ID, sizePx)
    }

    private fun scopedKey(profileId: String, key: String): String =
        "profile_${profileId}_$key"

    private fun legacyFallbackFloat(profileId: String, key: String): Float? {
        if (!isLegacyFallbackEligible(profileId)) return null
        return readFloat(key)
    }

    private fun resolveProfileId(profileId: String): String =
        profileId.trim().ifBlank { DEFAULT_PROFILE_ID }

    private fun isLegacyFallbackEligible(profileId: String): Boolean {
        return profileId == DEFAULT_PROFILE_ID
    }

    private fun readFloat(key: String): Float? =
        if (prefs.contains(key)) prefs.getFloat(key, 0f) else null

    companion object {
        const val DEFAULT_PROFILE_ID = "default-profile"
        const val PREFS_NAME = "MapPrefs"
        const val KEY_OFFSET_X = "uilevo_x"
        const val KEY_OFFSET_Y = "uilevo_y"
        const val KEY_SIZE = "uilevo_size"

        const val LEGACY_OFFSET_X = "variometer_x"
        const val LEGACY_OFFSET_Y = "variometer_y"
        const val LEGACY_SIZE = "variometer_size"
    }
}
