# Agent-Execution-Contract-MapTask-2026-02-25.md

## Purpose

This contract defines how an automated agent executes
`Map_Task_Refactor_Plan_2026-02-25.md` after the 2026-02-26 recheck.

Hierarchy (highest to lowest):

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/AGENT.md`
5. this file

## 0) Mission

Close map/task blockers to release-grade quality by:

- restoring default `enforceRules` reliability,
- preserving compile/lint/build green baselines,
- replacing brittle source-text tests,
- hardening thermal timezone determinism,
- removing known flaky test patterns,
- cleaning repository hygiene.

## 1) Mandatory Startup Gate (Per Run)

Before editing:

1. Read:
   - `AGENTS.md`
   - `docs/ARCHITECTURE/ARCHITECTURE.md`
   - `docs/ARCHITECTURE/CODING_RULES.md`
   - `docs/ARCHITECTURE/PIPELINE.md`
   - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
   - `docs/ARCHITECTURE/CONTRIBUTING.md`
   - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
   - `docs/refactor/Map_Task_Refactor_Plan_2026-02-25.md`
2. Declare in run summary:
   - phase being executed,
   - SSOT owners touched,
   - dependency direction check (`UI -> domain -> data`),
   - time-base declaration.
3. Preflight checks:
   - `./gradlew :feature:map:compileDebugKotlin`
   - `./gradlew enforceRules`
   - `./gradlew :feature:map:testDebugUnitTest`
   - `./gradlew :feature:map:lintDebug`
   - `./gradlew assembleDebug`

If any check fails, agent must report exact command and file references, then continue only with the smallest safe remediation slice.

## 2) Strict Priority Order

P0 (must close first):
- Fix default `enforceRules` configuration-cache failure:
  - `build.gradle.kts` must not launch external process probes during configuration.

P1 (confidence blocker):
- Replace brittle source-text tests:
  - `feature/map/src/test/java/com/example/xcpro/map/HotspotsOverlayPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/screens/navdrawer/GeneralSettingsScreenPolicyTest.kt`

P1 (determinism hardening):
- Zone behavior contract and tests:
  - `feature/map/src/main/java/com/example/xcpro/di/OgnThermalModule.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnThermalRepositoryTest.kt`

P2 (flake and hygiene):
- Remove sleep/wall-clock polling in:
  - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/OpenSkyTokenRepositoryTest.kt`
- Cleanup temp/log artifact policy in:
  - `.gitignore`
  - local untracked temp output

## 3) Non-Negotiables

Agent must never:

- violate MVVM + UDF + SSOT layering,
- move business logic into UI or ViewModel,
- use wall time directly in domain/fusion logic,
- introduce hidden mutable global state,
- bypass use-case boundaries,
- keep source-file text matching as a substitute for behavior tests,
- create undocumented architecture deviations.

## 4) Execution Loop

For each slice:

1. Pick one smallest open item from highest current priority.
2. Implement minimal cohesive edits.
3. Run scoped checks first.
4. Run full campaign checks:
   - `./gradlew :feature:map:compileDebugKotlin`
   - `./gradlew enforceRules`
   - `./gradlew :feature:map:testDebugUnitTest`
   - `./gradlew :feature:map:lintDebug`
   - `./gradlew assembleDebug`
5. Record:
   - changed files,
   - command results,
   - residual risk.
6. Repeat until no open P0/P1/P2 items remain.

Stop only when all priorities are closed or an external blocker is explicitly documented.

## 5) Windows/IO Lock Fallback

If Gradle fails due file locks:

1. Retry once after daemon cleanup.
2. If still blocked, run:
   - `./gradlew :feature:map:compileDebugKotlin`
   - `./gradlew enforceRules`
   - if failure is configuration-cache-only, run `./gradlew --no-configuration-cache enforceRules` as diagnostic evidence
   - targeted touched tests
   - `./gradlew assembleDebug`
3. Mark lint/test gate as pending and report exact blocker.

Agent must not claim full verification when required gates are skipped.

## 6) Mandatory Re-Pass Protocol

When asked for re-pass, execute six passes:

1. architecture and dependency boundaries,
2. correctness and behavior regressions,
3. time-base and replay determinism,
4. concurrency and lifecycle safety,
5. tests/flaky-risk depth,
6. docs and hygiene sync.

Report findings by severity with file references.

## 7) Documentation Sync Rules

Agent must update:

- `docs/refactor/Map_Task_Refactor_Plan_2026-02-25.md` when priorities or evidence change,
- `docs/ARCHITECTURE/PIPELINE.md` if pipeline wiring changes,
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only for approved, time-boxed deviations.

## 8) Definition of Done

Campaign is done only when:

- `:feature:map:compileDebugKotlin` is green,
- `:feature:map:lintDebug` is green (zero errors),
- `enforceRules` is green without requiring `--no-configuration-cache`,
- map unit tests are green,
- `assembleDebug` is green,
- no open P0/P1 items remain,
- flaky patterns and hygiene items are closed or explicitly accepted as deferred.

## 9) Required Final Report Format

Final output must include:

1. closed vs remaining items by priority,
2. files changed,
3. verification matrix (command, pass/fail, notes),
4. residual blockers/risks,
5. updated quality scores:
   - Architecture cleanliness
   - Maintainability/change safety
   - Test confidence on risky map/task paths
   - Overall map/task slice quality
   - Release readiness (map/task slice)
