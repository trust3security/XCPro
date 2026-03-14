# Profile_Settings_Contributor_Boundary_Refactor_Plan_2026-03-13.md

## Purpose

Refactor profile settings export/import so `feature:profile` remains the
orchestrator of bundle format and restore flow, while each feature owns capture
and apply logic for its own settings sections.

This plan exists to reduce cross-module drift without changing the exported
bundle format or introducing ad-hoc wiring.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/AGENT.md`

## 0) Metadata

- Title: Profile Settings Contributor Boundary Refactor
- Owner: XCPro Team
- Date: 2026-03-13
- Issue/PR: TBD
- Status: In Progress

## 0A) Current Status Snapshot

- Phase 0 complete:
  - bundle/profile regression tests lock current ordering and behavior
- Phase 1 complete:
  - shared profile section contract, canonical ordering, contributor registry,
    and DI scaffolding are in place
- Phase 2 complete:
  - all clearly local `feature:profile` sections now capture/apply via local
    contributors
  - `AppProfileSettingsSnapshotProvider` is reduced from 517 lines to 118 lines
  - `AppProfileSettingsRestoreApplier` is reduced from 660 lines to 134 lines
- Phase 3 complete:
  - `VARIOMETER_WIDGET_LAYOUT` capture/apply now lives in `feature:variometer`
  - `OGN_TRAFFIC_PREFERENCES` capture/apply now lives in `feature:traffic`
  - `OGN_TRAIL_SELECTION_PREFERENCES` capture/apply now lives in
    `feature:traffic`
  - `ADSB_TRAFFIC_PREFERENCES` capture/apply now lives in `feature:traffic`
  - `WEATHER_OVERLAY_PREFERENCES` capture/apply now lives in `feature:weather`
  - `FORECAST_PREFERENCES` capture/apply now lives in `feature:forecast`
  - traffic-owned profile settings now migrate through owner-owned contributors
- Phase 4 complete:
  - `feature:profile` no longer carries production `implementation` dependencies
    on `:feature:forecast`, `:feature:traffic`, or `:feature:weather`
  - those owner modules remain available to `feature:profile` tests via
    `testImplementation`
  - `scripts/ci/enforce_rules.ps1` now blocks direct owner-module package
    imports in `AppProfileSettingsSnapshotProvider` and
    `AppProfileSettingsRestoreApplier`
  - `scripts/ci/enforce_rules.ps1` now blocks production `implementation`/`api`
    reintroduction of `:feature:forecast`, `:feature:traffic`, and
    `:feature:weather` into `feature:profile`
- Still intentionally deferred:
  - `CARD_PREFERENCES` remains in the orchestrator path and should be migrated
    in its own dedicated batch
  - active seam lock for that batch:
    `docs/refactor/Profile_Card_Boundary_Seam_Lock_2026-03-14.md`
  - Phase A of that seam is now complete:
    - `core/common -> :dfcards-library` has been removed
  - Phase B of that seam is now complete:
    - owner-owned `CARD_PREFERENCES` contributor extraction now lives in
      `dfcards-library`
  - Phase C of that seam is now complete:
    - production UI no longer constructs `CardPreferences(context)`
    - the profile/card seam is closed at the ownership level

## 1) Scope

- Problem statement:
  - `AppProfileSettingsSnapshotProvider` and `AppProfileSettingsRestoreApplier`
    are large cross-feature switchboards.
  - They directly depend on sibling-feature repositories for forecast, weather,
    traffic, and variometer-owned settings.
  - Adding or changing a settings section currently tends to require edits in
    `feature:profile`, even when `feature:profile` does not own the runtime
    setting.
- Why now:
  - This is a valid architecture exception area, but it is currently implemented
    as concrete cross-feature knowledge instead of a stable integration seam.
  - That makes long-term ownership expensive and increases cross-module drift.
- In scope:
  - Introduce contributor-style capture/apply contracts for profile settings
    sections.
  - Keep the profile bundle format and section IDs stable.
  - Move external feature-owned settings section logic out of
    `AppProfileSettingsSnapshotProvider` and `AppProfileSettingsRestoreApplier`.
  - Add DI assembly and regression tests for deterministic section handling.
- Out of scope:
  - Redesign of profile export/import UX.
  - Changes to section payload semantics unless a bug is already known.
  - Moving all settings repositories between modules.
  - Broad cleanup of unrelated `feature:profile` coupling such as
    `ProfileScopedDataCleaner`, unless needed for this refactor slice.
- User-visible impact:
  - None intended.
  - Exported bundles should remain backward-compatible and restore behavior
    should remain unchanged.

## 1A) Baseline Evidence

Current switchboard hotspots:

- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
  - 660 lines
- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
  - 517 lines

Current cross-feature dependencies in `feature/profile` driven by settings
capture/apply:

- `:feature:forecast`
- `:feature:traffic`
- `:feature:weather`
- `:feature:variometer`

Known external feature-owned settings currently captured/applied inside
`feature:profile`:

- Forecast overlay preferences
- Weather overlay preferences
- OGN traffic preferences
- OGN trail selection preferences
- ADS-B traffic preferences
- Variometer widget layout

Known in-module settings that may remain local to `feature:profile` for this
refactor:

- Card preferences
- Flight management preferences
- Look and feel / theme
- Map widget layout
- Glider config
- Units
- Map style
- Snail trail
- Orientation
- QNH
- Levo vario preferences
- Thermalling mode preferences
- Wind override preferences

Current Phase 0 / 1 test and contract gaps:

- current tests mostly assert section membership, not section ordering
- `ProfileSettingsSectionSets` are represented as `Set<String>`, which is not a
  strong enough contract for deterministic contributor iteration
- external contributors cannot depend on `feature:profile` without creating a
  cycle, so the contributor contract and canonical section ID registry cannot
  live only in `feature:profile`
- `ProfileSettingsSectionSnapshots.kt` currently keeps section DTOs internal to
  `feature:profile`; external feature contributors must not be forced to depend
  on that file as-is
- `ProfileSettingsSectionIds` declares dormant IDs for waypoint/airspace
  preferences that are not part of current capture sets; ordered registries must
  preserve that inactive status unless a separate feature plan activates them

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Runtime forecast settings | `ForecastPreferencesRepository` | repository APIs / flows | copied forecast business state inside `feature:profile` |
| Runtime weather settings | `WeatherOverlayPreferencesRepository` | repository APIs / flows | copied weather business state inside `feature:profile` |
| Runtime OGN / ADS-B settings | traffic repositories | repository APIs / flows | copied traffic business state inside `feature:profile` |
| Runtime variometer layout | `VariometerWidgetRepository` | repository APIs / flows | copied runtime layout state inside `feature:profile` |
| Profile bundle section IDs and import/export policy | profile settings contract/orchestrator | stable section ID constants and bundle rules | feature-local ad-hoc section ID strings |
| Exported profile settings snapshot | `ProfileSettingsSnapshotProvider` orchestration | `ProfileSettingsSnapshot` | alternate snapshot builders per feature bypassing orchestrator |
| Per-section capture/apply logic | section contributor owned by the feature that owns the runtime setting | contributor interface | section logic duplicated in `AppProfileSettings*` plus leaf feature |
| Restore result aggregation | `ProfileSettingsRestoreApplier` orchestration | `ProfileSettingsRestoreResult` | per-feature hidden failure handling |

SSOT rule for this refactor:

- Runtime settings remain authoritative in their existing repositories.
- `ProfileSettingsSnapshot` is an export artifact, not runtime SSOT.
- `feature:profile` may orchestrate profile bundle capture/apply, but it must
  not become the implementation owner of sibling-feature settings.

### 2.2 Dependency Direction

Primary flow for this slice:

`ProfileRepository -> profile settings orchestrator -> contributor port -> owning feature repository`

This is an integration path, not UI logic. No UI or domain logic should be
pulled into the bundle path.

- Modules/files touched:
  - `feature/profile/**`
  - likely a small contract surface in `core/**` or an equivalent shared module
  - `feature/forecast/**`
  - `feature/weather/**`
  - `feature/traffic/**`
  - `feature/variometer/**`
  - DI bindings in the contributing modules and/or `app`
- Boundary risk:
  - moving contributor contracts into `feature:profile` would create new
    sibling-feature dependencies on a feature module.
  - app/DI assembly must not absorb section payload logic.
  - contributor discovery must not reintroduce hidden global mutable state.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Forecast settings capture/apply | `AppProfileSettings*` switchboard | forecast contributor | forecast owns forecast settings | forecast contributor tests + bundle regression tests |
| Weather settings capture/apply | `AppProfileSettings*` switchboard | weather contributor | weather owns weather settings | weather contributor tests + bundle regression tests |
| OGN / ADS-B settings capture/apply | `AppProfileSettings*` switchboard | traffic contributors | traffic owns traffic settings | traffic contributor tests + bundle regression tests |
| Variometer layout capture/apply | `AppProfileSettings*` switchboard | variometer contributor | variometer owns variometer layout persistence | variometer contributor tests + restore regression tests |
| Bundle orchestration and result aggregation | monolithic `AppProfileSettings*` classes | retained orchestrators delegating to contributors | preserve one import/export entrypoint | provider/applier orchestration tests |
| Section order / duplicate-owner validation | implicit hard-coded order in monolith | explicit registry/ordering policy | preserve determinism as contributors move out | deterministic order tests + duplicate-owner tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AppProfileSettingsSnapshotProvider` direct external repository capture | `feature:profile` knows sibling feature repository details | contributor capture interface + DI set | 2 / 3 |
| `AppProfileSettingsRestoreApplier` direct external repository apply | `feature:profile` knows sibling feature payload parsing and restore details | contributor apply interface + DI set | 2 / 3 |
| Section ownership implied by `when` / `applySection(...)` blocks | one giant switchboard owns all sections | explicit section-owner registry | 1 |
| DI iteration order | implicit constructor field order | canonical section order registry | 1 |

### 2.3 Time Base

This refactor must not change the time semantics of any stored payload.
Settings-related timestamps remain opaque payload values copied through the
bundle path.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| QNH `capturedAtWallMs` | Wall | user-entered calibration metadata |
| Forecast `selectedTimeUtcMs` | Wall/UTC | forecast selection state |
| Wind override `timestampMillis` | Wall | manual override metadata |
| Card template `createdAt` | Wall | persistence metadata only |

Rules:

- This refactor must not reinterpret, compare, or recompute these values.
- Capture/apply should preserve existing behavior bit-for-bit where possible.

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `IO` for bundle persistence and contributor capture/apply work
  - no Main-thread requirement should be introduced
- Primary cadence/gating path:
  - event-driven only on export/import/backup sync
- Hot-path latency budget:
  - not user-frame-critical, but import/export should remain bounded and not
    add blocking work on Main

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - None introduced by this refactor
  - bundle encode/decode order must remain deterministic

Determinism note:

- Contributor discovery order must never define output order.
- Orchestrators must emit sections using canonical section ordering, not raw DI
  set iteration.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| `feature:profile` keeps importing sibling feature settings repositories | ARCHITECTURE dependency direction; CODING_RULES drift prevention | enforceRules pattern scan | `scripts/ci/enforce_rules.ps1` |
| contributor set owns duplicate section IDs | SSOT / explicit ownership | unit test + runtime registry check | new contributor registry tests |
| DI set iteration changes export order | determinism | unit test | new snapshot ordering test |
| moved contributor changes payload format | regression resistance | existing bundle codec and provider/applier tests | `AppProfileSettings*Test`, `ProfileRepositoryBundleTest` |
| partial restore failure handling changes | error handling | unit test | restore applier orchestration tests |
| profile bundle scope filters stop working | bundle contract | unit test | `ProfileRepositoryBundleTest` |

### 2.7 Visual UX SLO Contract

Not applicable. This refactor does not target map/overlay/replay interaction
runtime behavior.

## 3) Data Flow (Before -> After)

Before:

```
ProfileRepository
  -> ProfileSettingsSnapshotProvider
     -> concrete sibling-feature repositories
  -> ProfileSettingsSnapshot

ProfileRepository
  -> ProfileSettingsRestoreApplier
     -> parse + apply each section directly
     -> concrete sibling-feature repositories
```

After:

```
ProfileRepository
  -> ProfileSettingsSnapshotProvider (orchestrator only)
     -> contributor registry (canonical section order)
        -> section contributor owned by feature
           -> owning repository
  -> ProfileSettingsSnapshot

ProfileRepository
  -> ProfileSettingsRestoreApplier (orchestrator only)
     -> contributor registry (canonical section order)
        -> section contributor owned by feature
           -> owning repository
  -> ProfileSettingsRestoreResult
```

Contract note:

- Section payload DTOs should remain private to the owning contributor where
  practical. The shared contract should be section IDs plus contributor APIs,
  not a large cross-module snapshot DTO package.
- Therefore the contributor API should work at the `JsonElement` boundary:
  contributors may build/parse their own typed section DTOs internally, but the
  cross-module contract should remain small.

## 4) Implementation Phases

### Phase 0: Baseline Lock

- Goal:
  - Lock current bundle/export/import behavior before moving ownership.
- Files to change:
  - targeted tests only
- Tests to add/update:
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt`
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`
  - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTest.kt`
  - add explicit deterministic section-order expectations for:
    - export bundle settings section order
    - backup sync settings section order
    - restore applier applied/failed section aggregation order where exposed
  - add partial-failure aggregation expectations
  - add scope-filter expectations that prove:
    - aircraft-profile export excludes global sections
    - profile-scoped restore excludes global sections
    - full-bundle restore preserves all included sections
- Exit criteria:
  - existing export/import behavior is locked by tests
  - section-order expectations are explicit

### Phase 1: Contract and Registry Introduction

- Goal:
  - Introduce contributor contracts and a deterministic registry without
    changing bundle behavior.
- Files to change:
  - new shared contract surface in a lower-level module (`core:common` or a new
    dedicated contract module), not `feature:profile`, for:
    - contributor interfaces
    - canonical section ordering / supported-section registry
    - section ID constants and ordered section lists
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsBindingsModule.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSnapshot.kt`
  - new orchestrator helper/registry files under `feature/profile`
- Tests to add/update:
  - contributor registry duplicate-owner test
  - deterministic section ordering test
  - no-op registry/orchestrator behavior tests
  - contract-level test proving orchestration walks canonical order instead of
    DI registration order
- Exit criteria:
  - provider/applier can delegate through contributor contracts
  - ordering is canonical and test-locked
  - duplicate section ownership fails fast
  - no new feature-module cycle is introduced by the contract location

Phase 1 implementation rules:

- Do not use raw DI `Set` iteration as the source of truth for order.
- Prefer:
  - `Map<String, ProfileSettingsSectionContributor>` for ownership lookup
  - canonical ordered section lists for iteration
- Contributor cross-module API should be small, for example:
  - section ID(s)
  - `suspend fun capture(...) : JsonElement?`
  - `suspend fun apply(payload: JsonElement, importedProfileIdMap: Map<String, String>)`
- Feature-local typed DTOs may remain private behind that API.

### Phase 2: Local Decomposition in `feature:profile`

- Goal:
  - Shrink the switchboard by converting in-module settings sections to local
    contributors first, with no cross-module moves yet.
- Files to change:
  - `AppProfileSettingsSnapshotProvider.kt`
  - `AppProfileSettingsRestoreApplier.kt`
  - new local contributor files under `feature/profile/src/main/java/com/example/xcpro/profiles/`
- Tests to add/update:
  - per-contributor capture/apply tests for local sections as needed
  - orchestration tests still passing unchanged
- Exit criteria:
  - provider/applier become orchestration-only
  - large local blocks moved to focused contributors
  - no bundle-format regression

### Phase 3: External Feature Section Migration

- Goal:
  - Move external feature-owned settings sections out of `feature:profile`.
- Files to change:
  - `feature/forecast/**`
  - `feature/weather/**`
  - `feature/traffic/**`
  - `feature/variometer/**`
  - `feature/profile/**` orchestrator bindings
  - possibly `app/**` if composition wiring needs a central install site
- Tests to add/update:
  - forecast contributor tests
  - weather contributor tests
  - traffic contributor tests
  - variometer layout contributor tests
  - bundle regression tests across mixed local/external contributors
- Exit criteria:
  - forecast/weather/traffic/variometer section logic no longer lives in
    `AppProfileSettings*`
  - `feature:profile` no longer imports those external repositories in the
    provider/applier path
  - bundle roundtrip tests still pass

### Phase 3A: Traffic Batch Execution Plan

- Goal:
  - Finish the traffic-owned section migrations before starting weather or
    forecast.
  - Keep the change surface narrow by moving one owner module at a time.
- Status:
  - complete
  - `OGN_TRAFFIC_PREFERENCES`, `OGN_TRAIL_SELECTION_PREFERENCES`, and
    `ADSB_TRAFFIC_PREFERENCES` now capture/apply from `feature:traffic`
- Why this batch:
  - `OGN_TRAIL_SELECTION_PREFERENCES` and `ADSB_TRAFFIC_PREFERENCES` are still
    traffic-owned runtime settings implemented in `feature:profile`.
  - `OGN_TRAFFIC_PREFERENCES` is already owner-owned in `feature:traffic`, so
    leaving the other traffic sections behind would preserve split ownership.

#### Step 1: `OGN_TRAIL_SELECTION_PREFERENCES`

- Current implementation still lives in:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
- Runtime owner already exists in:
  - `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
- Planned changes:
  - add `OgnTrailSelectionProfileSettingsContributor` under
    `feature/traffic/src/main/java/com/example/xcpro/ogn/`
  - bind it through `TrafficProfileSettingsBindingsModule.kt`
  - keep a feature-local payload DTO inside the new contributor
  - preserve deterministic export by serializing a sorted set of selected
    aircraft keys
  - preserve current restore semantics:
    - clear selected aircraft first
    - then re-apply selected keys through repository normalization
  - remove the direct trail-selection capture/apply branches and constructor
    dependency from `AppProfileSettingsSnapshotProvider` and
    `AppProfileSettingsRestoreApplier`
  - add `OGN_TRAIL_SELECTION_PREFERENCES` to
    `ExtractedProfileSettingsContributorSections`
  - remove `OgnTrailSelectionSectionSnapshot` from
    `ProfileSettingsSectionSnapshots.kt`
- Tests:
  - new owner-module test covering capture payload and restore behavior
  - update focused profile tests to register the new contributor
- Exit criteria:
  - no direct `OgnTrailSelectionPreferencesRepository` usage remains in the
    provider/applier path
  - capture/apply behavior stays identical

#### Step 2: `ADSB_TRAFFIC_PREFERENCES`

- Current implementation still lives in:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
- Runtime owner already exists in:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
- Planned changes:
  - add `AdsbTrafficProfileSettingsContributor` under
    `feature/traffic/src/main/java/com/example/xcpro/adsb/`
  - bind it through the traffic multibinding module
  - keep a feature-local payload DTO inside the new contributor
  - preserve the current payload exactly for this slice:
    - `enabled`
    - `iconSizePx`
    - `maxDistanceKm`
    - `verticalAboveMeters`
    - `verticalBelowMeters`
    - `emergencyFlashEnabled`
    - `emergencyAudioEnabled`
    - `emergencyAudioCooldownMs`
    - `emergencyAudioMasterEnabled`
    - `emergencyAudioShadowMode`
    - `emergencyAudioRollbackLatched`
    - `emergencyAudioRollbackReason`
  - preserve current rollback restore semantics:
    - when latched, restore the reason or fall back to `"imported"`
    - otherwise clear the rollback latch
  - do not expand this slice to unrelated ADS-B preference keys such as
    default-medium-unknown-icon rollout fields
  - remove the direct ADS-B capture/apply branches and constructor dependency
    from `AppProfileSettingsSnapshotProvider` and
    `AppProfileSettingsRestoreApplier`
  - add `ADSB_TRAFFIC_PREFERENCES` to
    `ExtractedProfileSettingsContributorSections`
  - remove `AdsbTrafficSectionSnapshot` from
    `ProfileSettingsSectionSnapshots.kt`
- Tests:
  - new owner-module test covering capture payload and restore behavior,
    including rollback-latch preservation
  - update focused profile tests to register the new contributor
- Exit criteria:
  - no direct `AdsbTrafficPreferencesRepository` usage remains in the
    provider/applier path
  - payload format and restore behavior stay identical

#### Batch Stop Condition

- Stop after these two moves.
- Reassess before weather/forecast.
- Do not mix this batch with:
  - weather or forecast migrations
  - `CARD_PREFERENCES`
  - unrelated `feature:map` or app-shell cleanup

#### Focused Verification for This Batch

Run after each step:

```bash
./gradlew :feature:traffic:testDebugUnitTest
./gradlew :feature:profile:testDebugUnitTest --tests "com.example.xcpro.profiles.AppProfileSettingsSnapshotProviderTest" --tests "com.example.xcpro.profiles.AppProfileSettingsRestoreApplierTest" --tests "com.example.xcpro.profiles.ProfileSettingsContributorRegistryTest"
```

When the unrelated `feature:map` Hilt compile blocker is fixed, rerun:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileRepositoryBundleTest" --tests "com.example.xcpro.profiles.ProfileRepositoryBackupSyncTest"
```

#### Traffic Batch Risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| trail-selection ordering drift | bundle snapshots must stay deterministic | capture sorted selected-aircraft keys |
| restore semantics drift for trail selection | repository normalizes keys and current restore clears then reapplies | preserve clear-then-set flow exactly |
| ADS-B payload creep | repository exposes more fields than current bundle shape | keep payload fields exactly as currently exported |
| ADS-B rollback behavior drift | rollback latch has state + optional reason semantics | copy current `"imported"` fallback behavior exactly |
| mixed ownership left behind | traffic work would remain half in `feature:profile` | stop only after both traffic sections are moved |

### Phase 4: Dependency Cleanup and Guardrails

- Goal:
  - Remove obsolete module dependencies and prevent regression.
- Files to change:
  - `feature/profile/build.gradle.kts`
  - contributing module build files if contract dependency is needed
  - `settings.gradle.kts` if a new contract module is introduced
  - `scripts/ci/enforce_rules.ps1`
  - architecture docs if dependency wording changes
- Tests to add/update:
  - build-graph or scan-based guard coverage
  - provider/applier import-pattern tests if practical
- Exit criteria:
  - obsolete `feature:profile` dependencies are removed
  - CI blocks direct reintroduction of sibling-feature repository knowledge in
    `AppProfileSettings*`

### Phase 5: Optional Follow-Up Ownership Cleanup

- Goal:
  - Evaluate remaining non-bundle cross-feature profile ownership seams.
- Files to change:
  - only if approved after the main refactor lands
- Candidate follow-ups:
  - `AppProfileScopedDataCleaner` external ownership
  - any remaining profile-module wrappers that are actually owned by other
    features
- Exit criteria:
  - explicit keep/remove decision documented

## 5) Test Plan

- Unit tests:
  - contributor registry uniqueness and deterministic ordering
  - local contributor capture/apply parity
  - external contributor capture/apply parity
- Replay/regression tests:
  - not replay-specific; bundle export/import determinism is the regression axis
- UI/instrumentation tests:
  - not required unless profile import/export UI behavior changes
- Degraded/failure-mode tests:
  - one contributor fails while others succeed
  - missing contributor for requested section
  - duplicate contributor section ownership
  - unknown section in imported bundle remains reported, not silently applied
- Boundary tests for removed bypasses:
  - provider/applier no longer import external feature repositories directly

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| contributor iteration changes section order | High | canonical section order registry + explicit tests | XCPro Team |
| section ownership becomes ambiguous during migration | High | duplicate-owner validation + phased moves | XCPro Team |
| refactor changes bundle payload shape | High | keep section IDs stable; contributor-specific regression tests | XCPro Team |
| introducing a shared contract in the wrong module creates new drift | Medium | prefer a small contract surface, not a dependency on the full profile feature | XCPro Team |
| over-refactor by moving repositories between modules | Medium | keep repository relocation out of scope unless independently justified | XCPro Team |
| `ProfileScopedDataCleaner` remains as residual coupling | Low/Medium | treat as explicit follow-up, not hidden scope creep | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Bundle section IDs remain backward-compatible
- Section emission/apply order is deterministic and test-locked
- `feature:profile` no longer owns sibling-feature settings implementation for
  migrated sections
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

Quantitative gates:

- `AppProfileSettingsSnapshotProvider.kt` and
  `AppProfileSettingsRestoreApplier.kt` reduced below the default line budget or
  covered by an approved temporary deviation during the migration window
- `feature:profile` direct provider/applier imports for forecast/weather/traffic
  and variometer-owned sections removed by end of Phase 4
- build/test gates pass after each phase

## 8) Rollback Plan

- What can be reverted independently:
  - contributor contracts
  - local contributor extraction
  - each external feature migration
  - guardrail additions
- Recovery steps if regression is detected:
  1. Revert the last migrated contributor only.
  2. Keep baseline tests and registry validation if they still pass.
  3. Re-run required checks.
  4. Re-open the failed phase with narrower scope.
