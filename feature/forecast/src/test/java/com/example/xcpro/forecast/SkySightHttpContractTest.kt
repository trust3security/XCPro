package com.example.xcpro.forecast

import java.io.IOException
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySightHttpContractTest {

    @Test
    fun validatedHttpUrl_rejectsNonAllowlistedHost() {
        val failure = runCatching {
            SkySightHttpContract.validatedHttpUrl("https://example.com/path")
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message?.contains("allowlisted") == true)
    }

    @Test
    fun validatedHttpUrl_rejectsNonHttpsScheme() {
        val failure = runCatching {
            SkySightHttpContract.validatedHttpUrl("http://edge.skysight.io/path")
        }.exceptionOrNull()

        assertTrue(failure is IOException)
        assertTrue(failure?.message?.contains("HTTPS") == true)
    }

    @Test
    fun applyStandardHeaders_addsOriginForPointHost() {
        val request = Request.Builder()
            .url("https://cf.skysight.io/point/0/0")
            .get()
        SkySightHttpContract.applyStandardHeaders(
            builder = request,
            host = "cf.skysight.io",
            apiKey = "test-key"
        )
        val built = request.build()

        assertEquals("https://xalps.skysight.io", built.header("Origin"))
        assertEquals("test-key", built.header("X-API-KEY"))
        assertTrue(built.header("Accept")?.contains("application/json") == true)
    }

    @Test
    fun applyStandardHeaders_staticLegendHost_doesNotSetOrigin() {
        val request = Request.Builder()
            .url("https://static2.skysight.io/path")
            .get()
        SkySightHttpContract.applyStandardHeaders(
            builder = request,
            host = "static2.skysight.io",
            apiKey = "test-key"
        )
        val built = request.build()

        assertNull(built.header("Origin"))
        assertEquals("test-key", built.header("X-API-KEY"))
    }

    @Test
    fun requiresApiKey_matchesExpectedHosts() {
        assertTrue(SkySightHttpContract.requiresApiKey("edge.skysight.io"))
        assertTrue(SkySightHttpContract.requiresApiKey("CF.SKYSIGHT.IO"))
        assertTrue(SkySightHttpContract.requiresApiKey("skysight.io"))
        assertFalse(SkySightHttpContract.requiresApiKey("static2.skysight.io"))
        assertFalse(SkySightHttpContract.requiresApiKey("satellite.skysight.io"))
    }
}
