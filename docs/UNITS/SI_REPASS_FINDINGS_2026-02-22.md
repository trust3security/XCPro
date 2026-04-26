# SI Re-pass Findings

Date: 2026-02-22
Status: Updated (Run 46 non-`#12` `enforce_rules` caveat closeout complete)

## Scope
Deep re-pass across:
- `feature/map/src/main/java/com/trust3/xcpro/sensors`
- `feature/map/src/main/java/com/trust3/xcpro/adsb`
- `feature/map/src/main/java/com/trust3/xcpro/ogn`
- `feature/map/src/main/java/com/trust3/xcpro/replay`
- `feature/map/src/main/java/com/trust3/xcpro/tasks`
- `feature/map/src/main/java/com/trust3/xcpro/glider`
- `core/common/src/main/java/com/trust3/xcpro/common/glider`
- `dfcards-library/src/main/java/com/trust3/xcpro/common/units`

## Compliance Verdict
Not compliant.

The codebase is SI-strong in flight/fusion and traffic flows, but task/AAT/racing legacy plus polar internals still violate strict SI-only internal calculation policy.

## What Was Confirmed Clean
1. Core SI wrappers/conversions are correctly centralized.
2. Flight/sensor fusion internals are SI-first (`m`, `m/s`, `hPa`).
3. ADS-B and OGN keep internal distance state in meters; km is mostly boundary-only.
4. Replay converts external IAS/TAS km/h inputs to m/s before fusion, and movement snapshot distance contract fix remains in place.

## Delta: Missed in First Pass

### 1) AAT path optimizer has hard meter-vs-km logic errors
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPathOptimizerSupport.kt:11`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPathOptimizerSupport.kt:35`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPathOptimizerSupport.kt:107`

`calculatePathDistance` sums `AATMathUtils.calculateDistance` (km), but compares against `OPTIMIZATION_TOLERANCE_METERS` and target distances treated as meters.

### 2) AAT path optimizer target distance math uses meter labels against km distances
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPathOptimizer.kt:24`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPathOptimizer.kt:63`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPathOptimizer.kt:136`

Variables named `targetDistanceMeters` and `targetDistance` are compared against path distances derived from km-returning functions.

### 3) AAT flight path validator compares km distances to meter thresholds
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:61`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:66`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:92`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:97`

Distance is computed via km-returning utility and checked against meter thresholds like `+ 100.0` and `+ 50.0`.

### 4) AAT performance model mismatch: km written to meters field
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskPerformanceSupport.kt:41`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATResult.kt:177`

`distanceFromCenter` is documented as meters but assigned with `AATMathUtils.calculateDistance` (km).

## Delta: Missed in Second Pass

### 1) `AATDistanceCalculator` publishes km into models documented as meters
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:27`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:62`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:96`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:119`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:188`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:250`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATTask.kt:195`

`AATDistanceCalculator` methods are documented/consumed as meters, but `calculateTotalPathDistance` sums `AATMathUtils.calculateDistance` (km). This also corrupts `targetDistance` clamping (`meters` target clamped by km min/max).

### 2) `AATTaskDisplayGeometryBuilder` passes meter radii/lengths into a km geodesic API
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:14`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:23`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:79`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:101`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:130`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATMathUtils.kt:113`

`AATTask.start/finish` geometry fields are meters, but builder forwards them directly to `calculatePointAtBearing`, whose distance parameter is kilometers.

### 3) Sector geometry support has broad km-vs-meter contamination beyond `isInsideArea`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:18`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:37`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:41`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:74`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:102`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:158`

Distance-to-center is computed in km and compared with meter radii. Meter radii are also passed directly into km-based point generation.

### 4) Circle area bug scope is wider than previously recorded
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:67`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:71`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:95`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:181`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:188`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:195`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:273`

The earlier finding captured only `isInsideArea`; additional methods (boundary point generation, line-circle intersections, boundary distance) also mix km and meters.

### 5) Sector calculator also leaks km values in credited-fix/optimal-touch logic
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaCalculator.kt:102`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaCalculator.kt:125`

`calculateCreditedFix` and `calculateOptimalTouchPoint` continue operating on km-returning values while area radii are meters.

## Delta: Missed in Third Pass

### 1) Racing optimal start-line crossing width uses km as meters
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt:228`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt:229`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt:195`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt:202`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingGeometryUtils.kt:166`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingGeometryUtils.kt:263`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt:19`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt:74`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt:125`

`RacingWaypoint.gateWidth` is documented in kilometers, but `calculateOptimalLineCrossingPoint` forwards it to a meter-based destination routine without conversion. This shrinks effective line width by 1000x in the optimal-start path and corrupts distance-to-next computation for start-line selection UI.

## Delta: Missed in Fourth Pass

### 1) AAT start/finish cylinder radius is halved in multiple runtime paths
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATStartPointSelector.kt:91`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATFinishPointSelector.kt:101`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATPointTypeConfigurator.kt:79`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATRadiusAuthority.kt:153`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATRadiusAuthority.kt:154`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:162`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:185`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt:184`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATValidationBridge.kt:175`

Start/finish cylinder fields are labeled and stored as radius (km/meters), but renderer/geometry/validation conversion paths divide authoritative radius by 2.0. This introduces a 2x scale error for cylinder geometry/validation behavior.

## Delta: Missed in Fifth Pass

### 1) Replay runtime movement snapshot stores speed in a distance field
- `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayRuntimeInterpolator.kt:53`
- `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayRuntimeInterpolator.kt:158`
- `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayRuntimeInterpolator.kt:164`
- `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayHeadingResolver.kt:15`
- `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayMath.kt:12`
- `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayMath.kt:46`

`MovementSnapshot.distanceMeters` is a meter-valued field, but `ReplayRuntimeInterpolator` assigns it from `speedMs` (`m/s`). `ReplayHeadingResolver` then compares this value against `minDistanceM` (meters), producing unit-invalid gating for heading reuse.

## Delta: Missed in Sixth Pass

### 1) AAT quick-validation finish checks have the same km-vs-meter defect and were under-scoped
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:202`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:207`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:213`

`validateFinish` computes distance with `AATMathUtils.calculateDistance` (km), then compares that value against meter-based contracts (`lineLength`, `radius`) plus meter tolerances (`+ 100.0`, `+ 50.0`). Prior findings called out the same defect family in area/start/start-finish quick checks, but this finish path was not explicitly captured.

## Delta: Missed in Seventh Pass

### 1) Distance-circles UI hard-codes metric labels and ignores unit preference boundary
- `feature/map/src/main/java/com/trust3/xcpro/map/DistanceCirclesCanvas.kt:134`
- `feature/map/src/main/java/com/trust3/xcpro/map/DistanceCirclesCanvas.kt:138`
- `feature/map/src/main/java/com/trust3/xcpro/map/DistanceCirclesCanvas.kt:140`
- `feature/map/src/main/java/com/trust3/xcpro/map/DistanceCirclesCanvas.kt:143`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt:297`

`DistanceCirclesCanvas` formats labels as `km`/`m` directly and is rendered from `DistanceCirclesLayer` without any `UnitsPreferences` input, so NM/mi preferences are not applied at this output boundary.

### 2) Task UI distance outputs remain km-only across multiple production surfaces
- `feature/map/src/main/java/com/trust3/xcpro/tasks/CommonTaskComponents.kt:40`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/CommonTaskComponents.kt:51`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingManageBTTab.kt:82`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATManageContent.kt:41`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/BottomSheetState.kt:116`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/ui/RacingStartPointSelector.kt:183`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/ui/RacingTurnPointSelector.kt:247`

Task stats/minimized-indicator/selector paths format live and nominal distances as `km` text directly and pass `distanceKm` values rather than SI distances through `UnitsFormatter`, bypassing the units-preference boundary.

## Previously Known but Re-confirmed
1. AAT quick validation compares km distances to meter thresholds.
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:101`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:103`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:174`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:303`

2. AAT safety validator divides a km result by 1000 again.
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATTaskSafetyValidator.kt:16`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATTaskSafetyValidator.kt:17`

3. Circle/sector area calculators compare km distances to meter radii.
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:21`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:31`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaCalculator.kt:18`

4. Legacy task manager/coordinator contracts are still km-based.
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt:149`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt:118`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt:74`

5. Glider polar internals are km/h-based data contracts.
- `core/common/src/main/java/com/trust3/xcpro/common/glider/GliderModels.kt:4`
- `feature/map/src/main/java/com/trust3/xcpro/glider/PolarCalculator.kt:16`

## Re-pass #9 Run 1 Closures

### 1) Racing optimal start-line width km->m defect fixed
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingGeometryUtils.kt`
- `feature/map/src/test/java/com/trust3/xcpro/tasks/TaskManagerCoordinatorTest.kt`

`RacingWaypoint.gateWidth` (km contract) is now converted to meters before optimal start-line geometry calls.
Regression test verifies default 10km start-line produces ~5km half-width crossing offset.

### 2) Replay `MovementSnapshot.distanceMeters` contract fixed
- `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayRuntimeInterpolator.kt`
- `feature/map/src/test/java/com/trust3/xcpro/replay/ReplayRuntimeInterpolatorTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/replay/ReplayHeadingResolverTest.kt`

Runtime interpolation now stores segment distance in meters (not speed) in `distanceMeters`.
Replay heading resolver regression covers reuse behavior under low-distance movement.

### 3) Distance boundary outputs now honor units preference
- `feature/map/src/main/java/com/trust3/xcpro/map/DistanceCirclesCanvas.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/CommonTaskComponents.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/BottomSheetState.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/ui/RacingStartPointSelector.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/ui/RacingTurnPointSelector.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/task/MapTaskScreenUi.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskTopDropdownPanel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/ManageBTTabRouter.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingManageBTTab.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATManageContent.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATManageBTTab.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointList.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/ui/RacingTaskPointTypeSelector.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt`

Task/circle distance display paths now format from SI meters through `UnitsFormatter` with `UnitsPreferences` wiring.

## Re-pass #10 Run 1 Closures

### 1) AAT start/finish cylinder 2x scale defect fixed
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATValidationBridge.kt:175`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:162`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:168`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:185`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt:184`

`AATWaypoint.getAuthorityRadius()` is already authoritative radius in km. Runtime paths that divided this value by `2.0` have been corrected to use direct radius values. This removes the 2x shrink defect in start/finish cylinder rendering, finish-edge path geometry, and validation conversion.

## Re-pass #11 Run 2 Closures

### 1) Task manager/coordinator distance contracts moved to explicit SI-first APIs
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskTypeCoordinatorDelegate.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/AATCoordinatorDelegate.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/RacingCoordinatorDelegate.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
- `feature/map/src/test/java/com/trust3/xcpro/tasks/TaskManagerCoordinatorTest.kt`

Distance APIs now expose meter-first contracts (`*Meters`) with explicit km wrappers (`*Km`) and deprecated ambiguous wrappers retained for compatibility. Coordinator/delegate internals now aggregate and route SI distances in meters, removing prior implicit km assumptions in manager/coordinator boundaries.

### 2) Residual risk after Run 2 (resolved in Run 3)
- Deeper racing helper/turnpoint internals were still km-native after Re-pass #11 and required phase-3 normalization.

## Re-pass #12 Run 3 Closures

### 1) Racing helper/turnpoint/navigation/boundary distance math migrated to meter-first contracts
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingGeometryUtils.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTask.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskValidator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineSupport.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/boundary/RacingBoundaryCrossingPlanner.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/boundary/RacingBoundaryCrossingMath.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/TurnPointInterfaces.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/CylinderCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/KeyholeCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FAIQuadrantCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/StartLineCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/StartLineDisplay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/CylinderDisplay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/KeyholeDisplay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FAIQuadrantDisplay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FAIStartSectorDisplay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingGeometryCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskStorage.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
- `deleted map replay route helper`

Core change: internal racing distance calculations now use `haversineDistanceMeters` end-to-end; the legacy `haversineDistance` km helper is retained as deprecated compatibility only.

### 2) Meter-contract regression coverage expanded
- `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/RacingGeometryUtilsTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/boundary/RacingBoundaryCrossingPlannerTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/tasks/TaskManagerCoordinatorTest.kt`

Tests now assert meter-native start-line offset and boundary crossing distances without km-to-meter multiplication in assertions.

## Re-pass #13 Run 4 Findings (New)

### 1) Root contract leak: shared task waypoint radius remains km-native
- `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt:29`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt:33`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/AATTaskWaypointCodec.kt:38`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/AATTaskWaypointCodec.kt:69`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt:142`

`TaskWaypoint.customRadius` is documented and used as km. This keeps km semantics in core task contracts and forces repeated km->m conversion spread.

### 2) AAT core geodesic APIs are still km-first
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATMathUtils.kt:16`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATMathUtils.kt:32`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATMathUtils.kt:54`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt:30`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt:33`

Even with several meter wrappers added elsewhere, these core APIs still define distance contracts in km, so callers continue to mix conversions.

### 3) Additional AAT interaction/editing paths still km-native
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/gestures/AatGestureHandler.kt:28`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/map/AATEditModeState.kt:56`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/map/AATEditModeState.kt:148`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/map/AATAreaTapDetector.kt:21`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/map/AATMovablePointStrategySupport.kt:18`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt:17`

### 4) Observation-zone resolver still starts from km radius fields
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskObservationZoneResolver.kt:88`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskObservationZoneResolver.kt:93`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskObservationZoneResolver.kt:133`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskObservationZoneResolver.kt:136`

### 5) Residual ad-hoc racing km->m conversions remain
- `deleted map replay anchor helper:177`
- `deleted map replay anchor helper:251`
- `deleted map replay anchor helper:268`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt:258`

### 6) OGN policy/helper layer remains km-first
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnSubscriptionPolicy.kt:24`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt:412`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt:564`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt:130`

### 7) Minor correctness cleanup still needed
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/map/AATEditModeState.kt:60`

`0.00001` km is approximately 1 cm, while comment claims approximately 1 meter.

### 8) Legacy dead helper still present
- `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt:9`

No active call sites found in production paths.

## Compliance Statement
- Internal SI compliance for all calculations: failed.
- Partial compliance (flight/traffic/replay core): passed.
- Full compliance now primarily depends on boundary adapter conversion-test hardening and a policy decision on polar model storage units; transitional km compatibility wrappers also remain to be removed.

## Re-pass #16 Run 7 Closures

### 1) OGN unused km compatibility wrappers removed
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnSubscriptionPolicy.kt`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnSubscriptionPolicyTest.kt`

Removed production-unused km helpers to prevent internal contract drift:
- `OgnSubscriptionPolicy.haversineKm`
- `OgnSubscriptionPolicy.shouldReconnectByCenterMove`
- `isWithinReceiveRadiusKm`

### 2) Additional AAT meter-contract cleanup
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATTaskSafetyValidator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATFlightPathValidator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaCalculator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt`

Closed remaining meter-labeled assignment drift by replacing km-returning helper usage with meter-first helper calls in these paths.

### 3) Static SI guardrail expansion
- `scripts/ci/enforce_rules.ps1`

Added enforcement checks for:
- km-helper promotion into meter contracts in AAT internals,
- replay movement field contract (`distanceMeters = speedMs`),
- OGN km-helper reintroduction,
- hard-coded non-SI distance-unit labels in shared distance display surfaces.

### 4) Verification evidence (Run 7)
All required gates passed after the above fixes:
- `enforceRules`
- `testDebugUnitTest`
- `assembleDebug`
- `:app:connectedDebugAndroidTest`
- `connectedDebugAndroidTest`

### 5) Remaining deferred items
- Polar model storage contract decision (`km/h` source representation vs SI-normalized storage) is still open.
- Broad removal of remaining compatibility wrappers in task/AAT/racing APIs is deferred to a dedicated compatibility-cut pass to avoid unbounded churn in this run.

## Re-pass #17 Findings (New)

### 1) AAT radius authority is still km-canonical in internal contracts
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATRadiusAuthority.kt:41`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATRadiusAuthority.kt:146`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATRadiusAuthority.kt:197`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATRadiusAuthority.kt:238`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt:104`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt:105`

`AATRadiusAuthority` still defines and returns radius in km as the canonical API (`getRadiusForRole`, `getRadiusForWaypoint`, `getAuthorityRadius`) and then converts to meters at call sites. This keeps dual-unit internal task contracts alive across manager/renderer/validation/persistence paths.

### 2) Core/racing radius contracts remain dual-unit and km-biased
- `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt:29`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt:39`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParams.kt:147`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParams.kt:167`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt:19`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt:21`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt:24`

`TaskWaypoint.customRadius` and `resolvedCustomRadiusKm` remain active compatibility surfaces, `RacingWaypointCustomParams` stores km-native values with unit-ambiguous key names, and `RacingWaypoint` keeps km as the base unit with meter accessors layered on top.

### 3) AAT/racing manager distance APIs are still km-first in internal baselines
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt:144`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt:146`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt:295`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultAATTaskEngine.kt:186`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt:111`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt:290`

`AATTaskManager.calculateAATDistanceMeters` and `RacingTaskManager.calculateRacingDistanceMeters` still derive meters from km-primary functions. `DefaultAATTaskEngine.calculateTaskDistanceMeters` still computes via `calculateDistanceKm * 1000.0`.

### 4) Interactive AAT distance subsystem remains km-native and unsuffixed
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATGeoMath.kt:32`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATGeoMath.kt:56`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATInteractiveDistanceCalculator.kt:77`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATInteractiveDistanceCalculator.kt:140`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATInteractiveModels.kt:9`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATInteractiveModels.kt:31`

`AATInteractiveTaskDistance.totalDistance` and segment `distance` are km-valued without unit suffixes, and the calculator uses km geodesic helpers. This is an internal contract leak and a future regression risk.

### 5) AAT scoring speed contracts remain km/h in internal models/calculators
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATSpeedCalculator.kt:25`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATSpeedCalculator.kt:32`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/calculations/AATSpeedCalculator.kt:223`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATResult.kt:17`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATResult.kt:197`

`AATSpeedCalculator` and `AATResult`/`AATSpeedAnalysis` remain km/h-native internally (speed fields and arithmetic), which conflicts with strict SI-internal unit policy for task/scoring logic.

### 6) Wrapper leakage persists in coordinator/use-case/viewmodel surfaces
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt:221`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt:73`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt:124`

`*Km` APIs are still published in coordinator/use-case/viewmodel layers; grep pass indicates these wrappers currently have no external call sites and are candidates for immediate compatibility-cut removal.

Re-pass #17 verdict:
- No new direct P0 unit-crossing defect was confirmed in active runtime scoring/zone gates beyond already-tracked items.
- Additional P1/P2 SI-compliance gaps were identified and must be added to the remaining execution backlog before final compliance sign-off.

## Re-pass #18 Findings (New)

### 1) Task delegate layer still exposes ambiguous unsuffixed km wrappers
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskTypeCoordinatorDelegate.kt:21`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskTypeCoordinatorDelegate.kt:26`

`TaskTypeCoordinatorDelegate` still publishes deprecated unsuffixed `calculateDistance()` and `calculateSegmentDistance(...)` wrappers (km outputs), which keeps non-SI API surface in internal delegate contracts.

### 2) AAT/Racing calculator layers still publish km wrappers and ambiguous deprecated distance APIs
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskCalculator.kt:202`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskCalculator.kt:217`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskCalculator.kt:222`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCalculator.kt:224`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCalculator.kt:239`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCalculator.kt:244`

Calculator-level km wrappers remain in internal domain APIs even after meter-first methods were added, increasing regression surface for mixed-unit call paths.

### 3) `AATWaypoint` core model still uses km-native target-offset contract
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATWaypoint.kt:54`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATWaypoint.kt:55`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATWaypoint.kt:62`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATWaypoint.kt:64`

`targetPointOffset` is km-valued (unsuffixed) and `isTargetPointValid()` compares this km value against `assignedArea.radiusMeters / 1000.0`, preserving non-SI internal model semantics.

### 4) AAT competition-validation stack remains km/km2/km/h-native internally
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/FAIComplianceRules.kt:16`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/FAIComplianceRules.kt:71`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/FAIComplianceRules.kt:76`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/FAIComplianceAreaRules.kt:123`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATTaskStrategicValidator.kt:16`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATTaskStrategicValidator.kt:24`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATValidationScoreCalculator.kt:29`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATValidationScoreCalculator.kt:41`
- `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATCompetitionComplianceEvaluator.kt:22`

Competition thresholds and calculations are still modeled/evaluated in km, km2, and km/h within domain validation/scoring paths. These should be normalized to SI internals with explicit boundary formatting/conversion only at output.

### 5) Racing result model still declares km/h internal speed contract and appears unused
- `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTaskResultModels.kt:16`

`RacingTaskResult.averageSpeed` remains km/h-native. Static grep found no production call sites constructing or consuming this model, so this is likely dead or stale contract debt and a cleanup candidate.

Re-pass #18 verdict:
- No new direct P0 runtime unit-crossing bug was confirmed.
- Additional P1/P2/P3 residual SI contract debt exists beyond backlog `#29-#34`, primarily in delegate/calculator surfaces and AAT validation/scoring domains.

## Re-pass #19 Findings (Run 10)

### Closures Confirmed
1. Delegate-level ambiguous distance wrappers removed:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskTypeCoordinatorDelegate.kt`
2. Calculator-level km/ambiguous distance wrapper surfaces removed:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskCalculator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCalculator.kt`
3. `AATWaypoint` target-offset contract is meter-canonical:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/models/AATWaypoint.kt`
4. AAT competition validation/scoring internals are SI-first (meters/m2/m/s) with km/km/h text only at output formatting:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/FAIComplianceRules.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/FAIComplianceAreaRules.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATTaskStrategicValidator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATValidationScoreCalculator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/AATCompetitionComplianceEvaluator.kt`
5. Racing result speed contract is SI-native:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTaskResultModels.kt` (`averageSpeedMs`)
6. Additional Run 10 SI hardening:
   - Removed unused compatibility wrappers from `AATTaskCalculator`, `AATSpeedCalculator`, `AATGeoMath`, and `AATInteractiveTurnpointManager`.
   - Migrated `AATRadiusAuthority` to meter-canonical authority APIs and removed km authority extension usage.
   - Updated AAT persistence adapter to consume authority meters directly:
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`

### Residual Findings (Still Open)
1. Core/racing radius compatibility dual-contract remains active (`#30`):
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskPersistSerializer.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskInitializer.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt`
2. Legacy/dead km helper cleanup still pending (`#34` and `#28`):
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` (km-returning helper path)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt` (legacy helper)
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt` (unused helper)
3. Boundary adapter conversion-test expansion remains pending (`#13`).

### Verification Snapshot (Run 10)
1. PASS:
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`
   - focused `:feature:map:testDebugUnitTest` task/AAT/racing suite
2. PASS:
   - `:app:uninstallDebug :app:uninstallDebugAndroidTest` (environment remediation)
   - `:app:connectedDebugAndroidTest` (9 tests)
3. NOT COMPLETED:
   - `connectedDebugAndroidTest` (user-aborted to reduce execution time)

## Re-pass #20 Findings (Run 11)

### Closures Confirmed
1. Polar model/config contracts are now SI-canonical in storage and internal runtime usage:
   - `core/common/src/main/java/com/trust3/xcpro/common/glider/GliderModels.kt`
   - `core/common/src/main/java/com/trust3/xcpro/common/glider/GliderConfigModels.kt`
2. Polar interpolation and bounds internals are SI-only (`m/s`), with km/h only at explicit conversion boundaries:
   - `feature/map/src/main/java/com/trust3/xcpro/glider/PolarCalculator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/glider/GliderSpeedBounds.kt`
3. Legacy persisted km/h compatibility read is implemented and canonical SI write is enforced:
   - `feature/map/src/main/java/com/trust3/xcpro/glider/GliderRepository.kt`
4. Boundary wrappers remain explicit for UI/input compatibility:
   - `feature/map/src/main/java/com/trust3/xcpro/glider/GliderUseCase.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/glider/GliderViewModel.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/PolarThreePointPolarCard.kt`
5. Added regression coverage for migration/parity:
   - `feature/map/src/test/java/com/trust3/xcpro/glider/GliderRepositorySiMigrationTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/glider/PolarCalculatorSiContractTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/glider/GliderSpeedBoundsResolverSiContractTest.kt`

### Residual Findings (Still Open)
1. Core/racing radius compatibility dual-contract remains active (`#30`).
2. Legacy/dead km helper cleanup still pending (`#34` and `#28`).
3. Boundary adapter conversion-test expansion remains pending (`#13`).

### Verification Snapshot (Run 11)
1. PASS:
   - `:feature:map:testDebugUnitTest --tests "com.trust3.xcpro.glider.*"`
   - `enforceRules testDebugUnitTest assembleDebug`
2. NOT RUN:
   - `:app:connectedDebugAndroidTest`
   - `connectedDebugAndroidTest`

## Re-pass #21 Findings (Run 12, 2026-02-23)

### Scope
Focused static re-pass for backlog `#30` (core/racing radius dual-contract cleanup) with no code changes in this run.

### Residual Findings (Expanded `#30` Inventory)
1. `resolvedCustomRadiusKm()` remains active in internal racing call paths:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskInitializer.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt`
2. Core/racing projection layers still propagate dual radius contracts (`customRadius` km + `customRadiusMeters`):
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskPersistSerializer.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
3. Racing custom params remain km-native and unit-ambiguous:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParams.kt`
4. Observation-zone fallback still converts racing params from km inline:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskObservationZoneResolver.kt`
5. Racing model canonical storage remains km-based in compatibility bridges:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTask.kt`
6. Existing tests still encode compatibility-first radius expectations:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/core/TaskWaypointRadiusContractTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParamsTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/TaskPersistSerializerFidelityTest.kt`

### Adjacent (Out-of-scope for `#30`, but impacted by helper removal)
1. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/gestures/AatGestureHandler.kt` still references `resolvedCustomRadiusKm()` for AAT edit-mode callback compatibility.

### Verification Snapshot (Run 12)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #22 Findings (Run 13, 2026-02-23)

### Scope
Additional static re-pass for backlog `#30` focused on coordinator/viewmodel radius APIs and racing manager/model internals.

### Residual Findings (Newly Confirmed for `#30`)
1. Unsuffixed internal radius-update APIs still carry km semantics through task layers:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt`
2. Racing waypoint manager remains km-canonical for radius defaults/mutations:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointManager.kt`
3. Core `TaskWaypoint` still exposes unused km compatibility helpers that should be removed after migration:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt` (`withCustomRadiusKm`, `getEffectiveRadius`)
4. Racing navigation internals still depend on km-native raw fields in state/signature paths:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineSupport.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`

### Interpretation
`#30` is not only a mapper/serializer/model conversion task. It also requires API-contract migration in coordinator/use-case/viewmodel and the racing waypoint manager/navigation support chain to eliminate km-semantic leakage from active internal call paths.

### Verification Snapshot (Run 13)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #23 Findings (Run 14, 2026-02-23)

### Scope
Focused static re-pass for `#30` across racing manager bridge contracts, task import wiring, and residual radius helper/test debt.

### Residual Findings (Additional `#30` Scope)
1. `RacingTaskManager` bridge/update APIs remain unsuffixed and km-semantic for radius inputs:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
2. `TaskSheetViewModel.applyRacingObservationZone` still pushes internal racing radius updates through km conversion (`radiusMeters / 1000.0`):
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt`
3. Dead km-based helper remains in racing model:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt` (`effectiveRadius`)
4. Navigation support tests still use km-centric fixture semantics and need migration with contract changes:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineSupportTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineTest.kt`

### Interpretation
`#30` closure requires manager-level API migration and test-fixture migration in addition to model/mapper/persistence changes already listed in earlier re-passes.

### Verification Snapshot (Run 14)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #24 Findings (Run 15, 2026-02-23)

### Scope
Post-implementation static re-check for `#30` after manager bridge, import-path, and custom-parameter migration changes.

### Closures Confirmed
1. Racing manager bridge/update radius APIs are meter-named:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
2. Racing import-path km conversion is removed:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt` (`applyRacingObservationZone`)
3. Dead racing helper removal and fixture migration are in place:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineSupportTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineTest.kt`
4. Racing custom-parameter contract is meter-named with legacy-read compatibility:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParams.kt`

### Residual Findings (Still Open for `#30`)
1. Racing model canonical storage remains km-backed:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTask.kt`
2. `RacingWaypointManager` remains km-canonical in mutation/default logic:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointManager.kt`
3. Internal racing projection paths still dual-write `customRadius` + `customRadiusMeters`:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
4. One navigation guard still reads raw km field:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`
5. Radius contract tests are still compatibility-first in intent:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/core/TaskWaypointRadiusContractTest.kt`

### Verification Snapshot (Run 15)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #25 Findings (Run 16, 2026-02-23)

### Scope
Focused code re-check for `#30` tranche to identify misses after Run 15 residual triage, with file-level validation across racing model/manager/navigation/display paths.

### Residual Findings (Newly Added to `#30` Scope)
1. Racing model factory still keeps km compatibility inputs in active internal call paths (`customGateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`), which keeps non-meter contracts alive in manager flows:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointManager.kt`
2. Raw km field access remains in racing display diagnostics:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`

### Residual Findings (Reconfirmed from Run 15)
1. Racing model canonical radius storage remains km-based (`RacingWaypoint`, `RacingTask.racingWaypoints` bridge).
2. `RacingWaypointManager` defaults/mutations remain km-canonical.
3. Racing projection paths still dual-write `customRadius` + `customRadiusMeters`.
4. `RacingZoneDetector` still has one raw km field guard (`gateWidth <= 0.0`).
5. `TaskWaypointRadiusContractTest` still emphasizes compatibility-first expectations.

### Adjacent (Out-of-scope for `#30`)
1. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/gestures/AatGestureHandler.kt` still calls `resolvedCustomRadiusKm()` for edit-mode callback compatibility.

### Verification Snapshot (Run 16)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #26 Findings (Run 17, 2026-02-23)

### Scope
Focused static re-check for `#30` across core task radius helper semantics and internal AAT/racing engine normalization paths.

### Residual Findings (Newly Added to `#30` Scope)
1. Core radius helper still dual-writes the legacy km mirror in internal flows:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt` (`TaskWaypoint.withCustomRadiusMeters`)
2. Internal engine normalization/update paths still call this helper, so km mirror propagation remains active beyond boundary adapters:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/AATTaskWaypointCodec.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultAATTaskEngine.kt`
3. Internal-domain tests still encode compatibility-first km mirror expectations:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/domain/engine/DefaultAATTaskEngineTest.kt`

### Residual Findings (Reconfirmed from Run 16)
1. Racing model factory retains km compatibility inputs in active internal call paths (`customGateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`).
2. Raw km field access remains in racing diagnostics (`RacingFinishLineDisplay`).
3. Racing model storage/defaulting and projection residuals from Run 15 remain open (`RacingWaypoint`, `RacingTask.racingWaypoints`, `RacingWaypointManager`, `RacingZoneDetector`, `TaskWaypointRadiusContractTest` intent).

### Verification Snapshot (Run 17)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #27 Findings (Run 18, 2026-02-23)

### Scope
Focused static re-check for `#30` on residual legacy km-field reads in racing UI and remaining dual-contract touchpoints.

### Residual Findings (Newly Added to `#30` Scope)
1. Racing UI still reads the legacy km radius field in an active internal state key path:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt` (`taskWaypoint.customRadius` in `remember(...)` keys)

### Residual Findings (Reconfirmed from Run 17)
1. Core helper `TaskWaypoint.withCustomRadiusMeters` still dual-writes km mirrors.
2. Internal engine normalization/update paths still propagate that helper output (`DefaultRacingTaskEngine`, `AATTaskWaypointCodec`, `DefaultAATTaskEngine`).
3. Internal-domain test intent still validates km mirror behavior (`DefaultAATTaskEngineTest`).
4. Run 16 residuals remain open (`RacingWaypoint` factory compatibility inputs, `RacingFinishLineDisplay` raw km field access, model/storage/manager residuals).

### Verification Snapshot (Run 18)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #28 Findings (Run 19, 2026-02-23)

### Scope
Focused implementation + verification pass for backlog `#30` residuals in active core/racing/AAT task projection and UI-state paths.

### Closures Confirmed
1. Internal projection dual-write reduction in active manager/adapter bridges:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt`
2. AAT compose-state internal legacy km dependency removed:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATManageListItems.kt`
3. AAT gesture edit-mode callback no longer depends on `resolvedCustomRadiusKm()`:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/gestures/AatGestureHandler.kt`
4. Racing finish-line diagnostics/display path now uses meter-only calculations/logging:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`

### Residual Findings (Still Open for `#30`)
1. Racing model canonical radius storage remains km-backed:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTask.kt`
2. `RacingWaypointManager` still mutates/defaults through km-backed model fields:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointManager.kt`
3. `RacingWaypoint.createWithStandardizedDefaults` still includes km compatibility constructor inputs used by internal manager/model call paths:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
4. Radius-contract tests still include compatibility-first expectations in persistence compatibility surfaces:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/TaskPersistSerializerFidelityTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParamsTest.kt`

### Verification Snapshot (Run 19)
1. PASS:
   - `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.core.TaskWaypointRadiusContractTest" --tests "com.trust3.xcpro.tasks.domain.engine.DefaultAATTaskEngineTest" --tests "com.trust3.xcpro.tasks.aat.gestures.AatGestureHandlerHitTest" --tests "com.trust3.xcpro.tasks.TaskPersistSerializerFidelityTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest"`
   - `./gradlew --no-daemon --no-configuration-cache enforceRules`
   - `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest`
   - `./gradlew --no-daemon --no-configuration-cache assembleDebug`
2. NOT RUN:
   - `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
   - `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel`

## Re-pass #33 Findings (Run 24, 2026-02-23)

### Scope
Implementation + verification closure for backlog `#13` boundary adapter conversion tests across ADS-B/OGN/replay.

### Closures Confirmed (`#13`)
1. ADS-B boundary adapter coverage added:
   - repository bbox propagation + clamp edge assertions:
     - `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
   - OpenSky HTTP boundary serialization/auth header assertions:
     - `feature/map/src/test/java/com/trust3/xcpro/adsb/OpenSkyProviderClientTest.kt`
2. OGN boundary adapter coverage added:
   - socket-harness login filter precision + reconnect-center refresh:
     - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
   - exact receive-radius boundary include/exclude checks:
     - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
3. Replay boundary adapter coverage added:
   - IAS/TAS `km/h -> m/s` conversion + null/non-finite ingress reset:
     - `feature/map/src/test/java/com/trust3/xcpro/replay/ReplaySampleEmitterTest.kt`
   - synthetic replay speed conversion + step quantization:
     - `deleted map replay route helper test`

### Verification Snapshot (Run 24)
1. PASS:
   - `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.OpenSkyProviderClientTest" --tests "com.trust3.xcpro.adsb.AdsbTrafficRepositoryTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryPolicyTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryConnectionTest" --tests "com.trust3.xcpro.replay.ReplaySampleEmitterTest"`
   - `./gradlew --no-daemon --no-configuration-cache enforceRules testDebugUnitTest assembleDebug`
2. NOT RUN:
   - `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
   - `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel`

## Re-pass #34/#28 Findings (Run 25, 2026-02-23)

### Scope
Focused static code re-check for backlog `#34` and `#28`, including adjacent AAT helper surfaces discovered while validating closure readiness.

### Residual Findings (Still Open)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
   - Unused km helper `haversineDistance(...)` remains in production source.
2. `#34` unresolved target 1:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt`
   - `calculateDistanceInArea(...)` has unsuffixed return contract and accumulates km values via `AATMathUtils.calculateDistance(...)`; no callers found.
3. `#34` unresolved target 2:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
   - Legacy commented source block still contains km conversions and km haversine helper.

### Additional Adjacent Findings (Added to Run D Closeout Scope)
1. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATLongPressOverlay.kt`
   - local km haversine helper and meter-to-km conversion in hit-test logic; file appears unused (no call sites found).
2. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt`
   - km wrapper `haversineDistance(...)` remains as an unused compatibility surface.

### Verification Snapshot (Run 25)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 26, 2026-02-23)

### Scope
Follow-up static re-check for backlog `#34` and `#28` with active call-path validation around adjacent AAT area-size helpers.

### Residual Findings (Still Open)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
   - unused km helper `haversineDistance(...)` still present with no call sites.
2. `#34` unresolved target 1:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt`
   - `calculateDistanceInArea(...)` unsuffixed km-returning contract still present and appears unused.
3. `#34` unresolved target 2:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
   - legacy commented km-based source remains in production path.

### Newly Missed Adjacent Active Contract Drift
1. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt`
   - `calculateAreaSizeKm2(...)` is actively consumed by `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt`.
2. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt`
   - quick-validation area-size policy thresholds are currently km2-based (`< 10.0`, `> 5000.0`) and warning output labels area size as `km` instead of squared units.

### Verification Snapshot (Run 26)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 27, 2026-02-23)

### Scope
Deep follow-up static re-check for backlog `#34` and `#28` with explicit call-site sweep of AAT edit-geometry compatibility wrappers.

### Residual Findings (Still Open)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
   - unused km helper `haversineDistance(...)` still present with no call sites.
2. `#34` unresolved target 1:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt`
   - unsuffixed km-returning `calculateDistanceInArea(...)` still present and appears unused.
3. `#34` unresolved target 2:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
   - legacy commented km-based source remains in production path.

### Newly Missed Adjacent Wrapper Residuals
1. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt`
   - additional unused km wrappers were missed in prior pass:
     - `generateCircleCoordinates(...)`
     - `generateSectorCoordinates(...)`
     - `calculateDestinationPoint(...)`
   - previously tracked `haversineDistance(...)` unused km wrapper remains.

### Verification Snapshot (Run 27)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 28, 2026-02-23)

### Scope
Triple-pass sequence pass 1: strict dead-code/call-site revalidation for `#34/#28` targets and previously listed adjacent surfaces.

### Residual Findings (Reconfirmed)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
2. `#34` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` (`calculateDistanceInArea(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
3. Adjacent unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATLongPressOverlay.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt` km wrapper set

### Verification Snapshot (Run 28)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 29, 2026-02-23)

### Scope
Triple-pass sequence pass 2: deep AAT area-size helper chain review for adjacent active contract drift.

### Newly Expanded Adjacent Findings (`#40` scope)
1. Active km2 helper chain includes:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/CircleAreaCalculator.kt` (`calculateAreaSizeKm2(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaCalculator.kt` (`calculateAreaSizeKm2(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt` (`calculateAreaSizeKm2(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` (`calculateAreaSizeKm2(...)`)
2. Consumption path remains active:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt` (`areaSizeKm2` thresholds + warning strings)
3. Warning labels still use non-squared unit text (`km`) for area-size values.

### Verification Snapshot (Run 29)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 30, 2026-02-23)

### Scope
Triple-pass sequence pass 3: static guardrail coverage review in `scripts/ci/enforce_rules.ps1` for `#28/#34/#40` regression prevention.

### Newly Missed Adjacent Guardrail Gap
1. No explicit static checks currently prevent reintroduction of:
   - dead helper/file patterns in `AirspaceGestureMath` / `KeyholeVerification`,
   - km wrapper surfaces in `AATLongPressOverlay` / `AATEditGeometry`,
   - km2 policy/label drift in quick-validation area-size flow.
2. Existing `haversineDistance` guard is limited to `TaskManagerCoordinator` and does not cover these surfaces.

### Verification Snapshot (Run 30)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 31, 2026-02-23)

### Scope
This-cycle triple-pass pass 1: call-site/dead-code reconfirmation for `#34/#28` and previously logged adjacent wrappers.

### Residual Findings (Reconfirmed)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
2. `#34` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` (`calculateDistanceInArea(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
3. Adjacent unresolved wrappers:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATLongPressOverlay.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt`

### Verification Snapshot (Run 31)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 32, 2026-02-23)

### Scope
This-cycle triple-pass pass 2: quick-validation + unit-test coverage audit for adjacent `#40` scope.

### Newly Missed Adjacent Test-Coverage Gap
1. `feature/map/src/test/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngineUnitsTest.kt`
   - currently asserts start/finish meter-threshold behavior only.
   - missing assertions for area-size SI policy path and squared-unit warning-label contract in quick validation.

### Verification Snapshot (Run 32)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 33, 2026-02-23)

### Scope
This-cycle triple-pass pass 3: static-rule and verification-plan parity audit for closeout readiness.

### Newly Missed Adjacent Planning/Guardrail Gap
1. `scripts/ci/enforce_rules.ps1`
   - `#41` guard expansions for `#28/#34/#40` are still not implemented.
2. Verification closeout notes required alignment so residual scope is explicit beyond `#28/#34`.

### Verification Snapshot (Run 33)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 34, 2026-02-23)

### Scope
This-cycle triple-pass pass 1: dead-code/call-site reconfirmation for `#34/#28` plus adjacent AAT geometry compatibility surfaces.

### Residual Findings (Reconfirmed)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
2. `#34` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` (`calculateDistanceInArea(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
3. Adjacent unresolved wrappers:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATLongPressOverlay.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt`

### Newly Missed Adjacent Wrapper Residual
1. `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt`
   - unused km compatibility wrappers remain:
     - `generateCircleCoordinates(...)`
     - `generateStartLine(...)`
     - `generateFinishLine(...)`
     - `calculateDestinationPoint(...)`
   - no main/test call sites were found; active code paths use `*Meters` variants.

### Verification Snapshot (Run 34)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 35, 2026-02-23)

### Scope
This-cycle triple-pass pass 2: active quick-validation area-size and unit-test coverage re-check.

### Residual Findings (Reconfirmed)
1. `#40` remains active:
   - `AATTaskQuickValidationEngine` still consumes `calculateAreaSizeKm2(...)` chain for policy thresholds and warnings.
2. `#42` remains active:
   - `AATTaskQuickValidationEngineUnitsTest` still lacks area-size SI policy and squared-unit label assertions.

### Verification Snapshot (Run 35)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 36, 2026-02-23)

### Scope
This-cycle triple-pass pass 3: static-rule guardrail coverage re-check for closeout residuals.

### Newly Missed Adjacent Planning/Guardrail Gap
1. `scripts/ci/enforce_rules.ps1`
   - `#41` guard expansions remain unimplemented for `#28/#34/#40`.
2. New guardrail adjacency:
   - no static checks currently cover `#43`-class reintroduction for `AATGeometryGenerator` unused km compatibility wrappers.

### Verification Snapshot (Run 36)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 37, 2026-02-23)

### Scope
Focused follow-up re-check for `#34/#28` plus adjacent tracked residual surfaces (`#40/#41/#42/#43`).

### Residual Findings (Reconfirmed)
1. `#28` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`
2. `#34` unresolved:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` (`calculateDistanceInArea(...)`)
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`
3. Adjacent unresolved:
   - `#40`: active area-size km2 chain + quick-validation warning label drift.
   - `#42`: `AATTaskQuickValidationEngineUnitsTest` still lacks area-size SI/label assertions.
   - `#41`: static guard gap remains open and still needs `#43` coverage.
   - `#43`: unused km compatibility wrappers in `AATGeometryGenerator`.

### Newly Missed Items
1. None in this run; no additional residual beyond tracked `#28/#34/#40/#41/#42/#43`.

### Verification Snapshot (Run 37)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #34/#28 Findings (Run 38, 2026-02-23)

### Scope
Implementation closeout pass for residual backlog `#28/#34/#40/#41/#42/#43`.

### Closures
1. `#28` closed:
   - removed `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`.
2. `#34` closed:
   - removed dead `AreaBoundaryCalculator.calculateDistanceInArea(...)`,
   - removed commented `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt`,
   - migrated `AATLongPressOverlay` hit testing to meter-first helper,
   - removed km wrappers from `AATEditGeometry`.
3. `#40` closed:
   - added/propagated area-size `m2` internal contracts,
   - `AATTaskQuickValidationEngine` now uses m2 thresholds with `km2` labels only at warning output boundary.
4. `#42` closed:
   - expanded `AATTaskQuickValidationEngineUnitsTest` with area-size small/large warning + `km2` label assertions.
5. `#41` closed:
   - expanded `scripts/ci/enforce_rules.ps1` with guards for dead-file reintroduction, km-wrapper reintroduction (`AATEditGeometry`/`AATGeometryGenerator`), local haversine helper drift, and area-size policy/label drift.
6. `#43` closed:
   - removed unused `AATGeometryGenerator` km wrappers (`generateCircleCoordinates`, `generateStartLine`, `generateFinishLine`, `calculateDestinationPoint`).

### Verification Snapshot (Run 38)
1. PASS:
   - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.aat.AATTaskQuickValidationEngineUnitsTest" --no-daemon --no-configuration-cache`
2. PASS:
   - `./gradlew enforceRules --no-daemon --no-configuration-cache`
3. PASS:
   - `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
4. PASS:
   - `./gradlew assembleDebug --no-daemon --no-configuration-cache`

## Re-pass #18 Findings (Run 39, 2026-02-23)

### Scope
Five-pass cycle pass 1: internal task/AAT API and caller-chain sweep for compatibility-wrapper residuals.

### Residual Findings (New/Reconfirmed)
1. Active AAT km wrapper route remains in internal layers:
   - `TaskSheetViewModel.onUpdateAATWaypointPointType(...)`
   - `TaskSheetCoordinatorUseCase.updateAATWaypointPointType(...)`
   - `TaskManagerCoordinator.updateAATWaypointPointType(...)`
   - `AATCoordinatorDelegate.updateWaypointPointType(...)`
   - `AATTaskManager.updateAATWaypointPointType(...)` / `updateWaypointPointTypeBridge(...)`
   - `AATPointTypeConfigurator.updateWaypointPointType(...)`
2. Meter API still down-converts to km for AAT:
   - `TaskManagerCoordinator.updateWaypointPointType(...)` converts `*Meters` to km before AAT dispatch.
3. AAT persisted OZ import still routes through km wrapper path:
   - `TaskSheetViewModel.applyAatObservationZone(...)`.

### Verification Snapshot (Run 39)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #18 Findings (Run 40, 2026-02-23)

### Scope
Five-pass cycle pass 2: gesture-to-camera edit-radius contract sweep.

### Residual Findings (New/Reconfirmed)
1. Internal AAT edit-radius path remains km-contract based:
   - `TaskGestureCallbacks.onEnterEditMode(..., radiusKm)`
   - `AatGestureHandler.enterEditMode(...)` meters->km conversion
   - `MapGestureSetup` km callback wiring
   - `MapCameraManager.zoomToAATAreaForEdit(..., turnpointRadiusKm)`

### Verification Snapshot (Run 40)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #18 Findings (Run 41, 2026-02-23)

### Scope
Five-pass cycle pass 3: dead compatibility-wrapper inventory.

### Residual Findings (New/Reconfirmed)
1. Dead km wrapper in core task model:
   - `TaskWaypoint.resolvedCustomRadiusKm()` (no production callers).
2. Dead AAT km wrappers:
   - `AATMathUtils.calculateDistance(...)`
   - `AATMathUtils.calculateDistanceKm(...)`
   - `AATMathUtils.calculateCrossTrackDistance(...)`
   - `AATMathUtils.calculateAlongTrackDistance(...)`
3. Dead AAT area-size km2 wrappers:
   - `AreaBoundaryCalculator.calculateAreaSizeKm2(...)`
   - `CircleAreaCalculator.calculateAreaSizeKm2(...)`
   - `SectorAreaCalculator.calculateAreaSizeKm2(...)`
   - `SectorAreaGeometrySupport.calculateAreaSizeKm2(...)`
4. Dead racing km wrappers/views:
   - `RacingGeometryUtils.haversineDistance(...)`
   - deprecated km views on `RacingWaypoint` (`gateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`)

### Verification Snapshot (Run 41)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #18 Findings (Run 42, 2026-02-23)

### Scope
Five-pass cycle pass 4: test dependency sweep for wrapper-removal readiness.

### Residual Findings (New/Reconfirmed)
1. Tests still coupled to km wrappers:
   - `TaskWaypointRadiusContractTest` -> `resolvedCustomRadiusKm()`
   - `AATInteractiveTurnpointManagerValidationTest` -> `calculateDistanceKm(...)`
   - `AATEditGeometryValidatorTest` -> `calculateDistanceKm(...)`
   - `AreaCalculatorUnitsTest` / `AATTaskDisplayGeometryBuilderUnitsTest` -> `calculateDistance(...) * 1000`
   - `RacingGeometryUtilsTest` -> `haversineDistance(...)`

### Verification Snapshot (Run 42)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #18 Findings (Run 43, 2026-02-23)

### Scope
Five-pass cycle pass 5: static guardrail coverage audit for wrapper-cut closure.

### Residual Findings (New/Reconfirmed)
1. `scripts/ci/enforce_rules.ps1` has no explicit guards for `#18` wrapper-cut surfaces:
   - no rule against active `updateAATWaypointPointType` wrapper route,
   - no rule against internal `radiusKm` gesture/camera contracts,
   - no rule against dead km wrapper declarations/usages in core/AAT/racing utility/model paths.

### Verification Snapshot (Run 43)
1. NOT RUN (doc-only re-pass):
   - `enforceRules`
   - `testDebugUnitTest`
   - `assembleDebug`

## Re-pass #18 Findings (Run 44, 2026-02-23)

### Scope
Implementation closeout for backlog `#18` (compatibility-wrapper cut).

### Closures
1. Closed active AAT km/unsuffixed point-type route by migrating to meter-first contracts:
   - `TaskSheetViewModel.onUpdateAATWaypointPointTypeMeters(...)`
   - `TaskSheetCoordinatorUseCase.updateAATWaypointPointTypeMeters(...)`
   - `TaskManagerCoordinator.updateAATWaypointPointTypeMeters(...)`
   - `AATCoordinatorDelegate.updateWaypointPointTypeMeters(...)`
   - `AATTaskManager.updateAATWaypointPointTypeMeters(...)`
   - `AATPointTypeConfigurator.updateWaypointPointType(...)` now consumes meter params directly.
2. Closed gesture/camera edit-radius km-contract residual:
   - `TaskGestureCallbacks` now uses `radiusMeters`.
   - `AatGestureHandler` dispatches meter radii directly.
   - `MapGestureSetup` and `MapCameraManager.zoomToAATAreaForEdit(...)` are meter-contract internal paths.
3. Closed dead wrapper inventory from Run 41:
   - removed `TaskWaypoint.resolvedCustomRadiusKm()`.
   - removed dead km wrappers from `AATMathUtils`.
   - removed dead km2 wrappers from `AreaBoundaryCalculator`/`CircleAreaCalculator`/`SectorAreaCalculator`/`SectorAreaGeometrySupport`.
   - removed `RacingGeometryUtils.haversineDistance(...)`.
   - removed deprecated km view properties from `RacingWaypoint`.
4. Closed test-coupling residual from Run 42:
   - migrated affected tests to meter-first helpers/assertions.
5. Closed static-guard residual from Run 43:
   - added `#18` guard checks in `scripts/ci/enforce_rules.ps1` for legacy route names, km gesture/camera contracts, and removed km wrapper/view surfaces.

### Verification Snapshot (Run 44)
1. PASS:
   - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.trust3.xcpro.tasks.AATCoordinatorDelegateTest" --tests "com.trust3.xcpro.tasks.core.TaskWaypointRadiusContractTest" --tests "com.trust3.xcpro.tasks.aat.AATInteractiveTurnpointManagerValidationTest" --tests "com.trust3.xcpro.tasks.aat.interaction.AATEditGeometryValidatorTest" --tests "com.trust3.xcpro.tasks.aat.areas.AreaCalculatorUnitsTest" --tests "com.trust3.xcpro.tasks.aat.AATTaskDisplayGeometryBuilderUnitsTest" --tests "com.trust3.xcpro.tasks.racing.RacingGeometryUtilsTest"`
2. PARTIAL:
   - `./scripts/ci/enforce_rules.ps1` reports pre-existing unrelated failures in this repository state and aborts before full completion.
   - targeted `#18` guard patterns were validated directly via `rg` and returned no matches.

## Re-pass #12 Findings (Run 45, 2026-02-23)

### Scope
Fixture-matrix closure pass for racing/AAT distance invariants.

### Closures
1. Closed residual fixture-coverage gap for backlog `#12`:
   - added AAT nominal-distance fixture matrix coverage (`AATDistanceCalculatorUnitsTest`),
   - added racing geodesic fixture matrix coverage (`RacingGeometryUtilsTest`),
   - added coordinator cross-task fixture parity coverage (`TaskManagerCoordinatorTest`) asserting both racing and AAT segment APIs against canonical meter geometry for identical fixtures.
2. Reconfirmed meter-contract parity across task types for known geodesic fixtures at equator, mid-latitude, and southern-hemisphere coordinates.

### Verification Snapshot (Run 45)
1. PASS:
   - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.aat.calculations.AATDistanceCalculatorUnitsTest" --tests "com.trust3.xcpro.tasks.racing.RacingGeometryUtilsTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest"`

## Re-pass (Run 46, 2026-02-23)

### Scope
Closure pass for remaining non-`#12` static-rule caveat (`enforce_rules` false-positive + ripgrep no-file abort behavior).

### Closures
1. Closed task composable boundary false-positive:
   - `scripts/ci/enforce_rules.ps1` composable-surface scan now excludes `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCompat.kt` (approved compatibility DI host).
2. Closed ripgrep no-file abort behavior:
   - `Invoke-Rg` now uses `Start-Process` with captured stdout/stderr.
   - ripgrep `No files were searched` is classified as non-fatal no-match.
   - non-no-file ripgrep errors still fail enforcement.

### Verification Snapshot (Run 46)
1. PASS:
   - `./scripts/ci/enforce_rules.ps1`.
2. PASS:
   - recursive pass x5 of `./scripts/ci/enforce_rules.ps1` (`exit=0` on all passes).
3. PASS:
   - `./gradlew --no-configuration-cache enforceRules`.
4. NOTE:
   - `./gradlew enforceRules` with configuration cache enabled still reports pre-existing configuration-cache policy violations in root build config (`where powershell`/`where pwsh` process start during configuration phase).
