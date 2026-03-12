package com.example.xcpro.weglide.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeGlideTokenStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WeGlideTokenStore {
    private val prefs: SharedPreferences by lazy {
        createEncryptedPrefsOrFallback()
    }

    override suspend fun getTokens(): WeGlideTokenBundle? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.trim().orEmpty()
        if (accessToken.isBlank()) return null
        return WeGlideTokenBundle(
            accessToken = accessToken,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.trim()?.ifBlank { null },
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES_AT_EPOCH_MS, 0L),
            tokenType = prefs.getString(KEY_TOKEN_TYPE, null)?.trim().orEmpty().ifBlank { "Bearer" }
        )
    }

    override suspend fun saveTokens(tokens: WeGlideTokenBundle) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT_EPOCH_MS, tokens.expiresAtEpochMs)
            .putString(KEY_TOKEN_TYPE, tokens.tokenType)
            .apply()
    }

    override suspend fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT_EPOCH_MS)
            .remove(KEY_TOKEN_TYPE)
            .apply()
    }

    override suspend fun savePendingPkce(pkce: PendingPkce) {
        prefs.edit()
            .putString(KEY_PENDING_CODE_VERIFIER, pkce.codeVerifier)
            .putString(KEY_PENDING_CODE_CHALLENGE, pkce.codeChallenge)
            .putString(KEY_PENDING_STATE, pkce.state)
            .apply()
    }

    override suspend fun getPendingPkce(): PendingPkce? {
        val codeVerifier = prefs.getString(KEY_PENDING_CODE_VERIFIER, null)?.trim().orEmpty()
        val codeChallenge = prefs.getString(KEY_PENDING_CODE_CHALLENGE, null)?.trim().orEmpty()
        val state = prefs.getString(KEY_PENDING_STATE, null)?.trim().orEmpty()
        if (codeVerifier.isBlank() || codeChallenge.isBlank() || state.isBlank()) return null
        return PendingPkce(
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
            state = state
        )
    }

    override suspend fun clearPendingPkce() {
        prefs.edit()
            .remove(KEY_PENDING_CODE_VERIFIER)
            .remove(KEY_PENDING_CODE_CHALLENGE)
            .remove(KEY_PENDING_STATE)
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
        private const val PREFS_NAME = "weglide_oauth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT_EPOCH_MS = "expires_at_epoch_ms"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_PENDING_CODE_VERIFIER = "pending_code_verifier"
        private const val KEY_PENDING_CODE_CHALLENGE = "pending_code_challenge"
        private const val KEY_PENDING_STATE = "pending_state"
    }
}
