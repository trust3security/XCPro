# XCPro zoom / declutter audit

Date: 2026-03-28  
Repo inspected: `trust3security/XCPro` (`main`)

## Goal

Improve the visual experience when zooming so that:

- zooming in makes gliders easier to see and distinguish
- zooming out reduces clutter instead of letting icons and labels pile up
- watched / selected / important traffic stays visible without dominating the whole screen

## Executive summary

XCPro is not starting from zero.

The strongest existing foundation is the ADS-B path: it already has a zoom / viewport-aware declutter policy that changes icon scale, target limits, and label behavior.

The weakest areas for this specific problem are OGN traffic and the Live Follow watch-aircraft overlay:

- OGN already supports changing icon size, but the current runtime path does not expose the same kind of viewport-zoom policy that ADS-B has.
- OGN icon and label layers currently force overlap and ignore placement, which is a direct cause of clutter.
- The Live Follow watch-aircraft overlay uses a fixed icon scale and also forces overlap, so it does not naturally calm down when zoomed out.

## What is currently implemented

### 1) Map stack and gesture handling

The app uses MapLibre, but standard MapLibre gestures are explicitly disabled in the map initializer because XCPro uses a custom gesture system.

Practical impact: zoom-related UX changes need to be wired into XCPro runtime / overlay code, not assumed to happen automatically through default MapLibre behavior.

### 2) Traffic overlay architecture

Traffic rendering is split into separate paths:

- OGN traffic overlays
- ADS-B traffic overlays
- Live Follow watch-aircraft overlay
- additional OGN target helpers such as ring / line / badge overlays

This is good news because the declutter behavior can be improved incrementally instead of rewriting the entire map stack.

### 3) ADS-B current behavior

ADS-B already has a viewport declutter policy.

Current zoom bands in `AdsbTrafficViewportDeclutterPolicy.kt`:

- `zoom >= 10.5` → `iconScaleMultiplier = 1.0`, `maxTargets = 120`
- `zoom >= 9.25` → `iconScaleMultiplier = 0.88`, `maxTargets = 72`
- `zoom >= 8.25` → `iconScaleMultiplier = 0.78`, `maxTargets = 48`
- `else` → `iconScaleMultiplier = 0.68`, `maxTargets = 28`

It also decides whether all ADS-B labels may show based on viewport range, with a current threshold of `30,000 m`.

Runtime wiring already exists:

- `MapInitializer` updates ADS-B viewport zoom on initial camera setup and again on camera idle.
- `MapOverlayManagerRuntime` exposes `setAdsbViewportZoom(...)`.
- `AdsbTrafficOverlay` applies viewport policy changes through `setViewportZoom(...)`.

Weak point:

ADS-B icons still use overlap-friendly symbol settings. The existing policy helps, but the icon layers themselves still allow / ignore overlap, so stacks can still happen.

### 4) OGN current behavior

OGN already supports icon size changes, but the behavior is mostly static compared with ADS-B.

Current findings:

- `OgnTrafficOverlay` supports `setIconSizePx(...)`.
- `MapOverlayManagerRuntime` exposes `setOgnIconSizePx(...)`.
- `MapOverlayManagerRuntimeOgnDelegate` stores `ognIconSizePx` and forwards it to the OGN traffic and target-ring overlays.
- I did **not** find an OGN viewport-zoom method equivalent to ADS-B’s `setViewportZoom(...)` in this repo pass.

Current OGN rendering choices that directly create clutter:

- icon layer uses overlap enabled and placement ignored
- top label layer uses overlap enabled and placement ignored
- bottom label layer uses overlap enabled and placement ignored

So today OGN is basically saying: render everything, let it stack, and keep labels alive too.

That is the core problem.

### 5) Live Follow current behavior

The watch-aircraft overlay is even more fixed.

Current behavior:

- fixed icon bitmap size: `120 px`
- fixed symbol `iconSize = 2.0f`
- overlap enabled
- placement ignored

That means the watched aircraft stays very prominent, but it does not gracefully reduce visual weight when zoomed out.

### 6) Existing utilities already in the repo

There are already supporting utilities that can help with the improvement without inventing everything from scratch:

- `SailplaneIconBitmapFactory.create(iconSizePx: Int)` can generate glider icons at arbitrary sizes.
- `MapZoomConstraints` already contains zoom / scale logic, but it currently focuses on scale-bar behavior rather than traffic declutter.

### 7) Current target-cap behavior

There is already some target limiting in both traffic systems, but it is not unified.

- ADS-B uses zoom-aware caps through its viewport policy.
- OGN uses a much higher fixed cap (`MAX_TARGETS = 500`) and the feature builder stops adding features once that limit is reached.

I did not verify a priority sort earlier in the OGN pipeline during this pass, so that cap should be treated carefully. If truncation happens before priority ordering, important gliders can lose out to less important ones.

## Main causes of clutter in the current code

1. **Forced icon overlap on OGN**  
   OGN symbol layers are configured to draw over each other.

2. **Forced label overlap on OGN**  
   Both top and bottom labels are also configured to ignore placement.

3. **Fixed-size Live Follow watch icon**  
   The watched glider does not calm down visually when zooming out.

4. **ADS-B icons still overlap**  
   ADS-B has better policy plumbing than OGN, but the main icon layers still allow overlap.

5. **User icon size looks static instead of contextual**  
   Current icon-size controls appear to work more like a fixed base size than a base size multiplied by zoom context.

6. **No unified declutter strategy across traffic types**  
   ADS-B, OGN, and Live Follow are not yet following one shared zoom / density / priority model.

## Practical conclusion

XCPro already has the right architectural idea on the ADS-B side.

The fastest and safest path is:

1. extend that same zoom-aware policy concept to OGN
2. make Live Follow scale with zoom instead of staying fixed
3. tighten overlap rules for normal traffic
4. keep selected / watched / conflict / nearby traffic on a priority path so it stays visible

## Recommended direction

Treat the user-selected icon size as a **base size**, then multiply it by a zoom-aware policy.

That gives you all three outcomes you want:

- small and calm when zoomed out
- readable when zoomed in
- still user-configurable overall

## Files inspected during this pass

Primary files:

- `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapZoomConstraints.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/SailplaneIconBitmapFactory.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficMapApi.kt`
