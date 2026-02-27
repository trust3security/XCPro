# CHANGE_PLAN_APP_STARTUP_WORKMANAGER_INIT_2026-02-25.md

## Purpose

Stabilize app startup and release build viability by fixing the WorkManager
initialization contract violation that can cause startup failure/conflict paths.

This plan is "Genius-level contract" compliant and follows:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/AGENT.md`
5. `docs/refactor/Agent-Execution-Contract-GeniusLevel-2026-02-24.md`

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## 0) Metadata

- Title: App Startup Hardening - WorkManager Initializer Contract Fix
- Owner: XCPro Team
- Date: 2026-02-25
- Issue/PR: ARCH-20260225-APPSTARTUP-WM-INIT
- Status: Draft
- Campaign alignment: `docs/refactor/Genius_Level_Codebase_Refactor_Plan_2026-02-24.md` (Phase 1 blocker removal)

## 0A) Genius Startup Gate Declarations

- Targeted phase: Phase 1 (Release blockers) with focused startup hardening.
- SSOT ownership affected:
  - WorkManager runtime configuration remains in `XCProApplication` as the single owner.
  - Manifest startup provider metadata remains framework-owned, app declares explicit overrides.
- Dependency direction check:
  - `UI -> domain -> data` unchanged.
  - No domain/business logic moves.
- Time base declaration:
  - No new time-dependent domain logic introduced.
  - Startup wiring only.

### Baseline Evidence (captured 2026-02-25)

- `./gradlew.bat assembleDebug --no-configuration-cache` -> PASS
- `./gradlew.bat :app:assembleRelease --no-configuration-cache` -> FAIL at lint:
  - `RemoveWorkManagerInitializer` in `app/src/main/AndroidManifest.xml`
- `./gradlew.bat :app:assembleRelease -x lintVitalRelease --no-configuration-cache` -> PASS
- `./gradlew.bat testDebugUnitTest --no-configuration-cache` -> BLOCKED by existing unrelated test/worktree state
  and intermittent Windows file lock in `feature/map/build/test-results/.../output.bin`

## 1) Scope

- Problem statement:
  - `XCProApplication` implements `androidx.work.Configuration.Provider`, but manifest merge keeps
    `androidx.work.WorkManagerInitializer` active via `androidx.startup.InitializationProvider`.
  - This violates WorkManager on-demand initialization contract and currently blocks release lint/build.
- Why now:
  - This is a release-blocking defect and can create startup initialization conflicts.
- In scope:
  - Manifest-level removal of `androidx.work.WorkManagerInitializer` only.
  - Validation of merged manifests for debug/release variants.
  - Regression tests/checks for startup and build gates.
  - Documentation update for this startup invariant.
- Out of scope:
  - WorkManager feature redesign.
  - Background task architecture refactor.
  - Unrelated map/task runtime behavior changes.
- User-visible impact:
  - No UI behavior changes expected.
  - Improved startup safety and release reliability.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data / Contract | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| WorkManager configuration | `XCProApplication` (`Configuration.Provider`) | `workManagerConfiguration` | Alternate app-level WorkManager config owners |
| Startup initializer suppression policy | App manifest (`<provider ... tools:node/replace/remove>` metadata control) | merged manifest output | Parallel hidden initializer declarations |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain -> data`

- Modules/files touched:
  - `app/src/main/AndroidManifest.xml`
  - optional: startup wiring tests/docs in `app` and `docs/ARCHITECTURE`
- Boundary risk:
  - Over-removing startup metadata could disable unrelated AndroidX startup components.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| WorkManager auto-init trigger | AndroidX Startup merged metadata | `XCProApplication` only (on-demand contract) | Prevent double-init contract violation | merged manifest inspection + lint + release assemble |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Merged manifest startup provider metadata | Implicit WorkManager auto-init alongside explicit `Configuration.Provider` | Explicitly remove only `androidx.work.WorkManagerInitializer` metadata | Phase 1 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Startup initializer selection | N/A | Configuration wiring only; no clock math |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - No new coroutine/dispatcher behavior introduced.
- Primary cadence/gating sensor:
  - N/A (startup metadata and app initialization path only).
- Hot-path latency budget:
  - Startup path should remain unchanged or improved.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged; replay pipeline untouched)
- Randomness used: No
- Replay/live divergence rules:
  - No replay path changes.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| WorkManager init contract violation reintroduced | CONTRIBUTING + release safety gates | lint + release assemble | `app/src/main/AndroidManifest.xml`, `:app:assembleRelease` |
| Removing unrelated startup initializers by mistake | architecture change safety | review + merged-manifest assertion | merged manifest inspection (debug/release) |
| Startup regression in app process | ARCHITECTURE error handling + lifecycle host boundaries | smoke run + instrumentation when available | app startup smoke and connected test when device available |

## 3) Data Flow (Before -> After)

Before:

`AndroidX Startup InitializationProvider -> WorkManagerInitializer`
plus
`XCProApplication(Configuration.Provider) -> custom workerFactory`

After:

`AndroidX Startup InitializationProvider -> (WorkManagerInitializer removed)`
and only
`XCProApplication(Configuration.Provider) -> custom workerFactory`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - Confirm current failure signature and preserve evidence.
- Files to change:
  - none (evidence/log capture only).
- Tests/checks:
  - `assembleDebug`
  - `:app:assembleRelease` (expect current failure)
  - merged manifest inspection
- Exit criteria:
  - Reproducible `RemoveWorkManagerInitializer` error captured.

### Phase 1 - Manifest contract fix

- Goal:
  - Remove only `androidx.work.WorkManagerInitializer` from startup provider metadata.
- Files to change:
  - `app/src/main/AndroidManifest.xml`
- Tests/checks:
  - merge manifest validation (debug + release)
  - `:app:assembleRelease`
- Exit criteria:
  - Lint no longer reports `RemoveWorkManagerInitializer`.
  - `:app:assembleRelease` passes (or fails only for unrelated pre-existing issues).

### Phase 2 - Safety hardening

- Goal:
  - Ensure no collateral removal of other startup initializers.
- Files to change:
  - optional startup verification test/docs
- Tests/checks:
  - inspect merged manifest for retained non-WorkManager initializers (emoji/lifecycle/profileinstaller as intended)
  - run `assembleDebug`
- Exit criteria:
  - Non-target startup metadata remains present and startup path is healthy.

### Phase 3 - Verification and closeout

- Goal:
  - Run required gates and publish completion summary with residual risks.
- Files to change:
  - docs updates if needed (`PIPELINE.md` not expected to change; document if startup wiring note is added)
- Tests/checks:
  - required:
    - `./gradlew enforceRules`
    - `./gradlew testDebugUnitTest`
    - `./gradlew assembleDebug`
  - release confidence:
    - `./gradlew :app:assembleRelease`
- Exit criteria:
  - Startup blocker closed and verified.
  - Any blocked verification is explicitly reported with fallback evidence.

## 5) Test Plan

- Unit tests:
  - Optional: add manifest/startup contract assertion test if existing infrastructure supports it.
- Replay/regression tests:
  - Not applicable; no replay logic change.
- UI/instrumentation tests (if relevant):
  - App cold-start smoke on device/emulator.
- Degraded/failure-mode tests:
  - Validate app still starts when background work is scheduled.
- Boundary tests for bypass removal:
  - Confirm merged manifest does not include `androidx.work.WorkManagerInitializer`.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew :app:assembleRelease
```

Fallback when Windows file locks block test suite completion:

```bash
./gradlew enforceRules
./gradlew :app:compileDebugKotlin
./gradlew :feature:map:compileDebugKotlin
./gradlew assembleDebug
./gradlew :app:assembleRelease
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Removing too much startup metadata | High | Remove only `androidx.work.WorkManagerInitializer` entry; keep provider and other metadata | XCPro Team |
| Release still fails for unrelated lint/errors | Medium | Isolate this fix and report remaining blockers separately | XCPro Team |
| Startup regression not caught in CI-only verification | Medium | Add manual cold-start smoke and optional connected test | XCPro Team |

## 7) Acceptance Gates

- `RemoveWorkManagerInitializer` lint error is eliminated.
- Merged release manifest no longer contains `androidx.work.WorkManagerInitializer`.
- `XCProApplication` remains the sole WorkManager configuration owner.
- `./gradlew enforceRules` passes.
- `./gradlew assembleDebug` passes.
- `./gradlew :app:assembleRelease` passes (or any remaining failure is unrelated and documented).
- No architecture rule violations or new deviations introduced.

## 8) Rollback Plan

- What can be reverted independently:
  - Manifest metadata removal entry.
- Recovery steps if regression is detected:
  1. Revert manifest change.
  2. Rebuild debug/release and confirm baseline behavior.
  3. Reintroduce fix with narrower manifest override (metadata-level only).
  4. Re-run release + startup smoke checks.

## 9) Genius Compliance Checklist

- Phase-sliced execution (A/B/C/D/E model): included.
- Startup gate declarations: included.
- SSOT/dependency/time-base declarations: included.
- Enforcement mapping and acceptance gates: included.
- Verification-blocker fallback path: included.
- Quality rescore targets after implementation:
  - Architecture cleanliness >= 4.5/5
  - Maintainability/change safety >= 4.5/5
  - Test confidence on risky paths >= 4.0/5
  - Release readiness >= 4.5/5

