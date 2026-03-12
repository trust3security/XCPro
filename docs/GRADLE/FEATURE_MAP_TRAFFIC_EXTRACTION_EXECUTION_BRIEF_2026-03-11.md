# Feature Map Traffic Extraction Execution Brief 2026-03-11

## Purpose

Provide a concrete execution brief for the highest-value build-speed workstream:
extracting traffic-heavy implementation out of `feature:map` so small
traffic/render edits stop forcing a giant `feature:map` Kotlin rebuild.

This brief is intentionally execution-oriented. It is meant to be picked up by
another Codex agent with minimal rediscovery work.

Read first:

1. `docs/GRADLE/README.md`
2. `docs/GRADLE/BASELINE_BUILD_MEASUREMENTS_2026-03-10.md`
3. `docs/GRADLE/FEATURE_MAP_BUILD_HOTSPOT_ANALYSIS_2026-03-11.md`
4. `docs/ARCHITECTURE/ARCHITECTURE.md`
5. `docs/ARCHITECTURE/CODING_RULES.md`

## Why This Workstream Exists

Measured evidence says the main local iteration pain is not warm no-edit
`assembleDebug`. It is implementation-only edits inside `feature:map`.

Current evidence:

- edit-sensitive `map-impl` median from baseline: about `21.17s`
- controlled profiled sample after a `feature:map` impl edit:
  - `:feature:map:compileDebugKotlin`: `50.477s`
  - `:feature:map:kspDebugKotlin`: `8.853s`
  - `:app:compileDebugKotlin`: `UP-TO-DATE`
  - `:app:kspDebugKotlin`: still reran

Conclusion:

- the big win is reducing `feature:map` compile surface
- not adding more generic Gradle cache flags

## Outcome Target

### Architectural target

`feature:map` should keep:

- generic map-screen assembly
- generic map runtime ownership/facade
- non-traffic map concerns

Traffic-specific implementation should move out of `feature:map` behind narrow
ports/contracts.

### Measurement target

Use the retained historical edit-sensitive baseline plus current task profiling:

```powershell
./gradlew.bat :app:compileDebugKotlin --profile --console=plain
```

Success target for this workstream:

- at least `15%` improvement versus the retained historical `map-impl`
  baseline before continuing to broader extraction

Preferred target:

- reduce `map-impl` median from about `21.17s` to `<= 16s`

Stop condition:

- if the first meaningful extraction does not improve `map-impl` by at least
  `15%`, stop and reassess module strategy before doing more churn

## Hard Rules

1. No `feature:traffic` -> `feature:map` dependency.
2. No production behavior changes mixed into this workstream.
3. No rollout/default-medium cleanup mixed into this workstream.
4. No profile/QNH/widget repository moves in this workstream.
5. Do not claim build-speed wins for same-module file shuffles.
6. Keep replay behavior unchanged.
7. Preserve the existing map-facing package contract `com.example.xcpro.map`
   where practical to minimize call-site churn.

## Important Constraint

`feature:traffic` already contains a map-facing transitional surface:

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficMapApi.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficMapModels.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- traffic debug panels already moved under `feature/traffic/src/main/java/com/example/xcpro/map/ui`

That is the preferred landing zone for this workstream.

However:

- some remaining traffic-heavy files in `feature:map` still depend on
  `feature:map` types such as `MapStateReader`, `MapScreenState`, and
  `MapLocationUiModel`
- those files cannot be moved directly into `feature:traffic` without first
  extracting small ports or DTOs

## Recommended Landing Shape

### Keep in `feature:map`

- `MapScreenRoot.kt`
- `MapScreenContentRuntime.kt`
- generic map lifecycle/camera/location/task scaffolding
- `MapOverlayManagerRuntime.kt` as a thin owner/facade

### Move or re-home out of `feature:map`

- traffic selection state helpers
- traffic streaming/toggle orchestration
- traffic-specific overlay effect glue
- traffic-specific status/debug presentation and counters

### Use this bridge style

- define traffic-owned ports/DTOs in `feature:traffic`
- implement adapters in `feature:map`
- move traffic implementation against those ports

Do not widen many existing `internal` types to `public` just to force the move.

## Exact File Inventory

### Already in `feature:traffic`

These should be treated as the target home and extended, not duplicated:

- `feature/traffic/src/main/java/com/example/xcpro/map/MapTrafficUseCases.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficMapApi.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficMapModels.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficHelpers.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels*.kt`

### Remaining traffic-heavy files still in `feature:map`

Primary targets:

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelTrafficSelection.kt`
- traffic-specific builder functions currently in
  `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelStateBuilders.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
- traffic fields in
  `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
- traffic setup in
  `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`

Secondary targets:

- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeCounters.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeStatusCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeStatus.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapAdsbPersistentStatus.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapAdsbStatusTestTags.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/OgnTargetPolicy.kt`

Existing tests to preserve/update:

- `feature/map/src/test/java/com/example/xcpro/map/MapScreenTrafficCoordinatorOgnTargetTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTrafficSelectionTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenRootEffectsTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayRuntimeStatusCoordinatorTest.kt`

## Phased Execution

### Phase 0: Baseline Lock

Goal:

- preserve a reproducible baseline before any extraction

Required commands:

```powershell
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':app:compileDebugKotlin'
./gradlew.bat :feature:map:compileDebugKotlin
./gradlew.bat :feature:traffic:compileDebugKotlin
./gradlew.bat :app:compileDebugKotlin
```

Outputs to capture:

- retained historical `map-impl` median from
  `BASELINE_BUILD_MEASUREMENTS_2026-03-10.md`
- current no-edit median for `:app:compileDebugKotlin`
- any current compile blockers before extraction begins

Do not start code movement without this evidence.

### Phase 1: Boundary Prep, Not Big Moves

Goal:

- create traffic-owned ports/DTOs so the later move does not create a module
  cycle

Recommended new files under `feature:traffic`:

- `TrafficMapCoordinatorPorts.kt`
- `TrafficMapUiBindings.kt`
- `TrafficOverlayStatusPorts.kt`

Recommended contract contents:

- viewport/state port exposing only what traffic logic needs
  - current zoom
  - last camera target
- ownship/map position DTO
  - latitude
  - longitude
  - speed
  - bearing
  - accuracy if needed
- optional traffic selection DTOs if current map-specific models are too broad
- overlay presence/status snapshot port for status text generation

Adapters should stay in `feature:map`.

Likely adapter files to add under `feature:map`:

- `MapTrafficCoordinatorAdapters.kt`
- `MapTrafficStatusAdapters.kt`

Acceptance:

- no new module dependency cycle
- `feature:traffic` compiles without importing `feature:map`
- no behavior changes yet

### Phase 2: Move Selection and Coordinator Logic

Goal:

- relocate traffic orchestration logic out of `feature:map`

Files to change:

- move `MapScreenTrafficCoordinator.kt` implementation to `feature:traffic`
  once it depends only on traffic-owned ports/DTOs
- move `MapScreenViewModelTrafficSelection.kt` and any extracted
  traffic-only builder functions from `MapScreenViewModelStateBuilders.kt`
  once those builders no longer depend on `feature:map` internals

Keep in `feature:map`:

- creation call sites in `MapScreenViewModel.kt`
- adapters supplying map-owned state to the moved coordinator

Expected commit boundary:

- one commit for contract extraction
- one commit for coordinator + selection relocation

Acceptance:

- existing coordinator/selection tests continue to pass after relocation
- `MapScreenViewModel.kt` becomes thinner in traffic-specific logic
- no traffic implementation file remains in `feature:map` without a clear reason

### Phase 3: Move Traffic UI/Effect Glue

Goal:

- remove traffic-specific binding/effect churn from `feature:map` UI glue

Files to refactor:

- `MapScreenRootEffects.kt`
- `MapScreenBindings.kt`
- traffic fields in `MapScreenScaffoldInputModel.kt`
- traffic setup in `MapScreenScaffoldInputs.kt`

Target shape:

- `feature:map` keeps the screen shell
- traffic state is grouped into a smaller traffic-owned binding object
- traffic overlay effects are provided by a traffic-owned helper instead of
  being manually expanded across the map screen root

Suggested pattern:

- add a single traffic binding aggregate instead of many separate ADS-B/OGN
  fields threaded through map screen helpers

Acceptance:

- traffic-related parameter count in map-screen root helpers is materially lower
- traffic effect logic lives outside `feature:map`
- `MapScreenRootEffectsTest.kt` remains green or is relocated with equivalent coverage

### Phase 4: Move Status and Persistent UI Helpers

Goal:

- clean up the remaining traffic-specific presentation residue

Move/refactor:

- `MapAdsbPersistentStatus.kt`
- `MapAdsbStatusTestTags.kt`
- `OgnTargetPolicy.kt`
- `MapOverlayRuntimeCounters.kt`
- `MapOverlayRuntimeStatusCoordinator.kt`
- `MapOverlayManagerRuntimeStatus.kt`

Important note:

- the status files depend on map overlay presence and runtime state
- if they still need `MapScreenState`, first replace that dependency with a
  small status snapshot/port instead of moving the whole map runtime state type

Acceptance:

- no traffic-specific status/debug presentation remains in `feature:map`
  without a measured reason

### Phase 5: Benchmark Gate

Run:

```powershell
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':app:compileDebugKotlin'
./gradlew.bat :app:compileDebugKotlin --profile --console=plain
```

Pass criteria:

- current compile-path evidence shows at least `15%` improvement versus the
  retained historical `map-impl` baseline
- no significant regression in warm no-edit `:app:assembleDebug`
- no new architecture violations

Escalation trigger:

- if improvement is less than `15%`, do not keep relocating low-value files
- propose a dedicated lightweight module for traffic-map runtime/UI

## What Not To Touch In This Workstream

Do not mix in:

- profile snapshot/restore repository moves
- QNH repository moves
- map style repository moves
- widget layout repository moves
- rollout/default-medium cleanup
- forecast/weather extraction
- task/ballast/general map-screen cleanup unrelated to traffic

Those belong to separate workstreams and will blur the benchmark result.

## Verification Gates

Minimum after each meaningful phase:

```powershell
./gradlew.bat :feature:traffic:compileDebugKotlin
./gradlew.bat :feature:map:compileDebugKotlin
./gradlew.bat :app:compileDebugKotlin
```

Required final verification:

```powershell
./gradlew.bat enforceRules
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

If KSP/generated state becomes inconsistent on Windows:

```powershell
.\scripts\dev\kill_stale_gradle_processes.ps1 -ProjectRoot .
```

Then rerun the last failed command.

## Recommended Agent Split

If multiple agents are available, split like this:

### Agent A: Contracts and adapters

Owns:

- traffic-owned ports/DTOs in `feature:traffic`
- map-owned adapters in `feature:map`

Should not edit:

- `MapScreenRoot.kt`
- unrelated profile/settings repositories

### Agent B: Coordinator and selection relocation

Owns:

- `MapScreenTrafficCoordinator.kt`
- `MapScreenViewModelTrafficSelection.kt`
- extracted traffic-only builder functions from `MapScreenViewModelStateBuilders.kt`

Blocked until:

- Agent A lands ports/adapters or provides a clean branch target

### Agent C: UI/effect glue reduction

Owns:

- `MapScreenRootEffects.kt`
- `MapScreenBindings.kt`
- traffic subset of scaffold input plumbing

Blocked until:

- Agent A defines the traffic-owned binding contract

## Advice To The Executing Agent

The real win is not tiny file moves. The real win is getting traffic-specific
state/effect orchestration out of the giant `feature:map` compile unit.

So:

- start with contract extraction
- move coordinator and effect glue next
- benchmark before doing a long cleanup tail

If you only move small persistent-status/debug files and the benchmark barely
changes, stop and escalate instead of spending more time on low-value churn.
