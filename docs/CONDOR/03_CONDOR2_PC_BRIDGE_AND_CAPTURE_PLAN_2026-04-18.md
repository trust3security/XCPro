# Condor 2 PC Bridge and Capture Plan

## Purpose

Condor runs on Windows while XCPro runs on Android. This note defines the
first supported bridge assumptions for development and the raw-capture plan for
parser/runtime validation.

## First supported validation setup

Target v1 validation path:

```text
Condor 2 on Windows
-> virtual COM output
-> PC bridge process
-> Bluetooth RFCOMM / SPP
-> XCPro on Android
```

Reason:

- Condor already outputs NMEA through serial-style channels
- Android can consume RFCOMM/SPP with established patterns
- this keeps Condor-specific PC bridging outside the Android app’s core fusion
  design

## Bridge responsibilities

The PC bridge is not the source of truth. It is only a transport relay.

Bridge responsibilities:

- read Condor NMEA from a COM endpoint
- forward bytes unchanged to Android transport
- preserve line boundaries
- avoid rewriting sentence semantics

Bridge non-responsibilities:

- do not normalize wind direction
- do not compute derived flight values
- do not decide live-source selection
- do not synthesize missing sensor fields

## Capture fixture plan

Before or during implementation, collect raw Condor 2 captures covering:

- idle on runway
- aerotow / launch
- climb in lift
- straight cruise
- coordinated turn
- final glide
- disconnect / reconnect
- stale stream gap

Store fixture families separately:

- `raw-condor2-good-lines`
- `raw-condor2-malformed-lines`
- `raw-condor2-gap-and-reconnect`
- `raw-condor2-lxwp0-wind-cases`

## Raw capture rules

Capture format should preserve:

- receive order
- raw line bytes or faithful ASCII text
- local receive timestamp
- capture metadata: Condor version, bridge path, baud rate, and scenario

Do not:

- hand-edit lines to make tests pass
- mix Condor 2 and Condor 3 captures in one fixture set
- drop unknown sentence types without recording that they occurred

## Required capture metadata

Each fixture batch should record:

- Condor version
- scenario name
- whether bridge was COM-only, Bluetooth RFCOMM, or another path
- expected sentence families observed
- whether wind was non-zero

## Parser test expectations

Parser/runtime should prove:

- partial or malformed lines do not kill the stream
- unsupported lines are ignored without terminating the session
- `GGA` and `RMC` ownship values remain usable through gaps in `LXWP0`
- `LXWP0` instrument fields can update independently
- Condor 2 wind direction is converted correctly

## Later bridge options

Possible later paths, not required for v1:

- USB-serial based Android ingestion
- desktop helper packaging for the PC bridge
- Condor 3 bridge profile

These should remain transport choices, not reasons to change fused-runtime
ownership.

## Practical takeaway

Keep the PC bridge dumb. The Android app should own protocol meaning and source
selection. The bridge should only get Condor bytes onto the Android device
reliably enough to validate the runtime.
