# Map Screen Shell Ownership Extraction Plan

## 0) Metadata

- Title: Extract and narrow the MapScreen shell ownership seam
- Owner: Codex
- Date: 2026-03-14
- Issue/PR: TBD
- Status: In Review
- Execution rules:
  - Scope freeze: this plan covers only the MapScreen shell seam.
  - No ad hoc cleanup, rename-only churn, or unrelated feature work.
  - Keep behavior parity through Phases 1-3; treat them as ownership moves, not product changes.
  - Land one phase at a time with independent rollback.
  - Do not split `MapScreenViewModel` first; narrow the shell seam first so the ViewModel change is evidence-driven.

## 0.1) Progress Snapshot

- Phase 0:
  - seam-lock and baseline planning completed
- Phase 1:
  - completed
  - `MapScreenContentRuntime.kt` no longer owns the extracted QNH, forecast/weather, bottom-tab, and wind-tap state directly
- Phase 2:
  - completed
  - scaffold/content output contract narrowed into grouped `scaffold` and `content` inputs
- Phase 3:
  - completed
  - root/binding/scaffold assembly narrowed to grouped bindings and grouped root UI bindings
- Phase 4:
  - completed
  - `MapScreenViewModel.kt` now delegates the proven profile/session and WeGlide prompt seams
  - `MapScreenViewModel.kt` reduced to `332` lines and is back under the enforced hotspot budget
- Phase 4B:
  - completed on 2026-03-15
  - the retained task shell moved out of `MapScreenViewModel.kt` into `MapScreenTaskShellCoordinator.kt`
  - AAT edit-mode UI state now derives from the task authority seam instead of a local ViewModel mirror
- Verification snapshot after Phase 4A:
  - `./gradlew enforceRules --no-configuration-cache` passed
  - `./gradlew testDebugUnitTest --no-configuration-cache` passed
  - `./gradlew assembleDebug --no-configuration-cache` passed
  - focused manual smoke pass completed on 2026-03-14 for map entry, drawer/back, task panel, bottom tabs, QNH, traffic/weather overlays, replay, and WeGlide prompt paths
- Close-out review:
  - see `docs/refactor/Map_Screen_Shell_Closeout_Review_2026-03-14.md`
- Remaining close-out gap:
  - attach/refresh the impacted MapScreen SLO evidence package if this seam is being promoted under the strict map visual gate

## 1) Scope

- Problem statement:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` is still the broad screen composition root with a large constructor surface and cross-feature orchestration.
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`,
    `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt`,
    and `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
    still fan a wide shell contract through the screen.
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` still mixes main map rendering with shell-local state for QNH, bottom tabs, wind callout sizing, forecast/weather collection, traffic adaptation, and prompt plumbing.
  - The result is unclear ownership, broad review blast radius, file-size pressure, and avoidable recomposition/fanout risk on the main screen path.
- Why now:
  - This is the highest-ROI ownership seam left in the active code path.
  - The recent runtime extractions made the shell seam explicit; the next professional move is to finish that boundary rather than start a new refactor track.
  - Fixing this seam first reduces risk for later forecast/weather, traffic, and prompt ownership work because those changes will stop going through one monolithic shell host.
- In scope:
  - MapScreen shell decomposition inside `feature:map`.
  - Focused shell UI hosts for shell-local ephemeral state.
  - Grouped scaffold/content input models that follow ownership boundaries.
  - Root/binding decomposition so `MapScreenRoot` becomes an assembler rather than a mixed owner.
  - Narrowing `MapScreenViewModel` only where Phases 1-3 prove a stable seam.
- Out of scope:
  - `:feature:map-runtime` owner moves.
  - Traffic/forecast/weather business logic changes.
  - Profile/card, IGC, or navdrawer ownership work.
  - UI redesign or feature behavior changes.
  - Broad "cleanup" passes not tied to this seam.
- User-visible impact:
  - No intended product behavior change.
  - The goal is ownership clarity, smaller review slices, and lower future change risk.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Map screen state, style, tracking, zoom, recenter, and task-edit UI state | `MapStateStore` through `MapStateReader` and `MapStateActions` | screen state flows and action methods | shell-local mirrors in `MapScreenContentRuntime` or extracted hosts |
| Screen intents, prompt actions, replay entry points, and screen-level orchestration | `MapScreenViewModel` | methods and screen-facing state | duplicate intent handling in composables or host files |
| Forecast/weather state | existing forecast/weather owners | feature state flows and view models | shell-owned cached copies of feature state |
| Traffic state and traffic actions | existing traffic owners plus `MapTrafficUiBinding` / `MapTrafficUiActions` | grouped traffic binding models | direct traffic state assembly in the root content host |
| QNH calibration state and live flight data | existing QNH and flight-data owners | read-only inputs into QNH UI | shell-owned authoritative QNH values |
| WeGlide prompt state | existing WeGlide owner surfaced through the screen VM | prompt UI state | prompt lifecycle duplicated in shell UI state |
| Shell-only dialog, sheet, selected-tab, wind-callout, and viewport-size state | dedicated shell UI hosts created by this plan | local remembered state in focused host files | persistence in the ViewModel or duplicated remembered state across hosts |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI shell -> grouped shell bindings -> screen VM / feature facades -> repositories / data`

- Modules/files touched:
  - `feature:map`
  - documentation under `docs/refactor`
- Any boundary risk:
  - do not move business logic into composables while splitting files
  - do not make `feature:map` the owner of forecast/weather or traffic state it only renders
  - do not replace one giant model with another giant "shell facade"

### 2.2A Reference Pattern Check (Mandatory)

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenRuntimeDependencies.kt` | already groups a runtime seam into a narrow contract | grouped dependency model instead of raw constructor fanout | reuse the grouping style for shell inputs, not runtime owners |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenAuxiliaryPanelsInputs.kt` | already uses focused UI input groups for QNH and prompt rendering | narrow UI input models per section | extend the pattern to shell-owned hosts, not just data models |
| `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficUiBindings.kt` | already separates traffic data and traffic actions into focused models | owner-specific binding/action models | reuse as-is; do not invent a new traffic binding style |

### 2.2B Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| QNH dialog visibility, input, and local validation messaging | `MapScreenContentRuntime.kt` | dedicated shell QNH host | UI-only state should not live in the monolithic content host | host state tests + manual QNH smoke |
| Bottom-tab selection, sheet visibility, and last non-satellite map style memory | `MapScreenContentRuntime.kt` | dedicated shell bottom-tabs host | isolates shell-only interaction state from main map composition | host state tests + map-style/bottom-sheet smoke |
| Wind-arrow tap callout sizing and viewport bookkeeping | `MapScreenContentRuntime.kt` | dedicated shell wind-callout host | keeps display-only geometry state close to its renderer | host state tests + tap/callout smoke |
| Forecast/weather UI collection and shell adaptation | `MapScreenContentRuntime.kt` | focused forecast/weather host | removes leaf-feature wiring from the root content host | integration tests + SLO gate on ordering/startup |
| Traffic shell adaptation | `MapScreenContentRuntime.kt` | focused traffic host using existing traffic binding models | reduces cross-feature coupling in the content host | traffic smoke + targeted binding tests |
| Giant scaffold/content contract | `MapScreenScaffoldInputs.kt` and `MapScreenScaffoldInputModel.kt` | grouped section input models | makes ownership boundaries explicit at the shell edge | builder tests + compile checks |
| Broad binding assembly | `MapScreenBindings.kt` | focused binding groups per concern | stops the root from carrying unrelated feature details in one model | binding tests + recomposition checks |
| Cross-feature setup burden inside `MapScreenViewModel.kt` | `MapScreenViewModel.kt` | owner-specific coordinators or grouped dependency bundles proven by the shell seam | narrows constructor/orchestration only after the boundary is real | ViewModel tests + constructor/file-budget review |

### 2.2C Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` | direct collection of forecast/weather and traffic feature state in the monolithic host | focused section hosts with grouped inputs | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` | direct ownership of QNH, bottom-tab, and wind-callout shell state | dedicated shell hosts for those UI states | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt` | pure pass-through of the giant scaffold model | grouped content-section inputs | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt` | assembles one broad contract for unrelated concerns | grouped builder functions per section | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt` | acts as a giant data carrier | section-specific models aligned to ownership | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt` | combines unrelated feature outputs in one binding object | focused binding groups | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | assembles and forwards the entire broad shell surface | assembler over grouped bindings and grouped section inputs | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | broad constructor/orchestration surface that still reflects shell fanout | grouped collaborators or coordinators only where Phases 1-3 prove the seam | Phase 4 |

### 2.2D File Ownership Plan (Mandatory)

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` | Existing | high-level composition of focused content hosts only | this file is already the live content entry | keep as thin shell composition, not a state owner | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt` | Existing | shell bridge from scaffold inputs into grouped content inputs | already the handoff point | should remain a thin bridge, not a second composition root | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt` | Existing | grouped section input model declarations | already holds scaffold models | keep models close to scaffold assembly | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt` | Existing | grouped input builders and shell-only assembly logic | already builds scaffold inputs | do not push assembly into the ViewModel | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt` | Existing | temporary host for binding-group extraction during Phase 3 | existing binding hub | split before extending | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | Existing | top-level assembler over grouped bindings and managers | this is the screen root | should orchestrate, not adapt leaf-feature state | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | Existing | single screen state owner and intent handler | must remain the screen VM | do not move UI state into composables or repositories | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBottomTabsHost.kt` | New | shell-only bottom-tab state and behavior | focused UI ownership slice | too UI-specific for VM or domain | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenQnhDialogHost.kt` | New | shell-only QNH dialog state and input adaptation | focused UI ownership slice | keep QNH business rules in existing owners; this host only manages UI state | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenWindCalloutHost.kt` | New | shell-only wind callout geometry and viewport state | focused UI ownership slice | display-only geometry belongs in UI | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenForecastWeatherHost.kt` | New | forecast/weather state collection and shell adaptation for rendering | focused section owner | keeps leaf-feature state collection out of the root content host | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenTrafficHost.kt` | New | traffic-specific shell adaptation using existing traffic models | focused section owner | reuses traffic binding patterns instead of broad shell wiring | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSectionInputs.kt` | New | grouped section input models for the content path | makes section contracts explicit | more precise than extending the giant scaffold model | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt` | New | focused binding groups split out of `MapScreenBindings.kt` | needed to keep binding concerns narrow | avoids another oversized binding file | New file |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionDependencies.kt` | New | concern-specific injected dependency group for profile/style/layout routing | reduces ViewModel constructor width without creating a mega bundle | scoped to one proven concern only | New file |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionCoordinator.kt` | New | profile-scoped style, units, trail, QNH, and variometer routing | proven by the Phase 3 root/profile seam | belongs in map feature VM layer, not UI | New file |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenWeGlidePromptBridge.kt` | New | prompt collection and confirm/dismiss resolution for WeGlide post-flight prompts | prompt seam is already explicit in shell/UI | keeps prompt orchestration out of the main ViewModel body | New file |

Rules for the new files:

- Keep UI-only state in the `ui/` hosts.
- Keep `MapScreenViewModel` as the single screen-state owner.
- Keep Phase 4 collaborators concern-specific; do not introduce one generic `MapScreenViewModelCollaborators` facade.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Replay session timing and replay-state-derived rendering | Replay | unchanged by this shell refactor and must remain deterministic |
| Compose animation/frame-driven shell rendering | Monotonic / frame cadence | shell hosts may react to frames but do not author time |
| QNH dialog input, bottom-tab selection, prompt visibility, wind-callout sizing | Not time-based | these are shell-local UI values only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - `Main`: all shell UI hosts and Compose state handling
  - existing background owners remain unchanged; this plan does not add new background loops
- Primary cadence/gating sensor:
  - existing map render and feature-owner update cadence only
- Hot-path latency budget:
  - do not add new root composition work
  - root/binding fanout should shrink, not grow

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged from current behavior
  - shell hosts may render replay state but must not cache or derive a second replay source of truth

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Business logic drifts into new composable hosts | `ARCHITECTURE.md`, `CODING_RULES.md` | review + targeted host tests | new shell host files in `feature/map/src/main/java/com/example/xcpro/map/ui/` |
| Duplicate screen or feature state is introduced during file splits | SSOT rules in `ARCHITECTURE.md` | review + unit tests | host state tests and builder tests |
| Giant shell contract is replaced by a renamed giant contract | file ownership and narrow-file rules in `AGENTS.md` | review + line-budget gate | `MapScreenScaffoldInputModel.kt`, `MapScreenSectionInputs.kt`, `MapScreenBindingGroups.kt` |
| Root recomposition pressure stays broad after the split | MapScreen SLO contract | perf evidence + review | `MS-ENG-09` evidence package for Phases 2-3 |
| Replay behavior drifts while moving shell boundaries | replay determinism rules | unit/integration tests | replay-related `MapScreenViewModel` and shell wiring tests |
| Unrelated map behavior regresses during shell work | MapScreen validation plan | SLO evidence + smoke | impacted `MS-UX-*` / `MS-ENG-*` gates |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Drag/edit interaction remains stable while content ownership is split | `MS-UX-02` | `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md` baseline capture | no regression from baseline and no full teardown per move | drag scenario evidence + task-edit smoke | Phase 1 |
| Overlay ordering and shell transition behavior remain stable | `MS-UX-04` | same baseline package | no steady-state redundant reorder regression | weather/traffic ordering evidence | Phase 1 |
| Startup readiness and redraw behavior do not regress | `MS-UX-06` | same baseline package | cold/warm startup stays within baseline contract | startup profile evidence | Phase 1 |
| Lifecycle sync stays single-owner while shell hosts are introduced | `MS-ENG-06` | same baseline package | `<= 1` sync per owner-state transition | integration evidence | Phase 1 |
| Render cadence owner stays singular | `MS-ENG-10` | same baseline package | duplicate frame-owner count remains `0` | cadence harness evidence | Phase 1 |
| Marker stability, replay scrubbing, and overlay apply behavior remain stable after contract narrowing | `MS-UX-03`, `MS-UX-05`, `MS-ENG-01`, `MS-ENG-08` | same baseline package | no regression from baseline | replay/traffic/weather evidence | Phase 2 |
| Root recomposition pressure materially narrows after binding and contract split | `MS-ENG-09` | same baseline package | root recomposition count `<= baseline * 0.60` | compose recomposition harness | Phase 2 |
| Mixed-load pan/zoom/rotate stays smooth after root/binding decomposition | `MS-UX-01` | same baseline package | no regression; thresholds still pass | mixed-load stress evidence | Phase 3 |
| Final shell seam is release-safe | all impacted mandatory IDs above | prior phase evidence | pass all impacted mandatory SLOs or record approved deviation | final evidence package and gate result | Phase 4 |

Evidence path convention for this plan:

- `artifacts/mapscreen/phase0/map-shell-seam-baseline/<timestamp>/`
- `artifacts/mapscreen/phase1/map-shell-content-hosts/<timestamp>/`
- `artifacts/mapscreen/phase2/map-shell-contracts/<timestamp>/`
- `artifacts/mapscreen/phase3/map-shell-bindings/<timestamp>/`
- `artifacts/mapscreen/phase4/map-shell-viewmodel/<timestamp>/`

## 3) Data Flow (Before -> After)

Before:

```text
MapScreenViewModel
  -> MapScreenRoot
    -> MapScreenBindings + MapScreenManagers
      -> rememberMapScreenScaffoldInputs(...)
        -> MapScreenScaffoldContentHost(...)
          -> MapScreenContentRuntime(...)
            -> direct shell-local UI state + direct leaf-feature state collection
              -> map sections / overlay stack
```

After:

```text
MapScreenViewModel
  -> MapScreenRoot
    -> grouped binding groups + grouped scaffold section inputs
      -> MapScreenScaffoldContentHost(...)
        -> thin MapScreenContentRuntime(...)
          -> focused shell hosts
            -> BottomTabsHost
            -> QnhDialogHost
            -> WindCalloutHost
            -> ForecastWeatherHost
            -> TrafficHost
          -> render-only map sections / overlay stack
```

The ViewModel remains the single screen owner. The shell gets narrower by moving
UI-only state and leaf-feature wiring into focused hosts, not by creating a
second source of truth.

## 4) Implementation Phases

### Phase 0 - Seam Lock and Baseline

- Goal:
  - lock the seam boundary, freeze non-goals, and capture baseline evidence for impacted map-screen SLOs
- Files to change:
  - `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md`
  - evidence artifacts under `artifacts/mapscreen/phase0/map-shell-seam-baseline/<timestamp>/`
- Ownership/file split changes in this phase:
  - none in production code
  - confirm exact ownership targets and provisional file names before code changes
- Tests to add/update:
  - none required before baseline capture
  - define the exact smoke checklist for QNH, bottom tabs, weather/traffic overlays, replay, and prompt paths
- Exit criteria:
  - baseline evidence captured for impacted SLOs
  - current call sites and state owners documented
  - no production code changes mixed into the planning/baseline package

### Phase 1 - Split `MapScreenContentRuntime` by Ownership

- Goal:
  - remove shell-local state and direct leaf-feature state collection from `MapScreenContentRuntime.kt` without changing behavior
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenAuxiliaryPanelsInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBottomTabsHost.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenQnhDialogHost.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenWindCalloutHost.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenForecastWeatherHost.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenTrafficHost.kt`
- Ownership/file split changes in this phase:
  - QNH dialog UI state moves into `MapScreenQnhDialogHost.kt`
  - bottom-tab UI state moves into `MapScreenBottomTabsHost.kt`
  - wind-callout geometry state moves into `MapScreenWindCalloutHost.kt`
  - forecast/weather collection and adaptation moves into `MapScreenForecastWeatherHost.kt`
  - traffic adaptation moves into `MapScreenTrafficHost.kt`
  - `MapScreenContentRuntime.kt` becomes a thin composition shell
- Tests to add/update:
  - host state tests for QNH, bottom-tab, and wind-callout state
  - focused smoke coverage for traffic and forecast/weather rendering paths
  - regression checks for prompt and replay entry points touched by the content split
- Exit criteria:
  - `MapScreenContentRuntime.kt` no longer owns the moved UI state directly
  - `MapScreenContentRuntime.kt` is materially smaller and no longer collects unrelated leaf-feature state in one place
  - behavior parity confirmed by Phase 1 SLO gates and smoke checks

### Phase 2 - Narrow the Scaffold and Content Contracts

- Goal:
  - replace the giant scaffold/content contract with grouped section models aligned to ownership
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSectionInputs.kt`
- Ownership/file split changes in this phase:
  - section-specific input models replace the broad carrier model
  - scaffold builders assemble grouped models per concern instead of one broad shell object
  - content host becomes a thin handoff from scaffold to focused content hosts
- Tests to add/update:
  - builder tests for grouped section inputs
  - boundary tests proving removed bypasses no longer require the giant contract
  - replay/traffic/weather regression checks for the new grouped handoff
- Exit criteria:
  - no giant pass-through model remains in the scaffold/content path
  - grouped section models map cleanly to the Phase 1 hosts
  - Phase 2 SLO gates pass, including root recomposition pressure evidence

### Phase 3 - Decompose Bindings and Narrow `MapScreenRoot`

- Goal:
  - split broad binding assembly by concern so the root becomes an assembler only
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- Ownership/file split changes in this phase:
  - split the current broad `MapScreenBindings.kt` collection into focused groups for map/session/task/traffic state
  - move root-only UI collection out of `MapScreenRoot.kt` into focused root binding helpers
  - narrow `rememberMapScreenScaffoldInputs(...)` so the root passes grouped bindings instead of dozens of raw parameters
  - keep `MapScreenRootHelpers.kt` and `MapScreenRootEffects.kt` unchanged unless compile fallout proves otherwise
  - `MapScreenRoot.kt` stops adapting leaf-feature state directly and remains the top-level assembler over grouped bindings, managers, and section inputs
- Tests to add/update:
  - binding group tests
  - root assembly tests for stable wiring
  - mixed-load perf evidence focused on root fanout and pan/zoom behavior
- Exit criteria:
  - `MapScreenRoot.kt` is assembler-only
  - `MapScreenBindings.kt` is either reduced to a thin wrapper or removed in favor of grouped bindings
  - `rememberMapScreenScaffoldInputs(...)` no longer requires the current broad root parameter fanout
  - Phase 3 mixed-load SLO gate passes with no regression in earlier gates

### Phase 4 - Narrow `MapScreenViewModel` from Proven Seams Only

- Goal:
  - reduce `MapScreenViewModel.kt` constructor/orchestration width only where Phases 1-3 prove a real ownership seam
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionDependencies.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenWeGlidePromptBridge.kt`
  - affected `MapScreenViewModel` tests
- Ownership/file split changes in this phase:
  - keep `MapScreenViewModel` as the single screen-state owner
  - extract the proven profile/style/layout routing seam into a dedicated coordinator backed by a concern-specific injected dependency group
  - extract the proven WeGlide prompt collection/confirm-dismiss seam into a dedicated bridge
  - keep direct screen-state flows, `MapStateStore`, and thin event-routing methods in the ViewModel
  - do not introduce a single mega collaborator that merely repackages the old constructor
- Tests to add/update:
  - update `MapScreenViewModelTestRuntime.kt` for the narrowed constructor surface
  - keep the existing profile-scope assertions in `MapScreenViewModelCoreStateTest.kt`
  - add targeted prompt-resolution tests for the WeGlide bridge or the ViewModel seam
  - final end-to-end smoke for QNH, bottom tabs, traffic, forecast/weather, replay, and prompts
- Exit criteria:
  - constructor/orchestration surface is materially narrower
  - screen state ownership is unchanged
  - all impacted mandatory SLOs pass, or an approved deviation is recorded with issue, owner, and expiry
  - quality rescore is completed with evidence

## 5) Test Plan

- Unit tests:
  - shell host state tests for QNH, bottom tabs, and wind-callout geometry
  - grouped builder tests for scaffold section inputs and binding groups
  - targeted `MapScreenViewModel` collaborator tests if Phase 4 creates them
- Replay/regression tests:
  - replay entry points and replay-related UI wiring touched by the shell split
  - no-op behavior checks where grouped contracts replace direct fanout
- UI/instrumentation tests (if needed):
  - map screen smoke for bottom tabs, weather/traffic overlays, QNH dialog, and replay transitions
  - connected-device map interaction checks if a device is available
- Degraded/failure-mode tests:
  - invalid QNH input flow
  - forecast/weather unavailable state
  - traffic selection/dismiss flows after contract narrowing
- Boundary tests for removed bypasses:
  - scaffold/content path no longer depends on one giant input carrier
  - root and content hosts no longer own unrelated leaf-feature state

Required checks:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

Evidence capture guidance:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 -Resume -FromPhase <phaseId>
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| The split turns into rename-only churn without changing ownership | High | require each new file to own one clear concern and one explicit state boundary | Codex |
| UI-only state is pushed into the ViewModel to make the split easier | High | keep ephemeral dialog/sheet/callout state in focused UI hosts and review SSOT table on each phase | Codex |
| A new grouped contract becomes another god-object | High | cap grouped models to one concern and reject bundles that cross unrelated feature boundaries | Codex |
| Forecast/weather or traffic business logic accidentally moves into shell hosts | High | shell hosts may collect and adapt state only; business rules remain in existing owners | Codex |
| Replay/live behavior drifts during shell narrowing | High | keep replay ownership unchanged and rerun replay regression checks each phase | Codex |
| Root recomposition improves on paper but user-visible smoothness regresses | Medium | gate phase exits on the impacted `MS-UX-*` and `MS-ENG-*` evidence, not just code shape | Codex |
| Phase 4 hides coupling behind a facade instead of removing it | High | do not start Phase 4 until shell contracts are narrow and reject any mega collaborator | Codex |
| Unrelated repo failures obscure signal | Medium | treat pre-existing failures as baseline and do not mix unrelated cleanup into this plan | Codex |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- No ad hoc cleanup or unrelated refactors mixed into this seam
- `MapScreenContentRuntime.kt` no longer owns unrelated leaf-feature state in one monolithic host
- Scaffold/content contracts are grouped by concern rather than carried as one broad surface
- `MapScreenRoot.kt` is assembler-only after Phase 3
- `MapScreenViewModel.kt` remains the single screen-state owner after Phase 4
- Replay behavior remains deterministic
- Impacted map-screen SLOs pass, or an approved deviation is recorded in `KNOWN_DEVIATIONS.md` with issue, owner, and expiry

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 content-host split
  - Phase 2 scaffold/content contract narrowing
  - Phase 3 binding/root decomposition
  - Phase 4 ViewModel narrowing
- Recovery steps if regression is detected:
  - revert only the last completed phase
  - keep already-useful tests and evidence scripts if they remain architecture-safe
  - rerun `python scripts/arch_gate.py`, `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug`
  - preserve failing evidence under `artifacts/mapscreen/rollback/<issue-id>/`
