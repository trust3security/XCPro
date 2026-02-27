# Genius_Level_Codebase_Refactor_Plan_2026-02-24.md

## Purpose

Reach architecture-clean, release-ready, "genius-level" quality by closing
current blockers (lint/build hygiene) and reducing change risk in map/task and
adjacent hotspots.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/AGENT.md`

Use with:

- `docs/refactor/Agent-Execution-Contract-GeniusLevel-2026-02-24.md`

## 0) Metadata

- Title: Genius-Level Codebase Refactor Campaign
- Owner: XCPro Team
- Date: 2026-02-24
- Issue/PR: TBD
- Status: Active (campaign-level), map/task slice tracked by newer rebaseline

## 0B) Rebaseline Link (2026-02-26)

For map/task implementation order and latest verified blockers, use:

- `docs/refactor/Map_Task_Refactor_Plan_2026-02-25.md`
- `docs/refactor/Agent-Execution-Contract-MapTask-2026-02-25.md`

Current map/task blockers reflected there:

- default `enforceRules` configuration-cache failure from config-time shell probing in `build.gradle.kts`,
- brittle source-text policy tests,
- timezone determinism hardening work,
- flaky test wait patterns and repo hygiene cleanup.

## 0A) Baseline Evidence (2026-02-24)

Commands run:

- `./gradlew --no-configuration-cache enforceRules` -> PASS
- `./gradlew --no-configuration-cache testDebugUnitTest` -> PASS
- `./gradlew --no-configuration-cache assembleDebug` -> PASS
- `./gradlew --no-configuration-cache lintDebug` -> FAIL

Current measured risks:

- Lint blocking errors: `3`
- Lint warnings: `655`
- Lint hints: `38`
- First blocking file:
  - `feature/map/src/main/java/com/example/xcpro/adsb/data/AndroidAdsbNetworkAvailabilityAdapter.kt`
- Manifest mismatch:
  - `app/src/main/AndroidManifest.xml` currently has `INTERNET` only; missing
    `ACCESS_NETWORK_STATE` for connectivity APIs used in ADS-B network adapter.
- Build hygiene issue:
  - `build.gradle.kts` probes shell commands during configuration; this breaks
    configuration cache for default `enforceRules` invocation.
- Maintainability hotspots:
  - Production Kotlin files `> 350 LOC`: `28`
  - Production Kotlin files `> 500 LOC`: `10`
  - Concentration: primarily `feature/map` with additional `dfcards-library` hotspots.

## 1) Scope

- Problem statement:
  - The codebase has strong architecture guardrails and tests, but still has
    release blockers (lint errors), high warning debt, and large-file blast radius.
- Why now:
  - Blocking defects and maintenance drag are measurable and currently slowing
    safe iteration.
- In scope:
  - Lint blocker fixes and lint debt reduction.
  - Build config-cache compatibility for rule gates.
  - Hotspot decomposition of highest-risk large files.
  - Determinism and failure-mode test expansion for risky paths.
  - Guardrail updates and documentation sync.
- Out of scope:
  - New product features unrelated to quality/remediation.
  - UI redesign/theme work.
  - Cross-module rewrites without measurable risk reduction.
- User-visible impact:
  - Fewer regressions, faster bug-fix velocity, safer releases.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B network availability | `AdsbNetworkAvailabilityTracker` | `StateFlow<Boolean>` via domain port | UI/composable-local online state mirrors |
| Task render state | Task repository/use case stack | `TaskRenderSnapshot` | direct map runtime copies as authority |
| Replay timeline state | Replay repositories/use cases | replay-specific state flows | wall-time derived replay state |
| Weather/forecast settings | settings repositories/use cases | immutable UI state via VM | settings written directly from UI |
| Map overlay/runtime mode state | map view model + use cases | immutable VM `StateFlow` | hidden mutable globals |

### 2.2 Dependency Direction

Flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map`
  - `dfcards-library`
  - `app` (manifest and top-level wiring only when needed)
  - `build.gradle.kts`
  - `scripts/ci/enforce_rules.ps1` (if guardrails are extended)
  - `docs/ARCHITECTURE/*` and `docs/refactor/*`
- Boundary risk:
  - Lint and hotspot fixes can accidentally push policy back into UI or re-open
    manager escape hatches.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| ADS-B online-state permission safety | implicit platform assumption in adapter | explicit app manifest + safe adapter boundary | remove runtime crash risk and lint errors | lint + unit tests |
| Config-cache shell probing | config-time helper in build script | cache-safe task execution branch | stable CI/local verification | `enforceRules` with configuration cache |
| Formatting policy in AAT/map warnings | scattered inline string formatting | shared formatter helpers/use-case-level helpers | reduce locale bugs and warning spam | lint + unit tests |
| Large orchestration files | mixed concerns in single files | focused collaborators with clear ownership | reduce blast radius | line budgets + parity tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| map/task composables with direct manager usage (where found) | direct runtime manager mutation/query | VM intent APIs only | 3-4 |
| any non-owner render sync path (where found) | direct render-router calls | `TaskRenderSyncCoordinator` owner path | 3-4 |
| duplicated online-state checks in UI | ad-hoc connectivity checks | `AdsbNetworkAvailabilityPort` flow | 1-2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| live sensor freshness and cadence checks | Monotonic | stable duration math |
| replay sequencing/timestamps | Replay | deterministic repeatability |
| display-only timestamps and labels | Wall | human-readable UI only |

Explicitly forbidden:

- Monotonic vs wall comparisons
- Replay vs wall comparisons

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `Main`: Compose and MapLibre rendering.
  - `Default`: domain math/policy.
  - `IO`: file/network/persistence.
- Primary cadence/gating sensor:
  - sensor/replay event cadence, not UI frame cadence for domain policy.
- Hot-path latency budget:
  - task/map mutation to render state update target: <= 100 ms.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (required).
- Randomness used: No (unless explicitly seeded and documented).
- Replay/live divergence rules:
  - replay uses replay time only; live uses monotonic for validity windows.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| missing required permissions for connectivity APIs | ARCHITECTURE 10, CODING_RULES 12 | lint + manifest check | `app/src/main/AndroidManifest.xml`, ADS-B adapter tests |
| config-cache regression in verification tasks | CONTRIBUTING 3, 4 | gradle task verification | `build.gradle.kts` + `enforceRules` runs |
| locale-sensitive formatting bugs | CODING_RULES 1, 12 | lint (`DefaultLocale`) + unit tests | AAT/map formatting helpers |
| regression via mega files | CODING_RULES 15A | enforceRules line budgets + review | `scripts/ci/enforce_rules.ps1` |
| replay behavior drift | ARCHITECTURE 4A | deterministic replay tests | replay test suites |
| UI/domain boundary leakage | ARCHITECTURE 1, 2, 8 | enforceRules + tests | task/map UI and VM tests |

## 3) Data Flow (Before -> After)

Before (risk):

```
Mixed ownership in large files -> scattered side effects -> hard-to-predict regressions
```

After (target):

```
Source adapters -> Repository (SSOT) -> UseCase -> ViewModel -> UI
                                    -> single-owner render/effect coordinators
```

## 4) Implementation Phases

### Phase 0: Baseline Lock

- Goal:
  - Freeze metrics and risk inventory.
- Files to change:
  - this plan + companion execution contract.
- Tests to add/update:
  - none.
- Exit criteria:
  - baseline evidence committed and accepted.

### Phase 1: Release Blockers

- Goal:
  - Remove hard failures from lint/build verification.
- Files to change:
  - `app/src/main/AndroidManifest.xml`
  - `feature/map/src/main/java/com/example/xcpro/adsb/data/AndroidAdsbNetworkAvailabilityAdapter.kt`
  - `build.gradle.kts`
- Tests to add/update:
  - ADS-B network availability adapter tests for failure-safe behavior.
- Exit criteria:
  - `lintDebug` has `0` errors.
  - `./gradlew enforceRules` passes without forcing `--no-configuration-cache`.

### Phase 2: Warning Debt Burn-Down (High ROI)

- Goal:
  - Remove high-volume warning classes with low behavior risk.
- Files to change:
  - locale-format heavy AAT/map files.
  - logging callsites (`println`/`Log` where policy requires wrapper).
- Tests to add/update:
  - formatter tests and no-regression string output tests where needed.
- Exit criteria:
  - warning count reduced by >= 30 percent from baseline.

### Phase 3: Hotspot Decomposition Wave A

- Goal:
  - Split highest risk files (`> 650 LOC`) into focused collaborators.
- Initial targets:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
- Tests to add/update:
  - parity tests per extracted collaborator boundary.
- Exit criteria:
  - no targeted file remains above 500 LOC.

### Phase 4: Hotspot Decomposition Wave B

- Goal:
  - Reduce remaining large-file concentration in map/dfcards.
- Files to change:
  - next largest files above 400 LOC in `feature/map` and `dfcards-library`.
- Tests to add/update:
  - behavior-parity tests on each split seam.
- Exit criteria:
  - production files `> 500 LOC` reduced to `0`.
  - production files `> 350 LOC` reduced to `<= 15`.

### Phase 5: Determinism and Failure-Mode Hardening

- Goal:
  - expand risky-path tests and close silent regression vectors.
- Files to change:
  - replay/domain tests
  - ADS-B network and map/task failure-path tests
- Tests to add/update:
  - deterministic replay trace parity tests
  - offline/permission/fallback behavior tests
  - map/task sync ordering tests
- Exit criteria:
  - repeated-run deterministic parity on selected replay suites.

### Phase 6: Guardrails, Docs, and Rescore

- Goal:
  - lock gains and prevent drift.
- Files to change:
  - `scripts/ci/enforce_rules.ps1` (if needed)
  - architecture/pipeline docs if ownership flows changed
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only if approved exception is needed
- Tests to add/update:
  - guardrail coverage for new rules.
- Exit criteria:
  - all required checks pass.
  - final quality rescore documented with evidence.

## 5) Test Plan

- Unit tests:
  - collaborator parity tests for each decomposition.
  - ADS-B network adapter permission/fallback behavior.
  - formatter determinism tests where locale handling changed.
- Replay/regression tests:
  - repeated-run parity for replay state transitions.
- UI/instrumentation tests:
  - map/task critical flow checks when wiring changes.
- Degraded/failure-mode tests:
  - network unavailable, permission absent, map unavailable, style reload.
- Boundary tests:
  - enforceRules scans for disallowed bypasses after refactor.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| behavior drift during large-file splits | High | split behind tests; keep narrow commits | XCPro Team |
| warning cleanup introduces user-visible text changes | Medium | add output-parity tests for formatting paths | XCPro Team |
| config-cache fix breaks local shell compatibility | Medium | test on Windows shell matrix (`pwsh`/`powershell`) | XCPro Team |
| throughput drops from over-large refactor batches | Medium | strict phase slicing and short-lived branches | XCPro Team |

## 7) Acceptance Gates

- `lintDebug` passes with `0` errors.
- warning count reduced to `<= 200` (or explicit approved exception).
- `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, and
  `./gradlew assembleDebug` pass without `--no-configuration-cache`.
- production Kotlin files `> 500 LOC` equals `0`.
- production Kotlin files `> 350 LOC` equals `<= 15`.
- no architecture rule violations introduced.
- no new deviation entries unless explicitly approved with issue/owner/expiry.
- final quality rescore:
  - Architecture cleanliness >= 4.5/5
  - Maintainability/change safety >= 4.5/5
  - Test confidence on risky paths >= 4.5/5
  - Overall map/task slice quality >= 4.5/5
  - Release readiness >= 4.5/5

## 8) Rollback Plan

- Revertability:
  - each phase must be independently revertable (separate commits/PRs).
- Recovery steps:
  1. Revert only the failing phase commit range.
  2. Keep passing guardrail/test additions.
  3. Re-run required checks.
  4. Re-scope and reattempt with smaller slice.
