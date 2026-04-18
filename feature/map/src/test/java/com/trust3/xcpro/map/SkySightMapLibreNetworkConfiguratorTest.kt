package com.trust3.xcpro.map

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySightMapLibreNetworkConfiguratorTest {

    @Test
    fun isSkySightHost_matchesAllowlistCaseInsensitively() {
        assertTrue(SkySightMapLibreNetworkConfigurator.isSkySightHost("edge.skysight.io"))
        assertTrue(SkySightMapLibreNetworkConfigurator.isSkySightHost("EDGE.SKYSIGHT.IO"))
        assertTrue(SkySightMapLibreNetworkConfigurator.isSkySightHost("apig2.skysight.io"))
        assertTrue(SkySightMapLibreNetworkConfigurator.isSkySightHost("satellite.skysight.io"))
        assertFalse(SkySightMapLibreNetworkConfigurator.isSkySightHost("skysight.io"))
        assertFalse(SkySightMapLibreNetworkConfigurator.isSkySightHost("example.com"))
    }

    @Test
    fun applySkySightHeaders_addsOriginForAllowlistedHosts() {
        val request = Request.Builder()
            .url("https://edge.skysight.io/path")
            .build()

        val updated = SkySightMapLibreNetworkConfigurator.applySkySightHeaders(request)

        assertEquals("https://xalps.skysight.io", updated.header("Origin"))
    }

    @Test
    fun applySkySightHeaders_doesNotAddOriginForNonAllowlistedHosts() {
        val request = Request.Builder()
            .url("https://static2.skysight.io/path")
            .build()

        val updated = SkySightMapLibreNetworkConfigurator.applySkySightHeaders(request)

        assertNull(updated.header("Origin"))
    }
}
