package com.example.xcpro.map

import android.content.Context
import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.CoroutineScope

class MapOverlayManager(
    context: Context,
    mapState: MapScreenState,
    mapStateReader: MapStateReader,
    taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
    taskWaypointCountProvider: () -> Int,
    stateActions: MapStateActions,
    snailTrailManager: SnailTrailManager,
    coroutineScope: CoroutineScope,
    airspaceUseCase: AirspaceUseCase,
    waypointFilesUseCase: WaypointFilesUseCase,
    ognTrafficOverlayFactory: OgnTrafficOverlayFactory = TrafficOverlayFactories::createOgnTrafficOverlay,
    ognTargetRingOverlayFactory: OgnTargetRingOverlayFactory =
        TrafficOverlayFactories::createOgnTargetRingOverlay,
    ognTargetLineOverlayFactory: OgnTargetLineOverlayFactory =
        TrafficOverlayFactories::createOgnTargetLineOverlay,
    ognOwnshipTargetBadgeOverlayFactory: OgnOwnshipTargetBadgeOverlayFactory =
        TrafficOverlayFactories::createOgnOwnshipTargetBadgeOverlay,
    ognThermalOverlayFactory: OgnThermalOverlayFactory =
        TrafficOverlayFactories::createOgnThermalOverlay,
    ognGliderTrailOverlayFactory: OgnGliderTrailOverlayFactory =
        TrafficOverlayFactories::createOgnGliderTrailOverlay,
    ognSelectedThermalOverlayFactory: OgnSelectedThermalOverlayFactory =
        TrafficOverlayFactories::createOgnSelectedThermalOverlay,
    adsbTrafficOverlayFactory: AdsbTrafficOverlayFactory =
        TrafficOverlayFactories::createAdsbTrafficOverlay,
    monoTimeMs: () -> Long = TimeBridge::nowMonoMs
) : MapOverlayManagerRuntime(
    context = context,
    taskRenderSyncCoordinator = taskRenderSyncCoordinator,
    coroutineScope = coroutineScope,
    trafficRuntimeState = MapOverlayRuntimeStateAdapter(mapState),
    forecastWeatherRuntimeState = MapForecastWeatherOverlayRuntimeStateAdapter(mapState),
    ognTrafficOverlayFactory = ognTrafficOverlayFactory,
    ognTargetRingOverlayFactory = ognTargetRingOverlayFactory,
    ognTargetLineOverlayFactory = ognTargetLineOverlayFactory,
    ognOwnshipTargetBadgeOverlayFactory = ognOwnshipTargetBadgeOverlayFactory,
    ognThermalOverlayFactory = ognThermalOverlayFactory,
    ognGliderTrailOverlayFactory = ognGliderTrailOverlayFactory,
    ognSelectedThermalOverlayFactory = ognSelectedThermalOverlayFactory,
    adsbTrafficOverlayFactory = adsbTrafficOverlayFactory,
    nowMonoMs = monoTimeMs
) {
    private val baseOpsDelegate = MapOverlayManagerRuntimeBaseOpsDelegate(
        mapStateReader = mapStateReader,
        taskRenderSyncCoordinator = taskRenderSyncCoordinator,
        taskWaypointCountProvider = taskWaypointCountProvider,
        stateActions = stateActions,
        coroutineScope = coroutineScope,
        refreshAirspaceFn = { map ->
            loadAndApplyAirspace(map, airspaceUseCase)
        },
        refreshWaypointsFn = { map ->
            val (files, checks) = waypointFilesUseCase.loadWaypointFiles()
            loadAndApplyWaypoints(context, map, files, checks)
        }
    )

    private val lifecyclePort = MapOverlayRuntimeMapLifecycleDelegate(
        context = context,
        mapState = mapState,
        baseOpsDelegate = baseOpsDelegate,
        taskRenderSyncCoordinator = taskRenderSyncCoordinator,
        initializeTrafficOverlaysFn = ::initializeTrafficOverlaysRuntime,
        forecastOnMapStyleChanged = ::handleForecastMapStyleChangedRuntime,
        forecastOnInitialize = ::handleForecastInitializeRuntime,
        bringTrafficOverlaysToFront = ::bringTrafficOverlaysToFrontRuntime,
        snailTrailManager = snailTrailManager
    )

    private val statusReporter = MapOverlayRuntimeStatusCoordinator(
        mapState = mapState,
        showDistanceCircles = { mapStateReader.showDistanceCircles.value },
        taskWaypointCount = taskWaypointCountProvider,
        ognStatusSnapshot = ::ognStatusSnapshotRuntime,
        latestAdsbTargetsCount = ::latestAdsbTargetsCountRuntime,
        runtimeCounters = ::runtimeCountersSnapshot,
        forecastWeatherStatus = ::forecastWeatherStatusRuntime
    )

    init {
        attachShellPorts(
            lifecyclePort = lifecyclePort,
            statusReporter = statusReporter
        )
    }
}
