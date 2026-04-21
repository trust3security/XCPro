# CONDOR2_FULL Integration Phased IP (v2)

## Summary

This document replaces the earlier high-level Condor phased plan with an explicit state and boundary contract.

The implementation target is unchanged:

- `CONDOR2_FULL` is the only simulator-backed live mode in v1.
- map ownship must continue to come from fused live `FlightDataRepository.flightData.gps`.
- replay binders remain replay-only and are not reused as a live-source shortcut.
- `feature:profile` persists the user-selected desired live mode.
- `feature:flight-runtime` owns the canonical policy that resolves which live source is effective.
- `app` applies already-resolved policy only; it does not decide source policy.
- `feature:simulator` owns Condor transport, parsing, runtime session state, and stream health.
- the user must be able to actually connect a Condor 2 bridge from Android, so
  bridge connection UX is part of the v1 implementation slice.

This v2 plan exists because the first pass still left architecture-critical ownership and dependency rules under-specified.

## Architecture Goal

Add Condor 2 as a supported live-source path without introducing:

- a `feature:flight-runtime -> feature:profile` back-edge
- a `feature:flight-runtime -> feature:map` dependency
- a second live-mode state owner
- a policy bag / service-locator seam in runtime
- silent fallback from Condor to phone GPS in `CONDOR2_FULL`
- simulator-specific downstream policy living inside runtime

## Non-Goals

- Condor 3 support
- multiple simulator live modes in v1
- replay ownership changes
- map-specific source-selection logic
- app/service-owned active source policy
- full XCSoar-style multi-device manager parity

## State and Ownership Contract

The following contract is normative.

### DesiredLiveMode

```kotlin
enum class DesiredLiveMode {
    PHONE_ONLY,
    CONDOR2_FULL,
}
```

Owner and mutation rules:

- authoritative owner: `feature:profile`
- persistence owner: `feature:profile`
- mutation authority: settings/profile UI only
- runtime access path: `DesiredLiveModePort` declared in `feature:flight-runtime`
- forbidden: direct dependency from `feature:flight-runtime` to `feature:profile`
- forbidden: mirroring this state in app, map, simulator, or service code

### PhoneLiveCapability

```kotlin
sealed interface PhoneLiveCapability {
    data object Ready : PhoneLiveCapability
    data class Unavailable(val reason: PhoneLiveCapabilityReason) : PhoneLiveCapability
}

enum class PhoneLiveCapabilityReason {
    LOCATION_PERMISSION_MISSING,
    LOCATION_PROVIDER_DISABLED,
    PLATFORM_RUNTIME_UNAVAILABLE,
}
```

Owner and mutation rules:

- authoritative owner: platform-edge implementation of `PhoneLiveCapabilityPort`
- port declaration owner: `feature:flight-runtime`
- mutators: Android permission/provider/runtime observers only
- runtime access path: `PhoneLiveCapabilityPort` consumed by `LiveSourceResolver`
- intentionally narrow: this seam exposes only resolver-relevant phone capability inputs
- map-local restart heuristics, local GPS diagnostics, and sleep/doze recovery stay in the dedicated map-local phone-health seam
- forbidden: app/service/map code re-deriving live-source policy from richer phone-health state outside `LiveSourceResolver`

### EffectiveLiveSource

```kotlin
enum class EffectiveLiveSource {
    PHONE,
    CONDOR2,
}
```

Owner and mutation rules:

- authoritative owner: `LiveSourceResolver` in `feature:flight-runtime`
- persisted: no
- derived from: `DesiredLiveModePort`, `PhoneLiveCapabilityPort`, `CondorLiveStatePort`
- forbidden: duplicate computation in UI, app/service, simulator, fusion, wind, or map

### SelectedLiveSensorDataSource

```kotlin
enum class SelectedLiveSensorDataSource {
    PHONE_SENSORS,
    CONDOR_SIMULATOR,
}
```

Owner and mutation rules:

- authoritative owner: `LiveSourceResolver` in `feature:flight-runtime`
- derived only from: `EffectiveLiveSource`
- forbidden: separate sensor-selection logic in DI modules or UI

### SelectedLiveAirspeedSource

```kotlin
enum class SelectedLiveAirspeedSource {
    PHONE_OR_NONE,
    CONDOR_SIMULATOR,
}
```

Owner and mutation rules:

- authoritative owner: `LiveSourceResolver` in `feature:flight-runtime`
- derived only from: `EffectiveLiveSource`
- forbidden: independent airspeed-selection logic outside runtime

### CondorSessionState

```kotlin
data class CondorSessionState(
    val connection: ConnectionState,
    val framing: FramingState,
    val freshness: StreamFreshness,
    val hasFixPayload: Boolean,
    val lastReceiveElapsedRealtimeMs: Long?,
    val lastPayloadUtc: Instant?,
)
```

Owner and mutation rules:

- authoritative owner: `feature:simulator`
- mutators: Condor transport / parser / runtime only
- freshness authority: injected Android monotonic receive time (`elapsedRealtime`-style clock) at the bridge boundary
- parsed NMEA UTC: payload wall-time only, never runtime freshness authority
- forbidden: app/map/runtime computing Condor freshness independently

### CondorLiveState / `CondorLiveStatePort`

```kotlin
data class CondorBridgeRef(
    val stableId: String,
    val displayName: String?,
)

enum class CondorReconnectState {
    IDLE,
    WAITING,
    ATTEMPTING,
    BLOCKED,
    EXHAUSTED,
}

data class CondorLiveState(
    val selectedBridge: CondorBridgeRef?,
    val activeBridge: CondorBridgeRef?,
    val session: CondorSessionState,
    val reconnect: CondorReconnectState,
    val lastFailure: CondorLiveDegradedReason?,
)

interface CondorLiveStatePort {
    val state: StateFlow<CondorLiveState>
}
```

Owner and mutation rules:

- port declaration owner: `feature:flight-runtime`
- authoritative implementation owner: `feature:simulator`
- runtime access path: `LiveSourceResolver` reads Condor state through
  `CondorLiveStatePort`
- this is a read-only contract; the port must not expose bridge mutation,
  connect, disconnect, or reconnect methods
- selected bridge identity remains simulator-owned persisted state even though
  runtime consumes it through the read model
- active bridge identity, reconnect state, and last failure remain
  simulator-owned runtime state
- read model must not expose Android Bluetooth framework types, sockets, or
  other platform handles on this cross-feature seam
- forbidden: app/map/UI writing bridge state through `CondorLiveStatePort`

### `CondorBridgeControlPort`

```kotlin
interface CondorBridgeControlPort {
    suspend fun selectBridge(bridge: CondorBridgeRef)
    suspend fun clearSelectedBridge()
    suspend fun connect()
    suspend fun disconnect()
}
```

Owner and mutation rules:

- declaration owner: `feature:simulator`
- implementation owner: `feature:simulator`
- consumers: Condor settings / bridge connection UI only
- this seam owns settings-triggered bridge actions only; it is not a substitute
  for `VarioRuntimeControlPort`
- map/replay callsites must not use this seam for general live runtime
  lifecycle control
- `connect()` is the only manual start / retry action on this seam; queued or
  exhausted reconnect state remains simulator-owned diagnostics rather than a
  separate public retry command
- retry/backoff, invalid-selection handling, and bridge persistence mutation
  stay inside simulator ownership behind this seam

### LiveStartupRequirement

```kotlin
enum class LiveStartupRequirement {
    NONE,
    ANDROID_FINE_LOCATION_PERMISSION,
}
```

Owner and mutation rules:

- authoritative owner: `LiveSourceResolver` in `feature:flight-runtime`
- app consumes this requirement and applies platform behavior
- forbidden: app/service layer inventing separate permission rules for active source policy

### LiveSourceStatus

```kotlin
enum class PhoneLiveDegradedReason {
    LOCATION_PERMISSION_MISSING,
    LOCATION_PROVIDER_DISABLED,
    PLATFORM_RUNTIME_UNAVAILABLE,
}

enum class CondorLiveDegradedReason {
    DISCONNECTED,
    STALE_STREAM,
    FRAMING_ERROR,
    NO_FIX_PAYLOAD,
    TRANSPORT_ERROR,
}

sealed interface LiveSourceStatus {
    data object PhoneReady : LiveSourceStatus
    data class PhoneDegraded(val reason: PhoneLiveDegradedReason) : LiveSourceStatus
    data object CondorReady : LiveSourceStatus
    data class CondorDegraded(val reason: CondorLiveDegradedReason) : LiveSourceStatus
}
```

Owner and mutation rules:

- authoritative owner: `LiveSourceResolver` in `feature:flight-runtime`
- derived from the same inputs as `EffectiveLiveSource`
- consumed by UI and status callsites
- UI maps typed reasons to user-facing text; runtime does not expose free-form degraded reason strings on this seam
- forbidden: phone-only or Condor-only status logic bypassing runtime selection

### LiveSourceKind

```kotlin
enum class LiveSourceKind {
    PHONE,
    SIMULATOR_CONDOR2,
}
```

Owner and mutation rules:

- authoritative owner: `LiveSourceResolver` in `feature:flight-runtime`
- use: downstream feature policy classification
- forbidden: runtime owning downstream `feature:igc` / `feature:weglide` policy itself

### FlightDataRepository live/replay source axis

`FlightDataRepository.Source` remains:

```kotlin
enum class Source {
    LIVE,
    REPLAY,
}
```

Owner and mutation rules:

- authoritative owner: `FlightDataRepository`
- this axis distinguishes live authority from replay authority only
- both phone-backed live and Condor-backed live publish under `Source.LIVE`
- replay remains the only producer using `Source.REPLAY`
- simulator-aware downstream behavior consumes `LiveSourceKind`, not a widened repository source enum
- forbidden: adding `CONDOR`, `SIMULATOR`, or any other third repository source value for this work

## Canonical Policy Owner

`LiveSourceResolver` in `feature:flight-runtime` is the only selector and policy owner for live-source selection.

The following must derive from `LiveSourceResolver` and nowhere else:

- `EffectiveLiveSource`
- `SelectedLiveSensorDataSource`
- `SelectedLiveAirspeedSource`
- `LiveStartupRequirement`
- `LiveSourceStatus`
- `LiveSourceKind`

Forbidden patterns:

- sensor-specific selector outside runtime
- airspeed-specific selector outside runtime
- app/service deciding which source should be active
- UI deriving source policy from raw simulator or phone state
- map-specific source policy

## Boundary Rules

### `feature:profile`

Owns:

- persisted `DesiredLiveMode`
- settings/profile UI mutation path

Must not own:

- effective live-source selection
- runtime status derivation
- runtime startup rules

### `feature:flight-runtime`

Owns:

- `DesiredLiveModePort` declaration
- `PhoneLiveCapabilityPort` declaration
- `CondorLiveStatePort` declaration
- `LiveSourceResolver`
- `EffectiveLiveSource`
- `SelectedLiveSensorDataSource`
- `SelectedLiveAirspeedSource`
- `LiveStartupRequirement`
- `LiveSourceStatus`
- `LiveSourceKind`

Must not own:

- Condor transport/parsing/session runtime
- settings persistence implementation
- map-specific orientation display behavior
- `feature:igc` / `feature:weglide` downstream policy

### `feature:simulator`

Owns:

- Condor bridge transport
- parser/runtime
- reconnect and framing state
- `CondorSessionState`
- `CondorLiveStatePort` implementation
- `CondorBridgeControlPort`
- simulator-specific session lifecycle helpers
- simulator-specific NMEA / LXWP0 mapping
- persisted bridge selection and bridge-session diagnostics exposed through
  narrow simulator-owned seams

Must not own:

- desired mode persistence
- app/service permission policy
- global live-source selection policy
- map UI logic

### Condor bridge connection UX slice

This slice is required for the first implementation because "Condor 2 support"
is not complete unless a user can actually connect the Windows-hosted bridge to
XCPro on Android.

The v1 UX scope is:

- first-run bridge setup
- persisted bridge selection owner
- reconnect states
- minimum diagnostics surface for `CONDOR2_FULL`

Default product shape:

- reuse the existing bonded-Bluetooth selection / connect / disconnect pattern,
  plus reconnect-state diagnostics, already used for external Bluetooth device
  flows
- use a dedicated Condor screen only if boundary review shows that reuse would
  leak LX-specific ownership, strings, or state contracts into the simulator
  path

Ownership rules for this slice:

- settings UI may trigger bridge selection and connection actions
- persisted selected bridge identity belongs to a simulator-owned repository or
  equivalent simulator-owned state owner, not to `feature:profile`, `app`, or
  `feature:map`
- reconnect state, active bridge identity, and bridge diagnostics are
  simulator-owned runtime state
- `DesiredLiveMode` remains separate state owned by `feature:profile`; do not
  merge mode selection and bridge selection into one owner

Minimum v1 user-visible surface:

- selected bridge
- active bridge
- explicit connect / disconnect action
- reconnect state: waiting / attempting / blocked / exhausted
- minimum diagnostics: connection status, stream healthy vs stale, and last
  failure reason

First-run assumptions for v1:

- the user pairs the PC-hosted Bluetooth bridge in Android OS settings first
- XCPro then lets the user choose from bonded bridge devices
- XCPro remembers the chosen bridge and uses that persisted selection for later
  reconnect attempts

Bridge-selection lifecycle rules for v1:

- transient disconnect, stale stream, or reconnect exhaustion must not
  auto-clear the persisted selected bridge
- if the persisted bridge is no longer bonded, no longer available, or
  otherwise invalid, simulator state transitions to `BLOCKED` and surfaces the
  failure; XCPro must not silently swap to another bridge or silently clear the
  selection
- clearing the persisted bridge requires an explicit settings recovery action
  such as clear or re-pick
- retry/backoff ownership belongs to simulator runtime, not to UI, profile,
  app, or map code
- v1 may use a fixed or capped retry policy, but `WAITING`, `ATTEMPTING`,
  `BLOCKED`, and `EXHAUSTED` must be simulator-owned states rather than UI-only
  wording

### `app`

Owns:

- lifecycle/service/permission actuation
- application of already-resolved runtime policy
- app implementation of `VarioRuntimeControlPort`
- platform-edge implementation of `PhoneLiveCapabilityPort`
- internal fan-out from the public runtime-control seam into phone or Condor helpers

Must not own:

- active live-source decision
- duplicate status policy
- duplicate startup policy
- map-local phone restart heuristics or user-facing live-source wording

### `feature:map`

Consumes:

- fused `FlightDataRepository` outputs
- `LiveSourceStatus`
- map-local phone device health / restart diagnostics only through a dedicated local seam

Owns:

- `VarioRuntimeControlPort` as the canonical public map/replay runtime-control seam
- camera/display behavior
- map-specific orientation controller/display logic
- map-local phone device health / restart diagnostics seam for controller logic only

Must not own:

- source selection
- Condor runtime freshness logic
- direct Condor ownship truth
- user-facing live-source status derived from `UnifiedSensorManager`
- runtime policy types on the public `VarioRuntimeControlPort` API

## Explicit Bypass-Replacement Mapping

| Current bypass | Required replacement seam | Owner after change |
|---|---|---|
| `VarioForegroundService.startIfPermitted()` | app-side actuation driven by `LiveStartupRequirement` and resolver outputs | `app` |
| `ForegroundServiceVarioRuntimeController.ensureRunningIfPermitted()` | source-aware `VarioRuntimeControlPort` implementation driven by resolver outputs | `app` |
| `MapSensorsUseCase.gpsStatusFlow` direct phone status | `LiveSourceStatus` | `feature:flight-runtime` |
| `MapSensorsUseCase.sensorStatus()` / `isGpsEnabled()` used for mixed live status and phone diagnostics | dedicated map-local phone device health seam used only by controller diagnostics / restart logic | `feature:map` local diagnostics owner |
| `SensorFusionModule` direct `UnifiedSensorManager` live binding | `SelectedLiveSensorDataSource` | `feature:flight-runtime` |
| `WindSensorModule` direct live sensor binding | `SelectedLiveSensorDataSource` | `feature:flight-runtime` |

Notes:

- phone-specific and simulator-specific helpers may still exist behind `VarioRuntimeControlPort` and runtime-owned internal seams
- `LocationSensorsController` may consume phone-device health only for local restart and diagnostics; it must never derive effective source, ownship truth, or user-facing Condor status from that seam
- replay binders remain replay-only; do not reuse `MapLocationFlightDataRuntimeBinder` as a live routing shortcut
- Condor-selected live samples still publish into `FlightDataRepository` as `Source.LIVE`; downstream simulator-aware behavior keys off `LiveSourceKind`

## Heading Authority Rule

This rule is mandatory for `CONDOR2_FULL`.

When `EffectiveLiveSource == CONDOR2`:

- aircraft marker truth comes from fused simulator-fed `FlightDataRepository.flightData.gps` only
- ownship pose / bearing / track truth must come from fused flight data only
- phone orientation may drive camera or display behavior only
- phone compass must never become Condor ownship fallback

This means a stationary phone paired with a flying Condor session must still render simulator ownship truth for the aircraft marker.

## No-Fallback Rule

This rule is mandatory for `CONDOR2_FULL`.

If the Condor stream disconnects, stalls, or degrades while `DesiredLiveMode == CONDOR2_FULL`:

- `EffectiveLiveSource` remains Condor
- `LiveSourceStatus` becomes Condor-degraded
- the app may surface degraded state and runtime controls
- the system must not silently switch back to phone GPS

Any future fallback behavior would require a separate ADR and explicit UX/state model.

## Timebase Policy

Time ownership is fixed as follows:

- Condor stream freshness/staleness uses injected monotonic receive time captured on Android at the bridge boundary
- parsed NMEA UTC is payload data only
- runtime freshness must not be computed from payload UTC
- replay remains replay-time driven and isolated from live-source freshness

This prevents payload wall-time drift from becoming a hidden runtime health authority.

## App / Platform Boundary

`app` is an actuator and platform-edge capability adapter only.

`app` may:

- request Android permission when `LiveStartupRequirement` demands it
- implement `PhoneLiveCapabilityPort` as a narrow platform-edge capability adapter
- implement `VarioRuntimeControlPort` for map/replay callers
- apply resolver outputs to internal phone or Condor runtime helpers

`app` may not:

- decide which live source should be active
- derive its own startup policy
- override `LiveSourceResolver` with service-level heuristics

## Public Runtime-Control Contract

`VarioRuntimeControlPort` remains the stable caller seam for map and replay.

Contract:

- keep the public API semantic contract as "ensure the resolver-selected live runtime is active"
- treat `ensureRunningIfPermitted()` as a compatibility name, not as phone-GPS-specific policy authority
- keep the public API caller-agnostic
- do not pass `DesiredLiveMode`, `EffectiveLiveSource`,
  `LiveStartupRequirement`, or simulator-specific runtime types through the
  map-facing API
- app implementation may read runtime-derived startup and source state through
  inward-facing runtime seams
- any fan-out to phone or Condor-specific helpers stays internal to app or
  simulator ownership
- callsites must not interpret a false return as phone-permission-only semantics once resolver-driven Condor support exists

This keeps `feature:map` from becoming a transit path for runtime policy types.

## Map Phone-Health Boundary

Phone-device health is not the same thing as live-source truth.

Rules:

- user-facing live status and active-source truth come from `LiveSourceStatus`
  and fused live flight data
- phone-device diagnostics such as local GPS enabled state, local permission
  state, and restart heuristics stay in a dedicated map-local phone-health seam
- `LocationSensorsController` may use that phone-health seam for sleep/doze
  restart logic and diagnostics only
- phone-health diagnostics must never decide `EffectiveLiveSource`, Condor
  degraded-state policy, or ownship truth

## Downstream Feature Policy Rule

`feature:flight-runtime` may expose `LiveSourceKind`.

Downstream features such as `feature:igc` and `feature:weglide` may consume `LiveSourceKind.SIMULATOR_CONDOR2` to suppress simulator-inappropriate behavior.

`feature:flight-runtime` must not own those downstream policies directly.

## Implementation Phases

### Phase 0 - Contract lock and ADR update

- replace the old phased IP with this v2 contract
- update the Condor boundaries note with explicit seam rules
- promote the ADR draft with the durable decisions in this document
- identify the exact `PIPELINE.md` sections that must be updated in the same implementation change that rewires live runtime ownership or routing
- add boundary-lock tests to the implementation acceptance criteria before code movement starts

### Phase 1 - Ports and persisted mode

- add `DesiredLiveModePort` in `feature:flight-runtime`
- implement `DesiredLiveModePort` in `feature:profile`
- keep `feature:flight-runtime` free of any direct `feature:profile` dependency
- add `PhoneLiveCapabilityPort` in `feature:flight-runtime` with a narrow typed capability contract
- implement `PhoneLiveCapabilityPort` at the platform edge only; do not reuse the map-local phone-health seam as the runtime port
- add `CondorLiveStatePort` as a read-only runtime input contract with an
  explicit `CondorLiveState` read model
- add contracts for selected live sensor and airspeed sources
- preserve `VarioRuntimeControlPort` as the only public runtime-control seam
- lock the rule that any richer actuation inputs are read inside the app
  implementation, not pushed through the map-facing API

### Phase 2 - `LiveSourceResolver`

- implement `LiveSourceResolver` as the only live selector/policy owner
- derive effective source, sensor source, airspeed source, startup requirement, source status, and source kind from the same resolver
- derive source status from typed degraded reasons, not free-form strings
- remove duplicate source selection logic from existing callsites and DI modules

### Phase 3 - `feature:simulator`

- create `feature:simulator`
- move Condor transport, parser/runtime, stream freshness, reconnect state, and simulator-specific session lifecycle helpers there
- keep stream health and payload mapping simulator-owned
- split first concrete simulator owners explicitly:
  - `CondorBridgeTransport` (Bluetooth/socket/line-framing owner)
  - `CondorSentenceMapper` (GGA / RMC / LXWP0 mapping owner)
  - `CondorSessionRepository` (selected bridge persistence, `CondorSessionState`,
    reconnect state, last failure, and `CondorLiveStatePort` implementation)
  - `CondorBridgeController` (`CondorBridgeControlPort` implementation for
    settings-triggered select / clear / connect / disconnect)

### Phase 3A - Bridge connection UX slice

- add a Condor bridge connection UX slice covering first-run bridge setup,
  persisted bridge selection owner, reconnect states, and minimum diagnostics
  surface for `CONDOR2_FULL`
- reuse the existing Bluetooth settings interaction pattern unless boundary
  review requires a dedicated Condor screen
- keep persisted bridge identity in a simulator-owned state owner exposed
  through a narrow seam to settings UI
- keep reconnect status and bridge diagnostics simulator-owned; do not mirror
  them into profile, app, or map state owners
- keep bridge actions on a simulator-owned `CondorBridgeControlPort` (or
  equivalently narrow simulator-owned settings seam), separate from
  `CondorLiveStatePort` and separate from `VarioRuntimeControlPort`
- treat `selectBridge()` and `clearSelectedBridge()` as lifecycle mutations, not
  just persistence writes: changing or clearing selection must cancel any queued
  reconnect and remove the previous bridge as reconnect target immediately
- the persisted selected bridge is the only allowed reconnect target; simulator
  must never reconnect to a bridge that is no longer selected
- define the invalid-selection recovery path: blocked state when the persisted
  bridge is missing/unbonded, plus explicit clear / re-pick
- define retry/backoff ownership in simulator runtime; UI surfaces the state
  but does not own timers, counters, or retry policy
- keep `connect()` as the only manual start / retry action; when reconnect is
  `WAITING` or `EXHAUSTED`, pressing Connect cancels queued backoff and starts a
  fresh immediate attempt for the currently selected bridge
- keep `CondorLiveStatePort` contract-parity with the slice: expose only
  session/degraded fields that are actually produced by simulator owners in this
  phase; defer parser-level UTC or richer failure states until a concrete owner
  exists in the parser/fusion slice
- define the minimum settings/runtime text and control set needed for the user
  to connect, disconnect, recover, and understand bridge health

### Phase 3B - Bridge lifecycle hardening before Phase 4

- do not start DI / lifecycle rewiring until the bridge-ownership slice is
  closed under selection-change and clear-selection behavior
- keep code ownership narrow:
  - `CondorBridgeController` owns user intent for select / clear / connect /
    disconnect
  - `CondorBridgeTransport` owns active target, reconnect timer, and session
    execution
  - `CondorSessionRepository` remains a read-only projection / runtime read seam
- add an explicit transport-level action to cancel and forget the current target
  when selection changes or clears; do not infer this from `activeBridge`
- prove that reconnect `WAITING` / `ATTEMPTING` cannot outlive a clear or
  replace-selection action
- trim `CondorLiveStatePort` to implemented semantics, or fully implement the
  advertised fields before runtime consumers rely on them
- Phase 4 is blocked until this correction slice passes

### Phase 4 - DI / lifecycle rewiring

- rewire fusion and wind DI to consume `SelectedLiveSensorDataSource` and `SelectedLiveAirspeedSource`
- update the existing `VarioRuntimeControlPort` implementation so app-side lifecycle actuation consumes resolver outputs only
- ensure permission prompting is driven by `LiveStartupRequirement`, not by direct service heuristics
- keep phone or Condor-specific actuation fan-out internal to app/simulator
  helpers
- keep Condor and phone inside the same `FlightDataRepository.Source.LIVE` branch; do not widen repository source authority for simulator classification

### Phase 5 - UI and heading authority

- replace direct phone-only live status with `LiveSourceStatus`
- split `MapSensorsUseCase` so phone-device diagnostics / restart checks move to
  a dedicated map-local phone-health seam
- keep `LocationSensorsController` on phone-health diagnostics only; never let
  it own active-source truth or Condor degraded-state policy
- enforce fused Condor ownship truth for aircraft marker pose / bearing
- keep phone orientation strictly display/camera-scoped in Condor mode

### Phase 6 - Replay and side effects

- keep replay authoritative while active
- replay start suspends live authority through the existing replay path only
- replay stop re-applies resolver-selected live mode
- downstream features consume `LiveSourceKind` for simulator-specific suppression
- shipped scope: IGC recording input, WeGlide post-flight prompt publication,
  and LiveFollow side effects suppress when
  `LiveSourceKind.SIMULATOR_CONDOR2` is active

## Required Proof / Boundary-Lock Tests

Implementation is not complete until these proofs exist:

- boundary-lock tests proving no `feature:flight-runtime -> feature:profile` dependency
- boundary-lock tests proving no `feature:flight-runtime -> feature:map` dependency
- boundary-lock tests proving no direct dependence on simulator implementations from map or domain consumers
- boundary-lock tests proving map and replay start/stop requests still go through `VarioRuntimeControlPort` only
- boundary-lock tests proving `VarioRuntimeControlPort` does not grow runtime
  policy or simulator-specific parameters on the public API
- tests proving runtime reads Condor bridge/session state through
  `CondorLiveStatePort` only
- tests proving `CondorLiveStatePort` remains read-only and exposes no bridge
  mutation methods
- tests proving no duplicate `DesiredLiveMode` owner exists
- tests proving no duplicate persisted bridge-selection owner exists across
  simulator, profile, app, or map
- tests proving bridge mutation actions go through simulator-owned
  `CondorBridgeControlPort` (or equivalent simulator-owned settings seam), not
  `VarioRuntimeControlPort`
- tests proving selection logic does not exist outside `LiveSourceResolver`
- tests proving no third `FlightDataRepository.Source` value is introduced for Condor
- tests proving Condor-selected live samples still publish through `FlightDataRepository.Source.LIVE`
- tests proving user-facing live status no longer depends on
  `UnifiedSensorManager.gpsStatusFlow`
- tests proving runtime status uses typed source-specific degraded reasons rather than free-form strings
- UI or integration tests proving the selected bridge persists, reconnect state
  is surfaced, and the minimum diagnostics surface is present for
  `CONDOR2_FULL`
- UI or integration tests proving `Connect` is the only manual retry affordance
  and works during reconnect `WAITING` and after `EXHAUSTED`
- UI or integration tests proving transient failures do not clear the persisted
  bridge and missing/unbonded persisted bridges surface `BLOCKED` plus explicit
  clear / re-pick recovery
- unit or integration tests proving clear-selection during reconnect `WAITING`
  cancels the queued reconnect and prevents the old bridge from reconnecting
- unit or integration tests proving replace-selection during reconnect
  `WAITING` / `ATTEMPTING` forgets the previous target before any reconnect
  executes
- tests proving the reconnect target always matches the persisted selected
  bridge or null
- tests proving `CondorLiveStatePort` in the bridge-ownership slice does not
  advertise parser/degraded fields that simulator runtime does not yet produce
- tests proving phone-device diagnostics used by `LocationSensorsController` do
  not drive effective-source or ownship fallback behavior
- integration tests proving Condor ownship uses fused live `flightData.gps`
- orientation tests proving phone orientation never becomes Condor ownship fallback
- replay tests proving replay remains authoritative while active
- degraded-state tests proving Condor disconnect does not silently revert to phone GPS

## Rollout / Rollback Notes

Rollout should happen phase-by-phase, with boundary-lock tests added before the code movement that relies on them.

Rollback, if needed, must remove resolver-driven Condor selection as a whole. It must not reintroduce partial direct callsite bypasses or phone-fallback shortcuts.

## Assumptions and Defaults

- `CONDOR2_FULL` is the only simulator live mode in v1
- Condor 3 remains out of scope
- replay binder ownership remains replay-only
- repo files are not mutated during Plan Mode; this document is the proposed on-disk replacement text
