package com.example.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.common.glider.ThreePointPolar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GliderRepositoryFallbackTest {

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
    fun defaultState_usesFallbackPolar() {
        val repository = GliderRepository(appContext)

        assertNull(repository.selectedModel.value)
        assertTrue(repository.isFallbackPolarActive.value)
        assertEquals(FALLBACK_ID, repository.effectiveModel.value.id)
    }

    @Test
    fun selectingModelWithoutPolar_keepsFallbackPolarActive() {
        val repository = GliderRepository(appContext)

        repository.selectModelById("ASG-29-18")

        assertEquals("ASG-29-18", repository.selectedModel.value?.id)
        assertTrue(repository.isFallbackPolarActive.value)
        assertEquals(FALLBACK_ID, repository.effectiveModel.value.id)
    }

    @Test
    fun selectingModelWithoutPolar_withThreePointDisablesFallback() {
        val repository = GliderRepository(appContext)
        repository.selectModelById("ASG-29-18")
        repository.setThreePointPolar(
            ThreePointPolar.fromKmh(
                lowKmh = 80.0,
                lowSinkMs = 0.50,
                midKmh = 120.0,
                midSinkMs = 0.80,
                highKmh = 180.0,
                highSinkMs = 2.00
            )
        )

        assertFalse(repository.isFallbackPolarActive.value)
        assertEquals("ASG-29-18", repository.effectiveModel.value.id)
    }

    @Test
    fun selectingUsableModel_disablesFallbackPolar() {
        val repository = GliderRepository(appContext)

        repository.selectModelById("js1c-18")

        assertFalse(repository.isFallbackPolarActive.value)
        assertEquals("js1c-18", repository.effectiveModel.value.id)
    }

    private companion object {
        const val PREFS_NAME = "glider_prefs"
        const val FALLBACK_ID = "club-default-fallback"
    }
}
