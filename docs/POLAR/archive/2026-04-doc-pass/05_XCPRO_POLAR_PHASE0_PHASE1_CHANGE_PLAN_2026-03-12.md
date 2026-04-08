# XCPro Polar Phase 0-1 Change Plan

## 0) Metadata

- Title: Polar Metric Foundation and Default Card Surface Cleanup
- Owner: XCPro maintainers (agent-authored)
- Date: 2026-03-12
- Issue/PR: TBD
- Status: Complete

## 1) Scope

- Problem statement:
  - XCPro exposes measured `currentLD` but not theoretical polar L/D or best L/D.
  - Default card presets still center placeholder-only `final_gld`, `wpt_*`, and `task_*` cards.
- Why now:
  - This is the lowest-risk slice that improves pilot-facing polar outputs without crossing into target-aware final glide.
- In scope:
  - derive flight-only polar metrics from the active polar
  - propagate those metrics through `FlightMetricsResult -> CompleteFlightData -> RealTimeFlightData`
  - add live `polar_ld` and `best_ld` cards
  - clean shipped default templates so they prefer live cards
- Out of scope:
  - target-aware final glide
  - task-aware cards
  - new target SSOT
  - `final_gld` implementation
- User-visible impact:
  - pilots can see measured L/D, polar L/D, and best L/D separately
  - shipped presets stop emphasizing dead placeholder cards

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active glider model/config | `feature/profile/.../GliderRepository.kt` | existing flows | UI-local polar copies |
| Derived flight-only polar metrics | new `GlidePolarProvider` in `feature/map/.../glider` | pure functions / cached snapshot | card-side recomputation |
| Measured L/D | existing flight runtime | `currentLD` | reinterpretation as polar or required L/D |
| Card-facing polar metrics | `CompleteFlightData` then `RealTimeFlightData` adapter path | immutable snapshot fields | formatter-time sink lookups |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/.../glider/*`
  - `feature/map/.../sensors/domain/*`
  - `feature/map/.../flightdata/*`
  - `feature/map/.../MapScreenUtils.kt`
  - `dfcards-library/.../*`
- Any boundary risk:
  - low, provided cards remain formatting-only

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Polar L/D derivation | effectively missing | `GlidePolarProvider` | one consistent domain source | unit tests |
| Preset selection of dead cards | `FlightTemplates.kt` | still `FlightTemplates.kt`, but cleaned | remove fake shipping surface | template tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Default templates with `final_gld` and task placeholders | preset implies unsupported functionality | live polar-oriented presets | current change |
| Future card-side polar math | not yet present | adapter-fed metric fields | current change |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Polar L/D at current speed | same flight sample cadence as runtime metrics | stays replay-safe and sample-consistent |
| Best L/D | state-based on active model/config | deterministic for same active polar |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - same runtime compute path as existing flight metrics
- Primary cadence/gating sensor:
  - existing `CompleteFlightData` emission cadence
- Hot-path latency budget:
  - derived metrics must remain lightweight; cache best-L/D over active model/config

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - same active polar plus same input speed yields same L/D metrics

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| `currentLD` semantics drift | SSOT semantics | unit + review | mapper/card tests |
| Card-side business logic creep | layering rules | review + formatter tests | `CardFormatSpecTest.kt` |
| Best-L/D derivation regressions | domain correctness | unit tests | new `GlidePolarProviderTest.kt` |
| Placeholder presets still ship | user-facing correctness | unit tests | `FlightTemplatesDefaultsTest.kt` |

## 3) Data Flow (Before -> After)

Before:

`GliderRepository -> StillAirSinkProvider -> runtime metrics(currentLD only for L/D) -> CompleteFlightData -> RealTimeFlightData -> cards`

After:

`GliderRepository -> GlidePolarProvider -> runtime metrics(currentLD + polarLdCurrentSpeed + polarBestLd) -> CompleteFlightData -> RealTimeFlightData -> cards`

## 4) Implementation Phases

### Phase 0 - Surface Cleanup

- Goal:
  - stop shipping presets centered on unsupported final-glide/task cards
- Files to change:
  - `dfcards-library/.../FlightTemplates.kt`
- Tests to add/update:
  - `FlightTemplatesDefaultsTest.kt`
- Exit criteria:
  - built-in templates use live cards only for glide/performance presets

### Phase 1 - Polar Metric Foundation

- Goal:
  - add derived `polarLdCurrentSpeed` and `polarBestLd`
- Files to change:
  - `feature/map/.../glider/*`
  - `feature/map/.../sensors/domain/*`
  - `feature/map/.../flightdata/*`
  - `feature/map/.../MapScreenUtils.kt`
  - `dfcards-library/.../*`
- Tests to add/update:
  - glide provider tests
  - runtime mapping tests
  - card formatting tests
- Exit criteria:
  - live pipeline exposes measured L/D, polar L/D, and best L/D separately

## 5) Test Plan

- Unit tests:
  - best-L/D derivation from active polar
  - L/D at current speed
  - adapter mapping into `RealTimeFlightData`
  - `polar_ld` and `best_ld` card formatting
  - default template cleanup
- Replay/regression tests:
  - covered by deterministic flight-metric tests for same input
- UI/instrumentation tests:
  - not required for this slice
- Degraded/failure-mode tests:
  - no polar available yields invalid polar L/D cards

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Best-L/D sampling is inconsistent | wrong card values | centralize in provider and unit test representative polars | maintainers |
| New card IDs break catalog coverage | build/test failures | update ID/catalog/spec coverage tests together | maintainers |
| Preset changes surprise users | mild UX change | keep preset count stable, change only contents/names toward live metrics | maintainers |

## 7) Acceptance Gates

- No duplicate SSOT ownership introduced
- No polar math in cards or Compose
- `currentLD` remains measured glide ratio
- Required verification passes

## 8) Rollback Plan

- What can be reverted independently:
  - template cleanup
  - new polar metrics
  - new cards
- Recovery steps if regression is detected:
  - remove `polar_ld` and `best_ld` from presets first
  - keep existing `ld_curr`, `netto`, `levo_netto`, and `mc_speed`
