package com.example.xcpro.screens.navdrawer.lookandfeel

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.ui.theme.AppColorTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val LOOK_AND_FEEL_PREFS = "LookAndFeelPrefs"
private const val COLOR_THEME_PREFS = "ColorThemePrefs"

/**
 * Persistence helper for look & feel settings scoped by profile.
 */
class LookAndFeelPreferences(
    private val context: Context
) {

    private val lookAndFeelPrefs by lazy {
        context.getSharedPreferences(LOOK_AND_FEEL_PREFS, Context.MODE_PRIVATE)
    }

    private val colorPrefs by lazy {
        context.getSharedPreferences(COLOR_THEME_PREFS, Context.MODE_PRIVATE)
    }

    fun getStatusBarStyle(profileId: String): StatusBarStyle {
        val stored = lookAndFeelPrefs.getString(
            "profile_${profileId}_status_bar_style",
            StatusBarStyle.TRANSPARENT.id
        )
        return StatusBarStyle.values().find { it.id == stored } ?: StatusBarStyle.TRANSPARENT
    }

    fun setStatusBarStyle(profileId: String, style: StatusBarStyle) {
        lookAndFeelPrefs.edit()
            .putString("profile_${profileId}_status_bar_style", style.id)
            .apply()
    }

    fun getCardStyle(profileId: String): CardStyle {
        val stored = lookAndFeelPrefs.getString(
            cardStyleKey(profileId),
            CardStyle.STANDARD.id
        )
        return CardStyle.values().find { it.id == stored } ?: CardStyle.STANDARD
    }

    fun setCardStyle(profileId: String, style: CardStyle) {
        lookAndFeelPrefs.edit()
            .putString(cardStyleKey(profileId), style.id)
            .apply()
    }

    fun observeCardStyle(profileId: String): Flow<CardStyle> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == cardStyleKey(profileId)) {
                trySend(getCardStyle(profileId))
            }
        }
        trySend(getCardStyle(profileId))
        lookAndFeelPrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { lookAndFeelPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getColorTheme(profileId: String): AppColorTheme {
        val stored = colorPrefs.getString(
            "profile_${profileId}_color_theme",
            AppColorTheme.DEFAULT.id
        )
        return AppColorTheme.values().find { it.id == stored } ?: AppColorTheme.DEFAULT
    }

    fun setColorTheme(profileId: String, theme: AppColorTheme) {
        colorPrefs.edit()
            .putString("profile_${profileId}_color_theme", theme.id)
            .apply()
    }

    private fun cardStyleKey(profileId: String): String = "profile_${profileId}_card_style"
}
