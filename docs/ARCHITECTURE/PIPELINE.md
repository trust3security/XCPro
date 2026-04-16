### ViewModel Dependency Rule (Corrected)

- Profile/session-scoped owners must not be grouped into dependency bags when injected into ViewModels.
- ViewModels must depend on individual, focused domain-facing seams instead of aggregated *Dependencies-style containers.
- Any compatibility grouping must be treated as a temporary shim and must not be normalized as a stable pattern in pipeline documentation.

### Map Runtime Binding Rule

- The suppressed-GPS replay-location path is owned by `MapLocationFlightDataRuntimeBinder`.
- `MapScreenRootEffects` may launch that binder seam, but `MapComposeEffects` must not directly collect flight data or call `MapLocationRuntimePort.updateLocationFromReplayFrame(...)` for that path.
- Trigger cadence is preserved: only flight-data emissions drive suppressed-GPS replay-location forwarding; gate and orientation changes are read lazily on each emission and must not independently trigger location updates.
