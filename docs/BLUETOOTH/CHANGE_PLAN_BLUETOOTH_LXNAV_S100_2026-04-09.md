# XCPro Bluetooth Change Plan - LXNAV S100 - 2026-04-09

## Purpose

Add production-grade Bluetooth connectivity for LXNAV S100 class instruments to XCPro without breaking the current XCPro architecture.

This plan is intentionally repo-native:
- XCPro keeps its current MVVM + UDF + SSOT structure.
- XCSoar is used only as a behavioral reference for transport and sentence handling.
- Existing XCPro runtime owners remain the owners.
- The implementation is phased to avoid churn, reduce rollback cost, and keep review scope small.

---

## 0) Metadata

- Title: Bluetooth LXNAV S100 integration for XCPro
- Owner: draft for XCPro team
- Date: 2026-04-09
- Target repo: trust3security/XCPro
- Status: Historical master plan with 2026-04-11 status overlay
- Scope class: New runtime feature integrated into existing flight pipeline

---

## Current repo status on 2026-04-11

This file remains the master architecture contract for the LX S100 Bluetooth
slice, but it no longer describes a pre-implementation-only repo.

Production code already includes:

- Bluetooth Classic bonded-device transport
- line framing
- `LXWP0` and `LXWP1` parsing
- LX runtime snapshot/diagnostics/reconnect handling
- settings UI for bonded-device selection and connect/disconnect
- fused runtime ingress for external pressure altitude and TE vario

Open follow-up work:

- publish LX airspeed/TAS into the canonical live airspeed path
- render parsed LX device metadata in Bluetooth settings
- complete real-device validation and sanitized fixture capture

Phase-status view:

- Phase 0:
  - historical baseline complete
- Phase 1:
  - implemented
- Phase 2:
  - implemented
- Phase 3:
  - implemented for `LXWP0` and `LXWP1`
- Phase 4:
  - partially implemented
- Phase 5:
  - partially implemented
- Phase 6:
  - still open

Before acting on this plan, read:

- `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`
- `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md` for the current TAS/settings follow-up slice

---

## 1) Scope

### 1.1 Problem statement

XCPro needs to connect to an LXNAV S100 over Bluetooth, consume the external vario/baro data stream reliably, and integrate that stream into the existing XCPro runtime so external instrument data can override phone-derived values where appropriate.

### 1.2 Why now

XCPro already has strong runtime ownership rules and existing HAWK-oriented runtime seams. Bluetooth support should be added now in a way that respects those seams before more external-device logic grows in ad hoc locations.

### 1.3 In scope

- bonded-device Bluetooth Classic SPP support for LXNAV S100 class devices
- newline-delimited sentence framing over the Bluetooth byte stream
- pure LX sentence parsing for the initial read-only subset
- connection state, last-received time, sentence-rate, parse/checksum diagnostics
- external instrument snapshot state in the runtime/data layer
- explicit field-ownership policy for external-vs-phone data
- settings-side device selection and connect/disconnect UX
- required doc and pipeline updates when active wiring lands

### 1.4 Out of scope

- BLE/GATT transport unless real capture proves SPP is wrong
- Bluetooth scanning flow in v1
- task declaration over Bluetooth
- pilot declaration over Bluetooth
- MC / bugs / ballast writeback in v1
- generic multi-vendor abstraction across every external instrument in the same slice
- unrelated refactors or broad package renames
- changing the map shell into the runtime owner

### 1.5 User-visible impact

- pilot can select a bonded S100-class device
- pilot can connect and disconnect from XCPro settings/UI
- pilot can see whether the link is alive
- external baro/vario values can flow into XCPro when the link is healthy
- XCPro falls back to phone inputs when the device disconnects or data goes stale

---

## 2) Architecture contract

### 2.1 Dependency direction

Required direction remains:

`UI -> domain/use-case -> data/runtime`

Bluetooth transport is a data/runtime concern.
Sentence parsing is a pure domain/data concern.
Field selection policy is a use-case/runtime concern.
UI only renders state and dispatches intents.

### 2.2 Ownership contract

| Responsibility | Authoritative owner | Exposed as | Forbidden duplicate owner |
|---|---|---|---|
| Bonded-device discovery and connect/disconnect transport | variometer-owned external-device runtime seam | transport interface + connection state flow | map screen, Composables, ad hoc managers |
| LX sentence framing and checksum handling | pure parser/framer layer under runtime owner | pure functions / parser results | ViewModel or UI parsing |
| External instrument snapshot state | variometer-owned runtime repository | `StateFlow` snapshot + diagnostics | UI-local mutable copy |
| External-vs-phone field arbitration | explicit use case / selector | merged runtime snapshot | parser or transport |
| Live settings route for device selection | profile-owned settings screen and ViewModel | UI state + intents | map-owned settings |
| Fused flight truth after arbitration | existing flight runtime SSOT | repository flow consumed upstream | Bluetooth repository as app-wide flight truth |
| Map display of resulting values | map consumer/adapters only | already-merged runtime values | map-side Bluetooth owner |

### 2.3 Repo-native owner mapping

This plan deliberately follows the current XCPro owner pattern:

- `feature:profile` remains the settings UI owner.
- `feature:variometer` remains the live external-vario runtime owner.
- `feature:flight-runtime` remains fused-flight-data truth owner.
- `feature:map` stays adapter/render-only for this feature.

### 2.4 Naming / churn policy

Do not perform a broad rename of existing `hawk` classes, packages, or screens in this slice.

Reason:
- The repo already has `hawk` runtime and settings ownership.
- A broad rename from `hawk` to `bluetooth` or `external instrument` adds churn without delivering pilot value.
- New abstractions may use more generic internal names where that reduces future lock-in, but existing public surfaces should remain stable in this slice.

### 2.5 Transport assumption contract

v1 assumes:
- bonded-device Bluetooth Classic SPP / RFCOMM
- newline-delimited ASCII sentence stream
- no Bluetooth scan UI
- no BLE/GATT path unless raw hardware evidence contradicts the assumption

### 2.6 Data ownership policy

Default initial trust policy:

- Trust external barometric altitude when present and fresh.
- Trust external vario when present and fresh.
- Trust external wind only if it is actually provided and parser-validated.
- Do not blend the same field from phone and external sources at the same time.
- Do not claim IAS is pitot-truth for safety-sensitive use unless device verification proves it.
- Fall back to phone-derived values automatically when external data is missing, disconnected, or stale.

### 2.7 Time-base contract

- Staleness and elapsed-time checks use monotonic time only.
- Wall clock is for UI labels and exported logs only.
- Parser does not own time semantics beyond attaching receive timestamps.

### 2.8 One mistake to avoid

Do not let this become a map-owned feature.

If the new Bluetooth code ends up requiring `feature:map` to own the transport, parser, or source arbitration, the implementation is wrong and should fail review.

---

## 3) Reference pattern check

### 3.1 What to reuse from XCSoar conceptually

Reuse only the proven behavioral shape:
- byte-stream transport
- line buffering for partial reads
- checksum validation
- tolerant LX sentence parsing
- explicit ownership rules
- disconnect/reconnect hardening

### 3.2 What not to copy

Do not copy:
- XCSoar app architecture wholesale
- direct UI-layer access from driver code
- vendor naming in production XCPro Kotlin
- unreviewed code porting or copy-paste imports

### 3.3 XCPro-specific deviation

Instead of building a standalone device subsystem outside current owners, extend the existing variometer-owned runtime seam and profile-owned settings seam.

That is the low-churn XCPro-native path.

---

## 4) File ownership plan

These names are illustrative. Prefer the current repo layout where it already
provides the right owner.

### 4.1 Variometer-owned runtime additions

Current location:
- `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/`

Current examples already in repo:
- `BondedBluetoothDevice.kt`
- `BluetoothConnectionState.kt`
- `BluetoothConnectionError.kt`
- `BluetoothTransport.kt`
- `AndroidBluetoothTransport.kt`
- `NmeaLine.kt`
- `NmeaLineFramer.kt`
- `lxnav/LxSentenceParser.kt`
- `lxnav/runtime/LxExternalRuntimeRepository.kt`
- `lxnav/control/LxBluetoothControlUseCase.kt`
- `lxnav/control/LxBluetoothControlState.kt`

### 4.2 Profile-owned settings additions

Current location:
- `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/`

Current examples already in repo:
- `BluetoothVarioSettingsUseCase.kt`
- `BluetoothVarioSettingsModels.kt`
- `BluetoothVarioSettingsScreen.kt`
- `BluetoothVarioSettingsViewModel.kt`

### 4.3 Flight-runtime integration seam

Keep narrow.

Use an explicit read seam or selector input so external-device values enter the fused runtime through a defined contract.

Do not let the Bluetooth repository become a second app-wide flight SSOT.

### 4.4 Map layer

Map consumes already-selected runtime values only.

No Bluetooth socket code.
No sentence parsing.
No field arbitration.

---

## 5) Phase plan

### Phase 0 - Docs, contract freeze, and baseline

#### Objective
Freeze the architecture contract before active code wiring.

#### Tasks
1. Add this change plan to the repo.
2. Add a Bluetooth feature intent doc if needed or refresh the current one.
3. Confirm current runtime owners in repo docs stay unchanged.
4. Add baseline tests or fixture placeholders for line framing and parser samples.
5. Record explicit non-goals for v1.

#### Output
- docs-only PR or docs-first commit
- no production Bluetooth behavior yet

#### Exit criteria
- change plan is present
- reviewers can point to one authoritative Bluetooth implementation contract
- no active code wiring has landed yet

---

### Phase 1 - Transport contracts and framing

#### Objective
Create the seam before implementation.

#### Tasks
1. Add transport interfaces for bonded-device connection and byte-stream reads.
2. Add explicit connection state and error models.
3. Add `NmeaLine` / line-framing model with receive timestamp.
4. Add pure line-framer tests for:
   - partial line across reads
   - multiple lines in one read
   - CRLF cleanup
   - garbage/control-char tolerance
5. Keep this phase independent of Android socket implementation where possible.

#### Constraints
- no UI logic in transport
- no parser coupling yet
- no map dependency

#### Exit criteria
- transport and framing seams exist
- pure tests cover framing edge cases
- no LX-specific parser logic is required for the transport layer to compile

---

### Phase 2 - Android bonded-device SPP transport

#### Objective
Implement real RFCOMM transport with minimal platform surface.

#### Tasks
1. Implement bonded-device listing only.
2. Require only the connect permission needed for bonded-device access.
3. Implement RFCOMM socket connect/read/close.
4. Surface connection lifecycle as state:
   - disconnected
   - connecting
   - connected
   - error
5. Add reconnect/backoff behavior that does not spam UI state.
6. Add fakeable interfaces so runtime logic remains unit-testable.

#### Constraints
- no scanning
- no BLE
- no parser-owned reconnect logic
- no blocking work in UI layer

#### Exit criteria
- a bonded device can be selected and a byte stream can be consumed by the framer
- connection state and errors are observable through state flow or equivalent runtime seam

---

### Phase 3 - LX sentence parser and snapshot builder

#### Objective
Turn framed lines into validated external instrument samples.

#### Tasks
1. Implement pure parser support for initial read-only subset:
   - `$LXWP0`
   - `$LXWP1`
   - optionally ignore unsupported sentences safely
2. Add checksum validation/tolerance policy explicitly.
3. Tolerate blank fields and malformed lines without crashing.
4. Build a normalized snapshot model with only the fields v1 needs.
5. Add parser fixtures and tests covering:
   - valid checksum
   - missing checksum policy
   - bad checksum rejection
   - blank-field tolerance
   - unsupported-sentence ignore path

#### Constraints
- parser returns structured success/failure data, not UI strings
- parser owns no source-selection policy
- parser owns no app-level staleness logic

#### Exit criteria
- parser is pure and unit-tested
- external snapshot can be constructed from sentence stream safely

---

### Phase 4 - Runtime repository and source arbitration

#### Objective
Integrate external instrument data into the existing runtime ownership model.

#### Tasks
1. Build an external instrument repository under the variometer-owned runtime seam.
2. Maintain snapshot, diagnostics, sentence rate, and last-received monotonic time.
3. Add explicit selector/use-case logic for external-vs-phone field arbitration.
4. Wire external baro/vario into the current runtime through a narrow contract.
5. Ensure disconnect and stale-data fallback works automatically.
6. Add unit tests for:
   - full override when external values are present
   - partial override when only some fields are present
   - disconnect fallback
   - stale-data fallback

#### Constraints
- do not create a second fused-flight truth owner
- do not push external arbitration into UI
- do not keep permanent runtime ownership inside map adapters

#### Exit criteria
- external data can influence runtime through an explicit selector
- fallback behavior is correct and test-covered

---

### Phase 5 - Settings UI and diagnostics

#### Objective
Make the feature operable and debuggable without weakening boundaries.

#### Tasks
1. Extend the profile-owned settings screen / ViewModel path for:
   - bonded-device list
   - selected device persistence if appropriate
   - connect/disconnect intent
2. Surface diagnostics through a read-only preview/runtime contract:
   - connection state
   - last-received time
   - sentence rate
   - minimal parsed preview values
3. Add a rolling in-memory raw-line buffer toggle for debugging if low-risk.
4. Keep UI state lifecycle-aware and read-only.

#### Constraints
- no socket or parser logic in ViewModel or Composables
- settings screen owns interaction, not transport implementation

#### Exit criteria
- pilot can select a bonded device and operate the link
- pilot can see if the stream is alive

---

### Phase 6 - Hardening, hardware validation, and doc sync

#### Objective
Finish the feature to production quality.

#### Tasks
1. Run real S100 bench validation using the capture checklist.
2. Add sanitized real capture fixtures if possible.
3. Confirm actual sentence inventory and field behavior.
4. Harden malformed input handling and reconnect behavior.
5. Add counters/metrics:
   - reconnect count
   - parse failure count
   - checksum failure count
6. Update `PIPELINE.md` in the same PR that lands final active wiring.
7. Record any non-trivial decisions in ADR/change-plan notes if boundary choices changed.

#### Exit criteria
- real hardware evidence matches implementation assumptions, or deviations are documented
- final active runtime wiring is documented in repo docs

---

## 6) Acceptance criteria

### 6.1 Functional
- user can see bonded supported devices
- user can connect and disconnect
- app shows connection state clearly
- app can consume a live sentence stream from the external device
- parser supports the agreed v1 read subset
- external baro/vario can override phone-derived values through explicit runtime policy
- disconnect or stale data causes automatic fallback to phone values

### 6.2 Non-functional
- no crashes on malformed lines
- no crashes on checksum failure
- no UI-owned transport logic
- no permanent map-owned Bluetooth logic
- no silent error swallowing
- no second app-wide flight-data truth owner

### 6.3 Tests required
- line-framer unit tests
- parser unit tests
- selector/use-case unit tests
- ViewModel state tests for settings interactions where reasonable
- manual real-hardware validation before final signoff

---

## 7) Commands and gates

Required after each non-trivial phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run this too when the phase touches lint-sensitive Android code:

```bash
./gradlew lintDebug
```

If a command fails:
- fix the root cause,
- rerun the failed command,
- then rerun the full gate set before claiming completion.

---

## 8) Codex output contract

At the end of each phase, Codex must output:

```text
Phase N Summary
- What changed
- Files touched
- Tests run
- Results
- Risks or follow-ups
- Next recommended phase
```

For review phases, Codex or reviewer output must be:

```text
Review Verdict: PASS or FAIL
- Boundary compliance
- Ownership compliance
- Test sufficiency
- Runtime risks
- Exact fixes required before next phase
```

---

## 9) Risk register

### Risk 1 - Hidden transport assumptions
The device may not behave exactly like assumed SPP serial.

Mitigation:
- keep transport isolated,
- validate with real capture before final signoff,
- do not let that uncertainty leak into parser or UI.

### Risk 2 - Broad naming churn
Renaming all `hawk` surfaces now could destabilize unrelated work.

Mitigation:
- no sweeping rename in this slice.

### Risk 3 - Map-owned implementation drift
Quick wiring through map adapters may feel faster but will create long-term debt.

Mitigation:
- fail review if transport/parser/arbitration land in map-owned runtime.

### Risk 4 - Flight-runtime duplication
It is easy to accidentally create a second source of truth.

Mitigation:
- keep external instrument state as an input seam feeding the existing fused runtime.

### Risk 5 - Premature writeback scope
Trying to add declaration or writeback too early will create churn.

Mitigation:
- keep v1 read-only.

---

## 10) Final recommendation

Implement this as:
- existing XCPro architecture,
- existing runtime owners,
- new Bluetooth transport seam,
- new pure LX parser,
- explicit source arbitration,
- phase-by-phase delivery.

That is the professional path with the least churn.
