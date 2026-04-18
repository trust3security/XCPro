package com.trust3.xcpro

import com.trust3.xcpro.orientation.HeadingResolver
import com.trust3.xcpro.orientation.OrientationClock
import com.trust3.xcpro.orientation.OrientationSensorInputSource
import com.trust3.xcpro.orientation.OrientationStationaryHeadingPolicy
import com.trust3.xcpro.sensors.FlightStateSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class OrientationDataSourceFactory @Inject constructor(
    private val sensorInputSource: OrientationSensorInputSource,
    private val headingResolver: HeadingResolver,
    private val flightStateSource: FlightStateSource,
    private val clock: OrientationClock,
    private val stationaryHeadingPolicy: OrientationStationaryHeadingPolicy
) {
    fun create(scope: CoroutineScope): OrientationSensorSource =
        OrientationDataSource(
            sensorInputSource = sensorInputSource,
            scope = scope,
            headingResolver = headingResolver,
            flightStateSource = flightStateSource,
            clock = clock,
            stationaryHeadingPolicy = stationaryHeadingPolicy
        )
}
