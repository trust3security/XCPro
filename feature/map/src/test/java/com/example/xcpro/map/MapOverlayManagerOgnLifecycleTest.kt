package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.trail.SnailTrailManager
import com.example.xcpro.ogn.OgnAddressType
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnThermalHotspotState
import com.example.xcpro.ogn.OgnTrafficTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerOgnLifecycleTest {

    @Test
    fun updateOgnTrafficTargets_renderPathCreatesOverlay_onceAndSkipsReinitialize() = runTest {
        val map: MapLibreMap = mock()
        val trafficOverlay: OgnTrafficOverlay = mock()
        val fixture = createFixture(
            scope = this,
            ognTrafficOverlayFactory = { _, _, _ -> trafficOverlay },
            ognThermalOverlayFactory = { _ -> mock() },
            ognGliderTrailOverlayFactory = { _ -> mock() },
            adsbTrafficOverlayFactory = { _, _ -> mock() }
        )
        fixture.mapState.mapLibreMap = map

        val firstTargets = listOf(target(id = "T1", lastSeenMillis = 1_000L))
        val secondTargets = listOf(target(id = "T2", lastSeenMillis = 2_000L))

        fixture.manager.updateOgnTrafficTargets(
            targets = firstTargets,
            ownshipAltitudeMeters = 1200.0,
            altitudeUnit = AltitudeUnit.METERS,
            forceImmediate = true
        )
        fixture.manager.updateOgnTrafficTargets(
            targets = secondTargets,
            ownshipAltitudeMeters = 1200.0,
            altitudeUnit = AltitudeUnit.METERS,
            forceImmediate = true
        )

        verify(trafficOverlay, times(1)).initialize()
        verify(trafficOverlay, times(1)).render(
            targets = eq(firstTargets),
            ownshipAltitudeMeters = eq(1200.0),
            altitudeUnit = eq(AltitudeUnit.METERS),
            unitsPreferences = eq(UnitsPreferences())
        )
        verify(trafficOverlay, times(1)).render(
            targets = eq(secondTargets),
            ownshipAltitudeMeters = eq(1200.0),
            altitudeUnit = eq(AltitudeUnit.METERS),
            unitsPreferences = eq(UnitsPreferences())
        )
    }

    @Test
    fun initializeTrafficOverlays_styleRecreate_reinitsAndRendersLatestCachedData() = runTest {
        val map: MapLibreMap = mock()

        val firstTraffic: OgnTrafficOverlay = mock()
        val secondTraffic: OgnTrafficOverlay = mock()
        val firstThermal: OgnThermalOverlay = mock()
        val secondThermal: OgnThermalOverlay = mock()
        val firstTrail: OgnGliderTrailOverlay = mock()
        val secondTrail: OgnGliderTrailOverlay = mock()
        val firstAdsb: AdsbTrafficOverlay = mock()
        val secondAdsb: AdsbTrafficOverlay = mock()

        var trafficFactoryCount = 0
        var thermalFactoryCount = 0
        var trailFactoryCount = 0
        var adsbFactoryCount = 0

        val fixture = createFixture(
            scope = this,
            ognTrafficOverlayFactory = { _, _, _ ->
                when (trafficFactoryCount++) {
                    0 -> firstTraffic
                    else -> secondTraffic
                }
            },
            ognThermalOverlayFactory = { _ ->
                when (thermalFactoryCount++) {
                    0 -> firstThermal
                    else -> secondThermal
                }
            },
            ognGliderTrailOverlayFactory = { _ ->
                when (trailFactoryCount++) {
                    0 -> firstTrail
                    else -> secondTrail
                }
            },
            adsbTrafficOverlayFactory = { _, _ ->
                when (adsbFactoryCount++) {
                    0 -> firstAdsb
                    else -> secondAdsb
                }
            }
        )

        val latestTargets = listOf(target(id = "T9", lastSeenMillis = 9_000L))
        val latestThermals = listOf(thermal(id = "H9"))
        val latestTrails = listOf(trail(id = "S9"))

        fixture.manager.updateOgnTrafficTargets(
            targets = latestTargets,
            ownshipAltitudeMeters = 900.0,
            altitudeUnit = AltitudeUnit.FEET,
            forceImmediate = false
        )
        fixture.manager.updateOgnThermalHotspots(
            hotspots = latestThermals,
            forceImmediate = false
        )
        fixture.manager.updateOgnGliderTrailSegments(
            segments = latestTrails,
            forceImmediate = false
        )

        fixture.mapState.mapLibreMap = map
        fixture.manager.initializeTrafficOverlays(map)
        clearInvocations(
            firstTraffic,
            firstThermal,
            firstTrail,
            firstAdsb,
            secondTraffic,
            secondThermal,
            secondTrail,
            secondAdsb
        )

        fixture.manager.initializeTrafficOverlays(map)

        verify(firstTraffic, times(1)).cleanup()
        verify(firstThermal, times(1)).cleanup()
        verify(firstTrail, times(1)).cleanup()
        verify(firstAdsb, times(1)).cleanup()

        verify(secondTraffic, times(1)).initialize()
        verify(secondThermal, times(1)).initialize()
        verify(secondTrail, times(1)).initialize()
        verify(secondAdsb, times(1)).initialize()

        verify(secondTraffic, times(1)).render(
            targets = eq(latestTargets),
            ownshipAltitudeMeters = eq(900.0),
            altitudeUnit = eq(AltitudeUnit.FEET),
            unitsPreferences = eq(UnitsPreferences())
        )
        verify(secondThermal, times(1)).render(eq(latestThermals))
        verify(secondTrail, times(1)).render(eq(latestTrails))
    }

    @Test
    fun onMapDetached_cancelsDeferredTrafficRenderJob() = runTest {
        val map: MapLibreMap = mock()
        val trafficOverlay: OgnTrafficOverlay = mock()
        val fixture = createFixture(
            scope = this,
            ognTrafficOverlayFactory = { _, _, _ -> trafficOverlay },
            ognThermalOverlayFactory = { _ -> mock() },
            ognGliderTrailOverlayFactory = { _ -> mock() },
            adsbTrafficOverlayFactory = { _, _ -> mock() }
        )
        fixture.mapState.mapLibreMap = map
        fixture.manager.initializeTrafficOverlays(map)

        fixture.manager.setOgnDisplayUpdateMode(OgnDisplayUpdateMode.BATTERY)
        clearInvocations(trafficOverlay)

        fixture.manager.updateOgnTrafficTargets(
            targets = listOf(target(id = "T3", lastSeenMillis = 3_000L)),
            ownshipAltitudeMeters = 1000.0,
            altitudeUnit = AltitudeUnit.METERS,
            forceImmediate = false
        )
        fixture.manager.onMapDetached()

        advanceTimeBy(4_000L)
        runCurrent()

        verifyNoInteractions(trafficOverlay)
    }

    private fun createFixture(
        scope: TestScope,
        ognTrafficOverlayFactory: (MapLibreMap, Int, Boolean) -> OgnTrafficOverlay,
        ognThermalOverlayFactory: (MapLibreMap) -> OgnThermalOverlay,
        ognGliderTrailOverlayFactory: (MapLibreMap) -> OgnGliderTrailOverlay,
        adsbTrafficOverlayFactory: (MapLibreMap, Int) -> AdsbTrafficOverlay
    ): Fixture {
        val mapState = MapScreenState()
        val mapStateStore = MapStateStore(initialStyleName = "Terrain")
        val manager = MapOverlayManager(
            context = mock<Context>(),
            mapState = mapState,
            mapStateReader = mapStateStore,
            taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
            taskWaypointCountProvider = { 0 },
            stateActions = MapStateActionsDelegate(mapStateStore),
            snailTrailManager = mock<SnailTrailManager>(),
            coroutineScope = scope,
            airspaceUseCase = mock<AirspaceUseCase>(),
            waypointFilesUseCase = mock<WaypointFilesUseCase>(),
            ognTrafficOverlayFactory = ognTrafficOverlayFactory,
            ognThermalOverlayFactory = ognThermalOverlayFactory,
            ognGliderTrailOverlayFactory = ognGliderTrailOverlayFactory,
            adsbTrafficOverlayFactory = adsbTrafficOverlayFactory
        )
        return Fixture(
            manager = manager,
            mapState = mapState
        )
    }

    private fun target(
        id: String,
        lastSeenMillis: Long
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 850.0,
        trackDegrees = 90.0,
        groundSpeedMps = 35.0,
        verticalSpeedMps = 1.2,
        deviceIdHex = "ABC123",
        signalDb = -12.0,
        displayLabel = id,
        identity = null,
        rawComment = null,
        rawLine = "raw-$id",
        timestampMillis = lastSeenMillis,
        lastSeenMillis = lastSeenMillis,
        addressType = OgnAddressType.FLARM,
        addressHex = "ABC123",
        canonicalKey = "FLARM:ABC123"
    )

    private fun thermal(id: String): OgnThermalHotspot = OgnThermalHotspot(
        id = id,
        sourceTargetId = "T9",
        sourceLabel = "T9",
        latitude = -35.1,
        longitude = 149.1,
        startedAtMonoMs = 100L,
        startedAtWallMs = 1_000L,
        updatedAtMonoMs = 200L,
        updatedAtWallMs = 1_100L,
        startAltitudeMeters = 900.0,
        maxAltitudeMeters = 980.0,
        maxAltitudeAtMonoMs = 200L,
        maxClimbRateMps = 2.1,
        averageClimbRateMps = 1.4,
        averageBottomToTopClimbRateMps = 1.3,
        snailColorIndex = 12,
        state = OgnThermalHotspotState.ACTIVE
    )

    private fun trail(id: String): OgnGliderTrailSegment = OgnGliderTrailSegment(
        id = id,
        sourceTargetId = "T9",
        sourceLabel = "T9",
        startLatitude = -35.0,
        startLongitude = 149.0,
        endLatitude = -34.95,
        endLongitude = 149.05,
        colorIndex = 11,
        widthPx = 2.0f,
        timestampMonoMs = 1_000L
    )

    private data class Fixture(
        val manager: MapOverlayManager,
        val mapState: MapScreenState
    )
}
