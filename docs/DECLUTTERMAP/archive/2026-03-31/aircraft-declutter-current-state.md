> Archived on 2026-03-31.
> Superseded by [xcpro-close-proximity-declutter-brief.md](../../xcpro-close-proximity-declutter-brief.md) and [xcpro-phase-2a-close-proximity-declutter-plan.md](../../xcpro-phase-2a-close-proximity-declutter-plan.md).
> This document reflects pre-Phase 2A assumptions and should not be used as current declutter direction.

# Summary

- Confirmed: aircraft traffic rendering is split across `feature:traffic` and the map shell. Raw aircraft state lives in `feature:traffic` repositories/use cases, the map screen assembles UI binding state in `feature:map`, and actual map-layer rendering is done by MapLibre overlay classes in `feature:traffic`.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt` (`OgnTrafficRepositoryImpl`), `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt` (`AdsbTrafficRepositoryImpl`), `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` (`ognTargets`, `adsbTargets`), `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindingGroups.kt` (`rememberMapScreenTrafficBinding`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`.

- Confirmed: traffic aircraft icons are not Compose canvas elements and not DOM markers. They are MapLibre runtime overlays backed by `GeoJsonSource` plus `SymbolLayer` / `CircleLayer` / `LineLayer`.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` (`initialize`, `render`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` (`initialize`, `renderFrame`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` (`createOgnIconLayer`, `createOgnTopLabelLayer`, `createOgnBottomLabelLayer`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` (`createAdsbIconOutlineLayer`, `createAdsbIconLayer`, `createAdsbTopLabelLayer`, `createAdsbBottomLabelLayer`).

- Confirmed: both OGN and ADS-B icons currently render at true geographic coordinates with no per-aircraft display offset or custom collision layout. OGN features are emitted from target `latitude` / `longitude`; ADS-B features are emitted from target `lat` / `lon`.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` (`buildOgnTrafficOverlayFeatures`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt` (`toFeature`).

- Confirmed: the current "declutter" logic is zoom-policy only. It reduces icon size, hides some labels, and caps target count. It does not separate nearby aircraft in screen space.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportSizing.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt` (`select`).

- Confirmed: icon collision avoidance is explicitly disabled today for both traffic overlays.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` (`createOgnIconLayer` uses `iconAllowOverlap(true)` and `iconIgnorePlacement(true)`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` (`createAdsbIconOutlineLayer` and `createAdsbIconLayer` use `iconAllowOverlap(true)` and `iconIgnorePlacement(true)`).

- Recommended: the safest future declutter insertion point is inside the traffic overlay render path in `feature:traffic`, after authoritative targets have already been chosen but before the `GeoJsonSource` is updated. It should remain a display-only runtime concern, not a repository or ViewModel concern.
  Evidence: render ownership is already concentrated in `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`, and `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`.

# Relevant files and symbols

| File | Symbol(s) | Why it matters |
|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt` | `OgnTrafficRepository`, `OgnTrafficRepositoryImpl` | OGN traffic SSOT / state owner for raw target stream. |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt` | `publishTargets` | Publishes OGN targets in deterministic order. |
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt` | `AdsbTrafficRepository`, `AdsbTrafficRepositoryImpl` | ADS-B traffic SSOT / state owner for raw target stream. |
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt` | `select` | Filters, sorts, caps, and prepares ADS-B display targets. |
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficSelectionOrdering.kt` | `ADSB_DISPLAY_PRIORITY_COMPARATOR` | Current ADS-B display ordering policy. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/MapTrafficUseCases.kt` | `OgnTrafficUseCase`, `AdsbTrafficUseCase` | Use-case facade from repositories into the map slice. |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` | `ognTargets`, `adsbTargets`, `trafficCoordinator`, `trafficSelectionState` | ViewModel owns map-screen traffic UI state and selection state. |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindingGroups.kt` | `rememberMapScreenTrafficBinding`, `buildMapTrafficUiBinding` | Compose binding assembly for traffic state. |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenHotPathEffects.kt` | `MapTrafficOverlayRuntimeEffects` | Bridge from Compose state into overlay manager render calls. |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficOverlayUiAdapters.kt` | `createTrafficOverlayRenderPort`, `rememberTrafficOverlayRenderState` | Adapter from map shell to runtime overlay manager. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficOverlayEffects.kt` | `MapTrafficOverlayEffects` | Side-effect owner that pushes traffic state into runtime overlays. |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` | `MapOverlayManager` | Map-shell wrapper that owns runtime overlay manager composition. |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt` | `MapOverlayManagerRuntime` | Central runtime seam for traffic overlays and zoom / interaction forwarding. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | `initializeTrafficOverlays`, `updateTrafficTargets`, `setViewportZoom`, `findTargetAt` | OGN runtime delegate and render scheduling. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` | `initializeAdsbTrafficOverlay`, `updateAdsbTrafficTargets`, `setAdsbViewportZoom`, `findAdsbTargetAt` | ADS-B runtime delegate and render scheduling. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` | `initialize`, `setViewportZoom`, `render`, `findTargetAt` | OGN icon / label overlay implementation. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` | `createOgnIconLayer`, `createOgnTopLabelLayer`, `createOgnBottomLabelLayer`, `applyOgnViewportPolicyToStyle` | OGN icon anchor / size / rotation / label placement rules. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` | `buildOgnTrafficOverlayFeatures` | OGN feature generation from target lat/lon. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt` | `render`, `findTargetAt` | Separate OGN selected-target highlight overlay. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetLineOverlay.kt` | `render` | Separate OGN ownship-to-target line overlay. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt` | `render` | Separate ownship badge overlay tied to OGN target state. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` | `initialize`, `setViewportZoom`, `render`, `renderFrame`, `findTargetAt` | ADS-B icon / outline / label overlay implementation. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` | `createAdsbIconOutlineLayer`, `createAdsbIconLayer`, `createAdsbTopLabelLayer`, `createAdsbBottomLabelLayer`, `applyAdsbViewportPolicyToStyle` | ADS-B icon anchor / size / rotation / label placement rules. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt` | `toFeature` | ADS-B feature generation from target lat/lon. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt` | `onTargets`, `snapshot` | Visual-only ADS-B position interpolation before render. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt` | `resolveAdsbTrafficViewportDeclutterPolicy` | Current ADS-B zoom / viewport declutter policy. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt` | `resolveOgnTrafficViewportDeclutterPolicy` | Current OGN zoom declutter policy. |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt` | `setupInitialPosition`, `setupListeners` | Forwards zoom changes into traffic overlays. |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt` | `onMapTap` block | Hit-testing and tap priority resolution for traffic layers. |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficSelectionRuntime.kt` | `createTrafficSelectionState`, `MapTrafficSelectionState` | Current selected-aircraft state owner. |
| `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt` | `ensureRuntimeObjects` | Nearby precedent showing MapLibre `iconOffset(...)` is already used elsewhere in the map stack, although only with a fixed zero offset today. |

# Current aircraft rendering flow

1. Confirmed: raw aircraft state is produced in `feature:traffic` repositories and exposed as `StateFlow`.
   Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt` (`targets`, `snapshot`), `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt` (`targets`, `snapshot`).

2. Confirmed: `feature:traffic` use cases expose the repository state and traffic preferences through `OgnTrafficFacade` and `AdsbTrafficFacade`.
   Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/MapTrafficUseCases.kt` (`OgnTrafficUseCase`, `AdsbTrafficUseCase`).

3. Confirmed: `MapScreenViewModel` in `feature:map` is the screen-state owner for traffic data on the map screen. It collects target flows, overlay preferences, and selection state, but it does not render icons itself.
   Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` (`ognTargets`, `adsbTargets`, `selectedOgnTarget`, `selectedAdsbTarget`, `trafficCoordinator`).

4. Confirmed: Compose builds a `MapTrafficUiBinding` from the ViewModel flows, then uses side effects to push that state into the runtime overlay manager.
   Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindingGroups.kt` (`rememberMapScreenTrafficBinding`), `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenHotPathEffects.kt` (`MapTrafficOverlayRuntimeEffects`), `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficOverlayEffects.kt` (`MapTrafficOverlayEffects`).

5. Confirmed: `MapOverlayManager` / `MapOverlayManagerRuntime` in the map shell own the runtime map object and delegate traffic rendering to OGN and ADS-B delegates in `feature:traffic`.
   Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`, `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`.

6. Confirmed: OGN rendering path is:
   `OgnTrafficRepositoryImpl.targets`
   -> `OgnTrafficUseCase.targets`
   -> `MapScreenViewModel.ognTargets`
   -> `MapTrafficUiBinding.ognTargets`
   -> `MapTrafficOverlayEffects.updateOgnTrafficTargets(...)`
   -> `MapOverlayManagerRuntimeOgnDelegate.updateTrafficTargets(...)`
   -> `OgnTrafficOverlay.render(...)`
   -> `buildOgnTrafficOverlayFeatures(...)`
   -> `GeoJsonSource`
   -> MapLibre `SymbolLayer` icon + labels.
   Evidence: files above plus `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`.

7. Confirmed: ADS-B rendering path is:
   `AdsbTrafficRepositoryImpl.targets`
   -> `AdsbTrafficUseCase.targets`
   -> `MapScreenViewModel.adsbTargets`
   -> `MapTrafficUiBinding.adsbTargets`
   -> `MapTrafficOverlayEffects.updateAdsbTrafficTargets(...)`
   -> `MapOverlayManagerRuntimeTrafficDelegate.updateAdsbTrafficTargets(...)`
   -> `AdsbTrafficOverlay.render(...)`
   -> `AdsbDisplayMotionSmoother.snapshot(...)`
   -> `buildAdsbTrafficOverlayFeatures(...)`
   -> `GeoJsonSource`
   -> MapLibre `SymbolLayer` outline + icon + labels.
   Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`.

8. Confirmed: selection and hit-testing are separate from repository state. Taps are handled by `MapOverlayStack`, which asks the overlay manager to `findOgnTargetAt(...)`, `findOgnThermalHotspotAt(...)`, and `findAdsbTargetAt(...)`. Each overlay uses `map.queryRenderedFeatures(...)` against its own layers to recover the target identity from rendered features.
   Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt` (`onMapTap` block), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` (`findTargetAt`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt` (`findTargetAt`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` (`findTargetAt`), `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficSelectionRuntime.kt` (`createTrafficSelectionState`).

9. Confirmed: OGN target prominence is partly outside the main aircraft icon layer. The selected OGN target ring, target line, and ownship badge are separate overlays with their own sources / layers.
   Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetLineOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt`.

# How zoom currently affects rendering

- Confirmed: map zoom is forwarded to the traffic overlay runtime from `MapInitializer` when the map is first positioned and again on every camera-idle event.
  Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt` (`setupInitialPosition` calls `overlayManager.setOgnViewportZoom(...)` and `setAdsbViewportZoom(...)`; `setupListeners` camera-idle block repeats both calls).

- Confirmed: OGN zoom handling only changes viewport policy and rendered icon size. The policy has three low-zoom reduction bands and a close-zoom full-size band. Labels are only visible in the close-zoom band.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt` (`resolveOgnTrafficViewportDeclutterPolicy`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportSizing.kt` (`resolveOgnTrafficViewportSizing`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` (`setViewportZoom`, `applyViewportPolicyToStyle`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` (`applyOgnViewportPolicyToStyle`).

- Confirmed: ADS-B zoom handling changes icon scale, label strategy, and target cap. It also considers viewport range, not only zoom, when deciding whether all labels may be attempted.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt` (`resolveAdsbTrafficViewportDeclutterPolicy`, `shouldShowAllAdsbLabels`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` (`setViewportZoom`, `applyViewportPolicyToStyle`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` (`resolveAdsbViewportRangeMeters`, `applyAdsbViewportPolicyToStyle`).

- Confirmed: zoom changes trigger overlay policy changes and rerenders, but the current code does not perform any custom per-aircraft screen-space relayout as zoom changes.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` (`setAdsbViewportZoom` schedules a render when zoom changes and targets exist), `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` (`setViewportZoom` updates zoom and icon size only).

- Confirmed: current zoom behavior changes global icon size and label visibility, not aircraft separation. Nearby aircraft only look less crowded because icons get smaller or some labels disappear.
  Evidence: same files as above.

- Confirmed: current traffic relayout is zoom-aware, but not pan-aware. The overlay render path is triggered by target changes and zoom policy updates; there is no traffic-specific screen-space relayout on camera panning or rotation.
  Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt` (camera-idle only forwards zoom), `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`.

- Inference: if decluttering is implemented in screen space, pan / rotation invalidation will have to be added explicitly, because collisions depend on projected screen positions and the current traffic overlay triggers are not sufficient for that by themselves.

# Why overlap happens today

- Confirmed: both overlay types feed true geographic points directly into MapLibre, so aircraft that are geographically close at the current zoom project into nearly the same screen area.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` (`Point.fromLngLat(target.longitude, target.latitude)`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt` (`Point.fromLngLat(target.lon, target.lat)`).

- Confirmed: icon footprints are fixed-size screen assets with center anchoring, so even perfectly distinct coordinates can still overlap visually when projected too close together.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` (`iconAnchor("center")`, `iconSize(...)`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` (`iconAnchor("center")`, `iconSize(...)`), `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnIconSizing.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbIconSizing.kt`.

- Confirmed: MapLibre collision handling is intentionally disabled for the aircraft icons.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` and `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` use `iconAllowOverlap(true)` and `iconIgnorePlacement(true)`.

- Confirmed: OGN labels also ignore placement and can overlap whenever they are visible.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` (`createOgnTopLabelLayer`, `createOgnBottomLabelLayer` use `textAllowOverlap(true)` and `textIgnorePlacement(true)`).

- Confirmed: ADS-B labels do not separate icons. At close zoom, they may use MapLibre placement to avoid some label collisions, but the icons still overlap because icon collision remains disabled.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` (`adsbLabelTextExpression`, `adsbPriorityLabelsAllowOverlap`, icon layers still use overlap / ignore placement).

- Confirmed: there is no current screen-space offset cache, no slot assignment, no leader line for displaced traffic icons, and no cluster / count-bubble path in the traffic overlay code.
  Evidence: repo search in `feature/traffic` and `feature/map` found no traffic clustering code; overlay feature builders only write true coordinates and label/icon properties.

- Confirmed: the visible overlap problem is both geographic and screen-space. Geographic proximity is the input condition, but the unreadable pile-up is caused by fixed-size screen icons rendered with overlap forced on.
  Evidence: same files as above.

- Confirmed: OGN target ordering is deterministic before render because published targets are sorted by `displayLabel` and `canonicalKey`.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt` (`publishTargets`).

- Confirmed: ADS-B target selection / filtering is deterministic before it reaches the overlay because `AdsbTrafficStore.select(...)` sorts with `ADSB_DISPLAY_PRIORITY_COMPARATOR`.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt` (`select`), `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficSelectionOrdering.kt` (`ADSB_DISPLAY_PRIORITY_COMPARATOR`).

- Confirmed: ADS-B visual interpolation adds motion smoothing, but not overlap handling.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt`.

- Confirmed: there is no explicit per-feature symbol sort key in the traffic layers.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt` and `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` define icon / text properties but do not set a symbol sort key.

- Inference: if a future declutter algorithm depends on stable per-target ordering, it should not rely on implicit MapLibre draw order alone. It should use explicit stable keys from the data model (`canonicalKey`, `Icao24`) and its own deterministic ranking rules.

# Existing overlap or label handling

- Confirmed: OGN has a viewport policy, but it is not a collision solver. It does three things:
  - scales icons down at lower zoom,
  - hides labels below the close-zoom threshold,
  - limits rendering to `MAX_TARGETS`.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportSizing.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt` (`MAX_TARGETS`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` (`maxTargets` check).

- Confirmed: OGN also culls invalid coordinates and off-viewport targets at feature-build time.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` (`isValidOgnCoordinate`, `isOgnInVisibleBounds`).

- Confirmed: ADS-B has more display policy than OGN, but it is still not icon decluttering. It does four things:
  - scales icons down at lower zoom,
  - decides whether labels are attempted for all targets or only close targets,
  - caps the displayed target count by zoom / viewport policy,
  - sorts targets by emergency / distance / age / ID before capping.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficSelectionOrdering.kt`.

- Confirmed: ADS-B has visual-only motion smoothing. This helps position continuity between provider updates, but it does not resolve symbol collisions.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt`.

- Confirmed: ADS-B has sticky icon-type projection caching for icon classification stability, not spatial decluttering.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbStickyIconProjectionCache.kt`.

- Confirmed: the only built-in map-library collision behavior currently in use is conditional label placement for ADS-B when `showAllLabels` is true. That is label suppression, not icon separation.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt` (`textAllowOverlap(adsbPriorityLabelsAllowOverlap(...))`, `textIgnorePlacement(...)`).

- Confirmed: there is no existing traffic-side use of `iconOffset(...)` for decluttering. The only nearby precedent I found is the live-follow watch-aircraft overlay, which registers a fixed zero icon offset.
  Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt` (`iconOffset(arrayOf(0f, 0f))`).

# Best insertion point for decluttering

- Confirmed: the best ownership boundary is the traffic overlay render path in `feature:traffic`, not the repositories, not the ViewModel, and not Compose.
  Why this boundary is the best fit:
  - repositories own authoritative aircraft state and filtering policy,
  - the ViewModel owns screen state and selection state,
  - the overlay classes already own MapLibre types, layer properties, hit-testing, zoom policy, and render-time feature generation.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficOverlayRuntimeState.kt` (map-free runtime interfaces), `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`.

- Confirmed: the right logical step is "after projection data is available to the overlay, before `GeoJsonSource.setGeoJson(...)`". That means:
  - OGN: after `MapOverlayManagerRuntimeOgnDelegate.renderTargetsNow()` has the current targets and zoom context, before `buildOgnTrafficOverlayFeatures(...)` emits features.
  - ADS-B: after `AdsbDisplayMotionSmoother.snapshot(...)` has produced the current animated display frame, before `buildAdsbTrafficOverlayFeatures(...)` emits features.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` (`render` -> `renderOgnTrafficFrame`), `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` (`render` / `renderFrame`), `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`.

- Confirmed: "before projection" in repositories or use cases would be the wrong layer because the requested behavior is display-only screen-space separation. That would leak UI layout policy into authoritative traffic state.
  Evidence: repository / use-case files above plus `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` and `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficOverlayEffects.kt`.

- Confirmed: built-in MapLibre icon collision flags are not a good direct fit for the requested product behavior because the current stack already disables them and the desired behavior is preserving individual aircraft visibility, not simply hiding icons.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`.

- Inference: a future declutter implementation will also need a viewport invalidation hook from the map runtime, because screen-space collisions change on pan and rotation even when target data is unchanged. The current code only forwards traffic-specific policy updates on zoom and target changes.

# Recommended implementation approach

- Recommended low-risk approach:
  1. Keep authoritative aircraft coordinates unchanged in repositories and ViewModels.
  2. Inside the traffic overlay render path, project the currently rendered aircraft into screen space using the current map projection.
  3. Compute collision groups from icon bounds plus padding using the rendered icon size already known to the overlay.
  4. Assign deterministic display offsets in pixels using stable per-aircraft keys:
     - OGN: `canonicalKey` / `id`
     - ADS-B: `Icao24.raw`
  5. Apply those offsets only in the runtime overlay layer, not in repository data.
  6. Move labels with the icon or suppress them intentionally; do not let labels and icons drift independently by accident.
  7. Recompute the display layout on target updates and on viewport changes that affect screen projection.

- Confirmed fit with current ownership:
  - display-only logic belongs in runtime overlay owners,
  - overlay classes already own icon size, rotation, label placement, and hit-testing,
  - the data model already exposes stable IDs for deterministic slot assignment.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficModels.kt`.

- Recommended MapLibre mechanism:
  - Prefer per-feature display offsets consumed inside the traffic symbol layers if the Android binding supports the needed expression shape cleanly.
  - Nearby precedent: `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt` already uses `iconOffset(...)`, although only as a fixed zero value today.

- Why this is safer than built-in collision-only flags:
  - built-in icon collision will hide symbols rather than spread them,
  - the product request wants individual aircraft to remain visible where practical,
  - hit-testing already relies on rendered traffic layers, so keeping the solution inside those layers is the least disruptive path.

- Why this is safer than repository or ViewModel changes:
  - it avoids introducing duplicate state owners,
  - it avoids polluting domain models with screen-space concerns,
  - it preserves current traffic selection and details-sheet ownership.

- Inference: if per-feature `iconOffset` / `textOffset` expressions prove awkward in the MapLibre Android API, the next-best fallback is still an overlay-local layout step, but that would need careful viewport invalidation and would be riskier than property-driven offsets.

# Risks and gotchas

- Confirmed: OGN selected-target prominence is implemented by separate overlays that still render at the target's true coordinate.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetLineOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt`.
  Risk: if the main OGN aircraft icon is visually offset later, the ring / line / badge will no longer line up unless they are explicitly coordinated.

- Confirmed: hit-testing uses `queryRenderedFeatures(...)` against the actual map layers.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`.
  Risk: any declutter approach that draws aircraft outside those layers, or offsets visuals without updating the queried render layers, will break tap selection correctness.

- Confirmed: ADS-B positions are already visually smoothed over time.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt`.
  Risk: adding another independent smoothing / hysteresis stage for declutter offsets can produce wobble unless the two animation paths are coordinated.

- Confirmed: current traffic relayout is not pan-aware.
  Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`.
  Risk: a screen-space declutter step needs explicit camera / viewport invalidation or it will use stale layout after the user pans the map.

- Confirmed: there is no explicit per-feature sort key inside the traffic symbol layers.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`, `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`.
  Risk: if visual priority matters for crowded groups, the implementation should not assume the current layer ordering is enough.

- Confirmed: ADS-B and OGN do not share the same visual feature stack.
  Evidence: OGN has target ring / line / badge overlays; ADS-B has icon outline / emergency flash and metadata-driven label policy.
  Risk: one shared algorithm may still need two thin integration adapters.

- Confirmed: OGN can carry up to `MAX_TARGETS = 500` in the overlay path.
  Evidence: `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt`.
  Risk: an O(n^2) screen-space collision solver could become expensive in dense OGN scenarios unless it uses a spatial grid / bucket or operates on the visible subset only.

- Confirmed: the codebase already contains a comment saying "OGN layers render above ADS-B layers" in tap handling, but the runtime front-order helper currently calls `bringOgnOverlaysToFront()` and then `adsbTrafficOverlay?.bringToFront()`.
  Evidence: `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt` (`onMapTap` comment), `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` (`bringTrafficOverlaysToFront`).
  Risk: there may already be a code-comment drift or layering ambiguity that needs to be resolved before relying on current z-order assumptions.

# Questions or unknowns

- Unknown: should Pass 2 declutter both OGN and ADS-B overlays, or is the intended scope only one of them?
  Evidence: both stacks render aircraft icons today and both can overlap.

- Unknown: when an OGN target is selected, should the selected aircraft stay at the true projected position, should the target ring / line follow the displaced icon, or should the icon move with a leader / tether back to truth?
  Evidence: selected-target visuals are separate overlays in `OgnTargetRingOverlay`, `OgnTargetLineOverlay`, and `OgnOwnshipTargetBadgeOverlay`.

- Unknown: should emergency / selected ADS-B targets get a higher declutter priority or smaller offsets than neutral targets?
  Evidence: current ADS-B data already distinguishes emergency proximity tiers in `AdsbTrafficUiModel` and `ADSB_DISPLAY_PRIORITY_COMPARATOR`.

- Unknown: is the current tap-order comment in `MapOverlayStack` stale, or is there another front-order rule outside the files reviewed that keeps OGN above ADS-B in practice?
  Evidence: comment and runtime front-order code currently disagree.

- Unknown: can the current MapLibre Android API binding express per-feature `iconOffset` / `textOffset` arrays cleanly from feature properties, or would that require a small overlay-style refactor?
  Evidence: nearby precedent only shows a fixed `iconOffset(arrayOf(0f, 0f))` in `LiveFollowWatchAircraftOverlay`; the traffic overlays do not currently use per-feature icon offset expressions.

- Unknown: should labels follow the decluttered icon position fully, partially, or not at all when labels are visible?
  Evidence: current traffic labels are derived from the same feature and offset relative to the icon with fixed `textOffset(...)` rules, but there is no existing screen-space displacement logic.
