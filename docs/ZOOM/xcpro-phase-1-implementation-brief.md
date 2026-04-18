# XCPro Phase 1 implementation brief

## Title

Phase A corrective plan for true Phase 1 OGN declutter and Live Follow watch-aircraft scaling

## Status against current HEAD

This brief is re-based against the current `fix/aircraft-clutter` branch state,
not the earlier pre-implementation plan.

Current branch reality:

- OGN zoom-band icon sizing is implemented through:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportSizing.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- OGN immediate label visibility is implemented through style restyling in:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
- Live Follow watch-aircraft zoom scaling is implemented in:
  - `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt`

Current branch regressions that block release-grade readiness:

1. A new OGN cluster/screen-declutter path was added on top of Phase 1.
2. OGN tap behavior changed from direct target selection to cluster expand-on-tap.
3. The watched-aircraft overlay still has a style-recreation hole when zoom/state
   stay unchanged.

Verdict:

`Ready with corrections`

Phase A should correct those regressions without widening scope.

## Requested Phase A outcome

Restore the branch to the actual Phase 1 contract:

- OGN traffic icons scale by the existing four zoom bands.
- OGN top/bottom labels are gated by zoom through style visibility only.
- The user OGN icon-size preference remains the base size input.
- Live Follow watched-aircraft scale remains zoom-aware.
- OGN target tap behavior stays direct and predictable.
- No new cluster, regroup, or expand-on-tap behavior ships in this phase.
- Watched-aircraft survives map style recreation even when zoom and watched
  state do not change.

## Why Phase A exists

The current branch already landed most of the intended user-visible Phase 1
value, but it also crossed into Phase 2 behavior.

The cluster path is not a harmless refactor:

- it adds new feature-generation ownership
- it adds new runtime hit/result types
- it changes tap semantics in the map gesture layer
- it makes cluster topology depend on rendered icon size
- it does not recompute topology on zoom-only restyles

That violates the intended Phase 1 rule:

- zoom changes should restyle OGN layers only
- Phase 1 should not redesign clustering or tap-selection behavior

So Phase A is a corrective cut-back, not a new feature.

## Pipeline and owner path

Relevant pipeline anchors from `docs/ARCHITECTURE/PIPELINE.md`:

- `MapInitializer.setupInitialPosition(...)` and camera-idle callbacks are the
  viewport-zoom producers for traffic declutter.
- `MapOverlayManagerRuntimeOgnDelegate` is the OGN traffic runtime owner.
- `MapLiveFollowRuntimeLayer` is the owner seam for watched-aircraft runtime
  rendering.
- `LiveFollowWatchAircraftOverlay` is render-only MapLibre layer ownership, not
  repository or ViewModel ownership.

No repository, ViewModel, or persistence owner changes are required in Phase A.

## Reference patterns reviewed

Reference files reviewed:

- `origin/main:feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `origin/main:feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
- `origin/main:feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `origin/main:feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficOverlayRuntimeState.kt`
- `origin/main:feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
- `origin/main:feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- `origin/main:feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
- current `HEAD` versions of those same files plus:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt`

Pattern/structure to reuse:

- keep viewport zoom and rendered icon-size ownership in
  `MapOverlayManagerRuntimeOgnDelegate`
- keep OGN overlay behavior as one traffic overlay source/layer owner
- keep zoom-only behavior as style restyling, not source rebuild
- keep watched-aircraft ownership in `MapLiveFollowRuntimeLayer` ->
  `LiveFollowWatchAircraftOverlay`

Intentional deviation from current branch:

- remove branch-added OGN cluster/tap flow entirely from Phase A
- keep only the narrow viewport scaling and label-visibility slice

## Seam findings from the narrow code pass

### 1) The new OGN cluster path is branch-only scope expansion

The cluster path was introduced through these files:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterModels.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`

`origin/main` did not have cluster feature generation, cluster hit results, or
cluster expand-on-tap.

### 2) The cluster path is inconsistent with Phase 1 zoom semantics

Current cluster membership is computed from rendered icon size in:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt`

But zoom changes only restyle OGN layers through:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`

That leaves cluster topology stale until the next traffic render.

Conclusion:

The cluster path should not remain in Phase A.

### 3) The watched-aircraft seam is correct, but the overlay reapply is incomplete

The correct owner path is already in place:

- `MapScreenContentRuntime.currentZoom`
- `MapLiveFollowRuntimeLayer(currentZoom = ...)`
- `LiveFollowWatchAircraftOverlay.setViewportZoom(...)`

But `LiveFollowWatchAircraftOverlay.setViewportZoom(...)` currently returns
early when the resolved scale is unchanged, so style recreation at unchanged
zoom/state can leave the overlay absent until another trigger arrives.

Conclusion:

Keep the current owner seam and fix the style-reapply behavior inside
`LiveFollowWatchAircraftOverlay`.

### 4) OGN viewport policy ownership is already correct enough for Phase A

The branch now has one explicit OGN zoom-band owner:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt`

And one rendered-size owner:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportSizing.kt`

That ownership is acceptable for Phase A and should stay.

## Phase A scope

### In scope

1. Keep OGN viewport zoom-band icon scaling.
2. Keep OGN immediate top/bottom label visibility restyling.
3. Remove all branch-added OGN cluster/screen-declutter/render-hit behavior.
4. Restore direct OGN target tap behavior.
5. Keep watched-aircraft zoom scaling.
6. Fix watched-aircraft style recreation with unchanged zoom/state.
7. Rebase tests to the corrected Phase A seam.

### Out of scope

Do not do these in Phase A:

- any OGN clustering or regrouping
- cluster count labels
- expand-on-tap camera behavior
- spiderfy
- ranking changes
- `MAX_TARGETS` changes
- overlap-flag redesign
- shared ADS-B/OGN declutter extraction
- repository, ViewModel, or persistence refactors

Those remain Phase 2 or later.

## SSOT ownership and dependency direction

### SSOT ownership

- OGN base icon size preference
  - authoritative owner: existing traffic settings/repository path
  - read seam here: `MapOverlayManagerRuntimeOgnDelegate.setIconSizePx(...)`
  - forbidden duplicate: overlay-owned preference state

- OGN viewport zoom
  - authoritative owner: `MapOverlayManagerRuntimeOgnDelegate`
  - exposed as: runtime-only forwarded value into `OgnTrafficOverlay`
  - forbidden duplicate: second viewport authority in support/helpers

- OGN label visibility
  - authoritative owner: `OgnTrafficViewportDeclutterPolicy`
  - applied by: `OgnTrafficOverlay` plus `OgnTrafficOverlaySupport`
  - forbidden duplicate: feature-generation label blanking as an independent
    authority

- OGN target hit resolution
  - authoritative owner: rendered OGN target features in `OgnTrafficOverlay`
  - forbidden duplicate: parallel cluster-hit model in Phase A

- watched-aircraft zoom
  - authoritative owner: `MapLiveFollowRuntimeLayer` input from existing map
    zoom flow
  - applied by: `LiveFollowWatchAircraftOverlay`
  - forbidden duplicate: ViewModel/repository mirror

### Dependency direction

Remains:

`UI runtime -> map runtime render objects`

No new domain, repository, or Android boundary adapter is needed.

## File ownership plan

| File | New / Existing | Owner / Responsibility | Why this file owns it | Why not another layer/file |
|---|---|---|---|---|
| `docs/ZOOM/xcpro-phase-1-implementation-brief.md` | Existing | Phase A corrective plan | This is the active implementation brief | Do not spread phase ownership across chat history |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt` | Existing | Canonical OGN zoom-band policy | It already owns the four zoom bands | Do not move policy into UI or runtime helpers |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportSizing.kt` | Existing | Base-size to rendered-size resolution | It already owns rendered icon-size resolution | Keep one rendered-size owner |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` | Existing | OGN overlay runtime layer/source behavior | Overlay owns MapLibre source and style mutation | Delegate should not own feature generation |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` | Existing | OGN layer construction and style property helpers | This is the existing OGN style helper seam | Keep style helpers out of the delegate |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` | Existing | Branch-only cluster feature path | This file is only needed because the cluster path was added | Delete or retire in Phase A because cluster behavior is out of scope |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt` | Existing | Branch-only screen clustering policy | This file exists solely for cluster grouping | Delete or retire in Phase A |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterModels.kt` | Existing | Branch-only cluster hit/render models | This file exists solely for cluster behavior | Delete or retire in Phase A |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficOverlayRuntimeState.kt` | Existing | OGN runtime handle contract | This is the runtime seam between map shell and traffic overlay handles | Do not hide contract changes in callers |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | Existing | Cached OGN viewport input and rendered-size forwarding | It is the runtime owner already wired from `MapInitializer` | Do not duplicate runtime state in overlay helpers |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt` | Existing | Public map-runtime API surface | This is the shell-facing runtime contract owner | Keep shell APIs centralized |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt` | Existing | Map tap routing | The gesture layer owns user tap behavior | Do not hide tap semantics inside the delegate |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt` | Existing | Ring overlay rendering and hit resolution | It owns the target-ring runtime seam | Keep ring hit behavior local to the ring overlay |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | Existing | Watched-aircraft overlay lifecycle and zoom forwarding | This is the actual UI-runtime owner seam | Do not route through unrelated runtime managers |
| `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt` | Existing | Watched-aircraft scale/style application | It owns the MapLibre layer/source for the watched aircraft | The composable should not own style mutation details |

## Phase A file change plan

### Production files expected to change

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - remove `resolveRenderItems(...)`
  - remove cluster-aware hit path
  - keep viewport zoom state and style-only label gating
  - restore direct target feature rendering

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
  - keep icon-size and label-visibility restyling
  - remove branch-added cluster layer/style behavior
  - retain top/bottom label visibility toggles

- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficOverlayRuntimeState.kt`
  - keep `setViewportZoom(...)`
  - revert OGN hit APIs from `findHitAt(...)` to direct `findTargetAt(...)`

- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
  - keep rendered-size ownership and viewport zoom caching
  - remove cluster hit / expand APIs
  - restore direct target lookup

- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
  - keep `setOgnViewportZoom(...)`
  - remove public cluster hit / expand APIs
  - keep direct `findOgnTargetAt(...)`

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
  - restore direct OGN target tap path
  - remove cluster expand-on-tap behavior

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`
  - keep rendered-size ring behavior if it is already part of the viewport-sized
    Phase 1 path
  - revert hit API to direct target key return

- `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
  - keep zoom-band scaling
  - fix style recreation so runtime objects are reapplied even when the
    resolved scale is unchanged

### Files expected to be deleted or retired from the production path

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficScreenDeclutterModels.kt`

If any helper from those files remains useful after cluster removal, move only
the non-cluster logic into the canonical OGN overlay/support files and delete
the cluster-only artifacts.

## Test plan for Phase A

### Tests to delete or rewrite

- `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateClusterTapTest.kt`
- `feature/traffic/src/test/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureScreenDeclutterTest.kt`

Those tests lock Phase 2 behavior and should not stay attached to Phase 1.

### Tests to keep and adjust

- `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt`
  - keep viewport sizing expectations
  - remove cluster-specific API expectations

- `feature/map/src/test/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlayTest.kt`
  - keep zoom-band scale expectations
  - add style-recreation regression coverage

### Tests to add

1. OGN direct target hit regression
   - close-zoom taps still resolve a target key
   - no cluster hit type remains in the Phase A path

2. Watched-aircraft style recreation regression
   - unchanged zoom and unchanged watched state still recreate/apply the layer
     correctly after style replacement

3. OGN zoom restyle regression
   - zoom changes still restyle icon size and top/bottom label visibility
   - no zoom-only source rebuild requirement is introduced by Phase A

## Time base declaration

Phase A remains render-only runtime work.

Newly introduced time-dependent values:

- none

Existing unchanged time usage:

- OGN stale/live alpha rendering continues to use existing monotonic runtime
  behavior in overlay render code

Forbidden:

- no wall/system time added in domain or fusion
- no replay/live divergence introduced by the corrective cut-back

## Replay determinism

- Deterministic for same input: yes
- Randomness used: none
- Replay/live divergence rule: none added

Reason:

Phase A only removes cluster behavior from render/tap flow and fixes a render
 reapply hole in the watched-aircraft overlay.

## Delivery order

1. Remove the branch-added OGN cluster screen-declutter models and policy.
2. Restore direct OGN feature rendering and target hit resolution.
3. Restore direct OGN tap routing in the map gesture layer.
4. Keep OGN viewport zoom sizing and immediate label gating intact.
5. Fix watched-aircraft style recreation in `LiveFollowWatchAircraftOverlay`.
6. Rebase tests from cluster behavior back to true Phase 1 behavior.
7. Run focused tests, then required repo gates, then manual map QA.

## Acceptance criteria

### Functional

- OGN icons still scale by the existing four zoom bands.
- OGN top/bottom labels are visible only in the close zoom band.
- OGN zoom changes restyle the overlay without needing a zoom-only source
  rebuild.
- OGN tap selection resolves aircraft directly again.
- Live Follow watched-aircraft still scales across all four bands.
- Live Follow watched-aircraft survives style recreation without waiting for a
  new zoom or watched-state change.

### Technical

- no new SSOT owner
- no ViewModel or repository changes
- no new timebase risk
- no cluster path remains in Phase A production code

## QA checklist

1. Dense OGN traffic at close zoom
   - confirm direct aircraft tap selection still works

2. Dense OGN traffic across zoom bands
   - confirm icon scaling and top/bottom label gating still work

3. Wide-zoom startup
   - confirm OGN starts reduced and labels stay hidden

4. Close-zoom startup
   - confirm OGN starts at base size with labels visible

5. Change OGN icon size in settings
   - confirm all bands still scale from the new base size

6. Watched aircraft across zoom bands and after style swap
   - confirm scale changes immediately
   - confirm overlay remains visible after style recreation

## Verification

Required repo gates:

- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Focused tests expected during Phase A:

- OGN viewport sizing/policy tests
- OGN overlay direct target-hit regression
- Live Follow watched-aircraft scaling and style recreation tests

Map/runtime evidence obligations:

- impacted SLOs remain:
  - `MS-UX-01`
  - `MS-UX-03`
  - `MS-UX-04`
  - `MS-UX-06`
  - `MS-ENG-01`
  - `MS-ENG-02`

Manual map QA is still required before marking the PR ready.

## Rollback note

If Phase A goes wrong, rollback should target only the corrective commit and not
reintroduce the cluster path as part of the fix.

Phase A rollback intent:

- preserve current viewport zoom sizing owner
- preserve current label visibility owner
- preserve watched-aircraft scaling owner
- do not reopen cluster/tap redesign in the rollback

## Recommended next step

Implement Phase A exactly as a corrective cut-back:

1. keep viewport sizing and label gating
2. remove the cluster/tap expansion path
3. fix watched-aircraft style recreation

That yields the smallest release-grade Phase 1 slice from the current branch.
