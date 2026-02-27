# CHANGE_PLAN_OGN_DISPLAY_UPDATE_MODE_2026-02-24.md

## Purpose

Add an advanced OGN display-update mode control with three options:

- `Real-time`
- `Balanced`
- `Battery`

The mode must throttle only map rendering cadence for OGN overlays, never repository ingest.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN display update mode (UI-only render cadence)
- Owner: XCPro Team
- Date: 2026-02-24
- Issue/PR: TBD
- Status: Implemented (2026-02-24)

## 1) Scope

- Problem statement:
  OGN ingest can be frequent, but pilots may want lower map redraw cadence for battery/performance while keeping live data intake.
- Why now:
  Add explicit pilot control over display cadence without compromising traffic freshness at repository level.
- In scope:
  - Persist OGN display update mode in OGN preferences SSOT.
  - Expose mode through use-case and ViewModel flow chain.
  - Add 3-stop slider in OGN settings UI.
  - Throttle OGN map overlay rendering only (traffic, thermals, glider trails).
  - Keep ingest/parser/repository updates live and unchanged.
  - Update pipeline and OGN docs.
- Out of scope:
  - APRS socket cadence changes.
  - Parser/repository filter behavior changes.
  - ADS-B cadence changes.
  - Task/replay/navigation logic changes.
- User-visible impact:
  - New OGN settings slider labeled `Display update speed`.
  - Three selectable modes with different visual redraw cadence.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN display mode | `OgnTrafficPreferencesRepository` | `Flow<OgnDisplayUpdateMode>` | UI-local authoritative mode state |
| Active OGN overlay targets/hotspots/trails | Existing repositories | existing `StateFlow<List<...>>` | Secondary filtered/throttled SSOT copies |
| OGN render throttle state | `MapOverlayManager` (UI runtime only) | internal state only | Any repository-side throttle mirrors |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/*`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettings*`
- Boundary risk:
  - Accidentally throttling ingest instead of UI rendering.
  - Accidentally moving policy into Composables.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN display cadence preference | none | `OgnTrafficPreferencesRepository` | Persisted SSOT | preference tests |
| OGN render cadence policy | implicit immediate map renders | `MapOverlayManager` runtime throttle | UI-only pacing | runtime behavior checks |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN render throttling intervals | Monotonic | Stable UI cadence comparisons (`elapsedRealtime`) |
| OGN ingest timing | Existing repository rules | Unchanged by this plan |
| Settings persistence | Wall/UI lifecycle | DataStore lifecycle only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - UI rendering/throttle scheduling: `Main` coroutine scope in map runtime.
  - DataStore persistence/reads: existing repository dispatcher behavior.
- Primary cadence/gating sensor:
  - OGN ingest cadence remains repository-driven (socket feed).
  - New gating applies only to overlay render calls.
- Hot-path latency budget:
  - `Real-time`: immediate render.
  - `Balanced`: cap around `1000 ms`.
  - `Battery`: cap around `3000 ms`.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - This setting is UI-only and does not alter replay or fusion data semantics.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Ingest throttled by mistake | SSOT/pipeline contract | review + docs sync | `MapOverlayManager.kt`, `PIPELINE.md` |
| ViewModel/UI direct persistence bypass | MVVM/UDF rules | review + tests | `OgnSettingsViewModel`, repo tests |
| Mode not persisted/default broken | SSOT correctness | unit tests | `OgnTrafficPreferencesRepositoryTest.kt` |
| VM wiring drift | ViewModel contract | unit tests | `MapScreenViewModelTest.kt` |

## 3) Data Flow (Before -> After)

Before:

`OGN repositories -> MapScreenViewModel -> MapOverlayManager.render(...) immediate every update`

After:

`OGN repositories -> MapScreenViewModel -> MapOverlayManager.update... -> mode-based UI render scheduling`

Note:

`Repository ingest and publish cadence is unchanged.`

## 4) Implementation Phases

### Phase 0 - Contract and docs

- Goal:
  Define UI-only display mode scope and non-goals.
- Files to change:
  - this plan
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/OGN/OGN.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
- Exit criteria:
  - Contracts explicitly state render-throttle-only behavior.

### Phase 1 - Preferences SSOT and model

- Goal:
  Persist selected display mode in OGN preferences.
- Files to change:
  - `OgnDisplayUpdateMode.kt`
  - `OgnTrafficPreferencesRepository.kt`
- Tests:
  - default and persistence coverage in repository tests.
- Exit criteria:
  - Mode survives app restart and has safe default.

### Phase 2 - Use-case/ViewModel/UI wiring

- Goal:
  Surface mode to map runtime and settings UI.
- Files to change:
  - `MapScreenUseCases.kt`
  - `MapScreenViewModel.kt`
  - `MapScreenBindings.kt`
  - `MapScreenRoot.kt`
  - `MapScreenRootEffects.kt`
  - `OgnSettingsUseCase.kt`
  - `OgnSettingsViewModel.kt`
  - `OgnSettingsScreen.kt`
- Tests:
  - VM preference read tests.
- Exit criteria:
  - Slider updates persisted mode and map runtime observes it.

### Phase 3 - Runtime throttling

- Goal:
  Apply mode-based throttling in map overlay runtime only.
- Files to change:
  - `MapOverlayManager.kt`
  - map-ready runtime wiring in `MapScreenScaffoldInputs.kt`
- Tests:
  - existing compile/VM/repository tests; add runtime tests later if needed.
- Exit criteria:
  - OGN overlays redraw at selected cadence.
  - Empty-list updates clear immediately.
  - Mode switches re-render immediately and cancel pending jobs.

### Phase 4 - Hardening and verification

- Goal:
  Catch lifecycle/cleanup edge cases and finalize docs.
- Files to change:
  - runtime cleanup paths if needed
  - docs sync
- Exit criteria:
  - No stale pending jobs on map detach/style recreation.
  - Required checks run/pass where environment permits.

## 5) Test Plan

- Unit tests:
  - `OgnTrafficPreferencesRepositoryTest`:
    - default mode = `REAL_TIME`
    - persisted mode is restored
  - `MapScreenViewModelTest`:
    - default mode state
    - persisted mode read on init
- Replay/regression tests:
  - Existing replay suites unchanged.
- UI/instrumentation tests (if needed):
  - Slider snapping and persisted selection.
- Degraded/failure-mode tests:
  - Unknown stored value falls back to default mode.
- Boundary tests:
  - Ensure repository ingest remains active independent of display mode.

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
| Throttle applied to ingest path | High | Keep logic in `MapOverlayManager` only; docs/tests enforce | XCPro Team |
| Pending render jobs survive map detach | Medium | Cancel pending jobs on map detach/style recreation | XCPro Team |
| Slider writes too frequently | Low | 3 discrete stops, equality guard before write | XCPro Team |
| Docs drift from behavior | Medium | Update pipeline + OGN docs in same change | XCPro Team |

## 7) Acceptance Gates

- No architecture/coding-rule violations introduced.
- Single SSOT owner for display mode remains repository.
- Render-throttle-only behavior preserved (ingest unaffected).
- Mode persisted and observed by map runtime.
- Docs and tests updated for new behavior.

## 8) Rollback Plan

- What can be reverted independently:
  - UI slider and mode flow exposure.
  - Runtime throttle scheduling (fall back to immediate renders).
  - Preference key can remain inert if runtime use is removed.
- Recovery steps if regression is detected:
  1. Force `REAL_TIME` in runtime path.
  2. Keep settings key persisted but unused.
  3. Re-enable throttling after issue fix and targeted regression tests.

## 9) Implementation Update (2026-02-24)

Implemented:

1. Added `OgnDisplayUpdateMode` enum and DataStore persistence.
2. Wired mode through `OgnTrafficUseCase` and `MapScreenViewModel`.
3. Added 3-option OGN settings slider (`Real-time/Balanced/Battery`).
4. Added map-runtime throttling in `MapOverlayManager` for OGN traffic/thermal/trail rendering only.
5. Updated OGN and pipeline docs to reflect behavior and gates.

Known post-implementation hardening focus:

- Ensure pending OGN render jobs are cancelled on map detach cleanup (not only style recreation).
