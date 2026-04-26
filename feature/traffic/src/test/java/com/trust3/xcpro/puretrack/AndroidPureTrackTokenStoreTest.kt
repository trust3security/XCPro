package com.trust3.xcpro.puretrack

import java.nio.charset.StandardCharsets
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertFailsWith

class AndroidPureTrackTokenStoreTest {

    @Test
    fun saveRead_roundTripStoresSession() = runTest {
        val storage = FakeEncryptedSessionStorage()
        val store = AndroidPureTrackTokenStore(
            storage = storage,
            crypto = FakeTokenCrypto()
        )
        val session = PureTrackStoredSession(accessToken = "token-123", pro = true)

        store.saveSession(session)

        assertEquals(session, store.readSession())
    }

    @Test
    fun saveSession_doesNotStoreRawToken() = runTest {
        val storage = FakeEncryptedSessionStorage()
        val store = AndroidPureTrackTokenStore(
            storage = storage,
            crypto = FakeTokenCrypto()
        )

        store.saveSession(PureTrackStoredSession(accessToken = "secret-token-123", pro = true))

        val rawStoredValues = storage.record.toString()
        assertFalse(rawStoredValues.contains("secret-token-123"))
    }

    @Test
    fun saveSession_usesFreshIvForEachSave() = runTest {
        val storage = FakeEncryptedSessionStorage()
        val store = AndroidPureTrackTokenStore(
            storage = storage,
            crypto = FakeTokenCrypto()
        )

        store.saveSession(PureTrackStoredSession(accessToken = "token-1", pro = true))
        val firstIv = storage.record?.ivBase64
        store.saveSession(PureTrackStoredSession(accessToken = "token-2", pro = true))
        val secondIv = storage.record?.ivBase64

        assertNotEquals(firstIv, secondIv)
    }

    @Test
    fun readSession_missingRecordReturnsNull() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(),
            crypto = FakeTokenCrypto()
        )

        assertNull(store.readSession())
    }

    @Test
    fun saveSession_storageFailureThrowsRedactedTypedFailure() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(
                writeFailure = IllegalStateException("secret-token-123")
            ),
            crypto = FakeTokenCrypto()
        )

        val failure = assertFailsWith<PureTrackTokenStoreUnavailableException> {
            store.saveSession(PureTrackStoredSession(accessToken = "secret-token-123", pro = true))
        }
        assertEquals("PureTrack token persistence unavailable", failure.message)
    }

    @Test
    fun readSession_cryptoFailureThrowsRedactedTypedFailure() = runTest {
        val storage = FakeEncryptedSessionStorage()
        val crypto = FakeTokenCrypto()
        val store = AndroidPureTrackTokenStore(storage = storage, crypto = crypto)
        store.saveSession(PureTrackStoredSession(accessToken = "secret-token-123", pro = true))

        val failure = assertFailsWith<PureTrackTokenStoreUnavailableException> {
            AndroidPureTrackTokenStore(
                storage = storage,
                crypto = FakeTokenCrypto(decryptFailure = IllegalStateException("secret-token-123"))
            ).readSession()
        }

        assertEquals("PureTrack token persistence unavailable", failure.message)
    }

    @Test
    fun clearSession_storageFailureThrowsTypedFailure() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(
                clearFailure = IllegalStateException("secret-token-123")
            ),
            crypto = FakeTokenCrypto()
        )

        val failure = assertFailsWith<PureTrackTokenStoreUnavailableException> {
            store.clearSession()
        }

        assertEquals("PureTrack token persistence unavailable", failure.message)
    }

    @Test
    fun readSession_storageCancellationRethrows() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(
                readFailure = CancellationException("cancelled")
            ),
            crypto = FakeTokenCrypto()
        )

        val failure = assertFailsWith<CancellationException> {
            store.readSession()
        }

        assertEquals("cancelled", failure.message)
    }

    @Test
    fun saveSession_cryptoCancellationRethrows() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(),
            crypto = FakeTokenCrypto(encryptFailure = CancellationException("cancelled"))
        )

        val failure = assertFailsWith<CancellationException> {
            store.saveSession(PureTrackStoredSession(accessToken = "secret-token-123", pro = true))
        }

        assertEquals("cancelled", failure.message)
    }

    @Test
    fun saveSession_storageCancellationRethrows() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(
                writeFailure = CancellationException("cancelled")
            ),
            crypto = FakeTokenCrypto()
        )

        val failure = assertFailsWith<CancellationException> {
            store.saveSession(PureTrackStoredSession(accessToken = "secret-token-123", pro = true))
        }

        assertEquals("cancelled", failure.message)
    }

    @Test
    fun clearSession_storageCancellationRethrows() = runTest {
        val store = AndroidPureTrackTokenStore(
            storage = FakeEncryptedSessionStorage(
                clearFailure = CancellationException("cancelled")
            ),
            crypto = FakeTokenCrypto()
        )

        val failure = assertFailsWith<CancellationException> {
            store.clearSession()
        }

        assertEquals("cancelled", failure.message)
    }

    private class FakeEncryptedSessionStorage(
        var record: PureTrackEncryptedSessionRecord? = null,
        private val readFailure: RuntimeException? = null,
        private val writeFailure: RuntimeException? = null,
        private val clearFailure: RuntimeException? = null
    ) : PureTrackEncryptedSessionStorage {

        override fun read(): PureTrackEncryptedSessionRecord? {
            readFailure?.let { throw it }
            return record
        }

        override fun write(record: PureTrackEncryptedSessionRecord) {
            writeFailure?.let { throw it }
            this.record = record
        }

        override fun clear() {
            clearFailure?.let { throw it }
            record = null
        }
    }

    private class FakeTokenCrypto(
        private val encryptFailure: RuntimeException? = null,
        private val decryptFailure: RuntimeException? = null
    ) : PureTrackTokenCrypto {
        private var ivCounter = 0

        override fun encrypt(plaintext: ByteArray): PureTrackEncryptedPayload {
            encryptFailure?.let { throw it }
            ivCounter += 1
            return PureTrackEncryptedPayload(
                iv = "iv-$ivCounter".toByteArray(StandardCharsets.UTF_8),
                ciphertext = plaintext.xorWithMask()
            )
        }

        override fun decrypt(payload: PureTrackEncryptedPayload): ByteArray {
            decryptFailure?.let { throw it }
            return payload.ciphertext.xorWithMask()
        }

        private fun ByteArray.xorWithMask(): ByteArray =
            map { (it.toInt() xor MASK).toByte() }.toByteArray()

        private companion object {
            private const val MASK = 0x5A
        }
    }
}
