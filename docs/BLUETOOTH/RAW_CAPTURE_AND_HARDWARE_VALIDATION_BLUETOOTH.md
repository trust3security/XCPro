# Raw Capture and Hardware Validation - LXNAV S100 Bluetooth

## Purpose

This document defines the minimum real-hardware validation needed before XCPro Bluetooth support for LXNAV S100 is considered release-grade.

The goal is to confirm that the implementation assumptions match what the actual instrument emits.

---

## 1) What must be validated on real hardware

### 1.1 Device identity and pairing
Capture:
- Bluetooth device name as shown on Android
- whether the device is discoverable only when enabled from the instrument
- pairing/PIN behavior
- whether the device appears as a bonded classic serial device

### 1.2 Transport behavior
Capture:
- whether XCPro connects through bonded-device RFCOMM/SPP
- whether the stream is newline-delimited ASCII
- whether there are any binary bytes or framing quirks
- whether reconnect after power-cycle behaves cleanly

### 1.3 Sentence inventory
Capture at least 30 to 60 seconds of raw output and list:
- all sentence types observed
- approximate update rate of each sentence
- whether checksums are always present
- whether some sentences contain blank optional fields

### 1.4 Field sanity
Confirm with bench observation where practical:
- barometric altitude updates make sense
- vario values move plausibly
- wind fields appear only when expected
- device/info sentence appears and can be parsed safely

---

## 2) Minimum capture procedure

1. Pair the S100 with the Android test device.
2. Enable the Bluetooth output mode needed on the S100.
3. Connect using the XCPro Bluetooth implementation or a trusted serial-terminal app if needed for baseline.
4. Capture 30 to 60 seconds of raw stream while the device is stable.
5. If possible, also capture:
   - a disconnect/reconnect cycle,
   - a device power-cycle reconnect,
   - a short period with no useful motion change,
   - a period with changing altitude/vario if bench simulation is available.

---

## 3) Sanitization rules for fixture files

If raw capture is added to the repo as parser fixtures:
- remove MAC addresses
- remove serial numbers if considered sensitive
- remove pilot-identifying metadata if present
- keep sentence order, spacing, commas, and checksums intact
- do not hand-edit fields unless the sanitization rule is explicit and documented

Preferred fixture format:
- one sentence per line
- original line endings normalized to LF in fixture files
- accompanying short note describing source hardware and firmware version if known

---

## 4) Parser-expansion checklist after real capture

After the first real capture, check whether the parser or transport needs adjustment for:
- blank-field tolerance
- sentence order assumptions
- checksum policy
- extra LX or standard NMEA sentences
- unexpected line endings or control characters
- reconnect edge cases after socket loss

Do not widen scope casually.
Only add parser support for newly observed sentences if they are actually needed for v1 behavior.

---

## 5) Pass criteria for release-grade validation

The hardware validation passes when all are true:
- real S100 connects successfully through the intended transport path
- observed sentence stream matches implementation assumptions closely enough to be safe
- parser handles the real capture without crashing
- at least one sanitized fixture from real hardware backs tests if feasible
- disconnect fallback works cleanly
- connection-state and diagnostics UI reflect reality during bench validation

---

## 6) Fail conditions

Validation fails if any of these occur:
- real device is not using the transport path assumed by the implementation
- sentence framing differs materially from the implementation assumption
- parser fails on common real sentences
- reconnect path is unstable or leaves the app in a false connected state
- fallback to phone values does not happen reliably after disconnect or stale data

---

## 7) Notes to record during bench session

Record these in the PR or validation notes:
- test device model and Android version
- S100 firmware version if visible
- whether the stream required special instrument settings
- sentence types seen
- any observed mismatch from the original plan
- whether follow-up scope is needed for a later phase

---

## 8) Strong recommendation

Do not skip this step.

A clean architecture and passing unit tests are not enough for external-device work.
Real capture is what separates a plausible implementation from a reliable one.
