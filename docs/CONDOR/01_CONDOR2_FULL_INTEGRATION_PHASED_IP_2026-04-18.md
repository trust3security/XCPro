# Condor 2 Full Integration Phased IP

## 0) Metadata

- Title: Condor 2 full live-source integration for XCPro
- Owner: Codex planning pass
- Date: 2026-04-18
- Status: Draft

## 1) Problem statement

XCPro currently assumes phone GPS is the live ownship source. Condor 2 support
must let XCPro run on Android while Condor runs on Windows and treat the
simulator aircraft as the selected live aircraft.

The implementation must preserve:

- one fused live pipeline
- one flight-data SSOT
- replay determinism
- inward dependency direction

## 2) Scope

In scope:

- Condor 2 first
- ownship from `GGA` / `RMC`
- instrument inputs from `$LXWP0`
- selected live-source routing
- source-aware startup, status, and permission policy
- replay coexistence rules
- simulator-side-effect policy for IGC and WeGlide consumers

Out of scope:

- Condor 3 implementation
- auto-detection of arbitrary simulator versions
- multi-simulator support
- desktop XCPro
- production USB and BLE permutations in v1

## 3) Architecture contract

### 3.1 SSOT ownership

| Data / state | Authoritative owner | Notes |
|---|---|---|
| latest fused flight sample | `FlightDataRepository` | unchanged SSOT |
| desired live mode | persisted settings owner in `feature:profile` | user preference |
| effective live source | resolver in `feature:flight-runtime` | derives from desired mode and runtime availability |
| selected live sensor source | dedicated selector in `feature:flight-runtime` | narrow port, not dependency bag |
| selected live airspeed source | dedicated selector in `feature:flight-runtime` | separate seam from sensor source |
| Condor session / stream health | `feature:simulator` | raw runtime state |
| source-aware UI status | derived use case in `feature:flight-runtime` | map consumes, does not invent |

### 3.2 Module ownership

- `feature:simulator`
  - Condor transport
  - line framing and parser use
  - Condor 2 runtime repository
  - sentence mapping
  - stream freshness and reconnect state
- `feature:flight-runtime`
  - live-source ports
  - source selection and effective-source resolution
  - source-aware status model
  - selected live sensor and airspeed seams
- `feature:map`
  - map rendering
  - replay binders
  - map-specific UI status rendering
  - no Condor socket/parser ownership
- `app`
  - Android foreground-service entrypoint
  - mode-aware permission gate
- `feature:profile`
  - persisted desired live mode setting
- `feature:igc` and `feature:weglide`
  - their own simulator-session policy decisions based on source kind

### 3.3 Explicit non-goals

Do not:

- reuse `MapLocationFlightDataRuntimeBinder` for Condor
- introduce a broad `selected live runtime bundle`
- leave ownship heading authority ambiguous in Condor mode
- make `feature:variometer` the long-term owner of full simulator runtime
- let runtime own WeGlide or IGC upload policy

## 4) Canonical after-state flows

Phone mode:

```text
UnifiedSensorManager
-> selected live SensorDataSource
-> SensorFusionRepository
-> FlightDataRepository(Source.LIVE)
-> Map/ViewModels/UI
```

Condor 2 mode:

```text
Condor bridge transport
-> Condor line framer/parser
-> Condor runtime repository
-> selected live SensorDataSource + selected live AirspeedDataSource
-> SensorFusionRepository
-> FlightDataRepository(Source.LIVE)
-> Map/ViewModels/UI
```

Replay mode:

```text
Igc replay runtime
-> Replay source owners
-> FlightDataRepository(Source.REPLAY)
-> replay binder path only when suppressed live GPS is active
```

## 5) Implementation phases

### Phase 0 - Lock boundaries in docs

Goal:

- finish the plan and architecture decision before code

Deliverables:

- this phased IP
- boundaries/source-routing note
- PC bridge plan
- verification plan
- ADR draft

Exit criteria:

- no unresolved ownership questions remain

### Phase 1 - Shared seam extraction

Goal:

- prepare a stable base without Condor behavior yet

Implementation:

- extract reusable NMEA line/checksum utilities to a neutral shared owner
- add narrow live-source ports in `feature:flight-runtime`
- add persisted desired-live-mode setting in `feature:profile`

Tests:

- unit tests for line framing/checksum
- unit tests for desired-mode persistence

Exit criteria:

- no `feature:simulator -> feature:variometer` dependency is needed

### Phase 2 - Live-source selection and status

Goal:

- remove phone-only assumptions from live selection

Implementation:

- add effective live-source resolver
- add selected live sensor source seam
- add selected live airspeed source seam
- add source-aware status model for UI
- refactor map use case to consume source-aware status instead of direct phone
  status

Tests:

- source-selection unit tests
- no-fallback and stale/disconnected status tests

Exit criteria:

- `SensorFusionModule` and `WindSensorModule` no longer bind live source
  directly to `UnifiedSensorManager`

### Phase 3 - Condor 2 runtime

Goal:

- add Condor runtime in the correct module

Implementation:

- create `feature:simulator`
- add Condor bridge transport abstraction
- parse `GGA`, `RMC`, and `$LXWP0`
- implement Condor 2 wind-direction correction
- publish raw Condor stream status and parsed live samples

Tests:

- parser fixtures
- malformed-line tolerance
- stale stream detection
- wind-direction conversion tests

Exit criteria:

- Condor runtime produces clean sensor and airspeed outputs behind ports

### Phase 4 - Runtime wiring and permission policy

Goal:

- make Condor selectable as the authoritative live source

Implementation:

- refactor foreground-service start control to be mode-aware
- allow Condor mode to start without Android location permission
- keep phone mode permission-gated
- wire Condor outputs into selected live sensor and airspeed seams
- ensure fused live `flightData.gps` comes from Condor in `CONDOR2_FULL`

Tests:

- startup-requirement tests
- map ownship location integration tests
- flight-data source routing tests

Exit criteria:

- Android map shows the Condor location, not the phone location

### Phase 5 - UI and status surfaces

Goal:

- expose selection and status cleanly without UI owning runtime logic

Implementation:

- add desired live mode setting UI
- replace phone-only GPS banners with source-aware wording
- expose Condor connection / waiting / stale status
- add diagnostics surface for last sentence family and source mode

Tests:

- ViewModel mapping tests
- source-aware banner text tests

Exit criteria:

- no UI layer reads `UnifiedSensorManager` directly for Condor status

### Phase 6 - Replay and side-effect hardening

Goal:

- keep replay deterministic and simulator sessions non-uploadable by default

Implementation:

- replay suspends live authority cleanly and restores desired live mode after
  replay ends
- IGC and WeGlide consume source kind and suppress auto side effects for
  simulator sessions
- no silent fallback from Condor to phone GPS on disconnect

Tests:

- replay restore tests
- no-record/no-upload simulator tests
- disconnect-state tests

Exit criteria:

- replay and simulator paths remain structurally separate

## 6) Verification plan

Required checks before merge:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted proof:

- `feature:simulator` parser and runtime tests
- `feature:flight-runtime` source-selection tests
- map integration tests for ownship routing
- replay restore regression tests

## 7) Acceptance gates

- no new SSOT is introduced
- Condor ownship enters live fused flight data, not replay binders
- desired live mode persistence has one owner
- live source selection uses narrow seams, not a dependency bag
- heading authority in Condor mode is explicit
- simulator sessions do not auto-upload or auto-prompt
- replay stays deterministic

## 8) Practical acceptance scenario

The minimum product-level proof is:

1. user selects `CONDOR2_FULL`
2. Android phone grants no location permission
3. Condor 2 runs on the PC and streams NMEA through the supported bridge
4. XCPro map shows the Condor aircraft at the simulator location
5. disconnecting Condor does not silently snap XCPro back to phone GPS
