# Validation and golden-capture plan

## Why this matters

The target is not “XCPro can read some bytes from Condor”.

The target is:

```text
XCPro receives the same practical flight information XCSoar receives
through the same Condor -> bridge -> phone workflow
```

That means validation must be capture-driven and side-by-side.

## Golden capture strategy

Use the already-working XCSoar / HW VSP3 setup as the ground-truth path.

## Best capture points

### Preferred capture point
Capture the raw TCP text stream sent from the PC bridge to the phone.

Why:
- it is exactly what XCPro will consume
- it is independent of Android UI behavior
- it can be replayed into parser tests

### Alternate capture point
Capture the raw COM/NMEA output before it enters HW VSP3.

Why:
- also valid if the bridge is line-transparent
- useful if the TCP bridge is harder to instrument

## Capture scenarios

Do not capture only a static parked glider.

Capture at least:

1. **Startup / connection**
   - first valid sentences
   - initial timestamps
   - transition to active movement

2. **Straight cruise**
   - stable track
   - speed changes

3. **Turning / circling**
   - heading / track changes
   - vario changes

4. **Climb and sink**
   - several positive / negative vario values

5. **Wind-sensitive segment**
   - enough movement to compare wind interpretation

6. **Disconnect / stop**
   - Condor stop or bridge stop
   - stale timeout behavior

## What to store

Commit fixtures as text resources, for example:

```text
tests/resources/condor/
  startup_stream.txt
  cruise_stream.txt
  circling_stream.txt
  disconnect_stream.txt
```

Also store:
- the Condor version used
- port number
- whether the bridge was HW VSP3
- timestamp of capture
- brief notes about the flight segment

## Parser test coverage

Minimum parser tests should cover:

- valid `LXWP0`
- invalid checksum
- missing fields
- implausible values
- Condor 2 wind reciprocal correction
- TAS interpretation
- altitude mapping behavior

## Transport test coverage

Minimum transport tests should cover:

- bind success on configured port
- bind failure when port is taken
- incoming client connect
- line-stream reading
- remote disconnect
- stale timeout
- restart / reconnect

## Runtime policy tests

Minimum resolver tests should prove:

- Condor mode selects simulator ownship
- phone ownship is not silently mixed in
- state transitions are explicit
- disconnect produces expected state change

## Side-by-side acceptance test

Run the same Condor session and compare XCSoar and XCPro.

## Compare at least these categories

- ownship movement updates
- altitude behavior
- groundspeed / airspeed-related behavior as surfaced by the app
- vario behavior
- wind behavior
- direction / heading behavior as used by the UI

Exact pixel-perfect UI parity is not the goal. Data-path parity is.

## Manual acceptance checklist

Use this as the practical pass/fail list.

### Connection / setup
- [ ] XCPro shows phone IP clearly
- [ ] XCPro shows selected port
- [ ] XCPro reaches listening state
- [ ] PC bridge connects successfully

### Data flow
- [ ] XCPro receives valid Condor stream
- [ ] ownship moves on the map from simulator input
- [ ] position updates are continuous
- [ ] no silent fallback to phone GPS occurs

### Semantics
- [ ] altitude looks sane compared with XCSoar
- [ ] vario looks sane compared with XCSoar
- [ ] wind direction is not obviously reversed
- [ ] heading / directional behavior is consistent with simulator mode expectations

### Robustness
- [ ] disconnect is detected
- [ ] stale stream is surfaced
- [ ] reconnect works without app restart
- [ ] bad port / bind failure is surfaced clearly

### Architecture integrity
- [ ] map feature does not own socket code
- [ ] parser is not in UI
- [ ] simulator ownership is isolated
- [ ] runtime remains SSOT

## Recommended final proof artifact

After implementation, create one short markdown proof doc:

```text
docs/CONDOR/PROOF_CONDOR_TCP_XCPRO_PASS.md
```

Include:
- test environment
- screenshots
- capture files used
- PASS / FAIL by contract
- file/module ownership confirmation
