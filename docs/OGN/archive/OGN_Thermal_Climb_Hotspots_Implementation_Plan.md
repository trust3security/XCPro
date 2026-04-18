# OGN Thermal Climb Hotspots Implementation Plan

## Implementation status (2026-02-18)

- Implemented in code:
  - `OgnThermalRepository` derives and stores thermal hotspots for the app session.
  - `OgnTrafficPreferencesRepository` now persists `showThermalsEnabled`.
  - `MapScreenViewModel` and `MapScreenTrafficCoordinator` wire thermal toggle, selection, and mutual-exclusion with OGN/ADS-B selection.
  - `MapOverlayManager` and `OgnThermalOverlay` render thermal hotspots and support tap hit-testing.
  - Map action buttons include a `TH` toggle for `Show Thermals`.
  - `OgnThermalDetailsSheet` displays thermal start height, max climb, average climb, and max height.
- Verified:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 0) Metadata

- Title: OGN thermal hotspot display and thermal tap details
- Owner: XCPro Team
- Date: 2026-02-18
- Issue/PR: TBD
- Status: Draft for implementation
- Scope type: OGN feature plus map runtime overlay plus map details sheet
- Out of scope in this plan: replay-specific behavior tuning (explicitly ignored for now)

## 1) Confirmed Product Intent

This plan implements the requested behavior exactly:

1. Add a dedicated `Show Thermals` user option (separate from OGN traffic toggle).
2. Detect OGN gliders that are thermalling or sustained climbing.
3. Render thermal hotspots on map, color-coded by snail-trail climb palette.
4. Keep thermal hotspots in memory until app process restart.
5. On thermal tap, open a half-height bottom sheet (same style family as ADS-B details).
6. Thermal sheet fields:
   - thermal start height
   - max climb rate
   - average climb rate from bottom to top
   - max height reached

### 1.1 Scope contract

- Problem statement:
  OGN traffic is visible, but there is no thermal hotspot layer or thermal details interaction that exposes thermal-quality metrics to pilots.
- Why now:
  Pilot workflow depends on shared thermal awareness and best-climb visibility during live OGN traffic usage.
- In scope:
  - Dedicated `Show Thermals` preference and map toggle wiring.
  - OGN-derived thermal hotspot detection and session-lifetime storage.
  - Color-coded hotspot rendering using snail-trail vario palette.
  - Thermal tap selection and half-sheet details.
  - Architecture and pipeline documentation sync.
- Out of scope:
  - Replay behavior redesign.
  - New OGN protocol parsing fields outside current model.
  - Cross-session thermal persistence on disk.
- User-visible impact:
  Users can enable a thermal layer, see color-coded thermal strength, and inspect thermal stats from map tap.

## 2) Architecture Compliance Contract (Strict)

This plan must comply with:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`

Non-negotiables enforced in implementation:

1. Preserve MVVM plus UDF plus SSOT layering.
2. No business logic in composables.
3. No repository/domain policy inside ViewModel.
4. Inject and use monotonic clock in detection logic; no wall-clock time calls in domain logic.
5. No hidden global mutable state.
6. Keep runtime overlay ownership in `MapOverlayManager` (not split into `MapInitializer`).
7. If any deviation is unavoidable, record a time-boxed exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue ID, owner, and expiry.

### 2.1 Dependency direction confirmation

- Required flow:
  `UI -> domain/use-case -> data`
- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/*` (thermal model/repository/detector/preference)
  - `feature/map/src/main/java/com/trust3/xcpro/map/*` (use-case/viewmodel/runtime overlay wiring)
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/*` (tap routing and thermal sheet host)
  - `docs/OGN/*` and `docs/ARCHITECTURE/PIPELINE.md` (docs sync)
- Boundary risks:
  - ViewModel doing detector math.
  - Composables mutating domain state directly.
  - Runtime overlay becoming hotspot SSOT.

## 3) SSOT Ownership and Data Flow

### 3.1 SSOT Ownership

| Data | Authoritative owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| Raw OGN targets | `OgnTrafficRepository` | `StateFlow<List<OgnTrafficTarget>>` | Any UI/cache mirror of target truth |
| OGN overlay enabled preference | `OgnTrafficPreferencesRepository` | `Flow<Boolean>` | ViewModel-local preference storage |
| Show Thermals preference (new) | `OgnTrafficPreferencesRepository` | `Flow<Boolean>` | Composable/local preference authority |
| Derived thermal hotspots (session) | `OgnThermalRepository` (new) | `StateFlow<List<OgnThermalHotspot>>` | Runtime overlay or ViewModel owning hotspot truth |
| Per-target detector state | `OgnThermalRepository` internal | internal only | Any duplicated detector state in ViewModel/UI |
| Selected thermal id (UI selection) | `MapScreenViewModel` | `StateFlow<String?>` | Selection stored in overlay runtime |
| Map runtime thermal layers | `OgnThermalOverlay` via `MapOverlayManager` | runtime only | Persistent/map runtime becoming SSOT |

### 3.2 Directional flow

`OgnTrafficRepository.targets -> OgnThermalRepository -> OgnThermalUseCase -> MapScreenViewModel -> MapScreen bindings -> MapOverlayManager -> OgnThermalOverlay`

`OgnTrafficPreferencesRepository.showThermalsEnabledFlow -> OgnThermalUseCase -> MapScreenViewModel -> MapActionButtons and overlay visibility gating`

### 3.3 Boundary ownership moves

| Responsibility | Old owner | New owner | Validation |
|---|---|---|---|
| Thermal hotspot derivation from OGN targets | Not implemented | `OgnThermalRepository` | Repository/unit tests with fake clock |
| Thermal selection state | Not implemented | `MapScreenViewModel` | ViewModel selection tests |
| Thermal map runtime layers | Not implemented | `MapOverlayManager` | Runtime wiring tests and manual style reload QA |

### 3.4 Bypass removal and prevention

| Potential bypass | Why invalid | Required path |
|---|---|---|
| Composable calls repository/use-case directly | Breaks UDF and VM ownership | UI intent -> ViewModel -> UseCase |
| ViewModel computes thermal heuristics | Breaks domain boundary | `OgnThermalRepository`/detector only |
| Map runtime owns hotspot list | Breaks SSOT | Runtime render only from ViewModel state |

### 3.5 Boundary adapter check

1. New thermal detection logic consumes existing repository outputs and does not add a new network or sensor adapter boundary.
2. Preference persistence remains repository-owned (`OgnTrafficPreferencesRepository` via DataStore), keeping ViewModel/UI free from persistence APIs.
3. If thermal persistence later moves beyond process-memory (for example disk session restore), introduce a domain port plus data adapter before implementation.

### 3.6 Before and after data flow

Before:

`OgnTrafficRepository.targets -> MapScreenViewModel -> OGN map overlay only`

After:

`OgnTrafficRepository.targets -> OgnThermalRepository -> OgnThermalUseCase -> MapScreenViewModel -> thermal overlay and thermal details sheet`

## 4) Time Base and State Machine

### 4.1 Time base

| Value | Time base | Why |
|---|---|---|
| Detector dwell and hysteresis windows | Monotonic | Stable timing independent of wall clock changes |
| Per-target stale/candidate gap handling | Monotonic | Deterministic state transitions |
| Session hotspot retention | Process lifetime memory | Product requirement: persist until app restart |
| UI-only formatted text timestamps (if shown later) | Wall | Presentation only, not policy |

Rules:

1. Detection and hysteresis use injected monotonic clock only.
2. Hotspots are not age-evicted by default; they reset only on app restart.
3. Streaming pause or map hide must not clear hotspot SSOT.
4. Never compare monotonic and wall timestamps in thermal logic.

### 4.2 Threading and cadence

1. Detection and merge logic runs off main thread (`Dispatchers.Default` in repository scope).
2. Preference writes run via DataStore defaults (`IO` under repository implementation).
3. Map rendering and tap handling remain on main/UI runtime.
4. Thermal timeout/finalization also runs from repository-managed housekeeping timers so quiet upstream streams do not stall state transitions.

### 4.3 Replay determinism declaration

1. Replay-specific behavior is out of scope for this increment.
2. This feature must not alter replay pipeline components or replay clocks.
3. No randomness is introduced in detector logic; same input stream and monotonic timeline yields identical hotspot output.

### 4.4 Thermal detector state machine

States:

1. `IDLE`: no active candidate for target.
2. `CANDIDATE`: entry conditions partially met, dwell accumulating.
3. `ACTIVE`: thermal confirmed; hotspot updates in place.
4. `FINALIZED`: thermal segment ended; hotspot remains visible for session lifetime.

Transitions:

1. `IDLE -> CANDIDATE` when minimum sample count plus positive climb threshold are met.
2. `CANDIDATE -> ACTIVE` when entry dwell, minimum peak climb, minimum sample count, and minimum altitude gain checks pass.
3. `CANDIDATE -> IDLE` when conditions drop before dwell completion.
4. `ACTIVE -> FINALIZED` when continuity grace or missing timeout expires.
5. `FINALIZED -> ACTIVE` does not occur for same hotspot id; new segments create new hotspot ids.

## 5) Data Model (Hotspot Metrics Required by UI)

Add file:

- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalModels.kt`

Current implemented model fields:

```kotlin
data class OgnThermalHotspot(
    val id: String,
    val sourceTargetId: String,
    val sourceLabel: String,
    val latitude: Double,
    val longitude: Double,
    val startedAtMonoMs: Long,
    val updatedAtMonoMs: Long,
    val startAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
    val maxAltitudeAtMonoMs: Long?,
    val maxClimbRateMps: Double,
    val averageClimbRateMps: Double?,
    val averageBottomToTopClimbRateMps: Double?,
    val snailColorIndex: Int,
    val state: OgnThermalHotspotState
)
```

Metric definitions:

1. `startAltitudeMeters`: altitude at thermal entry confirmation for primary segment.
2. `maxAltitudeMeters`: highest altitude observed while segment was active.
3. `maxClimbRateMps`: max climb sample in segment.
4. `averageBottomToTopClimbRateMps`: `(maxAltitudeMeters - startAltitudeMeters) / (timeAtMaxAltitude - startTime)`, guarded for invalid duration.
5. `snailColorIndex`: climb-rate-derived color index from OGN snail-trail palette.

## 6) Detection Algorithm

### 6.1 Inputs and filters

1. Input source is only `OgnTrafficRepository.targets`.
2. Entry and continuation decisions are based on parsed `verticalSpeedMps`.
3. Altitude-derived metrics are computed only when altitude samples are finite.
4. Freshness gate: each target sample is processed once by monotonic `lastSeenMillis` ordering.

### 6.2 Current evidence model

1. Current production implementation does not use turn/geometry heuristics.
2. Detection is sustained-climb based with duration/sample/altitude-gain gating.
3. Spatial center is the centroid of accepted target position samples.

### 6.3 Current thresholds

1. Entry climb threshold: `+0.3 m/s`.
2. Continuation strong-climb threshold: `+0.15 m/s`.
3. Minimum confirm duration: `25s`.
4. Minimum confirm sample count: `4`.
5. Minimum confirm peak climb: `+0.5 m/s`.
6. Minimum confirm altitude gain: `35m` (when altitude data exists).
7. Continuity grace: `20s`.
8. Missing timeout: `45s`.

### 6.4 Segment and retention behavior

1. No spatial merge is applied in current implementation.
2. Each target thermal segment emits stable ids (`<targetId>-thermal-<segmentIndex>`).
3. Hotspots are retained for app session lifetime and clear on app restart.

## 7) Map UI and Interaction Design

### 7.1 Show Thermals option

Add preference and wiring:

1. New preference key in `OgnTrafficPreferencesRepository` for `showThermalsEnabled`.
2. Expose flow in `OgnTrafficUseCase`.
3. Expose state plus toggle intent in `MapScreenViewModel`.
4. Add thermal toggle button in map action buttons area.

Visibility rule:

- Thermal overlay visible only when `ognOverlayEnabled && showThermalsEnabled`.
- Detection semantics: while OGN streaming is enabled, thermal detection continues even when `showThermalsEnabled` is false, so hidden thermals can be re-shown without losing session history.
- If OGN streaming is disabled, active trackers are finalized and existing hotspot session state is retained.

### 7.2 Thermal overlay runtime

Add:

- `feature/map/src/main/java/com/trust3/xcpro/map/OgnThermalOverlay.kt`

Layers:

1. Thermal circles (color from snail-trail color index).
2. Optional climb label (best climb) with zoom gating.

Layer order:

1. Above base map and forecast raster.
2. Below OGN and ADS-B traffic icons.

### 7.3 Tap handling and selection priority

Tap order in `MapOverlayStack`:

1. OGN target hit-test.
2. Thermal hotspot hit-test.
3. ADS-B target hit-test.
4. Forecast wind arrow hit-test.

Selection policy:

1. Selecting thermal clears selected OGN and ADS-B targets.
2. Selecting OGN or ADS-B clears selected thermal target.

### 7.4 Thermal half-sheet

Add:

- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalDetailsSheet.kt`

Behavior:

1. Use `ModalBottomSheet`.
2. Configure sheet state to support partially expanded presentation (half-sheet behavior).
3. Match existing details-sheet typography and row style used by ADS-B and OGN details sheets.

Displayed values:

1. Start height.
2. Max climb rate.
3. Average climb rate bottom to top.
4. Max height.

All displayed values must use `UnitsPreferences`.

### 7.5 Lifecycle and collection contract

1. New flows exposed to Compose must be collected with `collectAsStateWithLifecycle`.
2. No composable-local authoritative thermal state is allowed.
3. Selection and visibility intents route through ViewModel only.

## 8) File-Level Phased Plan

## Phase 0 - Baseline and guardrails

- Goal:
  Lock baseline behavior and establish test harness constraints before adding feature logic.
- Files to change:
  - Test scaffolding files only (no production behavior changes).
- Tests to add/update:
  - Baseline assertions for current OGN overlay behavior and no-thermal-layer default behavior.
- Exit criteria:
  - Baseline tests pass and no feature behavior change is introduced.

## Phase 1 - Domain and SSOT

- Goal:
  Implement thermal detection state machine and hotspot SSOT in repository layer.
- Files to change:
  1. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalModels.kt`
  2. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`
  3. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalDetector.kt`
  4. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTurnDetector.kt`
  5. `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`
- Tests to add/update:
  - Detector and repository unit tests with fake monotonic clock.
- Exit criteria:
  - Thermal SSOT exists, uses injected clock, and passes deterministic unit tests.

## Phase 2 - Preferences and ViewModel

- Goal:
  Wire persisted `Show Thermals` preference and thermal selection state through VM/UDF path.
- Files to change:
  1. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  2. `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`
  3. `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
  4. `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenBindings.kt`
  5. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- Tests to add/update:
  - ViewModel tests for toggle, selection, mutual exclusion, and state clearing semantics.
- Exit criteria:
  - Exposed VM state includes:
    1. `thermalHotspots: StateFlow<List<OgnThermalHotspot>>`
    2. `showThermalsEnabled: StateFlow<Boolean>`
    3. `selectedThermalHotspot: StateFlow<OgnThermalHotspot?>`
  - No business logic moved into ViewModel or composables.

## Phase 3 - Overlay runtime and map tap routing

- Goal:
  Render thermal hotspots and support thermal hit-test selection without SSOT drift.
- Files to change:
  1. `feature/map/src/main/java/com/trust3/xcpro/map/OgnThermalOverlay.kt`
  2. `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenState.kt`
  3. `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  4. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
  5. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
- Tests to add/update:
  - Integration-level map runtime wiring tests (where feasible) and ViewModel interaction tests.
- Exit criteria:
  - Overlay lifecycle is owned only by `MapOverlayManager`.
  - Tap routing honors declared priority and selection mutual exclusion.

## Phase 4 - Action button and bottom sheet UI

- Goal:
  Provide user controls and thermal details presentation in ADS-B-style interaction.
- Files to change:
  1. `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtonItems.kt`
  2. `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt`
  3. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  4. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalDetailsSheet.kt`
- Tests to add/update:
  - UI tests or snapshot tests for toggle visibility and sheet fields where harness allows.
- Exit criteria:
  - `Show Thermals` toggle works and persists.
  - Thermal half-sheet opens on hotspot tap with required metrics and unit formatting.

## Phase 5 - Docs sync

- Goal:
  Keep docs aligned with new data flow and UI behavior.
- Files to change:
  1. `docs/OGN/OGN.md`
  2. `docs/OGN/OGN_PROTOCOL_NOTES.md`
  3. `docs/ARCHITECTURE/PIPELINE.md`
- Tests to add/update:
  - N/A (docs phase).
- Exit criteria:
  - Documentation matches implemented runtime wiring and user-facing behavior.
  - Required verification commands are run and recorded.

## 9) Test Plan (Compliance-Critical)

Coverage categories:

- Unit tests:
  Detector/repository/state-machine/merge/timebase and metric correctness.
- Replay/regression tests:
  Confirm no replay pipeline behavior is altered by this feature scope.
- UI/instrumentation tests:
  Thermal toggle visibility, hotspot tap selection, half-sheet field rendering.
- Degraded/failure-mode tests:
  Missing track, missing vertical speed, stale target gaps, noisy climb suppression.
- Boundary tests for removed bypasses:
  Ensure composables do not call repository/use-case directly and selection intents route through ViewModel.

## 9.1 Unit tests

Add:

1. `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalDetectorTest.kt`
2. `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`
3. `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTurnDetectorTest.kt`

Must cover:

1. Entry and exit hysteresis transitions.
2. Missing-track fallback from bearing-derived heading.
3. Start altitude, max altitude, max climb, average bottom-to-top climb calculations.
4. Merge behavior preserving required metrics.
5. Session persistence until restart semantics.
6. Toggle visibility gate (`ognOverlayEnabled && showThermalsEnabled`).
7. Fake clock determinism for time-based transitions.

## 9.2 Integration and UI tests

Extend:

1. `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`

Add UI tests where feasible:

1. Thermal toggle updates visibility state.
2. Thermal tap selects hotspot and opens thermal details sheet.
3. Thermal details fields render correct unit-formatted values.

## 9.3 Manual QA matrix

1. Enable OGN and `Show Thermals`.
2. Confirm hotspot appears only after entry dwell.
3. Confirm hotspot colors align with snail-trail vario semantics.
4. Tap hotspot and verify half-sheet appears.
5. Verify sheet fields: start height, max climb, avg bottom-to-top climb, max height.
6. Disable OGN overlay and verify thermal layer hides but session hotspots remain.
7. Re-enable OGN overlay and verify prior session hotspots reappear.
8. Restart app and verify hotspots are cleared.

## 10) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| False positives from noisy climb input | High | Hysteresis plus turn evidence plus fallback guards | XCPro Team |
| Duplicate thermals in dense gaggles | High | Spatial merge and compaction policy | XCPro Team |
| Memory growth in long sessions | Medium | Soft cap merge-compaction with deterministic merge rules | XCPro Team |
| UI clutter from labels | Medium | Zoom gating and label density cap | XCPro Team |
| Architecture drift into UI layer | High | Keep all detection and policy in repository plus tests | XCPro Team |
| Runtime ownership drift | High | Thermal overlay instantiated only by `MapOverlayManager` | XCPro Team |

## 10.1 Enforcement coverage map

| Risk | Rule reference | Guard type | File/test anchor |
|---|---|---|---|
| Business logic leaks to UI | ARCHITECTURE MVVM/UDF + CODING_RULES UI rules | Unit tests + review + enforceRules | `MapScreenViewModelTest.kt`, `MapScreenContent.kt` |
| Duplicate hotspot SSOT owners | ARCHITECTURE SSOT | Unit tests + review | `OgnThermalRepositoryTest.kt` |
| Non-injected time usage in detector | ARCHITECTURE timebase | Static rule + unit tests | `OgnThermalDetectorTest.kt` |
| Runtime overlay starts owning state | ARCHITECTURE map runtime ownership | Review + integration tests | `MapOverlayManager.kt`, `OgnThermalOverlay.kt` |
| Non-lifecycle-aware flow collection | ARCHITECTURE lifecycle collection | enforceRules + review | `MapScreenBindings.kt`, `MapScreenContent.kt` |

## 11) Acceptance Gates

1. `Show Thermals` option exists and is persisted.
2. Thermal overlay visibility strictly follows `ognOverlayEnabled && showThermalsEnabled`.
3. Hotspot colors use snail-trail climb palette mapping.
4. Hotspots persist for app session and clear only on app restart.
5. Thermal tap opens half-sheet with all required fields.
6. Required thermal metrics are numerically correct and unit-formatted.
7. MVVM plus UDF plus SSOT boundaries remain intact.
8. Required checks pass:
   - `./gradlew enforceRules`
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
   - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (when device/emulator available)
9. Replay pipeline behavior remains unchanged for this scoped feature.
10. `KNOWN_DEVIATIONS.md` remains unchanged unless an explicit approved exception is required.

## 12) Rollback Plan

1. Feature-flag thermal rendering path behind `showThermalsEnabled` default false.
2. If runtime issues appear, disable thermal rendering while preserving repository code for controlled re-enable.
3. If architecture constraints are breached during implementation, stop and either:
   - refactor to restore compliance, or
   - document deviation in `KNOWN_DEVIATIONS.md` with issue ID, owner, expiry before merge.

## 13) Quality Rescore Template (Mandatory at implementation completion)

- Architecture cleanliness: __ / 5
- Maintainability and change safety: __ / 5
- Test confidence on risky paths: __ / 5
- Overall map/task slice quality: __ / 5
- Release readiness (map/task slice): __ / 5

Replay note for this scope:

- Replay determinism safety: unchanged by design; verify via regression checks.

Implementation completion must include:

1. What changed.
2. Tests added and run.
3. Remaining risks.
4. If score is below 4.0, remediation follow-up with owner and date.
