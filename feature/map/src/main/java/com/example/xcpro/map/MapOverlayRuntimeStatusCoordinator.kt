package com.example.xcpro.map

internal class MapOverlayRuntimeStatusCoordinator(
    private val mapState: MapScreenState,
    private val showDistanceCircles: () -> Boolean,
    private val taskWaypointCount: () -> Int,
    private val ognStatusSnapshot: () -> OgnOverlayStatusSnapshot,
    private val latestAdsbTargetsCount: () -> Int,
    private val runtimeCounters: () -> MapOverlayRuntimeCounters,
    private val forecastWeatherStatus: () -> MapOverlayForecastWeatherStatus
) : MapOverlayRuntimeStatusReporter {
    override fun getOverlayStatus(): String {
        val currentOgnStatus = ognStatusSnapshot()
        return buildMapOverlayManagerStatus(
            mapState = mapState,
            showDistanceCircles = showDistanceCircles(),
            ognDisplayUpdateMode = currentOgnStatus.displayUpdateMode,
            latestOgnTargetsCount = currentOgnStatus.targetsCount,
            latestOgnThermalHotspotsCount = currentOgnStatus.thermalHotspotsCount,
            latestOgnGliderTrailSegmentsCount = currentOgnStatus.gliderTrailSegmentsCount,
            ognTargetEnabled = currentOgnStatus.targetEnabled,
            ognTargetResolved = currentOgnStatus.targetResolved,
            latestAdsbTargetsCount = latestAdsbTargetsCount(),
            runtimeCounters = runtimeCounters(),
            taskWaypointCount = taskWaypointCount(),
            forecastWeatherStatus = forecastWeatherStatus()
        )
    }
}
