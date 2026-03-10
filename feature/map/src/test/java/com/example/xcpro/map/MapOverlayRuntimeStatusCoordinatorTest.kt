package com.example.xcpro.map

import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayRuntimeStatusCoordinatorTest {

    @Test
    fun getOverlayStatus_usesProvidedSnapshotsAndCounters() {
        val coordinator = MapOverlayRuntimeStatusCoordinator(
            mapState = mapStateFixture,
            showDistanceCircles = { true },
            taskWaypointCount = { 12 },
            ognStatusSnapshot = {
                OgnOverlayStatusSnapshot(
                    displayUpdateMode = OgnDisplayUpdateMode.DEFAULT,
                    targetsCount = 2,
                    thermalHotspotsCount = 3,
                    gliderTrailSegmentsCount = 4,
                    targetEnabled = true,
                    targetResolved = false
                )
            },
            latestAdsbTargetsCount = { 5 },
            runtimeCounters = {
                MapOverlayRuntimeCounters(
                    overlayFrontOrderApplyCount = 11L,
                    overlayFrontOrderSkippedCount = 12L,
                    aatPreviewForwardCount = 1L,
                    adsbIconUnknownRenderCount = 10L,
                    adsbIconLegacyUnknownRenderCount = 11L,
                    adsbIconResolveLatencySampleCount = 12L,
                    adsbIconResolveLatencyLastMs = 13L,
                    adsbIconResolveLatencyMaxMs = 14L,
                    adsbIconResolveLatencyAverageMs = 15L,
                    adsbDefaultMediumUnknownIconEnabled = true
                )
            },
            forecastWeatherStatus = {
                MapOverlayForecastWeatherStatus(
                    forecastOverlayEnabled = true,
                    forecastWindOverlayEnabled = true,
                    satelliteContrastIconsEnabled = false,
                    skySightSatelliteEnabled = true,
                    skySightSatelliteImageryEnabled = false,
                    skySightSatelliteRadarEnabled = true,
                    skySightSatelliteLightningEnabled = true,
                    skySightSatelliteAnimateEnabled = false,
                    skySightSatelliteHistoryFrames = 3,
                    weatherRainEnabled = true,
                    weatherRainStatusCode = WeatherRadarStatusCode.OK,
                    weatherRainStale = false,
                    weatherRainFrameSelected = true,
                    weatherRainTransitionDurationMs = 250L
                )
            }
        )

        val status = coordinator.getOverlayStatus()

        assertTrue(status.contains("MapOverlayManager Status:"))
        assertTrue(status.contains("- Distance Circles: true"))
        assertTrue(status.contains("- OGN Targets: 2"))
        assertTrue(status.contains("- ADS-B Targets: 5"))
        assertTrue(status.contains("- ADS-B Default Medium Unknown Icon Enabled: true"))
        assertTrue(status.contains("- Task Waypoints: 12"))
        assertTrue(status.contains("- SkySight Satellite Overlay Enabled: true"))
        assertTrue(status.contains("- Forecast Overlay Enabled: true"))
        assertTrue(status.contains("- Weather Rain Status: OK"))
    }

    companion object {
        private val mapStateFixture = MapScreenState()
    }
}

