package com.trust3.xcpro.map

import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleUrlResolverTest {

    @Test
    fun resolve_knownStyle_returnsHttpsMapTilerUrl() {
        val url = MapStyleUrlResolver.resolve("Terrain")

        assertTrue(url.startsWith("https://api.maptiler.com/maps/"))
        assertTrue(url.contains("style.json?key="))
    }

    @Test
    fun resolve_unknownStyle_fallsBackToTerrain() {
        val url = MapStyleUrlResolver.resolve("UnknownStyle")

        assertTrue(url.contains("/maps/topo/style.json"))
    }

    @Test
    fun resolve_thermallingContrast_returnsDatavizLightStyle() {
        val url = MapStyleUrlResolver.resolve(MapStyleCatalog.THERMALLING_CONTRAST)

        assertTrue(url.contains("/maps/dataviz-light/style.json"))
    }
}
