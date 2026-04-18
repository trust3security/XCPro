# Change Plan: SI Units Compliance

Date: 2026-02-22
Status: In progress (Run 46 closed non-`#12` `enforce_rules` caveat; remaining focus is repo-wide verification hygiene)
Scope: `feature/map`, `core/common`, `dfcards-library`, task modules

## Execution Progress

### Run 1 (2026-02-22)
Completed:
1. Fixed racing optimal start-line gate-width unit defect (km input converted to meter geometry contract).
2. Fixed replay runtime movement snapshot contract (`distanceMeters` now stores meters, not m/s).
3. Wired map/task distance display boundaries to `UnitsPreferences` + `UnitsFormatter` (distance circles, task stats, minimized indicator, racing selector distance labels).
4. Added regression tests:
   - `TaskManagerCoordinatorTest` start-line width conversion.
   - `ReplayRuntimeInterpolatorTest` movement distance contract.
   - `ReplayHeadingResolverTest` low-distance heading reuse gate.
5. Fixed AAT start/finish cylinder radius halving defect by removing invalid `/ 2.0` conversions in renderer, geometry, and validation bridge paths.

Partial:
1. Task coordinator now exposes meter-first APIs with km compatibility wrappers (`TaskSheetCoordinatorUseCase` / `TaskSheetViewModel`).

Remaining:
1. Full AAT and legacy task internal SI normalization (phases 2-5).

### Run 2 (2026-02-22)
Completed:
1. Normalized manager/coordinator distance contracts to meter-first APIs with explicit unit naming:
   - `AATTaskManager`: added `*Meters`/`*Km` distance APIs and retained deprecated compatibility wrappers.
   - `RacingTaskManager`: added `*Meters`/`*Km` distance APIs and retained deprecated compatibility wrappers.
   - `TaskManagerCoordinator`: migrated internal distance aggregation/segment/proximity APIs to meter-first (`calculate*Meters`) with explicit km wrappers.
   - `TaskSheetCoordinatorUseCase`: migrated to meter-first segment usage and removed ad-hoc `* 1000` call-site conversions.
2. Added regression coverage for the new meter-first coordinator contract and km-wrapper parity in `TaskManagerCoordinatorTest`.

Partial:
1. Racing internals outside manager/coordinator boundaries (turnpoint/geometry helper layers) still carry km-native helper contracts and require phase-3 normalization.

### Run 3 (2026-02-22)
Completed:
1. Normalized racing helper/turnpoint/navigation/boundary internals to meter-first calculations:
   - Added `RacingGeometryUtils.haversineDistanceMeters` and migrated racing domain math away from `haversineDistance * 1000` patterns.
   - Updated `RacingZoneDetector`, `RacingNavigationEngineSupport`, `RacingBoundaryCrossingPlanner`, `RacingBoundaryCrossingMath`, `RacingTaskValidator`, and `RacingTask` structure checks to meter-first logic.
   - Updated turnpoint calculators/displays to explicit meter contracts (`TurnPointCalculator.calculateDistanceMeters` / `getEffectiveRadiusMeters`).
2. Centralized racing waypoint unit boundaries with explicit meter helpers on `RacingWaypoint` (`gateWidthMeters`, `keyholeInnerRadiusMeters`, `faiQuadrantOuterRadiusMeters`) and replaced ad-hoc conversions in racing internals.
3. Extended SI regression coverage for racing geometry/boundary paths:
   - Added `RacingGeometryUtilsTest` (meter-vs-km wrapper parity and start-line width meter contract).
   - Updated `TaskManagerCoordinatorTest` and `RacingBoundaryCrossingPlannerTest` to meter-native assertions.
4. Extended meter-first contract use into supporting racing components:
   - `DefaultRacingTaskEngine` and `RacingReplayLogBuilder` now consume meter-returning geometry helpers directly.

Partial:
1. Compatibility km wrappers still remain intentionally for migration safety (`RacingGeometryUtils.haversineDistance`, manager/coordinator `*Km` APIs).
2. Racing waypoint persistent configuration still stores radii as km fields; conversions are now centralized and explicit at calculation boundaries.

Remaining:
1. Add boundary adapter tests for ADS-B/OGN/replay SI conversion contracts (Phase 4 backlog).
2. Decide and execute polar internal storage contract migration (km/h vs SI).
3. Remove transitional km wrappers after caller migration and enforce static guards for new non-SI drift.

### Run 4 (2026-02-22)
Findings added to plan:
1. Shared task waypoint radius contract is still km-native (`TaskWaypoint.customRadius`), which keeps reintroducing km->m leakage in task/AAT/racing paths.
2. AAT core geodesic math API remains km-first (`AATMathUtils`, `AATGeometryGenerator`), preventing full SI-internal compliance.
3. Additional AAT interaction/editing paths remain km-native (`AatGestureHandler`, `AATEditModeState`, `AATAreaTapDetector`, `AATMovablePointStrategySupport`, `AATEditGeometry`).
4. Observation-zone resolver fallback logic still depends on km task fields (`TaskObservationZoneResolver`).
5. Remaining ad-hoc km->m conversions exist in racing replay/coordinator support (`RacingReplayAnchorBuilder`, `TaskManagerCoordinator`).
6. OGN distance policy remains km-first internally (`OgnSubscriptionPolicy.haversineKm` and call sites).

Immediate next implementation focus:
1. Phase 2A (new): migrate shared task radius contract to meter-first.
2. Phase 2B: migrate AAT geodesic math APIs and editing call sites to meters.
3. Phase 4A: migrate OGN policy helpers to meter-first internals with explicit protocol-boundary conversions only.

### Run 6 (2026-02-22)
Completed:
1. Executed Phase 2B remaining scope for AAT core/edit internals:
   - Meter-first geometry wiring across `AATGeometryGenerator` plus renderer/display callers.
   - Meter-first edit/hit-test/drag/strategy internals (`AATEditModeState`, `AATAreaTapDetector`, `AATMovablePoint*`, `AATEditGeometry`, `AATEditOverlayRenderer`, `AATEditModeManager`).
2. Executed replay/coordinator cleanup:
   - Removed ad-hoc km->m conversions from `RacingReplayAnchorBuilder` and `TaskManagerCoordinator` via `gateWidthMeters` and meter-native helpers.
3. Executed OGN policy/API migration:
   - Added `OgnSubscriptionPolicy.haversineMeters` + meter reconnect helper and moved repository/trail internals to meter-first distance checks.
4. Verification completed:
   - `--no-configuration-cache enforceRules`
   - `--no-configuration-cache testDebugUnitTest`
   - `--no-configuration-cache assembleDebug`

Notes:
1. Existing Gradle configuration-cache issue in build scripts still requires `--no-configuration-cache` for local command reliability.
2. Instrumentation status moved to Run 7 completion.

### Run 7 (2026-02-22)
Completed:
1. P2 wrapper cleanup in OGN internals:
   - removed unused km compatibility APIs (`haversineKm`, `shouldReconnectByCenterMove`, `isWithinReceiveRadiusKm`).
2. P2 SI hardening in AAT validators/area utilities:
   - fixed meter-labeled assignments still sourcing km helper outputs in `AATTaskSafetyValidator`,
     `AATFlightPathValidator`, `CircleAreaCalculator`, `SectorAreaCalculator`, and `SectorAreaGeometrySupport`.
3. Static guard expansion in `scripts/ci/enforce_rules.ps1`:
   - block `AATMathUtils.calculateDistance(...) * METERS_PER_KILOMETER` regressions,
   - block meter-labeled variables sourced from km-returning AAT helpers,
   - block replay `distanceMeters = speedMs` regression,
   - block OGN km-helper reintroduction,
   - block hard-coded non-SI distance labels in shared distance display surfaces.
4. Full verification matrix executed and passing:
   - `./gradlew --no-daemon --no-configuration-cache enforceRules`
   - `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest`
   - `./gradlew --no-daemon --no-configuration-cache assembleDebug`
   - `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
   - `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel`

Remaining/Deferred:
1. Polar internal storage contract decision (item #17) remains open by design.
2. Broad removal of legacy compatibility wrappers across task/AAT/racing remains deferred to a dedicated compatibility-cut pass.

Recommended next execution order:
1. Compatibility-wrapper cut pass first:
   - Remove internal `*Km`/ambiguous deprecated wrappers across task/AAT/racing manager/coordinator/use-case/viewmodel paths.
   - Keep only explicit protocol/persistence/display boundary conversions.
2. Polar SI contract migration second:
   - Move polar/config internal contracts to SI (`m/s`) and add versioned compatibility read for legacy `km/h` persisted fields.
   - Retain `km/h` only at UI/input boundaries.
3. Execute by autonomous contract:
   - Follow `docs/UNITS/AGENT_EXECUTION_CONTRACT_SI_REMAINING_P0_P1_2026-02-22.md` Section 9 run order, gates, commands, and reporting requirements.

Rationale:
1. Wrapper removal first reduces mixed-unit call paths and narrows the migration surface.
2. Polar storage migration is higher-risk (schema + persistence + UI contract), so it should run after wrapper cleanup to reduce blast radius.

### Run 8 (2026-02-22)
Findings added to plan:
1. AAT radius authority remains km-canonical and still drives repeated internal km->m conversions (`AATRadiusAuthority`, `AATTaskManager`, renderer/validation/persistence adapters).
2. Core/racing radius contracts remain dual-unit (`TaskWaypoint.customRadius` km + `customRadiusMeters`) and racing custom params are still km-based without explicit unit naming.
3. AAT/racing manager baseline distance APIs still originate in km-first contracts (`AATTaskManager.calculateAATDistanceKm`, `RacingTaskManager.calculateRacingDistanceKm`).
4. AAT interactive distance contracts remain km-native (`AATGeoMath`, `AATInteractiveDistanceCalculator`, `AATInteractiveModels`).
5. AAT scoring speed contracts remain km/h-native internally (`AATSpeedCalculator`, `AATResult`, `AATSpeedAnalysis`).
6. Additional coordinator/use-case/viewmodel `*Km` wrappers remain published and appear removable after compatibility cut.

Execution implication:
1. Compatibility-wrapper cut (`#18`) is still first, but now explicitly includes:
   - AAT radius authority contract migration to meters.
   - Core/racing radius dual-field normalization.
   - Removal of unused `*Km` wrappers in coordinator/use-case/viewmodel.
2. Polar SI migration (`#17`) remains second.
3. Post-polar SI closeout must include new re-pass items (`#29-#34`) before final compliance sign-off.

### Run 9 (2026-02-22)
Findings added to plan:
1. Task delegate layer still exposes ambiguous unsuffixed deprecated distance wrappers (`TaskTypeCoordinatorDelegate.calculateDistance` / `calculateSegmentDistance`), which are internal non-SI surfaces and should be removed.
2. AAT and racing calculator layers still publish km wrapper + deprecated unsuffixed APIs (`AATTaskCalculator.calculateDistanceToTargetPointKm`, `RacingTaskCalculator.calculateDistanceToOptimalEntryKm`), increasing mixed-unit regression surface.
3. `AATWaypoint` core model remains km-native for target-offset semantics (`targetPointOffset`, `isTargetPointValid`), and should be normalized to meter-first internal contracts.
4. AAT competition validation/scoring/compliance stack remains km/km2/km/h-native internally (`FAIComplianceRules`, `FAIComplianceAreaRules`, `AATTaskStrategicValidator`, `AATValidationScoreCalculator`, `AATCompetitionComplianceEvaluator`).
5. Racing result model speed contract remains km/h-native and appears stale/unused (`RacingTaskResultModels.averageSpeed`).

Execution implication:
1. Run C residual SI normalization scope is expanded to include new backlog `#35/#36/#37/#38` in addition to `#29-#33`.
2. Run D compliance closeout now includes stale/dead model decision `#39` alongside `#13/#28/#34`.
3. No new P0 runtime crossing defects found in Run 9, but final SI sign-off remains blocked until these additional contract debts are resolved.

### Run 10 (2026-02-22)
Completed:
1. Executed residual wrapper cleanup in AAT internals:
   - Removed unused deprecated compatibility wrappers from `AATTaskCalculator`.
   - Removed unused compatibility wrappers from `AATSpeedCalculator`.
   - Removed unused compatibility wrappers from `AATGeoMath`.
   - Removed deprecated `getDistanceLimits()` wrapper from `AATInteractiveTurnpointManager`.
2. Executed AAT radius authority SI hardening:
   - Replaced km-canonical authority APIs with meter-canonical contracts in `AATRadiusAuthority`.
   - Removed km authority extension usage (`getAuthorityRadius`) and normalized on `getAuthorityRadiusMeters`.
   - Updated `TaskPersistenceAdapters` to read authority radii in meters directly.
3. Revalidated Run-9 residual closures:
   - Delegate/calculator wrapper cleanup (`#35/#36`) confirmed.
   - `AATWaypoint` target-offset SI contract (`#37`) confirmed.
   - AAT validation/scoring SI normalization (`#38`) confirmed.
   - Racing result speed SI contract (`#39`) confirmed (`averageSpeedMs`).

Verification:
1. PASS:
   - `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.*" --tests "com.trust3.xcpro.tasks.aat.*" --tests "com.trust3.xcpro.tasks.racing.*"`
   - `./gradlew --no-daemon --no-configuration-cache enforceRules`
   - `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest`
   - `./gradlew --no-daemon --no-configuration-cache assembleDebug`
2. PASS:
   - `./gradlew --no-daemon --no-configuration-cache :app:uninstallDebug :app:uninstallDebugAndroidTest` (environment remediation).
   - `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (9 tests).
3. NOT COMPLETED:
   - `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel` (user-aborted to reduce execution time).

Remaining:
1. `#30` compatibility cut for core/racing radius dual contracts (`customRadius`/`resolvedCustomRadiusKm`, km-native racing params).
2. `#13` boundary-adapter conversion test expansion.
3. `#34` and `#28` cleanup of legacy/dead km helper paths.

### Run 11 (2026-02-23)
Completed:
1. Executed `#17` polar storage SI migration:
   - Core glider model/config contracts are now SI-canonical for storage/runtime (`*Ms`).
   - `GliderRepository` now supports compatibility read for legacy persisted km/h keys.
   - Canonical writes persist SI keys only (`iasMinMs`/`iasMaxMs`, `ThreePointPolar.*Ms`).
2. Migrated polar internals to SI-first call paths:
   - `PolarCalculator` now uses SI speed contracts for interpolation and three-point fit inputs.
   - `GliderSpeedBoundsResolver` now resolves ranges/bounds in SI and converts speed limits at boundary only.
3. Added focused regression coverage:
   - Legacy migration read + SI round-trip persistence stability (`GliderRepositorySiMigrationTest`).
   - Polar interpolation SI/parity tests (`PolarCalculatorSiContractTest`).
   - SI bounds resolution contract test (`GliderSpeedBoundsResolverSiContractTest`).

Verification:
1. PASS:
   - `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.glider.*"`
   - `./gradlew --no-daemon --no-configuration-cache enforceRules testDebugUnitTest assembleDebug`
2. NOT RUN:
   - instrumentation commands (`:app:connectedDebugAndroidTest`, `connectedDebugAndroidTest`) for this storage-domain pass.

Remaining:
1. `#30` compatibility cut for core/racing radius dual contracts:
   - remove internal `resolvedCustomRadiusKm` usage across racing mapper/initializer/engine/persistence/UI paths.
   - migrate `RacingWaypointCustomParams` + resolver callsites to meter-canonical fields/keys.
   - migrate `RacingWaypoint` / `RacingTask.racingWaypoints` to meter-canonical internal storage bridges.
   - confine km radius compatibility to explicit serializer/protocol boundaries only.
2. `#13` boundary-adapter conversion test expansion.
3. `#34` and `#28` cleanup of legacy/dead km helper paths.

### Run 12 (2026-02-23)
Findings added to plan:
1. `#30` scope remained broader than previously listed; additional active hotspots confirmed in:
   - `TaskObservationZoneResolver` (km->m inline conversion from racing params),
   - `RacingTask.racingWaypoints` bridge (meter->km down-conversion),
   - `RacingTaskManager.getCoreTask` and persistence mappers (dual-write km+meters).
2. `RacingWaypointCustomParams` remains unit-ambiguous with km defaults and unsuffixed key names, so contract migration must include key compatibility strategy (read legacy keys, write canonical meter keys).
3. Existing tests are compatibility-first; `#30` requires test intent pivot to SI-first internals with compatibility only at explicit boundaries.

Execution implication:
1. Run C in the execution contract must be treated as a file-targeted migration run for `#30`, not a generic residual cleanup pass.
2. Final `#30` completion criteria require no internal `resolvedCustomRadiusKm` dependencies in racing core paths.

### Run 13 (2026-02-23)
Findings added to plan:
1. `#30` also includes unsuffixed radius APIs with km semantics in non-boundary layers:
   - `TaskManagerCoordinator.updateWaypointPointType(...)`
   - `TaskSheetCoordinatorUseCase.updateWaypointPointType(...)`
   - `TaskSheetViewModel.onUpdateWaypointPointType(...)`
2. `RacingWaypointManager` remains km-canonical for defaulting and mutation paths (`gateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`) and must be migrated to meter-canonical internals.
3. `TaskWaypoint` still exposes dead km compatibility helpers (`withCustomRadiusKm`, `getEffectiveRadius`) that should be removed when internal callers are fully meter-first.
4. Racing navigation support still uses km-native raw radius fields in signature/check paths (`RacingNavigationEngineSupport`, `RacingZoneDetector`).

Execution implication:
1. Run C must now include coordinator/use-case/viewmodel API migration to explicit meter-named radius contracts and UI-boundary conversion strategy.
2. Run C acceptance requires meter-canonical `RacingWaypointManager` behavior and removal of unused core km helpers.
3. `#30` should not be marked done until these additional internal contracts are migrated or explicitly boundary-scoped.

### Run 14 (2026-02-23)
Findings added to plan:
1. `RacingTaskManager` bridge/update radius APIs remain unsuffixed (`gateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`) and still encode km semantics.
2. `TaskSheetViewModel.applyRacingObservationZone` still applies internal racing radius via km conversion (`radiusMeters / 1000.0`), which conflicts with target meter-canonical task/racing flow.
3. Dead km helper `RacingWaypoint.effectiveRadius` remains and should be removed during `#30` migration.
4. Navigation support tests still use km-centric fixture naming/contracts and must be migrated with manager/model API changes:
   - `RacingNavigationEngineSupportTest`
   - `RacingNavigationEngineTest`

Execution implication:
1. Run C scope must explicitly include `RacingTaskManager` API contract migration to meter-named parameters.
2. Run C must remove racing import-path km conversions in task sheet import flow.
3. Run C exit criteria should include updated navigation support tests to prevent reintroduction of km semantics.

### Run 15 (2026-02-23)
Completed:
1. Closed Run 14 implementation items for `#30`:
   - `RacingTaskManager` bridge/update APIs migrated to meter-named contracts.
   - `TaskSheetViewModel.applyRacingObservationZone` no longer converts meters back to km.
   - Dead `RacingWaypoint.effectiveRadius` helper removed.
   - Navigation support fixture/tests migrated to meter-oriented setup.
   - `RacingWaypointCustomParams` migrated to meter-named keys/fields with legacy read compatibility.

Residual confirmed:
1. Racing model canonical storage remains km-backed (`RacingWaypoint`, `RacingTask.racingWaypoints` bridge).
2. `RacingWaypointManager` defaults/mutations remain km-canonical internally.
3. Racing projection paths still dual-write `customRadius` + `customRadiusMeters`.
4. `RacingZoneDetector` still has a raw km field guard.
5. `TaskWaypointRadiusContractTest` remains compatibility-first.

Execution implication:
1. Keep `#30` open and narrow Run C follow-up to model/storage canonization and dual-write boundary tightening.

### Run 16 (2026-02-23)
Findings added to plan:
1. Racing model factory contract still exposes km compatibility inputs in active internal manager paths (`customGateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`), so model-canonical migration is incomplete:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointManager.kt`
2. Raw km field access remains in racing display diagnostics and should be removed as part of final radius-contract cleanup:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
3. Run 15 residuals remain otherwise unchanged (model canonical storage, manager defaults, dual-write racing projection paths, raw km guard in `RacingZoneDetector`, compatibility-first test intent).

Execution implication:
1. Run C closure criteria should explicitly include:
   - meter-only internal invocation contract for `RacingWaypoint.createWithStandardizedDefaults`,
   - cleanup of remaining raw km field reads in active racing logic/diagnostic paths.
2. `#30` should remain open until these misses are closed and verified.

### Run 17 (2026-02-23)
Findings added to plan:
1. Core radius helper still propagates legacy km mirror in internal flows:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt` (`withCustomRadiusMeters` synchronizes `customRadius` from meters).
2. Internal AAT/racing engine normalization paths still call this helper, so dual-field propagation is not boundary-only:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/AATTaskWaypointCodec.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultAATTaskEngine.kt`
3. Internal-domain test intent still asserts km mirror population:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/domain/engine/DefaultAATTaskEngineTest.kt`

Execution implication:
1. Run C closure must include a core-level dual-write strategy update so internal engine paths no longer require km mirror propagation.
2. Compatibility km-field assertions should be retained only in explicit boundary tests; internal engine tests should pivot to SI-first expectations.

### Run 18 (2026-02-23)
Findings added to plan:
1. Racing UI still reads legacy km radius field `customRadius` in active internal state wiring:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt`
2. This conflicts with `#30` target of boundary-only km compatibility ownership.

Execution implication:
1. Run C closure should include removal of internal racing UI dependence on `TaskWaypoint.customRadius` (use meter-only state keys/inputs internally).
2. `#30` remains open until this UI-path dependency is removed or explicitly boundary-scoped.

### Run 19 (2026-02-23)
Completed:
1. Implemented `#30` follow-up cleanup in active projection and UI-state paths:
   - Removed internal `customRadius` dual-write from:
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskManager.kt`
   - Removed legacy `customRadius` dependency from AAT list compose state keys:
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATManageListItems.kt`
   - Removed residual `resolvedCustomRadiusKm()` dependency in AAT gesture edit callback:
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/gestures/AatGestureHandler.kt`
   - Removed raw km diagnostic/display usage in finish-line rendering:
     - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
2. Verification pass completed:
   - PASS: focused `:feature:map:testDebugUnitTest` suite for changed task/AAT/racing paths.
   - PASS: `enforceRules`.
   - PASS: `testDebugUnitTest`.
   - PASS: `assembleDebug`.

Residual (still open for `#30`):
1. Racing model canonical radius storage remains km-backed (`RacingWaypoint`, `RacingTask.racingWaypoints`).
2. `RacingWaypointManager` defaults/mutations still operate through km-backed model fields.
3. `RacingWaypoint.createWithStandardizedDefaults` still carries km compatibility constructor inputs used in internal manager/model paths.
4. Radius contract tests still need final SI-first intent hardening in compatibility-heavy persistence surfaces.

### Run 22 (2026-02-23)
Findings added to plan (`#13` boundary adapter tranche re-check):
1. ADS-B boundary adapter tests are still incomplete:
   - missing repository-level assertion that `maxDistanceKm` propagates into provider `BBox` boundaries (`AdsbTrafficRepositoryTest`).
   - missing HTTP-boundary assertion for OpenSky query/header serialization (`OpenSkyProviderClientTest`).
2. OGN boundary adapter tests are still incomplete:
   - missing repository-level login/filter payload test (`r/{lat}/{lon}/{radiusKm}` formatting and km-radius contract).
   - missing receive-radius edge assertions around exact `radiusMeters` inclusion/exclusion.
3. Replay boundary adapter tests are still incomplete:
   - missing explicit IAS/TAS `km/h -> m/s` conversion contract tests in `ReplaySampleEmitterTest` for both-present, IAS-only, TAS-only branches.
   - missing explicit `targetSpeedKmh -> speedMs` boundary contract coverage in racing synthetic replay generation (`RacingReplayLogBuilder`).

Execution implication:
1. Keep `#13` open.
2. Run D must implement the above adapter-level tests before SI compliance closeout can be marked complete.
3. Backlog/test matrix/docs must be updated once those tests land (`EXECUTION_BACKLOG`, `VERIFICATION_MATRIX`, execution contract evidence table).

### Run 23 (2026-02-23)
Findings added to plan (`#13` boundary adapter tranche re-check, expanded):
1. ADS-B boundary adapter tests are still incomplete (expanded):
   - missing repository-level clamp-boundary assertion that `maxDistanceKm` min/max limits flow into provider `BBox` radius inputs.
   - missing HTTP-boundary assertions for OpenSky `extended=1` query contract and locale-stable 6-decimal coordinate formatting.
   - missing HTTP-boundary assertions that auth header emission is conditional (present for non-blank bearer, absent for blank bearer).
2. OGN boundary adapter tests are still incomplete (expanded):
   - missing socket-harness repository test for APRS login payload precision (`r/{lat5}/{lon5}/{radiusKmInt}`).
   - missing reconnect-path assertion that center movement over threshold reconnects with refreshed filter-center coordinates.
   - missing exact receive-radius equality/epsilon edge assertions.
3. Replay boundary adapter tests are still incomplete (expanded):
   - missing emitter ingress-boundary reset assertions for null and non-finite IAS/TAS values.
   - no dedicated `RacingReplayLogBuilder` unit test class currently locks speed conversion + step-quantized timing contract.

Execution implication:
1. Keep `#13` open.
2. Run D scope must include the above expanded adapter-boundary assertions before SI closeout.
3. Backlog and execution contract must stay synchronized with these expanded `#13` tasks.

### Run 24 (2026-02-23)
Completed (`#13` implementation tranche):
1. ADS-B boundary adapter tests implemented:
   - repository bbox propagation + clamp-edge assertions in `AdsbTrafficRepositoryTest`.
   - OpenSky HTTP boundary serialization/auth header assertions in `OpenSkyProviderClientTest`.
2. OGN boundary adapter tests implemented:
   - socket-harness login filter precision + reconnect-center refresh coverage in `OgnTrafficRepositoryConnectionTest`.
   - exact receive-radius boundary include/exclude tests in `OgnTrafficRepositoryPolicyTest`.
3. Replay boundary adapter tests implemented:
   - IAS/TAS conversion and null/non-finite ingress reset coverage in `ReplaySampleEmitterTest`.
   - `targetSpeedKmh -> speedMs` timing/quantization coverage in `RacingReplayLogBuilderTest`.
4. Verification completed:
   - PASS: targeted `:feature:map:testDebugUnitTest` suites for ADS-B/OGN/replay/racing replay builder.
   - PASS: `enforceRules testDebugUnitTest assembleDebug`.

Execution implication:
1. Mark `#13` done.
2. Remaining Run D scope is reduced to cleanup items `#28/#34` plus final compliance closeout documentation.

### Run 25 (2026-02-23)
Findings added to plan (`#34/#28` focused re-check):
1. `#28` remains open with no change in production code:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt` still contains unused km helper `haversineDistance(...)` (no call sites found in repo).
2. `#34` target 1 remains open:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` still has `calculateDistanceInArea(...)` with unsuffixed return contract and km accumulation (`AATMathUtils.calculateDistance(...)`).
   - method appears dead (no callers found).
3. `#34` target 2 remains open:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt` remains a legacy commented source block with km conversions and local km haversine helper.
4. Adjacent residuals found during `#34/#28` audit and added to closeout scope:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATLongPressOverlay.kt` local km haversine + meter-to-km conversion in hit-test path (file appears unused).
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt` unused km wrapper `haversineDistance(...)`.

Execution implication:
1. Keep `#28` and `#34` open.
2. Expand Run D closure checklist to include adjacent dead/unused km-helper surfaces in AAT interaction/UI so SI compliance closeout is complete.
3. Prefer delete-over-migrate where helper/file has no active call sites; if any helper is retained, contract must be explicit `*Meters`.

### Run 26 (2026-02-23)
Findings added to plan (`#34/#28` focused re-check, active call-path validation):
1. `#28` remains open:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt` still contains unused km helper `haversineDistance(...)`.
2. `#34` remains open:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` still has unsuffixed km-returning `calculateDistanceInArea(...)` (appears unused).
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt` remains commented legacy km-based source.
3. New adjacent miss confirmed (active path):
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt` `calculateAreaSizeKm2(...)` is actively consumed by `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngine.kt`.
   - This keeps internal quick-validation policy thresholds on km2 contracts (`< 10.0`, `> 5000.0`) instead of SI m2.
4. Area-size warning output in the same path currently labels area values as `km` rather than squared units.

Execution implication:
1. Keep `#28/#34` open.
2. Add adjacent backlog `#40` for active AAT area-size SI normalization/boundary scoping.
3. Run D closeout now targets `#28/#34/#40` before final compliance sign-off.

### Run 27 (2026-02-23)
Findings added to plan (`#34/#28` deep re-check, wrapper call-site sweep):
1. `#28` status unchanged:
   - `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt` unused km helper remains.
2. `#34` status unchanged:
   - dead unsuffixed km-returning `calculateDistanceInArea(...)` remains in `AreaBoundaryCalculator`.
   - commented km-based `KeyholeVerification` remains in production source.
3. New adjacent miss confirmed in `AATEditGeometry`:
   - additional unused km wrappers were not previously listed:
     - `generateCircleCoordinates(...)`
     - `generateSectorCoordinates(...)`
     - `calculateDestinationPoint(...)`
   - existing unused km wrapper `haversineDistance(...)` remains.

Execution implication:
1. Keep `#28/#34/#40` open.
2. Expand `#34` closeout checklist to include the full dead km-wrapper set in `AATEditGeometry`, not only `haversineDistance(...)`.
3. Prefer delete-over-migrate for these wrappers since no call sites were found in main/test sources.

### Run 28 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, pass 1):
1. Revalidated open items with no delta:
   - `#28` unresolved (`AirspaceGestureMath.haversineDistance(...)`).
   - `#34` unresolved (`calculateDistanceInArea(...)` + commented `KeyholeVerification`).
   - adjacent unresolved wrappers in `AATLongPressOverlay` and `AATEditGeometry`.

Execution implication:
1. No scope change from Run 27; keep `#28/#34/#40` open.

### Run 29 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, pass 2):
1. `#40` scope requires full helper-chain coverage, not just the top-level boundary calculator:
   - `CircleAreaCalculator.calculateAreaSizeKm2(...)`
   - `SectorAreaCalculator.calculateAreaSizeKm2(...)`
   - `SectorAreaGeometrySupport.calculateAreaSizeKm2(...)`
   - `AreaBoundaryCalculator.calculateAreaSizeKm2(...)` (consumer-facing path into quick validation)
2. `AATTaskQuickValidationEngine` area-size warnings still use non-squared label text (`km`).

Execution implication:
1. Expand `#40` implementation to migrate/scope the full km2 helper chain and fix output labels to explicit squared units.
2. Keep `#28/#34/#40` open.

### Run 30 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, pass 3):
1. Guardrail gap identified in `scripts/ci/enforce_rules.ps1`:
   - no explicit checks preventing reintroduction of `#28/#34/#40` patterns (`AirspaceGestureMath`, `KeyholeVerification`, `AATLongPressOverlay`, `AATEditGeometry` km wrappers, area-size km2 policy/label drift).
   - existing `haversineDistance` guard is scoped only to `TaskManagerCoordinator`.

Execution implication:
1. Add adjacent backlog `#41` for static-rule expansion to lock `#28/#34/#40` closures.
2. Keep `#28/#34/#40/#41` open until cleanup and guards are implemented.

### Run 31 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, this-cycle pass 1):
1. Revalidated current open items with no additional production-surface delta:
   - `#28` (`AirspaceGestureMath.haversineDistance(...)` still unused).
   - `#34` (`calculateDistanceInArea(...)` + commented `KeyholeVerification` still present).
   - adjacent wrappers in `AATLongPressOverlay` and `AATEditGeometry` remain unresolved.

Execution implication:
1. No scope change for production cleanup from Run 30.

### Run 32 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, this-cycle pass 2):
1. New adjacent miss identified in test coverage for `#40`:
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/aat/AATTaskQuickValidationEngineUnitsTest.kt` currently validates start/finish meter-threshold behavior only.
   - no tests lock area-size SI policy behavior or squared-unit warning output labeling in quick-validation path.

Execution implication:
1. Add adjacent backlog `#42` for explicit `#40` regression-test hardening.
2. Keep `#28/#34/#40/#41/#42` open until implementation + guards + tests are complete.

### Run 33 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, this-cycle pass 3):
1. Guardrail status unchanged: `#41` remains open (no static-rule additions yet in `scripts/ci/enforce_rules.ps1`).
2. Verification-plan parity drift identified:
   - residual-scope notes in verification docs were not fully aligned to expanded closeout scope beyond `#28/#34`.

Execution implication:
1. Update verification/doc notes to keep residual scope explicit (`#28/#34/#40/#41/#42`) until closure.
2. Preserve `#41` as required pre-closeout hardening step.

### Run 34 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, this-cycle pass 1):
1. `#28` and `#34` remain unchanged (`AirspaceGestureMath`, `AreaBoundaryCalculator.calculateDistanceInArea`, commented `KeyholeVerification`).
2. New adjacent miss identified in AAT geometry compatibility surface:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt` still exposes unused km wrappers:
     - `generateCircleCoordinates(...)`,
     - `generateStartLine(...)`,
     - `generateFinishLine(...)`,
     - `calculateDestinationPoint(...)`.
   - no main/test call sites were found for these wrappers; active paths use `*Meters` APIs.

Execution implication:
1. Add adjacent backlog `#43` for `AATGeometryGenerator` dead km-wrapper cleanup.
2. Keep `#28/#34/#40/#41/#42/#43` open until Run D closeout.

### Run 35 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, this-cycle pass 2):
1. `#40` remains active through the same `calculateAreaSizeKm2(...)` helper chain and quick-validation thresholds.
2. `#42` remains open:
   - `AATTaskQuickValidationEngineUnitsTest` still lacks area-size SI policy and squared-unit label assertions.

Execution implication:
1. No new scope expansion beyond `#40/#42` in this pass.
2. Preserve `#42` as required pre-closeout test hardening.

### Run 36 (2026-02-23)
Findings added to plan (`#34/#28` triple-pass sequence, this-cycle pass 3):
1. `#41` guardrail gap remains open in `scripts/ci/enforce_rules.ps1`.
2. Guardrail scope now also needs to cover new adjacent `#43` surface (`AATGeometryGenerator` unused km wrappers).

Execution implication:
1. Expand `#41` implementation to include static checks for `#43` reintroduction after cleanup.
2. Keep residual closeout scope explicit as `#28/#34/#40/#41/#42/#43`.

### Run 37 (2026-02-23)
Findings added to plan (`#34/#28` focused re-check):
1. Revalidated `#28` and `#34` with no production-surface change:
   - `AirspaceGestureMath.haversineDistance(...)` remains unused.
   - `AreaBoundaryCalculator.calculateDistanceInArea(...)` remains dead/unsuffixed.
   - commented `KeyholeVerification` remains in production source.
2. Revalidated adjacent residual scope with no new item:
   - `#40` area-size km2 policy/label drift remains.
   - `#42` quick-validation area-size unit-test gap remains.
   - `#41` guardrail gap remains and still needs `#43` coverage.
3. No new residual item was identified beyond currently tracked `#43`.

Execution implication:
1. Keep Run D closeout scope unchanged: `#28/#34/#40/#41/#42/#43`.
2. Prioritize implementation over further discovery loops unless new production deltas land in these files.

### Run 38 (2026-02-23)
Implementation completed:
1. Closed `#28` by removing dead `AirspaceGestureMath` helper file.
2. Closed `#34` by removing dead/legacy km helper surfaces:
   - removed `AreaBoundaryCalculator.calculateDistanceInArea(...)`,
   - removed commented `KeyholeVerification`,
   - migrated `AATLongPressOverlay` hit-test distance to `AATMathUtils.calculateDistanceMeters(...)`,
   - removed unused km wrappers from `AATEditGeometry`.
3. Closed `#40` by moving area-size quick-validation internals to m2 contracts:
   - added `calculateAreaSizeMeters2(...)` path in area calculators and boundary adapter,
   - `AATTaskQuickValidationEngine` now compares m2 thresholds and formats warnings with squared units (`km2`).
4. Closed `#42` by extending `AATTaskQuickValidationEngineUnitsTest` with area-size small/large warning coverage and squared-unit label assertions.
5. Closed `#41` with static guard expansion and included `#43` protection:
   - dead file reintroduction checks (`AirspaceGestureMath`, `KeyholeVerification`),
   - km-wrapper reintroduction checks for `AATEditGeometry` and `AATGeometryGenerator`,
   - local haversine helper guard for `AATLongPressOverlay`,
   - quick-validation area-size m2-internal and squared-label checks.
6. Closed `#43` by removing unused km wrappers from `AATGeometryGenerator` (`generateCircleCoordinates`, `generateStartLine`, `generateFinishLine`, `calculateDestinationPoint`).

Verification evidence:
1. PASS: `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.aat.AATTaskQuickValidationEngineUnitsTest" --no-daemon --no-configuration-cache`
2. PASS: `./gradlew enforceRules --no-daemon --no-configuration-cache`
3. PASS: `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache`
4. PASS: `./gradlew assembleDebug --no-daemon --no-configuration-cache`

### Run 39 (2026-02-23)
Findings added to plan (`#18` re-check pass 1: internal API/call-chain audit):
1. AAT point-type wrapper chain remains active with km/unsuffixed contracts across internal non-boundary layers:
   - `TaskSheetViewModel.onUpdateAATWaypointPointType(...)`
   - `TaskSheetCoordinatorUseCase.updateAATWaypointPointType(...)`
   - `TaskManagerCoordinator.updateAATWaypointPointType(...)`
   - `AATCoordinatorDelegate.updateWaypointPointType(...)`
   - `AATTaskManager.updateAATWaypointPointType(...)` / `updateWaypointPointTypeBridge(...)`
   - `AATPointTypeConfigurator.updateWaypointPointType(...)`
2. Meter-first coordinator route still down-converts to km on AAT dispatch:
   - `TaskManagerCoordinator.updateWaypointPointType(...)` AAT branch converts `*Meters` -> km before dispatch.
3. AAT imported OZ path still routes meters -> km -> wrapper:
   - `TaskSheetViewModel.applyAatObservationZone(...)`.

Execution implication:
1. Keep `#18` open.
2. Promote AAT point-type contracts to meter-first end-to-end and demote/remove km wrappers.

### Run 40 (2026-02-23)
Findings added to plan (`#18` re-check pass 2: gesture/camera radius contract audit):
1. Internal AAT edit-radius runtime chain remains km-named and km-routed:
   - `TaskGestureCallbacks.onEnterEditMode(..., radiusKm)`
   - `AatGestureHandler.enterEditMode(...)` (meters -> km conversion)
   - `MapGestureSetup` callback wiring
   - `MapCameraManager.zoomToAATAreaForEdit(..., turnpointRadiusKm)`

Execution implication:
1. Include gesture/camera radius migration in `#18` closeout (meters internally; km only at explicit display boundary if needed).

### Run 41 (2026-02-23)
Findings added to plan (`#18` re-check pass 3: dead-wrapper inventory):
1. Dead km compatibility wrappers with no production callers:
   - `TaskWaypoint.resolvedCustomRadiusKm()`
   - `AATMathUtils.calculateDistance(...)`, `calculateDistanceKm(...)`, `calculateCrossTrackDistance(...)`, `calculateAlongTrackDistance(...)`
   - `AreaBoundaryCalculator.calculateAreaSizeKm2(...)`, `CircleAreaCalculator.calculateAreaSizeKm2(...)`, `SectorAreaCalculator.calculateAreaSizeKm2(...)`, `SectorAreaGeometrySupport.calculateAreaSizeKm2(...)`
   - `RacingGeometryUtils.haversineDistance(...)`
   - `RacingWaypoint` deprecated km views (`gateWidth`, `keyholeInnerRadius`, `faiQuadrantOuterRadius`)

Execution implication:
1. Remove dead wrappers or retain only with explicit boundary-owner usage and tests.

### Run 42 (2026-02-23)
Findings added to plan (`#18` re-check pass 4: test-coupling audit):
1. Tests still rely on km wrappers and block clean removal:
   - `TaskWaypointRadiusContractTest` -> `resolvedCustomRadiusKm()`
   - `AATInteractiveTurnpointManagerValidationTest` / `AATEditGeometryValidatorTest` -> `calculateDistanceKm(...)`
   - `AreaCalculatorUnitsTest` / `AATTaskDisplayGeometryBuilderUnitsTest` -> `calculateDistance(...) * 1000`
   - `RacingGeometryUtilsTest` -> `haversineDistance(...)`

Execution implication:
1. Meter-first test migration is required in same tranche as wrapper removal to avoid churn.

### Run 43 (2026-02-23)
Findings added to plan (`#18` re-check pass 5: static guardrail coverage):
1. `scripts/ci/enforce_rules.ps1` has no checks specific to `#18` wrapper-cut closure:
   - no guard against active `updateAATWaypointPointType` wrapper route,
   - no guard against internal `radiusKm` gesture/camera contract reintroduction,
   - no guard against dead km wrapper declarations/usages in core/AAT/racing math/model utilities.

Execution implication:
1. Extend static checks as part of `#18` closeout implementation.
2. Keep `#18` open until wrapper migration + test migration + guardrails all land together.

### Run 44 (2026-02-23)
Implementation completed (`#18` compatibility-wrapper cut):
1. AAT point-type contract path is now meter-first end-to-end:
   - `TaskSheetViewModel` / `TaskSheetCoordinatorUseCase` / `TaskManagerCoordinator` / `AATCoordinatorDelegate` / `AATTaskManager` / `AATPointTypeConfigurator`.
2. AAT gesture/camera edit-radius runtime chain is meter-first:
   - `TaskGestureCallbacks`, `AatGestureHandler`, `MapGestureSetup`, `MapCameraManager`.
3. Dead km wrappers/views removed:
   - `TaskWaypoint.resolvedCustomRadiusKm`,
   - dead km wrappers in `AATMathUtils`,
   - dead km2 wrappers in AAT area calculators,
   - `RacingGeometryUtils.haversineDistance`,
   - deprecated km view properties in `RacingWaypoint`.
4. Tests migrated from km-wrapper coupling to meter-first assertions:
   - `TaskWaypointRadiusContractTest`,
   - `AATInteractiveTurnpointManagerValidationTest`,
   - `AATEditGeometryValidatorTest`,
   - `AreaCalculatorUnitsTest`,
   - `AATTaskDisplayGeometryBuilderUnitsTest`,
   - `RacingGeometryUtilsTest`,
   - plus import/delegate contract tests.
5. Static guardrails expanded in `scripts/ci/enforce_rules.ps1` for `#18` closure.

Verification evidence:
1. PASS: targeted `:feature:map:testDebugUnitTest` suite for all touched `#18` surfaces.
2. PARTIAL: `enforce_rules` full-script run still fails on pre-existing unrelated repo issues; targeted `#18` guard patterns return clean (no matches).

### Run 45 (2026-02-23)
Implementation completed (`#12` fixture-matrix coverage closeout):
1. Added broad known-distance fixture matrix coverage for AAT nominal-distance calculations:
   - `AATDistanceCalculatorUnitsTest.calculateNominalDistance_matchesFixtureMatrixAcrossLatitudes`.
2. Added broad known-distance fixture matrix coverage for racing geodesic calculations:
   - `RacingGeometryUtilsTest.haversineDistanceMeters_matchesKnownFixtureMatrix`.
3. Added coordinator-level cross-task fixture matrix checks:
   - `TaskManagerCoordinatorTest.segment distance meter contract holds across racing and aat fixture matrix`.

Verification evidence:
1. PASS: `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.aat.calculations.AATDistanceCalculatorUnitsTest" --tests "com.trust3.xcpro.tasks.racing.RacingGeometryUtilsTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest"`

### Run 46 (2026-02-23)
Implementation completed (remaining non-`#12` `enforce_rules` caveat closure):
1. Removed false-positive hit on compatibility DI host from task composable boundary rule:
   - exempted `TaskManagerCompat.kt` in composable-surface scans.
2. Hardened static-rule runner reliability for glob edge cases:
   - migrated ripgrep invocation to `Start-Process` with explicit stream capture,
   - converted `No files were searched` from hard abort to explicit no-match skip,
   - retained hard failures for other ripgrep errors.

Verification evidence:
1. PASS: `./scripts/ci/enforce_rules.ps1`.
2. PASS: recursive stability pass x5 (`exit=0` on all five runs).
3. PASS: `./gradlew --no-configuration-cache enforceRules`.
4. NOTE: `./gradlew enforceRules` (configuration cache enabled) still fails due existing configuration-cache policy issues in root build configuration, not SI/rule logic regressions.

## Problem Statement
The codebase is mixed-mode for units. Core flight pipelines are SI-first, but legacy task/AAT/racing paths still compute and compare values in km/km/h or mixed km-vs-meter semantics. This creates correctness risk and makes maintenance harder.

## Goal
Guarantee that all internal calculations use SI units:
- Distance: meters
- Speed: m/s
- Vertical speed: m/s
- Altitude: meters
- Pressure: hPa

Non-SI units remain only for:
- User display formatting.
- Explicit third-party protocol boundaries.

## Non-Goals
- No UX redesign of unit selector in this change.
- No protocol-level behavior changes to external providers beyond boundary adapters.
- No broad refactor of unrelated features.

## Constraints
- Preserve MVVM + UDF + SSOT layering.
- Keep replay deterministic.
- Keep dependency direction and module boundaries.
- Use explicit conversion boundaries; no hidden global conversion behavior.

## Baseline (Before Changes)
- SI wrappers/converters exist and are strong in common layers.
- Flight/fusion is mostly SI-clean.
- Task/AAT/Racing still contains mixed km/km/h semantics and at least one meter-vs-km mismatch risk.

## Re-pass Critical Defects (2026-02-22)
The eighth deep pass confirmed active correctness bugs that must be fixed first:
1. `AATPathOptimizerSupport` compares km path distances against meter-labeled target/tolerance values.
2. `AATPathOptimizer` computes meter-labeled target distances then compares to km path distances.
3. `AATFlightPathValidator` compares km distances to meter thresholds for start/finish checks.
4. `AATTaskQuickValidationEngine` compares km distances to meter thresholds across area/start/finish checks.
5. `AATTaskSafetyValidator` divides an already-km value by 1000.
6. `CircleAreaCalculator` and `SectorAreaCalculator` compare km distances to meter radii.
7. `AATTaskPerformanceSupport` writes km values into a field documented as meters.
8. `AATDistanceCalculator` publishes km values into `AATTaskDistance` fields documented as meters.
9. `AATDistanceCalculator` clamps a meter target (`expectedSpeed * hours * 1000`) using km min/max distances.
10. `AATTaskDisplayGeometryBuilder` forwards meter radii/line lengths into `AATMathUtils.calculatePointAtBearing` (km API).
11. `SectorAreaGeometrySupport` has wide km-vs-meter contamination in boundary math and point generation.
12. `CircleAreaCalculator` unit contamination extends beyond `isInsideArea` into intersections and boundary computations.
13. Racing optimal start-line crossing path passes `gateWidth` in km into a meter-based geometry API (`TaskManagerCoordinator` -> `RacingTaskManager` -> `RacingGeometryUtils.calculateOptimalLineCrossingPoint`), shrinking line width by 1000x in that flow.
14. AAT start/finish cylinder radius is entered/stored as radius but is divided by 2 in renderer/geometry/validation bridge paths (`AATTaskRenderer`, `AATGeometryGenerator`, `AATValidationBridge`), causing 2x scale error.
15. Replay runtime interpolation assigns `MovementSnapshot.distanceMeters` from `speedMs` (`ReplayRuntimeInterpolator`), and `ReplayHeadingResolver` compares that value against `minDistanceM`, creating a meters-vs-m/s contract violation in heading gating.
16. Re-pass #7 scope delta: `AATTaskQuickValidationEngine.validateFinish` (`AATMathUtils.calculateDistance` at line 202) compares km output against meter contracts/tolerances (`lineLength`/`radius` with `+ 100.0`/`+ 50.0`), and was not explicitly captured in earlier finding line coverage.
17. Re-pass #8 found distance output boundaries that still bypass unit preferences:
   - `DistanceCirclesCanvas` renders `km`/`m` labels directly without `UnitsPreferences`.
   - Task stats/minimized-indicator/selector paths render `km` text directly (`TaskStatsSection`, `BottomSheetState`, racing selectors) instead of formatting SI values via `UnitsFormatter`.

## Implementation Phases

### Phase 0: Contract Lock and Guard Rails
1. Freeze target contracts (internal SI only) per module.
2. Add/strengthen naming conventions (`*Meters`, `*Ms`, `*Hpa`, `*C`).
3. Add temporary lint/search checks to detect new non-SI internal calculations.

Definition of done:
- Contract doc approved.
- New checks can fail PRs for obvious regressions.

### Phase 1: Task Domain Contract Normalization
1. Standardize coordinator/domain contracts to meters for distances.
2. Keep legacy km APIs only as transitional wrappers (deprecated).
3. Update callers to consume meter contracts.

Definition of done:
- `TaskSheetCoordinatorUseCase` and core task distance APIs expose SI internally.
- Transitional wrappers are isolated and labeled for removal.

### Phase 2: AAT Legacy Calculator Correction
1. Normalize AAT math utilities and validators to one internal distance unit (meters).
2. Fix mixed comparisons where km values are compared to meter thresholds.
3. Explicitly fix `AATTaskQuickValidationEngine` start/finish threshold checks (`validateStart`, `validateFinish`) to compare meter values against meter contracts.
4. Correct area calculators that pass meter radii into km-based geodesic routines.
5. Add unit tests for each corrected path.

Definition of done:
- No km-vs-meter comparisons remain in AAT internals.
- `AATTaskQuickValidationEngine` start/finish validations are meter-correct and covered by regression tests.
- Validation thresholds behave correctly with known fixtures.

### Phase 3: Racing Legacy Normalization
1. Convert racing internal distance contracts to meters.
2. Keep any km formatting only in UI/presentation layers.
3. Add tests for geometry/distance invariants.
4. Route task/racing distance UI outputs through `UnitsPreferences` + `UnitsFormatter` (no hard-coded `km` text in production surfaces).

Definition of done:
- Racing manager/coordinator internal distance calculations are SI.
- Output formatting follows selected distance units (km/NM/mi) consistently.

### Phase 4: Boundary Adapter Hardening
1. ADS-B/OGN/replay/polar boundaries must explicitly convert once at ingress/egress.
2. Add adapter-level tests to prove internal SI values.
3. Remove scattered ad-hoc conversion code where possible.

Definition of done:
- Boundary conversion points are centralized and documented.
- Internal flows remain SI from boundary inward.

### Phase 5: Cleanup and Deprecation Removal
1. Remove deprecated km-based internal methods introduced for transition.
2. Rename any ambiguous symbols.
3. Final compliance sweep and doc update.

Definition of done:
- No transitional km internal APIs remain.
- Compliance status moves to Compliant.

## Acceptance Criteria
- All internal distance calculations are meters end-to-end.
- All internal speed calculations are m/s end-to-end.
- Display unit conversion only happens through unit formatter/converter boundary paths.
- Replay determinism preserved.
- Existing feature behavior preserved except fixed unit bugs.

## Test Strategy
- Unit tests for conversion boundaries.
- Unit tests for task/AAT/racing distance math correctness.
- Regression tests for OGN/ADS-B selection and display distance.
- Required verification commands from repo policy.

## Rollout Strategy
- Ship behind incremental PRs by phase.
- Land high-risk AAT fixes first with tests.
- Keep temporary compatibility wrappers for one release cycle max.

## Exit Criteria
- Final re-pass reports no internal non-SI calculations except documented protocol boundaries.
- Required test gates pass.
- Docs updated and synced.
