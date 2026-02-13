# UNITS_ExectutionPlanAutonomous.md

## 0) Agent Execution Contract (Read First)

This task runs in autonomous mode. The executing agent owns delivery from baseline to verification without per-step approval.

### 0.1 Authority
- Proceed phase by phase without waiting for confirmation.
- Ask questions only if blocked by missing external decisions or unavailable inputs.
- If ambiguity exists, choose the most repo-consistent option, record the assumption, and continue.

### 0.2 Responsibilities
- Implement the active scope sections fully:
  - Section 1 for ADS-B units/details contract work.
  - Section 8 addendum for ADS-B marker stability/independent-motion work.
- Preserve architecture constraints in:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
  - `docs/ARCHITECTURE/CONTRIBUTING.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- Keep business/domain logic out of UI.
- Use explicit time bases:
  - Monotonic for elapsed/staleness.
  - Replay timestamps for replay simulation.
  - Wall time only for labels/persistence.
- Update docs if wiring/policies change.
- Run required checks and fix failures caused by the change.

### 0.3 Workflow Rules
- Execute ordered phases in Section 2.
- When Section 8 addendum scope is active, execute Section 8.5 phases in order and treat their gates as mandatory.
- No partial production paths or TODO placeholders.
- Keep diffs focused.
- If tests change, state whether behavior changed or parity was preserved.

### 0.4 Definition of Done
- Active-scope phases completed:
  - Section 2 phases for Section 1 scope.
  - Section 8.5 phases for Section 8 scope.
- Active-scope acceptance criteria satisfied:
  - Section 3 for Section 1 scope.
  - Section 8.6 for Section 8 scope.
- Section 4 required checks passed (or blockers documented).
- Section 5 decision log updated for non-trivial choices.

---

## 1) Change Request

### 1.1 Summary (1-3 sentences)
- Ensure ADS-B aircraft details bottom sheet respects `General -> Units` for user-facing numeric fields.
- Lock this behavior with regression tests and explicit contracts so it does not regress.
- Resolve known semantic gaps around vertical rate formatting and distance-origin semantics.

### 1.2 User Value / Use Cases
- As a pilot, I want ADS-B detail values to match my selected units so data is immediately readable and trusted.
- As a pilot, I want distance in ADS-B details to represent ownship-relative distance so tactical interpretation is correct.
- As a maintainer, I want tests that fail on unit-contract regressions so future refactors stay safe.

### 1.3 Scope
- In scope:
  - ADS-B details sheet unit contract for:
    - Altitude
    - Speed
    - Vertical Rate
    - Distance
  - Vertical Rate `ft/min` label and FPM precision contract.
  - Distance-origin semantics contract (ownship vs ADS-B query center) and implementation alignment.
  - Center update cadence/reselection behavior impacting ADS-B detail freshness.
  - Documentation alignment in ADS-B docs and pipeline doc if wiring changes.
  - Regression test coverage for all above.
- Out of scope:
  - ADS-B provider API changes.
  - ADS-B icon artwork or size settings.
  - OGN feature behavior.
  - ADS-B filtering/polling policy changes not required by unit/semantics correctness.

### 1.4 Constraints
- Modules/layers affected:
  - `feature/map` ADS-B, map VM wiring, map UI details sheet
  - `dfcards-library` only if global units model change is required
  - `docs/ADS-b` and `docs/ARCHITECTURE/PIPELINE.md` if wiring changes
- Performance/battery limits:
  - No heavy work on main thread.
  - Center-refresh logic must avoid runaway updates.
- Backward compatibility/migrations:
  - Preserve existing ADS-B tap-selection and metadata enrichment behavior.
  - Prefer ADS-B-local formatting fix before global model changes unless required.
- Compliance/safety rules:
  - Preserve MVVM/UDF/SSOT boundaries.
  - Keep deterministic behavior in tests.
  - No unapproved architecture deviations.

### 1.5 Inputs and Outputs
- Inputs (events/data/sensors/APIs):
  - `UnitsRepository.unitsFlow`
  - ADS-B selected target flow
  - Map camera/GPS center updates
- Outputs (UI/state/storage/logging):
  - ADS-B details sheet text
  - ADS-B target selection details model values
  - Updated docs and tests

### 1.6 Behavior Parity Checklist (for refactors/replacements)
- Preserve:
  - ADS-B metadata fields and section layout behavior.
  - ADS-B target selection/dismiss behavior.
  - Existing overlay rendering behavior.
- Must improve/fix:
  - Vertical Rate unit label/precision consistency with user setting.
  - Distance semantics clarity and correctness.
  - Regression coverage for unit contract.

### 1.7 References
- Plan docs / specs:
  - `docs/ADS-b/ADSB_UNITS_ALIGNMENT_IMPLEMENTATION_PLAN.md`
  - `docs/ADS-b/ADSB.md`
  - `docs/ADS-b/ADSB_Adaptive_Polling_Credit_Budget_Change_Plan.md`
- Related code paths:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsPreferences.kt`
  - `dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsFormatter.kt`

---

## 2) Execution Plan (Agent Owns Execution)

### Phase 0 - Baseline
- Map current behavior end-to-end:
  - Units SSOT -> Map VM -> Map UI -> ADS-B details sheet.
  - ADS-B center update path and selection-store recompute path.
- Confirm invariants and architecture boundaries.
- Add baseline tests first where missing.

Gate:
- No intentional behavior change yet.

### Phase 1 - Core Logic
- Implement minimal fixes proven by failing tests:
  - Vertical Rate `ft/min` suffix contract.
  - Vertical Rate FPM precision contract.
  - Distance-origin correctness contract.
  - Center refresh/reselection behavior if stale data is confirmed.
- Keep changes testable and layer-correct.

Gate:
- Core tests pass.

### Phase 2 - Integration
- Wire changes through use case/viewmodel/repository/UI only as needed.
- If center ownership changes, make responsibilities explicit (query center vs ownship origin).
- Update pipeline doc when wiring changes.

Gate:
- Feature works end to end in debug build.

### Phase 3 - Hardening
- Validate lifecycle, cancellation, and flow cadence behavior.
- Remove dead paths if introduced.
- Finalize docs and test coverage.

Gate:
- Required checks pass.

### Phase 4 - Closed-Loop Regression Audit
- Run strict repeated audit passes:
  1. Code-path pass (data flow and ownership)
  2. Semantics pass (unit label, precision, origin meaning)
  3. Test pass (coverage and failure injection)
  4. Docs parity pass (implementation vs docs)
- If any new finding appears, patch and restart from pass 1.
- Exit only when a full pass yields zero new findings.

Gate:
- "No new findings" pass completed.

### Phase 5 - Delivery Summary
- Final consistency pass and evidence report.
- Produce PR-ready summary and manual verification steps.

Gate:
- Definition of Done met.

---

## 3) Acceptance Criteria

### 3.1 Functional Criteria
- [ ] Given a selected ADS-B target, when `UnitsPreferences` changes, then `Altitude`, `Speed`, `Vertical Rate`, and `Distance` in the details sheet reflect the selected units.
- [ ] Given `VerticalSpeedUnit.FEET_PER_MINUTE`, when details are shown, then `Vertical Rate` displays `ft/min` and uses integer precision.
- [ ] Given ADS-B details distance display, when map query center differs from ownship, then distance semantics remain ownship-referenced per documented contract.
- [ ] Given continuous map/GPS updates, when ADS-B is enabled, then center updates remain fresh and do not stall due to debounce starvation.
- [ ] Given center updates while targets are cached, when center changes, then distance values refresh according to approved contract (immediate reselection or explicitly documented delay).

### 3.2 Edge Cases
- [ ] Empty/missing values show placeholders and do not crash.
- [ ] Lifecycle transitions (map hidden/visible, overlay toggle) preserve correctness.
- [ ] Error/retry behavior remains unchanged for ADS-B provider failures.

### 3.3 Required Test Coverage
- [ ] Unit tests for ADS-B details sheet unit contract.
- [ ] Unit tests for vertical-rate label and precision contract.
- [ ] Unit/integration tests for distance-origin semantics.
- [ ] ViewModel/repository timing tests for center update cadence and reselection behavior.
- [ ] Replay/determinism checks where relevant (no replay behavior regression introduced).

---

## 4) Required Checks (Agent Must Run and Report)

Repo baseline checks:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When relevant and environment allows:
- `./gradlew connectedDebugAndroidTest`

Agent report must include:
- Commands executed.
- Pass/fail per command.
- Fixes applied for failures.
- Any checks not run and why.

---

## 5) Decision Log / ADR Notes

Record non-trivial decisions made during implementation:
- Decision:
  - ADS-B-local formatter/presentation policy vs global units model change.
- Alternatives considered:
  - Global `VerticalSpeedUnit` abbreviation change (`ft` -> `ft/min`).
  - ADS-B-only display policy override.
- Why chosen:
  - Prefer lowest-blast-radius path that satisfies acceptance criteria.
- Risks/impact:
  - Global change may impact card layouts and other screens.
- Follow-up work:
  - If global normalization is later required, schedule separate scoped change and regression sweep.

---

## 6) Required Output Format

At each phase end, report:

## Phase N Summary
- What changed:
- Files touched:
- Tests/checks run:
- Results:
- Next:

At task end, include:
- Final Done checklist.
- PR-ready summary (what/why/how).
- Manual verification steps (2-5 steps).

---

## 7) Execution Result (2026-02-12)

- Implemented:
  - ADS-B details vertical-rate contract (`ft/min` label + integer FPM precision).
  - ADS-B ownship-origin semantics for distance/bearing with query-center filtering preserved.
  - ADS-B center update cadence changed to immediate-first + sampled updates to prevent starvation.
  - Immediate cached-target reselection on center/origin updates.
- Verified with unit tests:
  - `AdsbDetailsFormatterTest`
  - `AdsbTrafficStoreTest`
  - `AdsbTrafficRepositoryTest`

---

## 8) Addendum: ADS-B Marker Stability + Independent Motion (2026-02-13)

This addendum extends the autonomous execution contract with map-marker behavior fixes
requested after field observation of icon disappear/reappear and synchronized jump updates.

### 8.1 Problem Statement
- Observed behavior:
  - ADS-B card/debug count can remain stable (example: 8 displayed),
    while some map icons disappear and reappear shortly after.
  - Aircraft markers update in synchronized jumps instead of independent smooth movement.
  - Additional code-path findings:
    - ADS-B center feed currently merges camera and GPS updates, which can cause center-owner oscillation and radius-boundary churn.
    - Center/origin updates can trigger immediate reselection work on caller thread; this risks UI-thread load spikes.
    - Current center sampling helper has leading-edge behavior only for the very first emission, not for each quiet-period burst.
- Target behavior:
  - Marker visibility is stable for targets that remain in the displayed set.
  - Each aircraft updates independently in UI runtime between network polls.

### 8.2 Mandatory Implementation Order
1. Render map from raw ADS-B targets.
   - Do not gate map positions on metadata enrichment flow latency.
   - Raw means "non-enriched ADS-B target stream from repository/use-case SSOT path", not direct provider/socket data in UI.
   - Metadata enrichment remains for details sheet and metadata-dependent icon classification only.
2. Add center hysteresis/threshold (or split center ownership).
   - Prevent aggressive re-selection churn from camera/GPS jitter near radius boundary.
   - Preferred: split query center (fetch/radius) from display/ownship-origin semantics.
   - Acceptable fallback: keep one center but apply movement/time hysteresis before reselection.
   - Ownership rule: center/radius policy math lives in repository/use-case; ViewModel/coordinator only forwards center intents/cadence.
   - Do not directly merge camera and GPS as competing center owners without arbitration policy.
   - If cadence sampling remains, require leading-edge emission per burst after idle (not only first-ever emission).
   - Keep center/origin update APIs non-blocking for ViewModel callers; reselection work must run on repository dispatcher.
3. Add `isFinite()` guards for track/climb before GeoJSON mapping.
   - Never emit non-finite rotation/climb values to map feature properties.
   - Normalize valid `trackDeg` into [0, 360) before map rotation property assignment.
4. Add UI-only per-aircraft motion smoothing.
   - Keep SSOT unchanged in repository-owned authoritative state.
   - Runtime map layer holds a per-ICAO interpolation cache and updates display pose per frame.
   - Do not write smoothed positions back to repository/domain models.
5. Prevent non-pose churn from forcing full map marker rewrites.
   - Keep map-marker rendering input focused on pose/icon/staleness fields.
   - Distance/age/details metadata changes must not force unnecessary whole-source rewrites unless marker-visible fields changed.

### 8.3 Architecture Constraints (Non-Negotiable)
- Preserve MVVM + UDF + SSOT boundaries.
- Preserve allowed flow: Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI.
- Keep smoothing runtime-local (UI/map runtime), not in repository/domain.
- Keep business geospatial policy (distance/radius/hysteresis) in repository/use-case; never in ViewModel/Composable.
- Keep dependency boundaries strict:
  - ViewModel depends on use-case APIs only (no direct repository/provider injection for this feature).
  - ViewModel must not own MapLibre/map runtime types for this feature; map-typed objects stay in UI/runtime controllers.
  - Use-case wrappers must not expose raw manager/controller escape hatches that bypass use-case methods.
- Respect DI/factory rules: do not construct domain/service collaborators directly in ViewModels/coordinators when DI/factory exists.
- Keep deterministic logic in repository/use-case tests.
- Use injected time sources for repository/use-case hysteresis and staleness logic; no `System.currentTimeMillis` / `SystemClock` in domain/fusion paths.
- Keep repository reselection/update-center work off Main-thread call paths; ViewModel-triggered center updates must not do blocking geospatial recompute inline.
- Respect time-base rules:
  - Live display smoothing uses monotonic timing when available (fallback wall time only for UI/runtime display).
  - Replay display smoothing uses replay/IGC timestamps.
  - No wall-time arithmetic in domain/fusion logic.
- UI smoothing loop must follow Compose rules:
  - Frame-ticker only, scoped to `LaunchedEffect`, and cancelled with composition lifecycle.
- Any new UI flow collection for this feature must use lifecycle-aware APIs (`collectAsStateWithLifecycle` in Compose, `repeatOnLifecycle` elsewhere).
- Any non-UI helper/manager class added for smoothing must not use Compose runtime state primitives (`mutableStateOf`, `derivedStateOf`, `remember`).
- Keep interpolation cache runtime-ephemeral only; it is display state, not SSOT, and must never be persisted.
- Do not use hidden global mutable state for smoothing/hysteresis caches (no singleton/static companion caches for per-target runtime state).

### 8.4 Suggested File Touch List
- Raw vs metadata map-source wiring:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` (expose raw ADS-B target stream through use-case boundary)
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- Center hysteresis / ownership:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt` (primary ownership for threshold/hysteresis/split-center policy)
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` (if use-case API/wrappers need to expose explicit query-center vs display-origin intents)
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` (intent routing only; no geospatial policy math)
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt` (only if cadence/dispatch plumbing is required)
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenFlowExtensions.kt` (if retained, update sampling semantics to leading-edge-per-burst behavior)
- Finite guards:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt` (defensive fallback checks)
- Independent motion smoothing:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - Optional new runtime helper:
    - `feature/map/src/main/java/com/example/xcpro/map/AdsbDisplayMotionSmoother.kt`

### 8.5 Detailed Execution Phases

#### Phase A - Decouple map rendering from metadata latency
- Route overlay marker positions from raw ADS-B target flow via use-case -> ViewModel -> UI path.
- Do not read provider/socket/sensor flows directly in ViewModel or UI.
- Preserve metadata-enriched flow for details sheet content and metadata display.
- Gate: map markers still render correctly when metadata store is cold/slow.

#### Phase B - Stabilize center-driven reselection
- Implement center hysteresis or split query/display center ownership in repository/use-case logic.
- Add anti-jitter threshold (distance and/or time) for center updates.
- Keep ViewModel/coordinator responsibilities to center intent routing/cadence only (no distance/radius policy computation).
- Replace competing center-source merge behavior with explicit center ownership arbitration.
- Ensure center/origin update calls do not perform blocking reselection work on Main call paths.
- Ensure cadence operator behavior is immediate-first per burst after idle.
- Gate: no rapid in/out reselection churn under small camera/GPS jitter.

#### Phase C - Hard numeric sanitization
- Guard `trackDeg` and `climbMps` with `isFinite()` before UI model -> GeoJSON property mapping.
- Normalize finite `trackDeg` to [0, 360) before map property assignment.
- Ensure map expressions never receive NaN/Infinity numeric properties.
- Gate: mapper/overlay handles malformed numeric inputs without dropping render path.

#### Phase D - Independent per-aircraft runtime smoothing
- Maintain previous/current sample per ICAO24 and interpolation timestamps.
- Keep smoothing input keyed by marker-visible pose fields; avoid resetting interpolation cache on metadata/details-only changes.
- On each frame:
  - Interpolate lat/lon per target independently.
  - Interpolate heading safely (angle wrap-aware).
  - Fall back to latest sample when interpolation window is exhausted.
- Do not extrapolate beyond latest known sample (avoid fabricated precision).
- Drop cache entries for removed/stale targets using repository staleness semantics.
- Ensure smoothing runtime loop is lifecycle-scoped and cancelled when map overlay is not active.
- Gate: no whole-fleet synchronized jump effect between poll ticks.

#### Phase E - Verification and documentation sync
- For non-trivial refactor scope, ensure execution is grounded in `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md` sections (ownership, tests, rollback).
- Update `docs/ARCHITECTURE/PIPELINE.md` if data-flow wiring/ownership changes.
- If any temporary rule exception is required, add a time-boxed entry to `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue ID, owner, expiry).
- Keep this addendum and final implementation consistent.
- Run required checks and report evidence.

### 8.6 Acceptance Criteria (Addendum)
- [ ] Given stable `displayedCount`, marker visibility is stable (no disappear/reappear flicker) for non-expired targets.
- [ ] Given moving aircraft with polling cadence >= 10s, markers move independently between polls (no whole-fleet jump only).
- [ ] Given camera/GPS micro-jitter, center updates do not cause repeated radius-boundary churn.
- [ ] Given idle center updates then a new movement burst, first center update is applied immediately (not delayed by prior sampling window).
- [ ] Given non-finite track/climb inputs, map overlay remains stable and does not inject invalid numeric properties.
- [ ] Given no new ADS-B sample for a target, smoothing does not extrapolate beyond last known position/heading.
- [ ] Given metadata/age/distance changes without marker-visible changes (pose/icon/staleness/label), map overlay avoids unnecessary whole-source marker churn.
- [ ] Details sheet metadata behavior remains intact.

### 8.7 Required Test Additions
- Repository and center behavior:
  - Extend `AdsbTrafficRepositoryTest` for center hysteresis/split-center semantics.
  - Extend `AdsbTrafficRepositoryTest` for non-blocking center/origin update behavior on caller thread.
- Mapping sanitization:
  - Extend `AdsbGeoJsonMapperTest` with non-finite `trackDeg`/`climbMps` cases.
  - Extend `AdsbGeoJsonMapperTest` with `trackDeg` normalization range cases.
- ViewModel routing:
  - Extend `MapScreenViewModelTest` to assert map-marker source uses raw ADS-B stream (not metadata-gated stream).
  - Extend `MapScreenViewModelTest` to assert raw stream arrives through use-case boundary (no direct provider/repository bypass).
  - Extend `MapScreenViewModelTest` to assert center updates are forwarded as intents only (no geospatial threshold math in ViewModel).
  - Extend `MapScreenViewModelTest` to assert no map-typed runtime handles are exposed for ADS-B marker smoothing.
- Coordinator/flow cadence:
  - Add `MapScreenTrafficCoordinatorTest` for center-source arbitration (camera vs GPS ownership behavior).
  - Add tests for `sampleWithImmediateFirst` (or replacement) to enforce immediate-first-per-burst semantics after idle.
- Runtime smoother (if extracted helper is added):
  - New unit tests for interpolation, heading wrap, stale/drop behavior.
  - New tests for lifecycle cancellation and time-base handling (live monotonic / replay IGC).
  - New tests asserting no extrapolation past latest sample and no shared-global cache cross-session leakage.
  - New tests ensuring metadata/details-only updates do not reset per-target interpolation state.
  - New lint/enforceRules-safe checks (or unit assertions) that smoothing helper does not depend on Compose runtime state.

### 8.8 Out of Scope (Addendum)
- ADS-B provider API contract changes.
- Polling-credit strategy redesign beyond what is needed for center hysteresis correctness.
- Icon artwork redesign or size-preference behavior.
