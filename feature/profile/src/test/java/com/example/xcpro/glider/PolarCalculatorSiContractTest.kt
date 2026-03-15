package com.example.xcpro.glider

import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.glider.PolarPoint
import com.example.xcpro.common.glider.ThreePointPolar
import com.example.xcpro.common.units.UnitsConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarCalculatorSiContractTest {

    @Test
    fun sinkMs_interpolatesUsingSiPointSpeeds() {
        val model = GliderModel(
            id = "test",
            name = "test",
            classLabel = "open",
            points = listOf(
                PolarPoint.fromKmh(100.0, 1.0),
                PolarPoint.fromKmh(120.0, 2.0)
            )
        )

        val sink = PolarCalculator.sinkMs(
            airspeedMs = UnitsConverter.kmhToMs(110.0),
            model = model,
            config = GliderConfig()
        )

        assertEquals(1.5, sink, 1e-6)
    }

    @Test
    fun sinkMs_threePointPolarFromKmhFactoryMatchesExplicitSiValues() {
        val model = GliderModel(
            id = "test",
            name = "test",
            classLabel = "open",
            points = null,
            polar = null
        )
        val configFromKmh = GliderConfig(
            threePointPolar = ThreePointPolar.fromKmh(
                lowKmh = 80.0,
                lowSinkMs = 0.50,
                midKmh = 120.0,
                midSinkMs = 0.80,
                highKmh = 180.0,
                highSinkMs = 2.00
            )
        )
        val configFromSi = GliderConfig(
            threePointPolar = ThreePointPolar(
                lowMs = UnitsConverter.kmhToMs(80.0),
                lowSinkMs = 0.50,
                midMs = UnitsConverter.kmhToMs(120.0),
                midSinkMs = 0.80,
                highMs = UnitsConverter.kmhToMs(180.0),
                highSinkMs = 2.00
            )
        )

        val sinkLowFromKmh = PolarCalculator.sinkMs(UnitsConverter.kmhToMs(80.0), model, configFromKmh)
        val sinkMidFromKmh = PolarCalculator.sinkMs(UnitsConverter.kmhToMs(120.0), model, configFromKmh)
        val sinkHighFromKmh = PolarCalculator.sinkMs(UnitsConverter.kmhToMs(180.0), model, configFromKmh)

        val sinkLowFromSi = PolarCalculator.sinkMs(UnitsConverter.kmhToMs(80.0), model, configFromSi)
        val sinkMidFromSi = PolarCalculator.sinkMs(UnitsConverter.kmhToMs(120.0), model, configFromSi)
        val sinkHighFromSi = PolarCalculator.sinkMs(UnitsConverter.kmhToMs(180.0), model, configFromSi)

        assertEquals(sinkLowFromSi, sinkLowFromKmh, 1e-6)
        assertEquals(sinkMidFromSi, sinkMidFromKmh, 1e-6)
        assertEquals(sinkHighFromSi, sinkHighFromKmh, 1e-6)
    }

    @Test
    fun sinkMs_emptyPointListFallsBackWithoutCrash() {
        val model = GliderModel(
            id = "empty",
            name = "empty",
            classLabel = "club",
            points = emptyList(),
            polar = null
        )

        val sink = PolarCalculator.sinkMs(
            airspeedMs = UnitsConverter.kmhToMs(100.0),
            model = model,
            config = GliderConfig()
        )

        assertTrue(sink.isFinite())
    }

    @Test
    fun sinkMs_emptyWingLoadingPointListsFallBackWithoutCrash() {
        val model = GliderModel(
            id = "empty-light-heavy",
            name = "empty-light-heavy",
            classLabel = "club",
            pointsLight = emptyList(),
            pointsHeavy = emptyList(),
            polar = null
        )

        val sink = PolarCalculator.sinkMs(
            airspeedMs = UnitsConverter.kmhToMs(100.0),
            model = model,
            config = GliderConfig()
        )

        assertTrue(sink.isFinite())
    }

    @Test
    fun sinkMs_degenerateThreePointFallsBackToModelPolar() {
        val model = GliderModel(
            id = "test-model",
            name = "test-model",
            classLabel = "club",
            points = listOf(
                PolarPoint.fromKmh(100.0, 1.0),
                PolarPoint.fromKmh(120.0, 2.0)
            )
        )
        val config = GliderConfig(
            threePointPolar = ThreePointPolar(
                lowMs = UnitsConverter.kmhToMs(100.0),
                lowSinkMs = 0.50,
                midMs = UnitsConverter.kmhToMs(100.0),
                midSinkMs = 0.80,
                highMs = UnitsConverter.kmhToMs(100.0),
                highSinkMs = 2.00
            )
        )

        val sink = PolarCalculator.sinkMs(
            airspeedMs = UnitsConverter.kmhToMs(110.0),
            model = model,
            config = config
        )

        assertEquals(1.5, sink, 1e-6)
    }
}
