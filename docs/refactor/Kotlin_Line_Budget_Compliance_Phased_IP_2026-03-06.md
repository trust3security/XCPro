# Kotlin Line-Budget Compliance Phased IP (Map Runtime + ADS-B Tests)

## Purpose

Production-grade phased implementation plan to bring current Kotlin files over
the default line-budget rule (`<= 500`) into compliance while preserving
behavior, architecture boundaries, replay determinism, and map visual SLO
performance.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (entry `RULES-20260306-14`)

## 0) Metadata

- Title: Kotlin line-budget compliance for map runtime and ADS-B test files
- Owner: XCPro Team
- Date: 2026-03-06
- Issue/PR: RULES-20260306-14 remediation lane
- Status: Draft

## 1) Scope

- Problem statement:
  - The following files currently exceed `500` lines:
    - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntime.kt` (625)
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` (595)
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt` (550)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` (519)
    - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTestRuntime.kt` (517)
    - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryEmergencyOutputTest.kt` (503)
- Why now:
  - This is an active deviation (`RULES-20260306-14`) with expiry `2026-04-30`.
  - Oversized files increase regression risk in map overlay and weather/rain state
    transitions.
  - Current enforcement does not reliably block new default-budget drift.
- In scope:
  - Refactor-only modularization of the six files above.
  - Add/adjust tests to lock behavior parity during file splits.
  - Tighten automated line-budget enforcement for default `<= 500` policy.
- Out of scope:
  - Functional feature additions.
  - Behavior/policy changes for map overlays, forecast/weather, ADS-B emergency
    logic, or settings UX.
  - Changes to external contracts/public APIs unless needed only for internal
    decomposition.
- User-visible impact:
  - None intended. Any visible runtime behavior change is a regression.

## 1A) Focused Code-Pass Deltas (Missed in Initial Draft)

Focused pass date: 2026-03-06 (production hardening pass).

- `MapOverlayManagerRuntime.kt` missed seams:
  - ADS-B render defer/flush cadence state machine remains in monolith and needs
    dedicated delegate extraction.
  - traffic front-order throttle/signature logic and delayed interaction
    deactivation logic still share the same file and should be isolated.
- `MapOverlayManager.kt` compatibility gate was missing:
  - `MapOverlayManager` is a thin subclass wrapper over runtime constructor and
    must stay constructor-compatible during runtime split phases.
- `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` drift risk was
  under-specified:
  - duplicated apply logic exists across set/reapply paths for forecast and rain;
    plan must require a single apply path and transition-table tests.
- `MapScreenContentRuntime.kt` split invariants were missing:
  - preserve `rememberSaveable` ownership/keys.
  - preserve `hiltViewModel()` acquisition scope and lifecycle behavior.
  - preserve lambda stability/recomposition behavior while extracting binders.
- `SettingsDfRuntime.kt` decomposition strategy was incomplete:
  - category rows and destination routing should move to a model-driven registry.
  - package naming mismatch (`com.example.ui1.screens`) must remain stable during
    split unless explicitly migrated in separate plan.
- ADS-B test split strategy missed a dependency step:
  - split shared fixture/fake-provider support first, then split scenarios.
  - include migration checklist for all subclasses extending
    `AdsbTrafficRepositoryTestBase`.
- Guardrail scope was too narrow:
  - add secondary watchlist for near-threshold Kotlin files (`>= 400`) so this
    refactor does not push adjacent hotspots over policy.

## 2) Architecture Contract

### 2.1 SSOT Ownership

No SSOT owner changes are allowed in this workstream.

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B traffic snapshot and emergency telemetry | `AdsbTrafficRepository` / store path | repository `Flow` snapshots | UI/runtime mirrors with policy logic |
| OGN traffic settings and trail settings | OGN preference repositories | settings `Flow` / `StateFlow` | duplicated mutable state in map runtime |
| Forecast/weather overlay settings | forecast/weather preference repositories | settings `Flow` / composed state | local mutable policy copies in Compose runtime |
| Runtime map overlay state | map runtime owner (`MapOverlayManager` + delegates) | runtime state holders internal to map module | parallel runtime owners with independent truth |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map` runtime files and tests.
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
    compatibility wrapper checks.
  - `scripts/ci/enforce_rules.ps1` for line-budget guard hardening.
- Boundary risk:
  - Refactor could accidentally move policy into UI helper files during split.
  - Mitigation: keep existing policy and state machines in same layer; extract by
    responsibility, not by convenience.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Map overlay runtime orchestration internals | `MapOverlayManagerRuntime.kt` monolith | focused runtime delegates (`adsb-cadence`, `traffic-order`, `lifecycle`, `style-reapply`) | reduce file size and isolate state-machine edges | existing overlay manager unit tests + no API behavior drift |
| Map overlay wrapper constructor compatibility | implicit runtime constructor pass-through in `MapOverlayManager.kt` | explicit compatibility checkpoint in split phases | prevent accidental API drift and subclass breakage | compile gate + constructor signature parity checks |
| Forecast/weather runtime apply and deferred rain handling internals | `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` monolith | focused helpers (`single apply path`, `deferred queue`, `detach/clear hygiene`) | reduce coupling and regression blast radius | weather-rain regression tests + no stale replay behavior |
| Map content runtime tab/state composition internals | `MapScreenContentRuntime.kt` monolith | per-domain runtime binders (forecast/weather/traffic tabs + status channels) | isolate composition logic and side-effect wiring | Compose/runtime tests + map integration tests |
| Settings drawer runtime section composition internals | `SettingsDfRuntime.kt` monolith | model-driven category registry + section-specific files | reduce review surface and line-budget risk | screen tests/snapshots compile + behavior parity |
| ADS-B shared test support and mega-scenarios | two test monolith files | shared support fixtures first, then scenario-focused files by behavior family | improve maintainability and prevent subclass breakage | unchanged test assertions + subclass migration compile checks |

### 2.2B Bypass Removal Plan (Mandatory)

No architecture-layer bypasses are intentionally added. Existing entrypoints stay
stable while internal code is decomposed.

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

Refactor must not alter time-base rules.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Overlay apply duration/cadence metrics | Monotonic | runtime latency measurement correctness |
| Replay selection and scrub timeline behavior | Replay | deterministic replay semantics |
| UI labels and wall-clock-attribution timestamps | Wall | UI/output only |

Explicitly forbidden:

- Monotonic vs wall comparisons
- Replay vs wall comparisons
- New direct system-time calls in domain/fusion paths

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - Main: map/UI runtime side effects and Compose collection
  - Default: pure compute helpers if extracted
  - IO: repository/network/disk boundaries only
- Primary cadence/gating sensor:
  - unchanged; existing map runtime and repository cadence contracts remain intact
- Hot-path latency budget:
  - preserve map SLOs (`MS-UX-*`, `MS-ENG-*`) from baseline matrix

### 2.5 Replay Determinism

- Deterministic for same input: Yes (must remain yes)
- Randomness used: No
- Replay/live divergence rules:
  - unchanged; replay state and timing contracts remain as currently documented

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| File-size drift reappears | `ARCHITECTURE.md` 14A, `CODING_RULES.md` 1A.4 | `enforceRules` hardening | `scripts/ci/enforce_rules.ps1` |
| Overlay behavior regression during split | map visual/runtime contract | unit/integration tests | `feature/map` overlay manager tests |
| Deferred weather-rain replay regression | determinism/state-machine rules | focused regression tests | `MapOverlayManagerRuntime...WeatherRain...Test` |
| Forecast/rain set vs reapply divergence | determinism/state-machine rules | transition-table tests + code review | `MapOverlayManagerRuntimeForecastWeatherDelegate*` tests |
| `MapOverlayManager` constructor drift from runtime split | dependency boundary and API stability | compile gate + targeted wrapper tests | `MapOverlayManager.kt` and runtime split tests |
| UI/business logic leakage | MVVM/UDF boundary rules | `enforceRules` + code review | `feature/map/ui` split files |
| `rememberSaveable` key/scope drift after compose split | UI lifecycle and state-ownership rules | compose restore tests + review checklist | `MapScreenContentRuntime*` tests |
| Hilt VM acquisition scope drift | ViewModel lifecycle contract | compose integration tests | `MapScreenContentRuntime*` tests |
| ADS-B test-base subclass breakage after split | test maintainability and safety | compile + targeted ADS-B suite runs | `feature/map/src/test/java/com/example/xcpro/adsb/**` |
| Nearby hotspot files regrow above budget during adjacent edits | file-budget policy | secondary watchlist report + stricter caps | `scripts/ci/enforce_rules.ps1` |
| Replay timing drift | time base/replay contract | unit/integration replay tests | map/replay test lanes |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

This refactor is no-intent behavior-preserving; mandatory SLOs must remain
within current thresholds (no regressions).

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Pan/zoom/rotate smoothness parity | MS-UX-01 | current baseline matrix | no regression; threshold remains met where currently green | package evidence run + trace compare | phase 6 |
| Marker stability parity (OGN/ADS-B/weather) | MS-UX-03 | current baseline matrix | no regression | dense-traffic stress evidence | phase 6 |
| Order/flicker parity in steady state | MS-UX-04 | current baseline matrix | no regression | reorder counters + integration tests | phase 6 |
| Replay scrub/render stability parity | MS-UX-05 | current baseline matrix | no regression | replay scrub tests + perf capture | phase 6 |
| Startup readiness parity | MS-UX-06 | current baseline matrix | no regression | cold/warm startup captures | phase 6 |
| Runtime apply latency parity | MS-ENG-01 | current baseline matrix | no regression | overlay apply perf counters | phase 6 |
| Lifecycle owner sync integrity | MS-ENG-06 | current baseline matrix | no regression | lifecycle integration tests | phase 6 |
| Weather cache identity correctness | MS-ENG-08 | current baseline matrix | no regression | weather rain cache tests | phase 6 |
| Render cadence ownership integrity | MS-ENG-10 | current baseline matrix | no regression | cadence owner tests | phase 6 |

## 3) Data Flow (Before -> After)

Before:

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI/runtime
```

After:

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI/runtime facade -> internal delegates
```

External data flow and ownership remain unchanged. Only internal file
decomposition changes.

## 4) Implementation Phases

### Phase 0 - Baseline + freeze

- Goal:
  - Capture behavior baseline before any splits.
  - Freeze public APIs for touched classes.
- Files to change:
  - none (capture-only + checkpoints and invariant ledger)
- Tests to add/update:
  - ensure current tests green; capture map package evidence references
  - add baseline checklist for:
    - `MapOverlayManager` runtime constructor compatibility
    - `MapScreenContentRuntime` `rememberSaveable` keys/state holders
    - `MapScreenContentRuntime` `hiltViewModel()` call-site ownership
    - ADS-B test-base subclass inventory
- Exit criteria:
  - baseline tests pass
  - baseline SLO references recorded for impacted IDs
  - baseline invariants checklist committed in plan notes

### Phase 1 - `MapOverlayManagerRuntime.kt` decomposition

- Goal:
  - Split runtime orchestration internals into focused delegates, including the
    ADS-B cadence state machine and traffic ordering path.
- Files to change (planned):
  - `MapOverlayManagerRuntime.kt` (facade reduced to <= 450)
  - new files under `feature/map/.../map/`:
    - `MapOverlayRuntimeAdsbCadenceDelegate.kt`
    - `MapOverlayRuntimeTrafficOrderDelegate.kt`
    - `MapOverlayRuntimeTrafficDelegate.kt`
    - `MapOverlayRuntimeInteractionDelegate.kt`
    - `MapOverlayRuntimeLifecycleDelegate.kt`
    - `MapOverlayRuntimeStyleReapplyDelegate.kt`
  - compatibility checkpoint:
    - `MapOverlayManager.kt` remains constructor-compatible with runtime facade
      until explicit migration phase is approved
- Tests to add/update:
  - existing overlay manager tests updated for moved internals
  - ADS-B defer/flush cadence parity tests
  - traffic order/front signature throttle parity tests
  - interaction deactivate/deferred deactivate parity tests
  - no-duplicate-owner and lifecycle idempotency checks
- Exit criteria:
  - original file <= 500
  - runtime facade target <= 450 (headroom)
  - `MapOverlayManager` wrapper compiles with no constructor drift
  - behavior-parity tests pass

### Phase 2 - `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` decomposition

- Goal:
  - Isolate weather/forecast apply policy and deferred queue state machine with
    single-source apply logic for set/reapply paths.
- Files to change (planned):
  - `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` (<= 450)
  - new files:
    - `ForecastOverlayApplyCoordinator.kt`
    - `WeatherRainDeferredApplyPolicy.kt`
    - `WeatherRainDeferredStateMachine.kt`
    - `ForecastWeatherReapplyBridge.kt`
- Tests to add/update:
  - stale deferred replay regression tests
  - disable-during-interaction and detach/reattach tests
  - transition-table tests for weather-rain defer/apply/clear/flush states
  - set-vs-reapply parity tests to enforce shared apply path behavior
- Exit criteria:
  - original file <= 500
  - runtime delegate target <= 450 (headroom)
  - weather-rain regression suite green

### Phase 3 - `MapScreenContentRuntime.kt` decomposition

- Goal:
  - Separate tab/runtime composition by concern without changing behavior or
    Compose lifecycle/state ownership contracts.
- Files to change (planned):
  - `MapScreenContentRuntime.kt` (<= 450)
  - new files under `feature/map/.../map/ui/`:
    - `MapScreenContentRuntimeStateHolders.kt`
    - `MapScreenContentRuntimeForecastBinder.kt`
    - `MapScreenContentRuntimeWeatherBinder.kt`
    - `MapScreenContentRuntimeTrafficBinder.kt`
    - `MapScreenContentRuntimeStatusChannels.kt`
- Tests to add/update:
  - Compose/runtime wiring tests for state and side-effect parity
  - `rememberSaveable` restore/key parity tests
  - Hilt ViewModel ownership/scope parity tests
  - recomposition/lambda stability smoke tests for extracted binders
- Exit criteria:
  - original file <= 500
  - runtime content file target <= 450 (headroom)
  - baseline `rememberSaveable` and Hilt invariants preserved
  - no UI/business logic drift

### Phase 4 - `SettingsDfRuntime.kt` decomposition

- Goal:
  - Split settings runtime into domain-specific sections with model-driven
    category registry (no routing behavior drift).
- Files to change (planned):
  - `SettingsDfRuntime.kt` (<= 450)
  - new files (same package):
    - `SettingsDfRuntimeCategoryRegistry.kt`
    - `SettingsDfRuntimeGeneralSection.kt`
    - `SettingsDfRuntimeMapSection.kt`
    - `SettingsDfRuntimeTrafficSection.kt`
    - `SettingsDfRuntimeWeatherSection.kt`
- Tests to add/update:
  - compose compile/snapshot coverage for each section path
  - category-row to route-destination parity tests
  - active-subsheet switch parity tests
- Exit criteria:
  - original file <= 500
  - runtime settings file target <= 450 (headroom)
  - package name remains stable during split (`com.example.ui1.screens`) unless
    separate migration plan is approved
  - settings behavior unchanged

### Phase 5 - ADS-B test file decomposition

- Goal:
  - Split ADS-B test monoliths using support-first extraction, then
    scenario-focused test files.
- Files to change (planned):
  - support-first extraction:
    - `feature/map/src/test/java/com/example/xcpro/adsb/support/**`
    - `AdsbTrafficRepositoryTestRuntime.kt` reduced to base support contract
  - `AdsbTrafficRepositoryTestRuntime.kt` (<= 450)
  - `AdsbTrafficRepositoryEmergencyOutputTest.kt` (<= 450)
  - new test files, by scenario family:
    - `AdsbTrafficRepositoryRuntimeLifecycleTest.kt`
    - `AdsbTrafficRepositoryRuntimeMetadataTest.kt`
    - `AdsbTrafficRepositoryEmergencyPolicyTest.kt`
    - `AdsbTrafficRepositoryEmergencyOutputPortTest.kt`
- Tests to add/update:
  - no assertion semantics change; move assertions intact
  - subclass migration compile checks for all classes extending
    `AdsbTrafficRepositoryTestBase`
- Exit criteria:
  - both original files <= 500
  - both original files target <= 450 (headroom)
  - all subclass test files compile/pass after support extraction
  - full ADS-B test matrix still green

### Phase 6 - Enforcement hardening and closure evidence

- Goal:
  - Prevent recurrence by tightening static gate for default Kotlin budget and
    adding near-threshold hotspot watchlist reporting.
- Files to change (planned):
  - `scripts/ci/enforce_rules.ps1`
  - optional docs update for watchlist policy in architecture/rules docs if
    enforcement semantics change
  - optional supporting script/tests for rule validation
- Tests to add/update:
  - guard tests or script self-check path if available
- Exit criteria:
  - new default `<= 500` violations fail `./gradlew enforceRules`
  - touched scope files are held to `<= 450` headroom targets
  - report output includes secondary watchlist (`>= 400`) for early-warning drift
  - all six files under 500

### Phase 7 - Final validation, deviation closure prep, and quality rescore

- Goal:
  - Produce production-grade release evidence and closure package.
- Files to change:
  - documentation updates only as required
  - `docs/ARCHITECTURE/PIPELINE.md` only if runtime ownership/wiring semantics
    changed during decomposition
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` closure update (only when criteria met)
- Tests to add/update:
  - none beyond final verification set
- Exit criteria:
  - required checks green
  - impacted SLO evidence attached
  - deviation `RULES-20260306-14` closure criteria met
  - mandatory quality rescore recorded with AGENT evidence fields

## 5) Test Plan

- Unit tests:
  - ADS-B cadence and traffic ordering helper/delegate tests
  - forecast/weather single-apply-path parity tests
  - deferred weather-rain transition-table tests
  - lifecycle idempotency checks for runtime owner helpers
- Replay/regression tests:
  - replay scrub parity for map runtime paths touched by split
- UI/instrumentation tests (if needed):
  - Compose/runtime state wiring checks for split content/settings sections
  - `rememberSaveable` restoration parity tests for split map content state holders
  - ViewModel ownership parity tests for `hiltViewModel()` call-site stability
  - connected tests if map interaction wiring is touched
- Degraded/failure-mode tests:
  - weather-rain disable while deferred
  - map detach/reattach with deferred state
  - defer(old) -> apply(new) -> interaction end (old must not replay)
- Boundary tests for removed bypasses:
  - N/A for this plan (no bypass contract changes)
- ADS-B split migration checks:
  - compile and run all subclasses extending `AdsbTrafficRepositoryTestBase`

Required checks:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Hidden behavior drift during large file splits | map/runtime regressions | phase-by-phase parity tests and no-intent behavior rule | XCPro Team |
| Performance regression due indirection | SLO misses | keep hot paths local and measure impacted SLO IDs | XCPro Team |
| Layering drift during UI/runtime extraction | architecture violation | boundary review checklist + `enforceRules` | XCPro Team |
| Runtime wrapper constructor drift (`MapOverlayManager`) | compile/runtime break risk | explicit compatibility checkpoint in phase 0/1 | XCPro Team |
| `rememberSaveable` key/scope drift in map content split | state restore regressions | freeze baseline keys and enforce restore tests | XCPro Team |
| ADS-B test subclass breakage during support extraction | test-suite fragmentation | support-first extraction + subclass migration checklist | XCPro Team |
| Incomplete line-budget gate | recurrence after refactor | harden `enforceRules` default-budget check | XCPro Team |
| Adjacent near-threshold hotspot growth | new policy drift shortly after closure | add `>= 400` watchlist visibility and tighter touched-file caps | XCPro Team |
| Merge conflicts with active map workstreams | delayed delivery | package by file family; land small sequential PRs | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time-base handling remains explicit and unchanged
- Replay behavior remains deterministic
- Impacted map SLO evidence shows no regression and mandatory thresholds remain satisfied where currently required
- All six scoped Kotlin files are `< 500` lines
- All touched scoped files meet target headroom (`<= 450`) unless explicitly excepted
- `MapOverlayManager` wrapper remains constructor-compatible with runtime facade
- `MapScreenContentRuntime` split preserves baseline `rememberSaveable` and Hilt VM scope invariants
- ADS-B split keeps all `AdsbTrafficRepositoryTestBase` subclass suites compiling and passing
- Default line-budget guard in `enforceRules` blocks new non-exempt `> 500` Kotlin files
- Secondary watchlist (`>= 400`) is reported during enforcement for early-warning drift
- Deviation `RULES-20260306-14` closure packet is ready

## 8) Rollback Plan

- What can be reverted independently:
  - Each phase lands as isolated commit(s) by file family.
  - If regression occurs, revert only the last phase package.
- Recovery steps if regression is detected:
  1. Revert offending phase commit(s).
  2. Re-run `arch_gate`, `enforceRules`, `testDebugUnitTest`, `assembleDebug`.
  3. Re-capture impacted SLO evidence if runtime behavior was touched.
  4. Re-open phase with smaller extraction units and additional parity tests.

## 9) Target End-State Checklist

- `SettingsDfRuntime.kt` <= 500
- `MapOverlayManagerRuntime.kt` <= 500
- `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` <= 500
- `MapScreenContentRuntime.kt` <= 500
- `AdsbTrafficRepositoryTestRuntime.kt` <= 500
- `AdsbTrafficRepositoryEmergencyOutputTest.kt` <= 500
- No new Kotlin file exceeds 500 without explicit, time-boxed deviation

## 9A) Secondary Watchlist (`>= 400` Near-Threshold, 2026-03-06 Snapshot)

This watchlist is informational for proactive budgeting and should be reviewed
at phase boundaries so adjacent work does not reintroduce `>500` drift.

- `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt` (496)
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt` (495)
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreEmergencyGeometryTest.kt` (489)
- `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetControls.kt` (486)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsScreen.kt` (483)
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt` (483)
