package com.example.xcpro.map

import com.example.xcpro.ogn.OgnDisplayUpdateMode

internal fun buildMapOverlayManagerStatus(
    mapState: MapScreenState,
    showDistanceCircles: Boolean,
    ognDisplayUpdateMode: OgnDisplayUpdateMode,
    latestOgnTargetsCount: Int,
    latestOgnThermalHotspotsCount: Int,
    latestOgnGliderTrailSegmentsCount: Int,
    latestAdsbTargetsCount: Int,
    taskWaypointCount: Int,
    forecastWeatherStatus: MapOverlayForecastWeatherStatus
): String {
    return buildString {
        append("MapOverlayManager Status:\n")
        append("- Distance Circles: $showDistanceCircles\n")
        append(
            "- Distance Circles Overlay: ${
                if (mapState.distanceCirclesOverlay != null) "Initialized" else "Not Initialized"
            }\n"
        )
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
    }
}
