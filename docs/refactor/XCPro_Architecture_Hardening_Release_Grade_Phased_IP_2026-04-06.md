# XCPro Architecture Hardening IP: Release-Grade Seam, Ownership, and Runtime Cleanup

## 0) Metadata

- Title: XCPro Architecture Hardening IP: Release-Grade Seam, Ownership, and Runtime Cleanup
- Owner: XCPro Team
- Date: 2026-04-06
- Issue/PR: TBD
- Status: Draft
- Execution rules:
  - This is a release-grade architecture hardening track, not a cleanup track.
  - No big-bang rewrite, no repo-wide churn, no rename-only moves, and no speculative abstractions.
  - Land one seam at a time with behavior parity, rollback, and post-phase drift review before starting the next phase.
  - Do not widen visibility, add service-locator-style escape hatches, or introduce new hidden state owners to "unblock" later work.
  - Additive migration first: introduce the correct owner or port, rewire consumers, remove compatibility glue, then close the phase.
  - Compatibility shims are allowed only when they are phase-boxed, documented here, and removed before the phase exits unless an expiry-backed deviation is recorded.
  - Do not mix this IP with logging cleanup, canonical formula cleanup, UI redesign, or unrelated line-budget work.
  - If a phase changes runtime wiring, module boundaries, or long-lived owners, update docs in the same phase rather than batching documentation at the end.
- Progress:
  - Phase 0 pending: baseline inventory, contract freeze, review checklist, and ADR stub.
  - Phases 1-6 pending.

## 1) Scope

- Problem statement:
  - The codebase has a strong architectural spine, but several critical seams still fall short of release-grade ownership discipline:
    - `MapScreenViewModel` exposes runtime/controller collaborators upward through `runtimeDependencies`.
    - Several long-lived runtime owners create or receive anonymous scopes implicitly.
    - Public constructors in authoritative owners still install `NoOp` collaborators or self-owned scopes in production-reachable APIs.
    - `TaskManagerCoordinator.taskSnapshotFlow` is the intended cross-feature seam, but public direct state reads still exist.
    - `feature:map-runtime` is nominally a runtime/render module but still depends on multiple higher-level feature modules.
    - `MapScreenViewModel` still concentrates too much orchestration in one owner.
  - None of these are acceptable as the steady-state end shape for a "genius release grade" architecture bar because they weaken reviewability, make drift easier, and create future ad hoc pressure points.
- Why now:
  - These six issues are the current highest-leverage architecture hardening targets.
  - They are coupled enough that delaying them increases drift, but they can still be fixed safely in sequence without a rewrite.
  - This work closes known boundary and ownership smells before they spread to new features.
- In scope:
  - Lock down map route/runtime escape hatches.
  - Standardize long-lived scope ownership and explicit teardown.
  - Remove public production convenience constructors and silent `NoOp` fallbacks for mandatory behavior.
  - Make `taskSnapshotFlow` the enforced cross-feature task read seam.
  - Tighten `feature:map-runtime` into a real runtime/render module.
  - Split `MapScreenViewModel` only after the seams underneath it are clean.
  - Add mandatory post-phase review so drift is caught immediately instead of at the end.
- Out of scope:
  - Repo-wide architecture cleanup outside the seams listed in this IP.
  - Logging standardization, formula ownership cleanup, or time/ID generation cleanup unless directly required to complete a listed phase safely.
  - UI redesign, visual refactors, or behavior changes unrelated to seam hardening.
  - New modules unless a narrower contract cannot be achieved inside the current module structure.
- User-visible impact:
  - None intended.
  - This IP is behavior-preserving by default. If any phase discovers a user-visible behavior change is required, that change needs its own explicit approval and documentation.
- Rule class touched:
  - Invariant
  - Default

## 2) Durable Architecture Contract

### 2.1 Non-Negotiable End-State Decisions

| Concern | Decision | Why |
|---|---|---|
| Map integration boundary | `feature:map` remains the integration shell for screen/runtime composition | Keeps higher-level feature orchestration out of the low-level runtime/render module |
| Map runtime boundary | `feature:map-runtime` must become runtime/render-only | Makes the module boundary real rather than nominal |
| Flight data SSOT | `FlightDataRepository` remains the fused-flight-data SSOT | Prevents route- or UI-owned runtime authority from reappearing |
| Task runtime authority | `TaskManagerCoordinator.taskSnapshotFlow` remains the canonical cross-feature task read seam | Prevents direct coordinator state bypasses and duplicate task authority |
| Runtime lifetime | Long-lived scopes must be explicit, named, and cancellable | Hidden lifetime is the most common source of drift in these seams |
| Constructor policy | Public production constructors must not silently install `NoOp` collaborators or self-owned long-lived scopes for mandatory behavior | Silent degraded wiring is not acceptable for release-grade ownership |
| ViewModel role | `MapScreenViewModel` remains the public screen entrypoint, but not a service locator or cross-feature runtime bundle | Prevents the app shell from coupling to concrete runtime controllers |

### 2.2 SSOT Ownership

| Data / Responsibility | Authoritative Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Fused live/replay flight data | `FlightDataRepository` | read-only flow / repository APIs | route-local or ViewModel-local duplicate flight runtime owners |
| Cross-feature task runtime state | `TaskManagerCoordinator` | `taskSnapshotFlow` plus narrow task APIs | direct cross-feature reads of `currentTask` / `currentLeg` as de facto authority |
| Map route runtime composition | internal map-shell runtime binding owner | internal port/binding types only | public runtime bundles exposed through `MapScreenViewModel` |
| Replay runtime lifetime | explicit replay runtime owner | read-only state and named lifecycle methods | helper-internal mutable scope resets becoming hidden authority |
| Profile backup/restore/settings policy | explicit injected repository collaborators | injected constructor only | public convenience constructors with silent `NoOp` policy objects |
| ADS-B emergency audio policy/runtime wiring | explicit DI-bound policy collaborators | explicit enabled or disabled DI wiring | public default `NoOp` fallbacks for mandatory production behavior |

### 2.2A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Task runtime snapshot | `TaskManagerCoordinator` | coordinator task mutation methods only | `taskSnapshotFlow` and snapshot wrapper if strictly needed | task repository + runtime coordinator state | task persistence owner | task clear/reset flows | replay-safe / injected runtime time bases only | task snapshot parity, replay restore, map/task consumer tests |
| Map route runtime bindings | internal map-shell owner | map shell composition only | internal bindings / narrow route ports | DI-composed runtime collaborators | N/A | route disposal / owner teardown | unchanged | route compile + map route behavior tests |
| Replay runtime handle / scope | explicit replay owner | replay lifecycle methods only | read-only replay state / lifecycle APIs | replay pipeline inputs | replay owner only | replay stop/reset/owner teardown | replay time | replay determinism and lifecycle tests |
| Flight management route data contract | narrow flight-management port | port methods only | route-local port surface | `FlightDataRepository` / map-owned card wiring | unchanged | route disposal | unchanged | flight management screen parity tests |
| Profile backup/restore wiring | injected repository collaborators | DI wiring only | repository API only | explicit collaborators | repository persistence owner | profile switch / clear / app restart | unchanged | profile bootstrap and restore tests |
| ADS-B emergency audio enablement | explicit DI-bound collaborators | DI wiring and runtime owner methods | repository/runtime API only | explicit policy owner | runtime owner | runtime stop / DI-disabled mode | unchanged | enabled/disabled DI path tests |

### 2.2B Dependency Direction

Dependency direction must remain:

`UI -> ViewModel / screen coordinator -> domain / use case / runtime port -> repository / data source`

Rules for this IP:

- UI does not gain new business or runtime ownership.
- `app` and nav composition do not reach through screen ViewModels into concrete runtime collaborators.
- `feature:map-runtime` does not become an integration bucket for higher-level feature policy.
- No phase may solve a boundary problem by adding a broader public API.

### 2.2C Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt` | authoritative runtime SSOT with live/replay gating | single runtime owner, read-only exposure, deterministic source gating | none |
| `feature/map/src/main/java/com/example/xcpro/map/MapTrafficCoordinatorAdapters.kt` | narrow runtime adapter surface without leaking writable state | internal adapter / port shape with explicit mutation entrypoints | none |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` | already contains the intended task read seam | preserve `taskSnapshotFlow` as authority while narrowing bypasses | public direct reads will be removed |

### 2.2D Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Map route runtime bundle exposure | `MapScreenViewModel.runtimeDependencies` public API | internal map-shell binding owner + narrow route ports | remove service-locator escape hatch | nav/route compile + grep + targeted route tests |
| Flight management route data access | `AppNavGraph` / `FlightDataMgmt` direct `FlightDataManager` wiring | narrow flight-management port owned by map shell | stop app-shell runtime coupling | route parity tests |
| Long-lived task persistence scope | `TaskManagerCoordinator` self-created scope | explicit injected or owner-declared scope | make lifetime visible and cancellable | scope teardown tests |
| Sensor fusion / live follow owner scope | DI provider-created anonymous scopes | named runtime owner scopes | remove hidden lifetime from DI providers | scope inventory grep + targeted tests |
| Task direct state reads | public `currentTask` / `currentLeg` cross-feature consumption | `taskSnapshotFlow` and snapshot wrapper if needed | enforce one task read seam | consumer rewires + grep |
| Feature orchestration currently in `feature:map-runtime` | low-level runtime module | `feature:map` or owning feature via narrow contracts | restore true module boundary | Gradle dependency diff + compile |
| Screen orchestration concentration | `MapScreenViewModel` | internal delegates/coordinators under the map feature | reduce concentration after seams are clean | targeted VM tests + file ownership review |

### 2.2E Bypass Removal Plan

| Bypass Callsite / Pattern | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` route composition | reaches through `mapViewModel.runtimeDependencies.flightDataManager` | consume a narrow flight-management port or map-owned route contract | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt` | direct `FlightDataManager` dependency in route surface | narrow read/action port owned by map shell | Phase 1 |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` public direct reads | cross-feature callers can read `currentTask` / `currentLeg` | `taskSnapshotFlow` or a snapshot wrapper | Phase 4 |
| DI providers creating anonymous `CoroutineScope(...)` | lifetime hidden inside provider code | explicit named owner scope or caller-owned scope | Phase 2 |
| Public convenience constructors with `NoOp` defaults | silently degraded production wiring | injected constructor only, plus internal/test builders | Phase 3 |

### 2.2F Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `MapShellRuntimeBindings` or equivalent internal binding type | `feature:map` | map shell composition only | `internal` | keeps concrete runtime collaborators inside map shell | introduced in Phase 1; not visible cross-module |
| `FlightDataMgmtPort` or equivalent narrow route contract | `feature:map` | flight management route only | minimal required visibility | replaces direct `FlightDataManager` routing | introduced in Phase 1 and kept as the stable route seam |
| Named runtime owner scopes | DI/runtime composition layer | authoritative runtime owners only | internal where possible | makes lifetime explicit and testable | introduced in Phase 2 |
| Snapshot wrapper for synchronous task reads, if needed | `feature:tasks` | cross-feature callers that truly need sync reads | narrow public API | avoids exposing raw state fields | only add if flow-only rewiring is insufficient |
| Internal map-screen delegates/coordinators | `feature:map` | `MapScreenViewModel` only | `internal` | reduces ViewModel concentration without widening public API | introduced in Phase 6 |

### 2.2G Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| Task runtime persistence scope | coordinator-owned background persistence / autosave work | explicit IO or injected dispatcher | owner teardown or app/runtime host teardown | must be named and visible instead of self-created internally |
| Sensor fusion runtime scope | long-lived sensor fusion work | explicit dispatcher chosen by runtime owner | runtime owner teardown | provider-created anonymous scope is not reviewable |
| LiveFollow runtime scope | long-lived following repository work | explicit dispatcher chosen by runtime owner | runtime owner teardown | same ownership issue as above |
| Replay runtime scope | replay lifecycle work | explicit replay/runtime dispatcher | replay stop/reset or owner teardown | hidden scope recreation in helpers is not acceptable |
| Orientation manager scope | long-lived orientation updates | main/runtime dispatcher | explicit owner teardown | default public self-owned scope path must be removed from production API |

Rules:

- A public production constructor must not create a long-lived scope by default.
- A DI provider must not hide an anonymous long-lived scope inside singleton/runtime-owner creation.
- If a class is intentionally process-lifetime-owned, that ownership must be explicit in code and documented here or in the ADR.

### 2.2H Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| temporary internal bridge from old map route wiring to new flight-management port | `feature:map` | allows additive route migration in Phase 1 | direct narrow route contract | removed before Phase 1 exit | map route parity tests |
| temporary deprecated task direct-read wrapper, if needed | `feature:tasks` | preserves callers while rewiring to snapshots | `taskSnapshotFlow` / snapshot wrapper | removed before Phase 4 exit unless deviation recorded | task consumer parity tests |

No other compatibility shims are planned. If a new shim appears during implementation, it must be added here before merge.

### 2.3 Time Base

This IP does not redesign time bases. It must preserve them.

| Value / Path | Time Base | Why |
|---|---|---|
| Replay runtime lifecycle and derived replay state | replay time | replay determinism must remain unchanged |
| Fused live flight data | existing injected/runtime time base | `FlightDataRepository` remains the canonical owner |
| Task runtime state | existing replay-safe/injected time sources | task seam hardening must not introduce wall-time reads |
| Profile / ADS-B policy wiring | unchanged | this is boundary work, not time-source work |

Rules:

- No new wall clock or direct system time usage may be introduced while executing this IP.
- Replay determinism must be explicitly rechecked in phases that touch replay-adjacent runtime owners.

## 3) Baseline Seam Inventory

### 3.1 Primary Findings

| Finding | Current Hotspots | Risk | Planned Phase |
|---|---|---|---|
| Public runtime/controller escape hatch from map ViewModel | `feature/map/.../MapScreenViewModel.kt`, `app/.../AppNavGraph.kt`, `feature/map/.../FlightDataMgmt.kt` | app/nav layer depends on concrete runtime collaborators through a ViewModel boundary | Phase 1 |
| Hidden or self-created long-lived scopes | `feature/tasks/.../TaskManagerCoordinator.kt`, `feature/map/.../di/SensorFusionModule.kt`, `feature/livefollow/.../di/LiveFollowModule.kt`, `feature/map/.../MapOrientationManager.kt` | lifetime ambiguity, cleanup drift, hidden background work | Phase 2 |
| Public production convenience constructors with silent `NoOp` policy wiring | `feature/profile/.../ProfileRepository.kt`, `feature/traffic/.../AdsbTrafficRepository.kt`, `feature/traffic/.../AdsbTrafficRepositoryRuntime.kt` | mandatory behavior can silently degrade in production wiring | Phase 3 |
| Task authority not fully encoded in public API | `feature/tasks/.../TaskManagerCoordinator.kt`, `feature/map/.../MapTasksUseCase.kt` | future callers can bypass the canonical task seam | Phase 4 |
| `feature:map-runtime` depends on higher-level feature modules | `feature/map-runtime/build.gradle.kts` and runtime files importing task/forecast/profile/weather/traffic concerns | runtime/render boundary is organizational rather than architectural | Phase 5 |
| Concentrated screen orchestration in map ViewModel | `feature/map/.../MapScreenViewModel.kt` | maintainability hotspot and future ownership drift | Phase 6 |

### 3.2 Secondary Hotspots to Audit During Execution

These are not separate phases, but they must be checked while the corresponding phase is in flight so known drift does not get left behind:

| Phase | Additional Hotspots | Why They Matter |
|---|---|---|
| Phase 2 | `feature/map/.../sensors/UnifiedSensorManager.kt`, `feature/map/.../replay/ReplayPipeline.kt`, `feature/map/.../replay/IgcReplayControllerRuntime.kt` | more examples of helper-owned or recreated runtime lifetime that can undermine the ownership standard |
| Phase 2 | `feature/map/.../vario/VarioServiceManager.kt` | service-owned scope is acceptable only if the owner contract stays explicit and documented |
| Phase 5 | `feature/map/.../MapTasksUseCase.kt`, task-performance / forecast / weather runtime adapters | these are likely the first code moves or contract extractions needed to make the module boundary real |
| Phase 6 | residual lifecycle and visibility concentration inside `MapScreenViewModel` after replay/task/profile/traffic seam extraction | finish the split without reopening already-correct owners |

## 4) Phase Overview

| Phase | Objective | Key Deliverable | Blocks Next Phase If Failed? |
|---|---|---|---|
| Phase 0 | lock baseline and execution contract | seam inventory, ADR stub, tests/evidence baseline | yes |
| Phase 1 | remove map ViewModel escape hatches | internal map-shell bindings + narrow flight-management route seam | yes |
| Phase 2 | standardize explicit runtime scope ownership | named owner scopes and teardown-safe authoritative owners | yes |
| Phase 3 | remove public silent `NoOp` production fallback wiring | injected-only production constructors and explicit disabled policies | yes |
| Phase 4 | make task snapshot the enforced cross-feature read seam | rewired consumers and removal of task direct-read bypasses | yes |
| Phase 5 | make `feature:map-runtime` a true runtime/render module | narrower Gradle and code dependency boundary | yes |
| Phase 6 | harden helper visibility and extract root lifecycle orchestration | stable public screen API plus stateless lifecycle helper | yes |

No phase starts unless the previous phase passes its post-phase review or records one tightly scoped carry item with owner and expiry in `KNOWN_DEVIATIONS.md`.

## 5) Phase 0: Baseline, Freeze, and Guardrails

### Goal

Lock the contract before code motion begins so later changes are attributable and reviewable.

### Work

- Create an ADR stub covering:
  - runtime scope ownership standard,
  - map shell versus map runtime boundary,
  - task snapshot authority,
  - constructor policy for mandatory collaborators.
- Record the exact baseline inventory of:
  - public runtime escape hatches,
  - DI-created long-lived scopes,
  - public convenience constructors with `NoOp` or self-owned scope defaults,
  - direct task state bypasses,
  - `feature:map-runtime` imports and dependencies that violate the target boundary.
- Capture the minimal baseline tests and evidence needed before refactoring:
  - map route / flight-management route behavior parity,
  - replay source gating parity,
  - task snapshot parity for existing consumers,
  - profile bootstrap and ADS-B enabled/disabled path parity where touched later.
- Align this IP with `PIPELINE.md`, `KNOWN_DEVIATIONS.md`, and `APPLICATION_WIRING.svg` expectations so later phases know which docs must move with code.

### Guardrails

- No implementation work lands in this phase other than test/evidence scaffolding and documentation required to freeze the contract.
- Do not turn Phase 0 into a broad "inventory cleanup" pass.

### Acceptance

- The plan, ADR stub, and baseline inventory exist in repo.
- Each later phase has a known verification target and review checklist.
- No unresolved ambiguity remains about the target owner for the six architecture seams in scope.

## 6) Phase 1: Remove Map ViewModel Escape Hatches

### Goal

Stop `app` and non-map-shell callers from reaching through `MapScreenViewModel` into concrete runtime collaborators.

### Primary Hotspots

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenRuntimeDependencies.kt`
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt`

### Changes

- Remove public upward consumption of `MapScreenViewModel.runtimeDependencies`.
- Replace the current public runtime bundle with two narrower shapes:
  - internal `MapShellRuntimeBindings` (or equivalent) for map-shell composition only,
  - narrow `FlightDataMgmtPort` (or equivalent) for the flight-management route.
- Rewire `AppNavGraph` so it no longer passes `FlightDataManager`, `MapOrientationManager`, or similar concrete runtime collaborators through a map ViewModel boundary.
- Move card-binding and other route-specific runtime composition behind a narrow map-owned contract rather than exposing the runtime bundle.
- Keep `MapScreenViewModel` focused on screen state and intents rather than serving as a public runtime container.

### Guardrails

- Do not move business logic into nav code or UI to avoid touching the runtime seam.
- Do not replace one public bundle with another slightly smaller public bundle.
- Do not widen the flight-management route API beyond what it actually needs.

### Acceptance

- `AppNavGraph` no longer references concrete map runtime collaborators through `MapScreenViewModel`.
- `FlightDataMgmt` consumes a narrow route contract instead of `FlightDataManager`.
- Any runtime binding type introduced in this phase is `internal` to the map shell unless a narrower cross-module contract is demonstrably required.
- Route behavior remains unchanged.

### Evidence

- `./gradlew enforceRules`
- targeted map route / flight-management tests
- compile or assemble for touched map/app modules

### Post-Phase Review Focus

- Did the phase remove the escape hatch, or only move it behind a new public bundle?
- Did any non-map-shell caller retain direct access to concrete runtime collaborators?
- Did the route seam get narrower in reality, not just renamed?

## 7) Phase 2: Standardize Runtime Scope Ownership and Teardown

### Goal

Eliminate hidden long-lived scopes and make lifetime ownership explicit, named, and cancellable.

### Primary Hotspots

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/di/SensorFusionModule.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/di/LiveFollowModule.kt`
- `feature/map/src/main/java/com/example/xcpro/MapOrientationManager.kt`

### Secondary Audit Hotspots

- `feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayPipeline.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayControllerRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`

### Changes

- Replace self-created or provider-created anonymous scopes with explicit named owner scopes where the class is an authoritative runtime owner.
- `TaskManagerCoordinator`
  - remove self-created persistence/runtime scope,
  - inject or compose a named scope with explicit lifecycle ownership,
  - make teardown path explicit and reviewable.
- `SensorFusionModule`
  - stop creating anonymous scopes inside providers,
  - inject a named sensor-fusion owner scope from the composition layer.
- `LiveFollowModule` / `FollowingLiveRepository`
  - do the same for follow runtime lifetime.
- `MapOrientationManager`
  - remove the public production path that self-creates its scope,
  - require caller-provided or owner-provided scope in production wiring,
  - keep any fallback path internal/test-only.
- Audit the secondary hotspots and either:
  - standardize them under the same owner model in this phase, or
  - record an explicit carry item with owner and expiry if they cannot be safely changed here.

### Guardrails

- No DI provider may create an anonymous long-lived production scope by phase exit.
- No public production constructor may own a default long-lived scope by phase exit.
- Do not spread scope ownership into helper classes unless they are the explicit runtime owner.

### Acceptance

- Every remaining long-lived scope in touched runtime owners has a named owner and cancellation trigger.
- `TaskManagerCoordinator`, sensor fusion, and LiveFollow no longer hide scope ownership in constructors or providers.
- Any intentionally service-owned or process-owned scope that remains is explicitly documented in code and in docs.

### Evidence

- `./gradlew enforceRules`
- targeted lifecycle / cancellation tests for touched owners
- grep-backed inventory review of `CoroutineScope(` in touched paths
- compile/assemble for touched modules

### Post-Phase Review Focus

- Did we reduce hidden lifetime, or simply move anonymous scopes into a different helper?
- Does every long-lived scope now have a named owner and cancellation trigger?
- Did replay-adjacent scope changes preserve determinism?

## 8) Phase 3: Remove Silent `NoOp` and Convenience Fallback Production Wiring

### Goal

Close the production-reachable degraded wiring paths that allow mandatory behavior to silently disappear.

### Primary Hotspots

- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
- `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`

### Changes

- `ProfileRepository`
  - keep the fully injected constructor as the production path,
  - move convenience constructors and silent `NoOp` defaults to internal/test support only,
  - remove any public constructor path that self-creates a long-lived scope for production.
- `AdsbTrafficRepository` and `AdsbTrafficRepositoryRuntime`
  - remove public defaults for emergency audio settings, audio output ports, and similar mandatory collaborators,
  - make enabled versus explicitly-disabled behavior a DI decision, not a constructor default.
- Audit adjacent runtime owners touched by the same smell during implementation:
  - if a collaborator is mandatory for correctness or policy, it must be explicit,
  - if a collaborator is truly optional, disabled mode must be deliberate and documented in DI, not silently installed.
- Provide dedicated test-support builders or fixtures so tests do not depend on public production convenience constructors.

### Guardrails

- Do not break tests by forcing all test code through production DI; provide focused test-support builders instead.
- Do not treat "disabled by default" as acceptable if the collaborator is mandatory for policy or correctness.
- Do not widen repository public APIs to compensate for constructor cleanup.

### Acceptance

- No production-reachable public constructor silently installs `NoOp` collaborators for mandatory runtime behavior.
- Disabled or optional policy wiring is explicit in DI.
- Tests continue to construct the affected owners through dedicated test support, not production convenience APIs.

### Evidence

- `./gradlew enforceRules`
- targeted profile and ADS-B repository tests
- compile/assemble for touched modules

### Post-Phase Review Focus

- Did we fully remove production silent fallbacks, or just hide them behind new overloads/defaults?
- Are mandatory policy collaborators now explicit at the composition boundary?
- Did test support stay narrow rather than recreating public convenience constructor drift elsewhere?

## 9) Phase 4: Make Task Snapshot Authority the Only Cross-Feature Read Seam

### Goal

Turn the current "mostly disciplined" task contract into an enforced one.

### Primary Hotspots

- `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapTasksUseCase.kt`
- any cross-feature consumer still reading task state directly

### Changes

- Keep `taskSnapshotFlow` as the single authoritative cross-feature read seam.
- Deprecate then remove public cross-feature reads of `currentTask` and `currentLeg`.
- Rewire cross-feature consumers to use:
  - `taskSnapshotFlow`, or
  - a narrow snapshot wrapper if a synchronous read is truly required.
- Keep task mutation APIs on the coordinator or explicit task use cases; only the read seam changes in this phase.
- Reconfirm that `TaskRepository` remains UI projection only and does not regain cross-feature runtime authority.

### Guardrails

- Do not introduce a second task snapshot owner or cache to make rewiring easier.
- Do not move task logic into map/UI callers during the migration.
- If a synchronous read helper is added, it must wrap the snapshot seam rather than exposing raw state fields.

### Acceptance

- Cross-feature production code no longer reads `currentTask` or `currentLeg` directly.
- `taskSnapshotFlow` remains the authoritative task-runtime read path across features.
- Task rendering, replay restore, and task-related map/runtime consumers remain behaviorally unchanged.

### Evidence

- `./gradlew enforceRules`
- targeted task snapshot and cross-feature consumer tests
- grep review for direct reads of `currentTask` / `currentLeg`
- compile/assemble for touched modules

### Post-Phase Review Focus

- Did the phase truly enforce one task read seam?
- Were any new snapshot mirrors or caches introduced that create a hidden second authority?
- Did task replay and restore behavior remain stable?

## 10) Phase 5: Tighten `feature:map-runtime` into a True Runtime/Render Module

### Goal

Make the module boundary real instead of nominal.

### Primary Hotspots

- `feature/map-runtime/build.gradle.kts`
- runtime files importing task, forecast, profile, weather, traffic, or other higher-level feature policy types

### Current Cut (2026-04-06)

- `feature/map/src/main/java/com/example/xcpro/map/MapTasksUseCase.kt`
  is the first confirmed move candidate and is treated as map-shell code.
- `TaskRenderSnapshot` remains in `feature:map-runtime` because runtime owners
  (`TaskRenderSyncCoordinator`, `MapCameraRuntimePort`) still consume it there.
- Compile proof showed the current `feature:map-runtime` dependency on
  `:feature:igc` and `:feature:profile` is still real through replay/session and
  profile-owned runtime types; those dependencies are not Phase 5A removals.
- `feature/traffic/.../TrafficOverlayRuntimeState.kt` remains the accepted
  traffic overlay handle seam. Moving it into `feature:map-runtime` would create
  a `feature:traffic <-> feature:map-runtime` cycle and conflicts with the
  accepted traffic overlay change plans.

### Changes

- Define the allowed contents of `feature:map-runtime`:
  - render/runtime primitives,
  - viewport/camera/overlay/trail/runtime controllers,
  - rendering cadence logic,
  - pure runtime math/helpers tied directly to map rendering.
- Move higher-level orchestration and feature policy adaptation back to:
  - `feature:map`, or
  - the owning feature module through narrow contracts.
- Reduce Gradle dependencies only when compile proof shows they are truly stale;
  do not remove `:feature:igc` / `:feature:profile` until their runtime-owned
  types are extracted or rewired.
- Prefer moving integration wrappers over inventing a new abstraction layer unless a small contract is clearly required.

### Guardrails

- No rename-only package motion to simulate progress.
- Do not create a generic "god bridge" module to avoid making real boundary decisions.
- Keep render/runtime code in `feature:map-runtime`; do not push low-level rendering concerns upward into `feature:map`.
- Do not force traffic overlay runtime contracts into `feature:map-runtime`
  while `feature:traffic` still owns the concrete overlay implementations.

### Acceptance

- `feature:map-runtime` is materially narrower in both code imports and Gradle dependencies.
- Higher-level feature orchestration no longer lives in the runtime/render module.
- The module boundary is demonstrably closer to the declared architecture and does not depend on naming alone.

### Evidence

- `./gradlew enforceRules`
- Gradle dependency diff for `feature:map-runtime`
- targeted compile/tests for moved seams
- assemble/compile for affected map modules

### Post-Phase Review Focus

- Did dependencies actually get narrower, or were responsibilities only renamed/moved sideways?
- Is render/runtime code still in the right place after the moves?
- Did the phase avoid introducing a new mega-adapter or bridge layer?

## 11) Phase 6: Finish `MapScreenViewModel` Hardening After Seams Are Clean

### Goal

Reduce the remaining concentration in `MapScreenViewModel` without re-splitting
already-correct replay/task/profile/traffic owners.

### Primary Hotspot

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`

### Changes

- Keep one public `MapScreenViewModel` entrypoint for the screen.
- Subphase `6A`: narrow helper visibility only where the helper is not exposed
  through the public `@HiltViewModel` constructor surface.
- Subphase `6B`: move root startup orchestration into a stateless internal
  lifecycle helper.
- Subphase `6C`: move root teardown orchestration into that same lifecycle
  helper.
- Keep the root ViewModel responsible for screen-facing state, intent
  forwarding, `viewModelScope`, `MapStateStore`, and delegate construction.

### Guardrails

- Do not reopen replay, traffic, task-shell, or profile-session ownership that
  has already been extracted correctly.
- Do not force constructor-injected map helpers to `internal` while they remain
  parameters on the public `MapScreenViewModel` constructor.
- Do not move business logic into Composables, routes, or nav code.
- Do not create a new lifecycle coordinator that becomes another hidden
  long-lived owner or service locator.

### Acceptance

- `MapScreenViewModel` no longer owns the screen startup/teardown body directly.
- Extracted helpers remain `internal` and do not widen the public map-screen
  API surface.
- Replay, traffic, task-shell, and profile-session owners stay where they
  already belong.
- Screen-facing behavior remains unchanged.

### Evidence

- `./gradlew enforceRules`
- targeted map ViewModel tests
- compile/assemble for touched map/app modules

### Post-Phase Review Focus

- Did the split follow real ownership boundaries, or just spread one god object across several arbitrary files?
- Did any delegate become a hidden new service locator or state owner?
- Did the public screen API stay stable and intentional?

## 12) Mandatory Post-Phase Review Protocol

Post-phase review is mandatory after every phase. It is not optional and it is not a final-only activity.

### 12.1 Review Inputs

- phase diff
- touched-file ownership summary
- updated docs for the phase
- verification evidence for the phase
- any compatibility shims still open
- any carry item proposed for `KNOWN_DEVIATIONS.md`

### 12.2 Review Questions

- Did this phase reduce the targeted seam, or just move it?
- Did this phase introduce any new long-lived scope owner or hidden fallback path?
- Did any caller regain access to concrete runtime collaborators?
- Did module dependencies get narrower in reality, not just by renaming or moving files?
- Did replay determinism and SSOT authority stay intact?
- Did the public API surface get smaller or clearer?
- Did this phase add any compatibility shim that now needs explicit removal or expiry?
- Did docs stay synchronized with the code?

### 12.3 Review Outcome

Each phase gets one of:

- `Pass`
  - phase target is met, drift is not observed, next phase may start.
- `Pass with boxed carry`
  - one narrow carry item remains,
  - carry item has owner, issue link, expiry, and explicit containment,
  - carry item is recorded in `KNOWN_DEVIATIONS.md`,
  - next phase may start only if the carry item does not undermine the next seam.
- `Fail`
  - drift or churn was introduced,
  - the target seam was not actually tightened,
  - next phase is blocked until corrected.

### 12.4 Quality Rescore

Each post-phase review must rescore the touched seam on the following dimensions:

| Dimension | Target Question |
|---|---|
| Ownership clarity | Is one owner clearly authoritative after the phase? |
| Boundary direction | Did dependency direction improve or at least stay strict? |
| Lifetime explicitness | Are long-lived scopes and teardown paths explicit? |
| API surface discipline | Did public/cross-feature API get narrower and more intentional? |
| Replay safety | Was determinism preserved where relevant? |
| Documentation sync | Do docs describe the new steady state accurately? |

If any rescore category is worse than baseline, the phase is not complete.

## 13) Verification and Evidence Plan

### 13.1 Minimum Verification by Phase

| Phase | Minimum Verification |
|---|---|
| Phase 0 | docs consistency review and baseline test inventory only |
| Phase 1 | `./gradlew enforceRules` plus targeted map route/flight-management tests and touched-module compile |
| Phase 2 | `./gradlew enforceRules` plus targeted lifecycle/cancellation tests and touched-module compile |
| Phase 3 | `./gradlew enforceRules` plus targeted profile/ADS-B tests and touched-module compile |
| Phase 4 | `./gradlew enforceRules` plus targeted task snapshot/consumer tests and touched-module compile |
| Phase 5 | `./gradlew enforceRules` plus Gradle dependency diff, targeted moved-seam tests, and touched-module compile |
| Phase 6 | `./gradlew enforceRules` plus targeted map ViewModel tests and touched-module compile |

### 13.2 Merge-Ready Verification Gates

After Phases 1, 3, 5, and 6, run the full merge-ready local gate:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

### 13.3 Map Runtime / Replay Evidence

When a phase touches map runtime, overlays, replay, task gesture/runtime wiring, or related performance-sensitive seams, collect evidence against the relevant SLO/profiling baselines from `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`.

Recommended mapping:

| Phase | Priority SLO IDs to Recheck |
|---|---|
| Phase 1 | `MS-UX-02`, `MS-UX-04`, `MS-UX-06`, `MS-ENG-06`, `MS-ENG-10` |
| Phase 2 | `MS-UX-03`, `MS-UX-05`, `MS-ENG-01`, `MS-ENG-03`, `MS-ENG-04`, `MS-ENG-05`, `MS-ENG-07`, `MS-ENG-08`, `MS-ENG-09`, `MS-ENG-11` |
| Phase 5 | `MS-UX-01`, plus any SLO tied to moved map-runtime owners |
| Phase 6 | `MS-UX-01`, `MS-UX-06`, `MS-ENG-09`, `MS-ENG-10` |

Use `scripts/qa/*` evidence runs when the touched seam changes measured map/runtime behavior enough that compile/test-only checks would be misleading.

## 14) Documentation Sync Requirements

Update docs in the same phase that changes the code:

- `docs/ARCHITECTURE/PIPELINE.md`
  - whenever runtime wiring, replay/live source flow, or task/runtime edges move.
- `docs/ARCHITECTURE/ARCHITECTURE.md`
  - if a durable policy or module rule changes.
- `docs/ARCHITECTURE/CODING_RULES.md`
  - if constructor, scope, or boundary rules become explicitly codified.
- ADR using `docs/ARCHITECTURE/ADR_TEMPLATE.md`
  - for runtime scope ownership standardization and map shell versus map-runtime boundary tightening.
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - when a carry item remains after post-phase review.
- `docs/ARCHITECTURE/APPLICATION_WIRING.svg`
  - after material wiring changes so the architecture artifact does not drift from the code.

## 15) Risks and Containment

| Risk | Why It Matters | Containment |
|---|---|---|
| Phase churn from trying to fix too much at once | architecture work can sprawl quickly | strict phase scope, no unrelated cleanup, mandatory post-phase review |
| Hidden regressions from boundary rewires | seams are runtime-heavy and cross-feature | additive migration, targeted tests, SLO evidence where needed |
| Temporary shims becoming permanent | common source of architectural drift | phase-boxed shim inventory plus removal trigger before phase exit |
| Module cleanup turning into a large file-move exercise | can create churn without architectural gain | move only code that meaningfully changes boundary direction |
| ViewModel split landing before lower seams are fixed | freezes bad ownership into more files | Phase 6 stays last by design |

## 16) Rollback Strategy

- Each phase must be mergeable and reversible on its own.
- Do not mix multiple seam moves into one commit stack if rollback would become ambiguous.
- If a phase fails post-phase review:
  - revert or correct within the phase before continuing,
  - do not carry forward a known boundary regression to "fix later" unless it is a tightly boxed deviation with owner and expiry.

## 17) Completion Criteria

This IP is complete only when all of the following are true:

- `MapScreenViewModel` no longer exposes public runtime/controller escape hatches.
- Long-lived scope ownership is explicit, named, and cancellable across the touched authoritative owners.
- Public production constructors no longer hide mandatory behavior behind `NoOp` or self-owned scope defaults.
- `taskSnapshotFlow` is the enforced cross-feature task read seam.
- `feature:map-runtime` is materially narrower in real dependencies and responsibility.
- `MapScreenViewModel` concentration has been reduced through internal owner-aligned delegates.
- Each phase has passed post-phase review without unresolved drift.
- Docs and diagrams match the implemented architecture.

This IP is not complete when the code "mostly works." It is complete when the seams are tighter, the owners are explicit, and the hardening survives phase-by-phase review without churn or drift.
