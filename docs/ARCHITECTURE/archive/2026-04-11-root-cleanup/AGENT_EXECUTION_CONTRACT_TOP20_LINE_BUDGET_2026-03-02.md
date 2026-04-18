# AGENT_EXECUTION_CONTRACT_TOP20_LINE_BUDGET_2026-03-02.md -- Autonomous Refactor Contract

Date: 2026-03-02
Owner: XCPro Team / Codex
Status: Active
Primary plan reference: `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/CHANGE_PLAN_TOP20_KOTLIN_LINE_BUDGET_500_2026-03-02.md`

Use with:
- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

---

# 0) Agent Execution Contract (Read First)

This document is the task-level autonomous execution contract for refactoring the top-20 largest Kotlin files to compliant maintainability budgets while preserving production behavior.

## 0.1 Authority
- Execute end-to-end without checkpoint confirmations.
- Ask questions only when blocked by missing repository context that cannot be inferred.
- Prefer architecture correctness over minimal diff size.

## 0.2 Responsibilities
- Implement the scoped work in Section 1.
- Preserve MVVM + UDF + SSOT and dependency direction (`UI -> domain -> data`).
- Keep replay deterministic for identical inputs.
- Keep domain/fusion/replay timebase rules intact.
- Keep production Kotlin files ASCII-only.
- Keep each target file at `<= 500` lines at phase closeout.

## 0.3 Workflow Rules
- Execute phases in order.
- No deferred TODO markers in production paths.
- Structural splits first; behavior changes are forbidden unless explicitly documented in the phase slice.
- Any temporary rule violation requires a time-boxed deviation in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue, owner, expiry).

## 0.4 Definition of Done
Work is complete only when:
- All phases are complete with gates satisfied.
- All target files are `<= 500` lines (or documented approved deviation).
- Required verification in Section 4 passes.
- Repass x5 evidence in Section 5 is complete.
- Architecture drift self-audit in Section 6 is complete.
- Quality scorecards in Section 7 are complete and each phase is `>= 94/100`.

## 0.5 Mandatory Read Order
- `AGENTS.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/CHANGE_PLAN_TOP20_KOTLIN_LINE_BUDGET_500_2026-03-02.md`

---

# 1) Change Request (Filled)

## 1.1 Objective
Refactor the top-20 largest Kotlin files into production-grade, architecture-compliant structures with explicit line-budget compliance (`<= 500`), without behavioral regressions.

## 1.2 Scope

Target files and required post-refactor budget:

| # | File | Target |
|---|---|---:|
| 1 | `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryTest.kt` | split, each file <= 450 |
| 2 | `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt` | split, each file <= 450 |
| 3 | `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt` | facade <= 350 |
| 4 | `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` | <= 350 |
| 5 | `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt` | <= 350 |
| 6 | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt` | <= 300 |
| 7 | `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt` | split, each file <= 450 |
| 8 | `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt` | split, each file <= 450 |
| 9 | `feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt` | facade <= 300 |
| 10 | `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt` | <= 350 |
| 11 | `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayController.kt` | <= 350 |
| 12 | `feature/map/src/test/java/com/trust3/xcpro/forecast/ForecastOverlayRepositoryTest.kt` | split, each file <= 450 |
| 13 | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt` | <= 300 |
| 14 | `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt` | <= 320 |
| 15 | `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt` | <= 300 |
| 16 | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | <= 300 |
| 17 | `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt` | <= 350 |
| 18 | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` | keep <= 500 |
| 19 | `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt` | keep <= 500 |
| 20 | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HawkVarioSettingsScreen.kt` | <= 300 |

## 1.3 Non-Goals
- Feature redesign or behavior expansion.
- Re-architecture outside the scoped 20 files.
- UI visual redesign beyond structural decomposition.

## 1.4 Constraints
- Preserve SSOT ownership for ADS-B, OGN, forecast, replay, and metrics states.
- No business logic movement into Composables.
- No new runtime singletons or hidden global mutable state.
- No direct wall/system time API calls in domain/fusion/replay paths.

## 1.5 Time Base Declaration
| Value | Time Base | Why |
|---|---|---|
| ADS-B/OGN freshness logic | Monotonic | live validity windows |
| Replay session progression | Replay | deterministic IGC-based behavior |
| Forecast auto-time UI state | Wall | user-facing time selection |
| Thermal retention policy | Wall + Monotonic split | retention vs sample freshness semantics |

## 1.6 Phase Quality Requirement
- Every phase in Section 2 must score `>= 94/100` before closure.
- Any phase score `< 94` is a hard fail and must be reworked.

---

# 2) Autonomous Execution Plan

## Phase 0 -- Policy and Gate Setup
- Add line-budget rule (`<= 500`) to:
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
- Extend static gate coverage in `scripts/ci/enforce_rules.ps1`.
- Keep policy wording and automation in the same change slice.

Gate:
- Rule docs updated.
- `enforceRules` fails if a scoped file exceeds target budget.
- Phase score >= 94/100.

## Phase 1 -- Repository/Domain Runtime Decomposition
Critical-first order:
1. `OgnTrafficRepository.kt`, `AdsbTrafficRepository.kt`
2. `OgnThermalRepository.kt`, `IgcReplayController.kt`
3. `MapOverlayManager.kt`, `ForecastRasterOverlay.kt`
4. `ForecastOverlayRepository.kt`, `CalculateFlightMetricsUseCase.kt`

Rules:
- one primary runtime file per slice.
- keep public contracts stable unless explicitly documented.
- no behavior edits mixed with structural extraction.

Gate:
- runtime behavior parity preserved by tests.
- determinism-sensitive slices stable across repeated runs.
- phase score >= 94/100.

## Phase 2 -- UI/Screen Decomposition
Targets:
- `MapScreenContent.kt`
- `Settings-df.kt`
- `ForecastOverlayBottomSheet.kt`
- `WeatherSettingsScreen.kt`
- `HawkVarioSettingsScreen.kt`

Rules:
- render + intent only in Composables.
- lifecycle-aware collection preserved.
- callback contracts unchanged.

Gate:
- no UI boundary violations.
- all target files <= 500.
- phase score >= 94/100.

## Phase 3 -- Test Decomposition
Targets:
- `AdsbTrafficRepositoryTest.kt`
- `MapScreenViewModelTest.kt`
- `CalculateFlightMetricsUseCaseTest.kt`
- `OgnThermalRepositoryTest.kt`
- `ForecastOverlayRepositoryTest.kt`

Rules:
- assertion parity required.
- scenario intent must remain explicit.
- shared fixtures must reduce duplication without hiding semantics.

Gate:
- test slices stable across two consecutive runs.
- no new `@Ignore`/`@Disabled`.
- phase score >= 94/100.

## Phase 4 -- Guard Hold for Already-Compliant Files
Targets:
- `AdsbSettingsScreen.kt`
- `CardPreferences.kt`

Rules:
- no behavior changes.
- guard against future growth beyond 500.

Gate:
- static budget checks present.
- files remain <= 500.
- phase score >= 94/100.

## Phase 5 -- Hardening and Closeout
- Documentation sync (`PIPELINE`, architecture docs if needed, plan docs).
- Evidence finalization and quality rescore.
- No unresolved compliance caveats in touched scope.

Gate:
- required commands pass.
- final quality scorecards complete.
- phase score >= 94/100.

---

# 3) Acceptance Criteria

## 3.1 Functional
- Runtime behavior parity preserved for ADS-B/OGN/forecast/replay/metrics.
- Replay determinism preserved for identical inputs.
- No regressions in map overlay ownership and update sequencing.

## 3.2 Architecture
- No SSOT duplication introduced.
- Dependency direction preserved (`UI -> domain -> data`).
- No boundary leaks (UI types in domain, data imports in UI).
- No direct system/wall-time calls in forbidden paths.

## 3.3 Maintainability
- All scoped files meet target line budget.
- Split files are responsibility-focused and test-covered.

## 3.4 Quality
- Every phase score >= 94/100.
- Overall contract closeout score >= 94/100.

---

# 4) Required Verification

Minimum:
- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When relevant:
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

Release/CI parity:
- `./gradlew connectedDebugAndroidTest --no-parallel`

## 4.1 Verification Evidence Table (Fill During Execution)
| Command | Purpose | Result (PASS/FAIL) | Duration | Failures fixed | Notes |
|---|---|---|---|---|---|
| `python scripts/arch_gate.py` | Architecture static gate | | | | |
| `./gradlew enforceRules` | Rule gate + line budget enforcement | | | | |
| `./gradlew testDebugUnitTest` | JVM regression verification | | | | |
| `./gradlew assembleDebug` | Build integrity | | | | |
| `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` | App instrumentation | | | | |
| `./gradlew connectedDebugAndroidTest --no-parallel` | Full instrumentation parity | | | | |

## 4.2 Local Limitation Rule
If any command cannot run, record:
- exact command
- exact blocker
- partial evidence run
- required follow-up and owner
- residual shipping risk

---

# 5) Recurring Repass x5 Protocol (Mandatory)

Run these 5 passes for each phase slice and at final closeout:

1. Structural pass:
   - line counts, function counts, split map completeness.
2. Architecture pass:
   - boundary purity, SSOT ownership, DI direction.
3. Timebase/determinism pass:
   - monotonic/replay/wall separation and concurrency risk checks.
4. Test pass:
   - scenario coverage parity and fixture quality.
5. Quality pass:
   - scorecard update, residual risk accounting, evidence completeness.

Repass deliverable:
- one short summary per pass
- file list touched
- pass/fail outcomes
- remediation actions

---

# 6) Architecture Drift Self-Audit (Must Be Complete)

- [ ] No business logic moved into UI.
- [ ] No UI/data dependency direction violations.
- [ ] No direct time API calls in domain/fusion/replay paths.
- [ ] No new hidden global mutable state.
- [ ] No raw manager/controller escape hatches added.
- [ ] Replay determinism preserved.
- [ ] No unresolved rule violations or undocumented deviations.

---

# 7) Quality Scorecards (Each Phase Must Be >=94/100)

## 7.1 Phase 0
| Dimension | Target |
|---|---:|
| Rule clarity + docs sync | >= 94 |
| Gate enforceability | >= 94 |
| Evidence quality | >= 94 |
| Overall | >= 94 |

## 7.2 Phase 1
| Dimension | Target |
|---|---:|
| Architecture cleanliness | >= 94 |
| Determinism/timebase safety | >= 94 |
| Test confidence | >= 94 |
| Maintainability outcome | >= 94 |
| Evidence completeness | >= 94 |
| Overall | >= 94 |

## 7.3 Phase 2
| Dimension | Target |
|---|---:|
| UI boundary purity | >= 94 |
| Lifecycle correctness | >= 94 |
| Regression confidence | >= 94 |
| Maintainability outcome | >= 94 |
| Evidence completeness | >= 94 |
| Overall | >= 94 |

## 7.4 Phase 3
| Dimension | Target |
|---|---:|
| Scenario coverage parity | >= 94 |
| Determinism across runs | >= 94 |
| Fixture/readability quality | >= 94 |
| Maintainability outcome | >= 94 |
| Evidence completeness | >= 94 |
| Overall | >= 94 |

## 7.5 Phase 4
| Dimension | Target |
|---|---:|
| Regression avoidance | >= 94 |
| Gate coverage quality | >= 94 |
| Maintainability outcome | >= 94 |
| Evidence completeness | >= 94 |
| Overall | >= 94 |

## 7.6 Phase 5
| Dimension | Target |
|---|---:|
| Documentation traceability | >= 94 |
| Verification completeness | >= 94 |
| Release readiness confidence | >= 94 |
| Residual risk closure | >= 94 |
| Overall | >= 94 |

---

# 8) Agent Output Format (Mandatory)

At end of each phase:

## Phase N Summary
- What changed:
- Files touched:
- Tests added/updated:
- Verification results:
- Risks and mitigations:
- Scorecard result:

At final completion:
- Done checklist (Sections 0.4 and 6)
- Completed evidence table (Section 4.1)
- Quality scorecards (Section 7)
- PR-ready summary (what/why/how)
- Manual verification steps (2-5 steps)

---

# 9) Autonomous Continuation Rules

- Continue phase-by-phase until all acceptance gates pass.
- If blocked:
  1. document blocker and scope
  2. apply safe partial changes only
  3. mark phase as blocked with required follow-up
- Never force completion claims without evidence table and scorecard closure.


