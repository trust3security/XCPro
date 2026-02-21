package com.example.xcpro.forecast

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForecastCredentialsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    fun loadCredentials(): ForecastProviderCredentials? {
        val savedUsername = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val savedPassword = prefs.getString(KEY_PASSWORD, null)?.trim().orEmpty()
        if (savedUsername.isBlank() || savedPassword.isBlank()) {
            return null
        }
        return ForecastProviderCredentials(
            username = savedUsername,
            password = savedPassword
        )
    }

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password.trim())
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private fun createEncryptedPrefsOrFallback(): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        private const val PREFS_NAME = "forecast_provider_credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
