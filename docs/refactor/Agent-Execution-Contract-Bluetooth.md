# AGENT.md - Bluetooth Execution Contract (LXNAV Hawk/S100)

This document is the single source of truth for implementing Bluetooth Classic SPP connectivity
for LXNAV Hawk/S100 in XCPro. It embeds the full Bluetooth plan verbatim to avoid omissions.

---

## 0) Agent Execution Contract (Read First)

### 0.1 Authority
- Do not ask for permission (no "If you want, I can proceed...").
- Proceed through all phases automatically.
- Only ask a question if truly blocked by missing information that prevents compilation/tests,
  or if an architecture rule is unclear/missing.
- If ambiguity exists, choose the most reasonable repo-consistent option and document the
  assumption in Section 5 (Notes / ADR).

### 0.2 Responsibilities
- Implement the change described in Section 1 and the full plan in Section 7.
- Run builds, unit tests, and lint locally.
- Fix all build/test/lint failures encountered.
- Preserve existing user-visible behavior unless explicitly stated otherwise.
- Keep business logic pure and unit-testable (no Android framework calls in core logic unless required).
- Prefer deterministic, injectable time sources:
  - Use monotonic time for staleness/elapsed logic.
  - Use wall time only for display timestamps.
  - Do not mix time sources in a single decision path.
- Errors are values. Do not swallow exceptions; expose errors in state.
- Parsers must be pure and unit-tested.
- Ownership/override logic belongs in a single UseCase.
- Do not refactor unrelated parts of the app.

### 0.3 Workflow Rules
- Work phase-by-phase, in order.
- Run required Gradle commands after each phase.
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or updated requirements
  (cite Section 1) and record it in Section 5.

### 0.4 Definition of Done
Work is complete only when:
- All phases in Section 2 and Section 7 are implemented.
- Acceptance criteria in Section 3 are satisfied.
- Required commands in Section 4 pass.
- Any non-trivial decisions are recorded in Section 5.

---

## 1) Change Request

### 1.1 Feature Summary (1-3 sentences)
Add Bluetooth Classic SPP connectivity to XCPro to connect to paired LXNAV Hawk/S100 devices,
consume NMEA sentences ($LXWP0, $LXWP1, optional GPRMC/GPGGA), parse them robustly, and
integrate external baro/vario data into the SSOT pipeline without breaking architecture.

### 1.2 User Stories / Use Cases
- As a pilot, I want to select a paired LXNAV device and connect so the app uses external baro altitude and vario.
- As a user, I want to see connection state, sentence rate, and last-received time so I can verify the link is alive.
- As a tester, I want robust parsing and fallback to phone sensors when disconnected.

### 1.3 Non-Goals (explicitly out of scope)
- Bluetooth scanning; use only bonded devices.
- Unrelated refactors or architectural changes.
- Business logic in UI or data transport layers.
- Blending external and phone values for the same field.
- Treating IAS as pitot-based without device verification.

### 1.4 Constraints
- Follow MVVM + UDF + SSOT with Hilt DI and coroutines/Flow.
- Parsers are pure and unit-tested; UseCases own ownership/override logic.
- Android 12+ permissions: require BLUETOOTH_CONNECT; avoid BLUETOOTH_SCAN by not scanning.
- Time base rules: monotonic for staleness/elapsed; wall time only for UI/output.

### 1.5 Inputs / Outputs
- Inputs: Bluetooth Classic SPP NMEA stream, newline-delimited ASCII sentences.
- Outputs: ExternalInstrumentSnapshot, connection state, metrics (checksum/parse/reconnect counts),
  sentence rate, last-received time, UI status display.

### 1.6 Behavior Parity Checklist (if refactor or replacements)
- When external device disconnects, fall back to phone sensors.
- External data overrides only fields it provides; no blending for the same field.

---

## 2) Execution Plan (Authoritative Summary)
This section summarizes the plan. The full plan is included verbatim in Section 7.

- Reference material: docs/bluetooth.md, docs/lxnav_nmea_examples.md; XCSoar for buffering/reconnect/checksum behavior.
- Document SSOT ownership flow and Bluetooth connection state machine.
- Phase 1: SPP transport and line buffering with reconnect, cancellable IO, no manual threads.
- Phase 2: LXNAV parsing + checksum handling; parser returns structured errors, not exceptions.
- Phase 3: External instrument repository + ownership policy UseCase; monotonic time only for staleness.
- Phase 4: Devices UI to connect/disconnect and show status; optional raw log buffer.
- Phase 5: Hardening for malformed lines, reconnects, metrics.

---

## 3) Acceptance Criteria

### 3.1 Functional Acceptance Criteria
- User can select a paired Bluetooth device (MAC/name) and connect.
- App shows connection state: DISCONNECTED / CONNECTING / CONNECTED / ERROR.
- App shows sentence rate (lines/sec) and last-received timestamp.
- App receives NMEA lines and parses at least:
  - $LXWP0 -> vario, baro altitude, optional IAS, heading, wind dir/speed when present.
  - $LXWP1 -> store raw fields (optional), display device info string if available.
- When external instrument is connected, external baro altitude and vario override phone-derived values (default policy).
- When device disconnects, app automatically falls back to phone sensors.

### 3.2 Non-Functional
- No crashes on malformed lines, missing checksum, bad checksum, long runs of commas/empty fields,
  Bluetooth drops or reconnects.

### 3.3 Test Coverage Required
- Line splitter/buffer unit tests.
- Parser unit tests (valid, missing checksum, bad checksum).
- Selection/ownership UseCase unit tests (override, partial override, disconnect fallback).

---

## 4) Required Checks (Agent Must Run and Pass)
- ./gradlew clean
- ./gradlew testDebugUnitTest
- ./gradlew lintDebug
- ./gradlew assembleDebug

---

## 5) Notes / ADR (Architecture Decisions Record)
If any non-trivial decision is made, record it here:
- Decision:
- Alternatives considered:
- Why chosen:
- Impact / risks:
- Follow-ups:

---

## 6) Agent Output Format (Mandatory)
At the end of each phase, the agent outputs:

## Phase N Summary
- What changed:
- Files touched:
- Tests run:
- Results:
- Next:

At the end of the task, include:
- Final Done checklist (Definition of Done items)
- PR-ready summary (what/why/how)
- How to verify manually (2-5 steps)

---

## 7) Full Bluetooth Plan (Verbatim, Do Not Omit)

# XCPro Bluetooth (LXNAV Hawk/S100) Implementation Plan for Codex

> **Goal:** Add reliable Bluetooth Classic **SPP** connectivity to XCPro, ingest LXNAV NMEA sentences (`$LXWP0`, `$LXWP1`, optional `GPRMC/GPGGA`), and integrate them into XCPro's existing SSOT + UseCase pipeline **without breaking architecture**.
>
> **This plan is written for Codex to execute autonomously.**

---

## 0) Ground Rules (Non-Negotiable)

### Autonomy / No-Questions Policy
- **Do not ask for permission** (no "If you want, I can proceed...").
- Proceed through all phases automatically.
- Only ask a question if truly blocked by missing information that prevents compilation/tests, or if an architecture rule is unclear/missing.
- In that case, ask **exactly one** question with **A/B** options, then stop.

### Definition of Done
A change is done only when ALL pass:
- `./gradlew clean`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

### Scope Guardrails
- Follow existing XCPro architecture and coding policy (SSOT, UseCases, UDF, DI).
- **Parsers must be pure and unit-tested.**
- **Ownership/override logic belongs in a single UseCase**, not in parsers or transport.
- Do not refactor unrelated parts of the app.

---

## 1) Inputs / Reference Material

Use these docs in-repo:
- `docs/bluetooth.md` (authoritative approach: SPP + NMEA, XCSoar reference)
- `docs/lxnav_nmea_examples.md` (example strings for unit tests)

Use XCSoar codebase as external reference only for:
- Robust line buffering (partial lines)
- Reconnect behavior
- Checksum handling
- LXWP field tolerance (blank commas)

---

## 2) Acceptance Criteria (What "Working" Means)

### Functional
1. User can select a **paired Bluetooth device** (MAC/name) and connect.
2. App shows connection state:
   - DISCONNECTED / CONNECTING / CONNECTED / ERROR
   - Sentence rate (lines/sec) and last-received timestamp.
3. App receives NMEA lines and parses at least:
   - `$LXWP0` -> vario, baro altitude, optional IAS, heading, wind dir/speed when present
   - `$LXWP1` -> store raw fields (optional), display "device info" string if available
4. When external instrument is connected:
   - External **baro altitude** and **vario** override phone-derived values (default policy).
5. When device disconnects:
   - App automatically falls back to phone sensors.
6. All required Gradle tasks pass.

### Non-Functional
- No crashes on:
  - malformed lines
  - missing checksum
  - bad checksum
  - long runs of commas / empty fields
  - Bluetooth drops / reconnects

---

## 3) Proposed File/Package Layout (Adjust to Match XCPro)

> If XCPro already has a "data sources" module/package, place these accordingly.

### Data Layer (transport + parsing)
- `data/external/bluetooth/`
  - `BluetoothSppClient.kt` - interface for SPP transport (testable with fakes)
  - `BluetoothSppClientImpl.kt` - RFCOMM socket connect + byte stream reader + reconnect loop
  - `NmeaLine.kt` - raw line + monotonic receive time
  - `BluetoothConnectionState.kt` - state model with error payload (DISCONNECTED/CONNECTING/CONNECTED/ERROR)
  - `BluetoothError.kt` - sealed error model for connect/IO/permission/unknown failures
  - `PairedBluetoothDevice.kt` - name + MAC
  - `BluetoothDeviceProvider.kt` - list bonded devices (no scanning required)

- `data/external/lxnav/`
  - `LxnavParser.kt`  -  pure parser for `$LXWP0`, `$LXWP1`, optional `RMC/GGA`
  - `NmeaChecksum.kt`  -  checksum validator helper (optional)

### Domain Layer (policy + selection)
- `domain/external/`
  - `ExternalInstrumentSnapshot.kt`  -  normalized values (SI units) + quality flags + timestamps
  - `ExternalInstrumentRepository.kt`  -  SSOT state flow of latest snapshot + status (including last error + metrics)
  - `SelectFlightInputsUseCase.kt`  -  ownership rules (external overrides phone for configured fields)

### DI
- `di/ExternalDeviceModule.kt`  -  provides Bluetooth client + repositories

### UI
- `ui/settings/devices/`
  - `DevicesViewModel.kt` + intents/state
  - `DevicesScreen.kt`  -  select paired device + connect/disconnect + show status

---

## 3A) SSOT Ownership Flow (External Inputs)

External NMEA lines -> ExternalInstrumentRepository (SSOT) -> SelectFlightInputsUseCase (ownership merge)
-> FlightDataCalculatorEngine / FlightDataRepository -> ViewModels -> UI + Audio

## 3B) Bluetooth Connection State Machine

States: DISCONNECTED, CONNECTING, CONNECTED, ERROR.

Allowed transitions:
- DISCONNECTED -> CONNECTING
- CONNECTING -> CONNECTED | ERROR | DISCONNECTED
- CONNECTED -> DISCONNECTED | ERROR
- ERROR -> DISCONNECTED | CONNECTING (retry)

## 4) Phase Plan (Execute Sequentially, No Check-ins)

### Phase 1  -  Bluetooth Classic SPP Transport (No Parsing Yet)
**Objective:** Connect to a paired device and stream newline-terminated ASCII lines.

Tasks:
1. Implement `BluetoothDeviceProvider`:
   - Returns bonded devices (name + MAC).
2. Implement `BluetoothSppClient`:
   - Connect via SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`
   - Read bytes from `InputStream`
   - Buffer partial lines; emit on `\n`
   - Guard buffer size (reset if > 4KB garbage)
   - Reconnect with exponential backoff
   - Use coroutines/Flow on Dispatchers.IO (no manual threads)
   - Blocking reads must be cancellable; close socket/streams on cancel
3. Create minimal repository/state:
   - `BluetoothConnectionState`
   - `StateFlow` for state, lastLineTime, linesPerSecond, lastError (as data), and error counters
4. Add unit tests where possible:
   - Line splitter/buffer tests (feed byte chunks; assert lines)

Verification:
- Add a debug screen or log output that shows raw lines.
- Run required Gradle commands.

---

### Phase 2  -  LXNAV Parsing + Unit Tests (Pure Code)
**Objective:** Parse `$LXWP0` robustly using example strings. Validate checksum if present.

Tasks:
1. Implement `NmeaChecksum`:
   - If `*CS` present -> validate XOR checksum
   - If checksum absent -> accept line (configurable; default accept)
   - On invalid checksum or malformed line, return a structured error (do not throw)
2. Implement `LxnavParser.parse(line)` (return ParseResult with success/error):
   - Handle `$LXWP0`:
     - tolerate blanks
     - parse: IAS(kph), baroAlt(m), vario(m/s), heading(deg), windDir(deg), windSpeed(kph) when present
   - Handle `$LXWP1`:
     - store raw fields (for now) + optionally extract minimal identity string
   - Ignore other sentences safely
3. Add unit tests using `docs/lxnav_nmea_examples.md` strings:
   - `$LXWP0` valid checksum
   - `$LXWP0` missing checksum
   - `$LXWP0` bad checksum rejected
   - `$GPRMC/$GPGGA` ignored or parsed if implemented

Verification:
- Run required Gradle commands.

---

### Phase 3  -  External Instrument SSOT Repository + Merge Policy UseCase
**Objective:** Turn parsed messages into a single snapshot and decide what overrides phone sensors.

Tasks:
1. Implement `ExternalInstrumentRepository`:
   - Consumes `Flow<NmeaLine>` from transport
   - Parses lines
   - Maintains `StateFlow<ExternalInstrumentSnapshot?>`
   - Maintains connection state + stats (last update time, sentence rate)
2. Define `ExternalInstrumentSnapshot` fields (minimum) - timestamps are monotonic only:
   - `baroAltM: Double?`
   - `varioMps: Double?`
   - `iasKph: Double?`
   - `headingDeg: Double?`
   - `windDirDeg: Double?`
   - `windSpeedKph: Double?`
   - `receivedElapsedNanos: Long` (from elapsedRealtimeNanos)
3. Implement `SelectFlightInputsUseCase` (ownership rules):
   - Default policy: if external provides `baroAltM` and `varioMps`, use them
   - Otherwise fall back to phone sources
   - Do not blend the same variable
4. Enforce time base rules:
   - Staleness checks use monotonic time only
   - Wall time is for UI labels/output only
5. Add unit tests for selection logic:
   - External connected overrides
   - External missing some fields only overrides those present
   - Disconnect falls back

Verification:
- Run required Gradle commands.

---

### Phase 4  -  UI: Devices Screen + Debugging
**Objective:** User can pick the paired LXNAV device, connect, see status and last received data.

Tasks:
1. Add `DevicesScreen`:
   - list paired devices
   - connect/disconnect button
   - show connection state, sentence rate, last line time
2. Add "Raw NMEA logging" toggle (recommended):
   - writes last N lines to a rolling buffer in memory
   - optional: export to file later (not required now)
3. Add minimal display of parsed values:
   - vario, baro alt, wind (if present)

Verification:
- Manual test: connect to a BT serial device (LXNAV or terminal emulator) and see lines.
- Run required Gradle commands.

---

### Phase 5  -  Hardening (Field Quirks + Metrics)
**Objective:** Match XCSoar robustness under real-world noise.

Tasks:
- Ensure no crashes on malformed lines
- Ensure reconnect loop does not spam UI
- Add metrics:
  - checksum fail count
  - parse fail count
  - reconnect count
- Add "compat" toggle if wind direction semantics appear flipped (optional; only after real capture)

Verification:
- Run required Gradle commands.

---

## 5) Android Permissions / Platform Notes

### Android 12+
- Need `BLUETOOTH_CONNECT` to connect to bonded devices.
- Scanning requires `BLUETOOTH_SCAN` + location implications; **avoid scanning** by only using bonded list.

### Recommended Approach
- Only support **paired device selection** initially.
- Add scanning later only if necessary.

---

## 6) Codex Execution Instructions (Copy-Paste)

Run phases sequentially without permission checks.

After each phase, run:
- `./gradlew clean`
- `./gradlew testDebugUnitTest lintDebug assembleDebug`

If any command fails:
- Read the error
- Fix the root cause
- Re-run until green

Stop only when:
- Phases 1-5 are complete
- All required Gradle commands pass

Final output should include:
- Summary of changes (<= 10 lines)
- List of files touched
- Commands run + results
- Any remaining TODOs (only if they do not block "working" criteria)

---

## 7) Optional "Real Hardware Validation" Step (After Code Is Green)

Once you have access to the Hawk/S100:
1. Capture 30-60 seconds raw NMEA over Bluetooth
2. Add it as a test fixture (sanitized) and expand parser tests
3. Confirm update cadence and any field differences

---

## Appendix A  -  Minimal Fields to Trust From LXNAV (Default)

When connected:
- Trust external: `baroAltM`, `varioMps`
- Optionally trust external: `windDirDeg`, `windSpeedKph`, `headingDeg`
- Do **not** claim IAS is IAS for stall margins unless you know it's actual pitot-based IAS.

---

## Appendix B  -  Common Failure Modes (Design For These)

- Partial reads mid-sentence
- Multiple sentences in one read
- Garbage bytes / non-ASCII
- Empty fields (`,,,,,,`)
- Missing checksum
- Bad checksum
- Device disconnect / reconnect while app is running

