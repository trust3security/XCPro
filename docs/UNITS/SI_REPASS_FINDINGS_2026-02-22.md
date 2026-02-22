# SI Re-pass Findings

Date: 2026-02-22
Status: Complete (Re-pass #7)

## Scope
Deep re-pass across:
- `feature/map/src/main/java/com/example/xcpro/sensors`
- `feature/map/src/main/java/com/example/xcpro/adsb`
- `feature/map/src/main/java/com/example/xcpro/ogn`
- `feature/map/src/main/java/com/example/xcpro/replay`
- `feature/map/src/main/java/com/example/xcpro/tasks`
- `feature/map/src/main/java/com/example/xcpro/glider`
- `core/common/src/main/java/com/example/xcpro/common/glider`
- `dfcards-library/src/main/java/com/example/xcpro/common/units`

## Compliance Verdict
Not compliant.

The codebase is SI-strong in flight/fusion and traffic flows, but task/AAT/racing legacy plus polar internals still violate strict SI-only internal calculation policy.

## What Was Confirmed Clean
1. Core SI wrappers/conversions are correctly centralized.
2. Flight/sensor fusion internals are SI-first (`m`, `m/s`, `hPa`).
3. ADS-B and OGN keep internal distance state in meters; km is mostly boundary-only.
4. Replay converts external IAS/TAS km/h inputs to m/s before fusion, but one movement snapshot field still has a unit contract defect (see Re-pass #6 delta below).

## Delta: Missed in First Pass

### 1) AAT path optimizer has hard meter-vs-km logic errors
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizerSupport.kt:11`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizerSupport.kt:35`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizerSupport.kt:107`

`calculatePathDistance` sums `AATMathUtils.calculateDistance` (km), but compares against `OPTIMIZATION_TOLERANCE_METERS` and target distances treated as meters.

### 2) AAT path optimizer target distance math uses meter labels against km distances
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizer.kt:24`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizer.kt:63`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPathOptimizer.kt:136`

Variables named `targetDistanceMeters` and `targetDistance` are compared against path distances derived from km-returning functions.

### 3) AAT flight path validator compares km distances to meter thresholds
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:61`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:66`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:92`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATFlightPathValidator.kt:97`

Distance is computed via km-returning utility and checked against meter thresholds like `+ 100.0` and `+ 50.0`.

### 4) AAT performance model mismatch: km written to meters field
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskPerformanceSupport.kt:41`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/models/AATResult.kt:177`

`distanceFromCenter` is documented as meters but assigned with `AATMathUtils.calculateDistance` (km).

## Delta: Missed in Second Pass

### 1) `AATDistanceCalculator` publishes km into models documented as meters
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:27`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:62`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:96`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:119`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:188`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt:250`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/models/AATTask.kt:195`

`AATDistanceCalculator` methods are documented/consumed as meters, but `calculateTotalPathDistance` sums `AATMathUtils.calculateDistance` (km). This also corrupts `targetDistance` clamping (`meters` target clamped by km min/max).

### 2) `AATTaskDisplayGeometryBuilder` passes meter radii/lengths into a km geodesic API
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:14`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:23`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:79`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:101`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplayGeometryBuilder.kt:130`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATMathUtils.kt:113`

`AATTask.start/finish` geometry fields are meters, but builder forwards them directly to `calculatePointAtBearing`, whose distance parameter is kilometers.

### 3) Sector geometry support has broad km-vs-meter contamination beyond `isInsideArea`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:18`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:37`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:41`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:74`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:102`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaGeometrySupport.kt:158`

Distance-to-center is computed in km and compared with meter radii. Meter radii are also passed directly into km-based point generation.

### 4) Circle area bug scope is wider than previously recorded
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:67`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:71`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:95`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:181`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:188`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:195`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:273`

The earlier finding captured only `isInsideArea`; additional methods (boundary point generation, line-circle intersections, boundary distance) also mix km and meters.

### 5) Sector calculator also leaks km values in credited-fix/optimal-touch logic
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaCalculator.kt:102`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaCalculator.kt:125`

`calculateCreditedFix` and `calculateOptimalTouchPoint` continue operating on km-returning values while area radii are meters.

## Delta: Missed in Third Pass

### 1) Racing optimal start-line crossing width uses km as meters
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:228`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt:229`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt:195`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt:202`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingGeometryUtils.kt:166`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingGeometryUtils.kt:263`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt:19`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetCoordinatorUseCase.kt:74`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:125`

`RacingWaypoint.gateWidth` is documented in kilometers, but `calculateOptimalLineCrossingPoint` forwards it to a meter-based destination routine without conversion. This shrinks effective line width by 1000x in the optimal-start path and corrupts distance-to-next computation for start-line selection UI.

## Delta: Missed in Fourth Pass

### 1) AAT start/finish cylinder radius is halved in multiple runtime paths
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATStartPointSelector.kt:91`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATFinishPointSelector.kt:101`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATPointTypeConfigurator.kt:79`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/models/AATRadiusAuthority.kt:153`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/models/AATRadiusAuthority.kt:154`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:162`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATTaskRenderer.kt:185`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt:184`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATValidationBridge.kt:175`

Start/finish cylinder fields are labeled and stored as radius (km/meters), but renderer/geometry/validation conversion paths divide authoritative radius by 2.0. This introduces a 2x scale error for cylinder geometry/validation behavior.

## Delta: Missed in Fifth Pass

### 1) Replay runtime movement snapshot stores speed in a distance field
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayRuntimeInterpolator.kt:53`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayRuntimeInterpolator.kt:158`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayRuntimeInterpolator.kt:164`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayHeadingResolver.kt:15`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayMath.kt:12`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayMath.kt:46`

`MovementSnapshot.distanceMeters` is a meter-valued field, but `ReplayRuntimeInterpolator` assigns it from `speedMs` (`m/s`). `ReplayHeadingResolver` then compares this value against `minDistanceM` (meters), producing unit-invalid gating for heading reuse.

## Delta: Missed in Sixth Pass

### 1) AAT quick-validation finish checks have the same km-vs-meter defect and were under-scoped
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:202`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:207`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:213`

`validateFinish` computes distance with `AATMathUtils.calculateDistance` (km), then compares that value against meter-based contracts (`lineLength`, `radius`) plus meter tolerances (`+ 100.0`, `+ 50.0`). Prior findings called out the same defect family in area/start/start-finish quick checks, but this finish path was not explicitly captured.

## Previously Known but Re-confirmed
1. AAT quick validation compares km distances to meter thresholds.
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:101`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:103`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:174`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt:303`

2. AAT safety validator divides a km result by 1000 again.
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATTaskSafetyValidator.kt:16`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATTaskSafetyValidator.kt:17`

3. Circle/sector area calculators compare km distances to meter radii.
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:21`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/CircleAreaCalculator.kt:31`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/SectorAreaCalculator.kt:18`

4. Legacy task manager/coordinator contracts are still km-based.
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt:149`
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt:118`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetCoordinatorUseCase.kt:74`

5. Glider polar internals are km/h-based data contracts.
- `core/common/src/main/java/com/example/xcpro/common/glider/GliderModels.kt:4`
- `feature/map/src/main/java/com/example/xcpro/glider/PolarCalculator.kt:16`

## Compliance Statement
- Internal SI compliance for all calculations: failed.
- Partial compliance (flight/traffic/replay core): passed.
- Full compliance requires task/AAT/racing normalization and a policy decision on polar model storage units.
