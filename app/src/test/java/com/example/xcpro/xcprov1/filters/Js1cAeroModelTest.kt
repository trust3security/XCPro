package com.example.xcpro.xcprov1.filters

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

class Js1cAeroModelTest {

    private val referenceWingLoading = Js1cAeroModel.referenceWingLoading()

    @Test
    fun sinkRate_matchesManufacturerPolarAtReferenceWeight() {
        assertApproximately(0.52, Js1cAeroModel.sinkRate(80.0 / 3.6, referenceWingLoading))
        assertApproximately(0.84, Js1cAeroModel.sinkRate(120.0 / 3.6, referenceWingLoading))
        assertApproximately(1.59, Js1cAeroModel.sinkRate(160.0 / 3.6, referenceWingLoading))
    }

    @Test
    fun sinkRate_scalesWithWingLoading() {
        val reference = Js1cAeroModel.sinkRate(120.0 / 3.6, referenceWingLoading)
        val heavyWingLoading = Js1cAeroModel.defaultWingLoading()
        val heavy = Js1cAeroModel.sinkRate(120.0 / 3.6, heavyWingLoading)

        val expectedScale = sqrt(heavyWingLoading / referenceWingLoading)
        assertApproximately(reference * expectedScale, heavy)
    }

    @Test
    fun circleSink_increasesSinkWithBankAngle() {
        val tas = 100.0 / 3.6
        val base = Js1cAeroModel.sinkRate(tas, referenceWingLoading)
        val bank45 = Js1cAeroModel.circleSink(tas, 45.0, referenceWingLoading)

        val loadFactor = 1.0 / cos(Math.toRadians(45.0))
        val expected = base * (loadFactor.pow(1.5) - 1.0)
        assertApproximately(expected, bank45)
    }

    private fun assertApproximately(trueValue: Double, actual: Double, tolerance: Double = 0.1) {
        assertEquals(trueValue, actual, tolerance)
    }
}
