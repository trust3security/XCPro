# UNITS_ExectutionPlanAutonomous.md

## 0) Agent Execution Contract (Read First)

This task runs in autonomous mode. The executing agent owns delivery from baseline to verification without per-step approval.

### 0.1 Authority
- Proceed phase by phase without waiting for confirmation.
- Ask questions only if blocked by missing external decisions or unavailable inputs.
- If ambiguity exists, choose the most repo-consistent option, record the assumption, and continue.

### 0.2 Responsibilities
- Implement Section 1 fully.
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
- No partial production paths or TODO placeholders.
- Keep diffs focused.
- If tests change, state whether behavior changed or parity was preserved.

### 0.4 Definition of Done
- Section 2 phases completed.
- Section 3 acceptance criteria satisfied.
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
