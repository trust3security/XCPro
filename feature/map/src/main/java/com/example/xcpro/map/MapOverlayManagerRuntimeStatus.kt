package com.example.xcpro.map

internal fun buildMapOverlayManagerStatus(
    mapState: MapScreenState,
    showDistanceCircles: Boolean,
    ognDisplayUpdateMode: OgnDisplayUpdateMode,
    latestOgnTargetsCount: Int,
    latestOgnThermalHotspotsCount: Int,
    latestOgnGliderTrailSegmentsCount: Int,
    ognTargetEnabled: Boolean,
    ognTargetResolved: Boolean,
    latestAdsbTargetsCount: Int,
    runtimeCounters: MapOverlayRuntimeCounters,
    taskWaypointCount: Int,
    forecastWeatherStatus: MapOverlayForecastWeatherStatus,
    renderSurfaceStatus: String
): String {
    return buildString {
        append("MapOverlayManager Status:\n")
        append("- Distance Circles: $showDistanceCircles\n")
        append(
            "- Blue Location Overlay: ${
                if (mapState.blueLocationOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append(
            "- OGN Traffic Overlay: ${
                if (mapState.ognTrafficOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- OGN Display Update Mode: ${ognDisplayUpdateMode.displayLabel}\n")
        append("- OGN Satellite Contrast Icons Enabled: ${forecastWeatherStatus.satelliteContrastIconsEnabled}\n")
        append("- OGN Targets: $latestOgnTargetsCount\n")
        append("- OGN Target Enabled: $ognTargetEnabled\n")
        append("- OGN Target Resolved: $ognTargetResolved\n")
        append(
            "- OGN Target Ring Overlay: ${
                if (mapState.ognTargetRingOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append(
            "- OGN Target Line Overlay: ${
                if (mapState.ognTargetLineOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append(
            "- OGN Ownship Target Badge Overlay: ${
                if (mapState.ognOwnshipTargetBadgeOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append(
            "- OGN Thermal Overlay: ${
                if (mapState.ognThermalOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- OGN Thermal Hotspots: $latestOgnThermalHotspotsCount\n")
        append(
            "- OGN Glider Trail Overlay: ${
                if (mapState.ognGliderTrailOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- OGN Glider Trail Segments: $latestOgnGliderTrailSegmentsCount\n")
        append(
            "- ADS-B Traffic Overlay: ${
                if (mapState.adsbTrafficOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- ADS-B Targets: $latestAdsbTargetsCount\n")
        append("- ADS-B Default Medium Unknown Icon Enabled: ${runtimeCounters.adsbDefaultMediumUnknownIconEnabled}\n")
        append("- ADS-B Unknown Icon Render Count: ${runtimeCounters.adsbIconUnknownRenderCount}\n")
        append("- ADS-B Legacy Unknown Icon Render Count: ${runtimeCounters.adsbIconLegacyUnknownRenderCount}\n")
        append("- ADS-B Icon Resolve Latency Sample Count: ${runtimeCounters.adsbIconResolveLatencySampleCount}\n")
        append("- ADS-B Icon Resolve Latency Last Ms: ${runtimeCounters.adsbIconResolveLatencyLastMs}\n")
        append("- ADS-B Icon Resolve Latency Max Ms: ${runtimeCounters.adsbIconResolveLatencyMaxMs}\n")
        append("- ADS-B Icon Resolve Latency Average Ms: ${runtimeCounters.adsbIconResolveLatencyAverageMs}\n")
        append("- ADS-B Animation Frame Scheduled Count: ${runtimeCounters.adsbAnimationFrameScheduledCount}\n")
        append("- ADS-B Animation Frame Rendered Count: ${runtimeCounters.adsbAnimationFrameRenderedCount}\n")
        append("- ADS-B Animation Frame Skipped Count: ${runtimeCounters.adsbAnimationFrameSkippedCount}\n")
        append("- ADS-B Active Animated Target Count: ${runtimeCounters.adsbActiveAnimatedTargetCount}\n")
        append("- ADS-B Emergency Animated Target Count: ${runtimeCounters.adsbEmergencyAnimatedTargetCount}\n")
        append("- ADS-B Interaction Reduced Motion Active: ${runtimeCounters.adsbInteractionReducedMotionActive}\n")
        append("- Overlay Front Order Apply Count: ${runtimeCounters.overlayFrontOrderApplyCount}\n")
        append("- Overlay Front Order Skipped Count: ${runtimeCounters.overlayFrontOrderSkippedCount}\n")
        append("- OGN Traffic Collector Emissions: ${runtimeCounters.ognTrafficCollectorEmissionCount}\n")
        append("- OGN Traffic Collector Deduped: ${runtimeCounters.ognTrafficCollectorDedupedCount}\n")
        append("- OGN Traffic Port Updates: ${runtimeCounters.ognTrafficPortUpdateCount}\n")
        append(
            "- OGN Target Visual Collector Emissions: ${
                runtimeCounters.ognTargetVisualCollectorEmissionCount
            }\n"
        )
        append(
            "- OGN Target Visual Collector Deduped: ${
                runtimeCounters.ognTargetVisualCollectorDedupedCount
            }\n"
        )
        append("- OGN Target Visual Port Updates: ${runtimeCounters.ognTargetVisualPortUpdateCount}\n")
        append("- ADS-B Traffic Collector Emissions: ${runtimeCounters.adsbTrafficCollectorEmissionCount}\n")
        append("- ADS-B Traffic Collector Deduped: ${runtimeCounters.adsbTrafficCollectorDedupedCount}\n")
        append("- ADS-B Traffic Port Updates: ${runtimeCounters.adsbTrafficPortUpdateCount}\n")
        append("- OGN Thermal Collector Emissions: ${runtimeCounters.ognThermalCollectorEmissionCount}\n")
        append("- OGN Trail Collector Emissions: ${runtimeCounters.ognTrailCollectorEmissionCount}\n")
        append(
            "- Selected OGN Thermal Collector Emissions: ${
                runtimeCounters.selectedOgnThermalCollectorEmissionCount
            }\n"
        )
        append("- Forecast Overlay Enabled: ${forecastWeatherStatus.forecastOverlayEnabled}\n")
        append("- Forecast Wind Overlay Enabled: ${forecastWeatherStatus.forecastWindOverlayEnabled}\n")
        append(
            "- Forecast Raster Overlay: ${
                if (mapState.forecastOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append(
            "- Forecast Wind Overlay: ${
                if (mapState.forecastWindOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- SkySight Satellite Overlay Enabled: ${forecastWeatherStatus.skySightSatelliteEnabled}\n")
        append("- SkySight Satellite Imagery Enabled: ${forecastWeatherStatus.skySightSatelliteImageryEnabled}\n")
        append("- SkySight Satellite Radar Enabled: ${forecastWeatherStatus.skySightSatelliteRadarEnabled}\n")
        append("- SkySight Satellite Lightning Enabled: ${forecastWeatherStatus.skySightSatelliteLightningEnabled}\n")
        append("- SkySight Satellite Animate Enabled: ${forecastWeatherStatus.skySightSatelliteAnimateEnabled}\n")
        append("- SkySight Satellite History Frames: ${forecastWeatherStatus.skySightSatelliteHistoryFrames}\n")
        append(
            "- SkySight Satellite Runtime Overlay: ${
                if (mapState.skySightSatelliteOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- Weather Rain Enabled: ${forecastWeatherStatus.weatherRainEnabled}\n")
        append("- Weather Rain Status: ${forecastWeatherStatus.weatherRainStatusCode}\n")
        append("- Weather Rain Stale: ${forecastWeatherStatus.weatherRainStale}\n")
        append("- Weather Rain Frame Selected: ${forecastWeatherStatus.weatherRainFrameSelected}\n")
        append("- Weather Rain Transition Duration Ms: ${forecastWeatherStatus.weatherRainTransitionDurationMs}\n")
        append(
            "- Weather Rain Overlay: ${
                if (mapState.weatherRainOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
        append("- Task Waypoints: $taskWaypointCount\n")
        append(renderSurfaceStatus)
    }
}
