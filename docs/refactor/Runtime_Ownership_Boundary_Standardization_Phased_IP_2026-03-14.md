# Runtime Ownership Boundary Standardization Phased IP

## 0) Metadata

- Title: Standardize runtime ownership boundaries without repo-wide churn
- Owner: XCPro Team
- Date: 2026-03-14
- Issue/PR: TBD
- Status: Complete
- Execution rules:
  - This is a boundary-hardening track, not a cleanup track.
  - No repo-wide churn, rename-only moves, or speculative abstractions.
  - Land one seam at a time with behavior parity and rollback.
  - Favor narrow contract changes at runtime boundaries over broad internal rewrites.
  - Do not mix this plan with identity/time-generation cleanup, canonical math consolidation, or logging cleanup; those are separate tracks.
- Progress:
  - Phase 0 complete: contract locked and scoped seams recorded.
  - Phase 1 complete: traffic selection now exposes read-only selection state plus named mutation methods.
  - Phase 2A complete: wind sensor input adaptation is transform-only and no longer owns a long-lived scope.
  - Phase 2B complete: wind override source now exposes `Flow`, allowing the repository to drop its hidden runtime scope while remaining the explicit persistence owner.
  - Phase 3 complete: replay controller runtime now owns the resettable replay runtime handle, making replay scope and fusion-repository lifetime explicit at the owner boundary.
  - Phase 4 complete: ADS-B emergency audio now uses immutable bootstrap config while the repository runtime remains the only live rollout owner.

## 1) Scope

- Problem statement:
  - Several representative runtime helpers still blur ownership boundaries by exposing writable runtime state publicly, creating long-lived scopes internally, or mirroring live rollout state through mutable process-wide holders.
  - The most concrete current examples are:
    - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficSelectionRuntime.kt`
    - `feature/profile/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorInputAdapter.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayPipeline.kt`
    - `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbEmergencyAudioFeatureFlags.kt`
  - The result is not necessarily broken behavior today, but it weakens reviewability, makes scope lifetime harder to reason about, and increases the chance of ad hoc fixes or duplicated ownership over time.
- Why now:
  - This is the highest-leverage architecture hardening move available without broad churn.
  - One standard runtime pattern will fix multiple review issues at once: public mutable state leakage, unclear cancellation ownership, and implicit runtime authority.
  - The first seam is small enough to prove the pattern before touching more sensitive runtime paths.
- In scope:
  - Define one standard runtime ownership pattern for long-lived runtime helpers.
  - Apply that pattern incrementally to the scoped seams in this plan.
  - Keep each phase behavior-neutral unless a separate approved plan says otherwise.
- Out of scope:
  - Repo-wide cleanup of all runtime helpers.
  - Logging standardization, canonical formula ownership, and hidden time/ID generation fixes.
  - Module moves, UI redesign, or behavior changes.
  - Broad line-budget cleanup unrelated to runtime ownership.
- User-visible impact:
  - None intended.
  - The goal is ownership clarity, smaller review surface, and safer future refactors.

## 2) Target Runtime Ownership Standard

### 2.1 Standard Contract

| Concern | Required owner shape | Required exposure | Forbidden pattern |
|---|---|---|---|
| Long-lived runtime state | explicit runtime owner (`ViewModel`, service, repository/runtime owner, or named coordinator) | read-only `StateFlow` / read-only port + named intent methods | public `MutableStateFlow` or public writable mutable fields |
| Mutation rights | one named owner or mutation entrypoint | `set...`, `update...`, or command method on the owner/port | arbitrary external `.value = ...` writes |
| Long-lived scope | top-level owner or explicitly documented runtime owner | injected or caller-owned scope whenever practical | helper/adapter silently creating or recreating its own scope |
| Derived runtime state | recomputable from authoritative owner state | read-only derived flow/value | second hidden mutable source of truth |
| Feature rollout/runtime flags | one authoritative owner | immutable snapshot or read-only state from the owner | mutable process-wide holder that becomes the de facto runtime authority |

Required rules for the scoped phases:
- Private mutable, public read-only.
- Caller-owned or explicitly owner-owned scope; never ambiguous scope ownership.
- Mutation must happen through named methods or owner ports.
- Helper/adaptor code should transform inputs, not become an undeclared lifecycle host.
- Scope recreation inside a helper is forbidden unless that helper is itself the explicit lifecycle owner.

### 2.2 Reference Pattern Check

Use the existing traffic port seam as the baseline pattern:

- `feature/map/src/main/java/com/trust3/xcpro/map/MapTrafficCoordinatorAdapters.kt`
  - exposes `StateFlow` to consumers
  - keeps writable state behind intent methods
  - makes mutation rights explicit without leaking writable flow types

Use `viewModelScope`-owned flows in screen/runtime owners as the preferred scope model when the owner is a `ViewModel`.

### 2.3 Generic Data Flow

Before:

```text
top-level owner
  -> helper/runtime class
    -> helper creates or resets its own scope
    -> helper exposes MutableStateFlow or mutable runtime holder
    -> external callers mutate helper state directly
```

After:

```text
top-level owner
  -> explicit runtime port/controller
    -> owner creates or passes the scope
    -> runtime port exposes StateFlow / read-only outputs
    -> mutation happens through named methods only
```

### 2.4 Prioritized Seam Inventory

| Seam | Current issue | Target contract | Initial phase |
|---|---|---|---|
| Traffic selection runtime | public `MutableStateFlow` in a public runtime contract | read-only selection port plus explicit `setSelected...` methods | Phase 1 |
| Wind runtime adaptation | adapter/repository path creates long-lived scopes and eager forever-collectors internally | one named wind runtime owner; adapters stay transform-only or clearly owner-scoped | Phase 2A-2B |
| Replay runtime pipeline | replay helper owns resettable scope and mutable runtime repository fields internally | explicit replay runtime owner with clear scope lifetime and reset path | Phase 3 |
| ADS-B emergency rollout mirror | mutable feature-flag holder becomes a live runtime state mirror | one authoritative owner surface with read-only exposure or explicit snapshot updates | Phase 4 |

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data / responsibility | Owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| Traffic target selection state | traffic selection runtime owner | read-only selection state + intent methods | public writable selection flows |
| Wind runtime transformation/collection lifetime | one named wind runtime owner | owner-managed scope and read-only outputs | adapter-owned hidden scope |
| Replay fusion runtime lifetime | one replay runtime owner | explicit replay lifecycle contract | hidden scope resets in helper internals |
| ADS-B emergency rollout state | one traffic runtime owner | read-only runtime snapshot/port | duplicate mutable flag mirrors as de facto authority |

### 3.2 Dependency Direction

Confirm dependency flow remains:

`top-level owner -> runtime port/controller -> repositories/adapters/domain`

Boundary rules for this plan:
- Do not move business logic into UI to "simplify" ownership.
- Do not introduce generic mega-facades.
- Do not widen visibility to compensate for unclear boundaries.
- Do not create new modules for this plan.

### 3.3 Time Base

This is ownership work, not a timebase redesign.

Rules:
- No new direct wall/system time usage may be introduced while executing this plan.
- Replay paths must remain deterministic for the same input.

### 3.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged unless a phase explicitly documents a safer owner
- Scope ownership:
  - must become more explicit after each phase, never less explicit
- Hot-path budget:
  - no new background loop or eager collector should be introduced unless it replaces a worse existing owner path

### 3.5 Enforcement Coverage

| Risk | Rule reference | Guard type | File/test |
|---|---|---|---|
| Public mutable runtime state leaks | `ARCHITECTURE.md` authoritative state contract | code review + targeted tests + grep | scoped runtime files |
| Helper-owned scope lifetime remains implicit | `ARCHITECTURE.md` scope ownership and lifetime | code review + targeted tests + grep | scoped runtime files |
| Replay lifecycle regresses while boundaries are narrowed | replay determinism rules | targeted unit/integration tests | replay runtime tests |
| Runtime flag ownership becomes more fragmented | SSOT ownership rules | targeted tests + review | ADS-B rollout/runtime tests |

## 4) Implementation Phases

### Phase 0 - Contract lock and seam baseline

- Goal:
  - Freeze the runtime ownership standard before code refactors begin.
  - Confirm the first scoped seams and keep this work separate from unrelated cleanup.
- Files to change:
  - plan/deviation docs only
- Tests to add/update:
  - none
- Exit criteria:
  - the target runtime standard is documented
  - the scoped seams are listed
  - no extra files are pulled into the track without explicit plan updates

### Phase 1 - Traffic selection runtime boundary

- Goal:
  - Convert traffic selection from a public writable-flow contract into a read-only runtime port with explicit mutation methods.
- Files to change:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficSelectionRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapTrafficCoordinatorAdapters.kt` if contract reuse helps
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
  - targeted traffic/map tests
- Tests to add/update:
  - selection-state tests that lock mutation through named methods only
  - regression tests for selected ADS-B / OGN / thermal lookup behavior
- Exit criteria:
  - no public `MutableStateFlow` remains in the traffic selection seam
  - callers cannot mutate selection state except through named methods
  - behavior parity is preserved

### Phase 2A - Wind input adapter boundary

- Goal:
  - Remove hidden long-lived scope ownership from wind sensor input adaptation.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorInputAdapter.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorInputs.kt`
  - any narrow owner/wiring files required by the chosen boundary
- Tests to add/update:
  - targeted wind runtime tests covering collection start/stop ownership
  - regression tests for wind sample adaptation behavior
- Exit criteria:
  - adapter-style code no longer owns hidden lifetime without justification
  - the shared wind input contract is read-only and does not imply ownership of hot state
  - behavior parity is preserved

### Phase 2B - Wind override repository boundary

- Goal:
  - Remove hidden long-lived scope ownership from the wind override source while keeping the repository as the explicit persistence owner.
- Files to change:
  - `feature/profile/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideSource.kt`
  - `feature/profile/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt`
  - any narrow consumers/tests affected by the contract change
- Tests to add/update:
  - targeted wind runtime tests covering override propagation
  - profile snapshot/restore tests if the contract type change affects them
- Exit criteria:
  - one named owner is responsible for persisted and external override state
  - the repository no longer creates a hidden long-lived scope only to expose read access
  - no new duplicate wind state authority is introduced

### Phase 3 - Replay runtime boundary

- Goal:
  - Make replay lifecycle ownership explicit and remove hidden scope reset behavior from helper internals.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/replay/ReplayPipeline.kt`
  - replay runtime wiring/tests touched by the boundary move
- Tests to add/update:
  - replay determinism regression tests
  - replay lifecycle tests covering start, reset, resume, and owner teardown
- Exit criteria:
  - replay scope ownership is explicit
  - replay runtime reset path is visible at the owner boundary
  - same replay input still produces deterministic output
- Completion note:
  - Completed 2026-03-14 by moving the resettable replay runtime handle into `IgcReplayControllerRuntime`, leaving `ReplayPipeline` as replay-specific wiring instead of the hidden lifecycle owner.
  - Locked with `ReplayPipelineOwnershipTest` plus the existing replay unit-test slice.

### Phase 4 - Remaining runtime flag/state stragglers

- Goal:
  - Clean up the remaining scoped runtime-holder patterns that still weaken ownership clarity after Phases 1-3.
- Files to change:
  - `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbEmergencyAudioFeatureFlags.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeLoop.kt`
  - any small supporting runtime-owner files needed to narrow that seam
- Tests to add/update:
  - ADS-B emergency rollout/runtime tests locking the new ownership path
- Exit criteria:
  - rollout/runtime state has one clear owner path
  - mutable flag mirrors are either removed, narrowed, or explicitly justified
  - remaining scoped exceptions are either resolved or renewed deliberately
- Completion note:
  - Completed 2026-03-14 by turning `AdsbEmergencyAudioFeatureFlags` into immutable bootstrap config and keeping live rollout state inside `AdsbTrafficRepositoryRuntime`.
  - Locked with the ADS-B emergency rollout/output/lifecycle tests plus an ownership test proving rollout overrides the bootstrap seed without mutating it.

## 5) Verification

Minimum validation for each code phase:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Useful grep checks while executing this plan:

```text
rg -n "val\\s+\\w+:\\s+MutableStateFlow<" app core feature dfcards-library
rg -n "private val scope = CoroutineScope|var scope: CoroutineScope = createScope\\(" app core feature dfcards-library
rg -n "stateIn\\(scope, SharingStarted\\.Eagerly" app core feature dfcards-library
```

Phase-specific evidence:
- Phase 1: traffic selection regression coverage
- Phase 2: wind runtime ownership tests
- Phase 3: replay determinism/lifecycle tests
- Phase 4: ADS-B rollout/runtime ownership tests

## 6) Done Definition

This plan is complete when:
- all scoped seams expose read-only runtime state or explicit owner ports
- long-lived scopes in scoped seams have one named owner and visible teardown path
- no scoped seam relies on public writable runtime state as its primary contract
- no duplicate authoritative runtime owner is introduced
- this plan and its linked deviation are removed or marked resolved because the scoped files comply
