package com.trust3.xcpro.map

import com.trust3.xcpro.weather.rain.WeatherRadarStatusCode
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
                    adsbDefaultMediumUnknownIconEnabled = true,
                    adsbAnimationFrameScheduledCount = 31L,
                    adsbAnimationFrameRenderedCount = 32L,
                    adsbAnimationFrameSkippedCount = 33L,
                    adsbActiveAnimatedTargetCount = 34,
                    adsbEmergencyAnimatedTargetCount = 35,
                    adsbInteractionReducedMotionActive = true,
                    ognTrafficCollectorEmissionCount = 16L,
                    ognTrafficCollectorDedupedCount = 17L,
                    ognTrafficPortUpdateCount = 18L,
                    ognTargetVisualCollectorEmissionCount = 19L,
                    ognTargetVisualCollectorDedupedCount = 20L,
                    ognTargetVisualPortUpdateCount = 21L,
                    adsbTrafficCollectorEmissionCount = 22L,
                    adsbTrafficCollectorDedupedCount = 23L,
                    adsbTrafficPortUpdateCount = 24L,
                    ognThermalCollectorEmissionCount = 25L,
                    ognTrailCollectorEmissionCount = 26L,
                    selectedOgnThermalCollectorEmissionCount = 27L
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
            },
            renderSurfaceStatus = {
                "Render Surface Diagnostics:\n- Repaint Requests: 28\n- Frames Rendered: 26\n"
            }
        )

        val status = coordinator.getOverlayStatus()

        assertTrue(status.contains("MapOverlayManager Status:"))
        assertTrue(status.contains("- Distance Circles: true"))
        assertTrue(status.contains("- OGN Targets: 2"))
        assertTrue(status.contains("- ADS-B Targets: 5"))
        assertTrue(status.contains("- ADS-B Default Medium Unknown Icon Enabled: true"))
        assertTrue(status.contains("- ADS-B Animation Frame Scheduled Count: 31"))
        assertTrue(status.contains("- ADS-B Interaction Reduced Motion Active: true"))
        assertTrue(status.contains("- OGN Traffic Collector Emissions: 16"))
        assertTrue(status.contains("- ADS-B Traffic Port Updates: 24"))
        assertTrue(status.contains("- Task Waypoints: 12"))
        assertTrue(status.contains("- SkySight Satellite Overlay Enabled: true"))
        assertTrue(status.contains("- Forecast Overlay Enabled: true"))
        assertTrue(status.contains("- Weather Rain Status: OK"))
        assertTrue(status.contains("Render Surface Diagnostics:"))
        assertTrue(status.contains("- Repaint Requests: 28"))
        assertTrue(status.contains("- Frames Rendered: 26"))
    }

    companion object {
        private val mapStateFixture = MapScreenState()
    }
}
