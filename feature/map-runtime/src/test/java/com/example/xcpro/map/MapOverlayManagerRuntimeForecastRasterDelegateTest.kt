package com.example.xcpro.map

import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapOverlayManagerRuntimeForecastRasterDelegateTest {

    @Test
    fun onMapStyleChanged_reappliesLatestForecastConfigToReplacementOverlays() {
        val runtimeState = FakeForecastWeatherOverlayRuntimeState()
        val map: MapLibreMap = mock()
        val existingPrimaryOverlay: ForecastRasterOverlay = mock()
        val existingWindOverlay: ForecastRasterOverlay = mock()
        val delegate = MapOverlayManagerRuntimeForecastRasterDelegate(
            runtimeState = runtimeState,
            bringTrafficOverlaysToFront = {}
        )
        val primaryTileSpec = tileSpec("https://tiles.example/primary/{z}/{x}/{y}.pbf")
        val windTileSpec = tileSpec("https://tiles.example/wind/{z}/{x}/{y}.pbf")
        val primaryLegend = ForecastLegendSpec(
            unitLabel = "Primary",
            stops = emptyList()
        )
        val windLegend = ForecastLegendSpec(
            unitLabel = "Wind",
            stops = emptyList()
        )
        runtimeState.mapLibreMap = map
        runtimeState.forecastOverlay = existingPrimaryOverlay
        runtimeState.forecastWindOverlay = existingWindOverlay

        delegate.setForecastOverlay(
            enabled = true,
            primaryTileSpec = primaryTileSpec,
            primaryLegendSpec = primaryLegend,
            windOverlayEnabled = true,
            windTileSpec = windTileSpec,
            windLegendSpec = windLegend,
            opacity = 0.70f,
            windOverlayScale = 1.25f,
            windDisplayMode = ForecastWindDisplayMode.ARROW
        )

        val overlayConstruction = mockConstruction(ForecastRasterOverlay::class.java)
        try {
            delegate.onMapStyleChanged(map)

            val replacementPrimaryOverlay = overlayConstruction.constructed()[0]
            val replacementWindOverlay = overlayConstruction.constructed()[1]
            verify(existingPrimaryOverlay, times(1)).cleanup()
            verify(existingWindOverlay, times(1)).cleanup()
            verify(replacementPrimaryOverlay, times(1)).render(
                tileSpec = eq(primaryTileSpec),
                opacity = eq(0.70f),
                windOverlayScale = eq(1.25f),
                windDisplayMode = eq(ForecastWindDisplayMode.ARROW),
                legendSpec = eq(primaryLegend)
            )
            verify(replacementWindOverlay, times(1)).render(
                tileSpec = eq(windTileSpec),
                opacity = eq(0.70f),
                windOverlayScale = eq(1.25f),
                windDisplayMode = eq(ForecastWindDisplayMode.ARROW),
                legendSpec = eq(windLegend)
            )
        } finally {
            overlayConstruction.close()
        }
    }

    private fun tileSpec(urlTemplate: String): ForecastTileSpec = ForecastTileSpec(
        urlTemplate = urlTemplate,
        minZoom = 3,
        maxZoom = 5
    )

    private class FakeForecastWeatherOverlayRuntimeState : ForecastWeatherOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override var forecastOverlay: ForecastRasterOverlay? = null
        override var forecastWindOverlay: ForecastRasterOverlay? = null
        override var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
        override var weatherRainOverlay: WeatherRainOverlay? = null
    }
}
