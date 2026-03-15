package com.example.xcpro

import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.orientation.OrientationSensorInputSource
import com.example.xcpro.orientation.OrientationStationaryHeadingPolicy
import com.example.xcpro.sensors.FlightStateSource
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
