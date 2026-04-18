# Condor 2 Boundaries and Source Routing

## Purpose

This note is the authoritative seam document for Condor 2 planning. It exists
to prevent the implementation from "working" by weakening architecture.

## Boundary decisions

### 1) Condor is a live-source path, not a replay path

Condor must feed the existing live fused pipeline.

Do:

- route Condor into the selected live sensor and airspeed seams
- let map ownship continue to consume fused `flightData.gps`

Do not:

- reuse `MapLocationFlightDataRuntimeBinder`
- call replay-location update APIs for Condor
- treat Condor as replay because it originates on a PC

### 2) Desired mode persistence is not owned by runtime

The desired live mode is a user preference and must be persisted by the
settings/profile side of the app.

Runtime ownership starts after that:

- runtime resolves effective source
- runtime exposes status and availability
- runtime does not become the persistence owner

### 3) `feature:flight-runtime` must expose narrow seams

Use separate seams for:

- selected live sensor source
- selected live airspeed source
- effective live source
- source-aware status
- startup requirements

Do not introduce one broad `bundle`, `runtime dependencies`, or
service-locator-style owner that hands out everything.

### 4) `feature:simulator` owns Condor runtime

`feature:simulator` should own:

- bridge transport
- line parsing
- Condor 2 sentence mapping
- session health and staleness

It should not own:

- map rendering
- replay shell logic
- profile persistence
- WeGlide or IGC policy

### 5) Ownship heading authority must be explicit

In `CONDOR2_FULL`, ownship pose truth comes from simulator-fed fused flight
data.

Allowed:

- phone orientation as a camera or display-control input if it does not replace
  ownship truth

Forbidden:

- letting phone compass/attitude silently become aircraft-heading authority in
  Condor mode

### 6) IGC and WeGlide keep their own policy

`feature:flight-runtime` may expose a source classification such as:

- `PHONE`
- `SIMULATOR_CONDOR2`
- `REPLAY`

But runtime must not own:

- recording/upload eligibility rules
- WeGlide prompt policy

Those decisions stay in the consuming features.

### 7) No silent fallback to phone GPS

If Condor disconnects while `CONDOR2_FULL` is selected:

- effective source remains Condor-selected but degraded
- UI shows disconnected / stale / waiting state
- phone GPS does not silently take over

Reason:

- silent fallback hides source drift and produces misleading map truth

## Canonical ownership map

| Responsibility | Owner | Why |
|---|---|---|
| user preference for live mode | settings/profile owner | user-owned persisted choice |
| Condor connection state | simulator runtime | transport-local fact |
| effective live source | flight-runtime resolver | cross-source orchestration |
| current fused flight sample | `FlightDataRepository` | existing SSOT |
| replay location suppression path | map replay binder | existing replay-only seam |
| source-aware banner text | map/viewmodel mapping | pure UI projection |

## Existing callsites that must change

Current phone-only assumptions that implementation must remove or narrow:

- `app/.../VarioForegroundService.startIfPermitted()`
- `app/.../ForegroundServiceVarioRuntimeController.ensureRunningIfPermitted()`
- `feature/map/.../MapSensorsUseCase.gpsStatusFlow`
- `feature/map/.../SensorFusionModule.provideSensorFusionRepository(...)`
- `feature/map/.../WindSensorModule.provideLiveSensorDataSource(...)`

These are not bugs in today’s phone-only flow. They are the exact ownership
pressure points for Condor.

## Canonical after-state routing

### Phone live mode

```text
UnifiedSensorManager
-> phone live source adapter
-> selected live SensorDataSource
-> SensorFusionRepository
-> FlightDataRepository(Source.LIVE)
```

### Condor 2 full mode

```text
Condor bridge transport
-> simulator parser/runtime owner
-> Condor sensor source adapter
-> selected live SensorDataSource
-> SensorFusionRepository
-> FlightDataRepository(Source.LIVE)
```

### Replay mode

```text
Replay runtime
-> Replay source owners
-> FlightDataRepository(Source.REPLAY)
-> replay map binder only when replay suppresses live GPS
```

## Architecture fit check

This routing aligns with current repo rules because:

- map remains a consumer
- runtime boundaries point inward
- replay keeps its dedicated binder seam
- `FlightDataRepository` remains the only fused-flight SSOT
- no new `feature:flight-runtime -> feature:map` back-edge is needed

## Practical takeaway

The clean implementation is not "make map read Condor data." The clean
implementation is "make Condor become the selected live source feeding the
existing fused flight-data path."
