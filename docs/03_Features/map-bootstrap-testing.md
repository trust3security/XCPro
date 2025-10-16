Map Bootstrap Verification
==========================

Use this checklist after touching the map initialization or overlay-loading flow.

- Launch the debug app on a device/emulator with saved waypoints; confirm the MapLibre view appears within one second and no duplicate placement/toast is logged.
- Rotate the device; ensure the existing `MapView` is reused, overlays stay visible, and there is no second "Map initialization completed" log line.
- Toggle a SkySight or airspace layer and verify overlays render without visible jank (cache hit) and that the logcat shows the cached waypoint load path (no file IO repeat).
- Pan/zoom repeatedly; watcher logs should *not* mention re-reading waypoint files, and the coroutine scope should stay at `Main`.
- From the task drawer, switch task types and ensure task overlays clear/reload without re-triggering map bootstrap logs.
