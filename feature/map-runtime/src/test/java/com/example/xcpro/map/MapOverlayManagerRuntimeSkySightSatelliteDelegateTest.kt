package com.example.xcpro.map

import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapOverlayManagerRuntimeSkySightSatelliteDelegateTest {

    @Test
    fun onMapStyleChanged_reappliesLatestSatelliteConfigToReplacementOverlay() {
        val runtimeState = FakeForecastWeatherOverlayRuntimeState()
        val map: MapLibreMap = mock()
        val existingOverlay: SkySightSatelliteOverlay = mock()
        val delegate = MapOverlayManagerRuntimeSkySightSatelliteDelegate(
            runtimeState = runtimeState,
            bringTrafficOverlaysToFront = {},
            onSatelliteContrastIconsChanged = {}
        )
        runtimeState.mapLibreMap = map
        runtimeState.skySightSatelliteOverlay = existingOverlay

        delegate.setSkySightSatelliteOverlay(
            enabled = true,
            showSatelliteImagery = true,
            showRadar = true,
            showLightning = false,
            animate = false,
            historyFrameCount = 4,
            referenceTimeUtcMs = 1_234L
        )

        val overlayConstruction = mockConstruction(SkySightSatelliteOverlay::class.java)
        try {
            delegate.onMapStyleChanged(map)

            val replacementOverlay = overlayConstruction.constructed().single()
            verify(existingOverlay, times(1)).cleanup()
            verify(replacementOverlay, times(1)).render(
                eq(
                    SkySightSatelliteRenderConfig(
                        enabled = true,
                        showSatelliteImagery = true,
                        showRadar = true,
                        showLightning = false,
                        animate = false,
                        historyFrameCount = 4,
                        referenceTimeUtcMs = 1_234L
                    )
                )
            )
        } finally {
            overlayConstruction.close()
        }
    }

    private class FakeForecastWeatherOverlayRuntimeState : ForecastWeatherOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override var forecastOverlay: ForecastRasterOverlay? = null
        override var forecastWindOverlay: ForecastRasterOverlay? = null
        override var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
        override var weatherRainOverlay: WeatherRainOverlay? = null
    }
}
