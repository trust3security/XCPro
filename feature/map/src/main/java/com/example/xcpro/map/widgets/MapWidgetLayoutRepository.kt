package com.example.xcpro.map.widgets

import android.content.Context
import com.example.xcpro.core.common.geometry.OffsetPx
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapWidgetLayoutRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readOffset(widgetId: MapWidgetId): OffsetPx? {
        val key = keyPrefix(widgetId)
        val xKey = "${key}_x"
        val yKey = "${key}_y"
        if (!prefs.contains(xKey) || !prefs.contains(yKey)) return null
        return OffsetPx(
            x = prefs.getFloat(xKey, 0f),
            y = prefs.getFloat(yKey, 0f)
        )
    }

    fun saveOffset(widgetId: MapWidgetId, offset: OffsetPx) {
        val key = keyPrefix(widgetId)
        prefs.edit()
            .putFloat("${key}_x", offset.x)
            .putFloat("${key}_y", offset.y)
            .apply()
    }

    fun readSizePx(widgetId: MapWidgetId): Float? {
        val key = keyPrefix(widgetId)
        val sizeKey = "${key}_size"
        if (!prefs.contains(sizeKey)) return null
        return prefs.getFloat(sizeKey, 0f)
    }

    fun saveSizePx(widgetId: MapWidgetId, sizePx: Float) {
        val key = keyPrefix(widgetId)
        prefs.edit()
            .putFloat("${key}_size", sizePx)
            .apply()
    }

    private fun keyPrefix(widgetId: MapWidgetId): String = when (widgetId) {
        MapWidgetId.SIDE_HAMBURGER -> KEY_SIDE_HAMBURGER
        MapWidgetId.FLIGHT_MODE -> KEY_FLIGHT_MODE
        MapWidgetId.SETTINGS_SHORTCUT -> KEY_SETTINGS_SHORTCUT
        MapWidgetId.BALLAST -> KEY_BALLAST
    }

    private companion object {
        private const val PREFS_NAME = "MapPrefs"
        private const val KEY_SIDE_HAMBURGER = "side_hamburger"
        private const val KEY_FLIGHT_MODE = "flight_mode_menu"
        private const val KEY_SETTINGS_SHORTCUT = "settings_shortcut"
        private const val KEY_BALLAST = "ballast_pill"
    }
}
