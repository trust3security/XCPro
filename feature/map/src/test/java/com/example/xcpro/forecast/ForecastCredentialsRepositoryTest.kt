package com.example.xcpro.forecast

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
        repository.clearCredentials()
    }

    @Test
    fun saveThenLoadCredentials_trimsAndReturnsValues() = runBlocking {
        repository.saveCredentials(
            username = "  pilot@example.com  ",
            password = "  secret123  "
        )

        val credentials = repository.loadCredentials()

        assertNotNull(credentials)
        assertEquals("pilot@example.com", credentials?.username)
        assertEquals("secret123", credentials?.password)
    }

    @Test
    fun loadCredentials_whenNotSet_returnsNull() = runBlocking {
        val credentials = repository.loadCredentials()
        assertNull(credentials)
    }

    @Test
    fun clearCredentials_removesSavedValues() = runBlocking {
        repository.saveCredentials(
            username = "pilot@example.com",
            password = "secret123"
        )
        repository.clearCredentials()

        val credentials = repository.loadCredentials()

        assertNull(credentials)
    }

    @Test
    fun credentialStorageMode_returnsKnownMode() = runBlocking {
        val mode = repository.credentialStorageMode()

        assertTrue(
            mode == ForecastCredentialStorageMode.ENCRYPTED ||
                mode == ForecastCredentialStorageMode.PLAINTEXT_FALLBACK
        )
    }
}
