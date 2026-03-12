package com.example.xcpro.weglide.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.xcpro.weglide.domain.WeGlideAccountLink
import com.example.xcpro.weglide.domain.WeGlideAccountStore
import com.example.xcpro.weglide.domain.WeGlideAuthMode
import com.example.xcpro.weglide.domain.WeGlidePreferencesStore
import com.example.xcpro.weglide.domain.WeGlideUploadPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val WEGLIDE_PREFERENCES_STORE_NAME = "weglide_integration_preferences"

private val Context.weGlidePreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = WEGLIDE_PREFERENCES_STORE_NAME
)

private val KEY_ACCOUNT_USER_ID = longPreferencesKey("weglide_account_user_id")
private val KEY_ACCOUNT_DISPLAY_NAME = stringPreferencesKey("weglide_account_display_name")
private val KEY_ACCOUNT_EMAIL = stringPreferencesKey("weglide_account_email")
private val KEY_ACCOUNT_CONNECTED_AT = longPreferencesKey("weglide_account_connected_at_epoch_ms")
private val KEY_ACCOUNT_AUTH_MODE = stringPreferencesKey("weglide_account_auth_mode")

private val KEY_AUTO_UPLOAD_FINISHED_FLIGHTS =
    booleanPreferencesKey("weglide_auto_upload_finished_flights")
private val KEY_UPLOAD_ON_WIFI_ONLY = booleanPreferencesKey("weglide_upload_on_wifi_only")
private val KEY_RETRY_ON_MOBILE_DATA = booleanPreferencesKey("weglide_retry_on_mobile_data")
private val KEY_SHOW_COMPLETION_NOTIFICATION =
    booleanPreferencesKey("weglide_show_completion_notification")
private val KEY_DEBUG_ENABLED = booleanPreferencesKey("weglide_debug_enabled")

@Singleton
class WeGlidePreferencesRepository constructor(
    private val dataStore: DataStore<Preferences>
) : WeGlideAccountStore, WeGlidePreferencesStore {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(context.weGlidePreferencesStore)

    override val accountLink: Flow<WeGlideAccountLink?> = dataStore.data
        .map { preferences ->
            val authMode = preferences[KEY_ACCOUNT_AUTH_MODE]
                ?.let { raw -> runCatching { WeGlideAuthMode.valueOf(raw) }.getOrNull() }
                ?: return@map null

            WeGlideAccountLink(
                userId = preferences[KEY_ACCOUNT_USER_ID],
                displayName = normalizeNonBlankOrNull(preferences[KEY_ACCOUNT_DISPLAY_NAME]),
                email = normalizeNonBlankOrNull(preferences[KEY_ACCOUNT_EMAIL]),
                connectedAtEpochMs = preferences[KEY_ACCOUNT_CONNECTED_AT] ?: 0L,
                authMode = authMode
            )
        }
        .distinctUntilChanged()

    override val preferences: Flow<WeGlideUploadPreferences> = dataStore.data
        .map { values ->
            WeGlideUploadPreferences(
                autoUploadFinishedFlights = values[KEY_AUTO_UPLOAD_FINISHED_FLIGHTS] ?: false,
                uploadOnWifiOnly = values[KEY_UPLOAD_ON_WIFI_ONLY] ?: false,
                retryOnMobileData = values[KEY_RETRY_ON_MOBILE_DATA] ?: true,
                showCompletionNotification = values[KEY_SHOW_COMPLETION_NOTIFICATION] ?: true,
                debugEnabled = values[KEY_DEBUG_ENABLED] ?: false
            )
        }
        .distinctUntilChanged()

    override suspend fun saveAccountLink(accountLink: WeGlideAccountLink) {
        dataStore.edit { values ->
            if (accountLink.userId == null) {
                values.remove(KEY_ACCOUNT_USER_ID)
            } else {
                values[KEY_ACCOUNT_USER_ID] = accountLink.userId
            }
            setOptionalString(values, KEY_ACCOUNT_DISPLAY_NAME, accountLink.displayName)
            setOptionalString(values, KEY_ACCOUNT_EMAIL, accountLink.email)
            values[KEY_ACCOUNT_CONNECTED_AT] = accountLink.connectedAtEpochMs
            values[KEY_ACCOUNT_AUTH_MODE] = accountLink.authMode.name
        }
    }

    override suspend fun clearAccountLink() {
        dataStore.edit { values ->
            values.remove(KEY_ACCOUNT_USER_ID)
            values.remove(KEY_ACCOUNT_DISPLAY_NAME)
            values.remove(KEY_ACCOUNT_EMAIL)
            values.remove(KEY_ACCOUNT_CONNECTED_AT)
            values.remove(KEY_ACCOUNT_AUTH_MODE)
        }
    }

    override suspend fun setAutoUploadFinishedFlights(enabled: Boolean) {
        setBoolean(KEY_AUTO_UPLOAD_FINISHED_FLIGHTS, enabled)
    }

    override suspend fun setUploadOnWifiOnly(enabled: Boolean) {
        setBoolean(KEY_UPLOAD_ON_WIFI_ONLY, enabled)
    }

    override suspend fun setRetryOnMobileData(enabled: Boolean) {
        setBoolean(KEY_RETRY_ON_MOBILE_DATA, enabled)
    }

    override suspend fun setShowCompletionNotification(enabled: Boolean) {
        setBoolean(KEY_SHOW_COMPLETION_NOTIFICATION, enabled)
    }

    override suspend fun setDebugEnabled(enabled: Boolean) {
        setBoolean(KEY_DEBUG_ENABLED, enabled)
    }

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { values -> values[key] = value }
    }

    private fun setOptionalString(
        values: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>,
        value: String?
    ) {
        val normalized = normalizeNonBlankOrNull(value)
        if (normalized == null) {
            values.remove(key)
        } else {
            values[key] = normalized
        }
    }
}

private fun normalizeNonBlankOrNull(value: String?): String? {
    return value?.trim()?.takeIf { text -> text.isNotEmpty() }
}
