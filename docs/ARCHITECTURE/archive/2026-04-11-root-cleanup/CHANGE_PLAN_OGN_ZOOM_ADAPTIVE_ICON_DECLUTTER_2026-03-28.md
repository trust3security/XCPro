# CHANGE_PLAN_OGN_ZOOM_ADAPTIVE_ICON_DECLUTTER_2026-03-28.md

## Purpose

Reduce OGN aircraft clutter on MapScreen by making rendered OGN aircraft icons
shrink as the user zooms out, while preserving the existing persisted user icon
size preference as the base size.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN zoom-adaptive icon declutter
- Owner: XCPro Team
- Date: 2026-03-28
- Issue/PR: TBD
- Status: In progress (`Phase 0` complete, `Phase 1` implemented, `Phase 2` implemented)

## 1) Scope

- Problem statement:
  OGN traffic icons currently keep one fixed rendered pixel size that comes from
  the persisted OGN icon-size preference. When the pilot zooms the map out, the
  number of visible OGN targets increases but the icon footprint does not
  shrink, so dense scenes become cluttered and can cover the map.
- Why now:
  This is a direct MapScreen usability problem in dense OGN scenes and is
  already aligned with the MapScreen SLO contract for pan/zoom smoothness and
  traffic marker stability.
- In scope:
  - Keep the user-configured OGN icon-size preference as the base size.
  - Add a zoom-aware rendered-size policy for OGN map overlays.
  - Reuse the existing startup and camera-idle zoom hooks already used by ADS-B.
  - Apply the derived rendered size to OGN traffic icons and the selected-target
    ring so the visuals stay aligned.
  - Add focused runtime/helper tests for zoom-driven size updates.
- Out of scope:
  - Changing OGN receive radius, sorting, viewport culling, target cap, or
    backend behavior.
  - Changing ADS-B behavior in this change.
  - Persisting a second "effective" icon-size preference.
  - Reworking OGN traffic rendering into a new overlay architecture.
- User-visible impact:
  - OGN icons become smaller as the user zooms out, reducing clutter.
  - The manual OGN icon-size setting still acts as the pilot's chosen base size.
- Rule class touched: Default

## 1A) Narrow Seam Re-pass Findings

1. Initial OGN traffic overlays are created too early for startup zoom to be the
   only seam:
   - `MapInitializer.setupMapStyle(...)` calls `setupOverlays(map)` before
     `setupInitialPosition(map)`, so OGN traffic overlays can be created before
     the intended initial camera zoom is applied.

2. There is already a narrower startup correction seam in runtime than a new
   ViewModel, Compose, or UI-shell zoom handoff:
   - `MapInitializer.setupInitialPosition(...)` already calls
     `overlayManager.setAdsbViewportZoom(zoomToUse.toFloat())` after the first
     camera move.
   - OGN should reuse that same initializer and camera-idle runtime seam,
     instead of adding viewport zoom to `onMapReady(...)` or UI binding state.

3. Style reloads do not re-fire that initial-position seam:
   - `MapRuntimeController.applyStyle(...)` routes style callbacks to
     `overlayManager.onMapStyleChanged(currentMap)`, which recreates traffic
     overlays without another `setupInitialPosition(...)` pass.
   - Because of that, OGN runtime must cache the last viewport zoom and reuse it
     during overlay recreation and lazy overlay creation.

4. The generic shell zoom hook is not the narrowest safe seam:
   - `MapOverlayManagerRuntime.onZoomChanged(...)` exists, but no production
     caller uses it today. Reusing it would require extra wiring without buying
     us startup correctness.

5. `MapTrafficOverlayReadyConfig` is settings/config state, not map-camera SSOT:
   - viewport zoom should not be persisted or modeled as traffic settings state.
   - `applyMapReadyTrafficOverlayConfig(...)` should remain base-settings apply
     only and must not become a second live map-zoom path.
   - the authoritative long-lived render-only owner remains the OGN runtime
     delegate.

6. The existing OGN base-size apply path is also a bypass that Phase 2 must
   close:
   - `MapOverlayManagerRuntimeOgnDelegate.setIconSizePx(...)` currently applies
     raw persisted base size directly to the traffic overlay and target ring.
   - Under active zoom declutter, both base-size changes and viewport-zoom
     changes must route through one shared "resolve effective rendered size then
     apply" path, or settings updates will snap icons back to oversized raw
     preference values.

7. OGN replacement and lazy-create paths also still use raw base size today:
   - `createOgnTrafficOverlay(...)`, `initializeTrafficOverlays(...)`, and the
     lazy target-ring recreation path all currently receive `ognIconSizePx`
     directly.
   - Phase 2 must ensure every overlay creation path consumes the resolved
     effective rendered size, not the persisted base size, or style reloads and
     lazy creation will regress to oversized icons.

8. The runtime bridge is still missing the OGN zoom input:
   - `MapOverlayManagerRuntime` already exposes `setAdsbViewportZoom(...)`, but
     not `setOgnViewportZoom(...)`.
   - Phase 2 must add that bridge before `MapInitializer` can wire startup and
     camera-idle zoom into OGN runtime.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN icon size preference | `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt` | `iconSizePxFlow` via `OgnTrafficFacade` | runtime or UI treating derived rendered size as the persisted source |
| Current map zoom | `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt` plus map camera callbacks | `currentZoom` state and camera idle/init callbacks | OGN preference repo or ViewModel owning map zoom |
| Effective rendered OGN icon size | `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | imperative runtime apply only | persisted state, ViewModel state, or Compose-owned authoritative state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Base OGN icon size preference | `OgnTrafficPreferencesRepository` | existing settings actions only | `OgnTrafficFacade.iconSizePx` -> `MapScreenViewModel` -> runtime | user setting | `OgnTrafficPreferencesRepository` | existing settings reset/profile restore only | N/A | existing preference tests |
| Effective rendered OGN icon size | `MapOverlayManagerRuntimeOgnDelegate` | `setIconSizePx(baseSize)` and new viewport-zoom update path only, both routed through one shared recompute/apply path | delegate -> overlay handle `setIconSizePx(...)` with effective rendered size | base OGN icon size + viewport zoom sizing policy | none | map detach, style recreation, new base size, zoom change | N/A | new sizing helper tests + runtime delegate tests |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` OGN map-runtime helpers and delegate
  - `feature:map-runtime` runtime bridge
  - `feature:map` map init/camera callback wiring
- Any boundary risk:
  - Accidentally moving a render-only zoom policy into preferences, ViewModel, or
    Compose.
  - Accidentally treating the derived rendered size as a new persisted setting.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` | ADS-B already accepts viewport zoom as a separate runtime input | keep base size preference separate from viewport-derived display policy | OGN may only need derived icon-size scaling, not the full ADS-B declutter policy object |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt` | ADS-B overlay recalculates render policy from base size plus viewport context | keep zoom-aware visual policy local to the overlay/runtime slice | OGN can stay imperative and reuse existing `setIconSizePx(...)` path |
| `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` | already pushes initial zoom and camera-idle zoom into ADS-B runtime | reuse the same startup/camera-idle event sources for OGN | add OGN runtime feed without widening ViewModel state |
| `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt` | existing OGN lifecycle tests already cover overlay recreation and cached-runtime behavior | extend runtime-level lifecycle verification instead of starting with UI-shell callback tests | add zoom-specific assertions only where runtime ownership actually changes |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN rendered size policy under zoom | no owner; fixed base preference is applied directly | `MapOverlayManagerRuntimeOgnDelegate` plus a focused zoom-sizing helper | render-only map behavior belongs in runtime overlay ownership, not persistence or ViewModel | helper tests + runtime delegate tests + manual zoom validation |
| OGN base-size updates while zoom declutter is active | raw base-size setter path inside OGN runtime | `MapOverlayManagerRuntimeOgnDelegate` shared recompute/apply path | settings updates must preserve the active viewport-derived size policy | runtime delegate tests |
| Startup viewport-zoom handoff to OGN runtime | no explicit owner | `MapInitializer.setupInitialPosition(...)` plus the existing camera-idle listener | map initialization already owns first camera position and first runtime zoom handoff | focused initializer test + runtime delegate tests + manual cold start |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| initial OGN overlay creation during `MapInitializer.setupMapStyle(...)` | OGN overlay can render before intended initial camera zoom is applied | `MapInitializer.setupInitialPosition(...)` pushes OGN viewport zoom after the first camera move, and OGN runtime caches/reuses that zoom | Phase 2 |
| `MapInitializer` camera-idle listener | only ADS-B receives camera-idle zoom for runtime declutter | also push camera-idle zoom into OGN runtime | Phase 2 |
| `MapOverlayManagerRuntimeOgnDelegate.setIconSizePx(...)` | raw base size is applied directly to overlay and ring | base-size updates call shared effective-size recompute/apply using cached viewport zoom | Phase 2 |
| OGN overlay creation paths (`createOgnTrafficOverlay(...)`, `initializeTrafficOverlays(...)`, lazy target-ring create) | replacement/lazy overlays are created with raw persisted base size | every creation path uses the delegate's resolved effective rendered size | Phase 2 |
| style reload recreation path (`MapRuntimeController.applyStyle(...) -> overlayManager.onMapStyleChanged(...)`) | OGN overlay recreation has no viewport-zoom memory | OGN runtime delegate caches last viewport zoom and reapplies it on recreation/lazy creation | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_OGN_ZOOM_ADAPTIVE_ICON_DECLUTTER_2026-03-28.md` | New | change contract and phased execution | required for non-trivial map runtime work | not production code | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficViewportSizing.kt` | New | pure zoom-to-rendered-size policy | isolates visual sizing rules from runtime plumbing | not in prefs repo; this is not persisted policy | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | Existing | OGN runtime ownership for base size, zoom input, and derived rendered size apply | already owns OGN overlay lifecycle and imperative size apply | not ViewModel or Compose; keep render-only state local | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` | Existing | runtime bridge API for OGN viewport zoom | same owner as existing ADS-B runtime zoom bridge | not in UI; keep shell/runtime seam together | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` | Existing | map startup and camera-idle zoom forwarding | already owns initial zoom and camera-idle callbacks | not in ViewModel; zoom source is map runtime | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficViewportSizingTest.kt` | New | pure policy coverage | lock scaling curve/clamps independent of map runtime | not runtime test only; helper is pure | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` | New | runtime zoom wiring coverage | mirror existing ADS-B viewport zoom test pattern | not in `feature:map`; delegate lives in `feature:traffic` | No |
| `feature/map/src/test/java/com/example/xcpro/map/MapInitializerTest.kt` or focused equivalent | New | startup and camera-idle OGN zoom forwarding coverage | lock the real runtime seam that owns initial and ongoing viewport zoom handoff | not in UI-shell tests; zoom does not belong to scaffold config state | Maybe |
| `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt` | Existing | style recreation and runtime reapply coverage | already owns OGN overlay lifecycle assertions | not duplicated in UI-shell tests; overlay recreation is runtime behavior | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `setOgnViewportZoom(zoomLevel: Float)` on runtime bridge | `feature:map-runtime` `MapOverlayManagerRuntime` | `feature:map` `MapInitializer` | existing runtime surface visibility | OGN needs the same zoom feed ADS-B already gets | stable additive API |
| `OgnTrafficViewportSizing` helper | `feature:traffic` OGN map runtime | OGN runtime delegate tests and implementation | `internal` | canonical owner for render-only zoom sizing policy | no compatibility shim planned |

Intentional non-move:

- Do not add viewport zoom to `OgnTrafficPreferencesRepository`,
  `MapScreenViewModel`, `MapScreenScaffoldInputs`, or
  `MapTrafficOverlayReadyConfig` as authoritative state. Zoom is live
  map-camera input, not traffic settings SSOT.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Base OGN icon size preference | N/A | configuration, not time-derived |
| Current viewport zoom | N/A | camera/view state, not time-derived |
| OGN render throttle cadence | Monotonic | existing delegate render scheduling stays unchanged |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - no new dispatcher decisions; existing map runtime apply path remains in the
    current runtime scope.
- Primary cadence/gating source:
  - existing initial-position and camera-idle zoom callbacks.
- Hot-path latency budget:
  - this change should stay within the current OGN overlay style-update path and
    must not introduce per-frame recomposition-driven churn.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none in fused/domain data.
  - for a given replay input and the same user zoom actions, rendered icon size
    must be identical across runs.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| derived rendered size becomes a second persisted owner | SSOT / state contract | review + focused tests | plan + delegate tests |
| zoom-aware sizing logic leaks into ViewModel/UI | UI/runtime ownership | review + file plan | change plan + code review |
| startup still shows wrong OGN size because overlays are created before `setupInitialPosition(...)` | startup runtime correctness | runtime/init tests | initializer zoom-forwarding test + delegate tests |
| camera-idle zoom changes do not reapply OGN size | runtime zoom wiring | unit/runtime test | new OGN delegate viewport zoom test |
| base icon-size changes overwrite active viewport declutter and reapply raw preference size | runtime ownership correctness | unit/runtime test | new OGN delegate viewport zoom test |
| style reload recreates OGN overlays without reapplied zoom-aware size | style/runtime recreation correctness | unit/runtime and lifecycle test | new OGN delegate viewport zoom test + OGN lifecycle test |
| target ring drifts from icon footprint after zoom-based resize | visual consistency | helper/runtime test + manual validation | delegate test + manual map check |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Dense OGN scenes are less cluttered when zoomed out | `MS-UX-03` | fixed-size icons at all zooms | icons shrink at lower zooms with no unreadable crowding | helper tests + manual dense-scene validation | Phase 2 |
| Pan/zoom with OGN enabled keeps visual readability | `MS-UX-01` | fixed OGN icon footprint under zoom-out | improved readability without new zoom hitching | manual validation plus existing MapScreen evidence lane if implementation lands | hardening |
| OGN overlay style updates stay lightweight | `MS-ENG-01` | existing style-update path | no new heavy rerender loop; style property apply only on zoom/base-size changes | runtime tests + review | hardening |

## 3) Data Flow (Before -> After)

Before:

`OgnTrafficPreferencesRepository.iconSizePxFlow -> MapScreenViewModel.ognIconSizePx -> Map traffic UI binding -> runtime setOgnIconSizePx(base size) -> OGN overlay uses one fixed rendered size`

After:

`OgnTrafficPreferencesRepository.iconSizePxFlow -> MapScreenViewModel.ognIconSizePx -> runtime setOgnIconSizePx(base size)`

and

`MapInitializer.setupInitialPosition(zoomToUse) + MapInitializer camera-idle zoom -> runtime setOgnViewportZoom(current zoom)`

and on style recreation:

`MapRuntimeController.applyStyle -> overlayManager.onMapStyleChanged(currentMap) -> OGN delegate reuses cached viewport zoom for replacement overlays`

then

`MapOverlayManagerRuntimeOgnDelegate (base size + cached viewport zoom) -> shared effective-size recompute -> OgnTrafficViewportSizing -> effective rendered size -> OGN traffic overlay + target ring`

## 4) Implementation Phases

### Phase 0 - Contract lock

- Goal:
  lock the ownership model and choose the smallest safe runtime seam.
- Files to change:
  - this plan
- Ownership/file split changes:
  - none yet
- Tests to add/update:
  - identify ADS-B viewport zoom tests to mirror
- Exit criteria:
  - base preference owner and runtime-derived size owner are explicit
- Status update:
  - Completed on 2026-03-28 during the narrow seam/code pass

### Phase 1 - Pure sizing policy

- Goal:
  define the OGN zoom-to-rendered-size policy in a pure helper with bounded
  clamps and thresholds.
- Files to change:
  - new `OgnTrafficViewportSizing.kt`
  - new `OgnTrafficViewportSizingTest.kt`
- Ownership/file split changes:
  - helper owns render-only zoom sizing policy
- Tests to add/update:
  - min/default/max size behavior
  - zoom threshold behavior
  - clamp behavior at very low/high zoom
- Exit criteria:
  - policy is deterministic and test-locked without map/runtime dependencies
- Status update:
  - Implemented on 2026-03-28 in `OgnTrafficViewportSizing.kt` with focused unit coverage

### Phase 2 - Runtime wiring and style-safe zoom reapply

- Goal:
  feed viewport zoom into OGN runtime, cache it in the OGN runtime delegate,
  and apply the effective rendered size across startup, camera-idle, and style
  recreation paths.
- Files to change:
  - `MapOverlayManagerRuntimeOgnDelegate.kt`
  - `MapOverlayManagerRuntime.kt`
  - `MapInitializer.kt`
  - new `MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt`
- Ownership/file split changes:
  - OGN runtime delegate now owns base size, cached viewport zoom, and effective rendered size.
  - `setIconSizePx(baseSize)` and `setOgnViewportZoom(zoomLevel)` both route through one shared effective-size recompute/apply path.
  - replacement/lazy overlay creation paths consume the resolved effective rendered size, never raw persisted base size.
  - `MapInitializer.setupInitialPosition(...)` and the existing camera-idle listener remain the only viewport-zoom producers for startup and ongoing map interaction.
  - `MapScreenScaffoldInputs` and `MapTrafficOverlayReadyConfig` stay base-settings apply only; they do not become zoom state owners.
- Tests to add/update:
  - new runtime bridge API `setOgnViewportZoom(...)` is exercised from both startup and camera-idle `MapInitializer` paths
  - lazy/style-recreated overlay creation receives cached viewport zoom
  - startup initial-position handoff applies post-overlay-create zoom
  - camera-idle zoom change reapplies effective size
  - base icon-size change after cached zoom preserves zoom declutter instead of reapplying raw base size
  - same zoom/effective size does not trigger unnecessary work
- Exit criteria:
  - OGN overlay and target ring resize correctly after `setupInitialPosition(...)`, on camera idle, after base-size changes, and after style recreation
- Status update:
  - Implemented on 2026-03-28 in OGN runtime, map-runtime bridge, `MapInitializer`, and focused runtime/init tests

### Phase 3 - Hardening and validation

- Goal:
  verify that the change reduces clutter without creating new runtime drift.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` if runtime wiring text changes materially
- Ownership/file split changes:
  - none
- Tests to add/update:
  - extend any affected lifecycle/runtime tests if wiring needs broader coverage
- Exit criteria:
  - required checks pass
  - manual dense-scene zoom validation is acceptable
  - `PIPELINE.md` is updated if the runtime zoom feed is documented differently

## 5) Test Plan

- Unit tests:
  - `OgnTrafficViewportSizingTest.kt`
  - `MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt`
  - `MapInitializerTest.kt` or focused equivalent for OGN zoom forwarding
- Replay/regression tests:
  - none specific beyond deterministic helper/runtime behavior; replay data path is unchanged
- UI/instrumentation tests (if needed):
  - only if camera-idle sequencing proves unreliable in local runtime testing
- Degraded/failure-mode tests:
  - invalid/non-finite zoom falls back safely to base size or ignores update
- Boundary tests for removed bypasses:
  - startup `setupInitialPosition(...)`, camera-idle zoom, base-size changes under cached zoom, and style recreation all reach OGN runtime correctly
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | unit tests + regression cases | pure viewport sizing helper tests |
| Time-base / replay / cadence | fake clock + deterministic repeat-run tests | existing cadence unchanged; deterministic runtime tests only |
| Ownership move / bypass removal / API boundary | boundary lock tests | delegate viewport zoom tests + initializer/lifecycle seam tests |
| UI interaction / lifecycle | UI or instrumentation coverage | manual camera-idle zoom validation; instrumentation only if local behavior disagrees |
| Performance-sensitive path | benchmark, metric, or SLO artifact | manual dense OGN zoom validation; MapScreen evidence lane if implementation broadens |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| shrink curve is too aggressive and makes icons unreadable | usability regression | use bounded minimum rendered size and validate at default/max base size | XCPro Team |
| slider semantics become confusing if runtime size fully replaces base size | settings UX confusion | keep base size persisted and document that zoom scaling is render-only | XCPro Team |
| OGN target ring no longer matches icon footprint | inconsistent selected-target visuals | apply the same effective size to the target ring | XCPro Team |
| settings icon-size changes overwrite active zoom declutter | usability regression after adjusting OGN size | route settings updates through the same shared effective-size recompute path and cover it in runtime tests | XCPro Team |
| startup seam is corrected but style reload seam is missed | stale or wrong size after map style changes | cache viewport zoom in OGN runtime delegate and cover recreation path in tests | XCPro Team |
| change introduces excessive zoom-triggered work | map runtime regression | reuse existing `setupInitialPosition(...)` and camera-idle hooks, not `onMapReady(...)`, per-frame callbacks, or Compose current-zoom effects | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  This is an additive render-only runtime policy change inside existing map
  boundaries. No durable module-boundary or public API architecture move is
  planned beyond a small runtime bridge addition.
- Why this belongs in a plan instead of ADR notes:
  the main work is scoped execution and validation, not a broad architecture
  policy change.

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Base OGN icon size preference remains authoritative in `OgnTrafficPreferencesRepository`
- Effective rendered OGN icon size remains runtime-only and non-persisted
- Replay behavior remains deterministic for the same replay input and zoom actions
- For map/overlay interaction changes: impacted visual SLOs are reviewed and any
  required evidence/deviation is handled before merge
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - `setOgnViewportZoom(...)` runtime bridge
  - `OgnTrafficViewportSizing` helper
  - OGN delegate zoom-aware apply path
- Recovery steps if regression is detected:
  - revert the new zoom-aware OGN runtime path
  - return OGN overlays to persisted base-size-only behavior
  - rerun required checks and manual map validation
