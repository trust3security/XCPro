# CHANGE_PLAN_OGN_OFFSCREEN_TARGET_BADGE_2026-03-27.md

## Purpose

Add an ownship-anchored OGN target badge that stays visible when the selected
OGN target aircraft is outside the current map viewport.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN off-screen target badge beside ownship
- Owner: XCPro Team
- Date: 2026-03-27
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  When a pilot targets an OGN aircraft and that aircraft moves off-screen, the
  current map loses the only distance/relative-height cue for the selected target.
- Why now:
  Targeting is already persisted and rendered, but the pilot loses awareness as
  soon as the selected aircraft leaves the visible map bounds.
- In scope:
  - Add a render-only ownship-adjacent target badge for off-screen selected OGN targets.
  - Show target distance plus relative altitude beside the blue ownship overlay.
  - Use navy text when target altitude is at or above ownship, red when below.
  - Keep existing target ring and target line behavior unchanged.
  - Add cleanup/runtime wiring/tests for the new overlay handle.
- Out of scope:
  - Changing how an OGN target is selected or persisted.
  - Replacing the on-screen target ring/line visuals.
  - Adding new domain/repository state.
- User-visible impact:
  - A selected OGN target remains readable even when off-screen.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Selected OGN target enabled/key | `OgnTrafficPreferencesRepository` + existing target resolution runtime | existing flows/state | any new VM/runtime authoritative target state |
| Off-screen ownship badge visual state | OGN map overlay runtime | opaque runtime handle only | persisted state, ViewModel mirrors, Compose-owned authoritative state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Selected OGN target | existing OGN selection runtime | existing selection actions only | existing ViewModel -> map UI binding | prefs key + live target list | existing OGN prefs repo | clear target / disable overlay / target disappears | existing runtime semantics | existing target tests |
| Off-screen ownship badge render model | `MapOverlayManagerRuntimeOgnDelegate` + badge formatter | OGN runtime update calls only | runtime delegate -> overlay handle | target enabled, resolved target, ownship coord, ownship altitude, units, viewport visibility | none | target disabled, no ownship, target on-screen, map teardown/style recreate | monotonic render cadence only; content itself is time-independent | new runtime + formatter tests |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` runtime overlay contracts and OGN overlay implementation
  - `feature:map-runtime` runtime forwarding
  - `feature:map` runtime handle storage, cleanup, and UI-to-runtime adapter wiring
- Any boundary risk:
  - Accidentally introducing a second owner for selected-target state.
  - Accidentally moving viewport/render-only logic into ViewModel/Compose.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTargetRingOverlay.kt` | Additive OGN target visual overlay handle | map-native overlay with source/layer lifecycle, cleanup, bring-to-front | text badge instead of circle geometry |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTargetLineOverlay.kt` | Existing target-specific visual rendered from delegate | runtime delegate owns latest target inputs and redraw cadence | badge also needs ownship altitude + units for label text |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnRelativeAltitudeLabelFormatter.kt` | Existing relative altitude label policy | signed relative-height formatting | badge color uses navy/red sign rule instead of existing icon color bands |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Off-screen target readability | none | OGN runtime overlay layer | render-only concern belongs with existing OGN map overlays | runtime tests + manual map validation |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_OGN_OFFSCREEN_TARGET_BADGE_2026-03-27.md` | New | feature/change contract | required for non-trivial map runtime work | not production code | No |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing | pipeline contract | runtime overlay wiring changed | must stay in canonical pipeline doc | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/TrafficOverlayRuntimeState.kt` | Existing | cross-module opaque overlay handle contract | new overlay handle belongs in the same runtime state seam | not in `feature:map`; keep map-free contract in `feature:traffic` | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnOwnshipTargetBadgeRenderModel.kt` | New | pure badge text/color/visibility model | isolates render policy from MapLibre plumbing | not ViewModel/domain because this is render-only map policy | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt` | New | concrete MapLibre badge overlay | same ownership as existing target ring/line overlays | not Compose overlay; keep target visuals map-native | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | Existing | OGN overlay orchestration | authoritative runtime coordinator for OGN target visuals | not `MapScreenViewModel`; keep runtime-only render state local | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` | Existing | map-runtime forwarding API | owns shell/runtime bridge | not business logic | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` | Existing | shell constructor wiring | owns factory injection defaults | not `feature:traffic`; constructor lives in map shell | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayEffects.kt` | Existing | Compose side-effect forwarding only | render inputs already originate here | no rendering logic should live here | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayUiAdapters.kt` | Existing | UI/runtime adapter | keep forwarding logic together | not in ViewModel | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenState.kt` | Existing | runtime handle storage | authoritative runtime cache for map-owned handles | not in ViewModel state | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeStateAdapter.kt` | Existing | bridge from map shell state to traffic runtime seam | extends existing opaque handle mapping | not in `feature:traffic`; depends on map shell state | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleSurfaceAdapter.kt` | Existing | runtime cleanup | must release new overlay on destroy/cleanup | not elsewhere; shell owns lifecycle teardown | No |
| focused tests under `feature/map` and `feature/traffic` | Existing/New | runtime/formatter coverage | lock behavior and cleanup | not manual-only validation | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnOwnshipTargetBadgeOverlayHandle` + factory | `feature:traffic` runtime contract | `feature:map-runtime`, `feature:map` | existing cross-module runtime seam | new opaque overlay handle must cross module boundary | stable additive API |
| `updateOgnTargetVisuals(... ownshipAltitudeMeters, altitudeUnit, unitsPreferences ...)` | `feature:map-runtime` forwarding seam | map UI adapter callers | existing runtime API, additive signature update | badge needs formatted distance/height inputs | no compatibility shim planned |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| signed relative-height text | `feature/traffic/src/main/java/com/example/xcpro/map/OgnRelativeAltitudeLabelFormatter.kt` | OGN target badge render model | existing signed delta formatter already drives OGN relative labels | No |
| distance text formatting | `core/common/src/main/java/com/example/xcpro/common/units/UnitsFormatter.kt` | OGN target badge render model | canonical units formatter | No |
| badge text color sign rule | new badge render-model file | ownship target badge only | requirement is badge-specific and differs from icon-band policy | No |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN target visual redraw cadence | Monotonic | existing runtime throttling already uses monotonic render scheduling |
| Badge label text/color | N/A (input-derived only) | derived from latest render inputs; no time math |

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - Map overlay render scheduling stays on the existing map runtime coroutine scope.
- Primary cadence/gating sensor:
  - existing OGN target visual runtime updates.
- Hot-path latency budget:
  - additive render should stay within the current OGN target-visual path budget and not introduce extra repository/UI churn.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none; badge is a pure function of the selected target, ownship inputs, and viewport visibility.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| duplicate target state owner | MVVM + SSOT | review + unit/runtime tests | runtime delegate wiring tests |
| badge not cleaned up on lifecycle teardown | runtime ownership | unit test | `MapLifecycleManagerOgnTargetCleanupTest.kt` |
| target visuals rerender traffic overlay unexpectedly | runtime isolation | unit test | `MapOverlayManagerOgnLifecycleTest.kt` |
| wrong navy/red sign mapping | display policy correctness | unit test | new badge render model test |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Off-screen selected target remains readable near ownship | `MS-UX-03` | no off-screen cue | stable ownship-adjacent badge | focused runtime tests + manual validation | implementation complete |
| No flicker/z-order regression vs ownship and traffic overlays | `MS-UX-04` | current ring/line stable | badge remains visible with correct front order | runtime ordering checks + manual validation | hardening |
| OGN target visual updates stay lightweight | `MS-ENG-01` | current target visuals path | no extra traffic rerender from badge updates | `MapOverlayManagerOgnLifecycleTest.kt` | hardening |

## 3) Data Flow (Before -> After)

Before:

`Selected OGN target -> MapTrafficOverlayEffects -> MapOverlayManagerRuntimeOgnDelegate -> target ring + target line`

After:

`Selected OGN target + ownship coord/altitude + units -> MapTrafficOverlayEffects -> MapOverlayManagerRuntimeOgnDelegate -> target ring + target line + off-screen ownship badge`

## 4) Implementation Phases

### Phase 0 - Contract and references

- Goal:
  Lock architecture contract, reference pattern, and runtime seam.
- Files to change:
  - this plan
- Tests to add/update:
  - identify existing OGN runtime tests to extend
- Exit criteria:
  - ownership and additive runtime path are explicit

### Phase 1 - Overlay contract and render model

- Goal:
  Add the new opaque overlay handle and pure badge render model.
- Files to change:
  - `TrafficOverlayRuntimeState.kt`
  - new badge render-model file
  - new badge overlay file
- Tests to add/update:
  - badge formatter/color tests
- Exit criteria:
  - badge can be rendered independently from selection persistence

### Phase 2 - Runtime wiring

- Goal:
  Wire badge rendering through existing OGN target visual updates.
- Files to change:
  - `MapOverlayManagerRuntimeOgnDelegate.kt`
  - `MapOverlayManagerRuntime.kt`
  - `MapOverlayManager.kt`
  - `MapTrafficOverlayEffects.kt`
  - `MapTrafficOverlayUiAdapters.kt`
  - `MapScreenState.kt`
  - `MapOverlayRuntimeStateAdapter.kt`
  - `MapLifecycleSurfaceAdapter.kt`
- Tests to add/update:
  - OGN lifecycle and cleanup tests
- Exit criteria:
  - badge shows only for off-screen selected targets and does not trigger traffic rerenders

### Phase 3 - Docs and hardening

- Goal:
  Sync pipeline docs and verify cleanup/front-order behavior.
- Files to change:
  - `PIPELINE.md`
  - any small runtime/status docs if needed
- Tests to add/update:
  - focused runtime verification
- Exit criteria:
  - docs match implementation and required verification is recorded

## 5) Test Plan

- Unit tests:
  - badge render model visibility/text/color cases
  - runtime wiring and cleanup updates
- Replay/regression tests:
  - none expected beyond existing deterministic runtime/unit coverage because no replay-time logic changes
