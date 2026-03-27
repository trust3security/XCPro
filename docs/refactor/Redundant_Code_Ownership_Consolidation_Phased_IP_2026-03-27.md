# Redundant Code Ownership Consolidation Phased IP

## 0) Metadata

- Title: Consolidate high-confidence redundant helpers and dead duplicate artifacts without behavior change
- Owner: XCPro Team
- Date: 2026-03-27
- Issue/PR: TBD
- Status: Draft
- Execution rules:
  - This is an ownership-and-maintainability cleanup track, not a feature rewrite.
  - Prefer existing canonical owners before adding new helpers.
  - Land low-risk dead-code deletion and UI dedupe before shared physics math consolidation.
  - Do not mix this plan with map SLO work, task geometry work, logging cleanup, or unrelated line-budget refactors.
  - If a duplicate currently acts as a compatibility wrapper, either keep it as an explicit shim with a removal trigger or remove it in a narrow follow-up phase. Do not silently leave ambiguous twins behind.
- Progress:
  - Phase 0 not started.
  - Phase 1 not started.
  - Phase 2 not started.
  - Phase 3 not started.

## 1) Scope

- Problem statement:
  - The repo currently contains several high-confidence duplicate helpers and at least one dead duplicate UI file.
  - The most concrete current examples are:
    - profile ID canonicalization copied into multiple repositories despite an existing owner in `core/common`
    - bearing normalization and shortest-delta math copied inside map runtime files despite an existing owner in `feature:flight-runtime`
    - standard-atmosphere helpers (`computeDensityRatio`, `pressureToAltitudeMeters`) copied across replay, flight-runtime, and variometer code
    - duplicate nav drawer component implementations in `feature:map`
    - duplicate vario dial UI helper logic in map and profile UI files
  - This violates the repo rule that shared formulas, thresholds, and reusable policies should have one canonical owner.
- Why now:
  - The redundant code is already easy to point to and can be removed in narrow, low-risk slices.
  - Existing dependencies already allow reuse of the likely canonical owners; this is not blocked by module wiring.
  - The cleanup reduces drift risk without changing user-visible behavior.
- In scope:
  - Delete dead duplicate files when no callsites remain.
  - Replace duplicate helper logic with existing canonical owners where they already exist.
  - Introduce one small canonical standard-atmosphere helper owner for shared physics math that currently has no single owner.
  - Add or update targeted regression tests around each consolidated owner.
  - Record any intentionally retained compatibility shim with an explicit removal trigger.
- Out of scope:
  - Broad repo-wide duplicate-code hunting beyond the scoped findings below.
  - UI redesign, copy changes, or interaction behavior changes.
  - New modules or dependency-graph changes.
  - Replay cadence, live sensor policy, or variometer behavior changes.
  - Map visual SLO work.
- User-visible impact:
  - None intended.
  - Any user-visible difference is a regression unless explicitly documented.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

This plan does not introduce new authoritative runtime state. It consolidates shared helper ownership only.

| Data / responsibility | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Default-profile alias normalization | `core/common/src/main/java/com/example/xcpro/core/common/profiles/ProfileSettingsProfileIds.kt` | pure helper API | repository-private `resolveProfileId(...)` copies |
| Bearing normalization and shortest-angle delta math | `feature/flight-runtime/src/main/java/com/example/xcpro/orientation/OrientationMath.kt` | pure helper API | private copies in map-runtime camera/icon files |
| Standard atmosphere density and pressure-altitude math | new shared helper in `feature:flight-runtime` | pure helper API | private copies in replay, flight-state, wind-input, and HAWK paths |
| Map nav drawer component implementations | `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerComponents.kt` | Compose functions | unused duplicate implementation files |
| Vario dial label/config helper logic | new focused helper in `feature:variometer` | pure UI helper API | duplicate copies in map/profile UI files |

### 2.1A State Contract

No new or changed authoritative state is introduced by this plan.

Existing state owners remain unchanged:
- profile repositories still own their own `activeProfileId` state
- replay/live owners still own timestamps and sensor sample state
- map/profile UI still own rendering-only state

This plan only centralizes helper logic used by those existing owners.

### 2.2 Dependency Direction

Confirmed direction remains:

`UI -> domain -> data`

- Modules/files touched:
  - `core:common`
  - `feature:profile`
  - `feature:flight-runtime`
  - `feature:map-runtime`
  - `feature:map`
  - `feature:variometer`
- Boundary risk:
  - Do not move business/domain math into UI files while deduplicating.
  - Do not create a new `core` or shared module just to hold a handful of helpers.
  - Reuse existing module edges only; no new upward dependency leaks.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `core/common/src/main/java/com/example/xcpro/core/common/profiles/ProfileSettingsProfileIds.kt` | Existing canonical owner for profile-ID alias policy | callers keep state ownership, shared helper owns normalization only | none |
| `feature/flight-runtime/src/main/java/com/example/xcpro/orientation/OrientationMath.kt` | Existing shared pure math owner reused by downstream runtime code | one pure helper file reused across modules | add a separate atmosphere helper file rather than expanding camera/UI files |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Default-profile alias normalization | private helper copies in profile/map/unit repos | `ProfileSettingsProfileIds` | canonical owner already exists in `core/common` | existing profile-ID tests plus affected repository tests |
| Bearing normalization / shortest delta | private helpers in map-runtime | `OrientationMath` | canonical owner already exists in a dependency the module already uses | `OrientationMathTest` plus map camera/icon tests |
| Standard atmosphere helpers | private helpers in replay, flight-runtime, and variometer | new shared helper in `feature:flight-runtime` | one owner for shared flight/replay/variometer physics math | new helper tests plus replay/wind/HAWK regressions |
| Nav drawer component implementations | duplicate files in `feature:map` | `DrawerComponents.kt` only | one active UI owner; the other file is dead duplication | compile + no-reference confirmation |
| Vario dial helper logic | duplicate map/profile UI helpers | new focused variometer-owned helper | `feature:variometer` already owns `VarioDialConfig` and related UI dial types | targeted helper test + compile |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapOrientationPreferences.resolveProfileId(...)` | private alias normalization copy | `ProfileSettingsProfileIds.canonicalOrDefault(...)` | Phase 1 |
| `MapStyleRepository.resolveProfileId(...)` | private alias normalization copy | `ProfileSettingsProfileIds.canonicalOrDefault(...)` | Phase 1 |
| `QnhPreferencesRepository.resolveProfileId(...)` | private alias normalization copy | `ProfileSettingsProfileIds.canonicalOrDefault(...)` | Phase 1 |
| `MapTrailPreferences.resolveProfileId(...)` | private alias normalization copy | `ProfileSettingsProfileIds.canonicalOrDefault(...)` | Phase 1 |
| `UnitsRepository.resolveProfileId(...)` | private alias normalization copy | `ProfileSettingsProfileIds.canonicalOrDefault(...)` | Phase 1 |
| `MapCameraManager` private bearing helpers | private copy | `OrientationMath` helpers | Phase 2 |
| `MapCameraPolicy` private bearing helper | private copy | `OrientationMath.shortestDeltaDegrees(...)` | Phase 2 |
| `IconHeadingSmoother` private bearing helper | private copy | `OrientationMath.shortestDeltaDegrees(...)` and `normalizeBearing(...)` | Phase 2 |
| `ReplaySampleEmitter.computeDensityRatio(...)` | private copy | shared atmosphere helper | Phase 3 |
| `WindEstimator.computeDensityRatio(...)` | private copy | shared atmosphere helper | Phase 3 |
| `FlightStateRepository.pressureToAltitudeMeters(...)` | private copy | shared atmosphere helper | Phase 3 |
| `WindSensorInputAdapter.pressureToAltitudeMeters(...)` | private copy | shared atmosphere helper | Phase 3 |
| `HawkVarioEngine.pressureToAltitudeMeters(...)` | private copy | shared atmosphere helper | Phase 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Redundant_Code_Ownership_Consolidation_Phased_IP_2026-03-27.md` | New | change plan and rollout contract | plan belongs in `docs/refactor` | not an architecture-global invariant | No |
| `core/common/src/main/java/com/example/xcpro/core/common/profiles/ProfileSettingsProfileIds.kt` | Existing | canonical profile-ID alias helper | already the right owner | do not create a second helper | No |
| `feature/profile/src/main/java/com/example/xcpro/MapOrientationPreferences.kt` | Existing | profile-owned settings storage | existing repo still owns this state | helper extraction only, no ownership move | No |
| `feature/profile/src/main/java/com/example/xcpro/map/MapStyleRepository.kt` | Existing | profile-owned map-style persistence | existing repo still owns persisted style state | helper extraction only | No |
| `feature/profile/src/main/java/com/example/xcpro/map/QnhPreferencesRepository.kt` | Existing | profile-owned QNH persistence | existing repo still owns persisted QNH state | helper extraction only | No |
| `feature/profile/src/main/java/com/example/xcpro/map/trail/MapTrailPreferences.kt` | Existing | profile-owned trail persistence | existing repo still owns trail settings | helper extraction only | No |
| `core/common/src/main/java/com/example/xcpro/common/units/UnitsRepository.kt` | Existing | units preference persistence | existing repo still owns unit preferences | helper extraction only | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/orientation/OrientationMath.kt` | Existing | canonical bearing math owner | already shared pure math | do not keep map-runtime copies | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/AtmosphereMath.kt` | New | canonical atmosphere helper owner | `feature:flight-runtime` owns flight math and is already depended on by map and variometer | avoids a speculative new core module | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapCameraManager.kt` | Existing | camera runtime owner using shared math | callsite belongs here | helper should not remain private here | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapCameraPolicy.kt` | Existing | camera policy using shared math | callsite belongs here | helper should not remain private here | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/IconHeadingSmoother.kt` | Existing | visual-only icon smoothing using shared math | callsite belongs here | helper should not remain private here | No |
| `feature/map/src/main/java/com/example/xcpro/replay/ReplaySampleEmitter.kt` | Existing | replay sample owner using shared atmosphere math | replay emission remains local | helper should not remain private here | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/WindEstimator.kt` | Existing | wind estimate owner using shared atmosphere math | callsite belongs here | helper should not remain private here | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt` | Existing | flight-state owner using shared atmosphere math | callsite belongs here | helper should not remain private here | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/WindSensorInputAdapter.kt` | Existing | wind-input adapter using shared atmosphere math | callsite belongs here | helper should not remain private here | No |
| `feature/variometer/src/main/java/com/example/xcpro/hawk/HawkVarioEngine.kt` | Existing | HAWK engine using shared atmosphere math | callsite belongs here | helper should not remain private here | No |
| `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerComponents.kt` | Existing | single nav drawer component owner | already active owner | no need for duplicate file | No |
| `feature/map/src/main/java/com/example/xcpro/NavigationDrawerComponents.kt` | Existing | dead duplicate to delete | removal belongs where duplicate lives | keeping it adds dead code | No |
| `feature/variometer/src/main/java/com/example/ui1/VarioDialConfigSupport.kt` | New | shared vario dial helper logic | `feature:variometer` owns `VarioDialConfig` and dial UI types | avoids copying helper into map/profile again | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt` | Existing | map UI caller consuming shared dial helper | caller belongs here | helper should not remain private in this large file | Yes, consume helper rather than add more local logic |
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsScreenRuntimeSupport.kt` | Existing | profile UI caller consuming shared dial helper | caller belongs here | helper should not remain private in this large file | Yes, consume helper rather than add more local logic |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `ProfileSettingsProfileIds` | `core:common` | profile, map, units, tests | existing public object | canonical owner already exists | use directly; keep `ProfileIdResolver` shim narrow until separate cleanup |
| `OrientationMath` helpers | `feature:flight-runtime` | map-runtime, flight-runtime tests | existing public top-level functions | canonical owner already exists | reuse directly; remove map-runtime private copies |
| `AtmosphereMath` helper | `feature:flight-runtime` | map replay, flight-runtime, variometer | new public pure helper | shared physics formulas need one owner | additive helper; no shim expected |
| `VarioDialConfigSupport` helper | `feature:variometer` | map UI, profile UI | new public pure UI helper | shared dial config/label logic already depends on variometer-owned dial types | additive helper; no shim expected |

### 2.2F Scope Ownership and Lifetime

No new long-lived scopes are allowed in this plan.

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileIdResolver.kt` | `feature:profile` | preserves existing profile-facing name while core/common owns the real normalization logic | direct `ProfileSettingsProfileIds` use or explicit supported alias decision | after all non-test production callsites are reviewed and either migrated or intentionally left on the wrapper | existing `app/src/test/java/com/example/xcpro/profiles/ProfileIdResolverTest.kt` |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| default-profile alias normalization | `core/common/src/main/java/com/example/xcpro/core/common/profiles/ProfileSettingsProfileIds.kt` | profile/map/unit repos and tests | repo-wide profile alias policy already lives here | No |
| bearing normalization + shortest-angle delta | `feature/flight-runtime/src/main/java/com/example/xcpro/orientation/OrientationMath.kt` | map-runtime camera and icon smoothing | shared pure orientation math already lives here | No |
| density ratio + pressure-altitude helpers | `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/AtmosphereMath.kt` | replay, wind, flight-state, HAWK | flight/replay/airspeed math belongs with runtime math owners | No |
| vario dial label/config generation | `feature/variometer/src/main/java/com/example/ui1/VarioDialConfigSupport.kt` | map overlay UI and HAWK settings UI | helper depends on variometer-owned dial UI types | No |

### 2.3 Time Base

This plan does not introduce any new time-dependent values. It only consolidates pure helper logic and deletes dead duplicates.

Timebase rules for touched callsites remain:

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| replay sample timestamps in `ReplaySampleEmitter` | Replay | unchanged; helper extraction must not alter replay timing |
| flight-state freshness/staleness windows | Monotonic | unchanged; not owned by consolidated helpers |
| profile ID normalization | N/A | pure string normalization only |
| bearing normalization / atmosphere helpers | N/A | pure math only |

Explicitly forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged
- Primary cadence/gating sensor:
  - unchanged
- Hot-path latency budget:
  - helper extraction must remain allocation-light and must not add blocking work or new coroutines

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged
  - the shared atmosphere helper must remain pure and must not read clocks, Android state, network state, or randomness

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| duplicate profile-ID normalization survives | `ARCHITECTURE.md` canonical formula owner; `CODING_RULES.md` `15C` | unit test + review | `app/src/test/java/com/example/xcpro/profiles/ProfileIdResolverTest.kt`, `app/src/test/java/com/example/xcpro/common/units/UnitsRepositoryProfileScopeTest.kt`, `feature/map/src/test/java/com/example/xcpro/map/QnhPreferencesRepositoryTest.kt`, `feature/map/src/test/java/com/example/xcpro/map/trail/MapTrailPreferencesTest.kt` |
| map-runtime bearing helper drift remains after cleanup | `ARCHITECTURE.md` canonical formula owner | unit test + review | `feature/flight-runtime/src/test/java/com/example/xcpro/orientation/OrientationMathTest.kt`, `feature/map-runtime/src/test/java/com/example/xcpro/map/MapCameraManagerBearingUpdateTest.kt`, `feature/map/src/test/java/com/example/xcpro/map/IconHeadingSmootherTest.kt` |
| shared atmosphere math changes behavior | `ARCHITECTURE.md` canonical formula owner; replay determinism invariants | new helper tests + affected callsite tests | new `AtmosphereMathTest.kt`, `feature/map/src/test/java/com/example/xcpro/replay/ReplaySampleEmitterTest.kt`, `feature/flight-runtime/src/test/java/com/example/xcpro/sensors/domain/WindEstimatorTest.kt`, `feature/variometer/src/test/java/com/example/xcpro/hawk/HawkVarioEngineTest.kt` |
| dead duplicate file deletion breaks hidden callers | compile + review | `:feature:map:compileDebugKotlin` and repo search for remaining imports/calls |
| vario dial helper split changes labels/config | unit test + compile | new variometer helper test plus `:feature:map:compileDebugKotlin` and `:feature:profile:compileDebugKotlin` |

## 3) Data Flow (Before -> After)

Before:

```text
caller-owned state
  -> private duplicate helper inside each file
  -> repeated policy/math implementations
```

After:

```text
caller-owned state
  -> canonical shared helper owner
  -> one reusable policy/math implementation
```

No runtime authority or SSOT flow changes are intended.

## 4) Implementation Phases

### Phase 0 - Baseline and seam lock

- Goal:
  - Confirm the exact duplicate set and lock current behavior with tests before refactoring shared owners.
- Files to change:
  - plan doc only unless a missing regression test is required before code moves
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - only missing baseline tests required for later phases
- Exit criteria:
  - canonical owners are named
  - phase boundaries and non-goals are frozen
  - no behavior changes land in this phase

### Phase 1 - Low-risk dead code and existing-owner reuse

- Goal:
  - Remove dead duplicate UI artifacts and rewire the duplicate profile-ID helpers to the already-existing canonical owner.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/NavigationDrawerComponents.kt` (delete)
  - `feature/profile/src/main/java/com/example/xcpro/MapOrientationPreferences.kt`
  - `feature/profile/src/main/java/com/example/xcpro/map/MapStyleRepository.kt`
  - `feature/profile/src/main/java/com/example/xcpro/map/QnhPreferencesRepository.kt`
  - `feature/profile/src/main/java/com/example/xcpro/map/trail/MapTrailPreferences.kt`
  - `core/common/src/main/java/com/example/xcpro/common/units/UnitsRepository.kt`
- Ownership/file split changes in this phase:
  - no state owner changes
  - one dead file removed
  - one canonical alias owner reused
- Tests to add/update:
  - update or extend affected repository tests only if they currently depend on private helper shape
- Exit criteria:
  - no private `resolveProfileId(...)` copies remain in scoped files
  - nav drawer duplicate file is deleted
  - all scoped tests and targeted compile pass

### Phase 2 - Pure shared math and UI helper consolidation

- Goal:
  - Reuse existing bearing math and consolidate duplicate vario dial UI helpers without touching behavior.
- Files to change:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapCameraManager.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapCameraPolicy.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/IconHeadingSmoother.kt`
  - `feature/variometer/src/main/java/com/example/ui1/VarioDialConfigSupport.kt` (new)
  - `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt`
  - `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsScreenRuntimeSupport.kt`
- Ownership/file split changes in this phase:
  - map-runtime stops owning copied bearing helpers
  - variometer owns the shared dial-config helper
- Tests to add/update:
  - `OrientationMathTest` if needed
  - `MapCameraManagerBearingUpdateTest`
  - `IconHeadingSmootherTest`
  - new pure helper test for vario dial label/config generation
- Exit criteria:
  - no private bearing helper copies remain in scoped files
  - map/profile UI call the shared dial helper
  - compile and targeted tests pass

### Phase 3 - Canonical atmosphere math owner

- Goal:
  - Move duplicated standard-atmosphere helpers into one shared pure owner in `feature:flight-runtime`.
- Files to change:
  - `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/AtmosphereMath.kt` (new)
  - `feature/map/src/main/java/com/example/xcpro/replay/ReplaySampleEmitter.kt`
  - `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/domain/WindEstimator.kt`
  - `feature/flight-runtime/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
  - `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/WindSensorInputAdapter.kt`
  - `feature/variometer/src/main/java/com/example/xcpro/hawk/HawkVarioEngine.kt`
- Ownership/file split changes in this phase:
  - one new shared pure helper owner
  - no runtime state ownership changes
- Tests to add/update:
  - new `AtmosphereMathTest.kt`
  - `ReplaySampleEmitterTest`
  - `WindEstimatorTest`
  - `HawkVarioEngineTest`
  - add a focused `FlightStateRepository` regression if needed for pressure-altitude fallback behavior
- Exit criteria:
  - no duplicate density/pressure-altitude helper copies remain in scoped files
  - replay/live behavior stays deterministic and unchanged
  - targeted tests and full required gates pass

### Phase 4 - Closeout and optional smaller duplicates

- Goal:
  - Remove any narrow leftover wrappers discovered during phases 1-3 and close the plan cleanly.
- Files to change:
  - only genuinely remaining duplicates discovered during implementation
  - optional: evaluate `ProfileIdResolver` wrapper and small UI duplicates such as repeated section-header helpers if they can be removed without broad churn
- Ownership/file split changes in this phase:
  - cleanup only; no new owners
- Tests to add/update:
  - only where a retained shim or final deletion needs lock coverage
- Exit criteria:
  - all scoped high-confidence duplicates are either removed or explicitly inventoried as temporary shims
  - plan status updated with actual completion notes

## 5) Test Plan

- Unit tests:
  - `app/src/test/java/com/example/xcpro/profiles/ProfileIdResolverTest.kt`
  - `app/src/test/java/com/example/xcpro/common/units/UnitsRepositoryProfileScopeTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/QnhPreferencesRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/trail/MapTrailPreferencesTest.kt`
  - `feature/flight-runtime/src/test/java/com/example/xcpro/orientation/OrientationMathTest.kt`
  - `feature/map-runtime/src/test/java/com/example/xcpro/map/MapCameraManagerBearingUpdateTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/IconHeadingSmootherTest.kt`
  - new `AtmosphereMathTest.kt`
  - `feature/flight-runtime/src/test/java/com/example/xcpro/sensors/domain/WindEstimatorTest.kt`
  - `feature/variometer/src/test/java/com/example/xcpro/hawk/HawkVarioEngineTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/replay/ReplaySampleEmitterTest.kt`
- Replay/regression tests:
  - `ReplaySampleEmitterTest`
  - existing replay regressions that already cover airspeed reconstruction behavior
- UI/instrumentation tests (if needed):
  - not expected; compile coverage should be enough unless dial-config extraction reveals behavior that is currently only tested through UI
- Degraded/failure-mode tests:
  - helper invalid-input tests for atmosphere math
- Boundary tests for removed bypasses:
  - profile-scope repository tests after `resolveProfileId(...)` copy removal
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | `OrientationMathTest`, new `AtmosphereMathTest`, `WindEstimatorTest`, `HawkVarioEngineTest`, `ReplaySampleEmitterTest` |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | existing replay tests; no timebase redesign in scope |
| Persistence / settings / restore | Round-trip / restore / migration tests | units/QNH/trail/profile repository tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | repository tests and compile checks around shared helper imports |
| UI interaction / lifecycle | UI or instrumentation coverage | not expected; compile-only unless behavior changes surface |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | not expected; pure helper extraction only |

Required checks at slice-complete gates:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Smaller per-phase checks:

```bash
./gradlew :feature:map:compileDebugKotlin
./gradlew :feature:profile:testDebugUnitTest
./gradlew :feature:map:testDebugUnitTest
./gradlew :feature:flight-runtime:testDebugUnitTest
./gradlew :feature:variometer:testDebugUnitTest
```

Connected tests:
- not required unless an apparently pure helper extraction unexpectedly changes runtime UI behavior

Map visual SLO evidence:
- not required if no interaction or overlay behavior changes

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| atmosphere helper extraction subtly changes replay or HAWK outputs | medium | land Phase 3 separately with dedicated unit/regression tests | XCPro Team |
| deleting `NavigationDrawerComponents.kt` breaks hidden callsites | low | repo-wide search first, then targeted map compile | XCPro Team |
| profile-ID consolidation accidentally changes legacy-alias fallback behavior | medium | rely on existing profile-ID and repository tests before and after rewiring | XCPro Team |
| shared helper additions turn into broad API sprawl | medium | keep helper APIs narrow, pure, and named in this plan; no new module creation | XCPro Team |
| refactor scope grows into a generic duplicate-code sweep | high | keep work to the listed findings only; anything else becomes a new plan or Phase 4 optional item | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  - This plan reuses existing owners or adds narrow helpers inside already-correct owner modules.
- Why this belongs in a change plan instead of plan notes:
  - no new module, dependency-graph, concurrency-policy, or durable runtime-boundary decision is being introduced

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- No new duplicate formula owner introduced
- Existing canonical owners are reused where they already exist
- Replay behavior remains deterministic
- No UI/business-logic boundary regressions
- `KNOWN_DEVIATIONS.md` remains unchanged unless an explicitly approved exception is required

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1, Phase 2, and Phase 3 should land as separate PRs and can be reverted independently
- Recovery steps if regression is detected:
  - revert the affected phase PR
  - restore the previous local helper at the callsite only if needed for emergency rollback
  - keep the canonical-owner test additions where they still provide value

## 9) Recommended Execution Order

1. Land Phase 1 first. It removes the dead duplicate file and reuses an already-existing canonical owner, so it has the best risk-to-value ratio.
2. Land Phase 2 next. It is still behavior-preserving, but it touches runtime UI code and should stay separate from physics math.
3. Land Phase 3 last. It is still a pure refactor, but it affects replay, wind, flight-state, and HAWK code paths, so it deserves its own focused test pass and review.

## 10) Readiness Verdict

`Ready`

Implementation can start after one scope discipline check:
- keep the work to the listed duplicates only
- do not turn this into a generalized cleanup sweep
- keep Phase 3 isolated from Phases 1 and 2
