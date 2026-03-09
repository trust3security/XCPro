package com.example.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GliderRepositoryProfileScopeTest {

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
    fun profileSwitch_keepsIndependentGliderConfigs() {
        val repository = GliderRepository(appContext)

        repository.setActiveProfileId("default-profile")
        repository.selectModelById("js1c-18")
        repository.updateConfig { it.copy(waterBallastKg = 12.0) }

        repository.setActiveProfileId("pilot-b")
        assertNull(repository.selectedModel.value)
        repository.selectModelById("ASG-29-18")
        repository.updateConfig { it.copy(waterBallastKg = 3.0) }

        repository.setActiveProfileId("default-profile")
        assertEquals("js1c-18", repository.selectedModel.value?.id)
        assertEquals(12.0, repository.config.value.waterBallastKg, 0.0)

        repository.setActiveProfileId("pilot-b")
        assertEquals("ASG-29-18", repository.selectedModel.value?.id)
        assertEquals(3.0, repository.config.value.waterBallastKg, 0.0)
    }

    @Test
    fun legacyFallback_appliesOnlyToDefaultProfile() {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("selected_model_id", "js1c-18")
            .putString("glider_config_json", "{\"waterBallastKg\":8.0}")
            .commit()

        val repository = GliderRepository(appContext)

        assertEquals("js1c-18", repository.selectedModel.value?.id)
        assertEquals(8.0, repository.config.value.waterBallastKg, 0.0)

        repository.setActiveProfileId("pilot-b")
        assertNull(repository.selectedModel.value)
        assertEquals(0.0, repository.config.value.waterBallastKg, 0.0)
    }

    private companion object {
        const val PREFS_NAME = "glider_prefs"
    }
}
