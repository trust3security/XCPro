# XCPro Phase 1 implementation brief

## Title

Phase 1 - re-based plan for OGN label gating and Live Follow watch-aircraft zoom scaling

## Status against current `main`

This brief is intentionally re-based against the current codebase.

The original phase-1 write-up is partially stale. The following already exist on
`main` and should not be re-implemented:

- OGN viewport zoom plumbing from `MapInitializer` into `MapOverlayManagerRuntime`
- `MapOverlayManagerRuntimeOgnDelegate` cached zoom ownership
- OGN rendered icon-size scaling from base size plus viewport context
- startup application of cached OGN zoom before overlay creation
- OGN target-ring sizing derived from the same effective rendered icon size

Primary files proving that current baseline:

- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficViewportSizing.kt`

## Outcome

Ship the actual remaining phase-1 win without rewriting the traffic runtime:

- OGN icon scaling continues to use the existing base-size-plus-viewport path.
- OGN top/bottom traffic labels become a viewport-policy decision, not a side
  effect of rendered icon size or user preference size.
- OGN label visibility updates immediately on zoom-policy changes through style
  layer visibility, instead of waiting for the next traffic source rebuild.
- Live Follow watched-aircraft icon scales with zoom through its actual owner
  path in the map UI runtime layer.
- Existing OGN zoom plumbing, target-ring sizing, and startup zoom caching are
  preserved.

## Why this phase exists

The codebase already moved further than the original brief assumed:

- OGN viewport zoom is already pushed from `MapInitializer`.
- OGN effective icon size is already derived in `MapOverlayManagerRuntimeOgnDelegate`.
- OGN still has one major phase-1 gap:
  normal top/bottom label behavior is still coupled to rendered icon size and
  GeoJson feature content, so zoom-only changes do not immediately update labels.
- Live Follow watch-aircraft overlay still uses a fixed symbol scale and is not
  wired to map zoom.

So the real phase-1 work is now:

1. make OGN label behavior explicitly viewport-driven and immediate
2. keep the existing OGN sizing owner instead of duplicating it
3. add watched-aircraft zoom scaling in the UI runtime seam that already owns it

## In scope

1. Keep the existing OGN viewport zoom and rendered-size owner path.
2. Rework OGN phase-1 viewport policy so label visibility is independent of the
   user icon-size preference.
3. Apply OGN label gating through style layer visibility for the top and bottom
   traffic label layers.
4. Preserve cluster-count rendering in phase 1 unless QA shows it is still too
   noisy at wide zoom.
5. Keep target-ring sizing aligned with the effective OGN rendered icon size.
6. Add zoom-aware scaling to the Live Follow watched-aircraft overlay.
7. Feed current map zoom into `MapLiveFollowRuntimeLayer` from the existing
   `currentZoom` hot-path flow.
8. Rebase unit tests so they lock the new ownership and behavior instead of the
   stale behavior now encoded in tests.

## Out of scope

Do not do these in phase 1:

- shared OGN + ADS-B declutter policy extraction
- priority-vs-normal traffic layer split
- target ranking changes before truncation
- dynamic OGN target caps
- clustering / spiderfy / expand-on-tap redesign
- changing OGN icon overlap flags for normal traffic
- density-aware OGN label rules beyond the simple phase-1 gate
- any ViewModel, repository, or persistence ownership change

Those remain phase-2 or later work.

## Reference patterns reviewed

Primary reference paths:

- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlaySupport.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt`
- `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt`

Pattern to reuse:

- traffic runtime delegate owns cached viewport inputs and forwards render-only
  state into overlay handles
- overlay classes own MapLibre layer/source/style mutation only
- Compose runtime layer owns Live Follow watched-aircraft rendering and should
  receive zoom through the existing `currentZoom` UI runtime flow

Intentional deviation from ADS-B:

- do not move OGN viewport-policy ownership fully into `OgnTrafficOverlay`
  because `MapOverlayManagerRuntimeOgnDelegate` already owns the effective icon
  size that must stay shared with `OgnTargetRingOverlay`

## Critical seam findings from the code pass

1. OGN zoom plumbing already exists.
   Re-adding `setOgnViewportZoom(...)` work would be duplicate churn.

2. Current OGN label gating is coupled to rendered icon size and happens during
   feature building.
   That means user base size can affect label visibility, and zoom-only changes
   do not update labels until the next OGN render.

3. Current OGN screen declutter/clustering also depends on rendered icon size.
   Phase 1 should keep that behavior as-is unless QA shows regroup lag is too
   visible after zoom changes.

4. Live Follow watched-aircraft overlay is not owned by
   `MapOverlayManagerRuntime`.
   It is created in `MapLiveFollowRuntimeLayer`, so zoom must be fed there.

5. Existing tests lock stale behavior:
   - `OgnTrafficLabelDeclutterPolicyTest`
   - `OgnTrafficOverlayFeatureLabelDeclutterTest`
   - `LiveFollowWatchAircraftOverlayTest`

## Implementation decisions

### 1) Keep the existing OGN viewport sizing owner

Do not add a new parallel `OgnTrafficViewportDeclutterPolicy.kt` as the phase-1
owner.

Use the existing `OgnTrafficViewportSizing.kt` seam as the canonical phase-1
owner and evolve it so one owner resolves:

- rendered icon size
- icon scale multiplier
- top/bottom traffic labels visible or hidden

The owner stays in `feature:traffic`.

### 2) Keep `MapOverlayManagerRuntimeOgnDelegate` as the effective viewport-policy owner

`MapOverlayManagerRuntimeOgnDelegate` already owns:

- base OGN icon size preference
- cached viewport zoom
- effective rendered OGN icon size used by both traffic and target-ring paths

Keep that ownership.

Recommended addition:

- resolve one OGN phase-1 viewport policy object in the delegate
- forward that policy to the OGN traffic overlay
- continue forwarding only rendered icon size to the target-ring overlay

This avoids duplicating viewport-policy math in multiple runtime owners.

### 3) Separate OGN label visibility from user icon size

The user icon-size preference remains the base input for icon rendering only.

Top/bottom label visibility must not be decided from the rendered icon-size
thresholds alone, because that couples label behavior to the user preference.

Phase-1 rule:

- close zoom: top and bottom traffic label layers visible
- below close zoom threshold: top and bottom traffic label layers hidden

Cluster-count rendering stays visible in phase 1 unless QA rejects it.

### 4) Apply OGN label gating through style layer visibility

Do not blank label text in GeoJson as the primary phase-1 mechanism.

Use style `visibility("visible" | "none")` for:

- `TOP_LABEL_LAYER_ID`
- `BOTTOM_LABEL_LAYER_ID`

Why:

- immediate zoom response without waiting for next target refresh
- lower churn than regenerating source data just to hide/show labels
- simpler startup behavior with less label flash risk

### 5) Preserve current OGN icon scaling unless QA requires retuning

The current OGN size path already exists and is covered by tests.

Phase 1 should not spend scope retuning OGN icon bands unless QA rejects the
current curve.

If a later tuning pass is needed, do it as a separate change from the label
visibility ownership fix.

### 6) Watch-aircraft zoom scaling is UI-runtime-owned

`LiveFollowWatchAircraftOverlay` is a render-only runtime object created by
`MapLiveFollowRuntimeLayer`.

So the phase-1 seam is:

`MapScreenContentRuntime.currentZoom`
-> `MapLiveFollowRuntimeLayer(currentZoom = ...)`
-> `LiveFollowWatchAircraftOverlay.setViewportZoom(...)`

Do not route this through `MapOverlayManagerRuntime`.

### 7) Cache watch-aircraft zoom inside the overlay

`LiveFollowWatchAircraftOverlay` should cache:

- current viewport zoom
- current resolved icon scale

That cached zoom must be applied:

- when zoom changes
- when the symbol layer is first created
- after style recreation

This preserves correct first-frame behavior at wide or close startup zoom.

## Ownership and boundaries

### SSOT / runtime ownership

- OGN base icon size preference
  - authoritative owner: existing traffic settings/repository path
  - read path here: `MapOverlayManagerRuntimeOgnDelegate.setIconSizePx(...)`
  - forbidden duplicate: new overlay-owned preference state

- OGN cached viewport zoom
  - authoritative owner: `MapOverlayManagerRuntimeOgnDelegate`
  - exposed as: delegate-local runtime state only
  - forbidden duplicate: second zoom owner inside OGN traffic overlay

- OGN effective phase-1 viewport policy
  - authoritative owner: `MapOverlayManagerRuntimeOgnDelegate`
  - exposed as: render-only policy forwarded to `OgnTrafficOverlay`
  - forbidden duplicate: separate label-policy owner in `OgnTrafficOverlaySupport`

- OGN top/bottom label visibility
  - authoritative owner: OGN phase-1 viewport policy
  - applied by: `OgnTrafficOverlay` / `OgnTrafficOverlaySupport`
  - forbidden duplicate: GeoJson-only label hiding path as the active phase-1 authority

- watched-aircraft viewport zoom
  - authoritative owner: `MapLiveFollowRuntimeLayer` input from existing map zoom flow
  - applied by: `LiveFollowWatchAircraftOverlay`
  - forbidden duplicate: ViewModel or repository mirror

### Dependency direction

Remains:

`UI runtime -> traffic/map runtime render objects`

No new repository, ViewModel, or domain bypasses are introduced.

### Boundary adapters

No new persistence, sensor, network, file-I/O, or device-boundary adapter is
required for this phase.

## File plan

- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficViewportSizing.kt`
  - existing
  - owns the canonical phase-1 OGN viewport policy
  - why here: existing OGN size policy already lives here; avoid duplicate policy owners

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficOverlayRuntimeState.kt`
  - existing
  - owns the OGN traffic overlay handle contract update needed to forward the viewport policy
  - why here: this is the cross-runtime seam already used by the delegate

- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
  - existing
  - owns cached OGN viewport inputs and forwarding of resolved render-only policy
  - why here: delegate already owns effective rendered icon size shared with target ring

- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - existing
  - owns live OGN style updates and any immediate style-only response to viewport-policy changes
  - why here: overlay owns MapLibre source/layer state

- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt`
  - existing
  - owns label-layer visibility and style property helpers
  - why here: all OGN symbol-layer construction and property updates already live here

- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicy.kt`
  - existing
  - remove or retire from the top/bottom label path
  - why here: keeping it active would leave duplicate label-policy ownership

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - existing
  - owns forwarding current map zoom into the live-follow runtime layer
  - why here: `currentZoom` is already collected here

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt`
  - existing
  - owns feeding `currentZoom` into the watched-aircraft overlay
  - why here: this composable already owns the overlay lifecycle

- `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
  - existing
  - owns watched-aircraft scale resolution and style application
  - why here: render-only MapLibre layer owner for this overlay

- tests to update
  - `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficViewportSizingTest.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicyTest.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficOverlayFeatureLabelDeclutterTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlayTest.kt`

## Time base declaration

Phase-1 changes are render-only runtime behavior.

Time-dependent values touched by this phase:

- none newly introduced in domain or fusion logic

Existing time use that remains unchanged:

- OGN stale/live alpha rendering continues to use existing monotonic runtime
  behavior inside the overlay render path

Forbidden:

- no wall-time use in domain/fusion
- no replay-time mixing with runtime zoom decisions

## Replay determinism

- Deterministic for same input: yes
- Randomness used: none
- Replay/live divergence rule: none added

Reasoning:

- OGN and Live Follow changes remain render-only map runtime behavior
- zoom input comes from existing map UI runtime state
- no network, persistence, or random source is added

## Delivery order

1. Re-base the OGN phase-1 policy owner in `OgnTrafficViewportSizing.kt`.
2. Update the OGN overlay handle seam so the delegate can forward the resolved
   viewport policy.
3. Apply immediate OGN label visibility through style layer visibility in
   `OgnTrafficOverlay` / `OgnTrafficOverlaySupport`.
4. Retire the old top/bottom label declutter path and update affected tests.
5. Thread `currentZoom` into `MapLiveFollowRuntimeLayer`.
6. Add cached zoom-aware watch-aircraft scaling in `LiveFollowWatchAircraftOverlay`.
7. Run unit tests and required repo gates.
8. Capture map evidence for the impacted SLOs.

## Acceptance criteria

### Functional

- OGN icons continue to scale from the existing base-size-plus-viewport path.
- OGN top/bottom labels are hidden below the chosen zoom threshold regardless
  of user base icon size.
- OGN top/bottom label visibility updates immediately when zoom policy changes.
- Live Follow watched aircraft visibly shrinks at wider zoom and returns to its
  larger close-zoom scale.
- No crash on style recreation or when zoom arrives before overlays are created.

### UX

- At wide zoom, OGN normal labels no longer linger because the user picked a
  larger icon-size preference.
- At close zoom, OGN labels return immediately and consistently.
- Watched aircraft remains easy to spot without dominating wide-zoom views.

### Technical

- no new SSOT owner
- no ViewModel or repository changes
- no new timebase risk
- no duplicate OGN viewport-policy owner

## QA checklist

1. Dense OGN traffic, zoom from close to wide and back.
   - confirm labels hide/show immediately on zoom-policy changes
   - confirm label behavior is the same at different user icon-size settings

2. Fresh app launch at a wide zoom.
   - confirm OGN starts with wide-zoom label state
   - confirm watched aircraft starts at the reduced wide-zoom scale

3. Fresh app launch at a close zoom.
   - confirm OGN labels are visible immediately
   - confirm watched aircraft starts at the close-zoom scale

4. Change OGN icon size in settings.
   - confirm icon scale still follows the new base size
   - confirm label visibility does not drift with the setting change

5. Enable watched aircraft overlay and zoom across bands.
   - confirm scale changes on zoom updates
   - confirm style recreation does not lose the cached scale

6. OGN tap selection at close zoom.
   - confirm selection still works

7. Satellite / contrast icon mode if available.
   - confirm icon scaling still applies correctly

## Verification

Required repo gates:

- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Map/runtime evidence obligations:

- declare impacted SLOs before coding
- minimum expected set for this phase:
  - `MS-UX-01`
  - `MS-UX-03`
  - `MS-UX-04`
  - `MS-UX-06`
  - `MS-ENG-01`
  - `MS-ENG-02`

Run connected tests when runtime/device behavior needs confirmation.

## Docs and governance

- Update this phase brief when implementation lands if any seam/name changes.
- `PIPELINE.md` does not need an update if ownership stays within the existing
  traffic runtime and live-follow runtime paths.
- No ADR is expected if this stays a narrow runtime implementation change.
- Add a `KNOWN_DEVIATIONS.md` entry only if SLO evidence misses mandatory
  thresholds or an explicit temporary rule exception is introduced.

## Recommended next step

Implement the re-based phase-1 scope from this brief instead of the stale
original version:

1. OGN immediate zoom-policy label gating with one canonical owner
2. watched-aircraft zoom scaling through the UI runtime seam
