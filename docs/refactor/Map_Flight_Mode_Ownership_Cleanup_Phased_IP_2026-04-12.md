# Map Flight Mode Ownership Cleanup Phased IP

## 0) Metadata

- Title: Recover map flight-mode ownership without adding a second card-config reader
- Owner: XCPro Team
- Date: 2026-04-12
- Issue/PR: TBD
- Status: Draft
- Branch observed during planning: `feat/bluetooth`
- Source audit:
  - `docs/AUDITS/01_XCPRO_ARCHITECTURE_OWNERSHIP_AUDIT_2026-04-12.md`
  - `docs/AUDITS/02_XCPRO_ARCHITECTURE_OWNERSHIP_AUDIT_EVIDENCE_2026-04-12.md`
- Execution rules:
  - This is an ownership seam cleanup, not a UI redesign.
  - Keep Phase 1 pure and testable. No Compose, Android `Context`, persistence, Flow wiring, or runtime side effects in Phase 1.
  - Do not add another direct `CardPreferences` reader in `feature:map`.
  - Do not put mode fallback policy in Composables, cards, formatters, or display-only adapters.
  - Keep `FlightDataRepository` as fused-flight-data SSOT. Do not add mode visibility or task/glide state to `CompleteFlightData`.
  - Do not touch task, glide, trail, traffic, Bluetooth, or subscription work in this track.

Progress:
- 2026-04-12: Phase 0 narrow seam/code pass complete. Existing code confirms that `FlightDataManager` owns `visibleModesFlow` and directly reads `CardPreferences`, while dfcards already exposes `FlightDataViewModel.profileModeVisibilities` backed by `FlightProfileStore`.

## 1) Scope

Problem statement:
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` loads profile-visible flight modes and applies fallback mode selection in `ProfileAndConfigurationEffects`.
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt` reads `CardPreferences` directly in `loadVisibleModes(...)`, then exposes `visibleModesFlow`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapScreenThermallingRuntimeBinding.kt`, and `feature/map/src/main/java/com/example/xcpro/gestures/CustomMapGestures.kt` consume the map-visible modes from `FlightDataManager`.
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt` already owns card profile-mode visibility state via `profileModeVisibilities`, with mutation through `setProfileFlightModeVisibility(...)` / `toggleProfileFlightModeVisibility(...)`.

Why now:
- The audit identified this as the top concrete architecture issue because it is a small but real SSOT and UI-purity drift.
- Fixing this seam first reduces the chance of later ad hoc changes treating `TaskRepository`, `FlightDataManager`, or Compose effects as runtime authorities.

In scope:
- Add a pure map flight-mode visibility/fallback policy for Phase 1.
- Move map requested/effective flight-mode state toward `MapScreenViewModel` ownership.
- Reuse the existing dfcards visibility owner as the card configuration source.
- Remove direct `CardPreferences` mode-visibility reads from `FlightDataManager`.
- Rewire overlay, gesture, and thermalling runtime consumers to read the ViewModel-owned map visible-mode state.

Out of scope:
- Changing card template/card layout behavior.
- Moving dfcards internals or making `FlightVisibility` public.
- TrailProcessor ownership cleanup.
- TaskRepository / TaskManagerCoordinator cleanup.
- Final glide, route, task-runtime, traffic, Bluetooth, subscription, or replay feature work.
- `PIPELINE.md` changes before production wiring changes.

User-visible impact:
- Intended behavior parity: Cruise remains always visible. Thermal and Final Glide remain profile-hideable. If the current requested mode becomes hidden, the effective map mode falls back to Cruise.

Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Card profile-mode visibility (`CRUISE`, `THERMAL`, `FINAL_GLIDE` visibility per profile) | `dfcards-library` card configuration owner: `FlightDataViewModel` + `FlightProfileStore`, persisted through `CardPreferences` | `FlightDataViewModel.profileModeVisibilities` and `flightModeVisibilitiesFor(...)` | `FlightDataManager.loadVisibleModes(...)` reading `CardPreferences`; Compose effects reading preferences or deciding fallback |
| Map requested flight mode | `MapScreenViewModel` / `MapStateStore` | read-only `StateFlow<FlightMode>` or focused state model | `FlightDataManager.currentFlightModeFlow` as a second authority |
| Map effective flight mode | `MapScreenViewModel` derived state using pure policy | read-only state used by map UI/runtime consumers | Compose fallback mutations, manager fallback helpers |
| Map visible modes | derived projection in `MapScreenViewModel` | read-only `StateFlow<List<FlightMode>>` or `MapFlightModeUiState.visibleModes` | `FlightDataManager.visibleModesFlow` as authoritative state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `profileModeVisibilities` | `FlightDataViewModel` / `FlightProfileStore` | `setProfileFlightModeVisibility(...)`, `toggleProfileFlightModeVisibility(...)`, profile hydration | Phase 2 bridge into map VM | `CardPreferences` hydration and card settings mutations | `CardPreferences` through dfcards owner | profile change, card preference hydration, profile removal | N/A | existing dfcards profile layout tests plus bridge test |
| requested map mode | `MapScreenViewModel` | explicit user intent method only after Phase 2 | map UI, sensors use case, card mode sync | persisted last mode and user action | existing `FlightMgmtPreferencesUseCase` / repository | profile switch, user mode selection, fallback request rules | N/A | VM mode-state tests |
| effective map mode | `MapScreenViewModel` derived from requested mode + profile visible modes | no direct external mutation | overlay, gestures, thermalling runtime, sensors application path | requested map mode + pure policy output | none | recompute on requested mode/profile visibility change | N/A | pure policy tests, VM fallback tests |
| visible map modes | `MapScreenViewModel` derived state | none outside recompute | overlay/menu, gesture cycle, thermalling visibility gate | dfcards visibility map via Phase 2 bridge | none | recompute on profile visibility/profile change | N/A | pure policy tests and overlay/root binding test |

### 2.2 Dependency Direction

Confirmed target dependency flow:

`dfcards config owner -> map ViewModel bridge -> map UI/runtime consumers`

Boundary risks:
- Pulling `CardPreferences` into a new map repository or helper would reproduce the same ownership bug under a new name.
- Making `dfcards-library` `FlightVisibility` public just to reuse defaults would widen card internals for a map-only cleanup.
- Putting fallback in `FlightDataManagerSupport.kt` would add policy to a mixed support file that already contains unrelated display helpers.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightVisibility.kt` | canonical card visibility defaults and Cruise-always-visible rule | default all modes visible; Cruise cannot be hidden; ignore invalid raw names at persistence boundary | keep internal; map policy consumes typed visibility maps instead of raw string maps |
| `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataStateMapper.kt` | pure-ish typed mapping around profile-mode visibility state | derive visibility for profile with default fallback | not directly reusable because it is internal to dfcards |
| `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt` | existing `FlightMode` <-> `FlightModeSelection` mapping helper | reuse mapping behavior | do not add policy/fallback here in Phase 1 because the file also owns display data helpers |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Load card profile-mode visibility for map flight-mode menu | `FlightDataManager` via direct `CardPreferences` read | dfcards owner bridged into `MapScreenViewModel` | one card config owner already exists | bridge test and removal of direct `CardPreferences` read |
| Decide fallback when requested mode is hidden | Compose effect and `FlightDataManager.getFallbackMode()` | pure `MapFlightModePolicy` used by `MapScreenViewModel` | fallback is state policy, not rendering | pure tests and VM tests |
| Publish map visible modes to UI/runtime | `FlightDataManager.visibleModesFlow` | `MapScreenViewModel` derived state | visible modes are a map UI/runtime projection | overlay/root binding compile and targeted tests |
| Publish current card flight-mode selection | `FlightDataManager.currentFlightModeFlow` | `MapScreenViewModel` as the map mode state owner, with dfcards sync as a side effect | avoid second current-mode authority | VM tests and root binding test |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapComposeEffects.ProfileAndConfigurationEffects` | calls `flightDataManager.loadVisibleModes(...)` | no visibility load in Compose; collect VM-provided state | Phase 4 |
| `MapComposeEffects.ProfileAndConfigurationEffects` | calls `flightDataManager.isCurrentModeVisible(...)` and `getFallbackMode()` | VM applies pure policy and exposes effective state | Phase 2-4 |
| `FlightDataManager.loadVisibleModes(...)` | direct `CardPreferences.getProfileAllFlightModeVisibilities(...)` | remove; card visibility comes from `FlightDataViewModel.profileModeVisibilities` bridge | Phase 3-5 |
| `MapOverlayStack` | collects `flightDataManager.visibleModesFlow` | consume VM-provided visible modes | Phase 4 |
| `MapScreenViewModelLifecycle` / `MapScreenThermallingRuntimeBinding` | thermalling gate reads `flightDataManager.visibleModesFlow` | consume VM-provided visible modes or effective visibility gate | Phase 4 |
| `MapScreenRootStateBindings` | collects `flightDataManager.currentFlightModeFlow` | collect VM-owned current/effective selection | Phase 4-5 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Map_Flight_Mode_Ownership_Cleanup_Phased_IP_2026-04-12.md` | New | phased ownership cleanup contract | non-trivial ownership cleanup belongs in `docs/refactor` | not durable enough for global docs yet | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapFlightModePolicy.kt` | New, Phase 1 | pure map mode visibility and fallback resolver | map owns map-visible mode projection, not dfcards card config authority | not in Compose, not `FlightDataManager`, not `FlightDataManagerSupport` | No |
| `feature/map/src/test/java/com/example/xcpro/map/MapFlightModePolicyTest.kt` | New, Phase 1 | pure policy regression tests | test next to map policy owner | not in dfcards because map projection behavior is the consumer policy | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | Existing, Phase 2 | screen state owner and intent orchestration | already owns map mode intents through `setFlightMode(...)` | not UI effects or display manager | Split helper if file growth becomes material |
| `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt` | Existing, Phase 2 | map requested/effective mode state if store remains the state holder | already owns `currentMode` state | not card config or persistence owner | No |
| `feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt` | Existing, Phase 3 | bridge bound card ViewModel state into map owner | it already binds `FlightDataViewModel` to the map screen owner scope | not a card config authority; only bridge/collector if needed | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelCardIngestion.kt` | Existing, Phase 3 | card-ingestion wiring factory | existing creation point for card bridge dependencies | not for policy or state decisions | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` | Existing, Phase 4 | UI effects only | remove mode visibility/fallback policy from here | not a state owner | No, delete code rather than add |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` | Existing, Phase 4 | render/menu consumer | should receive visible modes as prepared UI input | not a visibility owner | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenThermallingRuntimeBinding.kt` | Existing, Phase 4 | thermalling runtime binding | consume prepared visibility gate only | not a card-config or fallback owner | No |
| `feature/map/src/main/java/com/example/xcpro/gestures/CustomMapGestures.kt` | Existing, Phase 4 | gesture cycle consumer | consume prepared visible modes | not a visibility owner | No |
| `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt` | Existing, Phase 5 | live display/card data manager only | remove card visibility and current mode authority once consumers move | not a map mode state owner | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `MapFlightModePolicy.resolve(...)` | `feature:map` | `MapScreenViewModel` tests and later VM wiring | `internal` | pure policy for map-visible mode projection | stays if VM wiring uses it; no cross-module public API |
| `MapFlightModeUiState` or equivalent focused value | `feature:map` | map root/overlay/runtime binding | `internal` unless root package needs public exposure | keeps requested/effective/visible modes explicit | replace manager flows, then remove manager compatibility |
| card visibility bridge from `FlightDataViewModel.profileModeVisibilities` | `feature:map` bridge over dfcards owner | `MapScreenViewModel` | internal | map needs card config as input, not as persistence owner | remove `FlightDataManager.loadVisibleModes(...)` after consumers move |

### 2.2F Scope Ownership and Lifetime

No new long-lived scope is allowed in Phase 1.

Later phases may add or reuse one collector scoped to `viewModelScope` through the existing `CardIngestionCoordinator` / map VM binding. If that happens:
- owner: `MapScreenViewModel` screen lifecycle;
- dispatcher: inherited flow collection context unless persistence or CPU work is introduced;
- cancellation: ViewModel clear or coordinator stop;
- rule: the collector may bridge state only and must not become a card config authority.

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| `FlightDataManager.visibleModesFlow` | `feature:map` display manager | temporary current consumer path | VM-owned visible-mode state | overlay, gestures, thermalling runtime no longer read it | VM/root binding tests |
| `FlightDataManager.currentFlightModeFlow` | `feature:map` display manager | temporary card/menu sync path | VM-owned requested/effective mode state plus dfcards sync | root/effects no longer collect it | VM/root binding tests |
| `MapComposeEffects` fallback path | UI effects | legacy current behavior | VM policy application | effects no longer call visibility/fallback manager methods | Compose/root compile and policy tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| Card visibility defaults and Cruise-always-visible persistence rule | `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightVisibility.kt` | dfcards ViewModel/profile store | card config persistence is dfcards-owned | Map may mirror typed semantics only until a public dfcards projection API exists |
| Map requested-to-effective mode fallback | `feature/map/src/main/java/com/example/xcpro/map/MapFlightModePolicy.kt` | map VM, overlay/gesture/thermalling consumers indirectly | map UI/runtime projection is map-owned | No additional callsite policy allowed |
| `FlightMode` <-> `FlightModeSelection` mapping | currently split across `FlightDataManagerSupport.kt` and `MapScreenViewModelStateBuilders.kt` | map manager, state builders, planned policy | mapping is adapter policy between map and dfcards model types | Phase 1 should avoid broad consolidation; later phase may create focused mapping file if it reduces duplication without churn |

### 2.2I Stateless Object / Singleton Boundary

Phase 1 must not introduce a Kotlin `object` holding mutable state.

If `MapFlightModePolicy` is implemented as an `internal object`, it must be stateless and pure. A top-level function is preferred if it fits local style and keeps the file simpler.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| requested map mode | N/A | user/profile state, no timing |
| effective map mode | N/A | pure derivation from requested mode and visibility state |
| visible map modes | N/A | pure derivation from card config state |

This track must not introduce wall, monotonic, replay, or system-time calls.

### 2.4 Threading and Cadence

- Phase 1: no Flow, coroutine, dispatcher, Android, or cadence work.
- Later VM/bridge phases: lifecycle-bound collection through `viewModelScope` or existing coordinator scope only.
- Hot-path budget: mode visibility changes are profile/settings events, not per-frame or per-sample work.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules: none. Replay should consume the same effective map mode state as live; thermalling auto-mode remains separately suppressed in existing replay runtime wiring.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| UI owns fallback policy | `CODING_RULES.md` UI rules forbid business logic/state derivation in Compose | targeted test plus code review | remove calls in `MapComposeEffects.kt`; add `MapFlightModePolicyTest` |
| `FlightDataManager` remains a card preference owner | `AGENTS.md` ownership defaults and SSOT rules | grep/code review + compile | remove `loadVisibleModes(...)` and `CardPreferences` dependency from `FlightDataManager.kt` |
| New duplicate visibility reader in map | `CODING_RULES.md` state duplication and repository rules | code review + grep for `getProfileAllFlightModeVisibilities` under `feature/map` | Phase 3 review |
| Runtime visibility gate regresses | `CODING_RULES.md` ownership move/API boundary test matrix | unit/VM/root binding tests | `MapFlightModePolicyTest`, VM tests |
| Map interaction changed without evidence | `CODING_RULES.md` map visual SLO gate | phase gate | SLO evidence only if gesture/menu runtime behavior changes beyond data source rewiring |

## 3) Data Flow

Before:

```text
CardPreferences
  -> FlightDataManager.loadVisibleModes(...)
  -> FlightDataManager.visibleModesFlow
  -> MapOverlayStack / CustomMapGestures / thermalling runtime

MapComposeEffects
  -> FlightDataManager.isCurrentModeVisible(...)
  -> FlightDataManager.getFallbackMode()
  -> onModeChange(CRUISE)
```

After:

```text
CardPreferences
  -> FlightDataViewModel / FlightProfileStore
  -> FlightDataViewModel.profileModeVisibilities
  -> map VM bridge
  -> MapFlightModePolicy
  -> MapFlightModeUiState
  -> MapOverlayStack / CustomMapGestures / thermalling runtime
```

## 4) Implementation Phases

### Phase 0 - Plan and seam lock

Goal:
- Record the narrow target before implementation and avoid broad churn.

Files changed:
- `docs/refactor/Map_Flight_Mode_Ownership_Cleanup_Phased_IP_2026-04-12.md`

Evidence from code pass:
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightVisibility.kt` is internal and owns card visibility defaults.
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt` exposes `profileModeVisibilities`.
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt` directly reads `CardPreferences` in `loadVisibleModes(...)`.
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` applies fallback in Compose.

Exit criteria:
- This plan exists and names the exact Phase 1 file/test targets.

### Phase 1 - Pure policy and tests only

Goal:
- Create a pure resolver for map visible modes and requested/effective fallback behavior.

Files to change:
- `feature/map/src/main/java/com/example/xcpro/map/MapFlightModePolicy.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapFlightModePolicyTest.kt`

Ownership/file split:
- `MapFlightModePolicy.kt` owns only typed map mode projection:
  - input: requested `FlightMode` and `Map<FlightModeSelection, Boolean>`;
  - output: visible `List<FlightMode>` and effective `FlightMode`;
  - no persistence, no Flow, no Android, no manager calls.

Behavior to lock:
- missing visibility map means all modes visible;
- Cruise is always visible even if a false value appears;
- Thermal hidden means Thermal request falls back to Cruise;
- Final Glide hidden means Final Glide request falls back to Cruise;
- visible requested mode remains effective;
- any empty or invalid derived visible set sanitizes to Cruise.

Exit criteria:
- Targeted pure tests pass.
- No production wiring change yet.

### Phase 2 - Move mode state ownership into map ViewModel

Goal:
- Add VM-owned requested/effective/visible map mode state using the pure policy.

Files likely to change:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelStateBuildersTest.kt` or a focused VM test file

Constraints:
- Split user requests from runtime-applied thermalling mode changes if needed. A hidden Thermal card should not be reselected by user-facing profile visibility, while existing thermalling automation must continue to respect replay suppression and the Thermal visibility gate.
- Do not call `CardPreferences` from the VM.
- Do not add business math to Compose.

Exit criteria:
- VM tests prove fallback and requested/effective mode behavior.

### Phase 3 - Bridge dfcards visibility state into map VM

Goal:
- Use the existing dfcards owner as the profile-mode visibility source.

Files likely to change:
- `feature/map/src/main/java/com/example/xcpro/map/CardIngestionCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelCardIngestion.kt`
- focused tests in `feature/map/src/test/java/com/example/xcpro/map/`

Constraints:
- The bridge may collect `FlightDataViewModel.profileModeVisibilities` but must not mutate card config except through existing dfcards APIs.
- The bridge must not become a second persistence owner.

Exit criteria:
- Map VM receives typed visibility data from dfcards state.
- No new `getProfileAllFlightModeVisibilities(...)` call under `feature/map` except existing temporary code until Phase 5 removal.

### Phase 4 - Rewire UI/runtime consumers

Goal:
- Replace manager-backed visible/current mode consumers with VM-owned state.

Files likely to change:
- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenThermallingRuntimeBinding.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelLifecycle.kt`
- `feature/map/src/main/java/com/example/xcpro/gestures/CustomMapGestures.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt`

Constraints:
- Compose effects render and coordinate lifecycle effects only; they must not decide fallback.
- Gesture/menu code consumes visible modes only.
- Thermalling runtime consumes an explicit Thermal-visible gate or visible modes from the VM owner.

Exit criteria:
- Overlay, gestures, and thermalling runtime no longer read `flightDataManager.visibleModesFlow`.
- `MapComposeEffects` no longer calls visibility/fallback methods on `FlightDataManager`.

### Phase 5 - Remove manager compatibility

Goal:
- Reduce `FlightDataManager` back toward live display/card sample management.

Files likely to change:
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- any factory/constructor callsites that pass `CardPreferences` only for visible modes
- related tests

Constraints:
- Remove `CardPreferences` from `FlightDataManager` only after the bridge and consumers are rewired.
- Keep display-card data flow behavior unchanged.

Exit criteria:
- `FlightDataManager` no longer owns `visibleModesFlow`, `loadVisibleModes(...)`, `isCurrentModeVisible(...)`, or `getFallbackMode()`.
- `feature/map` has no direct mode-visibility persistence read path.

### Phase 6 - Docs, gates, and governance

Goal:
- Sync durable docs only after behavior/wiring has actually changed.

Docs:
- Update `docs/ARCHITECTURE/PIPELINE.md` if the map/card mode wiring changes.
- Add an ADR only if a new durable public/cross-module API is introduced.
- Add `KNOWN_DEVIATIONS.md` only if a temporary rule violation remains after a phase.

Gates:
- `./gradlew :feature:map:compileDebugKotlin`
- targeted `:feature:map:testDebugUnitTest` tests for new map policy/VM tests
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## 5) Test Plan

Unit tests:
- `MapFlightModePolicyTest`:
  - default visibility makes all modes visible;
  - Cruise cannot be hidden;
  - hidden Thermal request falls back to Cruise;
  - hidden Final Glide request falls back to Cruise;
  - visible requested mode is preserved;
  - empty/sanitized visible set returns Cruise only.
- VM tests after Phase 2:
  - requested mode and effective mode are distinct when a profile hides the requested mode;
  - user mode selection persists through the existing flight-management preferences seam only when appropriate;
  - thermalling runtime mode switch respects Thermal visibility and replay suppression.
- Bridge tests after Phase 3:
  - dfcards `profileModeVisibilities` update reaches map VM state;
  - no direct `CardPreferences` read is needed in `FlightDataManager`.

Replay/regression tests:
- No new replay test is required in Phase 1 because the policy has no timebase or replay input.
- Add a regression only if later phases change thermalling replay suppression behavior.

SLO tests:
- Not required for Phase 1 pure policy.
- Reassess in Phase 4 if gesture/menu runtime behavior changes beyond replacing the data source.

## 6) Readiness Verdict

Ready with corrections.

Implementation should start only with Phase 1 pure policy and tests. The main correction from the second seam pass is that Phase 1 must not create a new map-side repository or direct preferences reader. The existing dfcards `FlightDataViewModel.profileModeVisibilities` / `FlightProfileStore` path is the current card configuration authority, and the map side should consume that authority through an explicit bridge before removing `FlightDataManager` compatibility.
