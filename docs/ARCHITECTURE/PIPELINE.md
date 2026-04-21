### ViewModel Dependency Rule (Corrected)

- Profile/session-scoped owners must not be grouped into dependency bags when injected into ViewModels.
- ViewModels must depend on individual, focused domain-facing seams instead of aggregated *Dependencies-style containers.
- Any compatibility grouping must be treated as a temporary shim and must not be normalized as a stable pattern in pipeline documentation.

### Map Runtime Binding Rule

- The suppressed-GPS replay-location path is owned by `MapLocationFlightDataRuntimeBinder`.
- `MapScreenRootEffects` may launch that binder seam, but `MapComposeEffects` must not directly collect flight data or call `MapLocationRuntimePort.updateLocationFromReplayFrame(...)` for that path.
- Trigger cadence is preserved: only flight-data emissions drive suppressed-GPS replay-location forwarding; gate and orientation changes are read lazily on each emission and must not independently trigger location updates.

### Live Source Selection Rule

- Simulator owns Condor bridge/session state and Condor transport read models only.
- `feature:profile` owns persisted desired live mode.
- `feature:flight-runtime` reads desired live mode through its declared port and owns selected live source, effective live status, startup requirements, selected live sensor source, selected live airspeed source, and selected live external instrument source.
- `feature:flight-runtime` may expose refresh or invalidation seams for platform capability inputs, but it remains the only owner of selection and startup policy.
- `app` owns live actuation adapters only; it must start or stop the resolver-selected backend without adding policy.
- `app` may trigger runtime live-source refresh or read-model invalidation on resume or before actuation, but it must not interpret or replace runtime selection policy.
- `feature:map` may consume resolver-selected live status and local phone diagnostics, but it must not select phone vs Condor or implement Condor fallback policy.
- `feature:simulator` owns Condor sentence parsing and simulator runtime publication. GPS and TAS stay on the selected live sensor / airspeed seams, while Condor pressure altitude and TE vario cross the runtime boundary through the selected external instrument seam.
- Direct Condor wind enters the wind pipeline only through `ExternalWindWritePort`; wind selection policy remains owned by the wind domain/runtime path.
- `FlightDataRepository.Source` remains `LIVE` or `REPLAY`; phone vs Condor selection is internal to the live-runtime seam.
- Downstream simulator-aware side effects stay keyed off `LiveSourceKind`: IGC recording, WeGlide post-flight prompting, and LiveFollow sharing suppress external side effects when the live source kind is `SIMULATOR_CONDOR2`, while replay remains authoritative through `FlightDataRepository.Source.REPLAY`.
