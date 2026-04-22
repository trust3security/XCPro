package com.trust3.xcpro.glider

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.common.glider.GliderAircraftTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PolarCatalogAssetDataSourceTest {
    @Test
    fun loadModels_readsBundledJsonPolars() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val models = PolarCatalogAssetDataSource(context).loadModels()

        assertEquals(
            setOf("js1-18", "js1-21", "js3-15", "js3-18"),
            models.map { it.id }.toSet()
        )
        assertTrue(models.all { it.aircraftType == GliderAircraftTypes.SAILPLANE })
        val js118 = models.first { it.id == "js1-18" }
        assertNotNull(js118.points)
        assertTrue(js118.points!!.size >= 3)
    }
}
