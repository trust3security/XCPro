# Map Flight Mode Ownership Cleanup Plan

## 0) Metadata

- Title: Recover map flight-mode ownership without adding a second card-config reader
- Owner: XCPro Team
- Date: 2026-04-12
- Status: Draft, updated after narrow seam/code pass
- Branch observed during planning: `feat/bluetooth`
- Related audit:
  - `docs/AUDITS/01_XCPRO_ARCHITECTURE_OWNERSHIP_AUDIT_2026-04-12.md`
  - `docs/AUDITS/02_XCPRO_ARCHITECTURE_OWNERSHIP_AUDIT_EVIDENCE_2026-04-12.md`
- Related architecture rules:
  - `AGENTS.md`: ViewModels own screen state and orchestration; UI must not own business policy; ownership boundary changes require an ADR.
  - `docs/ARCHITECTURE/CODING_RULES.md`: no duplicate state owners; new state requires owner/mutator/read path/reset/tests; compatibility shims require a removal trigger.
  - `docs/ARCHITECTURE/CONTRIBUTING.md`: non-trivial ownership moves require a plan and ADR.
- Readiness verdict: Ready with corrections. The corrections in section 9 are required before coding starts.

## 1) Goal and Non-Goals

Goal:
- Move map flight-mode visibility and fallback ownership out of `FlightDataManager` and out of Compose/UI effects.
- Keep dfcards as the persisted card profile-mode visibility owner.
- Make map-side flight-mode state deterministic and single-owner.
- Separate user-requested mode from transient runtime override mode.
- Preserve current behavior unless this plan explicitly says otherwise.

Non-goals:
- Do not add a new repository, module, or direct `CardPreferences` visibility reader in `feature:map`.
- Do not touch task, glide, trail, traffic, Bluetooth, subscription, or replay feature work except where replay suppresses thermalling runtime override.
- Do not move dfcards internals or make `FlightVisibility` public for this cleanup.
- Do not add a new long-lived scope. Reuse `viewModelScope` and the existing `CardIngestionCoordinator` scope only.

## 2) Current Seam Evidence

Current direct bypasses found in the narrow pass:
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
  - owns `_visibleModes`, `visibleModesFlow`, `loadVisibleModes(...)`, `isCurrentModeVisible(...)`, and `getFallbackMode()`.
  - reads `CardPreferences` visibility directly.
  - exposes `currentFlightModeFlow`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
  - calls `flightDataManager.loadVisibleModes(...)`.
  - calls `isCurrentModeVisible(...)` and `getFallbackMode()`.
  - calls `flightDataManager.updateFlightMode(...)` from a UI effect.
  - calls `prepareCardsForProfile(...)` using manager-backed `currentFlightModeSelection`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - collects `flightDataManager.visibleModesFlow`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt`
  - collects `flightDataManager.currentFlightModeFlow`.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelLifecycle.kt`
  - passes `flightDataManager.visibleModesFlow` into thermalling runtime.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - passes `applyFlightMode = ::setFlightMode` into thermalling wiring.
  - `setFlightMode(...)` currently mutates `MapStateStore`, `FlightDataManager`, and `sensorsUseCase` directly in one step.
- `feature/map-runtime/src/main/java/com/example/xcpro/mapruntime/state/MapStateReader.kt`
  - currently exposes only `currentMode`. Do not widen this cross-module API unless implementation proves a runtime consumer genuinely needs it.

Existing correct owner:
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
  - exposes `profileModeVisibilities` and `activeProfileId`.
  - owns profile-mode visibility mutations through dfcards APIs.
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightProfileStore.kt`
  - hydrates profile-mode visibility state from `CardPreferences`.
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt`
  - remains the persistence boundary for card profile visibility.

## 3) Target Ownership Contract

Persisted profile mode visibility:
- Owner: dfcards (`FlightDataViewModel`, `FlightProfileStore`, `CardPreferences`).
- Map-side role: read-only input through the existing card ingestion bridge.
- Forbidden: direct `CardPreferences` visibility reads from `feature:map`.

Map-side runtime mode state:
- Owner: `MapStateStore`.
- Mutator/orchestrator: `MapScreenViewModel` only.
- UI/read consumers: `MapScreenViewModel` read-only flows and the existing `mapState.currentMode` compatibility path for effective mode.
- Forbidden: Compose fallback policy, `FlightDataManager` fallback policy, manager-owned visible mode flows.

`FlightDataManager`:
- Not a visibility or fallback owner.
- May temporarily remain an effective-mode sink only where existing card/display plumbing still requires it.
- Any remaining mode mirror must be tagged with `Compatibility shim:` and include owner, reason, removal trigger, and regression coverage.

`MapStateReader`:
- Keep `currentMode` as effective mode for compatibility.
- Do not add requested/runtime/visible/source state to this cross-module interface unless a concrete runtime consumer cannot use `MapScreenViewModel` / `MapStateStore`.

## 4) State Contract

| State | Owner | Mutator | Read Path | Derived From | Reset/Clear | Tests |
|---|---|---|---|---|---|---|
| `requestedFlightMode` | `MapStateStore` | `MapScreenViewModel.setFlightMode(...)` | `MapScreenViewModel.requestedFlightMode` | user request | ViewModel lifetime only; not cleared by profile visibility, replay, or runtime override | VM tests |
| `runtimeFlightModeOverride: FlightMode?` | `MapStateStore` | `MapScreenViewModel.applyRuntimeFlightMode(...)`, `clearRuntimeFlightModeOverride()` | `MapScreenViewModel.runtimeFlightModeOverride` | thermalling runtime | clear on replay-active suppression, thermalling runtime stop/reset, and `onCleared` | VM + thermalling wiring tests |
| `visibleFlightModes` | `MapStateStore` | `MapScreenViewModel.onProfileModeVisibilitiesChanged(...)` | `MapScreenViewModel.visibleFlightModes` | dfcards `profileModeVisibilities` + `activeProfileId` | recompute on dfcards visibility/profile changes | policy + bridge tests |
| `effectiveFlightMode` | `MapStateStore` | `MapScreenViewModel.recomputeMapFlightModeState()` | `MapScreenViewModel.effectiveFlightMode`; `mapState.currentMode` compatibility path | requested + runtime override + visible modes | recompute on requested/runtime/visibility changes | policy + VM tests |
| `effectiveFlightModeSource` | `MapStateStore` | `MapScreenViewModel.recomputeMapFlightModeState()` | `MapScreenViewModel.effectiveFlightModeSource` | policy result | recompute with effective mode | policy + VM tests |

`effectiveFlightModeSource` values:
- `REQUESTED`
- `RUNTIME_OVERRIDE`
- `FALLBACK_CRUISE`

## 5) Policy Contract

Create:
- `feature/map/src/main/java/com/example/xcpro/map/MapFlightModePolicy.kt`

Add:
- `internal enum class MapFlightModeSource { REQUESTED, RUNTIME_OVERRIDE, FALLBACK_CRUISE }`
- `internal data class MapFlightModeUiState(...)` with:
  - `requestedMode: FlightMode`
  - `runtimeOverrideMode: FlightMode?`
  - `effectiveMode: FlightMode`
  - `effectiveModeSource: MapFlightModeSource`
  - `visibleModes: List<FlightMode>`
  - `requestedModeVisible: Boolean`
  - `runtimeOverrideVisible: Boolean`
- `internal fun resolveMapFlightModeUiState(requestedMode: FlightMode, runtimeOverrideMode: FlightMode?, modeVisibilities: Map<FlightModeSelection, Boolean>): MapFlightModeUiState`

Policy rules:
- Empty visibility map means all modes visible.
- `CRUISE` is always visible, even if input says false.
- `THERMAL` is visible unless explicitly false.
- `FINAL_GLIDE` is visible unless explicitly false.
- `visibleModes` order is exactly `CRUISE`, `THERMAL`, `FINAL_GLIDE`.
- `runtimeOverrideVisible` is true only when `runtimeOverrideMode != null` and the override mode is in `visibleModes`.
- `requestedModeVisible` is true only when `requestedMode` is in `visibleModes`.
- Effective resolution order:
  1. visible runtime override
  2. visible requested mode
  3. `CRUISE`
- Effective source:
  - `RUNTIME_OVERRIDE` when visible runtime override wins.
  - `REQUESTED` when requested mode wins.
  - `FALLBACK_CRUISE` when neither visible runtime override nor visible requested mode is available.
- Keep `FlightMode` <-> `FlightModeSelection` mapping local to this file for now.
- Do not place this policy in `FlightDataManagerSupport.kt`.
- No Android, Compose, Flow, persistence, logging, manager calls, or scopes in this file.

## 6) Implementation Plan

Implement as one complete cleanup. Phases are ordering checkpoints, not permission to merge a partial state.

### Phase 1 - Pure Policy and Tests

Create:
- `feature/map/src/main/java/com/example/xcpro/map/MapFlightModePolicy.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapFlightModePolicyTest.kt`

Test:
- empty visibility map means all three modes visible in correct order.
- `CRUISE` cannot be hidden.
- requested `THERMAL` hidden falls back to `CRUISE`.
- requested `FINAL_GLIDE` hidden falls back to `CRUISE`.
- visible runtime override wins over requested.
- hidden runtime override with visible requested uses requested.
- hidden runtime override with hidden requested falls back to `CRUISE`.
- visible mode order remains `CRUISE`, `THERMAL`, `FINAL_GLIDE`.

### Phase 2 - MapStateStore and ViewModel Ownership

Modify:
- `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`

`MapStateStore` must:
- add private `MutableStateFlow`s for `requestedFlightMode`, `runtimeFlightModeOverride`, `visibleFlightModes`, and `effectiveFlightModeSource`.
- keep `currentMode` as effective-mode read path for compatibility.
- add a comment on `currentMode` clarifying that it now means effective mode, not requested mode.
- initialize `visibleFlightModes` to `[CRUISE, THERMAL, FINAL_GLIDE]`.
- expose read-only `StateFlow` accessors for new state.
- add exactly one internal aggregate updater: `internal fun applyFlightModeUiState(state: MapFlightModeUiState)`.
- keep mutation private/internal. Do not expose individual setters that allow a second mutator path.

`MapScreenViewModel` must:
- expose:
  - `val requestedFlightMode: StateFlow<FlightMode>`
  - `val runtimeFlightModeOverride: StateFlow<FlightMode?>`
  - `val visibleFlightModes: StateFlow<List<FlightMode>>`
  - `val effectiveFlightMode: StateFlow<FlightMode> = mapState.currentMode`
  - `val effectiveFlightModeSource: StateFlow<MapFlightModeSource>`
- keep the active profile visibility map as a private derived input only if needed; it must not become another owner.
- add:
  - `private fun recomputeMapFlightModeState()`
  - `internal fun applyRuntimeFlightMode(mode: FlightMode)`
  - `internal fun clearRuntimeFlightModeOverride()`
  - `internal fun onProfileModeVisibilitiesChanged(activeProfileId: ProfileId?, allVisibilities: Map<ProfileId, Map<FlightModeSelection, Boolean>>)`
- use `ProfileIdResolver.canonicalOrDefault(...)` when selecting the active profile map.
- ensure `setFlightMode(newMode)` updates requested mode only, recomputes, then propagates the effective mode.
- ensure `applyRuntimeFlightMode(mode)` updates runtime override only, recomputes, then propagates the effective mode.
- ensure `clearRuntimeFlightModeOverride()` clears runtime override, recomputes, then propagates the effective mode.
- dedupe propagation so `FlightDataManager` and `sensorsUseCase` are updated only when effective mode actually changes.
- clear runtime override on replay-active suppression, thermalling runtime stop/reset, and `onCleared`.
- never let runtime override mutate `requestedFlightMode`.
- never clear `requestedFlightMode` on profile visibility changes, replay suppression, or runtime override changes.

### Phase 3 - Bridge dfcards Visibility Into Map Owner

Modify:
- `feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelCardIngestion.kt`

Bridge rules:
- Extend `CardIngestionCoordinator.bindCards(flightViewModel)` to collect `flightViewModel.profileModeVisibilities` and `flightViewModel.activeProfileId`.
- Use one additional `Job` only.
- Use `combine(...)` so active profile and visibility map are forwarded together.
- Start the visibility/profile bridge after existing card preference initialization wiring, and keep hydration/card sample/unit collection behavior unchanged.
- Forward to a map-side callback: `onProfileModeVisibilitiesChanged = viewModel::onProfileModeVisibilitiesChanged`.
- Keep the bridge read-only. It must not mutate dfcards visibility state.
- Keep bind idempotency: repeated `bindCards(...)` must not create duplicate visibility/profile collectors.

Tests:
- add or update a focused `CardIngestionCoordinator` test proving active profile and profile visibilities are forwarded together.
- test bind idempotency for the new job.
- include a grep proof that no direct `CardPreferences.getProfileAllFlightModeVisibilities(...)` read is introduced under `feature/map`.

### Phase 4 - Runtime, UI, and Card Preparation Rewire

Modify as needed:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelLifecycle.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenThermallingRuntimeStarter.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenThermallingRuntimeBinding.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ThermallingModeRuntimeWiring.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenOrientationRuntimeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/gestures/CustomMapGestures.kt` if it still consumes map mode visibility.

Thermalling runtime rules:
- Stop passing `applyFlightMode = ::setFlightMode` into thermalling automation.
- Pass `applyRuntimeFlightMode(...)` and `clearRuntimeFlightModeOverride()` instead.
- Pass effective/visible mode state from `MapScreenViewModel` / `MapStateStore`.
- If replay is active, clear runtime override and suppress thermalling runtime mode changes.
- If Thermal is not visible, clear runtime override instead of mutating requested mode.
- In `ThermallingModeRuntimeWiring`, map thermalling actions explicitly:
  - `SwitchFlightMode(THERMAL)` -> `applyRuntimeFlightMode(THERMAL)`.
  - `SwitchFlightMode` to any non-thermal mode produced as a restore/exit action -> `clearRuntimeFlightModeOverride()`.
  - zoom and contrast-map actions keep their existing behavior.
- Add a clear callback to the lifecycle stop/reset path so `onCleared` and thermalling reset cannot leave stale runtime override state.

UI and card preparation rules:
- UI, gestures, and overlays consume `MapScreenViewModel.visibleFlightModes` and `MapScreenViewModel.effectiveFlightMode`.
- Use `effectiveFlightModeSource` only where a consumer genuinely needs source diagnostics or behavior.
- `MapComposeEffects.ProfileAndConfigurationEffects` must not call:
  - `flightDataManager.loadVisibleModes(...)`
  - `flightDataManager.isCurrentModeVisible(...)`
  - `flightDataManager.getFallbackMode()`
  - `flightDataManager.updateFlightMode(...)` for mode ownership
- Card preparation must source the card-driving mode from `MapScreenViewModel.effectiveFlightMode`, converted to `FlightModeSelection`.
- Keep `FlightDataViewModel.prepareCardsForProfile(...)` behavior intact; only change the source of the mode argument.
- Orientation runtime should receive the effective mode selection, not a manager-owned current mode.

### Phase 5 - FlightDataManager Cleanup

Modify:
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- any factory/DI/callsite that passes `CardPreferences` only for mode visibility.

Remove:
- `_visibleModes`
- `visibleModesFlow`
- `visibleModes`
- `loadVisibleModes(...)`
- `isCurrentModeVisible(...)`
- `getFallbackMode()`
- `CardPreferences` constructor dependency if it becomes unused.

For `currentFlightModeFlow`:
- remove it entirely if all consumers are migrated.
- otherwise keep only as an explicit temporary compatibility mirror of effective mode, with:
  - comment prefix `Compatibility shim:`
  - owner: `feature:map` map screen ownership cleanup
  - reason: remaining card/display plumbing still needs a mirror during this cleanup
  - removal trigger: no production/test consumer reads `FlightDataManager.currentFlightModeFlow`
  - regression coverage

For `updateFlightModeFromEnum(...)`:
- prefer removal or rename to effective-mode sink semantics if all consumers are rewired.
- if retained, comment it as a compatibility shim and ensure only `MapScreenViewModel` calls it after recompute/dedupe.

### Phase 6 - ADR and Docs

Add an ADR:
- recommended path: `docs/ARCHITECTURE/ADR_MAP_FLIGHT_MODE_OWNERSHIP_2026-04-12.md`
- use `docs/ARCHITECTURE/ADR_TEMPLATE.md`.

ADR decision to record:
- dfcards remains the visibility/persistence owner.
- `MapStateStore` + `MapScreenViewModel` own map-side requested/runtime/effective/visible mode state.
- `FlightDataManager` no longer owns visibility or fallback.
- thermalling automation uses runtime override lane, not requested-mode mutation.
- no new public/cross-module state API is added unless implementation requires it; if `MapStateReader` is widened, the ADR must name the consumers and stability expectation.
- no timebase or replay nondeterminism is introduced.

Update:
- `docs/ARCHITECTURE/PIPELINE.md`
  - replace the thermalling `setFlightMode(...)` description with runtime override lane behavior.
  - update map/card mode preparation ownership so `FlightDataManager` is not documented as the mode visibility/fallback owner.
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only if a temporary rule violation remains.

## 7) Required Tests and Gates

Run in this order and fix failures:

1. `./gradlew :feature:map:compileDebugKotlin`
2. `./gradlew :feature:map:testDebugUnitTest --tests "*MapFlightModePolicyTest"`
3. `./gradlew :feature:map:testDebugUnitTest --tests "*ThermallingModeRuntimeWiringTest"`
4. `./gradlew :feature:map:testDebugUnitTest`
5. `./gradlew :dfcards-library:compileDebugKotlin`
6. `./gradlew enforceRules`

If the slice is ready for merge or touches broad root bindings, also run:
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Add/update tests for:
- policy behavior listed in Phase 1.
- requested mode persists when profile hides it; effective changes, requested does not.
- effective returns to requested when visibility later allows it and no visible runtime override exists.
- runtime override is separate from requested mode.
- replay suppression clears runtime override only.
- thermalling reset/stop clears runtime override without mutating requested mode.
- `FlightDataManager` and `sensorsUseCase` receive effective mode only when it changes.
- bridge forwards `activeProfileId + profileModeVisibilities` together.
- compatibility shim behavior, only if `currentFlightModeFlow` or an equivalent manager mirror remains.

## 8) Required Grep Checks Before Finishing

Run and include results:

- `rg -n "getProfileAllFlightModeVisibilities" feature/map`
- `rg -n "visibleModesFlow|loadVisibleModes\\(|isCurrentModeVisible\\(|getFallbackMode\\(" feature/map`
- `rg -n "currentFlightModeFlow" feature/map`
- `rg -n "currentFlightModeSelection" feature/map`
- `rg -n "applyFlightMode = ::setFlightMode|::setFlightMode" feature/map/src/main/java/com/example/xcpro/map`
- `rg -n "updateFlightModeFromEnum|updateFlightMode\\(" feature/map`
- `rg -n "setCurrentMode\\(" feature/map`
- `rg -n "FlightDataManager.*visible|visibleModes" feature/map/src/main/java/com/example/xcpro/map`

Expected outcome:
- no direct `CardPreferences` visibility reads in `feature/map`.
- no manager-owned visible mode helpers left in production `feature/map`.
- no thermalling runtime path using generic requested-mode callback.
- no root/effects path sourcing card-driving mode from `FlightDataManager.currentFlightModeFlow`.
- `currentFlightModeFlow` is gone, or clearly marked as a compatibility shim with a removal trigger and test coverage.
- remaining `setCurrentMode(...)` calls, if any, are internal to the store or compatibility path and do not bypass `MapScreenViewModel` recompute.

## 9) Corrections Folded In From Narrow Seam Pass

These are not optional:

- Add a clear-runtime-override callback to thermalling lifecycle stop/reset and `onCleared` handling.
- In thermalling action handling, treat non-thermal restore switch actions as `clearRuntimeFlightModeOverride()`, not requested-mode mutation.
- Rewire card preparation from effective mode, and remove manager mode update calls from Compose effects.
- Update root/effects pass-throughs that use `currentFlightModeSelection`, not just the original manager flow collection site.
- Do not widen `MapStateReader` unless a named runtime consumer requires it.
- If a `FlightDataManager` mode mirror remains, label it with `Compatibility shim:` and add a removal trigger plus test coverage.
- Add bridge idempotency/timing coverage for the new `CardIngestionCoordinator` visibility/profile job.
- Update `ThermallingModeRuntimeWiringTest` explicitly; do not rely only on VM tests.
- Update `PIPELINE.md` after wiring changes; an ADR alone is not enough.
- Add the stronger grep checks in section 8.

## 10) Final Recommendation

Implement this as a full ownership cleanup in one focused branch. Do not stop after adding the policy file unless the branch is explicitly parked as non-mergeable work-in-progress. The correct end state is:

```text
dfcards persisted visibility owner
  -> CardIngestionCoordinator read-only bridge
  -> MapScreenViewModel recompute/orchestration
  -> MapStateStore requested/runtime/effective/visible state
  -> UI/runtime consumers

FlightDataManager
  -> live display/card sample management only
  -> no visibility/fallback authority
```
