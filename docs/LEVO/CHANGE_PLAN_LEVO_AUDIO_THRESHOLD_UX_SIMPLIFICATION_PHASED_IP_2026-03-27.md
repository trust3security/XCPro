# CHANGE_PLAN_LEVO_AUDIO_THRESHOLD_UX_SIMPLIFICATION_PHASED_IP_2026-03-27

## Purpose

Define a phased implementation plan that removes the current user-facing
confusion around Levo audio thresholds without changing authoritative ownership,
replay behavior, or audio cadence.

This plan follows:

1. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/LEVO/levo.md`
7. `docs/LEVO/levo-replay.md`

## 0) Metadata

- Title: Levo Audio Threshold UX Simplification
- Owner: XCPro Team
- Date: 2026-03-27
- Issue/PR: LEVO-20260327-AUDIO-THRESHOLD-UX
- Status: Implemented
- Readiness verdict: Completed

## 1) Scope

- Problem statement:
  - The current Levo settings UI exposes four threshold controls:
    `liftThreshold`, `deadbandMin`, `deadbandMax`, and
    `sinkSilenceThreshold`.
  - The positive pair is confusing because actual climb beeps start at
    `max(liftThreshold, deadbandMax)`, so two sliders describe one effective
    user-visible boundary.
  - The negative pair is similarly confusing because sink tone activation uses
    `min(sinkSilenceThreshold, deadbandMin)`, so two sliders again collapse to
    one effective boundary.
  - The UI text currently suggests separate concepts even though current audio
    mapping does not give users two independently meaningful behaviors.
- Why now:
  - The current UX teaches the wrong mental model.
  - Existing defaults already collapse the positive pair to the same value
    (`+0.1 m/s`) and the negative pair to one effective sink boundary.
  - This is a small but high-friction settings surface that should be clarified
    before additional profile/settings work increases the cost of change.
- In scope:
  - Define one canonical user-facing threshold per side:
    positive lift start and negative sink start.
  - Preserve current effective runtime behavior during migration.
  - Keep `LevoVarioPreferencesRepository` as the authoritative owner.
  - Add a shared pure policy/helper so UI, persistence migration, and runtime
    tests use the same semantics.
  - Stage profile snapshot/restore compatibility so existing saved settings are
    not lost.
- Out of scope:
  - Retuning frequency mapping, duty cycle, audio pitch curve, or tone cadence.
  - Changing TE/raw audio input selection.
  - Changing replay/live wiring or timebase behavior.
  - Reworking Purple Needle behavior beyond documentation updates.
- User-visible impact:
  - Users see one meaningful positive threshold and one meaningful negative
    threshold instead of redundant pairs.
  - Existing saved configurations keep the same effective audio start points.
- Rule class touched: Default

### 1.1 Current-State Findings

1. Positive audio start is already one effective boundary.
- `VarioFrequencyMapper.mapLift(...)` gates lift audio with:
  `maxOf(settings.liftThreshold, effectiveDeadbandMax())`.
- This means the higher of `liftThreshold` and `deadbandMax` wins.

2. Negative audio start is already one effective boundary.
- `VarioFrequencyMapper.mapSink(...)` computes:
  `minOf(settings.sinkSilenceThreshold, settings.deadbandMin)`.
- This means the lower or more-negative value wins.

3. The settings UI currently exposes redundant concepts.
- `VarioAudioComponents.kt` presents separate sliders for:
  `Lift Threshold`, `Deadband Min`, `Deadband Max`, and
  `Sink Silence Threshold`.
- The explanatory text implies more independent control than the runtime
  actually provides.

4. Persistence and profile capture currently store all four raw fields.
- `LevoVarioPreferencesRepository` persists the four values directly.
- `LevoVarioProfileSettingsContributor` captures and restores the same raw
  fields for profile bundles.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Persisted Levo audio settings | `LevoVarioPreferencesRepository` | `LevoVarioConfig.audioSettings` | Any second mutable settings store in UI or runtime |
| Effective positive audio-start threshold | Derived from `VarioAudioSettings` by canonical helper | Pure helper output only | UI-local `max(...)` copies, profile-local copies |
| Effective negative audio-start threshold | Derived from `VarioAudioSettings` by canonical helper | Pure helper output only | UI-local `min(...)` copies, profile-local copies |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Raw Levo audio threshold fields during compatibility phases | `LevoVarioPreferencesRepository` | repository `updateAudioSettings(...)` only | `config -> audioSettings` | DataStore keys | `LevoVarioPreferencesRepository` | user reset / explicit restore | none | repository round-trip tests |
| Effective lift audio start threshold | none, derived only | none | canonical helper -> UI/runtime tests | `max(liftThreshold, deadbandMax)` | none | recomputed on every read | none | helper + mapper tests |
| Effective sink audio start threshold | none, derived only | none | canonical helper -> UI/runtime tests | `min(sinkSilenceThreshold, deadbandMin)` | none | recomputed on every read | none | helper + mapper tests |

### 2.2 Dependency Direction

Confirmed target dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/profile` for settings UI and profile snapshot/restore integration.
  - `feature/flight-runtime` for shared threshold semantics helper/model.
  - `feature/variometer` for mapper alignment tests and, if needed, final
    consumption cleanup.
- Boundary risk:
  - The Composable must not compute threshold policy itself.
  - The canonical semantics should live in a pure shared owner, not in UI text
    or ad-hoc ViewModel math.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/LevoVarioSettingsViewModel.kt` | Existing Levo settings VM already forwards state mutations through one use-case/repository path | Keep UI as a thin intent-forwarding layer | Replace raw redundant setters with canonical per-side setters |
| `feature/profile/src/main/java/com/example/xcpro/profiles/LevoVarioProfileSettingsContributor.kt` | Existing profile contributor owns Levo settings capture/apply | Keep repository-owned capture/apply path | Add compatibility alias handling before raw-field removal |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Canonical threshold semantics for "effective lift start" and "effective sink start" | Implicit math inside mapper plus duplicated UI interpretation | New pure helper in shared audio model layer | Make one meaning reusable across UI, migration, and tests | helper unit tests + mapper regression tests |
| User-facing threshold editing contract | Raw field setters in ViewModel/UI | Canonical per-side setters using shared helper | Remove UI confusion without changing SSOT | VM tests + UI text/assertion tests where useful |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `VarioAudioComponents.kt` raw sliders for `liftThreshold` and `deadbandMax` | UI exposes redundant positive controls directly | One positive control backed by canonical helper/setter | Phase 2 |
| `VarioAudioComponents.kt` raw sliders for `sinkSilenceThreshold` and `deadbandMin` | UI exposes redundant negative controls directly | One negative control backed by canonical helper/setter | Phase 2 |
| Any future migration logic reading raw fields independently | Ad-hoc caller-specific `min/max` interpretation | Shared compatibility helper | Phase 1 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/LEVO/CHANGE_PLAN_LEVO_AUDIO_THRESHOLD_UX_SIMPLIFICATION_PHASED_IP_2026-03-27.md` | New | Rollout contract for this change | Levo-specific behavior and settings plan | Not a global architecture rule | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/audio/VarioAudioThresholdSemantics.kt` | New | Canonical pure helper for effective threshold semantics and compatibility writes | Shared model layer already owns `VarioAudioSettings` | UI must not own policy; repository should not become the only consumer of semantics | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/audio/VarioAudioSettings.kt` | Existing | Shared runtime settings contract | Canonical settings model already lives here | Avoid duplicate model in profile or variometer modules | No |
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/LevoVarioSettingsViewModel.kt` | Existing | Intent forwarding for settings screen | Current owner of settings UI actions | Do not push mutation logic into Composable | No |
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/VarioAudioComponents.kt` | Existing | Threshold controls and explanatory copy | Existing owner of threshold sliders | UI change belongs in settings component, not repository | Watch line budget only |
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/LevoVarioSettingsScreen.kt` | Existing | Wiring screen state to threshold card | Existing screen owner | Keep state wiring centralized | No |
| `feature/profile/src/main/java/com/example/xcpro/vario/LevoVarioPreferencesRepository.kt` | Existing | Persist and read Levo settings | Authoritative store already here | Do not create second persistence seam | No |
| `feature/profile/src/main/java/com/example/xcpro/profiles/LevoVarioProfileSettingsContributor.kt` | Existing | Profile capture/apply compatibility | Existing profile snapshot owner for Levo settings | Avoid bundle logic in UI or runtime | No |
| `feature/variometer/src/main/java/com/example/xcpro/audio/VarioFrequencyMapper.kt` | Existing | Runtime audio gate semantics | Existing owner of actual tone mapping | Keep mapper authoritative for playback logic | No |
| `feature/flight-runtime/src/test/java/com/example/xcpro/audio/VarioAudioThresholdSemanticsTest.kt` | New | Locks pure effective-threshold semantics | The helper owner lives in `feature:flight-runtime` | Avoid testing shared helper through downstream modules only | No |
| `feature/variometer/src/test/java/com/example/xcpro/audio/VarioFrequencyMapperTest.kt` | New | Locks mapper no-regression behavior | Mapper runtime behavior belongs in `feature:variometer` | Keep shared-helper tests separate from mapper tests | No |
| `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt` | Existing | Profile capture proof | Existing bundle coverage path | Avoid duplicate profile snapshot test harness | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `VarioAudioThresholdSemantics` helper | `feature:flight-runtime` audio model layer | profile UI, repository migration logic, tests | `internal` if same-module only; widen only if named consumers require it | Centralize effective-threshold math | Permanent owner if raw fields remain; revisit if model collapses fully |
| Canonical per-side setter/getter methods | ViewModel/use-case/repository boundary | settings UI and profile restore path | narrowest practical visibility | Replace raw redundant user-facing operations | Keep old raw-field writes only during compatibility phases |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| Effective lift start threshold | `feature/flight-runtime/.../VarioAudioThresholdSemantics.kt` | settings UI, migration, tests, optionally mapper | It is shared policy derived from the shared settings model | No |
| Effective sink start threshold | `feature/flight-runtime/.../VarioAudioThresholdSemantics.kt` | settings UI, migration, tests, optionally mapper | Same reason | No |

### 2.3 Time Base

No new time-dependent values are introduced.

- Threshold values are static m/s settings, not time-based state.
- Replay/live determinism must remain unchanged because only threshold
  interpretation and UI semantics are affected.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged; settings remain persisted through DataStore and observed on the
    existing repository/runtime collection paths.
- Primary cadence/gating sensor:
  - unchanged; audio still evaluates on the existing runtime sample cadence.
- Hot-path latency budget:
  - unchanged; helper math is pure `min/max` and must not add measurable work.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added by this plan
  - the same effective thresholds must apply to live and replay settings
    projections

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| Existing saved config has divergent raw pairs (for example `liftThreshold != deadbandMax`) | Recoverable | repository/helper migration logic | User sees one canonical effective threshold per side | Preserve current effective runtime behavior by using `max(...)` for positive and `min(...)` for negative when deriving canonical values | helper migration tests |
| Legacy profile snapshot contains four raw threshold fields only | Recoverable | profile contributor compatibility path | Import succeeds with preserved effective behavior | Accept legacy fields and map them through compatibility helper | profile restore tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI continues to expose duplicate threshold semantics | `ARCHITECTURE.md` responsibility matrix; `CODEBASE_CONTEXT_AND_INTENT.md` no scattered undocumented thresholds | review + UI/VM tests | settings VM tests and visual review |
| Effective threshold behavior changes accidentally during simplification | `CODEBASE_CONTEXT_AND_INTENT.md` honest outputs, stability | unit/regression tests | new audio semantics test + mapper regression test |
| Profile capture/restore loses Levo audio semantics | `ARCHITECTURE.md` state contract ownership | unit tests | `AppProfileSettingsSnapshotProviderTest`, restore applier tests |
| New threshold policy drifts between UI and runtime | `CODING_RULES.md` canonical policy owner guidance | code review + unit tests | shared helper coverage and mapper usage/alignment tests |

## 3) Data Flow (Before -> After)

Before:

```text
Settings UI raw sliders
  -> LevoVarioSettingsViewModel raw field setters
  -> LevoVarioPreferencesRepository raw persisted fields
  -> VarioServiceManager
  -> SensorFusionRepository.updateAudioSettings(...)
  -> VarioAudioEngine / VarioFrequencyMapper min/max semantics
```

After:

```text
Settings UI canonical per-side sliders
  -> LevoVarioSettingsViewModel canonical setters
  -> VarioAudioThresholdSemantics helper
  -> LevoVarioPreferencesRepository compatibility write
  -> VarioServiceManager
  -> SensorFusionRepository.updateAudioSettings(...)
  -> VarioAudioEngine / VarioFrequencyMapper same effective semantics
```

## 4) Implementation Phases

### Phase 0: Baseline and Behavior Lock

- Goal:
  - Lock the current effective threshold behavior before any UX simplification.
- Files to change:
  - add focused tests only
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - add a pure audio semantics regression test covering:
    - positive pair uses `max(liftThreshold, deadbandMax)`
    - negative pair uses `min(sinkSilenceThreshold, deadbandMin)`
    - default settings preserve current thresholds
  - add or update profile capture/restore tests for divergent raw pairs
- Exit criteria:
  - current runtime semantics are test-locked
  - no user-visible behavior changes yet

### Phase 1: Canonical Semantics Extraction

- Goal:
  - Introduce one shared pure helper for effective threshold semantics and
    compatibility writes.
- Files to change:
  - `VarioAudioSettings.kt`
  - new `VarioAudioThresholdSemantics.kt`
  - optionally `VarioFrequencyMapper.kt` if helper adoption reduces duplication
- Ownership/file split changes in this phase:
  - threshold interpretation becomes an explicit shared policy owner
- Tests to add/update:
  - helper unit tests
  - mapper regression tests proving no behavior change
- Exit criteria:
  - no UI changes yet
  - all callers can derive canonical per-side values from one owner

### Phase 2: User-Facing Simplification

- Goal:
  - Replace the four threshold controls with one positive control and one
    negative control.
- Files to change:
  - `LevoVarioSettingsViewModel.kt`
  - `LevoVarioSettingsScreen.kt`
  - `VarioAudioComponents.kt`
- Ownership/file split changes in this phase:
  - none; UI still forwards intent only
- Tests to add/update:
  - ViewModel tests for canonical setters
  - if UI tests exist for this screen, update labels/assertions accordingly
- Exit criteria:
  - settings UI no longer exposes redundant threshold pairs
  - changing one canonical slider preserves current effective tone-start
    behavior

### Phase 3: Persistence and Profile Compatibility Hardening

- Goal:
  - Keep imports/restores stable while preparing to remove redundant stored
    semantics.
- Files to change:
  - `LevoVarioPreferencesRepository.kt`
  - `LevoVarioProfileSettingsContributor.kt`
  - relevant profile restore/apply tests
- Ownership/file split changes in this phase:
  - none; repository remains SSOT
- Tests to add/update:
  - restore old snapshots with divergent pairs and verify canonical values keep
    effective behavior
  - repository round-trip tests for compatibility reads/writes
- Exit criteria:
  - legacy stored fields still restore correctly
  - canonical semantics survive profile bundle capture/apply

### Phase 4: Internal Cleanup and Documentation Sync

- Goal:
  - Remove obsolete raw-field semantics only after compatibility evidence is in
    place and at least one safe migration window is defined.
- Files to change:
  - `VarioAudioSettings.kt`
  - `LevoVarioPreferencesRepository.kt`
  - `LevoVarioProfileSettingsContributor.kt`
  - `docs/LEVO/PurpleNeedle.md`
  - `docs/LEVO/levo.md`
  - `docs/ARCHITECTURE/PIPELINE.md` only if runtime wiring changes
- Ownership/file split changes in this phase:
  - optional final collapse from four raw fields to two canonical fields
- Tests to add/update:
  - migration tests for old keys/old profile payloads
  - updated documentation assertions in review checklist
- Exit criteria:
  - one canonical persisted threshold per side if cleanup is approved
  - no compatibility regressions
  - docs match runtime semantics

## 5) Test Plan

- Unit tests:
  - helper semantics tests
  - mapper no-regression tests
  - ViewModel canonical setter tests
  - repository compatibility round-trip tests
- Replay/regression tests:
  - none new beyond deterministic behavior lock, unless settings replay wiring
    is touched directly
- UI/instrumentation tests (if needed):
  - optional screen-level assertion that only one positive and one negative
    threshold control remain
- Degraded/failure-mode tests:
  - divergent legacy pair migration test
  - legacy profile payload import test
- Boundary tests for removed bypasses:
  - ensure UI does not mutate raw redundant fields independently
  - ensure profile contributor does not reinterpret thresholds differently from
    runtime helper
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / policy | Unit tests + regression cases | semantics helper tests + mapper regression tests |
| Persistence / settings / restore | Round-trip / restore / migration tests | repository tests + profile capture/apply tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | ViewModel tests + profile contributor tests |
| UI interaction / wording | UI or screenshot-level review | settings screen review and optional UI assertion |

Required checks for implementation slices:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Legacy users intentionally stored divergent raw pairs and expect a specific hidden combination | Audio start point could shift unexpectedly | Migrate by preserving current effective behavior, not individual raw values | XCPro Team |
| UI simplification lands before behavior is test-locked | Silent regression in tone-start behavior | Phase 0 tests are mandatory before UI edits | XCPro Team |
| Profile import/export compatibility is broken by field cleanup | Restored profiles lose audio semantics | Keep compatibility reads and tests until old payloads are proven migrated | XCPro Team |
| Team stops after UI cleanup and leaves raw semantics undocumented | Future contributors reintroduce confusion | Keep helper as canonical owner and update Levo docs in the same slice | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No for Phases 0-3
- ADR file: none
- Decision summary:
  - This plan does not change module ownership, dependency direction, timebase,
    or concurrency policy.
  - It clarifies semantics and optionally cleans up a shared model after
    compatibility coverage exists.
- Why this belongs in a change plan instead of plan notes:
  - The work is phased, touches persisted settings, and needs explicit migration
    and verification steps, but it does not yet justify a durable architecture
    boundary decision record.

Re-evaluate ADR need if Phase 4 removes raw cross-module fields from the public
`VarioAudioSettings` contract.

## 7) Acceptance Gates

- Settings UI exposes one positive threshold and one negative threshold only
- No duplicate SSOT ownership is introduced
- Effective live and replay threshold behavior remains unchanged for the same
  saved configuration
- Legacy profile snapshots and persisted settings restore without loss of
  effective behavior
- `LevoVarioPreferencesRepository` remains the authoritative owner
- Shared threshold semantics have one canonical owner
- `KNOWN_DEVIATIONS.md` stays unchanged unless an explicit temporary exception is
  approved

## 8) Rollback Plan

- What can be reverted independently:
  - UI simplification can be reverted without reverting tests or helper
    extraction.
  - Helper extraction can stay even if the UI rollout is postponed.
  - Phase 4 field cleanup must remain separately revertible from earlier phases.
- Recovery steps if regression is detected:
  - Revert the latest UI/persistence slice first.
  - Keep Phase 0 regression tests and compatibility tests in place.
  - If a migrated build changes effective thresholds, restore compatibility
    writes immediately and defer raw-field deletion.
