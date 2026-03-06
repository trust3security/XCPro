# ADS-B Traffic Store Test Decomposition Plan (Production Grade)

Date: 2026-03-06  
Owner: XCPro map/adsb test slice  
Scope: `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTest.kt`

## Objective

Refactor `AdsbTrafficStoreTest.kt` into a maintainable, production-grade test suite where:

- no Kotlin test file in this slice exceeds `460` lines,
- behavior coverage is preserved or improved,
- deterministic transition and emergency-risk paths remain locked by tests,
- CI and local gates remain green.

## Baseline (Current)

- Current file length: `1148` lines.
- Current test count: `21` tests.
- Current risk: oversized file makes edits unsafe, increases merge conflicts, and hides gaps in emergency/trend behavior.

## Implementation Update (2026-03-06)

Completed:
- Monolithic `AdsbTrafficStoreTest.kt` removed.
- Test suite split into focused files:
  - `AdsbTrafficStoreFilteringAndOrderingTest.kt` (`195` lines)
  - `AdsbTrafficStoreTrendTransitionsTest.kt` (`378` lines)
  - `AdsbTrafficStoreEmergencyGeometryTest.kt` (`283` lines)
  - `AdsbTrafficStoreCirclingEmergencyTest.kt` (`313` lines)
- Required gates passed:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules testDebugUnitTest assembleDebug`

Notes:
- Phase 1 helper extraction was not required to meet the `< 460` objective.
- All existing assertions and behavior coverage were preserved by move/split.

## Non-Negotiable Constraints

- No production behavior change in this refactor unless a failing test proves a defect.
- Keep MVVM/UDF/SSOT contracts intact (tests only in this plan).
- Preserve deterministic scenario coverage.
- Keep all resulting `.kt` files in this test slice `< 460` lines.

## Target End State (File Topology + Line Budgets)

1. `AdsbTrafficStoreFilteringAndOrderingTest.kt` (`<= 320` lines)
- purge expiry
- cap/stale behavior
- reference position distance/bearing
- vertical filter behavior
- stable tie-break ordering

2. `AdsbTrafficStoreTrendTransitionsTest.kt` (`<= 420` lines)
- red/amber/green transition sequence rules
- post-pass de-escalation dwell/fresh-sample requirements
- re-entry into red when closing resumes
- deterministic repeated-sequence assertion

3. `AdsbTrafficStoreEmergencyGeometryTest.kt` (`<= 380` lines)
- geometry emergency applied when truly inbound/closing
- ownship-turning projected miss suppression
- stale closing-age suppression
- provider-age precedence
- ownship-reference unavailable suppression

4. `AdsbTrafficStoreCirclingEmergencyTest.kt` (`<= 360` lines)
- circling-rule red + emergency semantics
- circling disabled fallback semantics
- vertical-cap fallback to geometry emergency
- emergency audio candidate behavior (capped-out and precedence)

5. Support files
- `AdsbTrafficStoreTestSupport.kt` (`<= 280` lines)
- Optional split if needed:
  - `AdsbTrafficStoreFixtures.kt` (`<= 220` lines)
  - `AdsbTrafficStoreScenarioDsl.kt` (`<= 220` lines)

6. Remove/retire
- delete or reduce `AdsbTrafficStoreTest.kt` to a small compatibility wrapper (`<= 40` lines), then remove once all tests are moved.

## Phase Plan

## Phase 0 - Baseline Lock

Changes:
- Do not refactor yet.
- Capture baseline test inventory and expected behaviors by category.
- Record current line counts for each candidate file.

Acceptance:
- Inventory doc/checklist created in this plan file.
- No test behavior changed.

Verification:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.adsb.AdsbTrafficStoreTest"`

## Phase 1 - Shared DSL/Fixtures Extraction

Changes:
- Extract repeated `store.select(...)` call parameters into scenario helpers.
- Introduce explicit helpers for:
  - first sample,
  - closer sample,
  - recovery sample,
  - circling-mode select path.
- Keep helper names semantic (no ambiguous generic builders).

Acceptance:
- Existing tests compile and pass with helper usage.
- No assertion logic weakened.
- Support files remain `< 460` lines.

Verification:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.adsb.AdsbTrafficStore*"`

## Phase 2 - Split Core Filtering/Ordering Tests

Changes:
- Move filtering, stale, cap, vertical, and ordering tests into `AdsbTrafficStoreFilteringAndOrderingTest.kt`.
- Keep fixture-independent tests near pure data/setup.

Acceptance:
- New file `< 460` lines.
- Zero duplicate helper code between files.

Verification:
- targeted class test run for the new file
- then full ADS-B test package run

## Phase 3 - Split Trend Transition State Machine Tests

Changes:
- Move tier transition tests (`red -> amber -> green`, freshness gates, dwell gates, re-escalation) into `AdsbTrafficStoreTrendTransitionsTest.kt`.
- Keep deterministic-sequence test in this file.
- Add compact assertion helpers for tier/reason expectations.

Acceptance:
- Transition semantics unchanged.
- All trend tests deterministic on repeated run.
- File `< 460` lines.

Verification:
- run new trend class twice in same command invocation
- assert deterministic test still passes

## Phase 4 - Split Emergency Geometry + Circling Suites

Changes:
- Move geometry emergency policy tests to `AdsbTrafficStoreEmergencyGeometryTest.kt`.
- Move circling-specific and emergency-audio-candidate tests to `AdsbTrafficStoreCirclingEmergencyTest.kt`.
- Keep explicit naming for geometry vs circling reason paths.

Acceptance:
- Both files `< 460` lines.
- Emergency reason assertions remain explicit (no implicit tier-only checks).

Verification:
- targeted runs for both new classes
- ADS-B package run

## Phase 5 - Final Cleanup + Enforcement Hardening

Changes:
- Remove deprecated monolithic file.
- Normalize helper placement/imports.
- Add or tighten local rule check for this hotspot to prevent regression above `460` lines (if enforce-rules config supports per-file caps).

Acceptance:
- No file in `feature/map/src/test/java/com/example/xcpro/adsb/` that belongs to this suite exceeds `460`.
- Test names remain stable and readable.
- No architectural deviations required.

Verification:
- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Risk Controls

1. Behavior drift risk during move/split
- Control: move-only commits first, then helper consolidation in follow-up commits.

2. Hidden dependency risk between tests
- Control: each new class owns only one behavior domain; helpers remain stateless.

3. Determinism regression risk
- Control: keep and rerun repeated-sequence test in transition suite.

4. File-size regression risk
- Control: explicit per-file budgets above plus enforce-rules hotspot cap in Phase 5.

## Quality Bar (Production-Grade Definition)

- Coverage quality: emergency, trend, and filtering semantics are each isolated and readable.
- Change safety: single-behavior edits touch one test class, not a monolith.
- Determinism: repeated sequence tests remain stable across runs.
- Operability: failures identify behavior domain immediately by class name.

## Scoring Target

Required minimum after Phase 5:

- Architecture cleanliness: `>= 95/100`
- Maintainability/change safety: `>= 95/100`
- Test confidence on risky paths: `>= 95/100`
- Overall ADS-B traffic-store test slice quality: `>= 95/100`
- Release readiness (test slice): `>= 95/100`

## Implementation Order Recommendation

1. Phase 0 + Phase 1 in one PR (no behavior change).
2. Phase 2 and Phase 3 as separate PRs (easier review).
3. Phase 4 + Phase 5 together (final hardening and guardrails).
