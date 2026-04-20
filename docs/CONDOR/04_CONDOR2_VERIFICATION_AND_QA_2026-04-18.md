# Condor 2 Verification and QA

## Purpose

This note defines the minimum evidence required before Condor 2 support should
be considered architecture-safe and implementation-complete.

## Required automated checks

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Add targeted module proof for:

- `feature:simulator`
- `feature:flight-runtime`
- `feature:map`

## Architecture enforcement proof

These checks are required because the main risk is structure drift, not parser
syntax:

- no `feature:flight-runtime -> feature:profile` back-edge
- no `feature:flight-runtime -> feature:map` back-edge
- no duplicate desired-live-mode owner
- no duplicate persisted bridge-selection owner across simulator, profile, app,
  or map
- no source-selection policy outside `LiveSourceResolver`
- no competing public runtime-control seam beside `VarioRuntimeControlPort`
- no third `FlightDataRepository.Source` value for Condor
- no free-form degraded reason strings on `LiveSourceStatus`
- no replay-binder reuse for Condor
- no phone-compass fallback as Condor ownship truth

## Unit-test matrix

### Parser and runtime

- `GGA` parsing
- `RMC` parsing
- `$LXWP0` parsing
- malformed line tolerance
- partial line framing
- Condor 2 wind-direction conversion
- stale stream detection
- disconnect and reconnect state transitions

### Resolver and state contracts

- desired live mode persistence through `DesiredLiveModePort`
- phone capability input exposed only through `PhoneLiveCapabilityPort`
- Condor bridge/session input exposed only through read-only `CondorLiveStatePort`
- effective source resolution
- selected sensor source derivation
- selected airspeed source derivation
- startup requirements by mode
- source kind derivation
- typed phone degraded reason derivation
- typed Condor degraded reason derivation
- no silent fallback to phone when Condor disconnects
- source-aware status derivation

### Bridge UX contract

- persisted bridge selection is owned by simulator-facing state, not profile,
  app, or map mirror state
- bridge mutation actions are owned by a simulator-facing settings seam, not by
  `VarioRuntimeControlPort`
- first-run bridge selection can enumerate the intended bonded bridge choices
- selected bridge survives process restart
- reconnect states surface waiting / attempting / blocked / exhausted
- `Connect` is the only manual start / retry action; no separate retry control
  is required for waiting or exhausted reconnect state
- minimum diagnostics expose selected bridge, active bridge, stream freshness,
  and last failure reason
- transient disconnect or reconnect exhaustion does not clear the selected
  bridge
- missing or unbonded persisted bridge surfaces `BLOCKED` plus explicit clear /
  re-pick recovery
- Condor mode can connect without Android location permission

### Fusion and routing

- selected live sensor source routes into live fusion
- selected live airspeed source routes into wind inputs
- Condor-backed live samples still publish through `FlightDataRepository.Source.LIVE`
- fused live `flightData.gps` reflects Condor ownship in `CONDOR2_FULL`
- replay still gates live updates correctly

### Side effects

- simulator sessions do not auto-record/upload by default
- WeGlide prompt logic rejects simulator sessions by source kind

### Boundary-lock tests

- runtime reads desired mode only through `DesiredLiveModePort`
- runtime reads coarse phone capability only through `PhoneLiveCapabilityPort`
- runtime reads Condor bridge/session state only through `CondorLiveStatePort`
- `CondorLiveStatePort` exposes no bridge mutation methods
- desired-mode writes stay in profile/settings ownership
- map and replay request runtime start/stop through `VarioRuntimeControlPort` only
- no public `PhoneLiveRuntimeControlPort` or `CondorLiveRuntimeControlPort` exists
- no map/replay caller uses the simulator-owned Condor bridge control seam
- `VarioRuntimeControlPort` public API stays caller-agnostic and does not accept
  runtime policy or simulator-specific parameters
- app acts on `LiveStartupRequirement` and `EffectiveLiveSource` only
- map status consumes `LiveSourceStatus`, not direct phone manager state
- phone-device diagnostics remain separate from user-facing live-source status
- map-local phone-health diagnostics do not substitute for `PhoneLiveCapabilityPort`
- `LocationSensorsController` phone-health checks do not drive effective source
  or ownship fallback

## Integration scenarios

### Scenario 0: first-run bridge setup

1. pair the PC-hosted bridge in Android Bluetooth settings
2. open the Condor settings / bridge connection surface in XCPro
3. choose the bonded bridge device
4. connect
5. restart XCPro

Expected result:

- the bridge can be selected from the intended bonded-device list
- XCPro persists the selected bridge
- after restart, XCPro still knows which bridge is selected
- the user does not need to re-pick the bridge for every reconnect
- desired live mode and selected bridge remain separate concepts in the UI and
  state model

### Scenario 0A: persisted bridge becomes unavailable

1. pair and select a Condor bridge
2. restart XCPro once to confirm the bridge is persisted
3. remove or unpair that bridge from Android
4. restart XCPro in `CONDOR2_FULL`

Expected result:

- XCPro does not silently clear the saved bridge on startup
- reconnect state becomes `BLOCKED`
- diagnostics explain why recovery is blocked
- the user can explicitly clear or re-pick the bridge

### Scenario 1: core product proof

1. select `CONDOR2_FULL`
2. do not grant Android location permission
3. connect Condor 2 bridge
4. start a Condor flight near Lake Keepit, NSW
5. verify map ownship appears at the simulator location

Expected result:

- Condor location is visible
- app does not require phone GPS permission to start the Condor runtime
- no replay binder path is involved

### Scenario 2: Condor disconnect

1. start in `CONDOR2_FULL`
2. begin valid live updates
3. interrupt the Condor stream

Expected result:

- UI moves to stale/disconnected state
- ownship does not silently switch to phone GPS
- runtime remains mode-correct and recoverable

### Scenario 3: replay coexistence

1. start in `CONDOR2_FULL`
2. start replay
3. stop replay

Expected result:

- replay becomes authoritative while active
- live Condor source does not bleed into replay
- selected live mode is restored after replay stops

### Scenario 4: heading authority

1. place phone stationary on a desk
2. run `CONDOR2_FULL`
3. turn/fly in the simulator

Expected result:

- ownship pose and heading follow Condor-fed fused flight data
- phone orientation may affect display/camera behavior only
- phone compass does not silently replace simulator ownship truth

### Scenario 5: phone-health isolation

1. start in `CONDOR2_FULL`
2. disable phone GPS provider or make phone-local GPS unhealthy
3. keep Condor stream healthy

Expected result:

- user-facing status remains Condor-driven
- ownship remains Condor-driven
- phone-health diagnostics may appear only in controller or diagnostics surfaces
- no fallback to phone-local status wording or ownship occurs

## UI verification

The UI must prove:

- no phone-only banner text is shown during healthy Condor mode
- degraded Condor text is source-aware, not generic phone-GPS text
- settings clearly expose which live mode is selected
- the bridge connection surface clearly exposes selected bridge and active
  bridge
- the bridge connection surface exposes reconnect state and minimum diagnostics
  without requiring a debug-only screen
- the bridge connection surface exposes explicit clear / re-pick recovery when
  a persisted bridge is no longer available

## Manual QA checklist

- verify first-run bridge pairing + selection + connect flow works end to end
- verify selected bridge persists across app restart
- verify transient disconnect does not erase the saved bridge
- verify `Connect` immediately retries during reconnect `WAITING` and after
  reconnect `EXHAUSTED`
- verify missing/unpaired saved bridge yields `BLOCKED` plus explicit recovery
- verify Condor mode can connect and start without Android location permission
- verify phone mode still behaves exactly as before
- verify replay behavior is unchanged
- verify no new direct UI dependency on sensor managers or simulator runtime
- verify no hidden fallback to phone GPS exists

## Merge gate

Do not merge until all of the following are true:

- Condor ownship is routed through fused live flight data
- desired/effective source ownership is explicit and singular
- Condor remains inside the `FlightDataRepository.Source.LIVE` branch
- replay remains deterministic
- simulator sessions have no automatic network side effects
- architecture seams match the boundaries document in this folder
- boundary-lock tests prove there is no runtime/profile back-edge
