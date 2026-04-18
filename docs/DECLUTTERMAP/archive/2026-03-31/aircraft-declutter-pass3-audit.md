> Archived on 2026-03-31.
> Superseded by [xcpro-close-proximity-declutter-brief.md](../../xcpro-close-proximity-declutter-brief.md) and [xcpro-phase-2a-close-proximity-declutter-plan.md](../../xcpro-phase-2a-close-proximity-declutter-plan.md).
> This audit captured the pre-Phase 2A direction and is retained for history only.

# Aircraft Declutter Pass 3 Audit

## Summary Verdict

Verdict: **approve for manual device QA, with follow-up concerns**.

The current Path B patch is internally coherent:

- true aircraft coordinates stay authoritative outside the runtime display path,
- both overlays project true positions, compute screen-space declutter, unproject temporary display positions, and emit displaced GeoJSON,
- labels and taps follow the displaced feature geometry rather than a second coordinate source,
- selected OGN traffic stays pinned at truth so the ring/line/badge overlays remain aligned.

I did **not** apply production-code fixes in this pass. I did not find a fail-level defect that met the brief's "tiny, clearly-correct, low-risk" bar. The remaining issues are follow-up risks rather than safe one-line fixes.

## Real Data Flow

### OGN

1. True coordinates in
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
   - `latestOgnTargets` remains the runtime cache of true `OgnTrafficTarget` data.
2. Projection step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
   - `render(...)` builds `TrafficProjectionSeed` values from true target lat/lon in `buildDeclutterSeeds(...)`.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`
   - `resolveTrafficDeclutteredDisplayCoordinates(...)` projects those seeds with `map.projection.toScreenLocation(...)`.
3. Declutter step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterEngine.kt`
   - `TrafficScreenDeclutterEngine.layout(...)` computes deterministic offsets per crowded screen-space group.
4. Unprojection step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`
   - displaced screen points are converted back with `map.projection.fromScreenLocation(...)`.
5. GeoJSON emission
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
   - `render(...)` passes `displayCoordinatesByKey` into `renderOgnTrafficFrame(...)`.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
   - `buildOgnTrafficOverlayFeatures(...)` writes display coordinates into the emitted `Feature` geometry.
6. Hit-testing path
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
   - `findTargetAt(...)` checks selected-ring hit-testing first, then traffic-layer hit-testing.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
   - `findTargetAt(...)` calls `map.queryRenderedFeatures(...)` on the OGN icon and label layers backed by the displaced GeoJSON source.

Selected-target alignment path:

- `MapOverlayManagerRuntimeOgnDelegate.updateTargetVisuals(...)` updates `latestPinnedTargetKey`.
- `OgnTrafficOverlay.setPinnedTargetKey(...)` marks the selected target as `pinAtOrigin`.
- `TrafficScreenDeclutterEngine.layout(...)` keeps that target at zero offset.
- `OgnTargetRingOverlay`, `OgnTargetLineOverlay`, and `OgnOwnshipTargetBadgeOverlay` still consume true target coordinates, so they remain aligned with the pinned selected aircraft.

### ADS-B

1. True coordinates in
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
   - `latestAdsbTargets` remains the runtime cache of true `AdsbTrafficUiModel` data.
2. Smoothing step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`
   - `render(...)` feeds true target updates into `AdsbDisplayMotionSmoother`.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt`
   - `snapshot(...)` returns the current smoothed frame.
3. Projection step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`
   - `renderFrame(...)` builds `TrafficProjectionSeed` values from the smoothed frame.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`
   - `resolveTrafficDeclutteredDisplayCoordinates(...)` projects those positions with `map.projection.toScreenLocation(...)`.
4. Declutter step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterEngine.kt`
   - `TrafficScreenDeclutterEngine.layout(...)` computes per-aircraft display offsets.
5. Unprojection step
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`
   - displaced screen points are converted back with `map.projection.fromScreenLocation(...)`.
6. GeoJSON emission
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`
   - `renderFrame(...)` passes `displayCoordinatesByKey` into `renderAdsbTrafficFrame(...)`.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`
   - `buildAdsbTrafficOverlayFeatures(...)` writes those display coordinates via `AdsbGeoJsonMapper.toFeatureInternal(...)`.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
   - the emitted `Feature` geometry uses the temporary display coordinate when present.
7. Hit-testing path
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
   - `findAdsbTargetAt(...)` forwards to the runtime ADS-B overlay.
   - `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`
   - `findTargetAt(...)` calls `map.queryRenderedFeatures(...)` on the rendered ADS-B icon and label layers backed by the displaced GeoJSON source.

## Findings Table

| Criterion | Status | Notes |
| --- | --- | --- |
| nearby aircraft stop fully piling on top of each other | PASS | Both overlays now emit displaced runtime feature geometry instead of leaving all aircraft at the same projected point. |
| aircraft remain individually selectable | PASS | Tap queries hit the same rendered layers that use the displaced GeoJSON geometry. |
| true domain coordinates remain untouched outside runtime display path | PASS | Display coordinates are created inside runtime overlay code only; repositories/ViewModels remain unchanged. |
| map-screen state ownership remains unchanged | PASS | New state stays inside `feature:traffic` overlay/runtime objects; `MapScreenState` still owns opaque overlay handles only. |
| selected OGN target still lines up with ring/line/badge overlays | PASS | Selected OGN traffic is explicitly pinned at origin before declutter; target visuals still use true target coordinates. |
| ADS-B smoothing still works without a second fighting animation path | CONCERN | Smoothing runs before declutter, which is the right order, but both the smoother and declutter engine keep prior visual state. This needs device QA for wobble. |
| layout is deterministic and not obviously jittery | CONCERN | Engine tie-breaks are stable, but overlay seeds still derive `priorityRank` from list order. The audit did not prove upstream list ordering is always stable. |
| offsets reduce or disappear as zoom increases or collisions resolve | CONCERN | Zoom-strength policy is correct, but viewport zoom is only forwarded on initial position and camera idle, not on every camera-move tick. Final settled state is correct; live zoom transition behavior still needs QA. |
| patch does not silently change filtering, clustering, or z-order policy | PASS | The patch changes geometry placement only. Existing per-overlay filtering caps and current overlay front-order behavior remain intact. |

## Hard-Bug Audit Notes

| Failure Mode | Status | Notes |
| --- | --- | --- |
| offset computed from stale camera state | CONCERN | Screen projection is recomputed on rerender from the live map projection, but zoom-dependent policy values are refreshed only on initial placement and camera idle. |
| pan/rotate not invalidating when needed | PASS | `MapInitializer` camera-move invalidates projection, and camera-idle forces a final immediate refresh. |
| labels attached to true point while icon uses display point | PASS | Labels and icon share one displaced feature geometry in both overlays. |
| hit-testing reads different geometry than the displaced geometry | PASS | Both `findTargetAt(...)` paths query rendered symbol layers backed by the displaced source data. |
| selected OGN target accidentally offset in some paths but not others | PASS | Pinned-key propagation is explicit in delegate init, lazy render, and selection updates. |
| ADS-B smoother and declutter both keep prior-state caches | CONCERN | True, but currently still contained to runtime display code. This is a QA risk rather than a clear code bug from inspection alone. |
| deterministic ordering depends on unstable iteration order | CONCERN | Engine key tie-breaks exist, but `priorityRank` is still input-order driven. |
| viewport throttling causes lag or misses final refresh | CONCERN | Active camera movement is throttled to `120 ms`; camera-idle forces a final refresh. No device evidence was captured in this pass. |
| tests only prove engine math, not runtime seams | CONCERN | Runtime delegate/init seams are covered, but there is still no device-level rendered hit-test proof or map SLO evidence for this patch. |

## Exact Files Inspected

- `docs/aircraft-declutter-implementation.md`
- `docs/DECLUTTERMAP/CODEX_AIRCRAFT_DECLUTTER_PASS3_AUDIT.md`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterEngine.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlaySupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/AdsbDisplayMotionSmoother.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayInteractionCadencePolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnHelpers.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficHelpers.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficOverlayRuntimeState.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeBaseOpsDelegate.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/traffic/src/test/java/com/trust3/xcpro/map/TrafficScreenDeclutterEngineTest.kt`
- `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt`
- `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateViewportZoomTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapInitializerOgnViewportZoomTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt`

## Small Fixes Applied

No production-code fixes were applied in this audit pass.

Reason:

- no fail-level bug was proven from local inspection,
- the remaining issues need measured behavior validation or a broader policy decision,
- forcing a speculative fix here would have exceeded the "tiny, clearly-correct" bar.

## Remaining Risks For Manual QA

1. Zoom-driven declutter fade and icon-size policy may lag during live zoom movement because zoom is forwarded on initial placement and camera idle, not every camera-move tick.
2. ADS-B dense groups may show mild wobble because motion smoothing and declutter offset reuse both keep local visual history.
3. OGN and ADS-B still declutter independently, so cross-overlay overlap and current overlay front-order behavior remain unresolved.

## Recommended Next Pass

If manual QA finds visible problems, the next pass should stay narrow:

1. Add measured device QA and map SLO evidence for crowded pan/rotate/zoom behavior.
2. If zoom-transition lag is visible, add an explicit live zoom-update path that preserves the current throttle guarantees instead of forcing immediate rerenders on every camera callback.
3. If dense ADS-B groups wobble, add a dedicated policy for how smoother state and declutter-state reuse should cooperate, with integration tests around crowded moving groups.

## Manual QA Checklist

1. Two aircraft nearly coincident at low zoom.
2. Three to five aircraft in one tight group.
3. Same group while zooming in and out repeatedly.
4. Heading-up / rotation changes without target updates.
5. Tap each displaced icon in a crowded group.
6. Selected OGN target in a crowded group.
7. ADS-B emergency or priority target in a crowded group.
8. OGN and ADS-B targets near each other, since cross-overlay collision is still out of scope.
