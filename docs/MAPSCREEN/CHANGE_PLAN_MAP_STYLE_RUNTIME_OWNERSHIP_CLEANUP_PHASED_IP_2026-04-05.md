# Map Style Runtime Ownership Cleanup Phased IP

## 0) Metadata

- Title: Consolidate map-style ownership and runtime overrides before thermalling contrast style work
- Owner: XCPro Team
- Date: 2026-04-05
- Issue/PR: TBD
- Status: Draft

Required pre-read order:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
9. `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`
10. `docs/General/THERMALLING/THERMALLING_MODE_CHANGE_PLAN.md`

## 1) Scope

- Problem statement:
  - The map-style runtime currently has more than one mutable owner.
  - Persistent style, startup style, profile reapply, drawer selection, forecast satellite temporary style, and future thermalling style all compete through different seams.
  - A startup double-apply race exists today: `MapInitializer.setupMapStyle(...)` performs the first style apply, while `MapComposeEffects.MapStyleAndConfigurationEffects(...)` can queue a second `MapCommand.SetStyle(...)` before map readiness and `MapRuntimeController.onMapReady(...)` then replays it.
  - `initialMapStyle` is threaded through multiple app/map shell layers even though runtime style already has a store-backed owner.
  - Style identity is stringly typed, so unsupported values can persist and silently fall back.
  - Thermalling runtime automation still lacks the planned replay no-op/reset guard.
  - The current implementation works for three static styles, but it is not safe to extend blindly with a transient thermalling contrast map.
- Why now:
  - The requested thermalling contrast/light basemap should not be added on top of duplicated style ownership.
  - Style reloads are map-runtime expensive because they trigger overlay reattachment; ownership mistakes here are correctness and SLO risks, not just cleanup debt.
- In scope:
  - Consolidate map-style authority into one runtime owner and one persistence owner.
  - Introduce a typed style contract in `feature:map` while preserving existing persisted string compatibility.
  - Remove UI-local style state ownership from the drawer and forecast bottom-tabs path.
  - Collapse the `initialMapStyle` app-shell prop chain and remove redundant startup bootstrap writes.
  - Make startup style application ownership explicit: `MapInitializer` owns first apply on map creation, and `MapRuntimeController` owns post-startup style changes only.
  - Add the missing replay bypass/reset guard for thermalling runtime automation.
  - Prepare a safe transient override seam for forecast satellite and future thermalling contrast style.
  - Define the final phase that adds a hidden thermalling contrast style on the cleaned seam.
- Out of scope:
  - No broad redesign of MapLibre startup or overlay lifecycle.
  - No custom MapTiler Studio theme authoring in this IP.
  - No drawer visual redesign.
  - No broad task-screen UX refactor beyond removing direct style URL bypass if touched.
  - No persistence migration away from the current user-visible strings in this pass.
- User-visible impact:
  - Manual map style selection remains stable and accurate.
  - Forecast satellite view no longer needs a Compose-local restore cache.
  - Thermalling automation can later apply a temporary high-contrast basemap without corrupting the saved style.
  - Replay will not trigger thermalling auto mode/zoom/style behavior.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| persisted profile base map style key | `MapStyleRepository` | profile-scoped config entry | any runtime-only override persisted as a saved style |
| runtime base map style selection | `MapStateStore` | canonical catalog-backed style key flow | drawer-local selected style state |
| runtime style override state | `MapStateStore` | internal override state + derived effective style flow | Compose-local restore caches such as `lastNonSatelliteMapStyleName` |
| effective runtime map style | `MapStateStore` | canonical catalog-backed effective style key flow | second effective-style cache in UI, VM, or runtime managers |
| style ID -> label/url/selectability mapping | `MapStyleCatalog` | pure lookup functions | scattered string literals and inline style URL lists |
| thermalling replay participation gate | `ThermallingModeRuntimeWiring` | internal runtime gating only | replay/live flags inside UI or coordinator state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| profile base style key | `MapStyleRepository` | `saveStyle`, `writeProfileStyle`, `clearProfile` | `MapStyleUseCase` | config JSON | `MapStyleRepository` | profile clear or explicit save | N/A | repository/use-case tests |
| runtime base style | `MapStateStore` | `MapScreenStyleCoordinator` / thin VM delegates only | drawer binding, runtime style resolution | repository read or explicit user selection normalized through `MapStyleCatalog` | none | profile switch, explicit user selection | N/A | state/store tests |
| runtime override set | `MapStateStore` | forecast and thermalling runtime mutators only | effective style resolver | active override reasons | none | override source disabled, replay reset, thermalling exit, lifecycle clear | N/A | override policy tests |
| effective runtime style | `MapStateStore` | derived only | `MapInitializer`, `MapRuntimeController`, runtime/UI bindings | canonical base style key + override policy | none | derived from base/override state | N/A | coordinator/runtime tests |
| thermalling replay bypass flag | `ThermallingModeRuntimeWiring` | derived only | internal gating | `replaySessionState.selection != null` | none | changes with replay session | Replay selection state only | runtime wiring tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

Modules/files touched:
- `feature:map`
- `feature:profile`
- `feature:map-runtime` compatibility seam only unless a later follow-up explicitly chooses module API churn
- `docs/MAPSCREEN`
- `docs/ARCHITECTURE/PIPELINE.md` when implementation lands

Boundary risk:
- letting Composables continue to own style restore state
- persisting a transient forecast or thermalling override
- pushing style precedence rules into the ViewModel
- creating a new singleton state owner for style runtime
- introducing a typed style contract in `feature:profile` that would invert module intent

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt` | canonical map runtime SSOT owner | keep authoritative runtime style state in the store, not UI-local remember state | expand the style state from one raw string to base + override + effective catalog-backed key state while preserving `MapStateReader.mapStyleName` compatibility in this IP |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt` | canonical imperative map-style apply seam | continue emitting `MapCommand.SetStyle(...)` from the ViewModel/feature boundary only | command payload becomes typed or catalog-backed instead of scattered raw strings |
| `feature/map/src/main/java/com/example/xcpro/map/ThermallingModeRuntimeWiring.kt` | existing live runtime gate between fused flight data and thermalling policy | keep replay/live participation gating in runtime wiring, not inside the core coordinator | add explicit replay reset/no-op behavior that was deferred in the existing thermalling plan |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| drawer selected style state | `NavigationDrawer` local `remember` state | `MapStateStore` base style | drawer is a renderer, not a style owner | UI/state tests |
| forecast satellite restore target | `MapScreenBottomTabsUiState` local saveable state | `MapStateStore` override state + `MapStyleOverridePolicy` | restore behavior is runtime style policy, not bottom-sheet UI state | override policy tests |
| startup initial style reapply | `MainActivityScreen` raw config + `MapComposeEffects` startup write | `MapScreenViewModel` seeded store + `MapInitializer.setupMapStyle(...)` | startup style bootstrap must have one runtime owner | startup binding tests |
| style identity and URL mapping | scattered strings in UI, tests, resolver, and task screen | `MapStyleCatalog` in `feature:map` | one canonical contract is required before adding hidden contrast styles | catalog tests |
| replay thermalling participation gate | none | `ThermallingModeRuntimeWiring` | replay gating is an integration rule, not a coordinator responsibility | runtime wiring tests |

### 2.2B1 Startup Style Apply Boundary

| Stage | Owner | Allowed Input | Forbidden Input | Validation |
|---|---|---|---|---|
| first style apply for a newly created map instance | `MapInitializer.setupMapStyle(...)` | effective style read from the seeded `MapStateStore` through `MapStateReader` | queued bootstrap `MapCommand.SetStyle(...)` from Compose startup plumbing | startup bootstrap tests |
| post-startup style changes and true pre-ready replay | `MapRuntimeController.applyStyle(...)` | explicit user/profile/runtime override commands | taking over first-style bootstrap ownership | runtime controller tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MainActivityScreen` -> `initialMapStyle` | raw config value passed through Compose tree | `MapStateStore` seeded from `MapStyleUseCase.initialStyle()` only | Phase 3 |
| `AppNavGraph` -> `MapRouteHost` -> `MapScreen` -> `MapScreenRoot` -> `MapScreenRootEffects` -> `MapScreenScaffoldInputs` -> `MapScreenScaffoldInputModel` `initialMapStyle` pass-through | app/map shell APIs carry a legacy startup style prop long after style state is already in the map feature | remove the prop chain entirely; map route startup uses the seeded store only | Phase 3 |
| `MapComposeEffects.MapStyleAndConfigurationEffects(...)` | startup writes style back into the VM from a Compose prop | remove effect; startup style comes from the seeded store and `MapInitializer` | Phase 3 |
| `MapComposeEffects` + queued `MapCommand.SetStyle(...)` before readiness | startup bootstrap can enqueue a second style apply that `MapRuntimeController.onMapReady(...)` replays after `MapInitializer` already set a style | eliminate bootstrap writes and preserve `MapRuntimeController` for true post-startup commands only | Phase 3 |
| `NavigationDrawer.selectedMapStyle` | UI-local style source of truth | bind selected radio state from `MapStateStore` base style | Phase 2 |
| `MapScreenBottomTabsUiState.lastNonSatelliteMapStyleName` | Compose-local temporary restore owner | runtime override reason state in `MapStateStore` | Phase 2 |
| `TaskRouteScreen.map.setStyle(\"https://...\")` | direct inline style URL bypass | `MapStyleCatalog` / `MapStyleUrlResolver` canonical runtime mapping | Phase 3 |
| `MapControlsUI.getMapStyleUrl(...)` and `MapStyles` | legacy raw-string helper/constants outside the canonical map seam | delete if unused, otherwise rebind to `MapStyleCatalog`/resolver | Phase 1 audit |
| `MapStyleProfileSettingsContributor` profile capture/apply | string-backed profile import/export seam not called out in the first pass | keep string compatibility at the repository boundary and lock it with contributor tests | Phase 1 compatibility lock |
| `ThermallingModeRuntimeWiring` | live-only assumption without replay signal | replay-derived no-op/reset guard | Phase 4 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/MAPSCREEN/CHANGE_PLAN_MAP_STYLE_RUNTIME_OWNERSHIP_CLEANUP_PHASED_IP_2026-04-05.md` | New | task-level plan and phase gates for this slice | required for non-trivial architecture-sensitive work | not an ADR; this is an execution plan first | No |
| `docs/MAPSCREEN/00_INDEX.md` | Existing | discoverability for the slice plan | keeps MAPSCREEN planning docs navigable | not production logic | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapStyleCatalog.kt` | New | typed style IDs, labels, persistence-key parsing, selectability metadata | one canonical map-style contract is needed in `feature:map` | not `feature:profile`; map runtime owns style semantics | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapStyleOverridePolicy.kt` | New | precedence rules for base style vs transient overrides | keeps policy out of `MapStateStore` and ViewModel | not UI code; not persistence | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapStyleUrlResolver.kt` | Existing | runtime style URL resolution from the canonical catalog | existing runtime apply seam already points here | not scattered inline URLs | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt` | Existing | authoritative runtime base/override/effective style state | existing map runtime SSOT owner | not Composables or repositories | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` | Existing | `MapStyleUseCase` conversion between repository strings and typed style IDs | existing boundary between persistence and map feature | do not leak repository strings into runtime | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenStyleCoordinator.kt` | New | style mutation orchestration and command emission without becoming an SSOT | keeps style orchestration out of the already-large ViewModel | not `MapScreenViewModel.kt`; keep VM delegates thin | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionCoordinator.kt` | Existing | profile switch orchestration; delegates style application to style coordinator | existing profile-switch owner should keep multi-setting switch behavior | do not move profile-switch orchestration into UI | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | Existing | thin delegates only | existing screen boundary | do not add more than trivial forwarding because the file is already large | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapStateReader.kt` | Existing | read-only module API seam for map runtime consumers | current style flow crosses this module boundary | do not widen scope with avoidable API churn in this IP | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt` | Existing | wire drawer actions to base-style selection and transient style actions | existing UI-to-VM binding seam | not inside Composables | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` | Existing | remove redundant startup style effect | current duplicate startup style writer lives here | not elsewhere | No |
| `feature/map/src/main/java/com/example/xcpro/navdrawer/NavigationDrawer.kt` | Existing | render selected base style from authoritative state only | current stale local owner lives here | not another UI file | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBottomTabsUiState.kt` | Existing | bottom-sheet display state only after style ownership is removed | current temporary style owner lives here | not a policy file | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` | Existing | runtime content pass-through for bottom-tabs style inputs | current bottom-tabs style state crosses this seam | not a state owner, but must align when owner changes | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt` | Existing | forecast satellite toggle calls transient override API only | existing caller for sat-view toggle | not a state owner | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSupport.kt` | Existing | temporary style-name constants used by bottom-tabs runtime | current raw constants should fold into the catalog/policy seam | not left as a hidden second style contract | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSectionInputs.kt` | Existing | UI pass-through model for current style name | current style value crosses this content seam | align with the chosen compatibility strategy | No |
| `feature/map/src/main/java/com/example/xcpro/map/ThermallingModeRuntimeWiring.kt` | Existing | replay bypass/reset integration | correct live/replay runtime seam | not coordinator core logic | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenThermallingRuntimeBinding.kt` | Existing | provide replay signal into thermalling runtime binding | existing binding entrypoint | not ViewModel business logic | No |
| `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/TaskRouteScreen.kt` | Existing | consume canonical style catalog/resolver instead of inline URL | removes one remaining direct style bypass | not left as debt on a second map surface | No |
| `feature/map/src/test/java/com/example/xcpro/map/MapStyleUrlResolverTest.kt` | Existing | catalog/resolver contract tests | existing resolver test home | not broad integration first | No |
| `feature/map/src/test/java/com/example/xcpro/map/MapStateStoreTest.kt` | Existing | canonical runtime style state regressions | existing store behavior already covers style mutation dedupe | not only ViewModel tests | No |
| `feature/map/src/test/java/com/example/xcpro/map/ThermallingModeRuntimeWiringTest.kt` | Existing | replay no-op/reset coverage | existing runtime wiring test home | not only coordinator tests | No |
| `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenBottomTabsUiStateTest.kt` | Existing | prove style ownership leaves this file | regression against reintroducing local style ownership | not runtime test | No |
| `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenBottomTabsUiStateRememberTest.kt` | Existing | Compose-state restore regression coverage for bottom tabs | existing test already covers general-settings sheet restore interactions | not only pure-function tests | No |
| `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelCoreStateTest.kt` | Existing | profile style/base style state contract | existing screen state regression home | not repository-only tests | No |
| `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierProfileScopedSectionsTest.kt` | Existing | profile restore compatibility proof for map style import | current restore contributor coverage already asserts `writeProfileStyle(...)` | not duplicated in feature:map tests | No |

### 2.2D1 Second Seam Pass Additions

| File Set | Why It Must Be In Scope | Planned Handling |
|---|---|---|
| `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`, `app/src/main/java/com/example/xcpro/AppNavGraph.kt`, `app/src/main/java/com/example/xcpro/MapRouteHost.kt` | app-shell `initialMapStyle` derivation and pass-through start here | remove runtime style prop plumbing in Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreen.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt` | map-shell pass-through keeps the startup prop alive across the feature boundary | remove the prop chain in Phase 3 and bind UI from authoritative runtime state |
| `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt` | startup first-apply and post-startup replay are separate owners that need an explicit contract | keep both seams, but document and test their non-overlapping ownership |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapStateReader.kt` and `feature/map/src/test/java/com/example/xcpro/map/LocationManagerRenderSyncTest.kt` fake readers | `mapStyleName` is already a cross-module string API with downstream test doubles | keep this API string-backed in this IP and normalize style semantics behind `feature:map` boundaries |
| `feature/map/src/main/java/com/example/xcpro/map/MapCommand.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSectionInputs.kt` | typed-style migration is incomplete if imperative and UI pass-through surfaces stay arbitrary strings | include them in Phase 1/2 contract cleanup without forcing module API churn |
| `feature/profile/src/main/java/com/example/xcpro/profiles/MapStyleProfileSettingsContributor.kt` and profile contributor tests | profile import/export still captures and restores string keys | keep this as the compatibility seam while runtime becomes typed |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSupport.kt` | bottom-tabs style state and raw style constants now cross these runtime helpers | include them in Phase 2 so the local restore owner is removed end-to-end |
| `feature/map/src/main/java/com/example/xcpro/screens/overlays/MapControlsUI.kt` | second seam pass found a legacy raw-string helper that is currently unused | treat as delete-or-align debt in Phase 1, but not as a blocker if it remains inactive |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `MapStyleId` and `MapStyleCatalog` | `feature:map` | map runtime/UI/use-case code | internal to `feature:map` where practical | map runtime needs typed style semantics without moving profile module boundaries | persisted repository values remain strings in this IP |
| `MapStateReader.mapStyleName` | `feature:map-runtime` | initializer, runtime managers, UI bindings, test doubles | module API | current runtime consumers already depend on a string flow | keep string-backed in this IP; catalog-backed normalization happens before values enter this seam |
| `MapCommand.SetStyle` style payload | `feature:map` | `MapScreenViewModel`, `MapRuntimeController` | internal to `feature:map` | the imperative runtime command boundary must not stay an arbitrary string seam | convert to canonical catalog-backed key payload in Phase 1 without changing `MapStateReader` |
| `MapScreenMapBindings.mapStyleName` / effective style binding | `feature:map` | map UI scaffolding and overlays | internal to `feature:map` | UI needs authoritative style rendering, but should not invent style semantics | keep as a canonical key/string binding in this IP; typed UI API can be a later cleanup if still needed |
| `MapScreenStyleCoordinator` | `feature:map` | `MapScreenViewModel`, `MapScreenProfileSessionCoordinator` | internal to `feature:map` | keeps style orchestration focused and testable | no compatibility shim expected |
| profile import/export map style payload | `feature:profile` `MapStyleProfileSettingsContributor` | profile snapshot/restore pipeline | existing | typed runtime cleanup must not break import/export compatibility | remain string-backed through the repository boundary |
| repository string persistence contract | `feature:profile` `MapStyleRepository` | `MapStyleUseCase` | existing | preserve config compatibility and module direction | convert at the use-case boundary; do not leak strings upward |

### 2.2F Scope Ownership and Lifetime

No new long-lived scope is planned.

`MapScreenStyleCoordinator`, if added, will be caller-owned by the ViewModel and hold no authoritative mutable state outside `MapStateStore`.

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| `initialMapStyle` app/map shell prop chain | app shell + map UI shells | legacy startup bootstrap plumbing before the seeded store becomes the only owner | remove the prop chain in Phase 3 | no map route or map screen API accepts `initialMapStyle` | startup binding tests |
| `MapStateReader.mapStyleName: StateFlow<String>` | `feature:map-runtime` | avoid unnecessary module API churn while catalog normalization is introduced | keep as compatibility API in this IP | only revisit in a separate module-API cleanup slice if it still hurts | `LocationManagerRenderSyncTest.kt` and runtime/test doubles stay green |
| `MapStyleCatalog.fromPersistedKey(...)` fallback | `MapStyleCatalog` | preserve compatibility with existing config strings and unknown historical values | none; this is the permanent persistence boundary hardening path | not removed; unknown values must stay recoverable | catalog tests |
| `MapStyleProfileSettingsContributor` string capture/apply seam | `feature:profile` | preserve profile import/export compatibility while runtime becomes typed | none in this IP; contributor remains the compatibility boundary | not removed in this IP | profile contributor tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| style key/label/selectability/url mapping | `feature/map/src/main/java/com/example/xcpro/map/MapStyleCatalog.kt` | resolver, drawer, task screen, tests | style semantics belong to the map runtime feature | No |
| effective style precedence (`forecast satellite > thermalling contrast > base style`) | `feature/map/src/main/java/com/example/xcpro/map/MapStyleOverridePolicy.kt` | state/store tests, style coordinator, runtime callers | override priority is runtime policy, not UI state | No |
| satellite/default non-satellite fallback constants | `feature/map/src/main/java/com/example/xcpro/map/MapStyleCatalog.kt` or `MapStyleOverridePolicy.kt` | bottom-tabs runtime helpers | these constants are style semantics, not UI helper trivia | No |
| replay no-op/reset for thermalling runtime | `feature/map/src/main/java/com/example/xcpro/map/ThermallingModeRuntimeWiring.kt` | thermalling runtime binding tests | this is an integration participation rule between replay and live thermalling automation | No |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| `MapStyleCatalog` | pure lookup contract for style metadata | No | it owns no runtime state and only maps IDs/keys to metadata | DI would add ceremony without state/lifecycle benefit | catalog tests |
| `MapStyleOverridePolicy` | pure precedence rule | No | state stays in `MapStateStore`; policy only computes effective style | DI would not improve testability for a pure function | override policy tests |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| persisted and runtime style selection | N/A | style identity is not time-based |
| thermalling session enter/exit timers | Monotonic | existing coordinator contract remains unchanged |
| replay participation gate for thermalling runtime | Replay session state only | replay bypass must follow replay selection state, not wall time |

Explicitly forbidden comparisons:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - main-safe state mutation in `MapStateStore`
  - existing repository persistence path unchanged
- Primary cadence/gating sensor:
  - style changes only on explicit user action, profile switch, forecast toggle, thermalling enter/exit, or replay gate transitions
- Hot-path latency budget:
  - no new polling loops
  - no per-frame style logic
  - override resolution must be O(1) and transition-only

### 2.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| invalid persisted style key fallback | `AppLogger` once per read path if logging is added | low | do not log profile payloads or raw config JSON | keep only if needed for migration visibility |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - forecast satellite override remains user-driven and may still apply during replay if enabled
  - thermalling runtime automation is live-only and must reset/no-op while replay selection is active
  - style resolution must be deterministic for the same base style + override reasons

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| persisted style key unknown/unsupported | Recoverable | `MapStyleCatalog` | falls back to `Topo` instead of crashing or persisting garbage upward | keep app functional; optional one-shot log | catalog/use-case tests |
| startup bootstrap queues a second style before map ready | Recoverable | `MapInitializer` + `MapRuntimeController` contract | startup should not visibly reapply style twice or churn overlays | remove bootstrap write path and lock the first-apply/post-startup boundary with tests | startup/runtime controller tests |
| map style command arrives before map ready | Recoverable | `MapRuntimeController` | latest style is replayed when the map becomes ready | existing queued latest-only behavior remains | runtime controller tests |
| replay starts during active thermalling session | Recoverable | `ThermallingModeRuntimeWiring` | thermalling auto mode/zoom/style behavior stops and session resets | clear thermalling runtime session and restore manual control | runtime wiring tests |
| user manually selects a base style while thermalling contrast override is active | User Action | style coordinator | manual selection wins for the current session | clear thermalling override and persist the new base style | style coordinator tests |

### 2.5B Identity and Model Creation Strategy

No new IDs or timestamps are created in this IP.

### 2.5C No-Op / Test Wiring Contract

No new production `NoOp` path is planned.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| local style owner reappears in drawer/bottom sheet | SSOT-MAP-01, UI owns rendering only | unit/UI test + review | `MapScreenBottomTabsUiStateTest.kt`, drawer tests if added |
| typed style contract drifts back to raw strings | canonical runtime style contract | unit tests + review | `MapStyleUrlResolverTest.kt`, catalog tests |
| `MapCommand` / UI binding surfaces remain stringly typed | canonical runtime style contract | unit tests + review | catalog tests plus `MapScreenViewModelCoreStateTest.kt` / binding tests |
| `feature:map-runtime` API churn expands scope without user value | module/API boundary rules | plan guard + compatibility tests | `MapStateReader.kt`, `LocationManagerRenderSyncTest.kt` |
| replay still drives thermalling automation | replay determinism rules | runtime wiring tests | `ThermallingModeRuntimeWiringTest.kt` |
| startup double-style apply race remains | startup/runtime ownership rules | targeted startup/runtime controller tests + review | startup binding tests, `MapRuntimeController` tests |
| startup/profile style apply duplicates remain | startup/runtime ownership rules | state/binding tests + review | `MapScreenViewModelCoreStateTest.kt`, targeted wiring tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| no redundant style-triggered reorders/flicker in steady state | `MS-UX-04` | duplicate style owners can trigger avoidable transition churn | zero redundant style-driven reorders with unchanged effective style | targeted runtime tests and evidence run if runtime behavior changes materially | Phase 3 |
| startup map reaches stable style + overlays without extra bootstrap writes | `MS-UX-06` | current startup path includes redundant style reapply seam | no redundant startup style mutation beyond seeded store + startup apply | targeted startup test plus evidence when implemented | Phase 3 |
| overlay style-transition apply duration stays bounded | `MS-ENG-01` | current style reload path reattaches overlays | no regression beyond baseline for real style transitions | evidence run if style transition behavior changes | Phase 5 |
| style-ready -> overlays-ready stabilization remains bounded | `MS-ENG-02` | existing startup/style transition cost | no regression beyond baseline | evidence run if startup/style wiring changes | Phase 3 |

## 3) Data Flow (Before -> After)

Before:

```text
MapStyleRepository/raw config
  -> MainActivityScreen initialMapStyle
  -> AppNavGraph / MapRouteHost / MapScreen / MapScreenRoot / RootEffects / ScaffoldInputs prop chain
  -> Compose startup effect writes style into VM
  -> queued SetStyle can replay after MapInitializer already set startup style
  -> NavigationDrawer keeps its own selected style
  -> forecast bottom sheet keeps a local last-non-satellite style cache
  -> MapStateStore holds one raw string style name
  -> MapStyleUrlResolver resolves scattered string literals
  -> Thermalling runtime has no replay participation gate
```

After:

```text
MapStyleRepository (persisted base style key only)
  -> MapStyleUseCase converts to MapStyleId
  -> MapStateStore owns base style + transient override set + effective style
  -> MapInitializer performs the first style apply for a new map instance only
  -> MapScreenStyleCoordinator mutates store and emits style commands
  -> MapRuntimeController applies post-startup style commands only
  -> drawer renders authoritative base style only
  -> forecast and thermalling paths request transient overrides only
  -> MapStyleCatalog/MapStyleUrlResolver provide one canonical runtime style mapping
  -> ThermallingModeRuntimeWiring resets/no-ops while replay is active
```

## 4) Implementation Phases

### Phase 0 - Seam/code pass and regression lock

- Goal:
  - Lock the current problem seams before production edits.
- Files to change:
  - this plan
  - targeted tests only
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - style catalog fallback expectations
  - drawer/bottom-tabs no-local-owner regression expectations
  - startup double-apply race expectation between `MapInitializer`, `MapComposeEffects`, and `MapRuntimeController`
  - profile import/export string compatibility expectation
  - thermalling replay no-op/reset expectation
- Exit criteria:
  - current failure modes are captured in focused tests
  - no production code moved yet

Phase 0 seam/code pass conclusions:
- `MapStateStore` remains the correct runtime SSOT owner.
- `MapStyleRepository` remains the correct persistence owner.
- `NavigationDrawer` and `MapScreenBottomTabsUiState` are not valid style owners.
- `MapInitializer` and `MapRuntimeController` are both legitimate style-apply seams, but they need an explicit non-overlapping boundary.
- the `initialMapStyle` prop chain is broader than the first pass captured and must be removed end-to-end, not just at `MapComposeEffects`.
- `MapStateReader.mapStyleName` is a cross-module `feature:map-runtime` API seam; this IP should not widen scope by converting it unless a separate module-API slice is explicitly approved.
- `MapCommand.SetStyle`, `MapScreenBindingGroups`, and `MapScreenSectionInputs` are real contract surfaces, not incidental implementation details.
- `MapScreenContentRuntime.kt` and `MapScreenContentRuntimeSupport.kt` participate in the current bottom-tabs style seam and must be included when that local owner is removed.
- profile import/export remains a required string compatibility seam through `MapStyleProfileSettingsContributor`.
- `MapControlsUI.kt` is currently inactive helper debt, not an active owner, but it must not survive as a second raw-style contract if retained.
- replay gating belongs in `ThermallingModeRuntimeWiring`, not in `ThermallingModeCoordinator`.

### Phase 1 - Typed style contract and canonical catalog

- Goal:
  - Replace ad hoc runtime string handling with a catalog-backed contract in `feature:map` without forcing `feature:map-runtime` API churn in this IP.
- Files to change:
  - `MapStyleCatalog.kt` (new)
  - `MapStyleUrlResolver.kt`
  - `MapCommand.kt`
  - `MapScreenBindingGroups.kt`
  - `MapScreenSectionInputs.kt`
  - `MapScreenContentRuntimeSupport.kt`
  - `MapScreenUseCases.kt`
  - `MapControlsUI.kt` (delete if unused or align if retained)
  - resolver/use-case tests
- Ownership/file split changes in this phase:
  - create one canonical style contract owner
  - keep `MapStateReader.mapStyleName` string-backed as a compatibility seam in this IP
  - keep repository persistence values string-backed for compatibility
- Tests to add/update:
  - persisted key -> `MapStyleId` parsing
  - unknown key fallback to `Topo`
  - imperative command/binding typed-style coverage
  - hidden/non-selectable style metadata coverage
  - profile contributor compatibility coverage stays green against the typed runtime boundary
- Exit criteria:
  - runtime mutation and resolution code no longer relies on arbitrary raw strings for style identity
  - `feature:map-runtime` `MapStateReader` remains stable in this slice
  - one catalog owns labels, keys, and URLs

### Phase 2 - Runtime style owner consolidation

- Goal:
  - Move all runtime style state to `MapStateStore` and remove UI-local style owners.
- Files to change:
  - `MapStateStore.kt`
  - `MapStyleOverridePolicy.kt` (new)
  - `MapScreenStyleCoordinator.kt` (new)
  - `NavigationDrawer.kt`
  - `MapScreenScaffoldInputs.kt`
  - `MapScreenSectionInputs.kt`
  - `MapScreenContentRuntime.kt`
  - `MapScreenContentRuntimeSupport.kt`
  - `MapScreenBottomTabsUiState.kt`
  - `MapScreenContentRuntimeSections.kt`
- Ownership/file split changes in this phase:
  - drawer becomes read-only for base style
  - forecast satellite path becomes a transient override caller only
  - style precedence lives in a focused policy file, not in UI or the ViewModel
- Tests to add/update:
  - effective style precedence tests
  - drawer selected-state tests against authoritative base style
  - bottom-sheet tests proving no local restore owner remains
  - bottom-tabs remember/runtime pass-through tests
- Exit criteria:
  - no Compose-local map style state remains authoritative
  - effective style resolves from one runtime owner only

### Phase 3 - Startup/profile/bootstrap path cleanup

- Goal:
  - Remove redundant startup writes and profile/bootstrap duplication.
- Files to change:
  - `MainActivityScreen.kt`
  - `AppNavGraph.kt`
  - `MapRouteHost.kt`
  - `MapScreen.kt`
  - `MapComposeEffects.kt`
  - `MapScreenRoot.kt`
  - `MapScreenRootEffects.kt`
  - `MapScreenScaffold.kt`
  - `MapScreenScaffoldInputs.kt`
  - `MapScreenScaffoldInputModel.kt`
  - `MapInitializer.kt`
  - `MapRuntimeController.kt`
  - `MapScreenProfileSessionCoordinator.kt`
  - `TaskRouteScreen.kt`
  - profile contributor tests if compatibility assertions need adjustment
  - `docs/ARCHITECTURE/PIPELINE.md`
- Ownership/file split changes in this phase:
  - startup base style comes only from the seeded `MapStateStore`; no app-shell prop round-trip remains
  - `MapInitializer` owns first style apply during map creation only
  - `MapRuntimeController` owns post-startup style changes and true pre-ready replay only
  - profile switch remains profile-session owned, but delegates style mutation through the style coordinator
  - secondary map surface stops bypassing the canonical resolver
- Tests to add/update:
  - startup style bootstrap test
  - startup no-double-apply race test
  - true pre-ready style commands still replay latest-only after startup ownership cleanup
  - profile switch reapply test
  - profile import/export style round-trip compatibility test
  - task-screen style resolver usage test if practical
- Exit criteria:
  - `initialMapStyle` prop no longer acts as a second bootstrap owner and no map shell API still depends on it
  - startup first-apply vs post-startup apply ownership is explicit and test-locked
  - profile switch and startup use one authoritative style path
  - no direct inline MapTiler style URL remains on touched map surfaces

### Phase 4 - Thermalling replay guard and transient override hardening

- Goal:
  - Make thermalling runtime safe in replay and safe to coexist with transient style overrides.
- Files to change:
  - `MapScreenThermallingRuntimeBinding.kt`
  - `ThermallingModeRuntimeWiring.kt`
  - `ThermallingModeRuntimeWiringTest.kt`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Ownership/file split changes in this phase:
  - replay gate is integration-owned in runtime wiring
  - coordinator stays focused on monotonic enter/exit and restore policy only
- Tests to add/update:
  - replay selection active -> no thermalling actions
  - replay starts mid-session -> reset path
  - live resumes after replay -> thermalling automation can re-enter cleanly
- Exit criteria:
  - thermalling runtime is explicitly live-only
  - replay determinism contract is restored

### Phase 5 - Thermalling contrast style implementation on the cleaned seam

- Goal:
  - Add the requested temporary high-contrast thermalling basemap on top of the cleaned architecture.
- Files to change:
  - `MapStyleCatalog.kt`
  - `MapStyleOverridePolicy.kt`
  - thermalling settings files in `feature:profile`
  - thermalling runtime/style coordinator files in `feature:map`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Ownership/file split changes in this phase:
  - add hidden runtime-only style ID for the chosen contrast map
  - thermalling requests an override only; it does not persist style
  - forecast satellite keeps higher precedence than thermalling contrast
- Planned product defaults:
  - initial contrast style candidate: MapTiler `Dataviz Light`
  - hidden runtime-only style ID
  - `forecast satellite > thermalling contrast > base style`
  - if the saved base style is `Satellite`, thermalling contrast does not auto-override it in V1
- Tests to add/update:
  - thermalling enter applies transient style once
  - thermalling exit restores base style
  - manual base style selection during active thermalling clears the thermalling override
  - replay never applies thermalling style override
- Exit criteria:
  - requested thermalling contrast behavior works without corrupting saved style or replay behavior

## 5) Test Plan

- Unit tests:
  - `MapStyleCatalog` parse/selectability/URL tests
  - `MapStyleOverridePolicy` precedence tests
  - `MapStateStore` effective-style state tests
  - `MapScreenBottomTabsUiStateTest` and `MapScreenBottomTabsUiStateRememberTest` ownership/restore tests
  - startup style bootstrap and no-double-apply tests
  - `ThermallingModeRuntimeWiringTest` replay no-op/reset coverage
- Replay/regression tests:
  - thermalling runtime replay bypass
  - repeated replay/live transitions do not leave stale thermalling session state
- UI/instrumentation tests:
  - optional drawer selected-style rendering test if unit coverage is insufficient
- Degraded/failure-mode tests:
  - unknown persisted style fallback
  - startup bootstrap does not enqueue a second style apply after `MapInitializer` already set the initial style
  - style command before map ready keeps latest-only behavior
- Boundary tests for removed bypasses:
  - startup no longer depends on `initialMapStyle` round-trip write or shell prop threading
  - bottom-sheet no longer owns style restore cache
  - profile import/export still round-trips persisted map style keys
  - `feature:map-runtime` fake/read-only consumers remain compatible because `MapStateReader.mapStyleName` stays string-backed in this IP
- Existing suite anchors to update:
  - `MapStateStoreTest.kt`
  - `MapStyleUrlResolverTest.kt`
  - `MapScreenBottomTabsUiStateTest.kt`
  - `MapScreenBottomTabsUiStateRememberTest.kt`
  - `MapScreenViewModelCoreStateTest.kt`
  - `MapRuntimeControllerWeatherStyleTest.kt`
  - `ThermallingModeRuntimeWiringTest.kt`
  - `AppProfileSettingsSnapshotProviderTest.kt`
  - `AppProfileSettingsRestoreApplierProfileScopedSectionsTest.kt`
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Runtime style policy | unit tests + resolver/state regressions | catalog, policy, and store tests |
| Replay/runtime integration | deterministic runtime tests | `ThermallingModeRuntimeWiringTest.kt` |
| Persistence/settings compatibility | round-trip parse/fallback tests | use-case/repository boundary tests plus profile contributor restore tests |
| Ownership move / bypass removal | boundary lock tests | drawer/bottom-tabs/startup tests |
| Cross-module compatibility seam | no avoidable API churn | `MapStateReader` consumers and fake-reader tests stay green |
| Performance-sensitive path | SLO evidence when style wiring lands | targeted mapscreen evidence run if Phase 3 or 5 changes runtime timings materially |

Required checks before merge-ready implementation:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Additional evidence when runtime timings materially change:

```bash
scripts/qa/run_mapscreen_evidence.bat
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| typed style contract expands beyond the smallest useful slice | delayed delivery | preserve existing persisted strings and avoid config migration in this IP | XCPro Team |
| typed-style cleanup drags `feature:map-runtime` into avoidable API churn | larger refactor, more test fallout, possible ADR requirement | keep `MapStateReader.mapStyleName` string-backed in this IP and normalize behind `feature:map` boundaries | XCPro Team |
| `MapScreenViewModel.kt` grows further | maintainability loss | keep VM delegates thin and move style orchestration into a focused coordinator file | XCPro Team |
| startup cleanup removes only one seam and leaves the double-apply race alive | redundant style reloads and overlay churn remain | treat the race as a first-class Phase 3 target and lock the `MapInitializer`/`MapRuntimeController` boundary with tests | XCPro Team |
| style override policy conflicts with forecast satellite UX | incorrect effective style | centralize precedence in one policy file with tests before feature rollout | XCPro Team |
| replay gate is added in the wrong layer | hidden live/replay divergence | keep the gate in `ThermallingModeRuntimeWiring` only and add targeted tests | XCPro Team |
| typed runtime cleanup breaks profile snapshot/restore compatibility | imported profiles lose or corrupt map style | keep `MapStyleProfileSettingsContributor` string-backed and add contributor round-trip coverage | XCPro Team |
| style reload transition regresses mapscreen SLOs | flicker/startup/runtime performance regressions | phase-gate the real thermalling contrast feature behind mapscreen evidence if transition counts or timings move | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  - This IP cleans up existing owners and runtime seams inside established map/profile boundaries.
- Why this belongs in an ADR instead of plan notes:
  - No new module boundary or durable public architecture contract is introduced beyond a focused internal cleanup.

## 7) Readiness Verdict

`Ready with corrections`

Corrections already baked into this plan:
- keep `MapStateStore` as the runtime SSOT instead of inventing a second state owner
- keep repository persistence string-backed for compatibility, but move typed semantics to `feature:map`
- remove UI-local style ownership before adding thermalling contrast style
- add the missing replay guard before enabling any thermalling style override

Open decisions remaining before coding starts:
- confirm the hidden thermalling contrast style name and final MapTiler style URL (`Dataviz Light` is the recommended V1 default)
- confirm whether the task screen is included in this slice or deferred to the next cleanup slice if schedule pressure is high
- confirm whether inactive `MapControlsUI.kt` helper debt is deleted in Phase 1 or left to a separate dead-code cleanup follow-up
