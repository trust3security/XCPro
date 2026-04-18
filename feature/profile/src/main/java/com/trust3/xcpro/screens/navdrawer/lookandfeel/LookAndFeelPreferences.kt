package com.trust3.xcpro.screens.navdrawer.lookandfeel

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val LOOK_AND_FEEL_PREFS = "LookAndFeelPrefs"
private const val DEFAULT_STATUS_BAR_STYLE_ID = "transparent"
private const val DEFAULT_CARD_STYLE_ID = "standard"

/**
 * Persistence helper for look & feel settings scoped by profile.
 */
@Singleton
class LookAndFeelPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val lookAndFeelPrefs by lazy {
        context.getSharedPreferences(LOOK_AND_FEEL_PREFS, Context.MODE_PRIVATE)
    }

    fun getStatusBarStyleId(profileId: String): String {
        return lookAndFeelPrefs.getString(
            "profile_${profileId}_status_bar_style",
            DEFAULT_STATUS_BAR_STYLE_ID
        ) ?: DEFAULT_STATUS_BAR_STYLE_ID
    }

    fun observeStatusBarStyleId(profileId: String): Flow<String> = stringFlow(
        lookAndFeelPrefs,
        "profile_${profileId}_status_bar_style",
        DEFAULT_STATUS_BAR_STYLE_ID
    )

    fun setStatusBarStyleId(profileId: String, styleId: String) {
        lookAndFeelPrefs.edit()
            .putString("profile_${profileId}_status_bar_style", styleId)
            .apply()
    }

    fun getCardStyleId(profileId: String): String {
        return lookAndFeelPrefs.getString(
            cardStyleKey(profileId),
            DEFAULT_CARD_STYLE_ID
        ) ?: DEFAULT_CARD_STYLE_ID
    }

    fun setCardStyleId(profileId: String, styleId: String) {
        lookAndFeelPrefs.edit()
            .putString(cardStyleKey(profileId), styleId)
            .apply()
    }

    fun observeCardStyleId(profileId: String): Flow<String> = stringFlow(
        lookAndFeelPrefs,
        cardStyleKey(profileId),
        DEFAULT_CARD_STYLE_ID
    )

    fun clearProfile(profileId: String) {
        lookAndFeelPrefs.edit()
            .remove("profile_${profileId}_status_bar_style")
            .remove(cardStyleKey(profileId))
            .apply()
    }

    private fun cardStyleKey(profileId: String): String = "profile_${profileId}_card_style"

    private fun stringFlow(
        sourcePrefs: SharedPreferences,
        key: String,
        defaultValue: String
    ): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(sourcePrefs.getString(key, defaultValue) ?: defaultValue)
            }
        }
        trySend(sourcePrefs.getString(key, defaultValue) ?: defaultValue)
        sourcePrefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sourcePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
