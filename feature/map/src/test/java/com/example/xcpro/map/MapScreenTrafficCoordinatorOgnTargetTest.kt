package com.example.xcpro.map

import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.model.MapLocationUiModel
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnTrafficTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenTrafficCoordinatorOgnTargetTest {

    @Test
    fun onSetOgnTarget_enable_autoEnablesOverlay_andPersistsNormalizedKey() = runTest {
        val fixture = createFixture(
            scope = this,
            ognOverlayEnabled = MutableStateFlow(false)
        )
        whenever(fixture.ognTrafficFacade.setOverlayEnabled(true)).thenReturn(Unit)
        whenever(
            fixture.ognTrafficFacade.setTargetSelection(
                enabled = true,
                aircraftKey = "FLARM:AB12CD"
            )
        ).thenReturn(Unit)

        fixture.coordinator.onSetOgnTarget(" flarm:ab12cd ", enabled = true)
        advanceUntilIdle()

        verify(fixture.ognTrafficFacade).setOverlayEnabled(true)
        verify(fixture.ognTrafficFacade).setTargetSelection(
            enabled = eq(true),
            aircraftKey = eq("FLARM:AB12CD")
        )
        verify(fixture.ognTrafficFacade, never()).clearTargetSelection()
    }

    @Test
    fun onSetOgnTarget_disable_clearsSelection() = runTest {
        val fixture = createFixture(
            scope = this,
            ognOverlayEnabled = MutableStateFlow(true)
        )
        whenever(fixture.ognTrafficFacade.clearTargetSelection()).thenReturn(Unit)

        fixture.coordinator.onSetOgnTarget("FLARM:AB12CD", enabled = false)
        advanceUntilIdle()

        verify(fixture.ognTrafficFacade).clearTargetSelection()
        verify(fixture.ognTrafficFacade, never()).setTargetSelection(
            enabled = eq(true),
            aircraftKey = eq("FLARM:AB12CD")
        )
    }

    @Test
    fun bind_targetSuppressed_clearsTargetSelection() = runTest {
        val fixture = createFixture(
            scope = this,
            ognOverlayEnabled = MutableStateFlow(true),
            ognTargetEnabled = MutableStateFlow(true),
            ognTargetAircraftKey = MutableStateFlow("FLARM:DDA85C"),
            ognSuppressedTargetIds = MutableStateFlow(emptySet())
        )
        whenever(fixture.ognTrafficFacade.clearTargetSelection()).thenReturn(Unit)

        fixture.coordinator.bind()
        advanceUntilIdle()
        fixture.ognSuppressedTargetIds.value = setOf("FLARM:DDA85C")
        advanceUntilIdle()

        verify(fixture.ognTrafficFacade, times(1)).clearTargetSelection()
        coroutineContext.cancelChildren()
    }

    @Test
    fun bind_targetNotSuppressed_doesNotClearTargetSelection() = runTest {
        val fixture = createFixture(
            scope = this,
            ognOverlayEnabled = MutableStateFlow(true),
            ognTargetEnabled = MutableStateFlow(true),
            ognTargetAircraftKey = MutableStateFlow("FLARM:DDA85C"),
            ognSuppressedTargetIds = MutableStateFlow(emptySet())
        )

        fixture.coordinator.bind()
        advanceUntilIdle()
        fixture.ognSuppressedTargetIds.value = setOf("FLARM:112233")
        advanceUntilIdle()

        verify(fixture.ognTrafficFacade, never()).clearTargetSelection()
        coroutineContext.cancelChildren()
    }

    private fun createFixture(
        scope: CoroutineScope,
        ognOverlayEnabled: MutableStateFlow<Boolean>,
        ognTargetEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
        ognTargetAircraftKey: MutableStateFlow<String?> = MutableStateFlow(null),
        ognSuppressedTargetIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    ): OgnTargetCoordinatorFixture {
        val ognTrafficFacade: OgnTrafficFacade = mock()
        val adsbTrafficFacade: AdsbTrafficFacade = mock()
        whenever(adsbTrafficFacade.isStreamingEnabled).thenReturn(MutableStateFlow(false))
        val coordinator = MapScreenTrafficCoordinator(
            scope = scope,
            allowSensorStart = MutableStateFlow(true),
            isMapVisible = MutableStateFlow(true),
            ognOverlayEnabled = ognOverlayEnabled,
            adsbOverlayEnabled = MutableStateFlow(false),
            mapState = MapStateStore(initialStyleName = "Terrain"),
            mapLocation = MutableStateFlow<MapLocationUiModel?>(null),
            isFlying = MutableStateFlow(false),
            ownshipAltitudeMeters = MutableStateFlow<Double?>(null),
            ownshipIsCircling = MutableStateFlow(false),
            circlingFeatureEnabled = MutableStateFlow(false),
            adsbMaxDistanceKm = MutableStateFlow(10),
            adsbVerticalAboveMeters = MutableStateFlow(500.0),
            adsbVerticalBelowMeters = MutableStateFlow(500.0),
            rawOgnTargets = MutableStateFlow<List<OgnTrafficTarget>>(emptyList()),
            selectedOgnId = MutableStateFlow<String?>(null),
            ognTargetEnabled = ognTargetEnabled,
            ognTargetAircraftKey = ognTargetAircraftKey,
            ognSuppressedTargetIds = ognSuppressedTargetIds,
            showSciaEnabled = MutableStateFlow(false),
            showThermalsEnabled = MutableStateFlow(false),
            thermalHotspots = MutableStateFlow<List<OgnThermalHotspot>>(emptyList()),
            selectedThermalId = MutableStateFlow<String?>(null),
            rawAdsbTargets = MutableStateFlow<List<AdsbTrafficUiModel>>(emptyList()),
            selectedAdsbId = MutableStateFlow<Icao24?>(null),
            ognTrafficFacade = ognTrafficFacade,
            adsbTrafficFacade = adsbTrafficFacade,
            emitUiEffect = {}
        )

        return OgnTargetCoordinatorFixture(
            coordinator = coordinator,
            ognTrafficFacade = ognTrafficFacade,
            ognSuppressedTargetIds = ognSuppressedTargetIds
        )
    }

    private data class OgnTargetCoordinatorFixture(
        val coordinator: MapScreenTrafficCoordinator,
        val ognTrafficFacade: OgnTrafficFacade,
        val ognSuppressedTargetIds: MutableStateFlow<Set<String>>
    )
}
