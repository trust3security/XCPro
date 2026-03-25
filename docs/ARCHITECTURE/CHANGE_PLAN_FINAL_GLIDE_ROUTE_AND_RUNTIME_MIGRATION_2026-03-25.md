# CHANGE_PLAN_FINAL_GLIDE_ROUTE_AND_RUNTIME_MIGRATION_2026-03-25.md

## Purpose

Phased, low-risk implementation plan for moving final glide to the durable
boundary approved in `ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md`.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `AGENTS.md`

## 0) Metadata

- Title: Final glide route/runtime migration
- Owner: XCPro Team
- Date: 2026-03-25
- Issue/PR: local branch only; Phase 4 complete and ready for the first GitHub update
- Status: Implemented locally through Phase 4; full local proof passed
- ADR:
  - `docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md`

### Local branch precondition

Start from local `main` only. Do not push or open a PR until phase 4 is fully
complete and the full local proof passes.

```bash
git switch main
git status
git switch -c final-glide-route-runtime-migration
git tag -a fg-runtime-baseline-2026-03-25 -m "Baseline before final glide route/runtime migration"
```

Recommended discipline:
- one local commit per phase
- optional local checkpoint tag after each completed phase
- no GitHub branch or PR until phase 4 exit criteria are met

### Phase 0 baseline inventory

- Current task runtime owner:
  - `TaskManagerCoordinator.taskSnapshotFlow`
- Current glide target owner:
  - `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt`
- Current final-glide solve owner:
  - `feature/map/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt`
  - invoked from `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
- Current known route limitation:
  - remaining-route projection is waypoint-center based and is not yet
    boundary-aware

### Current local implementation status

- Phase 0:
  - complete locally
- Phase 1A:
  - complete locally; `feature:tasks` owns an additive route seam via
    `NavigationRouteRepository`
- Phase 1B:
  - complete locally; the new task-owned route seam now projects
    boundary-aware racing touchpoints while current map glide consumers still
    read the legacy `GlideTargetRepository` path
- Phase 2:
  - complete locally; `feature:map-runtime` now owns
    `GlideComputationRepository.glide`, `FinalGlideUseCase` has moved upstream,
    and `MapScreenObservers` now consumes `GlideSolution` instead of solving
    directly
  - `GlideTargetRepository` remains only as a temporary compatibility shim for
    legacy callers/tests and is no longer the active map glide consumer path
  - `GlideTargetProjector` is the shared runtime glide-policy owner for the
    current racing finish rule and glide-status mapping; remaining-route
    geometry/status stay authoritative in `NavigationRouteRepository`
- Phase 3:
  - complete locally; duplicated glide-policy/status projection has been
    collapsed into `GlideTargetProjector` in `feature:map-runtime`
  - `FinalGlideUseCase` now consumes canonical `NavigationRoutePoint` values
    from the task-owned route seam instead of a duplicated map-runtime route
    point model
- Phase 4:
  - complete locally; the remaining `GlideTargetRepository` compatibility shim
    and its dedicated shim test are removed from the branch
  - final owner docs now describe the post-shim durable boundary
  - required full local proof passed:
    `./gradlew enforceRules`, `./gradlew testDebugUnitTest`,
    `./gradlew assembleDebug`

## 1) Scope

- Problem statement:
  - Current final glide is split across correct source seams
    (`FlightDataRepository`, `TaskManagerCoordinator.taskSnapshotFlow`,
    `TaskNavigationController.racingState`) but the current derived owner path
    still lives in `feature:map`.
  - `GlideTargetRepository` currently reduces the remaining route to waypoint
    centers.
  - `MapScreenObservers` still invokes `FinalGlideUseCase` directly.
- Why now:
  - We want to fix route correctness first, then move the derived glide owner
    out of the UI-adjacent map shell with minimal risk.
- In scope:
  - add a canonical remaining-route contract in `feature:tasks`
  - switch racing final-glide route projection to boundary-aware geometry
  - move derived glide computation to `feature:map-runtime`
  - thin `MapScreenObservers` so it consumes glide results instead of solving
  - update docs/tests for the new boundary
- Out of scope:
  - generalized non-task targets (home, alternate, free target)
  - AAT final glide
  - broad card/UI redesign
  - large invalid-status model cleanup beyond what is needed to complete the
    owner move
  - GitHub PR/push automation
- User-visible impact:
  - more accurate final-glide distance/arrival calculations near finish/start
    boundaries
  - no intended UI redesign
- Rule class touched:
  - Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Latest fused flight sample | `FlightDataRepository` | `flightData` | task-owned or UI-owned copies |
| Active task definition + active leg | `TaskManagerCoordinator` | `taskSnapshotFlow` | `TaskRepository` as runtime authority |
| Racing nav runtime state | `TaskNavigationController` | `racingState` | map/card-local nav truth |
| Canonical remaining racing route | new `NavigationRouteRepository` in `feature:tasks` | `route: Flow<NavigationRouteSnapshot>` | waypoint-center route derived in `feature:map` |
| Derived final glide result | `GlideComputationRepository` in `feature:map-runtime` | `glide: Flow<GlideSolution>` | direct `finalGlideUseCase.solve(...)` in observers/UI |
| UI card projection | existing map adapter path | `RealTimeFlightData` | task/glide math in cards/formatters |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `CompleteFlightData` | `FlightDataRepository` | fusion/live/replay pipeline only | map/runtime consumers | sensor fusion | none | source switch / clear | live monotonic + replay sample time | existing flight runtime tests |
| `TaskRuntimeSnapshot` | `TaskManagerCoordinator` | task managers / coordinator only | cross-feature runtime reads | task runtime | task persistence path | task load/clear/switch | runtime event time | existing task tests |
| `RacingNavigationState` | `TaskNavigationController` | racing nav engine only | cross-feature runtime reads | nav fixes + task runtime | none | task-type switch/reset | runtime fix time | existing navigation tests |
| `NavigationRouteSnapshot` | new `NavigationRouteRepository` (`feature:tasks`) | repository/projector only | future glide/runtime consumers and tests | `taskSnapshotFlow` + `racingState` + boundary helpers | none | no task / task-type switch / finished / invalid route | task/nav runtime time | new parity + boundary tests |
| `GlideSolution` | `GlideComputationRepository` (`feature:map-runtime`) | repository/use-case only | `feature:map` adapter path | `CompleteFlightData` + `WindState` + `TaskManagerCoordinator.taskSnapshotFlow` + `NavigationRouteSnapshot` + polar | none | no task / invalid inputs / source clear | live/replay fused sample time | new repository tests + existing solver tests |
| `RealTimeFlightData` | map adapter path | adapter only | cards / overlays | `CompleteFlightData` + `GlideSolution` | none | sample clear | fused sample time | existing conversion tests |

### 2.2 Dependency Direction

Confirmed target dependency flow remains:

`feature:tasks` (route authority) -> `feature:map-runtime` (derived glide owner) -> `feature:map` (adapter/render)

- Modules/files touched:
  - `feature:tasks`
  - `feature:map-runtime`
  - `feature:map`
  - docs under `docs/ARCHITECTURE`
- Any boundary risk:
  - moving solver ownership without introducing a reverse dependency from
    `feature:map-runtime` back to `feature:map`

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` | Non-UI runtime owner consumed by the map shell | narrow runtime owner + shell adapter split | final glide uses data flows instead of map overlay callbacks |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt` | Cross-feature task-derived runtime orchestration | upstream owner in runtime layer, map shell as consumer | final glide outputs a value flow instead of issuing render sync side effects |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Remaining racing-task route contract | `GlideTargetRepository` in `feature:map` | `NavigationRouteRepository` in `feature:tasks` | canonical route belongs with task runtime and boundary helpers | route parity tests, boundary regression tests |
| Glide solve orchestration | `MapScreenObservers` | `GlideComputationRepository` in `feature:map-runtime` | remove UI-adjacent business logic | repository tests + observer no-solve assertion |
| Final glide use-case host | `feature:map` | `feature:map-runtime` | keep solver in non-UI runtime layer | compile + tests + no dependency reversal |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapScreenObservers` | direct `finalGlideUseCase.solve(...)` | consume `glide: Flow<GlideSolution>` from `GlideComputationRepository` | 2 |
| `GlideTargetRepository.finishTarget` | compatibility shim used its own waypoint-center route + status projection | delegate to `NavigationRouteRepository` + shared `GlideTargetProjector`, then delete the shim | 3-4 |
| `FinalGlideUseCase.buildRoute(...)` | builds route from duplicated `GlideRoutePoint` centers | consume canonical `NavigationRoutePoint` values from `NavigationRouteSnapshot` | 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/tasks/src/main/java/com/example/xcpro/tasks/navigation/NavigationRouteSnapshot.kt` | New | cross-feature route model | task runtime contract | route is task-owned, not map-owned | no |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/navigation/NavigationRouteRepository.kt` | New | route projector / read-only flow owner | near task runtime + boundary helpers | avoids map-layer route ownership | maybe split projector helper if file grows |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/navigation/NavigationRouteProjector.kt` | New | pure parity projector for Phase 1A | isolates route derivation logic from the flow owner | avoids mixed responsibility in repository | no |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/navigation/NavigationRouteGeometryResolver.kt` | New | boundary-aware racing touchpoint resolver for Phase 1B | keeps route geometry with task/runtime owners | avoids boundary math in map/UI or a mixed repository file | no |
| `feature/tasks/src/test/java/com/example/xcpro/tasks/navigation/NavigationRouteRepositoryTest.kt` | New | parity + boundary route tests | route owner tests | keeps route correctness near owner | no |
| `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideComputationRepository.kt` | New | derived glide flow owner | non-UI runtime layer already depends on tasks + flight-runtime | avoids solve ownership in map shell | no |
| `feature/map-runtime/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt` | Existing/moved | final-glide math + runtime route-leg solving | solver belongs with derived runtime owner | not task-owned because it combines flight + wind + route + polar | maybe helper split later only if needed |
| `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideTargetProjector.kt` | New in phase 3 | explicit glide-policy/status projection owner | shared policy mapping used by runtime and compatibility shim | keeps status/finish-rule mapping out of UI and out of duplicate shims | no |
| `feature/map-runtime/src/test/java/com/example/xcpro/glide/GlideComputationRepositoryTest.kt` | New | repository wiring tests | owner-local proof | keeps new behavior testable without map shell | no |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` | Existing | adapter only | consumer of upstream glide result | must stop owning solve orchestration | no |
| `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt` | Deleted in phase 4 | retired compatibility bridge | Phase 4 removes the dead map shim after the authoritative path is fully upstream | no durable owner remains | no |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `NavigationRouteSnapshot` | `feature:tasks` | `feature:map-runtime`, tests | public/internal-to-consumers as needed | canonical remaining-route contract | durable |
| `NavigationRouteRepository.route` | `feature:tasks` | future glide/runtime consumers | injected read-only flow | upstream route owner | durable |
| `GlideComputationRepository.glide` | `feature:map-runtime` | `feature:map` | injected read-only flow | observer consumes result instead of solving | durable |
| relocated `FinalGlideUseCase` | `feature:map-runtime` | `GlideComputationRepository`, tests | internal/public as needed | keep solver math in non-UI runtime | durable |
| none remaining after phase 4 | n/a | compatibility cleanup complete on the branch | n/a | n/a | covered by compile + full proof |

### 2.2F Transitional Task Runtime Read

- `feature/map-runtime/src/main/java/com/example/xcpro/glide/GlideTargetProjector.kt`
  may read `TaskRuntimeSnapshot` only to map the current racing finish rule and
  fallback finish label into `GlideTargetSnapshot`.
- It must not derive remaining-route geometry or become a second route owner;
  boundary-aware route geometry/status remain authoritative in
  `NavigationRouteSnapshot` from `feature:tasks`.
- Phase 4 removes the old `feature:map` compatibility shim entirely; no
  `GlideTargetRepository` bridge remains on the authoritative or fallback
  production path.
- If finish-rule mapping grows beyond this narrow policy owner, replace it with
  a task-owned finish-constraint seam instead of expanding map-runtime task
  policy in this migration slice.

### 2.2G Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| `NavigationRouteRepository` flow scope | mirror current viewmodel/screen lifetime | existing injected scope / viewmodel scope | screen/viewmodel teardown | matches existing final-glide consumer lifetime without creating app-global state |
| `GlideComputationRepository` flow scope | derived combine owner | existing injected scope / viewmodel scope | screen/viewmodel teardown | keeps hot flow near consumer lifetime and avoids a new singleton cache |

### 2.2H Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| none remaining after phase 4 | n/a | compatibility cleanup complete on the branch | n/a | n/a | compile + full local proof |
| old solver import path | `feature:map` / `feature:map-runtime` | low-risk move with minimal callsite churn | single runtime-owned use-case file | phase 3 complete | solver tests |

### 2.2I Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| boundary crossing / touchpoint geometry | existing `feature/tasks/.../racing/boundary/*` helpers | route projector | task boundary math already lives here | no |
| final glide sink / speed scan / headwind math | relocated `FinalGlideUseCase` | `GlideComputationRepository`, tests | solver combines route + flight + wind + polar | no |
| finish-target reserve/default policy | existing glide policy inputs first, then explicit policy holder only if needed | repository/use-case | avoid premature model churn | yes, only during migration if wrapper exists |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| fused flight sample inputs | live monotonic / replay sample clock | existing SSOT contract |
| route projection state | task/nav runtime sample time | route should change only with task/nav updates |
| glide computation output | fused sample time | result tracks the same live/replay source gating |
| card display clock labels | existing adapter behavior | unchanged in this migration |

Explicitly forbidden comparisons:
- monotonic vs wall
- replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - reuse existing repository/use-case flow collection strategy
- Primary cadence/gating sensor:
  - published `FlightDataRepository.flightData` plus task/nav flows
- Hot-path latency budget:
  - no extra timer loop; keep compute cost comparable to current observer-owned solve

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - same route + glide code paths consume replay-published fused samples through
    the existing source gate

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| no racing task / prestart / finished | Degraded / Unavailable | route or glide owner | invalid glide state propagated as today | recompute on next task/nav update | existing + new tests |
| missing finish rule | Degraded | glide owner | invalid glide state | recompute when task changes | existing + new tests |
| missing altitude or polar | Degraded | glide owner | invalid glide state | recompute on next sample | existing solver tests |
| invalid route geometry | Recoverable / Degraded | route owner first, glide owner second | invalid glide state | recompute on next route change | new boundary tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| task route remains map-owned | SSOT / boundary rules | review + unit tests + `enforceRules` | new `NavigationRouteRepositoryTest` |
| observer still solves glide | business logic out of UI | unit test + review | new `GlideComputationRepositoryTest`, updated `MapScreenObservers` tests |
| replay nondeterminism | replay rules | existing replay tests + focused regression cases | solver/repository tests |
| dependency reversal | module boundaries | compile + `enforceRules` | phase 3 proof |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| finish-glide values remain responsive on map/cards | FG-SLO-01 | current observer-owned solve | no visible regression | targeted manual smoke + unit proof | 3 |
| finish distance/arrival near finish boundary becomes more correct | FG-SLO-02 | waypoint-center route | boundary-aware route | new route regression tests | 2 |

## 3) Data Flow (Before -> After)

Before:

```text
FlightDataRepository.flightData
  + TaskManagerCoordinator.taskSnapshotFlow
  + TaskNavigationController.racingState
  -> GlideTargetRepository (feature:map)
  -> MapScreenObservers
  -> FinalGlideUseCase.solve(...)
  -> convertToRealTimeFlightData(...)
```

After:

```text
TaskManagerCoordinator.taskSnapshotFlow
  + TaskNavigationController.racingState
  -> NavigationRouteRepository (feature:tasks)

FlightDataRepository.flightData
  + WindState
  + TaskManagerCoordinator.taskSnapshotFlow
  + NavigationRouteRepository.route
  -> GlideComputationRepository (feature:map-runtime)
  -> GlideSolution

MapScreenObservers
  -> convertToRealTimeFlightData(...)
  -> FlightDataManager / cards / overlays
```

## 4) Implementation Phases

### Phase 0 — local branch + documentation lock

- Goal:
  - create the local branch
  - lock the durable boundary and phase plan in docs
- Files to change:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md`
  - `docs/ARCHITECTURE/CHANGE_PLAN_FINAL_GLIDE_ROUTE_AND_RUNTIME_MIGRATION_2026-03-25.md`
- Ownership/file split changes in this phase:
  - docs only
- Tests to add/update:
  - none
- Exit criteria:
  - docs reviewed locally
  - branch created
  - no push yet

### Phase 1A - add task-owned route contract with behavior parity

- Goal:
  - introduce `NavigationRouteSnapshot` and `NavigationRouteRepository` in
    `feature:tasks` without changing final-glide behavior yet
- Files to change:
  - new `feature/tasks/.../navigation/NavigationRouteSnapshot.kt`
  - new `feature/tasks/.../navigation/NavigationRouteRepository.kt`
  - new `feature/tasks/.../navigation/NavigationRouteProjector.kt` if needed
  - new `feature/tasks/.../NavigationRouteRepositoryTest.kt`
- Ownership/file split changes in this phase:
  - remaining route becomes task-owned in parallel with the old map-owned path
- Tests to add/update:
  - parity tests mirroring current `GlideTargetRepository` route shape/status
- Exit criteria:
  - route contract exists upstream
  - parity tests pass
  - no consumer switched yet

Recommended local proof:

```bash
./gradlew :feature:tasks:testDebugUnitTest
```

### Phase 1B - switch route projection to canonical boundary-aware geometry

- Goal:
  - make `NavigationRouteRepository` compute the real remaining racing route
    using existing boundary helpers
- Files to change:
  - `feature/tasks/.../navigation/NavigationRouteRepository.kt`
  - `feature/tasks/.../navigation/NavigationRouteProjector.kt`
  - new `feature/tasks/.../navigation/NavigationRouteGeometryResolver.kt`
  - new boundary regression tests
- Ownership/file split changes in this phase:
  - route correctness is fixed while the old consumer path still exists
- Tests to add/update:
  - finish cylinder touchpoint regression
  - finish line crossing regression
  - sector crossing regression
  - invalid/finished/prestart route cases
- Exit criteria:
  - canonical route no longer depends on waypoint-center fallback for supported
    racing boundaries
  - regression tests pass
  - no glide-owner move yet
- Local status:
  - complete on the local branch
  - focused tests now lock finish cylinder, finish line, FAI quadrant, and
    finished/prestart route behavior
  - map glide consumers still remain on `GlideTargetRepository` until Phase 2

Recommended local proof:

```bash
./gradlew :feature:tasks:testDebugUnitTest
./gradlew enforceRules
```

### Phase 2 - move derived glide computation to `feature:map-runtime`

- Goal:
  - move the solve/orchestration owner out of `MapScreenObservers`
- Files to change:
  - new `feature/map-runtime/.../glide/GlideComputationRepository.kt`
  - move or recreate `FinalGlideUseCase.kt` in `feature:map-runtime`
  - update `feature/map/.../MapScreenUseCases.kt`
  - update `feature/map/.../MapScreenObservers.kt`
  - update or move solver tests
  - add `GlideComputationRepositoryTest.kt`
- Ownership/file split changes in this phase:
  - `feature:map-runtime` becomes the derived glide owner
  - `feature:map` becomes consumer/adapter only for final glide
- Tests to add/update:
  - repository combine/wiring tests
  - solver tests updated for canonical route input
  - observer test or assertion proving no direct solve call remains
- Exit criteria:
  - `MapScreenObservers` consumes upstream glide results
  - direct observer-owned solve is removed
  - compile passes across `feature:tasks`, `feature:map-runtime`, and `feature:map`
- Local status:
  - complete on the local branch
  - `GlideComputationRepository` now combines fused flight data, wind state,
    task runtime, and the task-owned route seam in `feature:map-runtime`
  - `FinalGlideUseCase` now lives in `feature:map-runtime`
  - `MapScreenObservers` now consumes upstream `GlideSolution` values only
  - `GlideTargetRepository` remains as a compatibility shim and is no longer
    the active map glide consumer path

Recommended local proof:

```bash
./gradlew :feature:map-runtime:testDebugUnitTest
./gradlew :feature:map:testDebugUnitTest
./gradlew enforceRules
```

### Phase 3 - glide policy cleanup

- Goal:
  - clean up duplicated or transitional glide-policy/status handling after the
    non-UI glide owner is in place
- Files to change:
  - route/glide status models and compatibility helpers only as needed
  - focused tests covering degraded-state handling and remaining policy drift
- Ownership/file split changes in this phase:
  - no new owner move; policy duplication is reduced
- Tests to add/update:
  - degraded/invalid state coverage that becomes clearer after Phase 2
- Exit criteria:
  - no duplicated glide-policy logic remains across map/runtime callsites
  - policy owner is explicit and tested
- Local status:
  - complete on the local branch
  - `GlideTargetProjector` now owns finish-rule and glide-status projection in
    `feature:map-runtime`
  - `GlideTargetRepository` now delegates to the canonical task route seam plus
    the shared projector and no longer maintains a separate waypoint-center
    route/policy path
  - `FinalGlideUseCase` now consumes canonical task route points directly
  - focused tests now lock shared projector policy cases and compatibility-shim
    delegation

Recommended local proof:

```bash
./gradlew :feature:map-runtime:testDebugUnitTest
./gradlew enforceRules
```

### Phase 4 - compatibility removal + doc cleanup + final validation

- Goal:
  - remove compatibility glue
  - make docs truthful about final mainline wiring
  - run full proof before any GitHub update
- Files to change:
  - remove `feature/map/.../GlideTargetRepository.kt`
  - remove `feature/map/.../GlideTargetRepositoryTest.kt`
  - final doc sync in `PIPELINE.md`
  - final ADR/change-plan sync for the post-shim durable boundary
  - cleanup imports/tests
- Ownership/file split changes in this phase:
  - no legacy map-owned canonical route or glide solve path remains
- Tests to add/update:
  - final path tests only
- Exit criteria:
  - compatibility glue removed
  - full local proof passes
  - branch is ready for first GitHub update
- Local status:
  - complete on the local branch
  - `GlideTargetRepository` and `GlideTargetRepositoryTest` are deleted
  - migration docs now describe the durable post-shim owner split
  - required full local proof passed on 2026-03-25

Required full local proof:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 5) Test Plan

- Unit tests:
  - `NavigationRouteRepositoryTest`
  - boundary route regression tests
  - `FinalGlideUseCaseTest` moved/updated with canonical route input
  - `GlideTargetProjectorTest`
  - `GlideComputationRepositoryTest`
  - updated map observer/adapter tests as needed
- Replay/regression tests:
  - reuse existing replay source-gating tests
  - add deterministic glide repeat-run coverage if phase 3 changes time/cadence assumptions
- UI/instrumentation tests:
  - not required initially unless map lifecycle regressions appear
- Degraded/failure-mode tests:
  - no task / prestart / finished
  - missing finish rule
  - missing QNH / altitude / polar
  - invalid route geometry
- Boundary tests for removed bypasses:
  - observer no longer solves
  - route no longer built from waypoint centers for supported racing boundaries

Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | route regressions + solver tests |
| Time-base / replay / cadence | Deterministic repeat-run tests | existing replay gating + targeted repository tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | repository/observer tests + `enforceRules` |
| Performance-sensitive path | no extra loop + targeted smoke | phase 3 manual smoke + existing cadence path |

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Boundary-aware route change alters finish distance unexpectedly | medium | phase 1 parity contract before phase 2 correctness switch | XCPro Team |
| Solver move causes cross-module churn | medium | keep package/import churn minimal, move owner after route contract is stable | XCPro Team |
| Legacy glue lingers after owner move | low/medium | phase 4 explicitly deletes or time-boxes leftover bridge | XCPro Team |
| Replay behavior regresses | high | keep time base unchanged and add deterministic repository tests | XCPro Team |

## 6A) Stop-and-fix rule

Stop immediately and fix before continuing if any of these occur:

- local `main` is dirty before branch setup
- the requested local branch does not match the current local `main` baseline
- Phase 1A requires boundary-aware geometry or glide-math behavior changes to
  compile
- a change would add task-route or glide-derived fields to `CompleteFlightData`
- a change would move business logic into `feature:map`, `MapScreenObservers`,
  cards, Composables, or formatting layers
- `./gradlew enforceRules` or targeted Phase 1A tests fail

Do not continue to the next phase with a known failing gate unless an explicit,
approved deviation exists. None is planned for this migration.

## 6B) ADR / Durable Decision Record

- ADR required: Yes
- ADR file:
  - `docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md`
- Decision summary:
  - canonical remaining route is task-owned
  - final glide computation is non-UI and runtime-owned
  - `feature:map` is consumer/adapter only
- Why this belongs in an ADR instead of plan notes:
  - owner boundaries and durable cross-module seams must outlive the rollout plan
- Current ADR note:
  - the ADR must match the actual post-phase-4 branch state before the first
    GitHub update

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Boundary/public API decisions are captured in the ADR
- Full local proof passes before the first GitHub update
- `KNOWN_DEVIATIONS.md` updated only if an explicit temporary exception is
  approved with issue, owner, and expiry

## 7A) Acceptance criteria for Phase 0 + Phase 1A

- local branch exists and no remote action was performed
- change plan reflects phases 0, 1A, 1B, 2, 3, and 4
- `NavigationRouteSnapshot` and its owner exist outside UI
- route state is derived from `TaskManagerCoordinator.taskSnapshotFlow` plus
  `TaskNavigationController.racingState`
- behavior parity is preserved in 1A; no boundary-aware geometry switch yet
- `CompleteFlightData` remains unchanged
- no glide math moved into UI
- targeted tests for the new route seam exist and pass
- the smallest relevant validation passes for this slice

## 8) Rollback Plan

- What can be reverted independently:
  - phase 1 route contract extraction
  - phase 2 boundary-aware route switch
  - phase 3 glide owner move
  - phase 4 cleanup
- Recovery steps if regression is detected:
  - reset to the last local phase tag
  - restore previous consumer wiring
  - keep docs + ADR if still valid, otherwise revert the doc phase alongside the
    code phase
