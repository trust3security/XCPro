package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.ActivePolarSnapshot
import com.trust3.xcpro.common.glider.ActivePolarSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarPreviewUseCaseTest {

    private val useCase = PolarPreviewUseCase(
        sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = 1.25

            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
    )

    @Test
    fun manualThreePointWithoutSelectedModel_showsManualHeadlineWithoutFallbackWarning() {
        val result = useCase.resolve(
            activePolar = snapshot(
                source = ActivePolarSource.MANUAL_THREE_POINT,
                selectedModelName = null,
                effectiveModelName = "Default club"
            ),
            speedKmh = 100.0
        )

        assertEquals("3-point polar - 100 km/h", result.headline)
        assertEquals("Using 3-point polar", result.hint)
        assertEquals(1.25, result.sinkMs ?: Double.NaN, 0.0)
        assertFalse(result.showFallbackHelp)
    }

    @Test
    fun fallbackSource_showsFallbackWarning() {
        val result = useCase.resolve(
            activePolar = snapshot(
                source = ActivePolarSource.FALLBACK_MODEL,
                selectedModelName = "ASG-29 (18m)",
                effectiveModelName = "Default club"
            ),
            speedKmh = 120.0
        )

        assertEquals("Fallback active: Default club - 120 km/h", result.headline)
        assertEquals("Using default club fallback polar", result.hint)
        assertTrue(result.showFallbackHelp)
    }

    private fun snapshot(
        source: ActivePolarSource,
        selectedModelName: String?,
        effectiveModelName: String
    ): ActivePolarSnapshot = ActivePolarSnapshot(
        source = source,
        selectedModelId = selectedModelName?.lowercase()?.replace(" ", "-"),
        selectedModelName = selectedModelName,
        effectiveModelId = effectiveModelName.lowercase().replace(" ", "-"),
        effectiveModelName = effectiveModelName,
        isFallbackPolarActive = source == ActivePolarSource.FALLBACK_MODEL,
        hasThreePointPolar = source == ActivePolarSource.MANUAL_THREE_POINT,
        referenceWeightConfigured = false,
        userCoefficientsConfigured = false
    )
}
