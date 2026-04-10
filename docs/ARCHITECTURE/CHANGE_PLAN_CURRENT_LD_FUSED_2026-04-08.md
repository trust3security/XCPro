# CHANGE_PLAN_CURRENT_LD_FUSED_2026-04-08

## 0) Metadata

- Title: Fused visible Current L/D rework
- Owner: Codex
- Date: 2026-04-08
- Issue/PR: N/A
- Status: Complete

## 1) Scope

- Problem statement:
  - visible `ld_curr` exposed the old raw ground metric instead of one
    operational Current L/D number for the pilot.
- Why now:
  - product explicitly decided that the visible Current L/D card must become a
    fused pilot-facing metric.
- In scope:
  - new fused `pilotCurrentLD/pilotCurrentLDValid/pilotCurrentLDSource`
  - rolling matched-window estimator
  - wind projection with zero-wind fallback
  - thermal/turn/climb hold behavior
  - active-polar support only through the still-air sink seam
  - visible `ld_curr` rewiring
- Out of scope:
  - changing raw `currentLD`
  - changing raw `currentLDAir`
  - implementing `currentVsPolar`
  - moving wind ownership or polar ownership
- User-visible impact:
  - `L/D CURR` now shows the fused pilot-facing Current L/D number.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| raw over-ground glide metric | `feature:flight-runtime` current LD owner path | `currentLD/currentLDValid` | no wind-aware rewrite in `FlightCalculationHelpers` |
| raw through-air glide metric | `feature:flight-runtime` current air LD owner path | `currentLDAir/currentLDAirValid` | no card-owned air-data math |
| visible fused pilot Current L/D | `feature:map-runtime` `PilotCurrentLdRepository` | `pilotCurrentLD/pilotCurrentLDValid/pilotCurrentLDSource` | no copy in `CompleteFlightData` |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `pilotCurrentLD` | `PilotCurrentLdRepository` | repository-local calculator only | `RealTimeFlightData` -> `ld_curr` | fused flight data + wind + direction + polar support | none | invalidated after hold expiry or no trustworthy source | replay-safe sample time | rolling math, fallback, hold |
| rolling sample window | `PilotCurrentLdCalculator` | calculator only | internal only | eligible straight-flight samples | none | reset on degraded fallback/non-eligible states | replay-safe sample time | window fill/refill tests |
| held last valid direction/value | `PilotCurrentLdCalculator` | calculator only | internal + debug source | last valid straight-flight fused output | none | expires after `20_000 ms` | replay-safe sample time | hold tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:flight-runtime`
  - `feature:map-runtime`
  - `feature:map`
  - `core:flight`
  - `dfcards-library`
- Any boundary risk:
  - avoided by keeping fused metric out of `CompleteFlightData`

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideComputationRepository.kt` | map-runtime repository combining flight, wind, and route state | map-side authoritative runtime join | fused Current L/D uses rolling estimator instead of glide solver |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` | map-side join into `RealTimeFlightData` | map-side combine and DTO exposure | adds fused Current L/D snapshot |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| visible `ld_curr` meaning | raw `currentLD` pipeline | `PilotCurrentLdRepository` + map/runtime join | product wants one fused pilot-facing Current L/D number | card rewiring and raw-metric isolation tests |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/map-runtime/.../PilotCurrentLdCalculator.kt` | New | matched-window math and fallback ladder | canonical fused metric owner | not UI and not raw flight-data SSOT | No |
| `feature/map-runtime/.../PilotCurrentLdRepository.kt` | New | authoritative runtime join and rolling state lifetime | uses flight + wind + direction seams | flight-runtime does not own route/course direction | No |
| `feature/map/.../MapScreenObservers.kt` | Existing | map-side join only | existing `RealTimeFlightData` combine owner | card layer must stay render-only | No |
| `dfcards-library/.../CardFormatSpec.kt` | Existing | rendering only | current card formatting owner | no metric math allowed here | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `PilotCurrentLdSnapshot` | `feature:map-runtime` | `feature:map` | public cross-module | expose fused metric to map/UI combine path | keep as long-lived runtime seam |
| `pilotCurrentLD*` fields on `RealTimeFlightData` | `core:flight` | `feature:map`, `dfcards-library` | public cross-module | visible card needs fused fields | retain alongside raw metrics |
| `isTurning` on runtime/flight DTOs | `feature:flight-runtime` | `feature:map-runtime` | public cross-module | thermal/turn hold gating | retain |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| rolling window samples | Replay-safe sample time | deterministic live/replay behavior |
| hold timeout | Replay-safe sample time | deterministic thermal hold behavior |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - same rolling window/hold semantics use sample time in both paths

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| wind unavailable/stale/low-confidence | Degraded | `PilotCurrentLdCalculator` | valid Current L/D with zero-wind behavior | `windAlong = 0` | zero-wind fallback tests |
| no reliable direction | Degraded | `PilotCurrentLdCalculator` | valid Current L/D with zero-wind behavior | skip wind projection | no-direction tests |
| TE missing briefly | Degraded | `PilotCurrentLdCalculator` | valid Current L/D | bounded `POLAR_FILL` | TE-gap tests |
| circling/turning/climbing | Degraded | `PilotCurrentLdCalculator` | hold then no data | hold for `20_000 ms` | hold/resume tests |
| no trustworthy source after hold | Unavailable | `PilotCurrentLdCalculator` | `--:1` | none | expiry tests |

## 3) Data Flow (Before -> After)

Before:

`FlightCalculationHelpers -> currentLD/currentLDValid -> CompleteFlightData -> RealTimeFlightData -> ld_curr`

After:

`FlightCalculationHelpers -> currentLD/currentLDValid` remains raw/internal

`CurrentAirLdCalculator -> currentLDAir/currentLDAirValid` remains raw/internal

`FlightDataRepository + WindSensorFusionRepository + WaypointNavigationRepository + StillAirSinkProvider -> PilotCurrentLdRepository -> PilotCurrentLdSnapshot -> RealTimeFlightData -> ld_curr`

## 4) Implementation Phases

### Phase 0 — lock contract
- Goal:
  - freeze fused visible-card semantics and fallback ladder
- Exit criteria:
  - owner and time-base decisions documented

### Phase 1 — add fused owner
- Goal:
  - add `PilotCurrentLdCalculator` and `PilotCurrentLdRepository`
- Exit criteria:
  - fused metric publishes independently of cards

### Phase 2 — direction, wind, hold policy
- Goal:
  - implement rolling matched window, direction priority, zero-wind fallback,
    and thermal-safe hold behavior
- Exit criteria:
  - circling geometry never enters the estimator

### Phase 3 — polar support and visible card rewiring
- Goal:
  - use active polar for plausibility and short-gap support only
  - rewire visible `ld_curr` to fused metric
- Exit criteria:
  - raw metrics remain available and unmodified

### Phase 4 — verification and docs sync
- Goal:
  - add tests, update docs, run required gates
- Exit criteria:
  - `enforceRules`, `testDebugUnitTest`, and `assembleDebug` pass

## 5) Test Plan

- Unit tests:
  - rolling-window integration
  - min-fill and full-window behavior
  - wind projection effect
  - zero-wind fallback
  - no-direction fallback
  - circling/turning/climbing hold and expiry
  - resume after thermal
  - ground fallback
  - bounded polar fill
  - setup-awareness only through polar support path
- Replay/regression tests:
  - replay-safe time usage through deterministic sample timestamps
- UI/instrumentation tests:
  - not required for this slice
- Boundary tests for removed bypasses:
  - `ld_curr` uses fused fields while raw metrics remain mapped

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| overly strict gating reduces availability | pilot sees more `--:1` than expected | keep zero-wind fallback and bounded polar support | fused calculator owner |
| accidental drift back into raw helper | semantics confusion | ADR + pipeline docs + isolation tests | maintainers |
| replay/timing drift | non-deterministic output | sample-time-only buffer/hold logic | fused calculator owner |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file:
  - `docs/ARCHITECTURE/ADR_CURRENT_LD_PILOT_FUSED_METRIC_2026-04-08.md`
- Decision summary:
  - visible `ld_curr` is now the fused pilot-facing Current L/D metric
- Why this belongs in an ADR instead of plan notes:
  - it changes durable product semantics, owner path, and cross-module API

## 7) Acceptance Gates

- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Raw `currentLD` and `currentLDAir` remain available and unchanged in meaning
- Visible `ld_curr` shows fused pilot Current L/D
- `ld_vario` remains unchanged

## 8) Rollback Plan

- What can be reverted independently:
  - visible card rewiring
  - map-runtime fused repository wiring
- Recovery steps if regression is detected:
  - restore `ld_curr` formatting to raw `currentLD/currentLDValid`
  - leave raw metrics intact
