# Phased Implementation Plan: SCIA 50-Aircraft No-Crash Release

Date
- 2026-02-27

Status
- Draft (codebase-read complete, implementation pending)

Owner
- XCPro Team

Primary goal
- Keep SCIA stable and responsive when users enable SCIA for up to 50 aircraft.
- No crash, no runaway loop behavior, deterministic graceful degradation under load.

Decision (limit vs app-side handling)
- No hard user-facing aircraft-count blocker.
- Keep internal safety caps and make the app degrade deterministically (downsample/priority) when load exceeds budget.
- Optional soft warning is allowed, but not a hard stop.

## 0) Architecture Contract (Pre-Implementation)

SSOT ownership
- SCIA enabled: `OgnTrafficPreferencesRepository.showSciaEnabledFlow`
- OGN overlay enabled: `OgnTrafficPreferencesRepository.enabledFlow`
- Trail segments authority: `OgnGliderTrailRepository.segments`
- Selected SCIA aircraft keys: `OgnTrailSelectionPreferencesRepository.selectedAircraftKeysFlow`
- Runtime map overlays: `MapOverlayManager` and `OgnGliderTrailOverlay` (runtime only, not SSOT)

Dependency direction
- Keep `UI -> domain/use-case -> data`.
- Move SCIA filtering/policy logic out of Compose (`MapScreenBindings.kt:97-109`) into use-case/viewmodel pipeline.

Time base
- Trail retention/pruning remains monotonic via injected `Clock` in repository.
- No wall-time math added in domain/fusion.

Replay determinism
- SCIA logic remains deterministic for same input sequence.
- No randomness.
- Live/replay divergence unchanged (replay still follows replay timeline).

Boundary check
- No new Android/framework dependency in domain/use-case logic.
- DataStore writes remain in repositories.

## 1) Code Findings Driving This Plan

1. UI-path heavy filtering still present:
- `MapScreenBindings.kt:97-109`

2. Key-match helper does per-call set normalization:
- `OgnAddressing.kt:84-85`

3. Toggle path has no in-flight coalescing:
- `MapScreenTrafficCoordinator.kt:247-256`, `:331-353`

4. SCIA-on from OGN-off does sequential writes:
- `MapScreenTrafficCoordinator.kt:254`, `:256`

5. Startup callback duplication can duplicate forced updates:
- `MapScreenSections.kt:221-225`, `:240-244`
- `MapScreenScaffoldInputs.kt:207-210`

6. Large-list O(n) comparisons in hot path:
- `MapOverlayManager.kt:344-345`
- `MapScreenRootEffects.kt:103-104`

7. Hard caps are safety-only today:
- repo cap `24_000` (`OgnGliderTrailRepository.kt:333`)
- render cap `12_000` (`OgnGliderTrailOverlay.kt:149,153`)

## 2) Phased Implementation

### Phase 0 - Baseline and Instrumentation

Goal
- Lock baseline behavior and establish measurable stress criteria for 50-aircraft SCIA.

Changes
- Add debug metrics for:
  - SCIA toggle request-to-visible latency
  - render segment count
  - render scheduling drops/coalesces
  - first-enable render duration
- Add deterministic stress fixture generator for multi-aircraft OGN trails.

Files (expected)
- `feature/map/src/test/...` new SCIA stress policy tests
- optional debug-only counters in `MapOverlayManager` / `OgnGliderTrailOverlay`

Exit criteria
- Baseline metrics captured for current behavior.
- No production behavior change yet.

### Phase 1 - Toggle Path Stability and Immediate UX

Goal
- Remove perceived SCIA toggle "pause" from queued duplicate writes.

Changes
- Add in-flight mutation serialization/coalescing for OGN/SCIA toggle actions.
- Add transient pending UI state (authoritative state remains DataStore flow).
- Add single-call mutation API for "enable OGN + enable SCIA" in one repository transaction.

Files (expected)
- `MapScreenTrafficCoordinator.kt`
- `MapScreenViewModel.kt`
- `MapScreenContent.kt`
- `MapScreenUseCases.kt`
- `OgnTrafficPreferencesRepository.kt`

Exit criteria
- Rapid taps do not queue conflicting writes.
- SCIA-on from OGN-off commits atomically.
- UI acknowledges tap immediately.

### Phase 2 - Move SCIA Filtering Out Of Compose

Goal
- Remove heavy SCIA segment filtering from composition path.

Changes
- Create use-case/viewmodel-owned filtered SCIA flow that combines:
  - raw trail segments
  - selected aircraft keys
- Keep Compose render-only.
- Remove duplicate selection-flow collection from both `MapScreenBindings` and `MapScreenContent`.
- Add optimized key-match helper that accepts pre-normalized selection sets (no per-segment remap).

Files (expected)
- `MapScreenBindings.kt`
- `MapScreenViewModel.kt`
- `MapScreenUseCases.kt`
- `OgnAddressing.kt`
- `OgnTrailSelectionUseCase.kt` (or new dedicated SCIA filter use-case)

Exit criteria
- No SCIA large-list filtering in Compose.
- No per-segment selected-set re-normalization.
- Behavioral parity for selected-aircraft trail visibility.

### Phase 3 - Startup and Render Push De-dup

Goal
- Eliminate duplicate heavy SCIA pushes during map startup.

Changes
- Make map-ready callback idempotent per map generation.
- Avoid double callback work path (before/after initialize) or gate heavy work to post-init only.
- Remove redundant forced SCIA trail push overlap between map-ready path and root overlay effects.

Files (expected)
- `MapScreenSections.kt`
- `MapScreenScaffoldInputs.kt`
- `MapScreenRootEffects.kt`
- `MapOverlayManager.kt`

Exit criteria
- One effective SCIA startup push per map generation.
- No redundant forced render pass on cold start.

### Phase 4 - Capacity Policy For 50 Aircraft (Deterministic Degradation)

Goal
- Guarantee non-crash behavior under 50 selected aircraft by internal policy, not user hard stop.

Changes
- Introduce explicit SCIA capacity policy object with constants and tests.
- Keep hard safety caps, add fairness/downsample policy when load exceeds render budget.
- Deterministic policy shape:
  - preserve newest segment for each selected aircraft
  - apply per-aircraft downsample factor for historical segments
  - enforce global render budget after fairness pass
- Optional soft warning when load causes aggressive downsample.

Reference sizing
- `estimatedSegments = selectedAircraft * updatesPerSecond * 1200` (20 minutes)
- `downsampleFactor = ceil(estimatedSegments / renderBudget)` when above budget

Files (expected)
- new policy class under `feature/map/src/main/java/com/trust3/xcpro/ogn/` or `map/`
- `OgnGliderTrailOverlay.kt` and/or `MapOverlayManager.kt`
- optional `OgnGliderTrailRepository.kt` integration if policy is repository-side

Exit criteria
- 50-aircraft stress does not crash.
- Degradation is deterministic and test-covered.

### Phase 5 - Off-Main SCIA Feature Build

Goal
- Prevent main-thread stalls from large GeoJSON feature creation.

Changes
- Split SCIA render into:
  - background build phase (features)
  - map-thread apply phase (`setGeoJson`)
- Add stale-job cancellation and generation token guard.

Files (expected)
- `OgnGliderTrailOverlay.kt`
- `MapOverlayManager.kt`

Exit criteria
- Main/UI path no longer builds large SCIA feature sets synchronously.
- No stale apply after style/map generation change.

### Phase 6 - Hardening, Tests, and Release Gates

Goal
- Prove release-grade behavior and no ad-hoc architecture drift.

Tests to add/extend
- Toggle coalescing and atomic SCIA+OGN mutation tests
- Non-UI filtering and key-normalization tests
- Startup de-dup map-ready tests
- Capacity fairness/downsample policy tests (including 50-aircraft stress fixtures)
- Overlay render scheduling and stale-job cancellation tests

Required commands
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Optional when environment is available
- `./gradlew connectedDebugAndroidTest --no-parallel`

Exit criteria
- All required gates pass.
- SCIA stress scenario shows stable behavior and acceptable responsiveness.

## 3) Complexity Control (No Ad-Hoc)

Rules for this implementation
- One explicit SCIA policy owner (single policy class), no scattered magic thresholds.
- No business logic in Compose.
- No new global mutable singleton state.
- Every new threshold documented in code + tests + SCIA docs.
- If any temporary deviation is required, record it in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue, owner, expiry.

## 4) Release-Grade Scoring Contract

Current SCIA implementation (post code pass)
- Architecture cleanliness: 8.1/10
- Maintainability/change safety: 7.3/10
- Release readiness: 6.9/10

Target after this plan
- Architecture cleanliness: >= 9.2/10
- Maintainability/change safety: >= 9.0/10
- Release readiness: >= 9.0/10

"Genius dev/programmer" expected score after full completion
- 9.0 to 9.4/10, contingent on passing gates and stress evidence.

