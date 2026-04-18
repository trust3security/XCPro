# Map Screen Shell Phase 3 Seam Lock

## 0) Metadata

- Title: Phase 3 seam lock for MapScreen root and binding narrowing
- Owner: Codex
- Date: 2026-03-14
- Status: Draft
- Parent plan:
  - `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md`

## 1) Purpose

Lock the exact Phase 3 implementation cut before production edits begin.

This pass is intentionally narrow:

- Phase 3 only
- no broad repo code pass
- no new content-runtime extraction
- no `MapScreenViewModel` constructor/orchestration split
- no manager/runtime ownership rewrite

## 2) Decision Summary

Phase 2 fixed the scaffold/content output seam. Phase 3 should now finish the
root side of that seam by narrowing binding collection and scaffold-input
assembly, not by starting another shell rewrite.

The professional cut is:

- split `MapScreenBindings.kt` by concern
- move remaining root-only UI collection into focused root binding helpers
- narrow `rememberMapScreenScaffoldInputs(...)` to grouped inputs
- make `MapScreenRoot.kt` an assembler over helpers, managers, effects, and the
  grouped scaffold/content handoff

Do not churn these already-focused files unless compile fallout forces it:

- `MapScreenRootHelpers.kt`
- `MapScreenRootEffects.kt`
- `MapScreenContentRuntime.kt`
- `MapScreenScaffold.kt`
- `MapScreenScaffoldContentHost.kt`

## 3) Exact Remaining Seam Inventory

### 3.1 Current Root Hotspots

Source file:

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt`

Observed shape:

- file size: `250` lines
- direct `collectAsStateWithLifecycle(...)` callsites still in root: `7`
- the root is already partly decomposed, but the remaining mixed ownership is
  concentrated in state collection and scaffold-input fanout

| Lines | Current Responsibility | Current Owner | Phase 3 Owner | Notes |
|---|---|---|---|---|
| 45 | `mapUiState`, `weGlideUploadPrompt` collection | `MapScreenRoot.kt` | focused root UI binding helper | root should consume grouped values, not collect them inline |
| 59-62 | `orientationData`, `windArrowState`, `showWindSpeedOnVario`, `showHawkCard` collection | `MapScreenRoot.kt` | split: orientation stays near runtime/effects wiring, other UI state moves to root UI binding helper | keep runtime-specific state separate from scaffold/content UI state |
| 63-66 | `hiddenCardIds` derivation | `MapScreenRoot.kt` | root UI binding helper | display-only derivation from existing VM state |
| 115 | task panel visibility derivation | `MapScreenRoot.kt` | keep in root or move to a focused root helper | acceptable in root if still only used for back handling and gating |
| 126 | `rememberMapScreenBindings(...)` | `MapScreenBindings.kt` | grouped binding collectors | current collector is too mixed |
| 130-148 | traffic overlay port/render-state assembly | `MapScreenRoot.kt` | keep in root | root-owned integration wiring is acceptable here |
| 150 | `currentFlightModeSelection` collection | `MapScreenRoot.kt` | root UI binding helper | consumed only by effect/scaffold assembly |
| 217-255 | `rememberMapScreenScaffoldInputs(...)` call with broad raw parameter fanout | `MapScreenRoot.kt` + `MapScreenScaffoldInputs.kt` | grouped scaffold-assembly inputs | this is the main remaining root churn point |

### 3.2 Current Binding Hotspot

Source file:

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindings.kt`

Observed shape:

- file size: `130` lines
- direct `collectAsStateWithLifecycle(...)` callsites: `42`
- map state, replay/session state, task state, and traffic state are still
  collected in one mixed function

| Lines | Current Responsibility | Current Owner | Phase 3 Owner | Notes |
|---|---|---|---|---|
| 17-38 | broad `MapScreenBindings` data model | `MapScreenBindings.kt` | focused binding groups | current model crosses unrelated concerns |
| 45-56 | map/session collection | `MapScreenBindings.kt` | map/session binding group | root consumes these broadly today |
| 57-79 | traffic collection | `MapScreenBindings.kt` | dedicated traffic binding helper/group | should reuse the existing `MapTrafficUiBinding` seam |
| 80-86 | task + saved-camera collection | `MapScreenBindings.kt` | task/viewport binding group | these do not need to sit in one broad binding object |
| 88-124 | broad object assembly | `MapScreenBindings.kt` | thin aggregator or removed file | if retained, it must be a wrapper only |

### 3.3 Files That Are Already Narrow Enough

| File | Reason To Leave Alone In Phase 3 |
|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootHelpers.kt` | already owns widget layout, variometer layout, safe-container tracking, and runtime-controller setup in focused helpers |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt` | already owns back handling and lifecycle/effect wiring in focused functions |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootStateBindings.kt` | already establishes the preferred helper pattern for flight cards, profile/look-and-feel, and airspace binding |

Professional constraint:

- extend the helper pattern already present in `MapScreenRootStateBindings.kt`
  before inventing a new root-facade type

## 4) Phase 3 Ownership Targets

### 4.1 Binding Groups

Owner target:

- new file: `MapScreenBindingGroups.kt`

Owns:

- focused binding models and collectors split by concern
- map/viewport bindings
- session/runtime bindings
- task bindings
- traffic binding helper that returns the existing `MapTrafficUiBinding`

Must not own:

- manager construction
- scaffold/content rendering
- business rules

### 4.2 Root UI Binding Helper

Owner target:

- extend `MapScreenRootStateBindings.kt`

Owns:

- root-only UI/state collection currently done inline in `MapScreenRoot.kt`
- `mapUiState`
- `weGlideUploadPrompt`
- `windArrowState`
- `showWindSpeedOnVario`
- `showHawkCard`
- `hiddenCardIds`
- `currentFlightModeSelection`

Must not own:

- manager creation
- runtime-controller creation
- overlay effect wiring

### 4.3 Scaffold-Assembly Input Narrowing

Owner target:

- `MapScreenScaffoldInputs.kt`

Owns:

- grouped scaffold-input assembly from already-collected root bindings/helpers

Phase 3 rule:

- narrow the input signature by grouped sources, not by creating one new
  replacement god-object

Preferred input groups:

- navigation/chrome args
- root UI bindings
- grouped map/session/task/traffic bindings
- widget layout binding
- variometer layout state
- profile/look-and-feel binding
- flight cards binding

## 5) Concrete Phase 3 File Cut

### 5.1 Files To Modify

| File | Why It Changes | Ownership Change |
|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindings.kt` | split broad binding collection by concern | stops being the mixed collector for unrelated state |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootStateBindings.kt` | add the remaining root UI binding helper | root stops collecting scattered UI state inline |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt` | accept grouped sources instead of broad raw parameter fanout | scaffold assembly becomes a grouped binding consumer |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt` | become assembler-only over grouped helpers and builders | root stops adapting scattered leaf-feature and shell UI state directly |

### 5.2 Files To Add

| File | Responsibility |
|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindingGroups.kt` | focused binding models and remember-functions for map/session/task/traffic groups |

### 5.3 Files Explicitly Not Touched In Phase 3

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSectionInputs.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootHelpers.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenManagers.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`

If implementation pressure reaches those files, stop and re-scope. That means
the work is spilling into another phase.

## 6) Concrete First PR Order

1. Extract focused binding groups from `MapScreenBindings.kt`.
2. Add the root-only UI binding helper to `MapScreenRootStateBindings.kt`.
3. Narrow `rememberMapScreenScaffoldInputs(...)` to grouped sources.
4. Slim `MapScreenRoot.kt` to assemble grouped bindings, managers, effects, and
   the final scaffold/content handoff.

Reason for this order:

- binding groups define the real seam
- root UI binding helper then removes the remaining inline collection
- scaffold-input narrowing becomes mechanical once grouped sources exist
- root cleanup is last so it reflects the proven seam rather than inventing one

## 7) Phase 3 Stop Rules

Stop and re-evaluate if any of these become necessary:

- changing the Phase 2 `MapScreenScaffoldInputs` output shape
- changing `MapScreenContent(...)` or content-runtime ownership again
- modifying `MapScreenRootHelpers.kt` or `MapScreenRootEffects.kt` for style-only reasons
- rewriting manager creation or runtime-controller ownership
- starting `MapScreenViewModel` collaborator extraction
- introducing one new broad `RootBindings` or `ScaffoldInputsArgs` replacement object

## 8) Phase 3 Smoke Checklist

### Root Assembly

- Map screen still enters cleanly and renders the map.
- General-settings launch on initial entry still opens once.
- Drawer open/close and back handling still behave the same.

### Overlay / Runtime Wiring

- Traffic overlay still appears with live updates.
- Weather overlay effects still apply and clear normally.
- Replay playing state still updates camera/runtime wiring as before.

### Scaffold / Shell

- Bottom tabs, QNH dialog, and prompt rendering still receive the same inputs.
- WeGlide prompt still appears and dismisses.
- Widget layout offsets and sizes still restore correctly.
- Variometer size/offset persistence still commits.

### Task / Navigation

- Task panel visibility still blocks back navigation correctly.
- AAT edit actions still route through the same callbacks.

## 9) Implementation Notes

- Prefer deleting inline collection from `MapScreenRoot.kt` over merely moving
  it under another wide local block.
- Reuse `MapScreenRootStateBindings.kt` as the binding-helper home unless a new
  file is required to stay under file-size pressure.
- Reuse `MapTrafficUiBinding` as the traffic binding contract; Phase 3 should
  not invent a parallel traffic model.
- `MapScreenBindings.kt` may remain temporarily, but only as a thin adapter
  over grouped binding models. If it still looks like a mixed collector at the
  end of the phase, the phase is not done.
