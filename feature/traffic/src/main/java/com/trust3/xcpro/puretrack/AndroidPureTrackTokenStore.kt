package com.trust3.xcpro.puretrack

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.cancellation.CancellationException

class PureTrackTokenStoreUnavailableException :
    RuntimeException("PureTrack token persistence unavailable")

internal class AndroidPureTrackTokenStore(
    private val storage: PureTrackEncryptedSessionStorage,
    private val crypto: PureTrackTokenCrypto
) : PureTrackTokenStore {

    constructor(context: Context) : this(
        storage = SharedPreferencesPureTrackEncryptedSessionStorage(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ),
        crypto = AndroidKeystorePureTrackTokenCrypto()
    )

    override suspend fun readSession(): PureTrackStoredSession? = runTokenStoreOperation {
        val record = storage.read() ?: return@runTokenStoreOperation null
        if (record.version != STORED_PAYLOAD_VERSION) {
            throw IllegalStateException("Unsupported PureTrack token payload")
        }
        val plaintext = crypto.decrypt(
            PureTrackEncryptedPayload(
                iv = decodeBase64(record.ivBase64),
                ciphertext = decodeBase64(record.ciphertextBase64)
            )
        )
        decodeSession(plaintext)
    }

    override suspend fun saveSession(session: PureTrackStoredSession) = runTokenStoreOperation {
        val payload = crypto.encrypt(encodeSession(session))
        storage.write(
            PureTrackEncryptedSessionRecord(
                version = STORED_PAYLOAD_VERSION,
                ivBase64 = encodeBase64(payload.iv),
                ciphertextBase64 = encodeBase64(payload.ciphertext)
            )
        )
    }

    override suspend fun clearSession() = runTokenStoreOperation {
        storage.clear()
    }

    private inline fun <T> runTokenStoreOperation(block: () -> T): T {
        try {
            return block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw PureTrackTokenStoreUnavailableException()
        }
    }

    private fun encodeSession(session: PureTrackStoredSession): ByteArray {
        val encodedToken = encodeBase64(session.accessToken.toByteArray(StandardCharsets.UTF_8))
        val proValue = if (session.pro) "1" else "0"
        return "$STORED_PAYLOAD_VERSION\n$proValue\n$encodedToken"
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun decodeSession(plaintext: ByteArray): PureTrackStoredSession {
        val parts = plaintext.toString(StandardCharsets.UTF_8).split('\n', limit = 3)
        if (parts.size != 3 || parts[0].toIntOrNull() != STORED_PAYLOAD_VERSION) {
            throw IllegalStateException("Malformed PureTrack token payload")
        }
        val pro = when (parts[1]) {
            "1" -> true
            "0" -> false
            else -> throw IllegalStateException("Malformed PureTrack token payload")
        }
        val token = decodeBase64(parts[2]).toString(StandardCharsets.UTF_8).trim()
        if (token.isBlank()) {
            throw IllegalStateException("Malformed PureTrack token payload")
        }
        return PureTrackStoredSession(accessToken = token, pro = pro)
    }

    private fun encodeBase64(value: ByteArray): String =
        Base64.getEncoder().encodeToString(value)

    private fun decodeBase64(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    private companion object {
        private const val STORED_PAYLOAD_VERSION = 1
        private const val PREFS_NAME = "puretrack_token_store"
    }
}

internal data class PureTrackEncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray
)

internal data class PureTrackEncryptedSessionRecord(
    val version: Int,
    val ivBase64: String,
    val ciphertextBase64: String
)

internal interface PureTrackTokenCrypto {
    fun encrypt(plaintext: ByteArray): PureTrackEncryptedPayload
    fun decrypt(payload: PureTrackEncryptedPayload): ByteArray
}

internal interface PureTrackEncryptedSessionStorage {
    fun read(): PureTrackEncryptedSessionRecord?
    fun write(record: PureTrackEncryptedSessionRecord)
    fun clear()
}

private class AndroidKeystorePureTrackTokenCrypto : PureTrackTokenCrypto {

    override fun encrypt(plaintext: ByteArray): PureTrackEncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return PureTrackEncryptedPayload(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext)
        )
    }

    override fun decrypt(payload: PureTrackEncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
        )
        return cipher.doFinal(payload.ciphertext)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "xcpro_puretrack_token_store"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}

private class SharedPreferencesPureTrackEncryptedSessionStorage(
    private val preferences: SharedPreferences
) : PureTrackEncryptedSessionStorage {

    override fun read(): PureTrackEncryptedSessionRecord? {
        val hasAnyValue =
            preferences.contains(KEY_VERSION) ||
                preferences.contains(KEY_IV) ||
                preferences.contains(KEY_CIPHERTEXT)
        if (!hasAnyValue) {
            return null
        }
        val version = preferences.getInt(KEY_VERSION, -1)
        val iv = preferences.getString(KEY_IV, null)
        val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
        if (version < 0 || iv.isNullOrBlank() || ciphertext.isNullOrBlank()) {
            throw IllegalStateException("Malformed PureTrack token record")
        }
        return PureTrackEncryptedSessionRecord(
            version = version,
            ivBase64 = iv,
            ciphertextBase64 = ciphertext
        )
    }

    override fun write(record: PureTrackEncryptedSessionRecord) {
        val committed = preferences.edit()
            .putInt(KEY_VERSION, record.version)
            .putString(KEY_IV, record.ivBase64)
            .putString(KEY_CIPHERTEXT, record.ciphertextBase64)
            .commit()
        if (!committed) {
            throw IllegalStateException("PureTrack token write failed")
        }
    }

    override fun clear() {
        val committed = preferences.edit()
            .remove(KEY_VERSION)
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .commit()
        if (!committed) {
            throw IllegalStateException("PureTrack token clear failed")
        }
    }

    private companion object {
        private const val KEY_VERSION = "version"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ciphertext"
    }
}
