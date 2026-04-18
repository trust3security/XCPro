# Map/Task Refactor Plan (2026-02-25)

## 0) Metadata

- Title: Map/Task Slice Refactor for Architecture and Release Hardening
- Owner: XCPro Team
- Date: 2026-02-25
- Issue/PR: TBD
- Status: Rebaselined after deep recheck (v2)
- Last recheck sync: 2026-02-26

### 0.1 Score Baseline (Current)

| Metric | Current | Target (This Plan) |
|---|---:|---:|
| Architecture cleanliness | 4.2/5 | 4.6/5 |
| Maintainability/change safety | 3.7/5 | 4.4/5 |
| Test confidence on risky paths | 3.9/5 | 4.4/5 |
| Overall map/task slice quality | 4.0/5 | 4.5/5 |
| Release readiness (map/task slice) | 3.4/5 | 4.4/5 |
| Test confidence on risky map/task paths | 4.1/5 | 4.6/5 |

## 1) Scope

- Problem statement:
  - Compile/lint/build baselines are now green for map/task, but the required architecture gate still fails in default mode and confidence debt remains:
    - `./gradlew enforceRules` fails under configuration cache because `build.gradle.kts` starts external probe processes at configuration time (`where pwsh` / `where powershell`).
    - Brittle source-text tests still validate file content strings instead of behavior.
    - Timezone determinism risk remains in thermal retention day-boundary wiring.
    - Flake-prone wall-clock/sleep test patterns remain in targeted tests.
    - Working tree hygiene remains noisy with high untracked artifact count.
- Why now:
  - `AGENTS.md` requires `./gradlew enforceRules` as a first-class gate; default failure blocks release confidence and CI parity.
- In scope:
  - Make `enforceRules` configuration-cache safe for default invocation.
  - Keep compile/lint/build baselines green while refactoring tests.
  - Replace brittle text-based tests with behavior tests.
  - Harden timezone determinism contract for thermal retention logic.
  - Remove sleep/wall-clock polling from known flaky tests.
  - Clean repository temp/log hygiene.
- Out of scope:
  - New product features.
  - Broad UI redesign.
  - Cross-module architecture rewrites not required for this closure.
- User-visible impact:
  - Stability and release confidence improvements; no intentional functional changes.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN traffic preferences | `OgnTrafficPreferencesRepository` | `Flow/StateFlow` | UI-owned persisted preference mirrors |
| Thermal hotspot state | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | UI-owned mutable hotspot authority |
| ADS-B network availability | `AndroidAdsbNetworkAvailabilityAdapter` through domain port | `StateFlow<Boolean>` | ad-hoc UI connectivity state |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Planned touch points:
  - `build.gradle.kts`
  - `feature/map/src/test/java/com/trust3/xcpro/map/HotspotsOverlayPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/GeneralSettingsScreenPolicyTest.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/di/OgnThermalModule.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/OpenSkyTokenRepositoryTest.kt`
  - `.gitignore`

Boundary risk:
- Medium. Test refactor can accidentally lose coverage unless behavior seams are explicit.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Thermal tracker lifetimes | Monotonic | consistent duration math |
| Thermal retention day-window boundary | Wall + injected zone | product behavior is local-day based |
| Replay sequencing | Replay | deterministic replay behavior |

Explicitly forbidden:
- Monotonic vs wall arithmetic
- Replay vs wall arithmetic

### 2.4 Replay Determinism

- Deterministic for same input: required and preserved.
- Randomness: no new randomness in domain logic.
- Divergence rule: local-day thermal retention must be deterministic under injected zone.

### 2.5 Enforcement Coverage

| Risk | Guard Type | File/Test |
|---|---|---|
| Config-cache unsafe `enforceRules` wiring | gradle gate | `./gradlew enforceRules`, `build.gradle.kts` |
| Compile baseline drift | compile gate | `:feature:map:compileDebugKotlin` |
| Lint baseline drift | lint gate | `:feature:map:lintDebug` |
| Brittle source-text tests | unit test quality gate | policy tests converted to behavior tests |
| Thermal timezone ambiguity | determinism gate | thermal retention tests with explicit `ZoneId` |
| Wall-clock/sleep flake patterns | stress-test gate | map/adsb targeted reruns |
| Temp/log artifact pollution | hygiene gate | `git status --short` remains focused |

### 2.6 Recheck Findings (2026-02-26, post-Phase-4 partial update)

P0 (closed):
- `./gradlew enforceRules` now passes with configuration cache enabled after removing config-time process probes from `build.gradle.kts`.
- Verification evidence:
  - `./gradlew enforceRules` -> PASS, configuration cache stored/reused.

P1 (closed in current workspace; keep as regression guards):
- `:feature:map:compileDebugKotlin` PASS.
- `:feature:map:lintDebug` PASS.
- `:feature:map:testDebugUnitTest --rerun-tasks` PASS.
- `./gradlew assembleDebug` PASS.
- `./gradlew --no-configuration-cache enforceRules` PASS.

P1 (test confidence debt, closed):
- Source-file text assertions removed and replaced with behavior tests in:
  - `feature/map/src/test/java/com/trust3/xcpro/map/HotspotsOverlayPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/GeneralSettingsScreenPolicyTest.kt`
- Verification evidence:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.HotspotsOverlayPolicyTest" --tests "com.trust3.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest"` -> PASS.

P1 (determinism contract risk):
- Timezone source uses `ZoneId.systemDefault()` in:
  - `feature/map/src/main/java/com/trust3/xcpro/di/OgnThermalModule.kt`

P2 (flake debt, closed for targeted files):
- Removed `Thread.sleep`/wall-clock wait patterns in:
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt` (`awaitCondition` now scheduler-driven)
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/OpenSkyTokenRepositoryTest.kt` (deferred gate instead of sleep)
- Verification evidence:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.OpenSkyTokenRepositoryTest"` -> PASS.

P2 (hygiene debt, partial closure):
- Added ignore coverage for transient logs/temp artifacts in `.gitignore`:
  - `build-*.log`
  - `tmp_*`
  - `tmp_*/`
- Remaining workspace noise includes non-temp tracked/untracked working files outside this plan slice.

## 3) Data Flow (Before -> After)

No ownership topology change intended:

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI
```

Work is corrective: verification-gate reliability, test/determinism hardening, and hygiene cleanup.

## 4) Implementation Phases

### Phase 0 - `enforceRules` Config-Cache Compatibility

- Goal:
  - Make default `./gradlew enforceRules` pass without `--no-configuration-cache`.
- Files:
  - `build.gradle.kts`
- Actions:
  - Remove configuration-time external process probing from task wiring.
  - Resolve shell command selection in a config-cache-safe way.
- Exit criteria:
  - `./gradlew enforceRules` passes (no extra flags).

### Phase 1 - Baseline Lock (Compile/Lint/Build)

- Goal:
  - Keep known-green gates stable while refactor slices land.
- Files:
  - no planned behavior files; verification-focused.
- Actions:
  - Re-run compile/lint/test/assemble after each subsequent phase.
- Exit criteria:
  - `:feature:map:compileDebugKotlin`, `:feature:map:lintDebug`, and `assembleDebug` remain green.

### Phase 2 - Replace Brittle Source-Text Tests

- Goal:
  - Convert policy tests from source-string assertions to behavior assertions.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/map/HotspotsOverlayPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/GeneralSettingsScreenPolicyTest.kt`
  - minimal production seams only if needed for testability.
- Actions:
  - Remove `Paths.get`/`Files.readAllBytes` code-path checks.
  - Validate behavior through coordinator/use-case/state outputs.
- Exit criteria:
  - Both tests pass without reading source files from disk.

### Phase 3 - Thermal Timezone Determinism Hardening

- Goal:
  - Keep local-midnight product behavior but make zone behavior explicit and test-proven.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/di/OgnThermalModule.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`
  - `docs/ARCHITECTURE/PIPELINE.md` if flow contract changes.
- Actions:
  - Preserve injected zone contract.
  - Add/extend tests for non-UTC and midnight boundary behavior.
- Exit criteria:
  - Thermal retention tests pass for explicit zone scenarios.

### Phase 4 - Flake Pattern Removal

- Goal:
  - Remove wall-clock/sleep polling in targeted tests.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/OpenSkyTokenRepositoryTest.kt`
- Actions:
  - Replace with test scheduler driven waiting or deterministic synchronization.
- Exit criteria:
  - No `Thread.sleep`/`System.currentTimeMillis` in these tests.
  - 6-pass rerun for targeted tests.

### Phase 5 - Repo Hygiene Hardening

- Goal:
  - Prevent temp/log artifacts from polluting working tree and commits.
- Files:
  - `.gitignore`
  - local untracked temp artifacts (cleanup).
- Actions:
  - Add robust ignore patterns for `tmp_*` and transient logs.
  - Remove existing generated temp folders/logs from workspace.
- Exit criteria:
  - `git status --short` no longer dominated by temp/log artifacts.

### Phase 6 - Full Verification and Rescore

- Goal:
  - Validate closure and update quality scoring with evidence.
- Required commands:
  - `./gradlew enforceRules`
  - `./gradlew :feature:map:compileDebugKotlin`
  - `./gradlew :feature:map:testDebugUnitTest --rerun-tasks`
  - `./gradlew :feature:map:lintDebug`
  - `./gradlew assembleDebug`
- Exit criteria:
  - All required gates green.
  - P0/P1 closed; only explicit approved residuals allowed.

## 5) Test Plan

- Unit tests:
  - Thermal retention boundary/zone cases.
  - Converted policy tests with behavior assertions.
- Stability tests:
  - Targeted rerun loops for former flaky tests.
- Required checks:

```bash
./gradlew enforceRules
./gradlew :feature:map:compileDebugKotlin
./gradlew :feature:map:testDebugUnitTest --rerun-tasks
./gradlew :feature:map:lintDebug
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Config-cache fix for `enforceRules` breaks shell portability | Medium | validate on Windows shell path used by this repo and keep fallback explicit | XCPro Team |
| Converted tests lose intent coverage | Medium | map each old assertion to concrete behavior assertion | XCPro Team |
| Timezone handling regressions | Medium | explicit zone test matrix (UTC + non-UTC) | XCPro Team |
| Flake cleanup incomplete | Medium | rerun stress loop and fail campaign on recurrence | XCPro Team |
| Temp artifacts reappear | Low | broaden `.gitignore` and enforce cleanup before commit | XCPro Team |

## 7) Acceptance Gates

- `enforceRules` green without `--no-configuration-cache`.
- `:feature:map:compileDebugKotlin` green.
- `:feature:map:lintDebug` green (zero errors).
- `:feature:map:testDebugUnitTest --rerun-tasks` green.
- No source-text file-reading tests in map/task policy tests.
- No `Thread.sleep`/wall-clock polling in the two targeted flaky tests.
- Temp/log artifact noise reduced to expected development baseline.
- Score targets:
  - Architecture cleanliness >= 4.6/5
  - Maintainability/change safety >= 4.4/5
  - Test confidence on risky paths >= 4.4/5
  - Overall map/task slice quality >= 4.5/5
  - Release readiness (map/task slice) >= 4.4/5
  - Test confidence on risky map/task paths >= 4.6/5

## 8) Rollback Plan

- Keep each phase in isolated commit(s).
- If regression occurs:
  1. Revert last phase only.
  2. Re-run required gates.
  3. Open focused follow-up issue with failing command and file references.
