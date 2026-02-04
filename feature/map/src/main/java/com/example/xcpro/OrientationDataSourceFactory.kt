package com.example.xcpro

import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.map.config.MapFeatureFlags
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class OrientationDataSourceFactory @Inject constructor(
    private val unifiedSensorManager: UnifiedSensorManager,
    private val headingResolver: HeadingResolver,
    private val flightStateSource: FlightStateSource,
    private val clock: OrientationClock,
    private val featureFlags: MapFeatureFlags
) {
    fun create(scope: CoroutineScope): OrientationSensorSource =
        OrientationDataSource(
            unifiedSensorManager = unifiedSensorManager,
            scope = scope,
            headingResolver = headingResolver,
            flightStateSource = flightStateSource,
            clock = clock,
            featureFlags = featureFlags
        )
}
