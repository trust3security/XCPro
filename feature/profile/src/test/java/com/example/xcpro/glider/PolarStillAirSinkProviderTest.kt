package com.example.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.common.glider.UserPolarCoefficients
import com.example.xcpro.common.units.UnitsConverter
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun referenceWeightAndUserCoefficients_areStoredButDoNotChangeCurrentSinkPath() {
        val repository = GliderRepository(appContext)
        repository.selectModelById("js1c-18")
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

    private companion object {
        const val PREFS_NAME = "glider_prefs"
    }
}
