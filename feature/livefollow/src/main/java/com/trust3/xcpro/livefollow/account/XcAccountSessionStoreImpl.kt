package com.trust3.xcpro.livefollow.account

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XcAccountSessionStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : XcAccountSessionStore {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    override suspend fun loadSession(): XcAccountSession? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.trim().orEmpty()
        if (accessToken.isBlank()) return null
        return XcAccountSession(
            accessToken = accessToken,
            authMethod = XcAccountAuthMethod.fromStorageValue(
                prefs.getString(KEY_AUTH_METHOD, null)
            )
        )
    }

    override suspend fun saveSession(session: XcAccountSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_AUTH_METHOD, session.authMethod.storageValue)
            .apply()
    }

    override suspend fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_AUTH_METHOD)
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
        private const val PREFS_NAME = "xcpro_private_follow_account"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_AUTH_METHOD = "auth_method"
    }
}
