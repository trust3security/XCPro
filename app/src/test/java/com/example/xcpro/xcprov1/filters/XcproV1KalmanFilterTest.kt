package com.example.xcpro.xcprov1.filters

import com.example.xcpro.xcprov1.model.XcproV1State
import org.junit.Assert.assertEquals
import org.junit.Test

class XcproV1KalmanFilterTest {

    @Test
    fun predict_doesNotIntegrateVerticalWindIntoAltitude() {
        val filter = XcproV1KalmanFilter()
        filter.reset()

        val stateField = XcproV1KalmanFilter::class.java.getDeclaredField("state").apply {
            isAccessible = true
        }
        val seededState = XcproV1State(
            altitude = 1000.0,
            climbRate = 1.0,
            accelBias = 0.0,
            verticalWind = 2.0,
            windX = 0.0,
            windY = 0.0
        )
        stateField.set(filter, seededState)

        val predictMethod = XcproV1KalmanFilter::class.java.getDeclaredMethod(
            "predict",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        predictMethod.invoke(filter, 1.0, 0.0)

        val updatedState = stateField.get(filter) as XcproV1State
        assertEquals(1001.0, updatedState.altitude, 1e-6)
        assertEquals(1.0, updatedState.climbRate, 1e-6)
        assertEquals(2.0, updatedState.verticalWind, 1e-6)
    }

    @Test
    fun update_holdsAltitudeInLevelFlight() {
        val filter = XcproV1KalmanFilter()
        filter.reset()

        var timestamp = 0L
        var lastState: XcproV1State? = null
        repeat(40) {
            timestamp += 100
            val result = filter.update(
                timestamp = timestamp,
                baroAltitude = 1200.0,
                verticalAccel = 0.0,
                gpsVerticalSpeed = 0.0,
                gpsGroundSpeed = 30.0,
                gpsTrackRad = 0.0,
                trueAirspeed = 30.0,
                airBearingRad = 0.0,
                headingDeg = 0.0,
                bankDeg = 0.0
            )
            lastState = result.state
        }

        val finalState = lastState ?: error("Filter did not produce a state")
        assertEquals(1200.0, finalState.altitude, 3.0)
    }
}
