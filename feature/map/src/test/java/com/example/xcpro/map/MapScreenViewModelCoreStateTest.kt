package com.example.xcpro.map

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.profiles.ProfileIdResolver
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelCoreStateTest : MapScreenViewModelTestBase() {

    @Test
    fun refreshWaypoints_success_updatesState() = runBlocking {
        val expected = listOf(
            WaypointData(
                name = "Test Field",
                code = "TEST",
                country = "NZ",
                latitude = -45.0,
                longitude = 170.0,
                elevation = "500m",
                style = 4,
                runwayDirection = "09/27",
                runwayLength = "1200m",
                frequency = "118.700",
                description = "Glider base"
            )
        )
        val loader = SuccessfulWaypointLoader(expected)

        val viewModel = createViewModel(waypointLoader = loader)

        drainMain()

        val state = viewModel.uiState.value
        assertEquals(expected, state.waypoints)
        assertTrue(state.isLoadingWaypoints.not())
        assertNull(state.waypointError)
    }

    @Test
    fun refreshWaypoints_failure_setsError() = runBlocking {
        val loader = FailingWaypointLoader(IllegalStateException("Failed to read waypoints"))

        val viewModel = createViewModel(waypointLoader = loader)

        drainMain()

        val state = viewModel.uiState.value
        assertTrue(state.waypoints.isEmpty())
        assertTrue(state.isLoadingWaypoints.not())
        val error = state.waypointError
        assertTrue(error is MapWaypointError.LoadFailed)
        assertEquals("Failed to read waypoints", (error as MapWaypointError.LoadFailed).recoveryHint)
    }

    @Test
    fun setMapStyle_emitsCommandAndUpdatesStore() = runBlocking {
        val viewModel = createViewModel()

        val nextStyle =
            if (viewModel.mapState.mapStyleName.value == "Satellite") "Topo" else "Satellite"
        val commandDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.mapCommands.first() }
        viewModel.setMapStyle(nextStyle)

        assertEquals(MapCommand.SetStyle(nextStyle), commandDeferred.await())
        assertEquals(nextStyle, viewModel.mapState.mapStyleName.value)
    }

    @Test
    fun setFlightMode_updatesStore() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel)

        viewModel.setFlightMode(FlightMode.THERMAL)

        assertEquals(FlightMode.THERMAL, viewModel.requestedFlightMode.value)
        assertEquals(FlightMode.THERMAL, viewModel.effectiveFlightMode.value)
        assertEquals(MapFlightModeSource.REQUESTED, viewModel.effectiveFlightModeSource.value)
    }

    @Test
    fun requestedMode_staysStickyWhenVisibilityHidesAndShowsIt() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel, activeProfileId = "pilot-a")

        viewModel.setFlightMode(FlightMode.THERMAL)
        viewModel.onProfileModeVisibilitiesChanged(
            activeProfileId = "pilot-a",
            allVisibilities = mapOf("pilot-a" to mapOf(FlightModeSelection.THERMAL to false))
        )

        assertEquals(FlightMode.THERMAL, viewModel.requestedFlightMode.value)
        assertEquals(FlightMode.CRUISE, viewModel.effectiveFlightMode.value)
        assertEquals(MapFlightModeSource.FALLBACK_CRUISE, viewModel.effectiveFlightModeSource.value)

        viewModel.onProfileModeVisibilitiesChanged(
            activeProfileId = "pilot-a",
            allVisibilities = mapOf("pilot-a" to mapOf(FlightModeSelection.THERMAL to true))
        )

        assertEquals(FlightMode.THERMAL, viewModel.requestedFlightMode.value)
        assertEquals(FlightMode.THERMAL, viewModel.effectiveFlightMode.value)
        assertEquals(MapFlightModeSource.REQUESTED, viewModel.effectiveFlightModeSource.value)
    }

    @Test
    fun runtimeOverride_isSeparateFromRequestedMode() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel)

        viewModel.setFlightMode(FlightMode.FINAL_GLIDE)
        viewModel.applyRuntimeFlightMode(FlightMode.THERMAL)

        assertEquals(FlightMode.FINAL_GLIDE, viewModel.requestedFlightMode.value)
        assertEquals(FlightMode.THERMAL, viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.THERMAL, viewModel.effectiveFlightMode.value)
        assertEquals(MapFlightModeSource.RUNTIME_OVERRIDE, viewModel.effectiveFlightModeSource.value)

        viewModel.clearRuntimeFlightModeOverride()

        assertEquals(FlightMode.FINAL_GLIDE, viewModel.requestedFlightMode.value)
        assertNull(viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.FINAL_GLIDE, viewModel.effectiveFlightMode.value)
        assertEquals(MapFlightModeSource.REQUESTED, viewModel.effectiveFlightModeSource.value)
    }

    @Test
    fun clearingRuntimeOverride_preservesRequestedMode() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel)

        viewModel.setFlightMode(FlightMode.FINAL_GLIDE)
        viewModel.applyRuntimeFlightMode(FlightMode.THERMAL)
        viewModel.clearRuntimeFlightModeOverride()

        assertEquals(FlightMode.FINAL_GLIDE, viewModel.requestedFlightMode.value)
        assertNull(viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.FINAL_GLIDE, viewModel.effectiveFlightMode.value)
    }

    @Test
    fun effectiveMode_propagatesOnlyWhenItChanges() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel, activeProfileId = "pilot-a")
        Mockito.clearInvocations(varioServiceManager)

        viewModel.setFlightMode(FlightMode.THERMAL)
        viewModel.setFlightMode(FlightMode.THERMAL)
        viewModel.onProfileModeVisibilitiesChanged(
            activeProfileId = "pilot-a",
            allVisibilities = mapOf("pilot-a" to mapOf(FlightModeSelection.THERMAL to false))
        )
        viewModel.onProfileModeVisibilitiesChanged(
            activeProfileId = "pilot-a",
            allVisibilities = mapOf("pilot-a" to mapOf(FlightModeSelection.THERMAL to false))
        )

        Mockito.verify(varioServiceManager).setFlightMode(FlightMode.THERMAL)
        Mockito.verify(varioServiceManager).setFlightMode(FlightMode.CRUISE)
        Mockito.verifyNoMoreInteractions(varioServiceManager)
        assertEquals(
            FlightModeSelection.CRUISE,
            viewModel.runtimeDependencies.flightDataManager.effectiveFlightModeSelection
        )
    }

    @Test
    fun explicitCruiseSelection_clearsRuntimeOverrideImmediately() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel)
        Mockito.clearInvocations(varioServiceManager)

        viewModel.applyRuntimeFlightMode(FlightMode.THERMAL)
        viewModel.setFlightMode(FlightMode.CRUISE)

        assertNull(viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.CRUISE, viewModel.requestedFlightMode.value)
        assertEquals(FlightMode.CRUISE, viewModel.effectiveFlightMode.value)
        Mockito.verify(varioServiceManager).setFlightMode(FlightMode.THERMAL)
        Mockito.verify(varioServiceManager).setFlightMode(FlightMode.CRUISE)
    }

    @Test
    fun explicitFinalGlideSelection_clearsRuntimeOverrideImmediately() {
        val viewModel = createViewModel()
        hydrateVisibilities(viewModel)
        Mockito.clearInvocations(varioServiceManager)

        viewModel.applyRuntimeFlightMode(FlightMode.THERMAL)
        viewModel.setFlightMode(FlightMode.FINAL_GLIDE)

        assertNull(viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.FINAL_GLIDE, viewModel.requestedFlightMode.value)
        assertEquals(FlightMode.FINAL_GLIDE, viewModel.effectiveFlightMode.value)
        Mockito.verify(varioServiceManager).setFlightMode(FlightMode.THERMAL)
        Mockito.verify(varioServiceManager).setFlightMode(FlightMode.FINAL_GLIDE)
    }

    @Test
    fun preHydrationStartup_isConservative() {
        val viewModel = createViewModel()
        Mockito.clearInvocations(varioServiceManager)

        viewModel.applyRuntimeFlightMode(FlightMode.THERMAL)

        assertEquals(listOf(FlightMode.CRUISE), viewModel.visibleFlightModes.value)
        assertEquals(FlightMode.THERMAL, viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.CRUISE, viewModel.effectiveFlightMode.value)
        Mockito.verifyNoInteractions(varioServiceManager)
    }

    @Test
    fun hiddenThermalAfterHydration_staysHiddenFromRuntimeOverride() {
        val viewModel = createViewModel()
        hydrateVisibilities(
            viewModel,
            activeProfileId = "pilot-a",
            visibilities = mapOf(FlightModeSelection.THERMAL to false)
        )
        Mockito.clearInvocations(varioServiceManager)

        viewModel.applyRuntimeFlightMode(FlightMode.THERMAL)

        assertEquals(listOf(FlightMode.CRUISE, FlightMode.FINAL_GLIDE), viewModel.visibleFlightModes.value)
        assertEquals(FlightMode.THERMAL, viewModel.runtimeFlightModeOverride.value)
        assertEquals(FlightMode.CRUISE, viewModel.effectiveFlightMode.value)
        Mockito.verifyNoInteractions(varioServiceManager)
    }

    @Test
    fun toggleDistanceCircles_updatesStore() {
        val viewModel = createViewModel()

        assertTrue(viewModel.mapState.showDistanceCircles.value.not())

        viewModel.mapStateActions.toggleDistanceCircles()

        assertTrue(viewModel.mapState.showDistanceCircles.value)
    }

    @Test
    fun updateCurrentZoom_updatesStore() {
        val viewModel = createViewModel()

        viewModel.mapStateActions.updateCurrentZoom(14.5f)

        assertEquals(14.5f, viewModel.mapState.currentZoom.value)
    }

    @Test
    fun setTarget_updatesStore() {
        val viewModel = createViewModel()

        val target = MapPoint(1.23, 4.56)

        viewModel.mapStateActions.setTarget(target, 12.0f)

        assertEquals(target, viewModel.mapState.targetLatLng.value)
        assertEquals(12.0f, viewModel.mapState.targetZoom.value)
    }

    @Test
    fun saveLocation_updatesStore() {
        val viewModel = createViewModel()

        val location = MapPoint(10.0, -20.0)

        viewModel.mapStateActions.saveLocation(location, 9.0, 180.0)

        assertEquals(location, viewModel.mapState.savedLocation.value)
        assertEquals(9.0, viewModel.mapState.savedZoom.value)
        assertEquals(180.0, viewModel.mapState.savedBearing.value)
    }

    @Test
    fun adsbSelection_tracksSelectedIdFromCurrentTargetList() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id))
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()

        assertEquals(id, viewModel.selectedAdsbId.value)
        assertEquals(id, viewModel.selectedAdsbTarget.value?.id)
    }

    @Test
    fun adsbSelection_clearsWhenSelectedTargetDisappears() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id))
        drainMain()
        viewModel.onAdsbTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedAdsbTarget.value?.id)

        adsbRepository.targets.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedAdsbId.value)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun selectedAdsbDetails_distanceRemainsOwnshipRelativeAndIndependentOfOgnTargets() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository
        )
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id, distanceMeters = 4_321.0))
        ognRepository.targets.value = listOf(sampleOgnTarget("OGN123"))
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()
        assertEquals(4_321.0, viewModel.selectedAdsbTarget.value?.distanceMeters ?: Double.NaN, 1e-6)
        assertEquals(220.0, viewModel.selectedAdsbTarget.value?.bearingDegFromUser ?: Double.NaN, 1e-6)

        ognRepository.targets.value = listOf(
            sampleOgnTarget("OGN999").copy(
                latitude = -34.0,
                longitude = 151.0,
                altitudeMeters = 2_600.0
            )
        )
        drainMain()

        assertEquals(4_321.0, viewModel.selectedAdsbTarget.value?.distanceMeters ?: Double.NaN, 1e-6)
        assertEquals(220.0, viewModel.selectedAdsbTarget.value?.bearingDegFromUser ?: Double.NaN, 1e-6)
    }

    @Test
    fun selectedAdsbDetails_carriesProximityTierTrendAndOwnshipReferenceSemantics() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(
            sampleAdsbTarget(
                id = id,
                distanceMeters = 1_750.0,
                usesOwnshipReference = false,
                proximityTier = AdsbProximityTier.NEUTRAL,
                isClosing = false,
                closingRateMps = -0.4,
                isEmergencyCollisionRisk = false
            )
        )
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()

        val selected = viewModel.selectedAdsbTarget.value ?: error("selected details missing")
        assertEquals(false, selected.usesOwnshipReference)
        assertEquals(AdsbProximityTier.NEUTRAL, selected.proximityTier)
        assertEquals(false, selected.isClosing)
        assertEquals(-0.4, selected.closingRateMps ?: Double.NaN, 1e-6)
        assertEquals(false, selected.isEmergencyCollisionRisk)
    }

    private fun hydrateVisibilities(
        viewModel: MapScreenViewModel,
        activeProfileId: String? = null,
        visibilities: Map<FlightModeSelection, Boolean> = emptyMap()
    ) {
        val resolvedProfileId = activeProfileId ?: ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID
        viewModel.setActiveProfileId(resolvedProfileId)
        drainMain()
        viewModel.onProfileModeVisibilitiesChanged(
            activeProfileId = activeProfileId,
            allVisibilities = mapOf(resolvedProfileId to visibilities)
        )
        drainMain()
    }
}
