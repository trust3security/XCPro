# Terrain Elevation Ownership Release-Grade Phased IP

## 0) Metadata

- Title: Unify terrain and elevation ownership behind one shared professional boundary
- Owner: XCPro Team
- Date: 2026-03-16
- Issue/PR: RULES-20260316-18
- Status: Approved
- Execution rules:
  - This is an ownership and boundary-correction track, not an AGL algorithm rewrite.
  - Do not change pilot-facing AGL/QNH math or product thresholds unless a seam fix proves a real bug.
  - Do not keep two terrain provider paths alive after Phase 4; temporary shims must have removal triggers.
  - Keep replay deterministic: replay must not trigger online terrain fetches or non-deterministic cache population.
  - Do not create a new dedicated terrain module unless the shared-port phase proves the existing graph cannot host the seam cleanly.
  - Logging cleanup in touched terrain files is required, but do not broaden this into the general logging program.
  - Land one boundary at a time with behavior parity and rollback.
- Progress:
  - Phase 0 complete: seam pass confirmed split terrain ownership, duplicate AGL/QNH provider paths, and mixed calculation/data responsibilities.
  - Phase 0 complete: temporary deviation recorded in `KNOWN_DEVIATIONS.md`.

## 1) Scope

- Problem statement:
  - Terrain/elevation policy is split across multiple owners and layers that should not own it.
  - `SimpleAglCalculator` currently constructs `OpenMeteoElevationApi` and therefore directly owns Android `Context`, network access, retry/backoff, and cache interaction.
  - `SrtmTerrainDatabase` separately owns cache maps, file I/O, download logic, and a hidden `CoroutineScope(...)`.
  - QNH uses a separate terrain path through `TerrainElevationProvider` / `SrtmTerrainElevationProvider`, so AGL and QNH do not share one terrain source/policy owner.
  - The result is duplicated terrain policy, harder testing, weaker reviewability, and a higher chance of AGL/QNH inconsistency over time.
- Why now:
  - This is the clearest single mixed-owner architecture cluster still left in the codebase.
  - It affects two important product paths: AGL and terrain-assisted QNH calibration.
  - The current design weakens determinism, portability, and logging discipline.
- In scope:
  - One shared terrain read contract for AGL and QNH.
  - One terrain repository owner for source selection, cache lifecycle, retry/backoff, and logging.
  - Removing Android/network/file/cache ownership from calculation classes.
  - Removing the duplicate QNH-only terrain provider seam after migration.
  - Replay-safe terrain lookup behavior.
- Out of scope:
  - AGL UI redesign or card presentation changes.
  - New terrain visuals or map overlay work.
  - Generic perf tuning outside the touched terrain lane.
  - Broad module decomposition unless the seam proves it is necessary.
- User-visible impact:
  - None intended.
  - QNH and AGL should become more consistent because they will read from the same terrain policy.

## 2) Seam Pass Findings

### 2.1 Concrete Current Drift

| Seam | Current drift | Why it matters |
|---|---|---|
| AGL calculation boundary | `SimpleAglCalculator` owns `Context`, network API construction, retry/backoff, and cache policy | calculation code is doing data-layer work |
| Terrain data boundary | `SrtmTerrainDatabase` owns disk cache, download logic, hidden scope, and raw `Log.*` | data ownership is hidden and not reusable through one clean port |
| Cross-feature read contract | AGL and QNH use different terrain entrypoints | duplicate terrain policy can diverge silently |
| Logging boundary | terrain files still bypass the canonical logging seam in parts of the lane | privacy/hot-path/logging review quality remains inconsistent |

### 2.2 Existing Seams To Reuse

- `feature/map/src/main/java/com/example/xcpro/qnh/TerrainElevationProvider.kt`
  - current minimal read-only terrain port
  - good starting shape, but the owner is too high in the graph for flight-runtime consumers
- `feature/flight-runtime/src/main/java/com/example/xcpro/audio/VarioAudioControllerPort.kt`
  - proven cross-module runtime port pattern where flight runtime depends on a small shared interface and Android side effects live outside the runtime module

### 2.3 Related Existing Plans

These plans remain relevant background but do not solve the ownership split by themselves:

- `docs/ARCHITECTURE/CHANGE_PLAN_AGL_PILOT_SAFE_RUNTIME_2026-02-27.md`
  - performance/freshness contract for AGL cadence and stale-state behavior
- `docs/refactor/Genius_Level_Crash_Stability_Plan_2026-02-26.md`
  - runtime hardening and duplicate AGL fetch cleanup

This plan complements them by fixing terrain ownership and the shared provider seam.

## 3) Target Terrain Standard

### 3.1 Ownership Contract

| Concern | Authoritative owner | Required exposure | Forbidden pattern |
|---|---|---|---|
| Terrain lookup policy | one terrain repository owner | one shared read port | separate AGL/QNH provider logic |
| Source selection / fallback | terrain repository owner | internal repo policy only | consumer-specific source choice |
| Memory + disk cache lifecycle | terrain repository owner | private internal state | cache maps owned in calculators or ad hoc providers |
| Android/network/file adapters | terrain data sources | narrow internal adapter APIs | Android `Context` / `HttpURLConnection` / file I/O in calculator classes |
| AGL value | fusion runtime owner (`FlightCalculationHelpers` -> `CompleteFlightData`) | runtime output only | terrain repository becoming a second AGL owner |
| QNH terrain-assisted sample | `CalibrateQnhUseCase` as a consumer of the shared read port | transient local value only | QNH-specific terrain repository |

### 3.2 Canonical Shape

Before:

```text
FlightDataCalculatorEngine
  -> SimpleAglCalculator
     -> OpenMeteoElevationApi

CalibrateQnhUseCase
  -> TerrainElevationProvider
     -> SrtmTerrainElevationProvider
        -> SrtmTerrainDatabase
```

After:

```text
AGL consumer (SimpleAglCalculator)
  -> TerrainElevationReadPort
     -> TerrainElevationRepository
        -> SrtmTerrainDataSource
        -> OpenMeteoTerrainDataSource

QNH consumer (CalibrateQnhUseCase)
  -> TerrainElevationReadPort
     -> same TerrainElevationRepository
```

### 3.3 Initial Shared-Port Owner

Release-grade default:

- shared read contract lives in `dfcards-library` because:
  - `SimpleAglCalculator` already lives there
  - `feature:flight-runtime` and `feature:map` already depend on it
  - this avoids a speculative new module in Phase 1

Planned rule:

- the shared contract must be interface-only and free of Android/network/file ownership
- if the lane grows beyond AGL/QNH or the dependency graph becomes strained, a later dedicated lower-level terrain module can be considered under a separate seam lock

## 4) Architecture Contract

### 4.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Terrain elevation for `(lat, lon)` | terrain repository | shared read port result | separate AGL-only and QNH-only lookup paths |
| Terrain source / fallback policy | terrain repository | internal policy | calculator/provider-local source choice |
| AGL value | fusion runtime output | `CompleteFlightData.agl` and related runtime metadata | second AGL owner in QNH/terrain code |
| QNH calibration terrain sample | `CalibrateQnhUseCase` transient execution state | local execution value | persisted or mirrored terrain authority in QNH layer |

### 4.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| terrain source selection/fallback state | terrain repository | repo internal policy only | shared read port | availability, cache state, configured policy | repo internal only | process restart or explicit cache clear | monotonic for retry/backoff if tracked | repository source-selection tests |
| terrain memory cache | terrain repository | repo internal only | internal | successful source reads | repository/disk cache owner | explicit clear, app restart, cache eviction | monotonic where age matters | cache hit/miss/eviction tests |
| current AGL | `FlightCalculationHelpers` | AGL worker only | fusion output -> repository/UI | baro altitude + terrain read result | none | runtime reset/restart | monotonic freshness | AGL calculator + fusion tests |
| QNH terrain-assisted sample | `CalibrateQnhUseCase` | use-case execution only | local use-case flow | GPS position + terrain read result | none | each calibration run | wall for final QNH metadata, monotonic for sample age | QNH calibration tests |

### 4.2 Dependency Direction

Required flow:

`consumer (AGL/QNH) -> shared terrain port -> terrain repository -> terrain data sources`

Modules/files touched:

- `dfcards-library`
- `feature:flight-runtime`
- `feature:map`

Boundary risks:

- `dfcards-library` must not gain new Android/network/data ownership while hosting the shared port.
- `feature:flight-runtime` must not depend upward on `feature:map`.
- `feature:map` may own the concrete terrain repository implementation and DI wiring, but not consumer-side policy.

### 4.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/qnh/TerrainElevationProvider.kt` | existing narrow terrain read port | keep the read-only single-purpose contract shape | relocate to a lower shared owner and broaden to both consumers |
| `feature/flight-runtime/src/main/java/com/example/xcpro/audio/VarioAudioControllerPort.kt` | cross-module runtime port consumed by flight runtime and implemented outside it | runtime module depends on small interface; side effects live in a higher module | terrain port is read-only and source-policy oriented rather than controller-like |

### 4.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| terrain read contract | `feature:map` QNH package | shared lower owner in `dfcards-library` | both AGL and QNH need the same contract | compile wiring + consumer tests |
| network/file/cache terrain policy | `SimpleAglCalculator` and `SrtmTerrainDatabase` | terrain repository/data sources in app-side data layer | calculation code must stop owning data work | repository/data-source tests |
| QNH-specific terrain seam | `SrtmTerrainElevationProvider` | shared repository via shared port | delete duplicate provider path | QNH + AGL parity tests |

### 4.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `SimpleAglCalculator` -> `OpenMeteoElevationApi` | calculator constructs network adapter directly | injected shared terrain read port | Phase 1 / 3 |
| `CalibrateQnhUseCase` -> `TerrainElevationProvider` -> `SrtmTerrainElevationProvider` | QNH-only terrain seam | shared terrain read port + shared repository | Phase 4 |
| `SrtmTerrainDatabase` direct use | QNH path reaches a private SRTM-only implementation | internal repository data source only | Phase 2 / 4 |

### 4.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Terrain_Elevation_Ownership_Release_Grade_Phased_IP_2026-03-16.md` | New | active remediation contract | repo-standard phased IP location | not a durable global doc | No |
| `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | Existing | temporary exception ledger | required by AGENTS for unresolved rule violations | not a feature-specific plan | No |
| `dfcards-library/.../TerrainElevationReadPort.kt` | New | shared pure terrain read contract | lowest existing shared owner for current graph | `feature:flight-runtime` is too high while calculator lives in `dfcards-library` | New file |
| `dfcards-library/.../SimpleAglCalculator.kt` | Existing | AGL policy/calculation only | this is the AGL calculation owner already | should not own network or file work anymore | Likely split helpers if still mixed after migration |
| `feature/map/.../terrain/TerrainElevationRepository.kt` | New | canonical terrain source/caching/fallback owner | app-side data owner with Android/network/file access | QNH or calculator classes should not own this | New file |
| `feature/map/.../terrain/SrtmTerrainDataSource.kt` | New or moved | SRTM file/disk/network adapter | isolates one data source | repository should not inline all SRTM mechanics | New/move |
| `feature/map/.../terrain/OpenMeteoTerrainDataSource.kt` | New or moved | Open-Meteo network adapter | isolates fallback source | calculator should not construct it | New/move |
| `feature/map/.../qnh/CalibrateQnhUseCase.kt` | Existing | QNH consumer only | existing QNH policy owner | should not own terrain implementation | No |
| `feature/map/.../qnh/SrtmTerrainElevationProvider.kt` | Existing | temporary compatibility shim only, then delete | temporary migration bridge only if needed | must not remain long term | Delete by Phase 4 |

### 4.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `TerrainElevationReadPort` | `dfcards-library` | `SimpleAglCalculator`, `CalibrateQnhUseCase`, DI wiring | public cross-module interface | one shared read seam for AGL and QNH | stable after migration |
| temporary adapter from old QNH provider contract | `feature:map` | QNH wiring only during migration | internal | keep migration phases narrow | remove in Phase 4 |

### 4.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| terrain repository internal work | perform cache/disk/network reads | injected `IO` dispatcher, structured suspend work preferred | caller coroutine cancellation | hidden standalone `CoroutineScope(...)` in the data source is forbidden after migration |

Rule:

- `SrtmTerrainDatabase` must not survive as a hidden scope owner after the repository phase.

### 4.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| old `TerrainElevationProvider` bridge if retained temporarily | `feature:map` | keep QNH migration narrow while shared port lands | direct QNH use of `TerrainElevationReadPort` | Phase 4 completion | QNH migration tests |

### 4.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| terrain source selection / offline-first fallback policy | terrain repository | AGL and QNH consumers | consumers must not choose their own source policy | No |
| AGL math (`altitude - terrain`) and submission policy | `SimpleAglCalculator` / existing AGL helper path | fusion runtime only | belongs to AGL calculation owner, not repository | No |
| QNH aggregation / calibration math | `CalibrateQnhUseCase` / `QnhMath` | QNH only | terrain repo supplies elevation only, not QNH math | No |

### 4.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| AGL submission/freshness timing | Monotonic | already in live fusion path; stable under wall-clock changes |
| terrain retry/backoff/cache age if tracked | Monotonic | avoids wall-time drift in runtime policy |
| QNH calibrated-at metadata | Wall | user-facing timestamp metadata remains wall-based |

Explicitly forbidden:

- replay-triggered terrain fetches using live network timing
- wall-time-based retry or cache-expiry decisions in the terrain repo

### 4.4 Threading and Cadence

- Dispatcher ownership:
  - terrain repository/data sources use injected `IO`
  - AGL worker cadence remains owned by the existing flight runtime helper
- Primary cadence/gating sensor:
  - unchanged; this plan does not retune AGL submit cadence
- Hot-path latency budget:
  - no new blocking work in the fusion hot path
  - terrain lookups remain asynchronous/coalesced through the existing AGL worker behavior

### 4.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| terrain repository/data-source diagnostics | `AppLogger` | coordinates are potentially sensitive | use redacted/coarsened coordinates or remove | remove low-value success chatter in migration |
| replay terrain bypass diagnostics | `AppLogger` if retained | low | debug-only/rate-limited | can be removed if not needed after tests |
| calculator hot-path terrain misses | `AppLogger` or delete | medium | rate-limited only if truly needed | prefer deletion over spam |

### 4.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - live mode may use terrain repository reads according to the canonical policy
  - replay mode must not trigger online terrain fetches or non-deterministic terrain-side effects
  - if replay needs terrain, it must use a deterministic preloaded/frozen path only; otherwise preserve the current replay-disabled behavior

### 4.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| no terrain available | Degraded | terrain repository -> consumer | AGL/QNH continue with existing fallback behavior | canonical repo policy only | repo + consumer tests |
| SRTM cache/file invalid | Recoverable | terrain repository | source fallback or null | internal policy, not consumer-specific | repo tests |
| network unavailable | Degraded | terrain repository | no online terrain result | offline-first or null according to repo policy | repo tests |
| replay terrain read requested | Unavailable by policy unless deterministic path exists | consumer + repo guard | preserve replay determinism | no online fetch | replay tests |

### 4.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| calculator still owns Android/network/file concerns | layering and boundary rules | targeted unit tests + code review + grep | `SimpleAglCalculator` and new port tests |
| duplicate QNH terrain path survives | SSOT / canonical policy rules | DI wiring tests + grep | QNH wiring tests |
| replay starts online terrain fetches | replay determinism rules | replay regression tests | flight-runtime tests |
| terrain files still use raw `Log.*` | logging rules | `enforceRules` + grep + targeted tests | terrain data files |

## 5) Implementation Phases

### Phase 0 - Contract lock and deviation

- Goal:
  - freeze the terrain ownership standard and record the active exception before code changes
- Files to change:
  - plan + deviation docs only
- Tests to add/update:
  - none
- Exit criteria:
  - one approved phased IP exists
  - deviation is time-boxed and linked to the plan
- Completion note:
  - Completed 2026-03-16 in this docs change.

### Phase 1 - Shared terrain port and pure calculator seam

- Goal:
  - define the shared terrain read contract and stop `SimpleAglCalculator` from constructing data adapters
- Files to change:
  - new shared terrain read port in `dfcards-library`
  - `SimpleAglCalculator.kt`
  - `FlightDataCalculatorEngine.kt` and any constructor/wiring files needed to inject the port
- Ownership/file split changes in this phase:
  - calculator becomes a consumer of terrain reads only
  - Android/network/data concerns stay out of the calculator
- Tests to add/update:
  - `SimpleAglCalculator` unit tests with a fake terrain port
  - flight-runtime tests proving the engine can run with a fake terrain provider
- Exit criteria:
  - `SimpleAglCalculator` no longer takes `Context`
  - calculator does not construct `OpenMeteoElevationApi`
  - replay gate behavior is explicitly preserved in tests

### Phase 2 - Canonical terrain repository and source adapters

- Goal:
  - create one repository that owns terrain source selection, cache lifecycle, retry/backoff, and logging
- Files to change:
  - new terrain repository and source adapters in `feature:map`
  - migrated or replaced `SrtmTerrainDatabase.kt`
  - migrated or replaced `OpenMeteoElevationApi.kt`
  - migrated or replaced `ElevationCache.kt` as needed
- Ownership/file split changes in this phase:
  - repository becomes the canonical data owner
  - source-specific mechanics move into focused adapters
  - hidden `CoroutineScope(...)` ownership is removed or made explicit at the repository owner
- Tests to add/update:
  - repository tests: source selection, cache hit/miss, fallback, invalid cache/file handling
  - logging tests or greps proving raw `Log.*` removal in touched files
- Exit criteria:
  - one repository implements the shared read port
  - raw `Log.*` is gone from touched terrain data files
  - there is one explicit terrain source policy

### Phase 3 - Flight runtime AGL migration

- Goal:
  - wire the live AGL path fully through the shared terrain repository seam
- Files to change:
  - flight-runtime DI/wiring files
  - AGL helper/engine files as needed
- Ownership/file split changes in this phase:
  - AGL remains the sole owner of AGL state
  - terrain repo remains a read-only dependency, not a state mirror
- Tests to add/update:
  - AGL runtime tests proving no behavior regression
  - replay tests proving replay does not trigger terrain networking
  - existing AGL perf evidence tests if touched by the new seam
- Exit criteria:
  - live AGL uses the shared terrain seam only
  - replay determinism remains intact
  - no direct terrain adapter construction remains in flight runtime

### Phase 4 - QNH migration and duplicate seam removal

- Goal:
  - move QNH onto the shared terrain seam and delete the duplicate provider path
- Files to change:
  - `TerrainElevationProvider.kt` / `SrtmTerrainElevationProvider.kt`
  - `CalibrateQnhUseCase.kt`
  - QNH DI/wiring files
- Ownership/file split changes in this phase:
  - QNH becomes a pure consumer of the shared port
  - QNH-specific terrain repository/provider ownership is removed
- Tests to add/update:
  - QNH calibration tests proving terrain-assisted path still works
  - tests proving QNH and AGL now hit the same fake/shared port seam
- Exit criteria:
  - duplicate QNH terrain provider seam is deleted
  - both AGL and QNH use the same terrain read contract
  - no consumer-specific terrain source policy remains

### Phase 5 - Hardening, docs, and closeout

- Goal:
  - lock the final terrain architecture, close the deviation, and verify no drift remains
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - ADR if the final ownership/API surface materially changes from current plan assumptions
  - `KNOWN_DEVIATIONS.md`
  - any rule-enforcement file needed if a new static guard is justified
- Ownership/file split changes in this phase:
  - docs reflect the canonical terrain seam and replay rule
  - deviation is removed if exit criteria are met
- Tests to add/update:
  - final regression pass across AGL + QNH + replay guards
- Exit criteria:
  - the canonical terrain seam is documented
  - deviation is removed or deliberately renewed with evidence
  - required verification passes

## 6) Test Plan

- Unit tests:
  - `SimpleAglCalculator` with fake terrain port
  - terrain repository source-selection/cache/fallback tests
  - QNH calibration tests using the shared port
- Replay/regression tests:
  - replay does not trigger online terrain fetches
  - live and replay behavior remains deterministic for the same replay input
- Integration tests:
  - flight-runtime wiring test with injected terrain port
  - QNH wiring test through DI seam
- Manual smoke:
  - live AGL available in normal flight mode
  - QNH auto-calibration still succeeds/falls back correctly
  - replay path does not introduce terrain-network churn

## 7) Risks and Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| AGL regressions from seam rewiring | High | fake-port tests plus existing AGL runtime/perf evidence suite |
| QNH behavior changes because terrain source policy becomes unified | Medium | explicit source-policy decision and QNH regression tests |
| replay determinism regresses | High | dedicated replay no-network tests before migration completion |
| repository grows into a new god-object | Medium | keep source adapters split and repository policy-only |
| module boundary gets muddier during migration | Medium | one shared port only; no second shared terrain contract allowed |

## 8) Acceptance Criteria

- One canonical terrain read port serves both AGL and QNH.
- `SimpleAglCalculator` is calculation-only and no longer owns Android/network/file/cache concerns.
- One terrain repository owns source selection, cache lifecycle, retry/backoff, and logging.
- Replay does not trigger online terrain fetches.
- Touched terrain files use `AppLogger` or remove logging entirely; no raw `Log.*` remains in the migrated lane.
- `PIPELINE.md` and any required ADR are updated if wiring or ownership changes materially during implementation.

## 9) Release-Grade Recommendation

This is worth doing.

Priority:

1. Finish the general logging track first or in parallel for the touched terrain files.
2. Execute Phase 1 and Phase 2 before any further AGL/QNH performance tuning.
3. Do not start a new terrain UI or map feature track until this ownership split is corrected.

Reason:

- this plan fixes one of the last clearly mixed owner/data-boundary clusters in the repo
- it improves correctness and reviewability more than generic cleanup would
- it gives QNH and AGL one professional terrain contract instead of two drifting ones
