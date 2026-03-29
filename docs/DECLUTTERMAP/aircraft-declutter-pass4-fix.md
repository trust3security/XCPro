# Aircraft Declutter Pass 4 Fix

## Summary

This pass fixes one specific issue only:

- during live zoom movement, declutter could use the current camera projection with a stale cached viewport zoom,
- that made zoom-driven declutter strength and zoom-sized collision policy lag until camera idle,
- visually, aircraft spacing could feel late or snap back only after the gesture settled.

Scope stayed narrow:

- no clustering,
- no cross-overlay OGN/ADS-B collision pass,
- no smoother/declutter redesign,
- no new map-state owner.

## Chosen Issue

Chosen device-QA issue:

- zooming in or out repeatedly could show delayed declutter fade/return-to-truth behavior during the active gesture.

This matches the Pass 3 concern that projection invalidation already ran on camera move, but viewport zoom was only refreshed on initial position and camera idle.

## Root Cause

Root cause was split runtime state inside the existing Path B implementation:

1. `MapInitializer` camera-move callbacks already called `invalidateTrafficProjection(...)`.
2. That projection invalidation recomputed display coordinates from the live map projection.
3. But the delegates' cached viewport zoom was not refreshed on that same path.
4. OGN and ADS-B therefore rerendered with:
   - fresh projected screen positions,
   - stale zoom-derived declutter policy.
5. The correct zoom value arrived only later through the initial-position/camera-idle zoom feed.

No repository or ViewModel state was wrong. This was a runtime delegate coherence issue inside the display-only map slice.

## Smallest Patch

The fix stays inside the existing runtime delegate ownership:

- `MapOverlayManagerRuntimeOgnDelegate.invalidateProjection(...)`
  - now samples the live map zoom before scheduling the projection rerender.
- `MapOverlayManagerRuntimeTrafficDelegate.invalidateProjection(...)`
  - now samples the live map zoom before scheduling the projection rerender.
  - unlike the public `setAdsbViewportZoom(...)` path, this sync updates cached zoom and overlay policy without forcing an extra immediate rerender.

Why this is the smallest safe fix:

- map shell wiring did not change,
- no new public API was added,
- no display state moved into `feature:map` or ViewModel state,
- the fix is limited to the existing runtime owner that already caches viewport zoom.

## Changed Files

| File | Owner / Responsibility | Change |
| --- | --- | --- |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | OGN runtime delegate; owns display-only OGN viewport zoom and projection invalidation | sync live camera zoom inside projection invalidation before rerender |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` | ADS-B runtime delegate; owns display-only ADS-B viewport zoom and projection invalidation | sync live camera zoom inside projection invalidation before rerender, without adding an extra forced render path |
| `feature/traffic/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` | OGN runtime delegate regression coverage | add test locking live-zoom sync on projection invalidation |
| `feature/traffic/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateViewportZoomTest.kt` | ADS-B runtime delegate regression coverage | add test locking live-zoom sync on projection invalidation |
| `docs/aircraft-declutter-pass4-fix.md` | change record for this narrow pass | document root cause, narrow fix, verification, and residual risk |

## Ownership and State Contract

- Authoritative aircraft coordinates: unchanged, still outside the runtime display path.
- Display-only viewport zoom cache: unchanged owner, still the traffic runtime delegates.
- Forbidden duplicate: no new ViewModel, repository, or Compose-owned zoom/declutter state was added.

Time base:

- unchanged,
- render cadence remains monotonic and delegate-local,
- this pass does not add wall-time or replay-time behavior.

## Verification

Focused proof run:

- `.\gradlew :feature:traffic:testDebugUnitTest --tests "com.example.xcpro.map.MapOverlayManagerRuntimeOgnDelegateViewportZoomTest" --tests "com.example.xcpro.map.MapOverlayManagerRuntimeTrafficDelegateViewportZoomTest"` -> PASS
- `.\gradlew enforceRules` -> PASS (`ARCH GATE PASSED`)

Deferred:

- root `testDebugUnitTest`
- `assembleDebug`
- connected/device instrumentation
- MapScreen SLO evidence capture

Those were deferred because this pass intentionally fixes one narrow runtime seam and the focused delegate tests plus `enforceRules` are the smallest sufficient local proof.

## Residual Risk

Still out of scope after this pass:

1. Projection rerenders during active gestures are still throttled to the existing cadence window, so some lag can still be visible under fast camera movement.
2. OGN and ADS-B still declutter independently and can overlap each other.
3. ADS-B motion smoothing and declutter offset reuse still both keep local visual history; this pass does not retune wobble behavior.

## Manual QA Focus After This Fix

1. Zoom in and out on a crowded OGN group and confirm spacing changes during the gesture, not only on camera idle.
2. Repeat on a crowded ADS-B group and confirm the same.
3. Verify no obvious extra rerender burst or flicker was introduced during pan/rotate without zoom.
