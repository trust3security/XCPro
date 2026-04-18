# HOTSPOTS Implementation Plan

This file is intentionally aligned to `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`.

## 0) Metadata

- Title: OGN Hotspots - Retention, Display Share Filter, Anti-Fake Filter, Area Dedupe, Modal Settings UX
- Owner: XCPro map/OGN layer
- Date: 2026-02-24
- Issue/PR: TBD
- Status: Implemented (2026-02-24)

Execution result:
- Retention policy is enforced in `OgnThermalRepository` (`1..23h` rolling, `All day` until local midnight).
- Display share filtering is enforced in `OgnThermalRepository` (`5..100`, strongest-first top share).
- Fake-climb suppression is enforced (`cumulative turn > 730 deg`).
- One-best-per-area output is enforced with distance-based dedupe (`AREA_DEDUP_RADIUS_METERS`).
- General Settings now shows `Hotspots`, and the settings screen is modal-style.
- Coverage includes repository + preferences unit tests for retention/display-share/turn/dedupe behavior.
- Crash hardening is enforced: missing confirmed-tracker hotspot IDs are self-healed, invalid thermal coordinates are skipped, and thermal overlay render exceptions are guarded in overlay/runtime manager paths.

## 1) Scope

- Problem statement:
Hotspot behavior needed strict runtime policy ownership in repository paths (retention, display-share filtering, anti-fake turn gate, and crowding dedupe) plus aligned settings UX in General.
- Why now:
Current behavior can show stale/noisy thermal markers and overload map readability in crowded zones.
- In scope:
retention policy wiring, `5..100` strongest-first display share filtering, `>730` cumulative-turn gate, best-hotspot-per-area dedupe, modal-style Hotspots settings flow, tests, and docs sync.
- Out of scope:
protocol-level OGN parsing redesign, cross-session hotspot persistence, non-thermal overlay redesign.
- User-visible impact:
fewer false hotspots, cleaner map in crowded areas, user-controlled hotspot lifetime and density, Hotspots settings UX matching modal expectations.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Thermal hotspots | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | UI-computed thermal lists |
| Thermal retention setting | `OgnTrafficPreferencesRepository` | `Flow<Int>` | Composable-local state as policy authority |
| Hotspots display-percent setting | `OgnTrafficPreferencesRepository` | `Flow<Int>` | Composable-local filtering as policy authority |
| Hotspots settings UI state | `HotspotsSettingsViewModel` | `StateFlow<HotspotsSettingsUiState>` | Direct DataStore writes from Composable |

### 2.2 Dependency Direction

Confirm flow remains:
`UI -> domain/usecase -> data/repository`

- Modules/files touched:
`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`,
`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalModels.kt`,
`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`,
`feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HotspotsSettings*.kt`,
`feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`,
`app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`.
- Any boundary risk:
time-base mixing risk between monotonic and wall-time paths.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Hotspot retention enforcement | UI text only (no runtime owner) | `OgnThermalRepository` | Policy must be domain/data owned | Repository unit tests |
| Anti-fake climb turn validation | None | `OgnThermalRepository` | Keep thermal validity policy near detector state | Repository unit tests |
| Area dedupe policy | None | `OgnThermalRepository` | One policy authority before UI/render | Repository unit tests |
| Display share policy (top N%) | None | `OgnThermalRepository` | Keep strongest-first filtering deterministic and reusable | Repository unit tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `HotspotsSettingsScreen` explanatory copy | UX text can drift from runtime policy | Keep runtime policy authoritative in repository and align UI copy | Phase 2 |
| `OgnThermalRepository.publishHotspots` | Historical gap: published without full area/density policy | Publish filtered/deduped/top-share list via policy functions | Phase 3 |
| `Settings-df.kt` -> route screen | General entry naming/behavior drift | Modal-aligned Hotspots settings host | Phase 4 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Tracker continuity + sample freshness | Monotonic | Stable duration math independent of wall jumps |
| `1h..23h` retention cutoff | Wall | Matches user expectation of elapsed local time |
| `all day` cutoff | Wall + local timezone | Contract is "until local midnight" |
| Publishing/timer scheduling cadence | Monotonic | Stable runtime housekeeping cadence |

Explicitly forbidden comparisons:

- monotonic vs wall
- replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
repository policy runs on injected `@DefaultDispatcher`; UI remains on `Main`.
- Primary cadence/gating sensor:
OGN targets flow emission + repository housekeeping timer.
- Hot-path latency budget:
target under 5 ms per emission for typical target counts; dedupe uses linear/grouped operations.

### 2.5 Replay Determinism

- Deterministic for same input: Yes, when clocks/timezone are fixed in tests.
- Randomness used: No.
- Replay/live divergence rules:
retention/day-boundary behaviors use wall time; replay tests must pin wall-time and timezone explicitly.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Policy leakage into UI | AGENTS Non-Negotiables | Unit test + review | `OgnThermalRepositoryTest` |
| Time-base mixing | AGENT 1.3 | Unit test + review | `OgnThermalRepositoryTest` with `FakeClock` |
| Dedupe regression | SSOT/policy ownership | Unit test | `OgnThermalRepositoryTest` |
| Retention preference drift | SSOT settings ownership | Unit test | `OgnTrafficPreferencesRepositoryTest` |
| Modal navigation regressions | UI contract | UI/integration test + manual QA | Settings navigation tests/manual script |

## 3) Data Flow (Before -> After)

Before:

```text
OgnTrafficRepository.targets -> OgnThermalRepository -> raw hotspots
HotspotsSettingsScreen slider -> DataStore (consumed by hotspot runtime)
```

After:

```text
OgnTrafficRepository.targets
+ OgnTrafficPreferencesRepository.thermalRetentionHoursFlow
+ OgnTrafficPreferencesRepository.hotspotsDisplayPercentFlow
-> OgnThermalRepository (turn gate + retention + area dedupe + strongest-first top-share filter)
-> MapScreenViewModel
-> Map overlay runtime
```

## 4) Implementation Phases

### Phase 0 - Baseline

- Goal:
lock current behavior and establish failing tests for requested policies.
- Files to change:
thermal repository tests and preferences tests.
- Tests to add/update:
new failing specs for turn gate, retention, dedupe, and modal entry behavior.
- Exit criteria:
baseline tests confirm current gap and protect regressions.

### Phase 1 - Pure Logic

- Goal:
implement turn accumulation and retention policy functions with explicit time-base boundaries.
- Files to change:
`OgnThermalRepository.kt`, `OgnThermalModels.kt`.
- Tests to add/update:
repository policy tests using `FakeClock`.
- Exit criteria:
deterministic unit tests pass for turn and retention logic.

### Phase 2 - Repository/SSOT Wiring

- Goal:
wire retention + display-percent preference flows into repository and enforce policy in publish path.
- Files to change:
`OgnThermalRepository.kt`, `OgnTrafficPreferencesRepository.kt`, related use cases if needed.
- Tests to add/update:
retention/display-percent persistence + end-to-end repository publish tests.
- Exit criteria:
single policy authority in repository, no UI policy duplication.

### Phase 3 - ViewModel/UI Wiring

- Goal:
apply modal contract for Hotspots settings and keep ViewModel as state authority.
- Files to change:
`Settings-df.kt`, `SettingsRoutes.kt`, `AppNavGraph.kt`, `HotspotsSettingsScreen.kt`, map settings host as needed.
- Tests to add/update:
navigation and UX behavior tests where available.
- Exit criteria:
Hotspots settings behavior matches modal contract without business logic in UI, including retention and display-share sliders.

### Phase 4 - Hardening and Docs

- Goal:
finish docs sync, enforce checks, and quality rescore.
- Files to change:
`docs/ARCHITECTURE/PIPELINE.md`, `docs/OGN/OGN.md`, HOTSPOTS docs.
- Tests to add/update:
degraded/failure-mode and edge-case tests.
- Exit criteria:
all required checks pass, docs reflect implemented behavior.

## 5) Test Plan

- Unit tests:
`OgnThermalRepositoryTest`, `OgnTrafficPreferencesRepositoryTest`.
- Replay/regression tests:
fixed-clock deterministic tests for retention/day-boundary logic.
- UI/instrumentation tests (if needed):
General -> Hotspots modal/open behavior and retention/display-share slider persistence visibility.
- Degraded/failure-mode tests:
missing track values, invalid coordinates, timezone/day-boundary transitions, stream-disabled transitions.
- Boundary tests for removed bypasses:
UI text and runtime behavior stay aligned; publish path always applies dedupe/retention/display-share filtering.

Required checks:

```bat
./gradlew.bat enforceRules
./gradlew.bat :feature:map:testDebugUnitTest
./gradlew.bat assembleDebug
```

Optional when relevant:

```bat
./gradlew.bat connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Time-base bugs around midnight | Incorrect hotspot pruning | Pin timezone + wall clock in tests; explicit wall/mono boundaries | OGN feature owner |
| Over-aggressive area dedupe | Hides useful hotspot data | Tune dedupe radius constant with field testing + unit fixtures | OGN feature owner |
| Modal navigation regressions | User cannot return cleanly to map | Add navigation tests and manual QA script | UI owner |
| Performance regression in dense traffic | Map update jank | Linear/grouped dedupe and profiling on realistic snapshots | Map runtime owner |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling is explicit in code and tests.
- Replay behavior remains deterministic for deterministic inputs.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).
- Hotspots feature contract met:
`1h..all day` retention, `5..100` strongest-first display share, `>730` turn gate, one-best-per-area, modal-style settings UX.

## 8) Rollback Plan

- What can be reverted independently:
UI modal changes, retention/display-percent flow wiring, turn gate policy, area dedupe policy.
- Recovery steps if regression is detected:
revert phase-by-phase commits, keep existing thermal detection baseline, and restore previous docs state.

## 9) AGENT.md Quality Rescore Hook

After implementation, record evidence-based rescore:

- Architecture cleanliness
- Maintainability/change safety
- Test confidence on risky paths
- Overall map/task slice quality
- Release readiness

## 10) Post-Implementation Re-pass Findings (2026-02-25)

Severity-ordered findings from Hotspots display-path code re-pass:

| Severity | Finding | Evidence |
|---|---|---|
| High | Hotspots toggle does not auto-enable OGN overlay, so hotspot rendering path receives `emptyList()` when OGN is off. | `MapScreenTrafficCoordinator.onToggleOgnThermals` only flips thermal pref (`feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt:214`), while render gate requires `ognOverlayEnabled && showOgnThermalsEnabled` (`feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt:89`). |
| High | Overlay anchor lists for forecast/weather/satellite do not include thermal layer IDs, enabling z-order where thermal circles can be occluded. | `ForecastRasterOverlay.ANCHOR_LAYER_IDS` (`feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt:777`), `WeatherRainOverlay.baseOverlayAnchors` (`feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt:311`), `SkySightSatelliteOverlay.SKY_SIGHT_ANCHOR_LAYER_IDS` (`feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt:350`). |
| Low | Legacy TH FAB component is currently unused in active map UI composition paths. | `OgnThermalsButton` only appears in its own file (`feature/map/src/main/java/com/trust3/xcpro/map/components/OgnThermalsButton.kt:16`). |

Proposed remediation intent:

- Align thermal toggle behavior with SCIA behavior by auto-enabling OGN overlay when turning Hotspots on.
- Add thermal layer IDs (`ogn-thermal-circle-layer`, `ogn-thermal-label-layer`) to forecast/weather/satellite anchor sets to preserve thermal visibility.
- Remove dead TH FAB component or wire it intentionally; keep one canonical hotspot control surface.

Re-pass cycle 2 note (2026-02-25):

- A second full re-pass (initial + 3 additional passes) reconfirmed the same findings.
- No new defects were discovered beyond the known toggle-contract and z-order issues.

Remediation status update (2026-02-25):

- Completed: `onToggleOgnThermals` now auto-enables OGN overlay when enabling thermals.
- Completed: thermal layer IDs were added to forecast/weather/satellite anchor lists to preserve hotspot visibility.
- Remaining: optional cleanup for unused `OgnThermalsButton` component.
