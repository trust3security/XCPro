# CHANGE_PLAN_RELEASE_GRADE_COMPLIANCE_2026-02-27.md

## Purpose

Bring the codebase to release-grade readiness with explicit architecture compliance,
security hygiene, deterministic behavior guarantees, and repeatable verification gates.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## 0) Metadata

- Title: Release-grade compliance hardening
- Owner: XCPro Team
- Date: 2026-02-27
- Issue/PR: TBD
- Status: In progress

Execution update (2026-02-27):

- Secret exposure remediation remains applied:
  - `docs/OPENSKY/credentials.json` uses placeholders only.
  - `.vscode/settings.json` no longer carries API key material.
- Local secret handling guidance added in:
  - `docs/ARCHITECTURE/LOCAL_SECRETS_SETUP.md`
- ADS-B regression hardening in test suite:
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
  - Added `@Before` reset of ADS-B and OGN DataStore preferences to remove cross-test
    persistence leakage that can cause ADS-B overlay toggle wait timeouts in ordered runs.
- Verification evidence update:
  - Basic build-only gate passed using direct Gradle distribution:
    - `...\\gradle-8.13\\bin\\gradle.bat --no-daemon assembleDebug`
  - Full unit gate and targeted map suite rerun remain pending completion.

## 1) Scope

- Problem statement:
  - Release readiness is not yet defensible due to tracked secret material, incomplete
    full-gate verification evidence, and unresolved security hardening items.
- Why now:
  - Current branch includes broad refactor and feature churn, so a structured release
    hardening pass is required before merge/release.
- In scope:
  - Secret and credential hygiene across tracked files and build wiring.
  - Release-gate command matrix completion and artifact capture.
  - Architecture/rules compliance re-check and documentation sync.
  - Determinism and boundary validation for high-risk slices (map/task/OGN/ADS-B/replay).
- Out of scope:
  - New feature development.
  - UI redesign and non-blocking UX polish.
  - Backend product changes beyond security-enabling interfaces.
- User-visible impact:
  - More stable release behavior, lower security/compliance risk, and clearer supportability.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Runtime release-readiness status | Plan/checklist docs in `docs/ARCHITECTURE` | Markdown artifacts | Ad-hoc unverifiable release claims |
| OGN/ADS-B runtime settings | Existing preferences repositories | `Flow`/`StateFlow` | UI-local persistent mirrors |
| Flight/task/replay authoritative state | Existing repositories/use-cases | `Flow`/`StateFlow` contracts | New manager/UI side mirrors |
| Secret material for local development | developer-local `local.properties` / user gradle properties | local, untracked files | tracked docs/settings files with real values |

### 2.2 Dependency Direction

Dependency flow must remain:

`UI -> domain -> data`

- Modules/files touched:
  - Primarily documentation and build/security wiring.
  - Targeted runtime files only when required to remove release blockers.
- Any boundary risk:
  - Security fixes that bypass use-case/data boundaries.
  - Urgent patches introducing manager escape hatches.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Release-go/no-go evidence | ad-hoc terminal history | versioned release plan/checklist docs | auditable release process | docs + command matrix |
| Secret handling policy | implicit local practice | explicit documented contract + checks | prevent recurring leaks | secret scan gate |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Tracked secret-bearing files (`docs/*`, editor settings) | direct committed secret values | redacted templates + local-only secret sourcing | Phase 1 |
| Any direct UI-to-repository mutations found during audit | ad-hoc bypass | ViewModel/use-case intent path | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Runtime fusion/task/OGN policy timing | Monotonic | deterministic duration math |
| Replay progression and comparisons | Replay | deterministic replay contract |
| Human-readable release timestamps/docs | Wall | reporting only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - Existing ownership preserved; no release hardening change may move heavy work to `Main`.
- Primary cadence/gating sensor:
  - Existing repository and overlay cadence contracts remain authoritative.
- Hot-path latency budget:
  - Preserve existing map/vario/task latency budgets; no regression accepted for release.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (required gate).
- Randomness used: only previously approved seeded/non-authoritative paths.
- Replay/live divergence rules:
  - No new divergence; replay remains isolated from live network side effects.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Secret leakage in tracked files | CONTRIBUTING security policy | secret scan + review | repo scan + CI secret check |
| Architecture drift during release fixes | ARCHITECTURE/CODING_RULES | `enforceRules` + review | `./gradlew enforceRules` |
| Unverified behavior at release | CONTRIBUTING DoD | test/build gates | `testDebugUnitTest`, `assembleDebug` |
| Replay determinism regressions | ARCHITECTURE determinism | targeted regression tests | replay/task/OGN test suites |
| Unauthorized deviation handling | KNOWN_DEVIATIONS contract | review + docs gate | `KNOWN_DEVIATIONS.md` update only with required fields |

## 3) Data Flow (Before -> After)

Before:

`Mixed release confidence (partial evidence) + tracked secret risk -> uncertain release quality`

After:

`Audited security hygiene + complete verification matrix + documented architecture compliance -> defensible release decision`

No ownership changes to production SSOT flow:

`Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI`

## 4) Implementation Phases

### Phase 0 - Baseline and Freeze

- Goal:
  - Capture exact starting state and freeze release criteria.
- Files to change:
  - This plan and release checklist docs.
- Tests to add/update:
  - none.
- Exit criteria:
  - Baseline risk register and command matrix defined.

### Phase 1 - Secrets and Credential Hygiene

- Goal:
  - Eliminate tracked secrets and enforce local-only secret handling.
- Files to change:
  - tracked secret files (replace with sanitized placeholders).
  - docs describing local secret provisioning.
  - optional CI/presubmit secret scan config if present.
- Tests to add/update:
  - secret scan checks or scripted grep guard.
- Exit criteria:
  - no real secrets in tracked files.
  - rotated credentials completed and documented.

### Phase 2 - Build/Config Hardening

- Goal:
  - Ensure build-time secret strategy is explicit and safe for release.
- Files to change:
  - Gradle/build config wiring as needed.
  - release documentation for environment inputs.
- Tests to add/update:
  - build validation for missing required release secrets.
- Exit criteria:
  - release build behavior is deterministic with explicit env requirements.

### Phase 3 - Architecture Compliance Re-pass

- Goal:
  - Re-audit high-risk slices for drift after recent changes.
- Files to change:
  - only drifted files found by audit.
  - `KNOWN_DEVIATIONS.md` only if explicitly required.
- Tests to add/update:
  - targeted unit tests on any fixed drift areas.
- Exit criteria:
  - no unapproved architecture/rule violations.

### Phase 4 - Verification Matrix Execution

- Goal:
  - Execute required release gates and capture evidence.
- Files to change:
  - release readiness checklist with command outcomes.
- Tests to add/update:
  - none unless failures reveal missing coverage.
- Exit criteria:
  - green required gates or documented blockers with owners/ETAs.

### Phase 5 - RC Decision and Sign-off

- Goal:
  - Produce final go/no-go with residual risk and rollback path.
- Files to change:
  - plan status + final checklist sign-off.
- Tests to add/update:
  - optional focused reruns for flaky/high-risk slices.
- Exit criteria:
  - explicit release decision with evidence references.

## 5) Test Plan

- Unit tests:
  - full unit suite with targeted reruns for map/task/OGN/ADS-B/replay.
- Replay/regression tests:
  - deterministic replay path verification on existing test suites.
- UI/instrumentation tests (if needed):
  - smoke checks for map overlays/settings where recent churn occurred.
- Degraded/failure-mode tests:
  - missing network, missing credentials, stale feed, and no-GPS paths.
- Boundary tests for removed bypasses:
  - any direct bypass removals validated by unit tests.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run when relevant (device/emulator available):

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Full multi-module instrumentation for release/CI verification:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Remaining tracked secret artifacts | high security/compliance risk | full repo secret scrub + key rotation | XCPro Team |
| Gradle lock/daemon contention during verification | false-negative gate failures | sequential gate execution and lock cleanup playbook | XCPro Team |
| Late architectural drift discovered near cut | release delay | early phase-3 audit + targeted fixes | XCPro Team |
| Flaky tests in large suite | low confidence in pass/fail signal | retry policy + isolate flaky suites with owners | XCPro Team |
| Emergency fix introduces rule violation | long-term maintainability debt | enforceRules + mandatory review checklist | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains explicit and test-covered on modified paths.
- Replay behavior remains deterministic.
- No real secrets in tracked files.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).
- Required verification matrix executed and recorded.

## 8) Rollback Plan

- What can be reverted independently:
  - Documentation and checklist updates.
  - Secret-scan CI/presubmit additions.
  - Targeted hardening patches by phase.
- Recovery steps if regression is detected:
  1. Revert the smallest failing phase commit set.
  2. Re-run required gate subset to confirm recovery.
  3. Re-open issue with root cause and updated mitigation owner.

## 9) Quality Rescore (Mandatory at Completion)

To be completed at phase-5 close with evidence:

- Architecture cleanliness: __ / 5
- Maintainability/change safety: __ / 5
- Test confidence on risky paths: __ / 5
- Overall map/task slice quality: __ / 5
- Release readiness: __ / 5

Each score must include:

- Evidence (files/checks/tests)
- Remaining risks
- Reason if score is below 4.0
