# Execution Backlog: SI Migration

Date: 2026-02-22
Status: Updated after `#18` implementation closeout (Run 44; `#18` done)

## Run 1 Result (2026-02-22)
- Completed: P0 AAT start/finish cylinder radius-halving defect removed from validation/renderer/geometry (`AATValidationBridge`, `AATTaskRenderer`, `AATGeometryGenerator`).
- Completed: P1 #14 replay movement snapshot contract + replay heading-gating regression tests.
- Completed: P1 #15 distance-circles boundary now uses `UnitsPreferences`/`UnitsFormatter`.
- Completed: P1 #16 task UI distance outputs now use selected distance units.
- Partial: P1 #9 racing manager/coordinator SI normalization (start-line gate-width km->m defect fixed; task-sheet coordinator now exposes meter contracts with km wrappers for compatibility).
- Remaining high-priority items are concentrated in AAT internals and full legacy contract cleanup.

## Run 2 Result (2026-02-22)
- Completed: P1 #8 AAT manager/coordinator distance contracts migrated to meter-first APIs with explicit `*Meters`/`*Km` naming and compatibility wrappers.
- Partial: P1 #9 racing manager/coordinator distance contracts migrated to meter-first APIs; deeper racing helper/turnpoint internals still need phase-3 normalization.
- Completed: P1 #10 ambiguous task distance APIs renamed/expanded with explicit unit suffixes across manager/coordinator/use-case boundaries.
- Completed: Added coordinator regression coverage for meter-first segment contract and km-wrapper parity (`TaskManagerCoordinatorTest`).

## Run 3 Result (2026-02-22)
- Completed: P1 #9 full racing helper/turnpoint/navigation/boundary SI normalization to meter-first internals (`RacingGeometryUtils.haversineDistanceMeters` adoption + meter helper migration across racing math call sites).
- Completed: P1 #12 racing distance invariants expanded with dedicated geometry meter-contract coverage (`RacingGeometryUtilsTest`) and updated meter-native assertions in boundary/coordinator tests.
- Completed: Meter-first racing path propagation into `DefaultRacingTaskEngine` and replay route generation (`RacingReplayLogBuilder`).
- Remaining: P1 #13 boundary adapter tests for ADS-B/OGN/replay explicit SI conversion contracts.

## Run 4 Result (2026-02-22)
- New P0 identified: shared task waypoint radius contract remains km-native (`TaskWaypoint.customRadius`), causing persistent km->m leakage across task/AAT/racing flows.
- New P0 identified: AAT core math/geodesic APIs are still km-native (`AATMathUtils`, `AATGeometryGenerator`), so SI cannot be guaranteed end-to-end.
- New P1 identified: additional AAT interaction/editing paths still use km internals (`AatGestureHandler`, `AATEditModeState`, `AATAreaTapDetector`, `AATMovablePointStrategySupport`).
- New P1 identified: observation-zone fallback and resolver logic still starts from km task fields (`TaskObservationZoneResolver`).
- New P1 identified: racing replay anchor and one coordinator path still use ad-hoc km->m multiplication (`RacingReplayAnchorBuilder`, `TaskManagerCoordinator`).
- New P1 identified: OGN distance policy/API remains km-first (`OgnSubscriptionPolicy.haversineKm` and call sites).
- New P2 identified: AAT edit-mode movement tolerance comment is wrong by 100x (`0.00001 km` is ~1 cm, not ~1 m).
- New P3 identified: dead km-based helper remains (`AirspaceGestureMath.haversineDistance`).

## Run 5 Result (2026-02-22)
- Completed: P0 #21 core `TaskWaypoint` radius contract is now meter-first (`customRadiusMeters`) with explicit km compatibility helpers.
- Completed: P1 #24 `TaskObservationZoneResolver` fallback contract now resolves waypoint radius from meter-first task fields.
- Completed: Persistence/serialization compatibility for the meter-first radius contract (`TaskPersistSerializer`, task persistence adapters) with explicit km boundary retention.
- Added/updated regression coverage for radius contract precedence and compatibility (`TaskWaypointRadiusContractTest`, serializer/engine/gesture tests).

## Run 6 Result (2026-02-22)
- Completed: P0 #22 AAT core geodesic contracts now expose/consume meter-first APIs across `AATGeometryGenerator` call paths; compatibility km wrappers retained where needed.
- Completed: P1 #23 remaining AAT edit/interaction internals migrated to meter contracts (`AATEditModeState`, `AATAreaTapDetector`, `AATMovablePointStrategySupport`, `AATEditGeometry`, and related edit/drag/render callers).
- Completed: P1 #25 replay/coordinator ad-hoc km->m hotspots removed (`RacingReplayAnchorBuilder`, `TaskManagerCoordinator`) using centralized meter fields/helpers.
- Completed: P1 #26 OGN movement/radius policy now meter-first internally (`OgnSubscriptionPolicy.haversineMeters` + repository callers), with km retained only at protocol/display boundaries.
- Verification: `enforceRules`, `testDebugUnitTest`, and `assembleDebug` pass (run with `--no-configuration-cache` due pre-existing Gradle configuration-cache issue in build scripts).

## Run 7 Result (2026-02-22)
- Completed: P2 wrapper cleanup tranche for OGN internals by removing unused km compatibility helpers (`OgnSubscriptionPolicy.haversineKm`, `OgnSubscriptionPolicy.shouldReconnectByCenterMove`, `isWithinReceiveRadiusKm`).
- Completed: Additional SI hardening in AAT validators/area helpers (`AATTaskSafetyValidator`, `AATFlightPathValidator`, `CircleAreaCalculator`, `SectorAreaCalculator`, `SectorAreaGeometrySupport`) to remove remaining km-helper promotion into meter-labeled variables.
- Completed: P2 static guard expansion in `scripts/ci/enforce_rules.ps1` for:
  - km-helper reintroduction in OGN internals,
  - replay `distanceMeters = speedMs` regression pattern,
  - `AATMathUtils.calculateDistance(...) * METERS_PER_KILOMETER` reintroduction,
  - meter-labeled variables sourcing from km-returning AAT helpers,
  - hard-coded non-SI distance labels in shared distance display surfaces.
- Verification (post-hardening): `enforceRules`, `testDebugUnitTest`, `assembleDebug`, `:app:connectedDebugAndroidTest`, and `connectedDebugAndroidTest` all pass with `--no-daemon --no-configuration-cache`.

## Run 8 Result (2026-02-22)
- Completed: Deep static re-pass across task/AAT/racing/OGN/polar contracts with fresh grep inventory.
- New misses identified (no code changes in this run):
  1. AAT radius authority remains km-canonical and still drives repeated internal km->m conversions.
  2. Core/racing radius contracts remain dual-unit (`customRadius` km + `customRadiusMeters`) with km-biased racing parameter contracts.
  3. AAT and racing manager distance APIs remain km-first baselines in several internal paths.
  4. AAT interactive-distance subsystem is km-native with unsuffixed distance fields.
  5. AAT scoring speed contracts (`AATSpeedCalculator`, `AATResult` speed fields) are km/h-native internally.
  6. Additional coordinator/use-case/viewmodel `*Km` wrappers remain published and appear unused by callers.
- Action: Added new backlog items `#29-#34` to make remaining SI migration scope explicit.

## Run 9 Result (2026-02-22)
- Completed: Follow-up deep static re-pass focused on residual wrapper surfaces and AAT competition-validation stack contracts.
- New misses identified (no code changes in this run):
  1. Task delegate layer still exposes ambiguous deprecated unsuffixed km wrappers (`TaskTypeCoordinatorDelegate`).
  2. AAT/Racing calculator layers still publish km wrapper/deprecated ambiguous distance APIs (`AATTaskCalculator`, `RacingTaskCalculator`).
  3. `AATWaypoint` core model still uses km-native target-offset semantics (`targetPointOffset`, `isTargetPointValid`).
  4. AAT competition-validation stack remains km/km2/km/h-native internally (`FAIComplianceRules`, `FAIComplianceAreaRules`, `AATTaskStrategicValidator`, `AATValidationScoreCalculator`, `AATCompetitionComplianceEvaluator`).
  5. Racing result model retains km/h speed contract and appears unused (`RacingTaskResultModels`).
- Action: Added backlog items `#35-#39` and expanded autonomous run mapping accordingly.

## Run 10 Result (2026-02-22)
- Completed: Compatibility-wrapper cleanup in residual AAT internals:
  1. Removed unused deprecated wrappers from `AATTaskCalculator`.
  2. Removed unused compatibility wrappers from `AATSpeedCalculator`.
  3. Removed unused compatibility wrappers from `AATGeoMath`.
  4. Removed deprecated `getDistanceLimits()` wrapper from `AATInteractiveTurnpointManager`.
- Completed: AAT radius authority SI normalization:
  1. Replaced km-canonical authority APIs with meter-canonical contracts in `AATRadiusAuthority`.
  2. Removed internal km authority extension usage (`getAuthorityRadius`), retaining only `getAuthorityRadiusMeters`.
  3. Updated `TaskPersistenceAdapters` AAT mapping to consume authority meters directly.
- Completed: Re-pass #18 residual closures now confirmed in code:
  1. `#35` delegate wrappers removed (`TaskTypeCoordinatorDelegate` meters-only contract).
  2. `#36` calculator km distance wrappers removed (AAT/Racing calculator paths).
  3. `#37` AAT waypoint target-offset contract normalized (`targetPointOffsetMeters`).
  4. `#38` AAT competition validation/scoring stack now SI-internal (meters/m2/m/s).
  5. `#39` racing result speed contract migrated to `averageSpeedMs` (model still appears unused).
- Partial: `#30` remains open; core/racing radius dual-contract compatibility (`customRadius` km and km-named racing params) still exists in internal flows.
- Verification:
  1. PASS: `enforceRules`, `testDebugUnitTest`, `assembleDebug`, and focused `:feature:map:testDebugUnitTest` for task/AAT/racing scope.
  2. PASS: `:app:connectedDebugAndroidTest` after uninstall/reinstall remediation (`:app:uninstallDebug :app:uninstallDebugAndroidTest`).
  3. NOT COMPLETED: `connectedDebugAndroidTest` (user-aborted to reduce execution time).

## Run 11 Result (2026-02-23)
- Completed: `#17` polar storage SI migration.
  1. Core glider/polar data contracts are SI-canonical for internal storage (`*Ms`) in `core/common` models/config.
  2. Glider persistence now reads legacy km/h keys and restores canonical SI fields (`GliderRepository` compatibility loader).
  3. Writes now persist canonical SI keys only (`iasMinMs`/`iasMaxMs` and `ThreePointPolar.*Ms`).
  4. Polar interpolation and bounds logic now consume SI speed contracts internally (`PolarCalculator`, `GliderSpeedBoundsResolver`).
- Completed: Added migration/parity coverage for #17:
  1. Legacy km/h JSON migration read test.
  2. SI round-trip persistence test (no legacy key write-back).
  3. Polar interpolation/bounds SI contract tests.
- Verification:
  1. PASS: `:feature:map:testDebugUnitTest --tests "com.example.xcpro.glider.*"`.
  2. PASS: `enforceRules testDebugUnitTest assembleDebug`.
  3. NOT RUN: instrumentation commands (not required for this storage-domain refactor pass).

## Run 12 Result (2026-02-23)
- Completed: Deep static code re-pass focused on backlog `#30` (core/racing radius dual-contract cleanup).
- New/confirmed misses for `#30` (no code changes in this run):
  1. `resolvedCustomRadiusKm()` still feeds internal racing call paths:
     - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskInitializer.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointListItems.kt`
  2. Core/racing projection layers still dual-write km+meters:
     - `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
  3. Racing custom params remain km-native and unit-ambiguous (`keyholeInnerRadius` / `faiQuadrantOuterRadius`):
     - `feature/map/src/main/java/com/example/xcpro/tasks/core/TaskWaypointCustomParams.kt`
  4. OZ fallback still converts racing params from km inline:
     - `feature/map/src/main/java/com/example/xcpro/tasks/TaskObservationZoneResolver.kt`
  5. Racing model layer remains km-canonical for storage/compat:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt`
  6. Radius contract tests still encode compatibility behavior as primary expectations:
     - `feature/map/src/test/java/com/example/xcpro/tasks/core/TaskWaypointRadiusContractTest.kt`
     - `feature/map/src/test/java/com/example/xcpro/tasks/core/TaskWaypointCustomParamsTest.kt`
     - `feature/map/src/test/java/com/example/xcpro/tasks/TaskPersistSerializerFidelityTest.kt`
- Action: Updated execution contract + change plan with explicit Run C `#30` file-level migration steps and acceptance criteria.

## Run 13 Result (2026-02-23)
- Completed: Additional deep static re-pass for backlog `#30` to verify radius contracts in coordinator/viewmodel and racing manager/model internals.
- New/confirmed misses for `#30` (no code changes in this run):
  1. Unsuffixed radius update APIs still carry km semantics through non-boundary internal layers:
     - `feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
  2. Racing waypoint manager remains km-canonical for radius defaults/mutations:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointManager.kt`
  3. Core compatibility helpers remain unused and keep km surfaces alive:
     - `feature/map/src/main/java/com/example/xcpro/tasks/core/Models.kt` (`withCustomRadiusKm`, `getEffectiveRadius`)
  4. Navigation support still depends on km-native raw radius fields in non-display internals:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngineSupport.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`
- Action: Expanded Run C execution scope in plan/contract docs to include coordinator/viewmodel API migration and racing manager/navigation signature cleanup.

## Run 14 Result (2026-02-23)
- Completed: Deep static re-pass for `#30` focused on racing manager bridge APIs, task import wiring, and radius-helper/test debt.
- New/confirmed misses for `#30` (no code changes in this run):
  1. Racing manager bridge/update APIs are still unsuffixed and km-semantic:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
  2. Racing OZ import path in viewmodel still converts meters back to km for internal update flow:
     - `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
  3. Dead km helper remains in racing model:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt` (`effectiveRadius`)
  4. Navigation support test fixtures remain km-oriented and must be migrated with API changes:
     - `feature/map/src/test/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngineSupportTest.kt`
     - `feature/map/src/test/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngineTest.kt`
- Action: Updated execution contract and change plan to include manager-bridge parameter migration, racing import-path cleanup, dead-helper removal, and test fixture migration in Run C.

## Run 15 Result (2026-02-23)
- Completed: Implemented the Run 14 `#30` misses in production code and focused tests:
  1. Racing manager bridge/update APIs migrated to meter-named contracts.
  2. Racing import-path km conversion removed from `TaskSheetViewModel.applyRacingObservationZone`.
  3. Dead `RacingWaypoint.effectiveRadius` helper removed.
  4. Navigation-support fixtures migrated to meter-oriented setup.
  5. Racing custom-parameter contract migrated to meter-named keys/fields with legacy read compatibility.
- New/confirmed residual misses for `#30` (re-check findings):
  1. Racing model canonical storage is still km-backed (`gateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`) with meter accessors layered on top:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt` (`racingWaypoints` bridge)
  2. `RacingWaypointManager` mutation/default paths remain km-canonical internally:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointManager.kt`
  3. Internal racing projection surfaces still dual-write `customRadius` + `customRadiusMeters`:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
  4. One navigation guard still reads the raw km field directly:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`
  5. Radius contract unit tests still encode compatibility-first expectations rather than SI-first internal contract guarantees:
     - `feature/map/src/test/java/com/example/xcpro/tasks/core/TaskWaypointRadiusContractTest.kt`
- Action: Keep `#30` open and narrow Run C follow-up to racing model/storage canonization and dual-write boundary tightening.

## Run 16 Result (2026-02-23)
- Completed: Deep static code re-check for backlog `#30` across racing model/manager/navigation/display paths and UNITS plan docs.
- New/confirmed residual misses for `#30` (no production code changes in this run):
  1. Racing model factory still carries km compatibility inputs in active internal mutation/default call paths (`customGateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`):
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointManager.kt`
  2. Raw km field access is still present in racing display diagnostics:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
  3. Prior Run 15 residuals remain open and are reconfirmed:
     - km-canonical racing model storage (`RacingWaypoint`, `RacingTask.racingWaypoints`)
     - km-canonical `RacingWaypointManager` defaults/mutations
     - internal dual-write of `customRadius` + `customRadiusMeters` in racing projection paths
     - raw km guard in `RacingZoneDetector`
     - compatibility-first assertions in `TaskWaypointRadiusContractTest`
- Action: Keep `#30` open; expand remaining scope to include model-factory contract cleanup and residual raw-field access cleanup before closure.

## Run 17 Result (2026-02-23)
- Completed: Additional static code re-check for `#30` focused on core radius helper behavior and internal engine normalization paths.
- New/confirmed residual misses for `#30` (no production code changes in this run):
  1. Core radius helper still dual-writes km compatibility field in internal flows:
     - `feature/map/src/main/java/com/example/xcpro/tasks/core/Models.kt` (`TaskWaypoint.withCustomRadiusMeters`)
  2. That core helper is still used in active internal engine normalization/update paths, so km field propagation is not boundary-only:
     - `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/AATTaskWaypointCodec.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/domain/engine/DefaultAATTaskEngine.kt`
  3. Internal-domain test intent still validates km mirror behavior (compatibility-first) instead of SI-only internal ownership:
     - `feature/map/src/test/java/com/example/xcpro/tasks/domain/engine/DefaultAATTaskEngineTest.kt`
- Action: Keep `#30` open; include core helper dual-write reduction and internal engine normalization cleanup in final tranche before closure.

## Run 18 Result (2026-02-23)
- Completed: Additional static code re-check for `#30` focused on residual km-field reads in racing UI/model call paths.
- New/confirmed residual misses for `#30` (no production code changes in this run):
  1. Racing UI still references the legacy km radius field in an active internal state path:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingWaypointListItems.kt` (`taskWaypoint.customRadius` in `remember(...)` key set)
  2. Previously tracked Run 17 residuals remain open (core helper dual-write, engine normalization propagation, compatibility-first internal test intent).
- Action: Keep `#30` open; include racing UI km-field read cleanup in the remaining compatibility-cut scope.

## Run 19 Result (2026-02-23)
- Completed: Implemented a focused `#30` cleanup tranche in active core/racing/AAT internal projection paths:
  1. Removed internal km mirror dual-write in task projections:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`
  2. Removed remaining internal legacy `customRadius` compose-state dependency in AAT list wiring:
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageListItems.kt`
  3. Removed residual AAT gesture path dependency on `resolvedCustomRadiusKm()`:
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/gestures/AatGestureHandler.kt`
  4. Removed raw km display/diagnostic usage in racing finish-line rendering path:
     - `feature/map/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
- Verification:
  1. PASS: `:feature:map:testDebugUnitTest` focused SI tests for task/AAT/racing changed paths.
  2. PASS: `enforceRules`.
  3. PASS: `testDebugUnitTest`.
  4. PASS: `assembleDebug`.
  5. NOT RUN: instrumentation commands in this tranche.
- Residual `#30` scope (still open):
  1. Racing model canonical radius storage remains km-backed (`RacingWaypoint`, `RacingTask.racingWaypoints`).
  2. `RacingWaypointManager` defaults/mutations still operate on km-backed model fields.
  3. `RacingWaypoint.createWithStandardizedDefaults` still carries km compatibility constructor inputs used in internal manager/model paths.
  4. Radius contract tests still emphasize compatibility behavior alongside SI checks (`TaskPersistSerializerFidelityTest`, `TaskWaypointCustomParamsTest`).

## Run 20 Result (2026-02-23)
- Completed: Implemented Step-1 `#30` model-storage migration tranche for racing radius contracts:
  1. Migrated `RacingWaypoint` canonical radius storage to meter-native fields (`gateWidthMeters`, `keyholeInnerRadiusMeters`, `faiQuadrantOuterRadiusMeters`) with deprecated km views kept as boundary compatibility only.
  2. Migrated `RacingTask.racingWaypoints` bridge construction to meter-native `RacingWaypoint` constructor fields.
  3. Migrated `RacingWaypointManager` defaults/mutations/replacement/reorder/update paths to meter-native storage fields and removed the last internal use of legacy `customGateWidth` (km compatibility input).
- Verification:
  1. PASS: `:feature:map:compileDebugKotlin`.
  2. PASS: `:feature:map:testDebugUnitTest` focused racing SI suite (`RacingNavigationEngineSupportTest`, `RacingNavigationEngineTest`, `TaskManagerCoordinatorTest`, `RacingGeometryUtilsTest`).
  3. PASS: `enforceRules`.
- Residual `#30` scope (still open):
  1. Radius contract tests still need SI-first assertion intent with compatibility checked only at explicit persistence/protocol boundaries (`TaskPersistSerializerFidelityTest`, `TaskWaypointCustomParamsTest`).

## Run 21 Result (2026-02-23)
- Completed: Closed remaining `#30` test-contract and boundary-hardening scope:
  1. Hardened persistence boundary rehydration to SI-first internals by clearing legacy km field on `TaskWaypoint` import (`TaskPersistSerializer.toTask` now sets `customRadius = null` and keeps canonical `customRadiusMeters`).
  2. Migrated radius-contract tests to SI-first intent (`TaskWaypointRadiusContractTest`) and added explicit legacy-compatibility-fallback coverage.
  3. Expanded custom-params coverage to enforce meter-key precedence and legacy-key boundary behavior (`TaskWaypointCustomParamsTest`).
  4. Updated serializer fidelity coverage so persistence/protocol compatibility is asserted at boundary payloads while internal tasks remain SI-canonical (`TaskPersistSerializerFidelityTest`).
- Verification:
  1. PASS: `:feature:map:testDebugUnitTest` focused radius/serializer/tests (`TaskWaypointRadiusContractTest`, `TaskWaypointCustomParamsTest`, `TaskPersistSerializerFidelityTest`).
  2. PASS: `:feature:map:testDebugUnitTest` focused engine/coordinator regression checks (`DefaultAATTaskEngineTest`, `TaskManagerCoordinatorTest`).
  3. PASS: `:feature:map:compileDebugKotlin`.
  4. PASS: `enforceRules`.
- Residual `#30` scope: none.

## Run 22 Result (2026-02-23)
- Completed: Deep static/targeted code re-check focused on backlog `#13` (boundary adapter conversion tests) with no production code edits in this run.
- New/confirmed residual misses for `#13`:
  1. ADS-B boundary request-contract coverage is incomplete:
     - no test currently asserts `AdsbTrafficRepositoryImpl` passes a bbox derived from `maxDistanceKm` into provider calls and updates that bbox when distance filters change.
     - no test currently asserts `OpenSkyProviderClient` request query serialization (`lamin/lomin/lamax/lomax`) and auth header behavior at the HTTP boundary.
  2. OGN boundary adapter coverage is incomplete:
     - no integration-level test currently asserts OGN login/filter payload composition (`r/{lat}/{lon}/{radiusKm}`) including km-radius boundary formatting.
     - existing OGN policy tests cover center preference and haversine math, but do not lock exact boundary-threshold behavior at `radiusMeters` cut lines.
  3. Replay boundary conversion coverage is incomplete:
     - `ReplaySampleEmitterTest` does not assert absolute `km/h -> m/s` conversion contracts for IAS/TAS ingestion paths (both-present, IAS-only, TAS-only).
     - no replay test currently locks racing synthetic replay speed boundary conversion (`targetSpeedKmh -> speedMs`) in `RacingReplayLogBuilder`.
- Action: Keep `#13` open; expand Run D plan and test backlog with explicit adapter-level cases before compliance closeout.

## Run 23 Result (2026-02-23)
- Completed: Additional deep static code re-check focused on backlog `#13` (boundary adapter conversion tests) with no production code edits in this run.
- New/confirmed residual misses for `#13` (expanded scope):
  1. ADS-B boundary adapter coverage is still incomplete:
     - no repository-level test asserts `updateDisplayFilters(maxDistanceKm)` clamp behavior (min/max) is propagated into provider `BBox` radius inputs.
     - no HTTP boundary test asserts OpenSky query contract includes `extended=1` and locale-stable 6-decimal coordinate serialization.
     - no HTTP boundary test asserts `Authorization` header is emitted only for non-blank bearer tokens.
  2. OGN boundary adapter coverage is still incomplete:
     - no repository/socket-harness test asserts APRS login filter payload precision (`r/{lat5}/{lon5}/{radiusKmInt}` with `%.5f` coordinates and integer km radius).
     - no reconnect-path test asserts center movement over threshold triggers reconnect with refreshed login filter center coordinates.
     - no exact edge test asserts receive-radius equality/epsilon behavior (`distanceMeters == radiusMeters` include, slight over-radius exclude).
  3. Replay boundary adapter coverage is still incomplete:
     - `ReplaySampleEmitterTest` still lacks boundary reset assertions for null/non-finite IAS/TAS ingress values at the km/h boundary.
     - no dedicated `RacingReplayLogBuilder` unit test class exists to lock `targetSpeedKmh -> speedMs` conversion and step-quantized timing behavior.
- Action: Keep `#13` open; expand Run D with the above concrete boundary assertions before SI compliance closeout.

## Run 24 Result (2026-02-23)
- Completed: Implemented backlog `#13` boundary adapter test tranche.
  1. ADS-B:
     - added repository-level bbox propagation coverage with clamp min/max assertions in `AdsbTrafficRepositoryTest`.
     - added HTTP boundary coverage for OpenSky query serialization (`lamin/lomin/lamax/lomax`, `extended=1`, fixed 6-decimal formatting) and conditional auth header behavior in `OpenSkyProviderClientTest`.
  2. OGN:
     - added socket-harness repository tests for APRS login filter payload precision and reconnect-center filter refresh in `OgnTrafficRepositoryConnectionTest`.
     - added exact receive-radius boundary include/exclude edge tests in `OgnTrafficRepositoryPolicyTest`.
  3. Replay:
     - expanded `ReplaySampleEmitterTest` for IAS/TAS `km/h -> m/s` conversion contracts (both-present, IAS-only, TAS-only) plus null/non-finite ingress reset behavior.
     - added dedicated `RacingReplayLogBuilderTest` covering `targetSpeedKmh -> speedMs` timing conversion and step quantization.
- Verification:
  1. PASS: `:feature:map:testDebugUnitTest` targeted `#13` suites (ADS-B/OGN/replay/racing replay builder).
  2. PASS: `enforceRules testDebugUnitTest assembleDebug`.
- Action: Mark `#13` done; remaining open cleanup is `#28/#34`.

## Run 25 Result (2026-02-23)
- Completed: Deep static code re-check focused on backlog `#34` and `#28` (no production code changes in this run).
- Misses confirmed:
  1. `#28` remains open:
     - `feature/map/src/main/java/com/example/xcpro/gestures/AirspaceGestureMath.kt` still exposes unused km helper `haversineDistance(...)` with no call sites in main/test sources.
  2. `#34` target 1 remains open:
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` still has `calculateDistanceInArea(...)` with an unsuffixed return contract and km accumulation via `AATMathUtils.calculateDistance(...)`.
     - method appears dead (no call sites found in repo).
  3. `#34` target 2 remains open:
     - `feature/map/src/main/java/com/example/xcpro/tasks/KeyholeVerification.kt` remains legacy commented-out source containing km conversions and local km haversine helper.
  4. Adjacent residuals discovered during this re-check (not previously tracked under `#34`):
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/ui/AATLongPressOverlay.kt` has a local km haversine helper and meter-to-km conversion in hit-test logic; file appears unused.
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/interaction/AATEditGeometry.kt` still exposes a km wrapper `haversineDistance(...)` that appears unused.
- Action: Keep `#28/#34` open and expand Run D closeout scope to include these adjacent dead/unused km-helper surfaces so compliance sign-off does not leave residual drift.

## Run 26 Result (2026-02-23)
- Completed: Additional focused static re-check for backlog `#34` and `#28`, including active call-path validation in AAT quick-validation flow (no production code changes in this run).
- Findings:
  1. `#28` and `#34` remain open with unchanged status from Run 25.
  2. New adjacent miss identified:
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` exposes `calculateAreaSizeKm2(...)` and this is actively used by `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt`.
     - this keeps active internal quick-validation policy comparisons on km2 contracts (`areaSizeKm2 < 10.0`, `> 5000.0`) instead of SI m2.
  3. Boundary-output labeling mismatch also present in the same warning path (`km` text for area-size values).
- Action: Added backlog item `#40` for AAT area-size contract normalization/explicit boundary scoping as part of Run D closeout.

## Run 27 Result (2026-02-23)
- Completed: Additional deep static re-check for backlog `#34` and `#28` with explicit call-site sweep of AAT edit-geometry compatibility wrappers (no production code changes in this run).
- Findings:
  1. `#28` remains open with unchanged status (`AirspaceGestureMath.haversineDistance` still unused).
  2. `#34` remains open with unchanged status (`calculateDistanceInArea` dead unsuffixed km contract + commented `KeyholeVerification`).
  3. New adjacent miss identified for `#34` closeout scope:
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/interaction/AATEditGeometry.kt` has additional unused km wrappers beyond `haversineDistance(...)`:
       - `generateCircleCoordinates(...)`,
       - `generateSectorCoordinates(...)`,
       - `calculateDestinationPoint(...)`.
     - no call sites were found for these km wrappers in main/test sources.
- Action: Expanded `#34` cleanup scope to remove or boundary-scope the full dead km-wrapper set in `AATEditGeometry`.

## Run 28 Result (2026-02-23)
- Completed: Triple-pass sequence pass 1 for `#34/#28` (focused dead-code/call-site revalidation, no production code changes).
- Findings:
  1. `#28` unchanged: `AirspaceGestureMath.haversineDistance(...)` remains unused.
  2. `#34` unchanged: dead unsuffixed `calculateDistanceInArea(...)` and commented `KeyholeVerification` remain.
  3. Adjacent items `AATLongPressOverlay` and `AATEditGeometry` km wrappers remain unresolved.
- Action: Keep `#28/#34/#40` open.

## Run 29 Result (2026-02-23)
- Completed: Triple-pass sequence pass 2 for `#34/#28` with deeper AAT area-size helper sweep (no production code changes).
- Findings:
  1. `#40` scope expanded: active km2 contracts are not only at `AreaBoundaryCalculator`; they are implemented in:
     - `CircleAreaCalculator.calculateAreaSizeKm2(...)`,
     - `SectorAreaCalculator.calculateAreaSizeKm2(...)`,
     - `SectorAreaGeometrySupport.calculateAreaSizeKm2(...)`,
     and consumed via `AreaBoundaryCalculator.calculateAreaSizeKm2(...)` in `AATTaskQuickValidationEngine`.
  2. Quick-validation area-size warnings still display `km` labels rather than squared units.
- Action: Expanded `#40` implementation scope to include underlying calculator/support helpers and warning-label correction.

## Run 30 Result (2026-02-23)
- Completed: Triple-pass sequence pass 3 for `#34/#28` with CI guardrail coverage sweep (no production code changes).
- Findings:
  1. `scripts/ci/enforce_rules.ps1` has no explicit guard patterns for:
     - dead km helper/file reintroduction in `AirspaceGestureMath` / `KeyholeVerification`,
     - `AATLongPressOverlay` local km haversine contract,
     - `AATEditGeometry` km wrapper set,
     - active km2 policy usage/labeling drift in quick-validation area-size path.
  2. Existing rule `fun haversineDistance(` is limited to `TaskManagerCoordinator` and does not cover the `#28/#34/#40` surfaces.
- Action: Added backlog item `#41` for static guard expansion to prevent reintroduction after cleanup.

## Run 31 Result (2026-02-23)
- Completed: Triple-pass sequence pass 1 for this cycle (`#34/#28` call-site/dead-code sweep, no production code changes).
- Findings:
  1. `#28` unchanged: `AirspaceGestureMath.haversineDistance(...)` remains unused.
  2. `#34` unchanged: dead unsuffixed `calculateDistanceInArea(...)` and commented `KeyholeVerification` remain.
  3. Previously tracked adjacent wrappers (`AATLongPressOverlay`, `AATEditGeometry`) remain unresolved.
- Action: No scope change from Run 30 for production cleanup items.

## Run 32 Result (2026-02-23)
- Completed: Triple-pass sequence pass 2 for this cycle (AAT quick-validation + test coverage sweep, no production code changes).
- Findings:
  1. `#40` remains active through the same km2 helper chain and quick-validation warning path.
  2. New adjacent miss identified: current unit tests do not lock `#40` behavior:
     - `feature/map/src/test/java/com/example/xcpro/tasks/aat/AATTaskQuickValidationEngineUnitsTest.kt` only covers start/finish meter-threshold behavior.
     - no tests currently assert area-size SI policy contract or squared-unit output labeling.
- Action: Added backlog item `#42` for `#40` test hardening.

## Run 33 Result (2026-02-23)
- Completed: Triple-pass sequence pass 3 for this cycle (guardrail + doc parity sweep, no production code changes).
- Findings:
  1. `#41` guardrail gap remains open in `scripts/ci/enforce_rules.ps1` (no new checks added yet).
  2. Verification-plan parity miss: SI closeout tracking text still referenced only `#28/#34` in some matrix notes and did not reflect expanded residual scope (`#40/#41`).
- Action: Updated UNITS verification/doc plan notes to keep residual scope explicit for compliance closeout.

## Run 34 Result (2026-02-23)
- Completed: Triple-pass sequence pass 1 for this cycle (`#34/#28` dead-code/call-site sweep, no production code changes).
- Findings:
  1. `#28` unchanged: `AirspaceGestureMath.haversineDistance(...)` remains unused.
  2. `#34` unchanged: dead unsuffixed `calculateDistanceInArea(...)` and commented `KeyholeVerification` remain.
  3. New adjacent miss identified:
     - `feature/map/src/main/java/com/example/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt` still exposes unused km compatibility wrappers:
       - `generateCircleCoordinates(...)`,
       - `generateStartLine(...)`,
       - `generateFinishLine(...)`,
       - `calculateDestinationPoint(...)`.
     - no main/test call sites were found for these wrappers; active callers use `*Meters` APIs.
- Action: Added backlog item `#43` for `AATGeometryGenerator` dead km-wrapper cleanup.

## Run 35 Result (2026-02-23)
- Completed: Triple-pass sequence pass 2 for this cycle (AAT area-size + test coverage re-check, no production code changes).
- Findings:
  1. `#40` remains open through active `calculateAreaSizeKm2(...)` helper chain and quick-validation area-size policy path.
  2. `#42` remains open:
     - `AATTaskQuickValidationEngineUnitsTest` still does not assert area-size SI policy behavior or squared-unit output labeling.
- Action: No additional scope expansion beyond `#40/#42`; keep both open.

## Run 36 Result (2026-02-23)
- Completed: Triple-pass sequence pass 3 for this cycle (guardrail coverage re-check, no production code changes).
- Findings:
  1. `#41` remains open:
     - `scripts/ci/enforce_rules.ps1` still has no explicit checks for `#28/#34/#40` surfaces.
  2. New guardrail adjacency tied to Run 34:
     - no static checks currently block reintroduction of unused km compatibility wrappers in `AATGeometryGenerator` (`#43`).
- Action: Expand `#41` implementation scope to include static protection for `#43` once wrapper cleanup lands.

## Run 37 Result (2026-02-23)
- Completed: Focused `#34/#28` re-check pass (dead-code/call-site + active quick-validation + guardrail sweep, no production code changes).
- Findings:
  1. `#28` unchanged: `AirspaceGestureMath.haversineDistance(...)` remains unused.
  2. `#34` unchanged: `AreaBoundaryCalculator.calculateDistanceInArea(...)` and commented `KeyholeVerification` remain.
  3. Adjacent residual scope unchanged:
     - `#40` active area-size km2 chain + warning label drift remains.
     - `#42` area-size quick-validation test gap remains.
     - `#41` static guard gap remains, including `#43` wrapper reintroduction protection.
  4. No new residual item was identified beyond already tracked `#43`.
- Action: Keep closeout scope unchanged at `#28/#34/#40/#41/#42/#43`.

## Run 38 Result (2026-02-23)
- Completed: Implemented closeout cleanup for `#28/#34/#40/#41/#42/#43`.
  1. Removed dead helper files:
     - `feature/map/src/main/java/com/example/xcpro/gestures/AirspaceGestureMath.kt`
     - `feature/map/src/main/java/com/example/xcpro/tasks/KeyholeVerification.kt`
  2. Removed dead/unused km helper contracts and migrated retained paths to meter-first APIs:
     - removed `AreaBoundaryCalculator.calculateDistanceInArea(...)`,
     - migrated `AATLongPressOverlay` hit-test distance to `AATMathUtils.calculateDistanceMeters(...)`,
     - removed unused km wrappers from `AATEditGeometry` and `AATGeometryGenerator`.
  3. Normalized quick-validation area-size internals to SI m2 and fixed squared-unit warning labels:
     - new meter-first `calculateAreaSizeMeters2(...)` chain in area calculators/boundary adapter,
     - `AATTaskQuickValidationEngine` now compares against m2 thresholds and formats warning units as `km2`.
  4. Added area-size SI/label regression tests:
     - extended `AATTaskQuickValidationEngineUnitsTest` with small/large area warning assertions (`km2` labels).
  5. Expanded static guardrails in `scripts/ci/enforce_rules.ps1`:
     - prevent reintroduction of dead helper files,
     - block km-wrapper reintroduction in `AATEditGeometry` and `AATGeometryGenerator`,
     - block local km haversine helper in `AATLongPressOverlay`,
     - enforce `AATTaskQuickValidationEngine` area-size m2-internal contract and squared-unit labels.
- Verification:
  1. PASS: `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.aat.AATTaskQuickValidationEngineUnitsTest" --no-daemon --no-configuration-cache`
  2. PASS: `./gradlew enforceRules --no-daemon --no-configuration-cache`
  3. PASS: `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
  4. PASS: `./gradlew assembleDebug --no-daemon --no-configuration-cache`
- Action: Mark `#28/#34/#40/#41/#42/#43` done.

## Run 39 Result (2026-02-23)
- Completed: `#18` re-check pass 1 (API/call-chain surface audit, no production code changes).
- Findings:
  1. Active legacy AAT km/unsuffixed point-type update chain still exists across internal layers:
     - `TaskSheetViewModel.onUpdateAATWaypointPointType(...)`
     - `TaskSheetCoordinatorUseCase.updateAATWaypointPointType(...)`
     - `TaskManagerCoordinator.updateAATWaypointPointType(...)`
     - `AATCoordinatorDelegate.updateWaypointPointType(...)`
     - `AATTaskManager.updateAATWaypointPointType(...)` / `updateWaypointPointTypeBridge(...)`
     - `AATPointTypeConfigurator.updateWaypointPointType(...)` (km parameters + `*1000` internal conversions)
  2. Meter-first coordinator API still routes through km conversion on AAT branch:
     - `TaskManagerCoordinator.updateWaypointPointType(...)` converts `*Meters` inputs to km before dispatch to AAT manager bridge.
  3. Persisted OZ import still routes through km wrapper path for AAT:
     - `TaskSheetViewModel.applyAatObservationZone(...)` converts meters to km and calls `updateAATWaypointPointType(...)`.
- Action: Keep `#18` open; prioritize meter-first migration of AAT point-type update contracts end-to-end.

## Run 40 Result (2026-02-23)
- Completed: `#18` re-check pass 2 (AAT gesture/camera radius contract audit, no production code changes).
- Findings:
  1. Internal runtime gesture contract still km-based:
     - `TaskGestureCallbacks.onEnterEditMode(..., radiusKm)`
     - `AatGestureHandler.enterEditMode(...)` converts meters to km.
  2. Map gesture/camera chain consumes km internally:
     - `MapGestureSetup` forwards `radiusKm` to camera manager.
     - `MapCameraManager.zoomToAATAreaForEdit(..., turnpointRadiusKm)` uses km-native naming and km-first math path.
- Action: Keep `#18` open; include gesture/camera radius contract migration to meters in wrapper-cut scope.

## Run 41 Result (2026-02-23)
- Completed: `#18` re-check pass 3 (dead compatibility-wrapper inventory, no production code changes).
- Findings:
  1. Dead km wrapper in core model (no production callers):
     - `TaskWaypoint.resolvedCustomRadiusKm()`.
  2. AAT km compatibility math wrappers remain with no production callers:
     - `AATMathUtils.calculateDistance(...)`
     - `AATMathUtils.calculateDistanceKm(...)`
     - `AATMathUtils.calculateCrossTrackDistance(...)`
     - `AATMathUtils.calculateAlongTrackDistance(...)`
  3. AAT area-size km2 wrappers remain and are now dead after m2 migration:
     - `AreaBoundaryCalculator.calculateAreaSizeKm2(...)`
     - `CircleAreaCalculator.calculateAreaSizeKm2(...)`
     - `SectorAreaCalculator.calculateAreaSizeKm2(...)`
     - `SectorAreaGeometrySupport.calculateAreaSizeKm2(...)`
  4. Racing km compatibility wrappers/views remain dead in production:
     - `RacingGeometryUtils.haversineDistance(...)`
     - `RacingWaypoint` deprecated km views (`gateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`).
- Action: Keep `#18` open; remove dead wrappers or explicitly confine them to tested boundary adapters only.

## Run 42 Result (2026-02-23)
- Completed: `#18` re-check pass 4 (test-coupling audit for wrapper removal readiness, no production code changes).
- Findings:
  1. Tests still depend on km wrappers and block clean removal:
     - `TaskWaypointRadiusContractTest` uses `resolvedCustomRadiusKm()`.
     - `AATInteractiveTurnpointManagerValidationTest` uses `AATMathUtils.calculateDistanceKm(...)`.
     - `AATEditGeometryValidatorTest` uses `AATMathUtils.calculateDistanceKm(...)`.
     - `AreaCalculatorUnitsTest` and `AATTaskDisplayGeometryBuilderUnitsTest` use `AATMathUtils.calculateDistance(...) * 1000`.
     - `RacingGeometryUtilsTest` uses `RacingGeometryUtils.haversineDistance(...)`.
- Action: Keep `#18` open; migrate test fixtures/assertions to meter-first helpers before deleting wrappers.

## Run 43 Result (2026-02-23)
- Completed: `#18` re-check pass 5 (static guardrail audit, no production code changes).
- Findings:
  1. `scripts/ci/enforce_rules.ps1` has no `#18`-specific checks to prevent reintroduction of:
     - legacy AAT point-type km wrapper route (`updateAATWaypointPointType` chain),
     - internal `radiusKm` gesture/camera contracts,
     - dead km wrapper declarations/usages in core/AAT/racing utility surfaces.
- Action: Keep `#18` open; expand static guardrails as part of `#18` closeout implementation tranche.

## Run 44 Result (2026-02-23)
- Completed: Implemented backlog `#18` compatibility-wrapper cut pass across task/AAT/racing internals.
- Implemented:
  1. Migrated active AAT point-type update chain to meter-first contracts end-to-end:
     - `TaskSheetViewModel` -> `TaskSheetCoordinatorUseCase` -> `TaskManagerCoordinator` -> `AATCoordinatorDelegate` -> `AATTaskManager` -> `AATPointTypeConfigurator`.
     - removed internal `*Meters -> km` conversion route in coordinator AAT dispatch.
  2. Migrated AAT gesture/camera edit-radius runtime chain to meters:
     - `TaskGestureCallbacks`, `AatGestureHandler`, `MapGestureSetup`, `MapCameraManager`.
  3. Removed dead km wrappers/views identified in Run 41:
     - `TaskWaypoint.resolvedCustomRadiusKm`,
     - dead km wrappers in `AATMathUtils`,
     - dead km2 wrappers in AAT area calculators,
     - `RacingGeometryUtils.haversineDistance`,
     - deprecated racing km view properties in `RacingWaypoint`.
  4. Migrated km-coupled tests to meter-first assertions/fixtures:
     - `TaskWaypointRadiusContractTest`,
     - `AATInteractiveTurnpointManagerValidationTest`,
     - `AATEditGeometryValidatorTest`,
     - `AreaCalculatorUnitsTest`,
     - `AATTaskDisplayGeometryBuilderUnitsTest`,
     - `RacingGeometryUtilsTest`,
     - plus `TaskSheetViewModelImportTest` and `AATCoordinatorDelegateTest` for new meter-named contracts.
  5. Added static guardrails in `scripts/ci/enforce_rules.ps1` for `#18` closure:
     - block legacy `updateAATWaypointPointType` route reintroduction,
     - block `radiusKm`/`turnpointRadiusKm` gesture/camera contracts,
     - block removed km wrapper/helper surfaces and removed racing km view properties.
- Verification:
  1. PASS: `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.example.xcpro.tasks.AATCoordinatorDelegateTest" --tests "com.example.xcpro.tasks.core.TaskWaypointRadiusContractTest" --tests "com.example.xcpro.tasks.aat.AATInteractiveTurnpointManagerValidationTest" --tests "com.example.xcpro.tasks.aat.interaction.AATEditGeometryValidatorTest" --tests "com.example.xcpro.tasks.aat.areas.AreaCalculatorUnitsTest" --tests "com.example.xcpro.tasks.aat.AATTaskDisplayGeometryBuilderUnitsTest" --tests "com.example.xcpro.tasks.racing.RacingGeometryUtilsTest"` (Kotlin daemon fallback warnings observed; build passed).
  2. PARTIAL: `./scripts/ci/enforce_rules.ps1` still fails early on pre-existing unrelated repository issues (`TaskManagerCompat` rule hit + existing rg no-files handling), so full-script green status is not attributable to this tranche.
  3. PASS (targeted static checks): all newly added `#18` guard patterns return no matches.

## Priority Legend
- P0: correctness bug risk
- P1: high-value consistency
- P2: cleanup/hardening

## Backlog

1. Done (Re-pass #9) - Fixed `AATPathOptimizerSupport` km-vs-meter mismatch (`OPTIMIZATION_TOLERANCE_METERS` and path distance unit).
2. Done (Re-pass #9) - Fixed `AATPathOptimizer` target distance unit consistency (`targetDistanceMeters` math vs km path functions).
3. Done (Re-pass #9) - Fixed `AATFlightPathValidator` start/finish checks (km distance compared to meter thresholds).
4. Done (Re-pass #9) - Fixed `AATTaskQuickValidationEngine` distance checks (km compared to meter thresholds), including `validateStart` and `validateFinish`.
5. Done (Re-pass #16) - Fixed `AATTaskSafetyValidator` meter contract regression (`distanceMeters` now sourced from `calculateDistanceMeters`).
6. Done (Re-pass #9) - Fixed `CircleAreaCalculator` and `SectorAreaCalculator` distance/radius unit mismatch.
7. Done (Re-pass #9) - Fixed `AATTaskPerformanceSupport` assignment to `AATAreaAchievement.distanceFromCenter` (meters field).
8. Done (Re-pass #11) - Normalized AAT manager/coordinator internal distance contracts to meters.
9. Done (Re-pass #12) - Normalized Racing manager/coordinator/helper/turnpoint/navigation/boundary internal distance contracts to meters.
10. Done (Re-pass #11) - Renamed ambiguous task distance APIs with explicit unit suffixes and compatibility wrappers.
11. Done (Re-pass #9) - Added AAT validation/path optimization unit coverage including explicit start/finish threshold cases.
12. Partial (Re-pass #12) - Added racing geometry meter-contract coverage and meter-native boundary/coordinator assertions; broader racing/aat fixture matrix still pending.
13. Done (Re-pass #33) - Added boundary adapter tests for ADS-B/OGN/replay conversions.
    - ADS-B:
      - repository-level `maxDistanceKm` bbox propagation + clamp-edge coverage in `AdsbTrafficRepositoryTest`.
      - OpenSky HTTP boundary serialization/auth-header coverage in `OpenSkyProviderClientTest`.
    - OGN:
      - APRS login payload precision + reconnect-center filter refresh coverage in `OgnTrafficRepositoryConnectionTest`.
      - exact receive-radius edge coverage in `OgnTrafficRepositoryPolicyTest`.
    - Replay:
      - IAS/TAS `km/h -> m/s` conversion + null/non-finite reset coverage in `ReplaySampleEmitterTest`.
      - `targetSpeedKmh -> speedMs` and timing quantization coverage in `RacingReplayLogBuilderTest`.
14. Done (Re-pass #9) - Fixed replay movement snapshot contract: `MovementSnapshot.distanceMeters` stores distance in meters (not speed in m/s) in `ReplayRuntimeInterpolator`, with heading-gating regression tests for `ReplayHeadingResolver`.
15. Done (Re-pass #9) - Fixed distance-circles output boundary to use `UnitsPreferences`/`UnitsFormatter` (removed hard-coded `km`/`m` labels in `DistanceCirclesCanvas`).
16. Done (Re-pass #9) - Fixed task UI distance outputs (`TaskStatsSection`, minimized indicator, racing selector distance text) to use selected distance units instead of hard-coded `km`.
17. Done (Re-pass #20) - Polar model storage contract migration:
    - SI-normalized storage/contracts (`m/s`) are canonical in glider config/polar internals.
    - Compatibility read for legacy persisted `km/h` keys is implemented in `GliderRepository`.
    - Boundary rule maintained: `km/h` only at UI/input edges.
18. Done (Run 44) - Compatibility-wrapper cut pass:
    - migrated AAT point-type route to meter-first contracts end-to-end.
    - migrated AAT gesture/camera edit-radius runtime contracts from km to meters.
    - removed dead km wrappers/views in core/AAT/racing identified during Runs 39-43.
    - migrated km-coupled tests to meter-first fixtures/assertions.
    - added static `enforce_rules` guardrails for `#18` closure surfaces.
19. Done (Re-pass #16) - Added static checks to block key non-SI regression patterns and hard-coded distance-unit labels on shared distance display surfaces.
20. Done (Re-pass #16) - Final docs synchronized with latest verification and residual-risk status.
21. Done (Re-pass #14) - Migrated `TaskWaypoint.customRadius` internal contract to meter-first (`customRadiusMeters`) with explicit compatibility helpers and boundary conversions.
22. Done (Re-pass #15) - Added meter-first AAT core geometry/maths call paths and migrated active callers (`AATMathUtils`/`AATGeometryGenerator` + renderer/display/task call sites).
23. Done (Re-pass #15) - Migrated remaining AAT interaction/editing internals to meter contracts (`AatGestureHandler`, `AATEditModeState`, `AATAreaTapDetector`, `AATMovablePointStrategySupport`, `AATEditGeometry` + dependent helpers).
24. Done (Re-pass #14) - Migrated `TaskObservationZoneResolver` fallback contracts to meter-first waypoint fields with explicit boundary conversion.
25. Done (Re-pass #15) - Replaced remaining ad-hoc racing replay/coordinator km->m conversions with centralized meter helpers (`RacingReplayAnchorBuilder`, `TaskManagerCoordinator`).
26. Done (Re-pass #15) - Introduced meter-first OGN distance helper/policy API and retained km filter string generation as explicit protocol boundary.
27. Done (Re-pass #16) - AAT edit movement tolerance contract aligned to ~1 m (`TARGET_MOVE_TOLERANCE_METERS = 1.0`) and re-confirmed in latest sweep.
28. Done (Run 38) - Removed dead `AirspaceGestureMath` helper file.
29. Done (Re-pass #19) - Migrated AAT radius authority to meter-canonical APIs:
    - Removed km-canonical `getRadiusForRole/getRadiusForWaypoint/getAuthorityRadius`.
    - Normalized authority usage to `getRadiusMetersForWaypoint` / `getAuthorityRadiusMeters`.
30. Done (Re-pass #30) - Complete compatibility cut of core/racing radius dual fields:
    - Done: Remove internal dependence on `TaskWaypoint.customRadius` km and `resolvedCustomRadiusKm` across racing mapper/initializer/engine/persistence/UI call paths.
    - Done: Normalize `RacingWaypointCustomParams` to explicit meter-named contracts (fields + keys) and migrate resolver call sites.
    - Done: Migrate unsuffixed coordinator/use-case/viewmodel/racing-manager radius APIs to explicit meter contracts (`gateWidthMeters`, `keyholeInnerRadiusMeters`, `faiQuadrantOuterRadiusMeters`) with UI-edge km conversion.
    - Done: Remove racing import-path km conversions in `TaskSheetViewModel.applyRacingObservationZone`.
    - Done: Remove dead `RacingWaypoint.effectiveRadius` km helper and migrate related navigation support tests/fixtures.
    - Done (Run 20): Moved racing model canonical radius storage to meters (`RacingWaypoint` / `RacingTask.racingWaypoints`) with km compatibility retained as boundary-only deprecated views.
    - Done (Run 20): Migrated `RacingWaypointManager` defaults/mutations/signature dependencies to meter-canonical storage.
    - Done (Run 19): Reduced internal dual-write surfaces (`customRadius` + `customRadiusMeters`) in active racing/AAT projection paths (`RacingTaskManager`, `TaskPersistenceAdapters`, `AATTaskManager`).
    - Done (Run 19): Removed raw km field reads in racing display/diagnostic path (`FinishLineDisplay`) and normalized meter-only internals there.
    - Done (Run 20): Moved `RacingWaypoint.createWithStandardizedDefaults` km compatibility inputs to boundary-only usage (internal manager/model call paths meter-only).
    - Done (Run 17): Limited `TaskWaypoint.withCustomRadiusMeters` km-field synchronization so internal engine normalization paths stop propagating km mirrors.
    - Done (Run 17): Internal engine assertions migrated to SI-first behavior (`DefaultAATTaskEngineTest`).
    - Done (Run 18): Removed internal racing UI dependence on legacy `TaskWaypoint.customRadius` reads (`RacingWaypointListItems`).
    - Done (Run 21): Updated radius contract tests to assert SI-first internal behavior while retaining compatibility only at explicit persistence/protocol boundaries (`TaskPersistSerializerFidelityTest`, `TaskWaypointCustomParamsTest`, `TaskWaypointRadiusContractTest`).
31. Done (Re-pass #19) - Removed remaining internal km wrappers from coordinator/use-case/viewmodel and manager baselines:
    - `TaskManagerCoordinator`, `TaskSheetCoordinatorUseCase`, `TaskSheetViewModel`, `AATTaskManager`, `RacingTaskManager`.
32. Done (Re-pass #19) - Normalized interactive AAT distance contracts to meters:
    - `AATGeoMath`, `AATInteractiveDistanceCalculator`, `AATInteractiveModels`, `AATInteractiveTurnpointManager`.
33. Done (Re-pass #19) - Normalized AAT scoring speed contracts to SI (`m/s`) internally:
    - `AATSpeedCalculator` + `AATResult`/`AATSpeedAnalysis` fields.
    - Additional unused km-compat wrappers removed in Run 10.
34. Done (Run 38) - Removed remaining km-based dead/debug helper surfaces:
    - removed `AreaBoundaryCalculator.calculateDistanceInArea(...)`,
    - removed legacy/commented `KeyholeVerification`,
    - migrated `AATLongPressOverlay` to meter-first hit-test distance helper,
    - removed unused km wrappers from `AATEditGeometry`.
35. Done (Re-pass #19) - Removed delegate-level ambiguous deprecated distance wrappers from internal contracts:
    - `TaskTypeCoordinatorDelegate` now exposes explicit meter APIs only.
36. Done (Re-pass #19) - Removed calculator-level internal km wrapper/deprecated distance surfaces:
    - `AATTaskCalculator` and `RacingTaskCalculator` distance surfaces are meters-only.
37. Done (Re-pass #19) - Migrated `AATWaypoint` target-offset contract to SI meters:
    - `targetPointOffsetMeters` and meter-validity checks are in place.
38. Done (Re-pass #19) - Normalized AAT competition-validation internals to SI:
    - internal thresholds/calculations use SI; km/km/h retained only in formatted messages.
39. Done (Re-pass #19) - Racing result speed contract migrated:
    - `RacingTaskResultModels.averageSpeedMs` is SI-native.
    - model still appears unused; optional dead-code cleanup can follow separately.
40. Done (Run 38) - Normalized AAT area-size contracts to SI m2 internally:
    - added meter-first area-size contracts in `AreaBoundaryCalculator`, `CircleAreaCalculator`, `SectorAreaCalculator`, and `SectorAreaGeometrySupport`,
    - `AATTaskQuickValidationEngine` now uses m2 thresholds internally with `km2` reserved for warning formatting only.
41. Done (Run 38) - Extended static enforcement to block `#28/#34/#40/#43` reintroduction patterns:
    - dead helper file reintroduction checks,
    - AAT edit/geometry km-wrapper reintroduction checks,
    - quick-validation area-size contract/label checks.
42. Done (Run 38) - Added explicit area-size SI/label tests:
    - `AATTaskQuickValidationEngineUnitsTest` now asserts small/large area warnings and squared-unit labeling.
43. Done (Run 38) - Removed unused km wrappers in `AATGeometryGenerator`:
    - removed `generateCircleCoordinates(...)`,
    - removed `generateStartLine(...)`,
    - removed `generateFinishLine(...)`,
    - removed `calculateDestinationPoint(...)`.

Autonomous execution contract:
- Execute remaining open backlog items in priority order using
  `docs/UNITS/AGENT_EXECUTION_CONTRACT_SI_REMAINING_P0_P1_2026-02-22.md` Section 9.

## Suggested PR Split
1. PR-1: AAT P0 correctness fixes + tests.
2. PR-2: Task manager/coordinator SI contract normalization.
3. PR-3: Racing SI normalization + tests.
4. PR-4: Compatibility-wrapper cut (task/AAT/racing internal wrappers).
5. PR-5: Polar SI storage migration + versioned persistence compatibility.
6. PR-6: Radius-contract normalization (AAT authority + core/racing params) + caller updates.
7. PR-7: AAT interactive/scoring SI contract migration (distance + speed internals).
8. PR-8: AAT competition validation/scoring SI normalization (FAI thresholds/rules internal SI contracts).
9. PR-9: Boundary adapter hardening + compliance sign-off (including stale/dead-model cleanup).
