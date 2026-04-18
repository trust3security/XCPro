# CHANGE_PLAN_SYNTHETIC_THERMAL_REPLAY_VALIDATION_2026-04-06

## 0) Metadata

- Title: Deterministic synthetic thermal replay for snail-trail validation
- Owner: XCPro Team
- Date: 2026-04-06
- Issue/PR: TBD
- Status: Complete

## 1) Scope

- Problem statement:
  - `example.igc` is useful as a parser/replay smoke fixture, but it remains a normal 1 Hz flight log and does not provide a deterministic thermal-validation baseline tailored to ownship snail-trail work.
- Why now:
  - the thermalling trail fixes need a repeatable pre-flight validation path that is replay-safe, deterministic, and does not depend on ad hoc fake file formats.
- In scope:
  - deterministic in-memory thermal `IgcLog` generation
  - a debug replay entrypoint that loads that log through the existing replay controller
  - test coverage for deterministic output and replay launch wiring
  - ground-validation docs/runbook updates
- Out of scope:
  - parser support for sub-second `.igc` B-record timestamps
  - synthetic live sensor injection
  - changes to production flight-data truth or live sensor wiring

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Synthetic thermal replay points | `SyntheticThermalReplayLogBuilder` | in-memory `IgcLog` | custom file-format or UI-owned replay point caches |
| Replay session selection/state | existing `IgcReplayController` | `SessionState` | alternate replay session owner in UI |
| Map replay launch actions | `MapScreenReplayCoordinator` with focused replay launch helpers | ViewModel intents | direct Composable replay controller calls |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Synthetic thermal scenario config | `SyntheticThermalReplayLogBuilder` | builder constructor / `build(...)` input only | replay launch helpers | deterministic constants + explicit config | none | per build call | replay | builder regression tests |
| Synthetic thermal replay selection | `IgcReplayController` | replay launch helpers only | existing replay session flows | `loadLog(...)` selection | none | normal replay stop/finish cleanup | replay | coordinator wiring tests |

### 2.2 Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/replay/RacingReplayLogBuilder.kt` | deterministic in-memory replay generation | build a standard `IgcLog`, not a custom file | thermal geometry instead of straight racing legs |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt` | existing replay launch owner | use `loadLog(...)` through the replay coordinator seam | add a dedicated thermal validation path |

### 2.2B File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/replay/SyntheticThermalReplayLogBuilder.kt` | New | deterministic thermal `IgcLog` generation | replay-domain helper beside existing replay builders | not UI; not parser/file IO | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/replay/SyntheticThermalReplayLauncher.kt` | New | synthetic replay launch orchestration | focused replay launch helper for debug/dev scenarios | keeps `MapScreenReplayCoordinator` below hotspot cap | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/replay/DemoReplayLauncher.kt` | New | existing demo replay start variants | focused debug replay launcher | avoids coordinator hotspot growth | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt` | Existing | replay session/racing orchestration | canonical replay owner already lives here | should not move replay authority to UI | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt` plus existing map UI input files | Existing | debug replay button wiring only | existing debug replay lane already lives here | avoids a second debug UI surface | No |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Synthetic thermal timestamps | Replay | replay uses IGC timestamps as simulation clock |
| Ground-validation screen recordings | Wall | evidence only, outside domain logic |

Explicitly forbidden:
- replay vs wall comparisons in replay generation
- any sub-second custom `.igc` format

### 2.4 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - synthetic thermal is replay-only
  - live sensor path remains unchanged
  - sub-second feel comes from `ReplayCadenceProfile.LIVE_100MS`, not parser changes

## 3) Data Flow

`SyntheticThermalReplayLogBuilder -> IgcReplayController.loadLog(...) -> existing replay pipeline -> FlightDataRepository(REPLAY) -> MapScreen trail/render consumers`

## 4) Implementation Summary

1. Add deterministic thermal replay builder with clean and wind-noisy variants.
2. Reuse the existing map replay coordinator + `loadLog(...)` seam.
3. Surface the synthetic thermal path through the existing debug replay action lane and keep the completed thermal inspectable at replay end.
4. Update ground-validation docs/runbook so synthetic thermal replay becomes the recommended deterministic baseline before flight testing.

## 5) Test Plan

- Unit tests:
  - builder output is deterministic
  - timestamps are monotonic
  - altitude climbs from 1000 ft to 6000 ft
  - south wind implies north drift
- Replay launch tests:
  - synthetic thermal replay uses `ReplayCadenceProfile.LIVE_100MS`
  - the correct display names/variants are passed to `loadLog(...)`
  - synthetic replay completion reseeks the final frame and keeps the validation mode active until user exit/cancel
  - synthetic replay uses a replay-only retention override so the full thermal is preserved for inspection
- Required verification:
  - `scripts/qa/run_change_verification.bat -Profile slice-replay`
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenReplayCoordinatorTest" --tests "com.trust3.xcpro.map.replay.SyntheticThermalReplayLogBuilderTest"`
  - `scripts/qa/run_change_verification.bat -Profile pr-ready`

## 6) ADR / Deviation Check

- ADR required: No
- Known deviation required: No
- Reason:
  - this change reuses existing replay ownership and does not change module boundaries, parser semantics, or public runtime contracts.
