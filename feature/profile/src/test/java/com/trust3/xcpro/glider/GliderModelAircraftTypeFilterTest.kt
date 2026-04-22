package com.trust3.xcpro.glider

import com.trust3.xcpro.common.glider.GliderAircraftTypes
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.profiles.AircraftType
import org.junit.Assert.assertEquals
import org.junit.Test

class GliderModelAircraftTypeFilterTest {
    @Test
    fun filterGliderModelsForAircraftType_returnsOnlyMatchingAircraftType() {
        val models = listOf(
            model("sailplane", GliderAircraftTypes.SAILPLANE),
            model("paraglider", GliderAircraftTypes.PARAGLIDER),
            model("hang-glider", GliderAircraftTypes.HANG_GLIDER)
        )

        assertEquals(
            listOf("sailplane"),
            filterGliderModelsForAircraftType(models, AircraftType.SAILPLANE).map { it.id }
        )
        assertEquals(
            listOf("paraglider"),
            filterGliderModelsForAircraftType(models, AircraftType.PARAGLIDER).map { it.id }
        )
        assertEquals(
            listOf("hang-glider"),
            filterGliderModelsForAircraftType(models, AircraftType.HANG_GLIDER).map { it.id }
        )
        assertEquals(
            listOf("sailplane"),
            filterGliderModelsForAircraftType(models, AircraftType.GLIDER).map { it.id }
        )
        assertEquals(
            listOf("sailplane", "paraglider", "hang-glider"),
            filterGliderModelsForAircraftType(models, null).map { it.id }
        )
    }

    private fun model(id: String, aircraftType: String): GliderModel =
        GliderModel(id = id, aircraftType = aircraftType, name = id, classLabel = "")
}
