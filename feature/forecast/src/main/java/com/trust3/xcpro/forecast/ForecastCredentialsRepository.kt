package com.trust3.xcpro.forecast

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
    private val storageLock = Any()

    @Volatile
    private var storageMode: ForecastCredentialStorageMode = ForecastCredentialStorageMode.ENCRYPTED

    private val fallbackPolicyPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(FALLBACK_POLICY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var storage: CredentialStorage = createStorage()

    suspend fun loadCredentials(): ForecastProviderCredentials? {
        val activeStorage = synchronized(storageLock) { storage }
        val savedUsername = activeStorage.getUsername().orEmpty()
        val savedPassword = activeStorage.getPassword().orEmpty()
        if (savedUsername.isBlank() || savedPassword.isBlank()) {
            return null
        }
        return ForecastProviderCredentials(
            username = savedUsername,
            password = savedPassword
        )
    }

    suspend fun saveCredentials(username: String, password: String) {
        val activeStorage = synchronized(storageLock) {
            if (storageMode == ForecastCredentialStorageMode.ENCRYPTION_UNAVAILABLE) {
                throw IllegalStateException(
                    "Secure credential storage unavailable; enable memory-only fallback to proceed."
                )
            }
            storage
        }
        activeStorage.save(username = username, password = password)
    }

    suspend fun clearCredentials() {
        synchronized(storageLock) {
            storage.clear()
        }
    }

    suspend fun credentialStorageMode(): ForecastCredentialStorageMode {
        return synchronized(storageLock) { storageMode }
    }

    suspend fun volatileFallbackAllowed(): Boolean {
        return fallbackPolicyPrefs.getBoolean(KEY_ALLOW_VOLATILE_FALLBACK, false)
    }

    suspend fun setVolatileFallbackAllowed(allowed: Boolean) {
        val persisted = fallbackPolicyPrefs.edit()
            .putBoolean(KEY_ALLOW_VOLATILE_FALLBACK, allowed)
            .commit()
        if (!persisted) {
            throw IllegalStateException("Failed to persist credential storage fallback policy.")
        }
        synchronized(storageLock) {
            if (!allowed && storageMode == ForecastCredentialStorageMode.VOLATILE_MEMORY) {
                storage.clear()
            }
            storage = createStorage()
        }
    }

    private fun createStorage(): CredentialStorage {
        val encryptedStorage = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            SharedPreferencesCredentialStorage(encryptedPrefs)
        }.onSuccess {
            storageMode = ForecastCredentialStorageMode.ENCRYPTED
        }.getOrNull()
        if (encryptedStorage != null) {
            return encryptedStorage
        }

        val fallbackAllowed = fallbackPolicyPrefs.getBoolean(KEY_ALLOW_VOLATILE_FALLBACK, false)
        if (fallbackAllowed) {
            storageMode = ForecastCredentialStorageMode.VOLATILE_MEMORY
            return InMemoryCredentialStorage()
        }
        storageMode = ForecastCredentialStorageMode.ENCRYPTION_UNAVAILABLE
        return DisabledCredentialStorage
    }

    private interface CredentialStorage {
        fun getUsername(): String?

        fun getPassword(): String?

        fun save(username: String, password: String)

        fun clear()
    }

    private class SharedPreferencesCredentialStorage(
        private val prefs: SharedPreferences
    ) : CredentialStorage {
        override fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

        override fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

        override fun save(username: String, password: String) {
            val saved = prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .commit()
            if (!saved) {
                throw IllegalStateException("Failed to persist credentials.")
            }
        }

        override fun clear() {
            val cleared = prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .commit()
            if (!cleared) {
                throw IllegalStateException("Failed to clear credentials.")
            }
        }
    }

    private class InMemoryCredentialStorage : CredentialStorage {
        @Volatile
        private var username: String? = null

        @Volatile
        private var password: String? = null

        override fun getUsername(): String? = username

        override fun getPassword(): String? = password

        @Synchronized
        override fun save(username: String, password: String) {
            this.username = username
            this.password = password
        }

        @Synchronized
        override fun clear() {
            username = null
            password = null
        }
    }

    private object DisabledCredentialStorage : CredentialStorage {
        override fun getUsername(): String? = null

        override fun getPassword(): String? = null

        override fun save(username: String, password: String) {
            throw IllegalStateException("Secure credential storage unavailable.")
        }

        override fun clear() = Unit
    }

    private companion object {
        private const val PREFS_NAME = "forecast_provider_credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val FALLBACK_POLICY_PREFS_NAME = "forecast_provider_credentials_policy"
        private const val KEY_ALLOW_VOLATILE_FALLBACK = "allow_volatile_fallback"
    }
}
