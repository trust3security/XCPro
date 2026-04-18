# CHANGE_PLAN_CURRENT_LD_THERMAL_STATE_POLISH_2026-04-09

## 0) Metadata

- Title: Visible Current L/D thermalling-state polish
- Owner: Codex
- Date: 2026-04-09
- Issue/PR: N/A
- Status: Draft

## 1) Scope

- Problem statement:
  - the visible fused `ld_curr` card now computes the right pilot-facing metric,
    but its secondary label still shows `LIVE` whenever `pilotCurrentLDValid` is
    true and `NO DATA` otherwise.
  - during thermalling/circling/turning, that subtitle is misleading because the
    value may be held rather than freshly updated, and after hold expiry the
    pilot loses the reason for no-data.
- Why now:
  - the product goal is pilot clarity, not raw engineering exposure.
  - the highest-value next step is to make the visible card explain thermal
    behavior clearly before revisiting the old coarse raw helper.
- In scope:
  - visible `ld_curr` subtitle behavior only
  - show `THERMAL` while the fused Current L/D is being held through
    thermalling/circling/turning
  - show `THERMAL` with `--:1` after hold expiry if thermalling/circling/turning
    still persists
  - expose any missing authoritative flight-state flag needed by the card
    formatting seam
  - keep the existing fused estimator, hold timeout, and fallback ladder
    unchanged
- Out of scope:
  - changing raw `currentLD/currentLDValid`
  - changing raw `currentLDAir/currentLDAirValid`
  - changing `pilotCurrentLD` math, timebase, or fallback priorities
  - revisiting the old coarse GPS/baro helper as the primary pilot-improvement
    focus
  - changing wind, active-polar, bugs, or ballast ownership
  - implementing `currentVsPolar`
  - changing shipped templates
- User-visible impact:
  - `L/D CURR` will show `THERMAL` instead of `LIVE`/generic `NO DATA` while the
    glider is thermalling/circling/turning and the fused Current L/D is being
    held or timed out
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| fused Current L/D value | `feature:map-runtime` `PilotCurrentLdRepository` | `pilotCurrentLD/pilotCurrentLDValid/pilotCurrentLDSource` | no second calculator in cards/UI |
| circling flag | `feature:flight-runtime` flight metrics owner path | `CompleteFlightData.isCircling` -> `RealTimeFlightData.isCircling` | no UI-side circling heuristics |
| turning flag | `feature:flight-runtime` flight metrics owner path | `CompleteFlightData.isTurning` -> `RealTimeFlightData.isTurning` | no UI-side turn detection heuristics |
| pilot-facing subtitle string | `dfcards-library` formatter layer | display-only `THERMAL` / `LIVE` / `NO DATA` label | no business-state mirror in UI |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `pilotCurrentLD` | `PilotCurrentLdRepository` | repository-local calculator only | `RealTimeFlightData` -> `ld_curr` | existing fused rolling estimator | none | existing hold/timeout contract | replay-safe sample time | existing Current L/D fused tests |
| `pilotCurrentLDSource` | `PilotCurrentLdRepository` | repository-local calculator only | `RealTimeFlightData` -> formatter | existing fused source ladder | none | existing hold/timeout contract | replay-safe sample time | source mapping and formatter tests |
| `isTurning` on card DTO path | `feature:flight-runtime` metrics path | existing runtime metrics mutators only | `CompleteFlightData` -> `MapScreenUtils` -> `RealTimeFlightData` | circling detector output | none | per-sample runtime state | replay-safe sample time | DTO mapping tests |
| `ld_curr` secondary label | `CardFormatSpec` | formatter only | card render output | `pilotCurrentLDValid`, `pilotCurrentLDSource`, `isCircling`, `isTurning` | none | per render | N/A | formatter tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:flight-runtime`
  - `feature:map`
  - `core:flight`
  - `dfcards-library`
- Any boundary risk:
  - low if this remains a display-only formatting change using existing
    authoritative flags
  - medium if formatter starts inferring thermalling from ad hoc value patterns

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt` | existing owner for card value/subtitle formatting | display-only mapping from authoritative DTO fields to user-visible secondary labels | add `THERMAL` mapping for `ld_curr` |
| `feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt` | existing owner for `CompleteFlightData` -> `RealTimeFlightData` field exposure | pass through authoritative runtime flags without adding business logic | expose `isTurning` if needed |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/currentld/PilotCurrentLdModels.kt` | current fused source/status seam already exists | reuse `pilotCurrentLDSource` instead of inventing a second Current L/D state model | none |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| turning-state availability for card formatting | `CompleteFlightData` only | `RealTimeFlightData` also carries `isTurning` | card formatter needs authoritative turning context | DTO mapping tests |
| visible `ld_curr` subtitle semantics during thermal hold | generic `LIVE` / `NO DATA` branch in formatter | explicit display-only `THERMAL` mapping in formatter | improve pilot clarity without changing metric ownership | card formatter tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `CardFormatSpec` `LD_CURR` secondary label | generic `strings.live` / `strings.noData` regardless of held thermal state | formatter branch using authoritative `pilotCurrentLDSource` plus `isCircling/isTurning` | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `core/flight/src/main/java/com/trust3/xcpro/core/flight/RealTimeFlightData.kt` | Existing | UI-safe cross-module DTO fields | existing card data contract owner | card layer must not read `CompleteFlightData` directly | No |
| `feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt` | Existing | `CompleteFlightData` -> `RealTimeFlightData` mapping | existing map-side DTO projection seam | no business math; simple pass-through only | No |
| `dfcards-library/src/main/java/com/example/dfcards/CardStrings.kt` | Existing | card label contract | canonical place for formatter labels | avoid hard-coded literals in formatter/tests | No |
| `dfcards-library/src/main/java/com/example/dfcards/AndroidCardStrings.kt` | Existing | Android resource-backed card labels | existing Android string binding owner | formatter must stay Android-agnostic | No |
| `dfcards-library/src/main/res/values/strings.xml` | Existing | localized resource value for `THERMAL` label | Android resource source of truth | do not bury strings in Kotlin | No |
| `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt` | Existing | display-only value/subtitle formatting | current `ld_curr` render owner | metric logic must remain upstream | No |
| `feature/map/src/test/java/com/trust3/xcpro/ConvertToRealTimeFlightDataTest.kt` | Existing | DTO projection regression coverage | existing mapping test seam | keep mapper proof local | No |
| `dfcards-library/src/test/java/com/example/dfcards/CardFormatSpecTest.kt` | Existing | card subtitle/value regression coverage | existing formatter regression seam | no UI instrumentation needed for this slice | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `RealTimeFlightData.isTurning` | `core:flight` | `feature:map`, `dfcards-library` | public cross-module DTO field | visible `ld_curr` subtitle needs authoritative turning context | retain as long-lived DTO field once added |
| `CardStrings.thermal` | `dfcards-library` | formatter + tests + Android resource adapter | public library contract | avoid hard-coded subtitle text | long-lived label contract |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| held-vs-fresh Current L/D determination | existing replay-safe sample time | owned by existing fused calculator, unchanged in this slice |
| `THERMAL` subtitle display state | current sample DTO state | formatter only consumes already-derived state |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added in this slice
  - subtitle behavior must remain a pure function of replay-safe DTO state

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| `pilotCurrentLDValid == true` and source is held while circling/turning | Degraded | `CardFormatSpec` display mapping over authoritative state | show held value with `THERMAL` subtitle | none beyond existing fused hold | formatter test |
| `pilotCurrentLDValid == false` while circling/turning after hold expiry | Unavailable | `CardFormatSpec` display mapping over authoritative state | show `--:1` with `THERMAL` subtitle | wait for fresh glide context to resume upstream | formatter test |
| invalid outside thermalling/turning | Unavailable | existing formatter path | show `--:1` with `NO DATA` | unchanged | regression test |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| card formatter starts inferring thermal state from ad hoc numeric heuristics | `ARCHITECTURE.md` responsibility matrix; `CODING_RULES.md` UI/formatter constraints | review + formatter tests | `CardFormatSpec.kt`, `CardFormatSpecTest.kt` |
| turning state stays hidden from card DTO and formatter guesses wrong | SSOT and explicit state contract rules | mapping test | `ConvertToRealTimeFlightDataTest.kt` |
| subtitle change accidentally alters `ld_vario` or other cards | layering/ownership isolation | formatter regression tests | `CardFormatSpecTest.kt` |

## 3) Data Flow (Before -> After)

Before:

`CompleteFlightData.isCircling + pilotCurrentLD* -> RealTimeFlightData (without isTurning) -> CardFormatSpec LD_CURR -> value + LIVE/NO DATA`

After:

`CompleteFlightData.isCircling/isTurning + pilotCurrentLD* -> RealTimeFlightData -> CardFormatSpec LD_CURR -> value + THERMAL/LIVE/NO DATA`

Where:

- `THERMAL` is display-only
- fused Current L/D ownership and math remain unchanged upstream

## 4) Implementation Phases

### Phase 0 - Lock product contract

- Goal:
  - freeze the pilot-facing subtitle rules before coding
- Files to change:
  - this plan
  - CurrentLD README/doc pointers if needed
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none
- Exit criteria:
  - exact `THERMAL` display rules are documented

### Phase 1 - Expose authoritative turn context to card DTOs

- Goal:
  - make `RealTimeFlightData` carry any missing runtime flag the formatter
    needs
- Files to change:
  - `RealTimeFlightData.kt`
  - `MapScreenUtils.kt`
  - mapping tests
- Ownership/file split changes in this phase:
  - none; pass-through only
- Tests to add/update:
  - DTO mapping coverage for `isTurning`
- Exit criteria:
  - card layer can distinguish circling/turning without local heuristics

### Phase 2 - Add `THERMAL` card-label support

- Goal:
  - add a dedicated `THERMAL` string seam for card formatting
- Files to change:
  - `CardStrings.kt`
  - `AndroidCardStrings.kt`
  - `dfcards-library/src/main/res/values/strings.xml`
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - string-backed formatter tests
- Exit criteria:
  - formatter can emit `THERMAL` without hard-coded literals

### Phase 3 - Rewire `ld_curr` subtitle logic

- Goal:
  - show `THERMAL` when the glider is thermalling/circling/turning and the
    visible Current L/D is held or unavailable because the glide context is gone
- Files to change:
  - `CardFormatSpec.kt`
  - `CardFormatSpecTest.kt`
- Ownership/file split changes in this phase:
  - none; display-only formatting remains in `dfcards-library`
- Tests to add/update:
  - valid held thermal case -> held value + `THERMAL`
  - invalid thermal timeout case -> `--:1` + `THERMAL`
  - valid straight glide case -> value + `LIVE`
  - invalid non-thermal case -> `--:1` + `NO DATA`
  - `ld_vario` unchanged
- Exit criteria:
  - `ld_curr` subtitle is pilot-correct without changing metric ownership

### Phase 4 - Verification and docs sync

- Goal:
  - run the normal release-grade verification bundle for the narrow slice
- Files to change:
  - `README.md` in CurrentLD docs if needed
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none beyond prior phases
- Exit criteria:
  - `enforceRules`, `testDebugUnitTest`, and `assembleDebug` pass

## 5) Test Plan

- Unit tests:
  - `ld_curr` shows held numeric value with `THERMAL` while circling/turning and
    fused source is held
  - `ld_curr` shows `--:1` with `THERMAL` when circling/turning persists after
    hold expiry
  - `ld_curr` shows `LIVE` in normal valid glide
  - `ld_curr` shows `NO DATA` when invalid outside thermalling/turning
  - `ld_vario` remains unchanged
- Replay/regression tests:
  - existing fused Current L/D replay-safe timing remains unchanged; no new
    replay logic required
- UI/instrumentation tests (if needed):
  - not required unless formatter wiring unexpectedly moves into screen code
- Degraded/failure-mode tests:
  - thermalling no-data vs generic no-data distinction
- Boundary tests for removed bypasses:
  - none
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Formatter regression tests | `CardFormatSpecTest.kt` |
| Time-base / replay / cadence | prove no new time logic added | code review + existing fused estimator tests unchanged |
| Ownership move / bypass removal / API boundary | DTO projection test | `ConvertToRealTimeFlightDataTest.kt` |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| formatter shows `THERMAL` too broadly | misleading pilot subtitle | gate on authoritative circling/turning state plus existing fused held/invalid conditions | formatter implementation |
| formatter still cannot tell turning vs generic no-data | subtitle remains wrong in some turns | expose `isTurning` to `RealTimeFlightData` instead of guessing | DTO mapping owner |
| scope expands into estimator rewrite | unnecessary churn and regression risk | keep math/fallback ladder explicitly out of scope | maintainers |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  - this is a narrow pilot-UX refinement on top of an already-accepted fused
    Current L/D architecture
- Why this does not belong in a new ADR:
  - it does not change the authoritative owner, cross-module boundary shape, or
    durable metric semantics

## 7) Acceptance Gates

- visible `ld_curr` subtitle shows `THERMAL` while thermalling/circling/turning
  instead of misleading `LIVE`
- visible `ld_curr` shows `--:1` plus `THERMAL` after hold expiry if thermalling
  persists
- straight-glide behavior remains unchanged
- raw `currentLD`, raw `currentLDAir`, and fused `pilotCurrentLD` meanings remain
  unchanged
- no new metric math is added to card/UI layers
- no ad hoc thermal heuristics are introduced in the formatter
- `KNOWN_DEVIATIONS.md` remains unchanged unless a new exception is explicitly
  approved

## 8) Rollback Plan

- What can be reverted independently:
  - `THERMAL` label resources and formatter mapping
  - `RealTimeFlightData.isTurning` exposure if it proves unnecessary
- Recovery steps if regression is detected:
  - restore the previous generic `LIVE` / `NO DATA` subtitle behavior for
    `ld_curr`
  - keep the fused metric owner and raw metrics unchanged
