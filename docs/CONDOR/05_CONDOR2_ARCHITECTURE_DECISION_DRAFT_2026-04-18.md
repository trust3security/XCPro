# ADR - Condor as a Single-Resolver Live Source (Draft)

- Status: Draft for promotion
- Date: 2026-04-18
- Decision area: live runtime architecture / Condor integration

## Context

XCPro already has established seams for:

- persisted user settings and profile state
- runtime-facing policy and fused flight truth
- map consumption of fused flight data
- app/service lifecycle and platform integration
- replay as an authoritative non-live path while active

Condor support needs to fit these seams without creating a parallel source-routing path.

The first-pass Condor plan identified the general module shape, but it left several durable architecture decisions under-specified:

- where desired live mode is owned
- who owns active live-source policy
- whether app/service code can decide policy
- who owns Condor stream freshness
- who owns ownship heading authority in `CONDOR2_FULL`
- whether phone GPS or phone compass can silently become fallback truth

This ADR captures the missing durable decisions.

## Decision

### 1. Desired live mode uses a runtime-declared port

`DesiredLiveModePort` is declared in `feature:flight-runtime` and implemented in `feature:profile`.

Decision details:

- `feature:profile` is the persistence owner of `DesiredLiveMode`
- settings/profile UI is the only mutator of desired mode
- `feature:flight-runtime` consumes desired mode through the port only
- `feature:flight-runtime` must not depend directly on `feature:profile`
- desired mode must not be mirrored in app, service, simulator, or map code

Rationale:

This preserves inward dependency direction and prevents a profile back-edge into runtime.

### 1A. Phone live capability uses a narrow runtime-declared port

`PhoneLiveCapabilityPort` is declared in `feature:flight-runtime` and implemented at the platform edge.

Decision details:

- the port exposes only resolver-relevant phone capability inputs
- it uses typed capability outcomes rather than free-form strings
- map-local GPS enabled state, permission diagnostics, and restart heuristics stay
  in a dedicated map-local phone-health seam
- app/service/map code must not derive active-source policy from richer phone-health state outside the resolver

Rationale:

This keeps source policy inside runtime while preventing map-local phone diagnostics from turning into a second capability and status owner.

### 1B. Condor live state uses a read-only runtime-declared port

`CondorLiveStatePort` is declared in `feature:flight-runtime` and implemented in
`feature:simulator`.

Decision details:

- the read model exposes selected bridge, active bridge, session health,
  reconnect state, and last failure
- runtime consumes this as input only
- the port has no bridge mutation, connect, disconnect, or reconnect methods
- the read model must not expose Android Bluetooth framework types or platform
  handles
- selected bridge persistence remains simulator-owned even though runtime reads
  it through the port

Rationale:

This gives runtime one explicit Condor state seam without turning runtime into a
transport owner or letting UI/app invent a second Condor state model.

### 2. `LiveSourceResolver` is the sole policy owner

`LiveSourceResolver` in `feature:flight-runtime` is the single owner of live-source selection policy.

It owns derivation of:

- `EffectiveLiveSource`
- `SelectedLiveSensorDataSource`
- `SelectedLiveAirspeedSource`
- `LiveStartupRequirement`
- `LiveSourceStatus`
- `LiveSourceKind`

Rationale:

This preserves one authoritative policy owner and prevents policy duplication across DI, UI, service control, or sensor-specific selectors.

### 2A. `FlightDataRepository.Source` remains a live-vs-replay axis

`FlightDataRepository.Source` keeps only `LIVE` and `REPLAY`.

Decision details:

- phone-backed live and Condor-backed live both publish under `Source.LIVE`
- replay remains the only producer using `Source.REPLAY`
- simulator-aware downstream behavior uses `LiveSourceKind`, not a widened repository source enum
- this work must not add `CONDOR`, `SIMULATOR`, or a similar third repository source value

Rationale:

This preserves the existing SSOT authority split used by replay-sensitive consumers while keeping simulator classification on a narrower runtime seam.

### 2B. `LiveSourceStatus` uses typed degraded reasons

`LiveSourceStatus` uses typed source-specific degraded reasons rather than raw strings.

Decision details:

- phone degraded states expose typed phone reasons
- Condor degraded states expose typed Condor reasons
- UI maps those typed reasons to user-facing wording
- runtime does not expose free-form degraded reason strings on this cross-feature seam

Rationale:

This keeps status contracts stable for tests, UI mapping, and future source additions without turning runtime status into ad hoc string policy.

### 3. App is a lifecycle / permission actuator and capability adapter only

`app` and platform/service code apply already-resolved policy only.

They may:

- request Android permission when required by resolver output
- implement `PhoneLiveCapabilityPort` as a narrow platform-edge adapter
- implement `VarioRuntimeControlPort` for map/replay callers
- translate resolver outputs into internal phone or Condor runtime actions

They may not:

- decide which live source should be active
- decide degraded-state fallback policy
- compute independent startup rules

Rationale:

This keeps policy in runtime and keeps app at the platform edge.

`VarioRuntimeControlPort` remains the only public start/stop seam. Do not add
public `PhoneLiveRuntimeControlPort` or `CondorLiveRuntimeControlPort`.

That seam also stays caller-agnostic:

- keep the semantic contract as "ensure the resolver-selected live runtime is active"
- treat `ensureRunningIfPermitted()` as a compatibility name, not as phone-GPS-only policy authority
- do not push runtime policy types through the map-facing API
- app implementation may read runtime-derived actuation state internally
- source-specific phone or Condor actuation remains internal fan-out

### 3A. Bridge selection persistence and control stay simulator-owned

A narrow simulator-owned bridge control seam, such as
`CondorBridgeControlPort`, owns settings-triggered bridge actions.

Decision details:

- selected bridge persistence belongs to `feature:simulator`
- bridge actions such as select / clear / connect / disconnect belong to
  simulator ownership
- `connect()` is the only manual start / retry action; reconnect waiting /
  exhausted remain simulator-owned state, not a separate public retry command
- the persisted selected bridge is the only allowed reconnect target
- changing or clearing selection cancels any queued reconnect and forgets the
  previous target immediately
- transient disconnect, stale stream, or reconnect exhaustion do not clear the
  selected bridge automatically
- missing or unbonded persisted bridge yields blocked recovery state until the
  user explicitly clears or re-picks it
- retry/backoff timers, counters, and state transitions stay inside simulator
  runtime ownership
- this seam is not a second public runtime-control API beside
  `VarioRuntimeControlPort`

Rationale:

This keeps bridge lifecycle close to transport/session ownership while avoiding
another public runtime-control surface for map or replay callers.

### 3A.1 `CondorLiveStatePort` stays contract-parity with simulator output

During the bridge-ownership slice, `CondorLiveStatePort` must only advertise
state that the simulator owner actually produces.

Decision details:

- parser-level UTC fields or richer degraded reasons are not declared early on
  the cross-feature read seam
- if a field or degraded reason is added to the port, the simulator owner must
  produce it in the same slice

Rationale:

This prevents runtime from depending on placeholder cross-module contract
surface that has no concrete owner yet.

### 3B. Phone device health remains separate from live-source truth

Phone-local health and restart diagnostics are not the same responsibility as
live-source truth.

Decision details:

- user-facing live-source status belongs to `LiveSourceStatus`
- ownship truth belongs to fused live `FlightDataRepository.flightData.gps`
- phone-local GPS enabled state, permission state, and restart heuristics stay
  in a dedicated map-local phone-health seam
- `LocationSensorsController` may consume that local seam for restart and
  diagnostics only
- phone-health reads must not decide `EffectiveLiveSource`, Condor degraded
  policy, or ownship fallback

Rationale:

This prevents `feature:map` from becoming a mixed owner of both source truth
and phone-device diagnostics.

### 4. Condor monotonic freshness is simulator-owned

Condor stream health uses injected Android monotonic receive time captured at the Android bridge boundary.

Decision details:

- monotonic receive time is the freshness/staleness authority
- payload NMEA UTC is wall-time payload only
- `feature:simulator` owns stream freshness and session health
- replay time remains replay-owned and isolated

Rationale:

This avoids wall-time drift becoming a hidden runtime health authority and prevents freshness logic from splitting across modules.

### 5. `CONDOR2_FULL` ownship truth is fused flight data only

When `EffectiveLiveSource == CONDOR2`:

- aircraft marker position / track / heading / bearing truth come from fused live `FlightDataRepository.flightData.gps`
- phone orientation may drive camera/display behavior only
- phone compass fallback for Condor ownship is forbidden
- Condor disconnect does not silently switch ownship truth to phone GPS

Rationale:

This preserves fused flight data as the aircraft-truth seam and prevents hidden mixed-authority behavior.

### 6. Runtime exposes classification, not downstream feature policy

`feature:flight-runtime` may expose `LiveSourceKind` so downstream features can suppress simulator-inappropriate behavior.

`feature:flight-runtime` must not own `feature:igc` / `feature:weglide` feature policy directly.

Rationale:

This keeps runtime generic and prevents downstream feature concerns from being pulled inward.

## Consequences

### Positive

- single owner for live-source policy
- preserved dependency direction toward stable runtime seams
- no profile back-edge into runtime
- no app/service-owned source policy
- clear ownership for Condor transport and stream health
- explicit Condor heading authority and no silent phone fallback
- downstream features can adapt through classification rather than runtime-owned behavior

### Trade-offs

- more explicit port surface in `feature:flight-runtime`
- more explicit simulator-owned bridge state/control surface
- some existing direct callsites must be rewired before feature behavior is considered complete
- app/service code becomes thinner and less permissive as an ad hoc policy surface

## Rejected Options

### Rejected: split selection policy across separate sensor / airspeed / status selectors

Reason rejected:

- duplicates source-selection policy
- makes status and DI decisions drift independently
- weakens cohesion by turning runtime into a policy bag rather than a single-resolver seam

### Rejected: app / service layer decides active source policy

Reason rejected:

- inverts dependency direction
- moves business/runtime policy to the platform edge
- encourages implicit fallback and startup heuristics outside runtime

### Rejected: adding a second public runtime-control seam

Reason rejected:

- duplicates caller-facing start/stop ownership
- weakens the stable map/replay boundary already provided by `VarioRuntimeControlPort`
- encourages source-specific public control APIs instead of keeping actuation internal

### Rejected: one mixed map seam for live-source status and phone diagnostics

Reason rejected:

- gives one map-facing owner two unrelated responsibilities
- makes Condor user-facing state vulnerable to phone-local health heuristics
- hides whether a given status read is source truth or just local diagnostics

### Rejected: direct runtime dependency on `feature:profile`

Reason rejected:

- introduces a back-edge from runtime to settings persistence
- breaks modular seam clarity
- encourages SSOT drift for desired mode

### Rejected: phone compass or phone GPS as implicit Condor fallback

Reason rejected:

- creates mixed authority for ownship truth
- hides degraded-state behavior
- makes `CONDOR2_FULL` behave differently from the user's selected mode without explicit state

## Validation / Proof Required

Implementation must include:

- boundary-lock tests proving no direct `feature:flight-runtime -> feature:profile` dependency
- boundary-lock tests proving no direct `feature:flight-runtime -> feature:map` dependency
- boundary-lock tests proving runtime start/stop still goes through `VarioRuntimeControlPort`
- boundary-lock tests proving `VarioRuntimeControlPort` public API stays
  caller-agnostic
- tests proving no duplicate desired-mode owner exists
- tests proving no separate selection logic exists outside `LiveSourceResolver`
- tests proving no third `FlightDataRepository.Source` value is introduced for Condor
- tests proving Condor-backed live samples still publish through `FlightDataRepository.Source.LIVE`
- tests proving runtime status uses typed degraded reasons rather than free-form strings
- tests proving phone-device diagnostics are separate from user-facing live
  source status
- integration tests proving Condor ownship uses fused live `flightData.gps`
- orientation tests proving phone orientation never becomes Condor ownship fallback
- replay tests proving replay remains authoritative while active
- degraded-state tests proving Condor disconnect does not silently revert to phone GPS

## Follow-up Work

- promote this draft to an accepted ADR once the wording is ratified
- update `PIPELINE.md` to align the live-source pipeline description with this ADR
- apply the bypass-removal matrix from the Condor boundaries note during implementation
