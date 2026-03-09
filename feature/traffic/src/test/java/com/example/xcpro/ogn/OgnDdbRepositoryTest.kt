package com.example.xcpro.ogn

import android.content.Context
import com.example.xcpro.core.time.FakeClock
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class OgnDdbRepositoryTest {

    @Test
    fun refreshIfNeeded_notDue_loadsCacheAndSkipsNetwork() = runTest {
        val filesDir = Files.createTempDirectory("ogn-ddb-test-not-due").toFile()
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        val clock = FakeClock(monoMs = 0L, wallMs = 100_000_000L)
        val repository = OgnDdbRepository(
            context = context,
            clock = clock,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val cacheDir = filesDir.resolve("ogn-ddb")
        cacheDir.mkdirs()
        cacheDir.resolve("ddb.json").writeText(MINIMAL_DDB_JSON, Charsets.UTF_8)
        cacheDir.resolve("ddb.timestamp").writeText((clock.nowWallMs() - ONE_HOUR_MS).toString(), Charsets.UTF_8)
        repository.connectionFactory = { throw AssertionError("Network should not be used for NotDue path") }

        val result = repository.refreshIfNeeded(force = false)

        assertEquals(OgnDdbRefreshResult.NotDue, result)
        val identity = repository.lookup(addressType = OgnAddressType.FLARM, deviceIdHex = "0123BC")
        assertNotNull(identity)
        assertEquals("X-0123", identity?.registration)
    }

    @Test
    fun refreshIfNeeded_forceUpdated_downloadsAndStoresIdentity() = runTest {
        val filesDir = Files.createTempDirectory("ogn-ddb-test-updated").toFile()
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        val clock = FakeClock(monoMs = 0L, wallMs = 2_000_000L)
        val repository = OgnDdbRepository(
            context = context,
            clock = clock,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        repository.connectionFactory = {
            FakeHttpURLConnection(
                responseCode = 200,
                responseBody = MINIMAL_DDB_JSON
            )
        }

        val result = repository.refreshIfNeeded(force = true)

        assertEquals(OgnDdbRefreshResult.Updated, result)
        assertEquals(clock.nowWallMs(), repository.lastUpdateWallMs())
        val identity = repository.lookup(addressType = OgnAddressType.FLARM, deviceIdHex = "0123BC")
        assertNotNull(identity)
    }

    @Test
    fun refreshIfNeeded_httpFailure_throwsIllegalState() = runTest {
        val filesDir = Files.createTempDirectory("ogn-ddb-test-http-fail").toFile()
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        val clock = FakeClock(monoMs = 0L, wallMs = 3_000_000L)
        val repository = OgnDdbRepository(
            context = context,
            clock = clock,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        repository.connectionFactory = {
            FakeHttpURLConnection(
                responseCode = 503,
                responseBody = """{"status":"error"}"""
            )
        }

        val thrown = runCatching { repository.refreshIfNeeded(force = true) }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown?.message?.contains("HTTP 503") == true)
    }

    @Test
    fun refreshIfNeeded_emptyPayload_throwsIllegalState() = runTest {
        val filesDir = Files.createTempDirectory("ogn-ddb-test-empty").toFile()
        val context: Context = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        val clock = FakeClock(monoMs = 0L, wallMs = 4_000_000L)
        val repository = OgnDdbRepository(
            context = context,
            clock = clock,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        repository.connectionFactory = {
            FakeHttpURLConnection(
                responseCode = 200,
                responseBody = """{"devices":[]}"""
            )
        }

        val thrown = runCatching { repository.refreshIfNeeded(force = true) }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown?.message?.contains("empty payload") == true)
    }

    private class FakeHttpURLConnection(
        responseCode: Int,
        responseBody: String
    ) : HttpURLConnection(URL("https://example.invalid/ddb")) {
        private val code = responseCode
        private val bodyBytes = responseBody.toByteArray(StandardCharsets.UTF_8)

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = code

        override fun getInputStream(): InputStream = ByteArrayInputStream(bodyBytes)
    }

    private companion object {
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
        private val MINIMAL_DDB_JSON = """
            {
              "devices": [
                {
                  "device_type": "F",
                  "device_id": "0123BC",
                  "aircraft_model": "LS-4",
                  "registration": "X-0123",
                  "cn": "23",
                  "tracked": "Y",
                  "identified": "Y",
                  "aircraft_type": "1"
                }
              ]
            }
        """.trimIndent()
    }
}
