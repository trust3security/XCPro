# CHANGE_PLAN_HOTSPOTS_THERMALS_2026-02-24.md

## Purpose

Architecture-level change plan for Thermals hotspot behavior upgrades.
Detailed implementation and maintenance guidance lives in:
`docs/HOTSPOTS/01_IMPLEMENTATION_PLAN.md`.

This file follows `CHANGE_PLAN_TEMPLATE.md` headings so non-trivial feature work is explicitly compliant.

## 0) Metadata

- Title: OGN Thermal Hotspots - Retention + Anti-Fake Turn Filter + Area Dedupe + Modal Thermals Settings
- Owner: XCPro map/OGN layer
- Date: 2026-02-24
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
Current thermal hotspot runtime does not fully enforce requested product policy.
- Why now:
Current map output can be noisy/stale and does not match the latest Thermals settings contract.
- In scope:
runtime policy wiring, settings UX contract, tests, and architecture/docs sync.
- Out of scope:
cross-session persistence, protocol parser redesign, unrelated overlays.
- User-visible impact:
cleaner/more reliable thermal hotspots and controllable retention.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Thermal hotspots | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | UI-side hotspot policy state |
| Retention setting | `OgnTrafficPreferencesRepository` | `Flow<Int>` | Direct UI-owned policy state |

### 2.2 Dependency Direction

Confirm:
`UI -> domain/usecase -> data/repository`

- Modules/files touched:
see `docs/HOTSPOTS/01_IMPLEMENTATION_PLAN.md` section 2.2.
- Any boundary risk:
mono/wall time mixing around retention/day-boundary logic.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Retention enforcement | None/UI copy only | `OgnThermalRepository` | Policy SSOT in repository | Unit tests |
| Anti-fake turn validation | None | `OgnThermalRepository` | Domain logic out of UI | Unit tests |
| Area best-of dedupe | None | `OgnThermalRepository` | Single output authority | Unit tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Thermals settings copy vs runtime | Claimed policy not enforced in runtime | Enforced in repository publish policy | Phase 2 |
| Raw hotspot publish path | No area retention/dedupe policy | Filtered + deduped publish function | Phase 3 |
| General -> Thermals route UX | Route screen, not modal contract | Modal-aligned settings host | Phase 4 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Tracker continuity/freshness | Monotonic | Stable runtime durations |
| Hour-based retention cutoff | Wall | User expectation |
| All-day cutoff | Wall + timezone | Local midnight contract |

Explicitly forbidden:

- Monotonic vs wall direct comparisons
- Replay vs wall direct comparisons

### 2.4 Threading and Cadence

- Dispatcher ownership:
repository policy on `Default`, UI collection on `Main`.
- Primary cadence/gating sensor:
OGN targets flow + repository housekeeping timer.
- Hot-path latency budget:
keep policy pass linear and bounded for dense traffic snapshots.

### 2.5 Replay Determinism

- Deterministic for same input: Yes with fixed test clocks/timezone.
- Randomness used: No.
- Replay/live divergence rules:
wall-time retention behavior must be explicitly test-controlled.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI policy leakage | AGENTS Non-Negotiables | Test + review | `OgnThermalRepositoryTest` |
| Time-base drift | AGENT 1.3 | Test + review | `FakeClock` thermal tests |
| Modal contract regressions | UX contract | UI test + review | settings/navigation tests |

## 3) Data Flow (Before -> After)

Before:
`targets -> thermal repository -> raw hotspots`

After:
`targets + retention flow -> thermal repository policy -> filtered/deduped hotspots -> VM -> map overlay`

## 4) Implementation Phases

- Goal, files, tests, and exit criteria are defined in:
`docs/HOTSPOTS/01_IMPLEMENTATION_PLAN.md` section 4.

## 5) Test Plan

- Unit:
`OgnThermalRepositoryTest`, `OgnTrafficPreferencesRepositoryTest`.
- Replay/regression:
fixed clock/timezone thermal retention tests.
- UI/instrumentation:
General -> Thermals modal behavior.
- Degraded/failure:
missing track, midnight transitions, disabled stream.
- Boundary tests for bypass removal:
ensure publish path always applies dedupe/retention.

Required checks:

```bat
./gradlew.bat enforceRules
./gradlew.bat :feature:map:testDebugUnitTest
./gradlew.bat assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Day-boundary bugs | Wrong pruning | timezone-pinned unit tests | OGN owner |
| Dedupe too aggressive | Signal loss | tune cell size + fixture validation | OGN owner |
| Navigation regressions | UX breakage | modal navigation tests + manual QA | UI owner |

## 7) Acceptance Gates

- No architecture/coding rule violations.
- No duplicate SSOT ownership.
- Explicit and tested time-base behavior.
- Deterministic replay behavior for deterministic inputs.
- `KNOWN_DEVIATIONS.md` update only if explicitly approved.

## 8) Rollback Plan

- Independent rollback slices:
modal UI changes, retention wiring, turn gate, dedupe.
- Recovery:
revert phases in reverse order and restore prior thermal baseline behavior.
