package com.trust3.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.glider.UserPolarCoefficients
import com.trust3.xcpro.common.units.UnitsConverter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PolarStillAirSinkProviderTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun manualThreePointPolar_changes_authoritative_sink_and_bestLd() {
        val repository = repository()
        repository.selectModelById("js1-18")
        val provider = PolarStillAirSinkProvider(repository)
        val speedMs = UnitsConverter.kmhToMs(100.0)

        val sinkBefore = provider.sinkAtSpeed(speedMs) ?: error("expected sink")
        val bestLdBefore = provider.bestLd() ?: error("expected best L/D")

        repository.setThreePointPolar(
            ThreePointPolar.fromKmh(
                lowKmh = 85.0,
                lowSinkMs = 0.62,
                midKmh = 110.0,
                midSinkMs = 0.74,
                highKmh = 150.0,
                highSinkMs = 1.32
            )
        )

        val sinkAfter = provider.sinkAtSpeed(speedMs) ?: error("expected sink")
        val bestLdAfter = provider.bestLd() ?: error("expected best L/D")

        assertNotEquals(sinkBefore, sinkAfter)
        assertNotEquals(bestLdBefore, bestLdAfter)
    }

    @Test
    fun bugsAndBallast_change_authoritative_sink_path() {
        val repository = repository()
        repository.selectModelById("js1-18")
        val provider = PolarStillAirSinkProvider(repository)
        val speedMs = UnitsConverter.kmhToMs(100.0)

        val sinkBefore = provider.sinkAtSpeed(speedMs) ?: error("expected sink")

        repository.updateConfig {
            it.copy(
                bugsPercent = 12,
                waterBallastKg = 20.0
            )
        }

        val sinkAfter = provider.sinkAtSpeed(speedMs) ?: error("expected sink")

        assertTrue(sinkAfter > sinkBefore)
    }

    @Test
    fun referenceWeightAndUserCoefficients_areStoredButDeferredFromCurrentSinkPath() {
        val repository = repository()
        repository.selectModelById("js1-18")
        val provider = PolarStillAirSinkProvider(repository)
        val speedMs = UnitsConverter.kmhToMs(100.0)

        val sinkBefore = provider.sinkAtSpeed(speedMs) ?: error("expected sink")
        val boundsBefore = provider.iasBoundsMs() ?: error("expected bounds")

        repository.setReferenceWeightKg(525.0)
        repository.setUserCoefficients(
            UserPolarCoefficients(
                a = 1.23,
                b = -0.04,
                c = 0.001
            )
        )

        val sinkAfter = provider.sinkAtSpeed(speedMs) ?: error("expected sink")
        val boundsAfter = provider.iasBoundsMs() ?: error("expected bounds")

        assertEquals(sinkBefore, sinkAfter, 1e-9)
        assertEquals(boundsBefore.minMs, boundsAfter.minMs, 1e-9)
        assertEquals(boundsBefore.maxMs, boundsAfter.maxMs, 1e-9)
        assertTrue(repository.activePolar.value.referenceWeightConfigured)
        assertTrue(repository.activePolar.value.userCoefficientsConfigured)
    }

    private fun repository(): GliderRepository =
        GliderRepository(appContext, PolarCatalogAssetDataSource(appContext))

    private companion object {
        const val PREFS_NAME = "glider_prefs"
    }
}
