# Map Orientation Flight-Mode Seam Hardening Phased IP

## 0) Metadata

- Title: Remove the direct UI/effects call to `MapOrientationManager.setFlightMode(...)`
- Owner: XCPro Team
- Date: 2026-04-16
- Issue/PR: TBD
- Status: Complete

Progress note:
- 2026-04-16: Phase 0 and Phase 1 implemented and verified:
  - `MapScreenOrientationRuntimeEffects` now depends on a callback seam instead of `MapOrientationManager`
  - `MapScreenRuntimeEffects` threads the callback seam without taking the concrete manager
  - `MapScreenViewModel` owns the concrete `orientationManager.setFlightMode(...)` invocation
  - verification result:
    - `./gradlew :feature:map:compileDebugKotlin` passed
    - `./gradlew :feature:map:testDebugUnitTest --tests "*MapScreenOrientationRuntimeEffectsTest"` passed
    - `./gradlew enforceRules` passed

Required pre-read order:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/ARCHITECTURE/ADR_FLIGHT_MGMT_ROUTE_PORT_2026-04-06.md`

## 1) Scope

- Problem statement:
  - The current map shell still allows UI/effects code to depend on the concrete `MapOrientationManager` and call `setFlightMode(...)` directly.
  - The immediate call path is:
    - `MapScreenViewModel.effectiveFlightMode`
    - `MapScreenRootUiBinding.currentFlightModeSelection`
    - `MapScreenRuntimeEffects(...)`
    - `MapScreenOrientationRuntimeEffects(...)`
    - `MapOrientationManager.setFlightMode(...)`
  - This is a direct architecture violation under the current UI/ViewModel boundary rules.
- Why now:
  - This is the cleanest high-value map-shell boundary breach still present.
  - The repo already has a narrow-seam precedent in `FlightDataMgmtPort`; this slice should reuse that pattern instead of expanding into a broader shell rewrite.
- In scope:
  - Phase 0 seam lock and audit for the exact setter path only.
  - Phase 1 removal of the direct UI/effects setter call.
  - Replace the concrete manager parameter in the runtime effect path with the smallest real seam:
    - preferred: callback from the map shell / ViewModel-owned wiring
    - acceptable: tiny map-owned seam with one responsibility
  - Update targeted tests and acceptance-grep checks for this path.
- Out of scope:
  - No broad `MapScreenViewModel` split.
  - No redesign of `rememberMapScreenManagers(...)`.
  - No cleanup of unrelated task/trail/traffic/replay/runtime ownership.
  - No widening of `MapOrientationRuntimePort`.
  - No broad `runtimeDependencies` remediation beyond what is strictly needed for this setter path.
  - No intended replay, trail, task, traffic, card, or map-behavior changes.
- User-visible impact:
  - None intended.
  - The same `currentFlightModeSelection` must still reach the orientation runtime.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| effective map flight mode | `MapStateStore` via `MapScreenViewModel` | `effectiveFlightMode` -> `currentFlightModeSelection` | any UI-local mirror of flight-mode runtime authority |
| orientation runtime flight-mode application seam | `MapScreenViewModel` screen-owned seam | callback or tiny map-owned seam | direct UI/effects access to `MapOrientationManager.setFlightMode(...)` |
| concrete orientation runtime mutation | `MapOrientationManager` | internal map-shell wiring only | UI/effects helper access to the concrete manager for this mutation |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| effective flight mode | `MapStateStore` | `MapScreenViewModel.setFlightMode(...)`, runtime override mutators already owned by the ViewModel | `MapScreenRootUiBinding.currentFlightModeSelection` | requested mode + runtime override + visibility policy | none | existing mode recompute paths only | N/A | `MapScreenViewModelCoreStateTest` |
| orientation flight-mode apply command | `MapScreenViewModel` seam only | runtime effect path invoking the seam | none; effect-only dispatch | current `FlightModeSelection` | none | per `LaunchedEffect` re-run / screen disposal | N/A | `MapScreenOrientationRuntimeEffectsTest` |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> ViewModel screen seam -> internal runtime collaborator`

Modules/files touched:
- `feature:map`
- `docs/MAPSCREEN`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

Boundary risk:
- leaving `MapScreenOrientationRuntimeEffects` coupled to `MapOrientationManager`
- replacing the direct call with a rename-only bag or generalized runtime port
- letting this small seam pass grow into a broad map-shell rewrite

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/ARCHITECTURE/ADR_FLIGHT_MGMT_ROUTE_PORT_2026-04-06.md` | earlier Phase 1 map-shell hardening replaced a concrete runtime handle with a narrow map-owned seam | remove the highest-value boundary leak first and keep grouped runtime collaborators internal | use an internal callback seam if that is smaller than adding a named port type |
| `feature/map/src/main/java/com/example/xcpro/map/FlightDataMgmtPort.kt` | existing narrow map-owned contract that avoids exposing a broader runtime bundle | create only the smallest seam required for the call path | no new cross-module contract is planned for this slice |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| calling `MapOrientationManager.setFlightMode(...)` for screen flight-mode updates | `MapScreenOrientationRuntimeEffects` UI/effects code | `MapScreenViewModel` wiring or a tiny map-owned seam invoked by it | UI/effects must not mutate runtime state through a concrete manager | targeted unit test + grep gates |
| carrying the concrete mutation dependency through the effect path | `MapScreenRuntimeEffects` / `MapScreenOrientationRuntimeEffects` | callback or tiny seam parameter | keep the effect path narrow and reviewable | compile + grep gates |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapScreenOrientationRuntimeEffects` | direct concrete call to `orientationManager.setFlightMode(currentFlightModeSelection)` | callback from `MapScreenRoot` / `MapScreenViewModel`, or a tiny map-owned seam if callback shape is not sufficient | Phase 1 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | Existing | own the narrow screen seam that applies orientation flight-mode selection | the ViewModel already owns map screen state/orchestration | no broader shell split is needed for one mutation seam | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | Existing | pass the narrow seam into the runtime effect path | screen root already wires root bindings and effects | avoids adding another bag or public route surface | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt` | Existing | thread the narrow seam into the orientation effect only | this file already composes runtime-effect helpers | do not move unrelated runtime effects in this slice | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffects.kt` | Existing | invoke the narrow seam from `LaunchedEffect` without knowing the concrete manager | this is the direct violation site | a new helper file is not needed if a callback is enough | No |
| `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffectsTest.kt` | Existing | verify effect forwarding through the replacement seam | this test already owns the targeted acceptance behavior | no broad integration test is needed | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| optional callback seam `(FlightModeSelection) -> Unit` | `feature:map` screen wiring | `MapScreenRoot`, `MapScreenRuntimeEffects`, `MapScreenOrientationRuntimeEffects` | internal | smallest valid replacement for the direct setter path | retain only while this screen-owned effect path exists |

Rules:
- Do not widen `MapOrientationRuntimePort` for this slice.
- Do not introduce a new cross-module runtime contract unless the callback shape proves impossible.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `currentFlightModeSelection` | N/A | state selection only; no time dependency |
| orientation flight-mode apply dispatch | N/A | event forwarding only; no time dependency |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added in this slice
  - the same current selection must still reach the same underlying orientation runtime owner

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI/effects directly mutates orientation runtime via concrete manager | `CODING_RULES.md` UI rules; `ARCHITECTURE.md` dependency direction | grep + targeted unit test + review | `rg -n "orientationManager\\.setFlightMode" feature/map/src/main/java/com/example/xcpro/map/ui`, `MapScreenOrientationRuntimeEffectsTest` |
| target effect files keep depending on `MapOrientationManager` | `ARCHITECTURE.md` stable seams only | grep + compile + review | `rg -n "MapOrientationManager" feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffects.kt feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt` |
| this slice grows into a broad runtime-port or bag refactor | `ARCHITECTURE.md` no broad dependency bags/service locators | plan review + diff review | this plan and PR review |

## 3) Data Flow (Before -> After)

Before:

`MapStateStore -> MapScreenViewModel.effectiveFlightMode -> MapScreenRootUiBinding.currentFlightModeSelection -> MapScreenRuntimeEffects -> MapScreenOrientationRuntimeEffects -> MapOrientationManager.setFlightMode(...)`

After:

`MapStateStore -> MapScreenViewModel.effectiveFlightMode -> MapScreenRootUiBinding.currentFlightModeSelection -> MapScreenRuntimeEffects -> MapScreenOrientationRuntimeEffects -> narrow screen seam -> MapOrientationManager.setFlightMode(...)`

## 4) Implementation Phases

### Phase 0 - Seam Lock And Audit

- Goal:
  - freeze the exact setter-path scope before code edits
- Files to change:
  - this change plan
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- Ownership/file split changes in this phase:
  - none in production code
- Tests to add/update:
  - none
- Exit criteria:
  - exact in-scope call path named
  - explicit non-goals named
  - acceptance grep commands recorded
  - deviation entry scoped and time-boxed until the code fix lands

### Phase 1 - Remove The Direct UI/Effects Setter Call

- Goal:
  - remove the direct UI/effects call to `MapOrientationManager.setFlightMode(...)` with no intended behavior change
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffects.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffectsTest.kt`
- Ownership/file split changes in this phase:
  - move the concrete setter call out of UI/effects code and into ViewModel-owned or map-shell-owned wiring
- Tests to add/update:
  - replace the current manager-mock test with a seam-forwarding test
  - add one focused regression assertion only if needed to prove unchanged forwarding behavior
- Exit criteria:
  - `MapScreenOrientationRuntimeEffects` no longer accepts or imports `MapOrientationManager`
  - no UI/effects file calls `orientationManager.setFlightMode(...)`
  - no new bag object or widened runtime port exists for this slice
  - compile/test/rule gates pass

## 5) Test Plan

- Unit tests:
  - `./gradlew :feature:map:testDebugUnitTest --tests "*MapScreenOrientationRuntimeEffectsTest"`
- Compile gates:
  - `./gradlew :feature:map:compileDebugKotlin`
- Rule gates:
  - `./gradlew enforceRules`
- Acceptance commands:
  - `git diff --name-status`
  - `rg -n "orientationManager\\.setFlightMode" feature/map/src/main/java/com/example/xcpro/map/ui`
  - `rg -n "MapOrientationManager" feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffects.kt feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
- Connected/SLO tests:
  - not required unless the implementation expands beyond this narrow setter path

## 6) Docs And Governance

- `PIPELINE.md` update required:
  - No
- ADR required:
  - No, unless implementation unexpectedly requires a durable cross-module port change
- `KNOWN_DEVIATIONS.md` entry required:
  - No after the Phase 1 code change is verified and the scoped deviation entry is removed
- Removal trigger:
  - completed on 2026-04-16 after the Phase 1 code change satisfied the grep and targeted verification gates above
