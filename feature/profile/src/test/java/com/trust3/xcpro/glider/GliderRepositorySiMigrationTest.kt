package com.trust3.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.units.UnitsConverter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GliderRepositorySiMigrationTest {

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
    fun load_ignoresLegacyIasFieldsAndMigratesLegacyThreePointKmh() {
        val legacyJson = """
            {
              "pilotAndGearKg": 92.0,
              "iasMinKmh": 85.0,
              "iasMaxKmh": 190.0,
              "threePointPolar": {
                "lowKmh": 75.0,
                "lowSinkMs": 0.55,
                "midKmh": 110.0,
                "midSinkMs": 0.75,
                "highKmh": 170.0,
                "highSinkMs": 1.85
              }
            }
        """.trimIndent()

        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONFIG_JSON, legacyJson)
            .commit()

        val repository = repository()
        val config = repository.config.value

        val threePointPolar = config.threePointPolar
        assertNotNull(threePointPolar)
        assertEquals(UnitsConverter.kmhToMs(75.0), threePointPolar?.lowMs ?: Double.NaN, 1e-6)
        assertEquals(UnitsConverter.kmhToMs(110.0), threePointPolar?.midMs ?: Double.NaN, 1e-6)
        assertEquals(UnitsConverter.kmhToMs(170.0), threePointPolar?.highMs ?: Double.NaN, 1e-6)
        assertEquals(75.0, threePointPolar?.lowKmh ?: Double.NaN, 1e-6)
        assertEquals(110.0, threePointPolar?.midKmh ?: Double.NaN, 1e-6)
        assertEquals(170.0, threePointPolar?.highKmh ?: Double.NaN, 1e-6)
    }

    @Test
    fun save_persistsCanonicalThreePointSiFieldsAndDoesNotRewriteIasFields() {
        val repository = repository()
        val targetPolar = ThreePointPolar.fromKmh(
            lowKmh = 70.0,
            lowSinkMs = 0.52,
            midKmh = 120.0,
            midSinkMs = 0.78,
            highKmh = 180.0,
            highSinkMs = 2.05
        )

        repository.updateConfig {
            it.copy(
                threePointPolar = targetPolar
            )
        }

        val persistedJson = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CONFIG_JSON, null)
        assertNotNull(persistedJson)
        assertFalse(persistedJson!!.contains("\"iasMinMs\""))
        assertFalse(persistedJson.contains("\"iasMaxMs\""))
        assertFalse(persistedJson.contains("\"iasMinKmh\""))
        assertFalse(persistedJson.contains("\"iasMaxKmh\""))
        assertTrue(persistedJson.contains("\"lowMs\""))
        assertFalse(persistedJson.contains("\"lowKmh\""))

        val reloaded = repository().config.value
        assertEquals(targetPolar.lowMs, reloaded.threePointPolar?.lowMs ?: Double.NaN, 1e-6)
        assertEquals(targetPolar.midMs, reloaded.threePointPolar?.midMs ?: Double.NaN, 1e-6)
        assertEquals(targetPolar.highMs, reloaded.threePointPolar?.highMs ?: Double.NaN, 1e-6)
    }

    private fun repository(): GliderRepository =
        GliderRepository(appContext, PolarCatalogAssetDataSource(appContext))

    private companion object {
        const val PREFS_NAME = "glider_prefs"
        const val KEY_CONFIG_JSON = "glider_config_json"
    }
}
