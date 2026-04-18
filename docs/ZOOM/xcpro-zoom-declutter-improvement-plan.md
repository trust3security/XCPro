# XCPro zoom / declutter improvement plan

## Desired user experience

### When zoomed out

- glider icons become smaller
- most normal labels disappear
- the map does not turn into a solid block of overlapping sailplanes
- selected / watched / important traffic is still visible

### At mid zoom

- important traffic remains readable
- only priority labels appear
- nearby traffic can still be distinguished and tapped

### When zoomed in

- gliders are clear and easy to separate visually
- selected and watched aircraft are obvious but not oversized
- labels become useful instead of noisy

## Design rules

1. **Use base size + zoom multiplier**  
   Do not replace the user icon-size setting. Keep it as the base and apply zoom-aware scaling on top.

2. **Separate icon scaling from label visibility**  
   Icon size and label visibility should not be one hardcoded package.

3. **Separate priority traffic from normal traffic**  
   Selected, watched, conflict, or very-close traffic must stay visible even when normal traffic is heavily decluttered.

4. **Use density-aware behavior, not zoom alone**  
   Zoom is a major input, but visible target count and viewport range should also influence what is rendered.

5. **Avoid flicker**  
   Threshold changes should be debounced or use clear zoom bands so labels and icons do not shimmer on every tiny camera change.

## Recommended implementation approach

### A) Create a shared traffic declutter policy

Use ADS-B’s current policy as the starting template and generalize it.

Suggested policy inputs:

- `zoomLevel`
- `viewportRangeMeters`
- `visibleTargetCount`
- `selectedTargetId`
- `watchTargetId`
- `isConflict` / `isCloseTraffic`

Suggested policy outputs:

- `normalIconScaleMultiplier`
- `priorityIconScaleMultiplier`
- `showNormalTopLabels`
- `showNormalBottomLabels`
- `showPriorityLabels`
- `allowNormalOverlap`
- `allowPriorityOverlap`
- `maxNormalTargets`
- `maxPriorityTargets`
- `closeTrafficLabelDistanceMeters`

### B) Move to a two-layer or three-layer traffic model

Recommended rendering split:

1. **priority traffic layer**  
   selected target, watched aircraft, conflict / very-close traffic

2. **normal traffic layer**  
   all other visible traffic

3. **labels layer**  
   optionally split into priority labels and normal labels if needed

Why this matters:

If overlap is disabled globally, MapLibre may hide the wrong glider. A priority layer solves that by guaranteeing visibility for the traffic that matters most.

### C) Use zoom-aware icon scaling everywhere

Suggested starting point for **normal traffic** icon multipliers:

- `zoom < 8.25` → `0.60`
- `8.25 <= zoom < 9.25` → `0.75`
- `9.25 <= zoom < 10.5` → `0.90`
- `zoom >= 10.5` → `1.00`

Suggested starting point for **priority traffic** icon multipliers:

- `zoom < 8.25` → `0.75`
- `8.25 <= zoom < 9.25` → `0.88`
- `9.25 <= zoom < 10.5` → `1.00`
- `zoom >= 10.5` → `1.08` to `1.12`

Formula:

`renderedIconPx = clamp(userBasePx * zoomMultiplier, minPx, maxPx)`

Suggested guardrails:

- normal min: `12-14 px`
- normal max: `28-32 px`
- priority max: `34-38 px`

These values are **starting points**, not final truth. They should be tuned against real traffic screenshots.

### D) Tighten label rules aggressively

Suggested label behavior:

- `zoom < 8.25`  
  no normal labels; only selected / watched / conflict labels if truly needed

- `8.25 <= zoom < 9.25`  
  priority top-label only

- `9.25 <= zoom < 10.5`  
  priority top-label + close-traffic labels

- `zoom >= 10.5`  
  allow fuller labels, but still gate by density / viewport range

Also use viewport range and target density as a second gate. Even at decent zoom, if the screen is packed, labels should stay conservative.

### E) Make OGN target caps dynamic

Current OGN fixed cap is too blunt for this problem.

Suggested starting caps for **normal OGN traffic**:

- far zoom: `30-50`
- mid zoom: `60-90`
- close zoom: `120-200`

Priority targets should bypass the normal cap.

Before truncation, sort by priority score such as:

1. selected target
2. watched target
3. conflict / very close traffic
4. distance to ownship
5. freshness / live status
6. recency / other relevance signals already available

## File-by-file implementation plan

### 1) `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`

Use this file as the template for a shared policy.

Options:

- generalize it into `TrafficViewportDeclutterPolicy.kt`, or
- keep ADS-B-specific policy and add a matching OGN version first, then unify later

**Recommendation:** start by copying the ADS-B policy shape, then unify after the first working OGN version.

### 2) `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`

Add:

- `ognViewportZoom`
- `setViewportZoom(zoomLevel: Float)`
- immediate rerender when zoom crosses a meaningful band

Also keep the existing manual icon-size path, but treat it as the base size.

### 3) `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`

Add viewport-aware state similar to ADS-B.

Responsibilities:

- store current zoom policy
- compute rendered icon size from base size + policy multiplier
- apply label visibility rules
- optionally separate priority and normal traffic rendering

### 4) `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`

This is the main declutter hotspot.

Changes:

- stop hardcoding `iconAllowOverlap(true)` / `iconIgnorePlacement(true)` for all normal traffic
- stop hardcoding `textAllowOverlap(true)` / `textIgnorePlacement(true)` for all labels
- make icon size, label size, offset, and visibility policy-driven
- if needed, introduce separate symbol layers for priority vs normal targets

### 5) `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`

Add a public `setOgnViewportZoom(zoomLevel: Float)` method.

Optionally add a single traffic-facing entry point later, but do not block the first pass on that cleanup.

### 6) `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`

Where ADS-B zoom is updated today, also update OGN zoom.

Do it in both places:

- initial camera setup
- camera idle listener

That keeps OGN behavior aligned with what ADS-B already does.

### 7) `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`

Replace the fixed watch-aircraft scale with zoom-aware scaling.

Recommended behavior:

- keep the watched aircraft visible across all zoom levels
- shrink it more aggressively when zoomed out
- only keep overlap forced on if that is truly required for product behavior
- if overlap stays forced on, the icon still needs a much smaller far-zoom scale

### 8) Optional helper: `feature/map-runtime/src/main/java/com/trust3/xcpro/map/SailplaneIconBitmapFactory.kt`

Use this only if dynamic bitmap generation gives better visual fidelity than pure symbol scaling.

## Rollout phases

### Phase 1 — quick visible win

Ship a small, low-risk improvement first:

1. OGN viewport zoom plumbing
2. OGN zoom-aware icon scaling
3. hide most OGN labels at low zoom
4. Live Follow watch-aircraft scaling

This should already make the map feel much calmer.

### Phase 2 — proper declutter

1. shared or shared-shaped declutter policy
2. normal vs priority traffic layers
3. dynamic target caps
4. density-aware label rules

### Phase 3 — advanced polish

1. ranking improvements before truncation
2. optional very-low-zoom clustering for non-priority traffic
3. optional tap-to-expand / spiderfy behavior for dense stacks if still needed

## Acceptance criteria

- at far zoom, icons are visibly smaller and normal labels are mostly absent
- at close zoom, gliders are readable and individually tappable
- selected / watched / conflict traffic remains visible across zoom levels
- dense traffic no longer forms one solid overlapping block
- zoom changes do not create harsh flicker or unstable label popping
- no clear performance regression during pan / zoom / follow mode

## Test plan

### Unit tests

- policy band outputs for several zoom levels
- label visibility gates by zoom + viewport range
- target-cap rules and priority ordering

### Screenshot / visual tests

Capture the same traffic snapshot at four zoom bands:

- wide
- medium-wide
- medium
- close

Compare:

- icon size
- label presence
- overlap severity
- selected / watched aircraft visibility

### Manual tests

Test with at least:

- ~20 targets
- ~80 targets
- ~200 targets
- ~500 OGN targets

Also verify:

- selected target remains easy to identify
- watch-aircraft overlay stays visible but not oversized
- tap hit detection still works when icons shrink
- panning / zooming remains smooth

## Recommended first PR

Keep the first PR tight and obvious:

1. add OGN viewport zoom plumbing
2. make OGN icons zoom-aware
3. hide OGN labels below a threshold
4. make Live Follow watch-aircraft scale with zoom

That should produce a clear UX improvement without forcing a risky full overlay redesign in one go.
