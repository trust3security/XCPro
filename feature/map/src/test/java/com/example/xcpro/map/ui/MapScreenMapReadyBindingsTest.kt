package com.example.xcpro.map.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.SelectedOgnThermalContext
import com.example.xcpro.map.model.MapLocationUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapScreenMapReadyBindingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mapReadyEvent_delegatesAppliesConfigAndIncrementsEpochOncePerEvent() {
        val mapRuntimeController = mock<MapRuntimeController>()
        val overlayManager = mock<MapOverlayManager>()
        val map = mock<MapLibreMap>()
        val trafficOverlayRuntimeInputs = sampleTrafficOverlayRuntimeInputs()
        var bindings: MapScreenMapReadyBindings? = null

        composeTestRule.setContent {
            bindings = rememberMapScreenMapReadyBindings(
                mapRuntimeController = mapRuntimeController,
                overlayManager = overlayManager,
                trafficOverlayRuntimeInputs = trafficOverlayRuntimeInputs
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            assertEquals(0, bindings!!.watchedPilotFocusEpoch)
            bindings!!.onMapReady(map)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            verify(mapRuntimeController, times(1)).onMapReady(map)
            verify(overlayManager, times(1)).setOgnDisplayUpdateMode(OgnDisplayUpdateMode.BALANCED)
            verify(overlayManager, times(1)).setOgnIconSizePx(72)
            verify(overlayManager, times(1)).setAdsbIconSizePx(44)
            verify(overlayManager, times(1)).setAdsbEmergencyFlashEnabled(true)
            verify(overlayManager, times(1)).setAdsbDefaultMediumUnknownIconEnabled(false)
            assertEquals(1, bindings!!.watchedPilotFocusEpoch)
            bindings!!.onMapReady(map)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            verify(mapRuntimeController, times(2)).onMapReady(map)
            verify(overlayManager, times(2)).setOgnDisplayUpdateMode(OgnDisplayUpdateMode.BALANCED)
            verify(overlayManager, times(2)).setOgnIconSizePx(72)
            verify(overlayManager, times(2)).setAdsbIconSizePx(44)
            verify(overlayManager, times(2)).setAdsbEmergencyFlashEnabled(true)
            verify(overlayManager, times(2)).setAdsbDefaultMediumUnknownIconEnabled(false)
            assertEquals(2, bindings!!.watchedPilotFocusEpoch)
        }
    }

    private fun sampleTrafficOverlayRuntimeInputs(): MapTrafficOverlayRuntimeInputs {
        return MapTrafficOverlayRuntimeInputs(
            ognTargets = MutableStateFlow(emptyList()),
            ognOverlayEnabled = MutableStateFlow(true),
            ognIconSizePx = MutableStateFlow(72),
            ognDisplayUpdateMode = MutableStateFlow(OgnDisplayUpdateMode.BALANCED),
            ognThermalHotspots = MutableStateFlow(emptyList()),
            showOgnSciaEnabled = MutableStateFlow(false),
            ognTargetEnabled = MutableStateFlow(false),
            ognResolvedTarget = MutableStateFlow(null),
            showOgnThermalsEnabled = MutableStateFlow(false),
            ognGliderTrailSegments = MutableStateFlow(emptyList()),
            overlayOwnshipAltitudeMeters = MutableStateFlow(null),
            ognAltitudeUnit = MutableStateFlow(AltitudeUnit.METERS),
            adsbTargets = MutableStateFlow(emptyList()),
            adsbOverlayEnabled = MutableStateFlow(true),
            adsbIconSizePx = MutableStateFlow(44),
            adsbEmergencyFlashEnabled = MutableStateFlow(true),
            adsbDefaultMediumUnknownIconEnabled = MutableStateFlow(false),
            selectedOgnTargetKey = MutableStateFlow<String?>(null),
            selectedAdsbTargetId = MutableStateFlow<Icao24?>(null),
            selectedOgnThermalContext = MutableStateFlow<SelectedOgnThermalContext?>(null),
            unitsPreferences = MutableStateFlow(defaultUnitsPreferences()),
            currentLocation = MutableStateFlow<MapLocationUiModel?>(null)
        )
    }

    private fun defaultUnitsPreferences(): UnitsPreferences = UnitsPreferences(
        altitude = AltitudeUnit.METERS,
        speed = SpeedUnit.KILOMETERS_PER_HOUR,
        distance = DistanceUnit.KILOMETERS
    )
}
