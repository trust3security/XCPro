package com.trust3.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.common.glider.ActivePolarSource
import com.trust3.xcpro.common.glider.ThreePointPolar
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
        val repository = repository()

        assertNull(repository.selectedModel.value)
        assertTrue(repository.isFallbackPolarActive.value)
        assertEquals(FALLBACK_ID, repository.effectiveModel.value.id)
        assertEquals(ActivePolarSource.FALLBACK_MODEL, repository.activePolar.value.source)
    }

    @Test
    fun selectingModelWithoutPolar_keepsFallbackPolarActive() {
        val repository = repository()

        repository.selectModelById("ASG-29-18")

        assertEquals("ASG-29-18", repository.selectedModel.value?.id)
        assertTrue(repository.isFallbackPolarActive.value)
        assertEquals(FALLBACK_ID, repository.effectiveModel.value.id)
        assertEquals(ActivePolarSource.FALLBACK_MODEL, repository.activePolar.value.source)
    }

    @Test
    fun selectingModelWithoutPolar_withThreePointDisablesFallback() {
        val repository = repository()
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
        assertEquals(ActivePolarSource.MANUAL_THREE_POINT, repository.activePolar.value.source)
    }

    @Test
    fun selectingUsableModel_disablesFallbackPolar() {
        val repository = repository()

        repository.selectModelById("js1-18")

        assertFalse(repository.isFallbackPolarActive.value)
        assertEquals("js1-18", repository.effectiveModel.value.id)
        assertEquals(ActivePolarSource.SELECTED_MODEL, repository.activePolar.value.source)
    }

    @Test
    fun manualThreePointWithoutSelectedModel_usesManualSourceWithoutFallback() {
        val repository = repository()

        repository.setThreePointPolar(
            ThreePointPolar.fromKmh(
                lowKmh = 85.0,
                lowSinkMs = 0.55,
                midKmh = 110.0,
                midSinkMs = 0.80,
                highKmh = 170.0,
                highSinkMs = 1.95
            )
        )

        val activePolar = repository.activePolar.value
        assertNull(repository.selectedModel.value)
        assertFalse(repository.isFallbackPolarActive.value)
        assertEquals(ActivePolarSource.MANUAL_THREE_POINT, activePolar.source)
        assertEquals(FALLBACK_ID, activePolar.effectiveModelId)
        assertTrue(activePolar.hasThreePointPolar)
        assertEquals(
            false,
            repository.loadProfileSnapshot("default-profile").isFallbackPolarActive
        )
    }

    private companion object {
        const val PREFS_NAME = "glider_prefs"
        const val FALLBACK_ID = "club-default-fallback"
    }

    private fun repository(): GliderRepository =
        GliderRepository(appContext, PolarCatalogAssetDataSource(appContext))
}
