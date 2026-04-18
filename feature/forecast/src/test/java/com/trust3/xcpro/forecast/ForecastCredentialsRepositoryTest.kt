package com.trust3.xcpro.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ForecastCredentialsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var repository: ForecastCredentialsRepository

    @Before
    fun setUp() = runBlocking {
        repository = ForecastCredentialsRepository(context)
        repository.setVolatileFallbackAllowed(false)
        repository.clearCredentials()
    }

    @Test
    fun saveThenLoadCredentials_preservesExactValues() = runBlocking {
        enableFallbackWhenRequired()
        repository.saveCredentials(
            username = "  pilot@example.com  ",
            password = "  secret123  "
        )

        val credentials = repository.loadCredentials()

        assertNotNull(credentials)
        assertEquals("  pilot@example.com  ", credentials?.username)
        assertEquals("  secret123  ", credentials?.password)
    }

    @Test
    fun loadCredentials_whenNotSet_returnsNull() = runBlocking {
        val credentials = repository.loadCredentials()
        assertNull(credentials)
    }

    @Test
    fun clearCredentials_removesSavedValues() = runBlocking {
        enableFallbackWhenRequired()
        repository.saveCredentials(
            username = "pilot@example.com",
            password = "secret123"
        )
        repository.clearCredentials()

        val credentials = repository.loadCredentials()

        assertNull(credentials)
    }

    @Test
    fun volatileFallbackAllowed_defaultsToFalse() = runBlocking {
        assertEquals(false, repository.volatileFallbackAllowed())
    }

    @Test
    fun setVolatileFallbackAllowed_updatesPolicy() = runBlocking {
        repository.setVolatileFallbackAllowed(true)
        assertEquals(true, repository.volatileFallbackAllowed())

        repository.setVolatileFallbackAllowed(false)
        assertEquals(false, repository.volatileFallbackAllowed())
    }

    @Test
    fun credentialStorageMode_returnsKnownMode() = runBlocking {
        val mode = repository.credentialStorageMode()

        assertTrue(
            mode == ForecastCredentialStorageMode.ENCRYPTED ||
                mode == ForecastCredentialStorageMode.VOLATILE_MEMORY ||
                mode == ForecastCredentialStorageMode.ENCRYPTION_UNAVAILABLE
        )
    }

    private suspend fun enableFallbackWhenRequired() {
        if (repository.credentialStorageMode() == ForecastCredentialStorageMode.ENCRYPTION_UNAVAILABLE) {
            repository.setVolatileFallbackAllowed(true)
        }
    }
}
