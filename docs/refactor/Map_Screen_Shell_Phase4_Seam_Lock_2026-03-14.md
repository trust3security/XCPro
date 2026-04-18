# Map Screen Shell Phase 4 Seam Lock

## 0) Metadata

- Title: Phase 4 seam lock for MapScreenViewModel narrowing
- Owner: Codex
- Date: 2026-03-14
- Status: Draft
- Parent plan:
  - `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md`

## 1) Purpose

Lock the exact Phase 4 implementation cut before any `MapScreenViewModel`
production edits begin.

This pass is intentionally narrow:

- Phase 4 only
- no broad code pass
- no `ui/` shell rewrites
- no new root/scaffold/content contract work
- no speculative mega collaborator

## 2) Decision Summary

Phases 1-3 already removed the broad shell coupling around
`MapScreenContentRuntime`, scaffold/content contracts, and `MapScreenRoot`.

The professional Phase 4 move is therefore not to "split the ViewModel" in the
abstract. It is to extract only the two concern seams that are now proven by the
finished shell work:

- profile/style/layout routing
- WeGlide prompt collection and resolution

Everything else that is already a focused helper or coordinator should stay as
it is in the first Phase 4 batch.

The first implementation batch should:

- reduce constructor width
- reduce the direct method cluster at the end of `MapScreenViewModel.kt`
- keep `MapScreenViewModel` as the single screen-state owner
- avoid touching root/scaffold/content files

## 3) Exact Remaining Seam Inventory

### 3.1 Current ViewModel Shape

Source file:

- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`

Observed shape:

- file size: `392` lines
- constructor block: `20` lines
- constructor dependencies: `28`
- direct public method cluster still concentrated at lines `295-383`

### 3.2 What Is Already Narrow Enough

These are already extracted and should not be reopened in the first Phase 4
batch:

| Existing Helper / Coordinator | Current Source | Why It Stays |
|---|---|---|
| UI event routing | `MapScreenUiEventHandler.kt` | already isolates drawer/UI-edit intent handling |
| Waypoint + QNH actions | `MapScreenWaypointQnhCoordinator.kt` | already owns waypoint load and QNH command routing |
| Replay orchestration | `MapScreenReplayCoordinator.kt` | already owns replay/demo/racing orchestration |
| Traffic orchestration | `MapScreenTrafficCoordinator` via `MapScreenViewModelRuntimeWiring.kt` | already owns the traffic runtime seam |
| Flow/state builders | `MapScreenViewModelStateBuilders.kt` | already isolates derived `StateFlow` construction |
| Observer binding | `MapScreenViewModelObservers.kt` | already isolates background observer wiring |
| Thermalling binding | `MapScreenThermallingRuntimeBinding.kt` | already isolates thermalling runtime bridging |

Professional constraint:

- Phase 4 should reuse this existing extraction pattern instead of replacing it
  with one new generic collaborator object.

### 3.3 Remaining Direct Owner Clusters

| Lines | Current Responsibility | Current Owner | Phase 4 Owner | Notes |
|---|---|---|---|---|
| 72-73 | `activeProfileId`, `qnhProfileSwitchJob` | `MapScreenViewModel.kt` | profile/session coordinator | local bookkeeping for profile-scoped routing |
| 277-282 | WeGlide pending prompt collection in `init` | `MapScreenViewModel.kt` | WeGlide prompt bridge | prompt seam is now explicit in root/content UI |
| 295-296 | map style command + persistence | `MapScreenViewModel.kt` | split: command stays in VM, persistence/profile routing goes to profile/session coordinator | `setMapStyle(...)` still touches screen state directly |
| 299-314 | map visibility + traffic toggle/selection forwarding | `MapScreenViewModel.kt` | stay in VM for first batch | already thin bridges over the traffic coordinator and prompt notification controller |
| 316-341 | profile-scoped style/units/orientation/glider/trail/QNH/variometer routing | `MapScreenViewModel.kt` | profile/session coordinator | highest-value proven seam |
| 348-357 | task gesture + AAT edit forwarding | `MapScreenViewModel.kt` | stay in VM for first batch | thin enough unless a second batch is needed |
| 361-381 | WeGlide prompt confirm/dismiss resolution | `MapScreenViewModel.kt` | WeGlide prompt bridge | second highest-value proven seam |
| 383 | cleanup fanout | `MapScreenViewModel.kt` | stay in VM for first batch | still the screen owner teardown point |

## 4) Phase 4 Ownership Targets

### 4.1 Profile / Session Routing Seam

Owner targets:

- new file: `MapScreenProfileSessionDependencies.kt`
- new file: `MapScreenProfileSessionCoordinator.kt`

Owns:

- active profile bookkeeping
- profile-scoped calls to:
  - `mapStyleUseCase`
  - `unitsUseCase`
  - `orientationSettingsUseCase`
  - `gliderConfigUseCase`
  - `variometerLayoutUseCase`
  - `trailSettingsUseCase`
  - `qnhUseCase`
- profile style re-application into `MapStateStore`
- profile-aware variometer layout routing
- persisted map-style save path

Must not own:

- `MapStateStore` as an authoritative state owner
- screen `StateFlow` exposure
- traffic/replay/business logic outside this concern

Phase 4 rule:

- `setMapStyle(...)` may stay in the ViewModel because it is still a direct
  screen-state command. The coordinator should own profile/style persistence and
  profile-scoped switching, not all map commands.

### 4.2 WeGlide Prompt Seam

Owner target:

- new file: `MapScreenWeGlidePromptBridge.kt`

Owns:

- collecting `pendingPrompt` into UI-facing prompt state
- confirm/dismiss resolution
- queue/enqueue outcome mapping
- prompt notification resolution synchronization

Must not own:

- other ViewModel UI state
- toast transport ownership beyond the callbacks/flows passed in
- unrelated replay/task logic

### 4.3 Keep in ViewModel for the First Batch

These should remain in `MapScreenViewModel.kt` during the first Phase 4 batch:

- `MapStateStore` ownership and exposed `StateFlow` surface
- `setMapStyle(...)`
- `setFlightMode(...)`
- `setMapVisible(...)`
- traffic toggle/selection forwarding
- task gesture + AAT edit forwarding
- `onCleared()`

Reason:

- they are already thin bridges or direct screen-state ownership points
- they are not the highest-ROI line-budget fix
- extracting them first risks facade churn rather than ownership improvement

### 4.4 Optional Phase 4B Only If Needed

If the first batch does not get `MapScreenViewModel.kt` under the enforced line
budget, the only acceptable follow-up seam is:

- task edit + gesture forwarding (`createTaskGestureHandler`, `enterAATEditMode`,
  `updateAATTargetPoint`, `exitAATEditMode`)

Do not start with this seam.

## 5) Concrete Phase 4 File Cut

### 5.1 Files To Modify

| File | Why It Changes | Ownership Change |
|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` | remove proven profile/prompt orchestration from the main body | becomes thinner while staying the single screen owner |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTestRuntime.kt` | adapt constructor wiring to narrowed dependency groups | test helper stays aligned to the production seam |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelCoreStateTest.kt` | keep profile/style/QNH assertions covering the extracted seam | preserves behavior contract through the split |

### 5.2 Files To Add

| File | Responsibility |
|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenProfileSessionDependencies.kt` | concern-specific injected dependency group for profile/style/layout routing |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenProfileSessionCoordinator.kt` | profile-scoped style, units, trail, QNH, and variometer routing |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenWeGlidePromptBridge.kt` | pending-prompt collection and confirm/dismiss resolution |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenWeGlidePromptBridgeTest.kt` or `MapScreenViewModelWeGlidePromptTest.kt` | prompt-resolution coverage, currently missing |

### 5.3 Files Explicitly Not Touched In Phase 4A

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenRuntimeDependencies.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelObservers.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelRuntimeWiring.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`

If implementation pressure reaches those files, stop and re-scope.

## 6) Concrete First PR Order

1. Add `MapScreenProfileSessionDependencies.kt`.
2. Add `MapScreenProfileSessionCoordinator.kt` and move:
   - `activeProfileId`
   - `qnhProfileSwitchJob`
   - `persistMapStyle(...)`
   - `setActiveProfileId(...)`
   - `ensureVariometerLayout(...)`
3. Add `MapScreenWeGlidePromptBridge.kt` and move:
   - prompt collection from `init`
   - `onConfirmWeGlideUploadPrompt(...)`
   - `onDismissWeGlideUploadPrompt(...)`
4. Re-measure `MapScreenViewModel.kt` line count and constructor width.
5. Only if still needed, consider the optional Phase 4B task-edit forwarding cut.

Reason for this order:

- profile/session routing is the largest proven direct block
- prompt routing is self-contained and already explicit in the shell/UI seam
- both cuts reduce line count and constructor width without changing SSOT

## 7) Phase 4 Stop Rules

Stop and re-evaluate if any of these become necessary:

- moving `MapStateStore` or exposed `StateFlow` ownership out of the ViewModel
- introducing one generic `MapScreenViewModelCollaborators` facade
- reopening Phase 1-3 UI/root/scaffold/content files
- rewriting replay or traffic coordinators that are already extracted
- changing prompt UX/behavior rather than just moving ownership
- adding a Hilt binding/module when a concern-specific `@Inject` dependency group
  class would suffice

## 8) Phase 4 Smoke / Review Checklist

### Profile / Style / Layout

- Switching active profile still applies the profile-scoped map style.
- Units still switch with the active profile.
- Trail settings still switch with the active profile.
- QNH profile routing still follows the active profile.
- Variometer layout ensure path still works after profile switch.

### Prompt

- Pending WeGlide prompt still appears on the map screen.
- Confirm still queues or skips with the same user message semantics.
- Dismiss still clears the prompt and resolves notifications.

### Existing Thin Bridges

- `setFlightMode(...)` still updates the map state and flight-data side.
- task/AAT edit flows still work unchanged.
- traffic toggles and selections still route through the traffic coordinator.

## 9) Implementation Notes

- Use the existing `MapScreenRuntimeDependencies.kt` pattern as a reference for
  concern-specific dependency grouping, but keep Phase 4 groups tightly scoped.
- The first batch should be enough to fix the current line-budget violation.
- If the first batch does not materially reduce the file, reassess before adding
  a second collaborator. Do not keep extracting until the ViewModel becomes a
  shell over arbitrary wrappers.
- There is currently no map-level WeGlide prompt test coverage. Add it as part
  of the prompt seam rather than deferring it again.
