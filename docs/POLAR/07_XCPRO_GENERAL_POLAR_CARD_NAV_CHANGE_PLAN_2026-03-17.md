# XCPro General Polar Authority + Card/Navigation Completion Plan

## 0) Metadata

- Title: General Polar authority for all polar-dependent cards and navigation/glide outputs
- Owner: XCPro maintainers (agent-authored)
- Date: 2026-03-17
- Issue/PR: TBD
- Status: Draft
- Relationship to earlier polar docs:
  - `05_XCPRO_POLAR_PHASE0_PHASE1_CHANGE_PLAN_2026-03-12.md` completed the flight-only `polar_ld` / `best_ld` slice.
  - `06_XCPRO_TASK_AWARE_GLIDE_CARD_PLAN_2026-03-12.md` completed the racing-task finish-glide MVP for `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0`.
  - This plan extends those slices into one release-grade contract: the polar configured in General Polar becomes the one authoritative polar everywhere polar-dependent values are calculated or shown, while remaining waypoint/task cards are completed without card-side math.

## 1) Scope

- Problem statement:
  - XCPro already has a real active-polar path, but it is only partially standardized end to end.
  - Some pilot-facing outputs already use the active General Polar through `GliderRepository -> PolarStillAirSinkProvider`.
  - Some surfaces still derive preview/state directly from lower-level helpers, and several navigation/task cards are still placeholder-only because the upstream card contract is incomplete.
  - There is no single current change plan that covers both:
    - "General Polar is the one authority everywhere polar-dependent math runs", and
    - "all remaining relevant navigation/task cards are completed on top of that authority."
- Why now:
  - The user request is explicit: the polar set in General Polar should be the one authoritative polar throughout the app wherever polar-dependent values are calculated or shown.
  - The repo already has enough runtime seams to finish this safely, but only if the remaining gaps are solved through SSOT/domain boundaries rather than ad hoc formatter or UI work.
- In scope:
  - define exactly what "General Polar" means in XCPro
  - keep `GliderRepository` as the General Polar SSOT
  - standardize one read/compute contract for all polar-dependent runtime consumers
  - complete the remaining navigation/task card data path without putting business math in cards or Compose
  - remove or time-box remaining bypasses that undermine the approved seams
  - add a bounded research lane for source-backed glider polars where built-in catalog quality is currently weak
- Out of scope:
  - forcing polar semantics into cards that are not polar-dependent
  - changing the meaning of `ld_curr`
  - a broad glider-catalog rebuild as a blocker for the SSOT work
  - reworking unrelated map, overlay, or task-edit UX
- User-visible impact:
  - when the pilot changes General Polar, every polar-dependent value updates from the same authority
  - existing flight-only and finish-glide cards stay semantically consistent
  - remaining waypoint/task cards move from placeholder-only to real values when their upstream domain contract exists
  - fallback/no-polar/degraded states become explicit instead of implicit
- Rule class touched: Invariant

### 1.1 Definition: "General Polar"

For this plan, "General Polar" means the profile-scoped active polar edited through:

`General Settings -> Polar -> PolarSettingsScreen`

Current entrypoint:

- `app/src/main/java/com/example/xcpro/appshell/settings/GeneralSettingsRouteSubSheets.kt`

Authoritative owner:

- `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`

It includes:

- selected model
- effective model
- fallback-polar state
- manual `threePointPolar`
- bugs
- ballast
- IAS min/max overrides
- other glider config fields that alter still-air sink or bounds

It does not include:

- MacCready / Auto MC

Those remain owned by the Levo settings/preferences path and are solver inputs, not polar ownership.

### 1.2 Pilot-Facing Card Inventory

Only cards whose value or validity depends on the active polar are in scope for the "General Polar everywhere" rule. Navigation/task cards that are not themselves polar-derived are still in scope for completion because they share the same upstream target/join contract.

| Card ID | Category | Polar Dependency | Current State | Required End State |
|---|---|---|---|---|
| `ld_curr` | flight/performance | no; measured glide only | live | keep unchanged and explicitly non-polar |
| `polar_ld` | flight/performance | yes | live | continue to use General Polar only |
| `best_ld` | flight/performance | yes | live | continue to use General Polar only |
| `netto` | flight/performance | yes | live | continue to use General Polar only |
| `netto_avg30` | flight/performance | yes, via `netto` validity pipeline | live | continue to use General Polar only |
| `levo_netto` | flight/performance | yes | live | continue to use General Polar only |
| `mc_speed` | flight/performance | yes | live | continue to use General Polar only |
| `final_gld` | navigation/glide | yes | live for racing finish-glide MVP | keep on General Polar only |
| `arr_alt` | navigation/glide | yes | live for racing finish-glide MVP | keep on General Polar only |
| `req_alt` | navigation/glide | yes | live for racing finish-glide MVP | keep on General Polar only |
| `arr_mc0` | navigation/glide | yes | live for racing finish-glide MVP | keep on General Polar only |
| `wpt_dist` | navigation | no; geometry only | placeholder-only | real current-leg or selected-target distance |
| `wpt_brg` | navigation | no; geometry only | placeholder-only | real current-leg or selected-target bearing |
| `wpt_eta` | navigation | indirect; policy may use glide/groundspeed mode | placeholder-only | real ETA with explicit owner and degraded states |
| `task_spd` | competition | indirect; task performance policy | placeholder-only | real task-speed metric with one owner |
| `task_dist` | competition | no; geometry/task progress | placeholder-only | real task-distance metric with one owner |
| `start_alt` | competition | no direct polar dependency; task rule policy | placeholder-only | real start-alt metric with one owner |

Non-scope cards:

- altitude, pressure, wind-only, time, GPS quality, raw vario, and similar cards remain unchanged unless they need new degraded-state labels from a joined card snapshot

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| General Polar raw settings (`selectedModel`, `effectiveModel`, `config`, `isFallbackPolarActive`) | `feature/profile/.../GliderRepository.kt` | existing `StateFlow`s | UI-local glider/polar mirrors |
| Active General Polar snapshot/provenance for consumers that need to show or branch on polar state | narrow read-only active-polar contract derived from existing `GliderConfigRepository` / `GliderRepository` state | immutable snapshot / `StateFlow` | formatter-side or screen-side reconstruction of source/provenance |
| Still-air sink/bounds/LD math from the active General Polar | `PolarStillAirSinkProvider` over canonical polar math owners | `StillAirSinkProvider` | direct ad hoc sink lookup logic outside the owner path |
| Flight-only polar outputs (`polarLdCurrentSpeed`, `polarBestLd`, `netto`, `levoNetto`, `speedToFly*`) | `CalculateFlightMetricsRuntime` -> `CompleteFlightData` | immutable runtime snapshot | card-side recomputation |
| Finish-glide target state | `GlideTargetRepository` | immutable `GlideTargetSnapshot` flow | ViewModel/UI-local finish-target mirrors |
| Current navigation target state for `wpt_*` cards | new `NavigationTargetRepository` or equivalent target-owner seam | immutable target snapshot flow | formatter-side leg/waypoint queries |
| Task performance card state (`task_spd`, `task_dist`, `start_alt`) | new task-domain/use-case owner | immutable card snapshot / flow | task math in cards, UI, or adapters |
| Card-facing joined snapshot | app adapter layer near the `MapScreenUtils.kt` path | `RealTimeFlightData` or a sibling immutable card snapshot | dfcards querying repositories or controllers |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| General Polar settings/config | `GliderRepository` | General Polar UI via `GliderUseCase`; runtime ballast path via `BallastRepositoryAdapter`; profile restore via `saveProfileSnapshot` | `GliderUseCase`, active-polar read contract, `PolarStillAirSinkProvider` | selected model + profile config | `GliderRepository` | profile switch, clear profile, settings mutation | state-based | repository precedence, migration, fallback tests |
| Active polar provenance snapshot | `GliderRepository` via new read port | repository-only | settings UI, diagnostics, adapter guardrails | General Polar settings/config | same as General Polar | profile switch, clear profile, no-polar fallback | state-based | snapshot/provenance tests |
| Flight polar metrics in `CompleteFlightData` | flight runtime | metrics runtime only | `FlightDataRepository` -> adapter -> cards/UI | active polar + current flight sample | none | source reset, replay stop, live/replay clear | live sample cadence / replay sample cadence | runtime, replay, mapper tests |
| Finish-glide solution | `FinalGlideUseCase` | pure solver call only | observer/join layer -> card adapter | flight snapshot + wind + active polar + finish target | none | no target, no polar, invalid altitude, prestart, finished | live sample cadence / replay sample cadence | solver, replay, adapter tests |
| Current-leg/navigation card snapshot | new navigation target/use-case owner | target owner/use-case only | observer/join layer -> card adapter | task state, nav state, selected/home target state, flight snapshot when ETA requires it | none | no target, prestart, finished, target switch | live sample cadence / replay sample cadence | target precedence, ETA, replay tests |
| Task performance card snapshot | new task-domain/use-case owner | task-domain owner only | observer/join layer -> card adapter | task state + nav state + flight snapshot | none | no task, prestart, invalid start rules, finish | live sample cadence / replay sample cadence | task metric and replay tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/profile/.../glider/*`
  - `feature/flight-runtime/.../glider/*`
  - `feature/flight-runtime/.../sensors/domain/*`
  - `feature/map/.../glide/*`
  - `feature/map/.../map/*`
  - `feature/tasks/.../*`
  - `dfcards-library/.../*`
  - `docs/POLAR/*`
- Any boundary risk:
  - cards or Compose querying task/navigation/glider owners directly
  - profile-owned polar math duplicated in map/profile UI
  - current finish-target routing in `GlideTargetRepository` still taking the broad coordinator dependency rather than a narrow runtime task-snapshot port

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/POLAR/05_XCPRO_POLAR_PHASE0_PHASE1_CHANGE_PLAN_2026-03-12.md` | completed flight-only polar slice | keep cards formatting-only; keep polar math upstream | broaden from flight-only to full card/nav surface |
| `docs/POLAR/06_XCPRO_TASK_AWARE_GLIDE_CARD_PLAN_2026-03-12.md` | completed finish-glide MVP seam | keep finish-glide solved upstream and mapped into cards | extend beyond finish cards to current-leg/task metrics |
| `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt` | current active-polar compute seam | keep one injected compute port over repository-owned polar state | add a read-only provenance snapshot alongside compute port |
| `feature/map/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt` | current domain owner for finish-glide math | keep solver pure and adapter-free | do not let waypoint/task metrics drift into the formatter layer |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Active polar provenance/display state | ad hoc repository reads and direct helper usage in settings/UI paths | narrow active-polar read contract derived from `GliderConfigRepository` / `GliderRepository` | one explicit app-wide read contract for "what polar is active right now" including actual source provenance | repository/read-port tests |
| Settings preview sink preview | `PolarPreviewCard` directly calling `PolarCalculator` | small profile-domain preview use case or read-port-driven preview mapper | keep screens render-only and stop bypassing the polar seam | preview tests |
| Finish-target input seam | `GlideTargetRepository` currently using `TaskManagerCoordinator.taskSnapshotFlow` | narrow runtime task read port over `TaskRuntimeSnapshot` + `TaskNavigationController.racingState` | reduce boundary drift without depending on the task-sheet UI projector | target repository tests |
| Current-leg/navigation card state | placeholder formatter behavior | new navigation target/use-case owner | complete waypoint cards without formatter math | target/ETA tests |
| Task performance card policy | absent / placeholder formatter behavior | new task-domain use case | task metrics belong with task policy, not cards | task metric tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/PolarPreviewCard.kt` | direct `PolarCalculator.sinkMs(...)` call | active-polar read contract + focused preview use case/mapper | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt` | direct `TaskManagerCoordinator.taskSnapshotFlow` dependency | narrow runtime task read port + `TaskNavigationController.racingState` | Phase 3 |
| `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt` for `wpt_dist`, `wpt_brg`, `wpt_eta`, `task_spd`, `task_dist`, `start_alt` | formatter placeholders standing in for missing domain data | adapter-fed card snapshot fields | Phase 4-6 |
| future card-side polar provenance logic | formatter infers fallback/no-polar state ad hoc | upstream snapshot fields and explicit invalid/degraded labels | Phase 4 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/flight-runtime/src/main/java/com/example/xcpro/glider/ActivePolarReadPort.kt` | New | cross-feature read-only contract for active polar snapshot | runtime and card/navigation domains can depend on a narrow contract | cards/UI must not depend directly on profile repository internals | No |
| `feature/flight-runtime/src/main/java/com/example/xcpro/glider/ActivePolarSnapshot.kt` | New | immutable model for active polar provenance/availability | shared contract model belongs with the read port | avoid leaking profile data classes across boundaries | No |
| `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt` | Existing | General Polar SSOT and snapshot emission | existing owner of selected/effective model and config | do not duplicate polar ownership in map/runtime | No |
| `feature/profile/src/main/java/com/example/xcpro/glider/PolarStillAirSinkProvider.kt` | Existing | compute port implementation over General Polar | current injected seam already exists | solver/runtime math should not read repository state ad hoc | No |
| `feature/profile/src/main/java/com/example/xcpro/glider/ActivePolarSnapshotMapper.kt` | New | maps repository state to the read-port snapshot | keeps repository focused on state ownership | avoid turning UI into the snapshot mapper | No |
| `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/PolarPreviewCard.kt` | Existing | render-only preview screen | General Polar UI already lives here | preview math/policy should move out to a domain mapper/use case | No |
| `feature/map/src/main/java/com/example/xcpro/glide/GlideTargetRepository.kt` | Existing | finish-glide target owner | current finish-target owner already exists | cards or adapters must not own finish-target selection | No |
| `feature/map/src/main/java/com/example/xcpro/navigation/NavigationTargetRepository.kt` | New | current-leg/selected/home navigation target owner | separates waypoint-target ownership from finish-glide owner | `CardFormatSpec` and `MapScreenUtils` are not target owners | No |
| `feature/map/src/main/java/com/example/xcpro/navigation/NavigationCardUseCase.kt` | New | waypoint ETA and related navigation card policy | domain logic for `wpt_*` belongs outside cards | formatter and observer layers should not compute ETA policy | No |
| `feature/tasks/src/main/java/com/example/xcpro/tasks/usecase/TaskPerformanceCardUseCase.kt` | New | task-speed/distance/start-alt domain policy | competition/task semantics belong with task domain | card formatter and map adapter should not own competition math | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapCardDataJoiner.kt` | New | combines flight, glide, navigation, and task card snapshots into one adapter-friendly model | keeps `MapScreenObservers` narrow | avoid turning `MapScreenObservers` into a mixed-purpose joiner | Yes; create this instead of enlarging observers |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt` | Existing | orchestration only | existing runtime flow owner | should launch collections, not grow new business logic | No |
| `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt` | Existing | card/UI adapter only | current adapter seam already exists | do not let domain math leak here | No |
| `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt` | Existing | card-facing immutable data contract | canonical card data model lives here | cards must format values provided to them, not reach elsewhere | No |
| `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt` | Existing | pure formatting and placeholder/degraded labels only | current formatter owner | business logic must stay upstream | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `ActivePolarReadPort` | `feature:flight-runtime` contract, implemented by `feature:profile` | profile UI, map/glide/card join layer, tests | narrow cross-module contract | exposes active General Polar state without leaking repository details | durable; likely permanent |
| `ActivePolarSnapshot` | same as above | same as above | narrow shared model | one explicit polar provenance model | durable; likely permanent |
| `NavigationTargetRepository` snapshot model | `feature:map` or adjacent navigation domain slice | map card joiner, tests | `internal` unless cross-module need appears | completes `wpt_*` without task-manager leaks | promote only if another module truly consumes it |
| `TaskPerformanceCardUseCase` output model | `feature:tasks` | app/map adapter layer | `internal` or narrow use-case surface | one owner for `task_*` / `start_alt` semantics | durable if cards stay supported |

### 2.2F Scope Ownership and Lifetime

No new long-lived scope is planned as part of this design.

Required rule:

- any new combine/collection for card joins should run inside existing owners:
  - repository owner scopes
  - `MapScreenObservers` runtime scope
  - existing ViewModel scopes

### 2.2G Compatibility Shim Inventory

No compatibility shim is preferred for the end state.

If an intermediate coordinator-to-task-state adapter is temporarily required while removing `TaskManagerCoordinator.taskSnapshotFlow` from the glide target seam, it must:

- be tagged `Compatibility shim:`
- name owner, target replacement, and removal trigger
- ship with regression tests

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| still-air sink from active polar | `feature/profile/src/main/java/com/example/xcpro/glider/PolarCalculator.kt` | sink provider, preview, tests | General Polar math is profile-owned and already canonical here | No |
| polar availability and IAS bounds | `feature/profile/src/main/java/com/example/xcpro/glider/GliderSpeedBounds.kt` | sink provider, STF, glide solver, tests | one owner for "has polar" and speed bounds | No |
| best L/D and L/D-at-speed derivation | `feature/profile/src/main/java/com/example/xcpro/glider/GlidePolarMetrics.kt` | flight metrics, cards, preview/supporting UI | one owner for derived polar performance values | No |
| finish-glide altitude-loss and required-glide solution | `feature/map/src/main/java/com/example/xcpro/glide/FinalGlideUseCase.kt` | finish cards, replay tests | already the domain owner of finish-glide math | No |
| waypoint ETA policy | new `NavigationCardUseCase.kt` | `wpt_eta`, supporting nav UI | ETA semantics must not be invented in adapters/cards | No |
| task-speed/distance/start-alt policy | new `TaskPerformanceCardUseCase.kt` | `task_spd`, `task_dist`, `start_alt` | competition/task math belongs with task domain | No |

### 2.2I Stateless Object / Singleton Boundary

No new Kotlin `object` or singleton-like state owner is required by this plan.

Existing stateless helpers (`PolarCalculator`, `GliderSpeedBoundsResolver`, `GlidePolarMetricsResolver`) remain acceptable because:

- they are pure/stateless
- they do not own authoritative state
- repository and provider layers remain the actual state owners

### 2.2J Second-Pass Contract Corrections

- Existing glider seam first:
  - `GliderConfigRepository` already exposes `selectedModel`, `effectiveModel`, `isFallbackPolarActive`, and `config`.
  - Any new active-polar contract must narrow or extend that seam, not create a parallel second owner.
- Runtime task seam first:
  - `TaskRepository.state` is a task-sheet UI projector.
  - Runtime/card consumers should depend on a narrow `TaskRuntimeSnapshot`-style read contract or the existing coordinator snapshot until that port exists.
- Polar source provenance is not derivable from one field:
  - `GliderSpeedBoundsResolver` gives `threePointPolar` precedence over model polar data.
  - With manual 3-point polar and no selected glider, current state can be:
    - `selectedModel = null`
    - `effectiveModel = fallbackModel`
    - `isFallbackPolarActive = false`
  - This means source provenance must be explicit if cards/settings/diagnostics need to say whether the active curve came from manual 3-point input, selected model data, or fallback.
- Airspeed contract must be made explicit:
  - `StillAirSinkProvider` currently documents sink lookup at true airspeed, but runtime consumers (`LevoNettoCalculator`, `SpeedToFlyCalculator`, `CalculateFlightMetricsRuntime`, `FinalGlideUseCase`) all pass IAS-scanned values.
  - Phase 2 must either rename/document the contract as IAS or add a real conversion strategy before more consumers are added.
- General Polar has more than one mutator path:
  - `BallastRepositoryAdapter` updates `GliderRepository.config.waterBallastKg` from the map/runtime ballast UI.
  - The plan must preserve one owner while acknowledging that General Polar is not edited only from the settings sheet.
- Stored-vs-operative config gap exists today:
  - `referenceWeightKg` is user-facing in `PolarConfigCard`.
  - `userCoefficients` is persisted in `GliderConfig`.
  - Neither is currently consumed by `PolarCalculator`, `GliderSpeedBoundsResolver`, or `PolarStillAirSinkProvider`.
  - Phase 0/2 must decide whether these fields become operative polar inputs or are explicitly documented as storage-only/non-authoritative for now.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| General Polar settings and provenance snapshot | state-based | not time-driven |
| flight-only polar metrics | live sample cadence / replay sample cadence | must stay tied to the same processed flight sample |
| finish-glide solution | live sample cadence / replay sample cadence | solver output must stay deterministic with the same target and sample |
| waypoint ETA and task-performance card outputs | live sample cadence / replay sample cadence | card outputs must remain replay-safe and sample-driven |
| card labels showing local clock time only | wall | UI-only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - repository/settings state stays on its current owner path
  - pure polar, glide, navigation, ETA, and task card math stays off Main on existing compute paths
  - no new file/network I/O should be introduced in hot card joins
- Primary cadence/gating sensor:
  - existing `CompleteFlightData` cadence for flight-driven outputs
  - task/navigation state cadence for target/task outputs
- Hot-path latency budget:
  - no visible regression in current card update cadence
  - changing General Polar should update dependent outputs on the next relevant processed sample without extra throttling layers

### 2.4A Logging and Observability Contract

No new production logging path is a goal of this plan.

If temporary debug logging is needed while validating propagation of General Polar through cards/replay:

- keep it out of tight loops where possible
- remove it before merge or track it under the existing logging deviation plan

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - General Polar state is profile state, not sample-time randomness
  - the same replay input, same General Polar state, same task/target state, and same solver settings must produce the same card and navigation outputs

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| no usable polar and no fallback available | Unavailable | active polar owner | `NO POLAR` style card state; glide solution invalid | no fake value; explicit invalid reason | repository + adapter tests |
| fallback polar active | Degraded | General Polar owner | values remain available, provenance says fallback | keep values live but label fallback in settings/diagnostics | snapshot/provenance tests |
| prestart racing finish target | Unavailable | `GlideTargetRepository` | finish cards show `PRESTART` | no fake glide values | target + formatter tests |
| finished task | Unavailable | `GlideTargetRepository` / task-domain owner | finish cards no longer pretend a remaining route exists | explicit invalid reason | target + formatter tests |
| current navigation target absent | Unavailable | navigation target owner | `wpt_*` cards show `NO WPT` style degraded state | no placeholder math | navigation target tests |
| impossible speed/ETA solution | Degraded | glide/nav/task domain owner | card shows `INVALID` or equivalent short reason | no divide-by-zero/infinite invented value | solver + formatter tests |

### 2.5B Identity and Model Creation Strategy

No new IDs or timestamp generation boundary is expected from this plan.

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| `StillAirSinkProvider` test doubles | fake/test implementations | tests only | explicit `NO POLAR` / invalid solver outputs | unit tests only |
| new navigation/task card use cases | fake target/task inputs in tests | tests only | explicit invalid snapshot values | keep constructors injectable; no silent production fallback |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| General Polar duplicated outside its owner path | SSOT + canonical formula owner rules | unit + review | new active-polar snapshot tests; review of direct helper usage |
| cards compute business logic | MVVM/UDF layering rules | formatter tests + review | `CardFormatSpecTest.kt` plus adapter tests |
| task coordinator bypass persists in glide target seam | task boundary and use-case boundary rules | review + boundary tests | `GlideTargetRepositoryTest.kt` or successor tests |
| changing General Polar does not propagate consistently | SSOT correctness | unit + integration tests | repository/provider/runtime propagation tests |
| replay drift in glide/nav/task cards | replay determinism rules | repeat-run replay tests | new replay determinism coverage for card outputs |
| missing degraded-state semantics | error/degraded-state rules | unit + formatter tests | invalid-state mapping tests |

### 2.7 Visual UX SLO Contract

This plan does not directly change map interaction, gesture, or overlay runtime behavior.

Current expectation:

- no MapScreen `MS-UX-*` or `MS-ENG-*` SLOs are directly impacted unless a later implementation phase changes pilot-facing map overlay behavior rather than card data only

## 3) Data Flow (Before -> After)

Current partial flow:

`General Settings Polar UI -> GliderRepository`

`GliderRepository -> PolarStillAirSinkProvider -> flight metrics / final glide -> CompleteFlightData -> MapScreenUtils -> RealTimeFlightData -> cards`

Current gaps:

- settings preview can still derive sink directly from lower-level polar helpers
- finish-target resolution still depends on a broader coordinator snapshot than the narrower documented seam
- `wpt_*`, `task_*`, and `start_alt` do not yet have real upstream card data

Target flow:

`General Settings Polar UI -> GliderRepository (SSOT)`

`GliderRepository / GliderConfigRepository -> ActivePolarReadPort`

`GliderRepository -> PolarStillAirSinkProvider`

`PolarStillAirSinkProvider -> CalculateFlightMetricsRuntime / FinalGlideUseCase / NavigationCardUseCase / TaskPerformanceCardUseCase`

`TaskRuntimeSnapshot read port + TaskNavigationController.racingState + selected/home/current-leg target sources -> GlideTargetRepository + NavigationTargetRepository`

`CompleteFlightData + GlideSolution + NavigationCardSnapshot + TaskPerformanceCardSnapshot -> MapCardDataJoiner -> MapScreenUtils -> RealTimeFlightData -> dfcards`

## 4) Implementation Phases

### Phase 0 - Contract Freeze and Baseline Inventory

- Goal:
  - freeze the meaning of "General Polar" and inventory every relevant card/output
  - add or confirm red/green tests for existing live behavior before new wiring
- Files to change:
  - `docs/POLAR/07_XCPRO_GENERAL_POLAR_CARD_NAV_CHANGE_PLAN_2026-03-17.md`
  - targeted existing tests where the current propagation contract is not yet locked
- Ownership/file split changes in this phase:
  - none; documentation and baseline tests only
- Tests to add/update:
  - propagation tests proving current flight-only polar cards already use the General Polar seam
  - baseline tests confirming placeholder-only `wpt_*`, `task_*`, and `start_alt` still have no real upstream values
  - baseline tests documenting current provenance edge cases:
    - manual 3-point polar with no selected model
    - selected model without polar falling back to club default
  - baseline tests or explicit doc notes for stored-but-non-operative fields:
    - `referenceWeightKg`
    - `userCoefficients`
- Exit criteria:
  - one approved card inventory and state-owner inventory exists
  - current live/pending slices are explicitly separated
  - the plan explicitly states whether dormant General Polar fields stay storage-only or become runtime inputs in later phases

### Phase 1 - Canonical Active Polar Read Contract

- Goal:
  - add one explicit read-only contract for "what active General Polar is in effect right now"
  - remove direct preview/helper bypasses in UI
- Files to change:
  - new `ActivePolarReadPort.kt`
  - new `ActivePolarSnapshot.kt`
  - `GliderRepository.kt`
  - new `ActivePolarSnapshotMapper.kt`
  - `PolarPreviewCard.kt`
- Ownership/file split changes in this phase:
  - General Polar remains owned by `GliderRepository`
  - any new read contract is derived from the existing `GliderConfigRepository` seam
  - profile UI stops reaching directly into lower-level polar calculators
- Tests to add/update:
  - active snapshot precedence:
    - manual 3-point polar overrides model polar
    - fallback active state is explicit
    - manual/no-selected provenance is explicit
    - IAS bounds/provenance remain explicit
  - preview tests for fallback/manual/model provenance
- Exit criteria:
  - any app surface that needs to show active-polar provenance reads it from one contract
  - no UI preview code directly reconstructs polar source/provenance

### Phase 2 - Existing Polar Consumer Unification

- Goal:
  - ensure every already-live polar-dependent output uses the same General Polar authority with no hidden side path
- Files to change:
  - `PolarStillAirSinkProvider.kt`
  - `StillAirSinkProvider.kt` only if the contract needs a narrow addition
  - `CalculateFlightMetricsRuntime.kt`
  - `FlightCalculationHelpers.kt`
  - `FinalGlideUseCase.kt`
  - supporting tests
- Ownership/file split changes in this phase:
  - none; this is a hardening/unification pass, not an owner move
- Tests to add/update:
  - sink-provider contract tests proving whether the active lookup contract is IAS or converted TAS
  - changing General Polar updates:
    - `polar_ld`
    - `best_ld`
    - `netto`
    - `levo_netto`
    - `mc_speed`
    - `final_gld`
    - `arr_alt`
    - `req_alt`
    - `arr_mc0`
  - decision coverage for currently dormant config inputs:
    - `referenceWeightKg`
    - `userCoefficients`
  - live vs replay determinism tests for the same polar state
- Exit criteria:
  - one manual General Polar change has consistent downstream effect across every already-live polar-dependent output
  - the airspeed unit contract is explicit in code/comments/tests

### Phase 3 - Navigation Target SSOT Completion

- Goal:
  - finish the target-state architecture needed for waypoint cards and tighten the finish-target seam
- Files to change:
  - `GlideTargetRepository.kt`
  - new `NavigationTargetRepository.kt`
  - task-state adapter/read-port files as needed
  - `TaskNavigationController` and `TaskRepository` use-case surfaces only if required
- Ownership/file split changes in this phase:
  - finish-glide target remains in `GlideTargetRepository`
  - current-leg/selected/home target ownership moves into a distinct navigation target owner
  - coordinator bypasses are removed or reduced behind a narrow runtime task-snapshot contract
- Tests to add/update:
  - current-leg vs finish target separation
  - prestart, started, finished, and invalid transitions
  - target precedence tests if selected/home sources are added
- Exit criteria:
  - `wpt_*` inputs exist as real domain-owned state
  - finish-glide cards and current-leg cards no longer share an ambiguous target owner

### Phase 4 - Card Contract Expansion and Joiner Extraction

- Goal:
  - expand the card data contract to carry the remaining real navigation/task values
  - keep observer/adapters narrow
- Files to change:
  - `FlightDataSources.kt`
  - new `MapCardDataJoiner.kt`
  - `MapScreenObservers.kt`
  - `MapScreenUtils.kt`
- Ownership/file split changes in this phase:
  - `MapScreenObservers` stays orchestration-only
  - `MapCardDataJoiner` becomes the owner of combining already-solved domain snapshots for cards
- Tests to add/update:
  - adapter mapping tests for new fields
  - formatter tests proving cards only format values supplied by the adapter
- Exit criteria:
  - `RealTimeFlightData` or an equivalent card-facing model contains all upstream values needed for the supported cards
  - placeholder-only formatters are removed for the completed cards

### Phase 5 - Waypoint Card Release

- Goal:
  - release `wpt_dist`, `wpt_brg`, and `wpt_eta` as real cards
- Files to change:
  - `NavigationCardUseCase.kt`
  - `MapCardDataJoiner.kt`
  - `CardFormatSpec.kt`
  - card catalog/template files if shipped presets change
- Ownership/file split changes in this phase:
  - none beyond using the new navigation owner and use case
- Tests to add/update:
  - geometry formatting tests for distance/bearing
  - ETA policy tests
  - boundary tests proving `wpt_*` uses current leg, not finish-glide target
- Exit criteria:
  - `wpt_dist`, `wpt_brg`, and `wpt_eta` render real values with explicit invalid states

### Phase 6 - Task/Competition Card Release

- Goal:
  - release `task_spd`, `task_dist`, and `start_alt` through one task-domain owner
- Files to change:
  - new `TaskPerformanceCardUseCase.kt`
  - relevant task-domain/read-model files
  - `MapCardDataJoiner.kt`
  - `CardFormatSpec.kt`
  - card catalog/template files if shipped presets change
- Ownership/file split changes in this phase:
  - task policy stays with the task domain
  - cards remain formatting-only
- Tests to add/update:
  - task-speed semantics
  - distance remaining semantics
  - start altitude semantics under prestart/start/finished states
  - replay determinism across task-state changes
- Exit criteria:
  - no placeholder-only `task_*` or `start_alt` card remains in the supported card surface

### Phase 7 - Polar Catalog Fidelity Research and Hardening

- Goal:
  - improve built-in glider polar quality where needed without blocking SSOT completion
- Files to change:
  - `core/common/.../GliderModels.kt`
  - relevant tests/docs only for the gliders updated
- Ownership/file split changes in this phase:
  - none; still owned by the glider catalog and General Polar SSOT
- Tests to add/update:
  - source-backed model validity tests for any glider entry changed
  - regression tests for fallback/model/manual precedence
- Exit criteria:
  - any glider catalog update cites a source and does not change the fact that General Polar remains the runtime authority
- Research rule:
  - use source-backed evidence where practical:
    - manufacturer or flight-manual polar tables
    - published sailplane manual/performance sheets
    - club or competition references only when primary data is unavailable and the source is documented
- Non-blocker note:
  - this phase is recommended, not a blocker for the architecture work; a pilot-entered General Polar still remains authoritative even when built-in catalog fidelity is imperfect

## 5) Test Plan

- Unit tests:
  - General Polar precedence and provenance
  - active-polar snapshot tests
  - best L/D and L/D-at-speed derivation from the active General Polar
  - finish-glide solver tests under General Polar changes
  - waypoint ETA/task metric tests
- Replay/regression tests:
  - identical replay input + identical General Polar state = identical card outputs
  - task-state transitions do not corrupt finish-glide or waypoint card outputs
  - live/replay parity for the same active General Polar where the inputs are equivalent
- UI/instrumentation tests (if needed):
  - General Polar preview/provenance UI
  - card-availability changes in shipped templates if the pilot-visible surface changes
- Degraded/failure-mode tests:
  - no usable polar
  - fallback polar active
  - no current target
  - prestart / finished / invalid route
  - impossible ETA / invalid speed solution
- Boundary tests for removed bypasses:
  - `PolarPreviewCard` no longer computes sink directly
  - `CardFormatSpec` remains formatting-only
  - finish-glide target uses the approved runtime task-snapshot seam
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | polar precedence, ETA/task metric, glide solver tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | replay determinism for glide/nav/task outputs |
| Persistence / settings / restore | Round-trip / restore / migration tests | `GliderRepository` snapshot/migration coverage |
| Ownership move / bypass removal / API boundary | Boundary lock tests | active-polar read-port tests; target seam tests |
| UI interaction / lifecycle | UI or instrumentation coverage | preview/provenance and any pilot-visible card-surface changes if needed |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | targeted card-join/runtime evidence if cadence regressions appear |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| "General Polar" is interpreted as only the manual 3-point fields, not the full active profile-scoped polar state | wrong scope and partial fixes | freeze the definition in Phase 0 and use explicit snapshot terminology in code/tests | maintainers |
| source provenance is inferred from `selectedModel` / `effectiveModel` / `isFallbackPolarActive` alone | wrong labels and inconsistent behavior for manual 3-point cases | add explicit active-polar source provenance to the read contract and lock it with tests | maintainers |
| runtime keeps mixing IAS and TAS language at the sink-provider seam | wrong glide/STF/netto assumptions and future bugs | make the airspeed contract explicit in Phase 2 and update comments/tests together | maintainers |
| cards remain the place where missing navigation/task semantics are improvised | architecture drift and replay bugs | keep all target/task/glide math upstream and test formatter purity | maintainers |
| target ownership becomes more duplicated while trying to finish `wpt_*` | state drift between finish and current-leg cards | create distinct target owners and explicit boundary tests | maintainers |
| user-facing General Polar fields remain persisted but non-operative | pilot confusion and false sense of authority | either wire them into canonical math or explicitly document them as storage-only/out of scope | maintainers |
| catalog data quality gets mixed up with SSOT completion | plan stalls on research instead of architecture | keep catalog research as a separate later phase; General Polar manual entry remains authoritative | maintainers |
| tightening the target seam reveals current coordinator boundary debt | medium implementation churn | phase the seam cleanup before expanding card contract | maintainers |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file: TBD
- Decision summary:
  - whether XCPro adds a durable cross-module `ActivePolarReadPort` beyond the existing `GliderConfigRepository` seam
  - whether finish-target and current-leg target ownership stay in separate repositories/use cases
  - where task/competition card policy lives long-term
- Why this belongs in an ADR instead of plan notes:
  - these are durable ownership/API decisions across `feature:profile`, `feature:flight-runtime`, `feature:map`, and `feature:tasks`

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- General Polar remains explicitly owned by `GliderRepository`
- Every polar-dependent card/output uses the same authoritative General Polar path
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Ownership/boundary/public API decisions are captured in an ADR when required
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)

## 8) Rollback Plan

- What can be reverted independently:
  - active-polar read contract
  - target-owner expansion for waypoint cards
  - card-contract expansion for waypoint/task cards
  - catalog-fidelity research updates
- Recovery steps if regression is detected:
  1. remove newly shipped waypoint/task cards from default templates first
  2. keep the already-working General Polar runtime path for existing live polar cards and finish-glide cards
  3. revert the newest target or adapter phase without reverting the underlying General Polar SSOT
