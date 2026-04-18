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

### Source selection and status

- desired live mode persistence
- effective source resolution
- no silent fallback to phone when Condor disconnects
- source-aware status derivation
- startup requirements by mode

### Fusion and routing

- selected live sensor source routes into live fusion
- selected live airspeed source routes into wind inputs
- fused live `flightData.gps` reflects Condor ownship in `CONDOR2_FULL`
- replay still gates live updates correctly

### Side effects

- simulator sessions do not auto-record/upload by default
- WeGlide prompt logic rejects simulator sessions by source kind

## Integration scenarios

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
- phone orientation does not silently replace simulator ownship truth

## UI verification

The UI must prove:

- no phone-only banner text is shown during healthy Condor mode
- degraded Condor text is source-aware, not generic phone-GPS text
- settings clearly expose which live mode is selected

## Manual QA checklist

- verify Condor mode can connect and start without Android location permission
- verify phone mode still behaves exactly as before
- verify replay behavior is unchanged
- verify no new direct UI dependency on sensor managers or simulator runtime
- verify no hidden fallback to phone GPS exists

## Merge gate

Do not merge until all of the following are true:

- Condor ownship is routed through fused live flight data
- desired/effective source ownership is explicit and singular
- replay remains deterministic
- simulator sessions have no automatic network side effects
- architecture seams match the boundaries document in this folder
