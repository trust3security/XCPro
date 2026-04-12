package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelProfileScopeTest : MapScreenViewModelTestBase() {

    @Test
    fun setActiveProfileId_updatesUnitsPreferencesForProfileScope() = runBlocking {
        val viewModel = createViewModel()
        unitsRepository.writeProfileUnits(
            profileId = "pilot-a",
            preferences = UnitsPreferences(altitude = AltitudeUnit.FEET)
        )
        unitsRepository.writeProfileUnits(
            profileId = "pilot-b",
            preferences = UnitsPreferences(altitude = AltitudeUnit.METERS)
        )

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        assertEquals(AltitudeUnit.FEET, viewModel.unitsPreferencesFlow.value.altitude)

        viewModel.setActiveProfileId("pilot-b")
        drainMain()
        assertEquals(AltitudeUnit.METERS, viewModel.unitsPreferencesFlow.value.altitude)
    }

    @Test
    fun setActiveProfileId_routesMapStylePerProfileAndAppliesProfileStyle() = runBlocking {
        val viewModel = createViewModel()

        viewModel.setActiveProfileId("pilot-a")
        viewModel.persistMapStyle(MapStyleCatalog.SATELLITE)
        awaitCondition {
            mapStyleRepository.readProfileStyle("pilot-a") == MapStyleCatalog.SATELLITE
        }

        viewModel.setActiveProfileId("pilot-b")
        viewModel.persistMapStyle(MapStyleCatalog.TERRAIN)
        awaitCondition {
            mapStyleRepository.readProfileStyle("pilot-b") == MapStyleCatalog.TERRAIN
        }

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        assertEquals(MapStyleCatalog.SATELLITE, mapStyleRepository.readProfileStyle("pilot-a"))
        assertEquals(MapStyleCatalog.SATELLITE, viewModel.mapState.mapStyleName.value)

        viewModel.setActiveProfileId("pilot-b")
        drainMain()
        assertEquals(MapStyleCatalog.TERRAIN, mapStyleRepository.readProfileStyle("pilot-b"))
        assertEquals(MapStyleCatalog.TERRAIN, viewModel.mapState.mapStyleName.value)
    }

    @Test
    fun persistMapStyle_normalizesUnknownStyleToDefaultBaseStyle() = runBlocking {
        val viewModel = createViewModel()

        viewModel.setActiveProfileId("pilot-a")
        viewModel.persistMapStyle("Map4")
        drainMain()

        assertEquals(MapStyleCatalog.defaultSelectableKey(), mapStyleRepository.readProfileStyle("pilot-a"))
    }

    @Test
    fun setActiveProfileId_updatesTrailSettingsForProfileScope() = runBlocking {
        val viewModel = createViewModel()
        trailSettingsUseCase.writeProfileSettings(
            "pilot-a",
            TrailSettings(
                length = TrailLength.SHORT,
                type = TrailType.ALTITUDE,
                windDriftEnabled = false,
                scalingEnabled = false
            )
        )
        trailSettingsUseCase.writeProfileSettings(
            "pilot-b",
            TrailSettings(
                length = TrailLength.FULL,
                type = TrailType.VARIO_2,
                windDriftEnabled = true,
                scalingEnabled = true
            )
        )

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        assertEquals(TrailLength.SHORT, viewModel.trailSettings.value.length)
        assertEquals(TrailType.ALTITUDE, viewModel.trailSettings.value.type)

        viewModel.setActiveProfileId("pilot-b")
        drainMain()
        assertEquals(TrailLength.FULL, viewModel.trailSettings.value.length)
        assertEquals(TrailType.VARIO_2, viewModel.trailSettings.value.type)
    }

    @Test
    fun setActiveProfileId_routesQnhProfileScope() = runBlocking {
        val viewModel = createViewModel()

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        viewModel.setActiveProfileId("pilot-b")
        drainMain()

        assertEquals(listOf("pilot-a", "pilot-b"), qnhRepository.activeProfileIds)
        assertEquals("pilot-b", qnhRepository.activeProfileId)
    }
}
