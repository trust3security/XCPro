package com.example.xcpro.adsb

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenSkyCredentialsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configuredCredentialsProvider: OpenSkyConfiguredCredentialsProvider
) {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    fun loadCredentials(): OpenSkyClientCredentials? {
        configuredCredentialsProvider.loadConfiguredCredentials()?.let { configuredCredentials ->
            if (configuredCredentials.clientId.isNotBlank() &&
                configuredCredentials.clientSecret.isNotBlank()
            ) {
                return configuredCredentials
            }
        }

        val savedClientId = prefs.getString(KEY_CLIENT_ID, null)?.trim().orEmpty()
        val savedClientSecret = prefs.getString(KEY_CLIENT_SECRET, null)?.trim().orEmpty()
        if (savedClientId.isNotBlank() && savedClientSecret.isNotBlank()) {
            return OpenSkyClientCredentials(
                clientId = savedClientId,
                clientSecret = savedClientSecret
            )
        }
        return null
    }

    fun saveCredentials(clientId: String, clientSecret: String) {
        prefs.edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_CLIENT_ID)
            .remove(KEY_CLIENT_SECRET)
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
        private const val PREFS_NAME = "adsb_opensky_credentials"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
    }
}
