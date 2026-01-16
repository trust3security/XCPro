package com.example.xcpro.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val COLOR_THEME_PREFS = "ColorThemePrefs"

class ThemePreferencesRepository(
    context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(COLOR_THEME_PREFS, Context.MODE_PRIVATE)
    }

    fun getThemeId(profileId: String): String {
        return prefs.getString(themeKey(profileId), AppColorTheme.DEFAULT.id) ?: AppColorTheme.DEFAULT.id
    }

    fun observeThemeId(profileId: String): Flow<String> {
        return stringFlow(themeKey(profileId), AppColorTheme.DEFAULT.id)
    }

    fun getCustomColorsJson(profileId: String, themeId: String): String? {
        return prefs.getString(customColorsKey(profileId, themeId), null)
    }

    fun observeCustomColorsJson(profileId: String, themeId: String): Flow<String?> {
        return nullableStringFlow(customColorsKey(profileId, themeId))
    }

    private fun themeKey(profileId: String): String = "profile_${profileId}_color_theme"

    private fun customColorsKey(profileId: String, themeId: String): String {
        return "profile_${profileId}_theme_${themeId}_custom_colors"
    }

    private fun stringFlow(key: String, defaultValue: String): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(prefs.getString(key, defaultValue) ?: defaultValue)
            }
        }
        trySend(prefs.getString(key, defaultValue) ?: defaultValue)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun nullableStringFlow(key: String): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(prefs.getString(key, null))
            }
        }
        trySend(prefs.getString(key, null))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
