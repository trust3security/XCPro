package com.example.xcpro.hawk

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class HawkVarioEngineTest {

    @Test
    fun rejects_outlier_baro_sample() {
        val engine = HawkVarioEngine()
        val config = HawkConfig(
            baroMedianWindow = 5,
            baroOutlierThresholdM = 1.0,
            maxBaroRateMps = 100.0,
            baroInnovationRejectMps = 100.0,
            baroRejectionWindow = 5
        )

        engine.updateBaro(baroSample(altitudeM = 0.0, monoMs = 100L), config)
        val output = engine.updateBaro(baroSample(altitudeM = 50.0, monoMs = 1100L), config)

        assertTrue(output != null && !output.baroSampleAccepted)
    }

    @Test
    fun high_accel_variance_disables_accel_weight() {
        val engine = HawkVarioEngine()
        val config = HawkConfig(
            baroMedianWindow = 3,
            baroOutlierThresholdM = 1000.0,
            maxBaroRateMps = 1000.0,
            baroInnovationRejectMps = 1000.0,
            baroSupportMinMps = 0.0,
            accelVarianceWindow = 5,
            accelVarianceOkMax = 0.5,
            accelWeightMax = 1.0,
            accelIntegralClampMps = 10.0,
            accelMaxGapMs = 500L
        )

        engine.updateBaro(baroSample(altitudeM = 0.0, monoMs = 0L), config)
        engine.updateAccel(accelSample(2.0, 100L), config)
        engine.updateAccel(accelSample(-2.0, 200L), config)
        engine.updateAccel(accelSample(2.0, 300L), config)
        val output = engine.updateBaro(baroSample(altitudeM = 0.0, monoMs = 400L), config)

        assertTrue(output != null)
        val raw = output!!.vRawMps ?: 0.0
        assertTrue(abs(raw) < 0.05)
        val variance = output.accelVariance ?: 0.0
        assertTrue(variance > config.accelVarianceOkMax)
    }

    private fun baroSample(altitudeM: Double, monoMs: Long): HawkBaroSample {
        val pressure = pressureForAltitude(altitudeM)
        return HawkBaroSample(
            pressureHpa = pressure,
            monotonicTimestampMillis = monoMs
        )
    }

    private fun accelSample(value: Double, monoMs: Long): HawkAccelSample =
        HawkAccelSample(
            verticalAcceleration = value,
            isReliable = true,
            monotonicTimestampMillis = monoMs
        )

    private fun pressureForAltitude(altitudeM: Double): Double {
        val ratio = 1.0 - (altitudeM / 44330.0)
        return 1013.25 * ratio.pow(5.255)
    }
}
