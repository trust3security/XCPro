package com.example.xcpro.variometer.layout

import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset

/**
 * Persistence adapter for the variometer overlay layout.
 *
 * Keeps compatibility with legacy keys (`variometer_*`) while persisting under
 * the newer "uilevo" namespace.
 */
class VariometerWidgetRepository(
    private val prefs: SharedPreferences
    ) {

    fun load(defaultOffset: Offset, defaultSizePx: Float): VariometerLayout {
        val offsetX = readFloat(KEY_OFFSET_X)
            ?: readFloat(LEGACY_OFFSET_X)
            ?: defaultOffset.x
        val offsetY = readFloat(KEY_OFFSET_Y)
            ?: readFloat(LEGACY_OFFSET_Y)
            ?: defaultOffset.y
        val sizePx = readFloat(KEY_SIZE)
            ?: readFloat(LEGACY_SIZE)
            ?: defaultSizePx
        val hasOffset = prefs.contains(KEY_OFFSET_X) && prefs.contains(KEY_OFFSET_Y)
        val hasSize = prefs.contains(KEY_SIZE)
        return VariometerLayout(
            offset = Offset(offsetX, offsetY),
            sizePx = sizePx,
            hasPersistedOffset = hasOffset,
            hasPersistedSize = hasSize
        )
    }

    fun saveOffset(offset: Offset) {
        prefs.edit()
            .putFloat(KEY_OFFSET_X, offset.x)
            .putFloat(KEY_OFFSET_Y, offset.y)
            .apply()
    }

    fun saveSize(sizePx: Float) {
        prefs.edit()
            .putFloat(KEY_SIZE, sizePx)
            .apply()
    }

    private fun readFloat(key: String): Float? =
        if (prefs.contains(key)) prefs.getFloat(key, 0f) else null

    private companion object {
        const val KEY_OFFSET_X = "uilevo_x"
        const val KEY_OFFSET_Y = "uilevo_y"
        const val KEY_SIZE = "uilevo_size"

        const val LEGACY_OFFSET_X = "variometer_x"
        const val LEGACY_OFFSET_Y = "variometer_y"
        const val LEGACY_SIZE = "variometer_size"
    }
}
