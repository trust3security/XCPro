# CONDOR2 Boundaries and Source Routing (v2)

## Purpose

This note locks the seam rules for Condor as a live-source path.

It exists to prevent a structurally convenient implementation from introducing:

- a profile back-edge into runtime
- a second desired-mode owner
- app/service-owned source policy
- duplicated selector logic across sensor, airspeed, UI status, or DI wiring
- direct map dependence on simulator/runtime internals

## Architecture Fit

The target shape is:

- `feature:profile` owns persisted desired live mode
- `feature:flight-runtime` owns live-source policy and selection
- `feature:simulator` owns Condor transport/parser/runtime/session health
- `app` applies resolved lifecycle and permission actions only
- `feature:map` consumes fused flight data and runtime status only

This avoids:

- `feature:flight-runtime -> feature:profile`
- `feature:flight-runtime -> feature:map`
- a second live-mode state owner
- a runtime policy bag

## Boundary Rule: DesiredLiveModePort

`DesiredLiveModePort` is declared in `feature:flight-runtime` and implemented in `feature:profile`.

Implications:

- `feature:flight-runtime` reads desired mode through its own declared port
- `feature:profile` remains the persistence owner
- settings/profile UI remains the only mutator of desired mode
- `feature:flight-runtime` must not depend directly on `feature:profile`
- app, simulator, map, and services must not own mirror state for desired mode

Allowed dependency direction:

- implementation points inward toward the runtime-declared port

Forbidden direction:

- runtime reaching outward to profile implementation details

## Boundary Rule: PhoneLiveCapabilityPort

`PhoneLiveCapabilityPort` is declared in `feature:flight-runtime` and implemented at the platform edge.

Implications:

- `LiveSourceResolver` reads only resolver-relevant phone capability through this port
- the port exposes typed phone capability outcomes such as permission/provider/runtime availability
- the port is intentionally narrower than map-local phone-health and restart diagnostics
- app/service/map code must not re-derive active-source policy from richer phone-health state outside the resolver
- `LocationSensorsController` and related map restart logic continue to use a dedicated map-local phone-health seam instead

## Boundary Rule: `CondorLiveStatePort` Is Read-Only Runtime Input

`CondorLiveStatePort` is declared in `feature:flight-runtime` and implemented by
`feature:simulator`.

Its read model includes:

- selected bridge identity
- active bridge identity
- `CondorSessionState` for connection / framing / freshness / fix payload
- reconnect state
- last failure reason

Rules:

- `LiveSourceResolver` consumes this state as input only
- the port must not expose bridge mutation, connect, disconnect, or reconnect
  methods
- selected bridge persistence remains simulator-owned even though runtime reads
  it through this port
- the read model must not expose Android Bluetooth framework types or platform
  handles
- in the bridge-ownership slice, the read model must expose only state that the
  simulator owner actually produces; future parser-level UTC or richer degraded
  reasons must not be declared early on this cross-feature seam
- UI and app code must not turn this read seam into a second state owner

## Boundary Rule: LiveSourceResolver Is the Only Selector / Policy Owner

`LiveSourceResolver` in `feature:flight-runtime` is the only owner of live-source selection policy.

The following are derived from `LiveSourceResolver` and nowhere else:

- `EffectiveLiveSource`
- `SelectedLiveSensorDataSource`
- `SelectedLiveAirspeedSource`
- `LiveStartupRequirement`
- `LiveSourceStatus`
- `LiveSourceKind`

This means:

- selected sensor source must not invent its own selection logic
- selected airspeed source must not invent its own selection logic
- startup requirement computation must not invent separate rules in app/service code
- UI status must not invent separate phone-only or Condor-only routing logic
- map must not infer active source directly from simulator or phone state

## Boundary Rule: FlightDataRepository Source Axis Stays LIVE vs REPLAY

`FlightDataRepository.Source` remains a live-vs-replay authority axis only.

Rules:

- the valid source values remain `LIVE` and `REPLAY`
- both phone-backed live and Condor-backed live publish under `Source.LIVE`
- replay remains the only producer using `Source.REPLAY`
- downstream simulator-aware behavior keys off `LiveSourceKind`, not a third repository source value
- do not add `CONDOR`, `SIMULATOR`, or similar repository source enum values for this integration

## Boundary Rule: App / Platform Edge Is an Actuator and Capability Adapter Only

`app` and service/platform code are actuation edges plus narrow capability adapters only.

They may:

- request Android location permission when required by resolver output
- implement `PhoneLiveCapabilityPort` as a narrow platform-edge capability adapter
- implement `VarioRuntimeControlPort` for map and replay callers
- forward already-resolved decisions to internal phone or Condor runtime helpers

They may not:

- decide the active source
- decide fallback policy
- decide degraded-state routing
- compute duplicate startup requirements
- override resolver policy with service heuristics

Permission prompting and runtime start decisions must be derived from resolver outputs.

`VarioRuntimeControlPort` remains the only public start/stop seam. Do not add
public `PhoneLiveRuntimeControlPort` or `CondorLiveRuntimeControlPort`.

It must also remain caller-agnostic:

- keep the semantic contract as "ensure the resolver-selected live runtime is active"
- treat `ensureRunningIfPermitted()` as a compatibility name, not as phone-GPS-only policy authority
- do not add `DesiredLiveMode`, `EffectiveLiveSource`,
  `LiveStartupRequirement`, or simulator-specific runtime types to the public
  map-facing API
- app implementation may read those inputs internally through runtime-owned read
  seams
- source-specific actuation fan-out remains internal to app or simulator owners

## Boundary Rule: Condor Bridge Control Stays Simulator-Owned

A narrow simulator-owned bridge control seam, such as
`CondorBridgeControlPort`, may exist for Condor settings UI.

It may:

- select the persisted bridge
- clear the persisted bridge
- request connect / disconnect
- use `connect()` as the only manual retry action while reconnect `WAITING` /
  `EXHAUSTED` remain simulator-owned state

It may not:

- become a second public map/replay runtime-control API
- own desired live mode
- be used by map/replay callers as a substitute for `VarioRuntimeControlPort`
- move retry/backoff ownership into UI, app, or profile code

Retry/backoff, invalid-selection handling, and persistence mutation remain
inside simulator runtime ownership behind this seam.

## Boundary Rule: Phone Device Health Is Not Live-Source Truth

Current map/controller code still uses phone-local sensor and GPS health.

That concern must stay separate from source truth:

- `LiveSourceStatus` owns user-facing live-source status
- fused live `FlightDataRepository.flightData.gps` owns ownship truth
- phone-local GPS enabled state, permission state, and restart heuristics belong
  to a dedicated map-local phone-health seam
- `LocationSensorsController` may consume that phone-health seam for restart and
  diagnostics only
- phone-health reads must never decide active source, Condor degraded-state
  policy, or ownship fallback

## Boundary Rule: Heading Authority in `CONDOR2_FULL`

Condor ownship truth is fused flight-data truth.

When `EffectiveLiveSource == CONDOR2`:

- ownship position, track, heading, and marker pose truth come from fused live `FlightDataRepository.flightData.gps`
- phone orientation may drive camera behavior or display behavior only
- phone compass fallback for Condor ownship is explicitly forbidden

This is a seam lock, not a UI preference.

If phone orientation is useful for camera/display in Condor mode, it must remain strictly display-scoped and must not become aircraft truth.

## Boundary Rule: No Silent Phone Fallback

When `DesiredLiveMode == CONDOR2_FULL` and Condor disconnects or becomes stale:

- effective source remains Condor
- status becomes Condor-degraded
- the system surfaces degraded behavior through runtime status and controls
- the system must not silently route ownship back to phone GPS

This avoids a hidden policy split between desired mode and actual source.

## Boundary Rule: Bridge Selection Lifecycle Ownership

Bridge-selection lifecycle is simulator-owned.

Rules:

- the persisted selected bridge survives app restart
- transient disconnect, stale stream, and reconnect exhaustion do not clear the
  persisted bridge automatically
- the persisted selected bridge is the only allowed reconnect target
- changing selection or explicitly clearing selection cancels any queued
  reconnect and forgets the previous target immediately
- if the persisted bridge is missing, unbonded, or otherwise invalid, simulator
  state transitions to `BLOCKED` and surfaces recovery actions rather than
  silently clearing or replacing the selection
- explicit clear / re-pick is the recovery path for invalid saved bridge state
- retry/backoff timing, counters, and state transitions belong to simulator
  runtime rather than UI wording or app heuristics

## Boundary Rule: Timebase Ownership

Condor runtime freshness is based on injected Android monotonic receive time at the bridge boundary.

- monotonic receive time is the runtime freshness authority
- NMEA UTC is payload data only
- replay time remains replay-owned and isolated

This prevents freshness drift from being inferred differently in runtime, simulator, or UI.

## Existing Callsites That Must Change

The following matrix is normative.

| Current callsite | Current bypass | Replacement seam | Implementation phase |
|---|---|---|---|
| `VarioForegroundService.startIfPermitted()` | service decides live startup directly | app-side actuation driven by `LiveStartupRequirement` and resolver outputs | Phase 4 |
| `ForegroundServiceVarioRuntimeController.ensureRunningIfPermitted()` | service/runtime controller decides startup directly | same resolver-driven app-side actuation | Phase 4 |
| `MapSensorsUseCase.gpsStatusFlow` | phone-only status path bypassing runtime selection | `LiveSourceStatus` from `feature:flight-runtime` | Phase 5 |
| `MapSensorsUseCase.sensorStatus()` / `isGpsEnabled()` and `LocationSensorsController` restart checks | mixed live-source meaning with phone-local diagnostics | dedicated map-local phone-health seam for controller-only diagnostics and restart | Phase 5 |
| `SensorFusionModule` direct `UnifiedSensorManager` binding | DI directly selects live sensor source | `SelectedLiveSensorDataSource` | Phase 4 |
| `WindSensorModule` direct live sensor binding | DI directly selects live live-source binding | `SelectedLiveSensorDataSource` | Phase 4 |
| map ownship orientation fallback path | phone orientation can implicitly become aircraft truth | fused `flightData.gps` ownship truth, phone orientation display-only | Phase 5 |

Notes:

- replay routing is not replaced by this work
- replay binders stay replay-only
- simulator-specific transport/parsing stays in `feature:simulator`

## Ownership Matrix

| Concern | Canonical owner | Consumers |
|---|---|---|
| persisted desired mode | `feature:profile` | `feature:flight-runtime` through `DesiredLiveModePort` |
| phone live capability | platform-edge `PhoneLiveCapabilityPort` implementation | `feature:flight-runtime` (`LiveSourceResolver`) |
| effective live source | `feature:flight-runtime` (`LiveSourceResolver`) | app, map, downstream features |
| selected live sensor source | `feature:flight-runtime` | fusion/wind DI |
| selected live airspeed source | `feature:flight-runtime` | airspeed/fusion consumers |
| Condor transport + parser + stream health | `feature:simulator` | `feature:flight-runtime` through `CondorLiveStatePort` |
| selected Condor bridge persistence | `feature:simulator` | `feature:flight-runtime` through `CondorLiveStatePort`; settings UI through simulator-owned seam |
| bridge connect / disconnect control | `feature:simulator` | Condor settings UI only |
| live startup requirement | `feature:flight-runtime` | app/service actuator |
| live source status | `feature:flight-runtime` | map/UI/status consumers |
| live source kind | `feature:flight-runtime` | downstream policy consumers |
| ownship marker truth in Condor mode | fused live `FlightDataRepository` | map |
| phone device health / restart diagnostics | `feature:map` local phone-health seam | `LocationSensorsController` and local diagnostics only |
| phone camera/display behavior | `feature:map` | map UI |

## Source Routing Rules

### Phone mode

When `DesiredLiveMode == PHONE_ONLY` and runtime capability is satisfied:

- `EffectiveLiveSource = PHONE`
- selected live sensor source resolves to phone-backed source
- selected airspeed source resolves to phone-backed source or none
- startup requirement may require Android location permission

### Condor mode

When `DesiredLiveMode == CONDOR2_FULL`:

- `EffectiveLiveSource = CONDOR2`
- selected live sensor source resolves to Condor-backed source
- selected live airspeed source resolves to Condor-backed source
- Condor-backed fused flight data still publishes through `FlightDataRepository.Source.LIVE`
- app applies resolver outputs through `VarioRuntimeControlPort` and internal Condor helpers
- phone runtime may still exist as a platform concern, but it does not become ownship truth or hidden fallback policy

### Replay mode

Replay remains outside this selector path and remains authoritative while active through the existing replay path.

Replay must not be implemented as a hidden alternate live-source branch.

## Validation Requirements

Implementation must prove all of the following:

- no direct `feature:flight-runtime -> feature:profile` dependency
- no duplicate desired-mode owner
- no separate selection logic outside `LiveSourceResolver`
- no app/service-owned active source policy
- no competing public runtime-control seam beside `VarioRuntimeControlPort`
- `CondorLiveStatePort` stays read-only and is the only runtime read seam for
  Condor bridge/session state
- bridge mutation actions stay simulator-owned and separate from
  `VarioRuntimeControlPort`
- no third `FlightDataRepository.Source` value for Condor
- no mixed owner for user-facing live-source status and phone-device health
- no free-form degraded reason strings on the runtime status seam
- Condor ownship uses fused live `flightData.gps`
- phone compass never becomes Condor ownship fallback
- replay remains authoritative while active
- Condor disconnect does not silently revert to phone GPS
