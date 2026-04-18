# XCPro file touchpoints checklist

## Highest-value files to change first

### P0 — do these first

- [ ] `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`
  - **Current role:** updates current zoom and currently pushes zoom into ADS-B overlay behavior.
  - **Why it matters:** this is the cleanest existing place to fan zoom changes into other traffic overlays.
  - **Change:** also call an OGN viewport-zoom update; optionally route watch-aircraft zoom updates from the same camera events.

- [ ] `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
  - **Current role:** already exposes `setAdsbViewportZoom(...)`, `setAdsbIconSizePx(...)`, and `setOgnIconSizePx(...)`.
  - **Why it matters:** this is the public runtime seam that should also expose OGN zoom behavior.
  - **Change:** add `setOgnViewportZoom(...)`; consider a later cleanup to a more unified traffic zoom API.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
  - **Current role:** tracks OGN icon size and forwards it to overlays; no OGN viewport-zoom method found in this pass.
  - **Why it matters:** this is the real control point for OGN rerender behavior.
  - **Change:** add `ognViewportZoom`, zoom-band tracking, and rerender triggers.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - **Current role:** supports `setIconSizePx(...)` and renders OGN traffic.
  - **Why it matters:** this is where zoom-aware icon scaling and policy state should live.
  - **Change:** add `setViewportZoom(...)`; compute rendered icon size from base size + policy.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
  - **Current role:** creates OGN icon and label layers; current settings force overlap / ignore placement.
  - **Why it matters:** this file is the biggest direct cause of visual clutter.
  - **Change:** make overlap, label visibility, and size policy-driven; consider separate priority vs normal layers.

- [ ] `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
  - **Current role:** renders watched aircraft using a fixed icon scale.
  - **Why it matters:** the watch glider stays visually heavy when zoomed out.
  - **Change:** replace fixed scale with zoom-aware scale; keep visibility without oversized far-zoom rendering.

## P1 — strong follow-up tasks

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`
  - **Current role:** already defines a solid zoom-aware policy shape.
  - **Change:** use as the template for a shared or shared-shaped traffic declutter policy.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`
  - **Current role:** ADS-B labels already respond to policy, but icons still allow overlap.
  - **Change:** decide whether icon overlap should stay only for priority traffic instead of all traffic.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt`
  - **Current role:** holds OGN overlay constants.
  - **Change:** add zoom-band constants, label thresholds, and min / max rendered icon sizes.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficSelectionRuntime.kt`
  - **Current role:** selection runtime for traffic interactions.
  - **Change:** ensure selected traffic bypasses declutter caps and overlap restrictions.

## P2 — optional polish / cleanup

- [ ] `feature/map-runtime/src/main/java/com/trust3/xcpro/map/SailplaneIconBitmapFactory.kt`
  - **Use only if needed:** dynamic icon assets may help if expression scaling does not look good enough.

- [ ] `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapZoomConstraints.kt`
  - **Current role:** scale-bar / zoom-limit logic.
  - **Note:** probably should not own traffic declutter, but it is useful as a reminder that zoom utility logic already exists in map-runtime.

- [ ] `feature/traffic/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt` and related coordinator / facade files
  - **Change:** confirm user-configured icon size remains the base input rather than being replaced by zoom policy.

## Suggested implementation order

1. OGN viewport zoom plumbing
2. OGN icon scaling by zoom
3. OGN label visibility thresholds
4. Live Follow watch-aircraft scaling
5. shared policy extraction
6. priority-layer split
7. dynamic target ranking / caps
8. optional clustering or stack-expansion behavior

## Definition of done per file

Before marking a file complete, check all of the following:

- [ ] logic is backed by unit tests where practical
- [ ] selected / watched / conflict traffic still stays visible
- [ ] normal traffic no longer relies on permanent overlap flags where declutter should apply
- [ ] tap selection still works after icon-size changes
- [ ] no noticeable visual flicker when crossing zoom bands
- [ ] no obvious render-performance regression during pan / zoom

## Simple PR breakdown

### PR 1

- `MapInitializer.kt`
- `MapOverlayManagerRuntime.kt`
- `MapOverlayManagerRuntimeOgnDelegate.kt`
- `OgnTrafficOverlay.kt`
- `OgnTrafficOverlaySupport.kt`
- `LiveFollowWatchAircraftOverlay.kt`

**Goal:** obvious zoom-in / zoom-out improvement with minimal architecture churn.

### PR 2

- shared declutter policy files
- ADS-B / OGN alignment work
- priority-layer split

**Goal:** unify behavior and reduce long-term maintenance cost.

### PR 3

- ranking, clustering, stack handling, extra polish

**Goal:** finish the dense-traffic edge cases.
