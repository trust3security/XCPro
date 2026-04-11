# Bluetooth Current Status - 2026-04-11

## Purpose

This file is the authoritative current-state note for `docs/BLUETOOTH/`.

Use this before acting on older phase docs. Several older files in this folder
were written before production Bluetooth wiring landed and are now historical
records, not the current repo state.

## Short answer

LXNAV S100 Bluetooth support is partially implemented in production code.

Implemented now:

- bonded-device Bluetooth Classic SPP transport
- manifest permission and Bluetooth feature wiring
- newline-delimited line framing
- LX parser support for `LXWP0` and `LXWP1`
- runtime ingestion, diagnostics, and reconnect state
- profile-owned Bluetooth settings UI for permission, bonded-device selection,
  connect, disconnect, and connection-health text
- fused runtime ingress for external pressure altitude and total-energy vario

Not implemented yet:

- LX `airspeedKph` published into the live external airspeed SSOT
- DF-card `tas` fed from live LX Bluetooth data
- explicit `ias` display suppression for TAS-only LX input
- parsed LX device metadata shown in the Bluetooth settings UI
- hardware validation signoff from sanitized real-device capture
- Garmin GLO 2 support

## Phase status summary

### Historical phase plan status

- Phase 0:
  - complete as a historical baseline record only
- Phase 1:
  - implemented
- Phase 2:
  - implemented
- Phase 3:
  - implemented for `LXWP0` and `LXWP1`; unsupported LX sentences are ignored safely
- Phase 4:
  - partially implemented
  - pressure altitude and TE vario are wired into fused runtime
  - LX airspeed/TAS is still not wired into the canonical live airspeed path
- Phase 5:
  - partially implemented
  - settings UI is operational
  - parsed device metadata is not rendered yet
- Phase 6:
  - still open

### Follow-up implementation slice

- `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
  - still open
  - this is the active follow-up plan for TAS/DF-card wiring and settings metadata

## Current code anchors

- Bluetooth transport:
  - `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/AndroidBluetoothTransport.kt`
- LX parser:
  - `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/lxnav/LxSentenceParser.kt`
- LX runtime repository:
  - `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/lxnav/runtime/LxExternalRuntimeRepository.kt`
- LX control/use-case state:
  - `feature/variometer/src/main/java/com/example/xcpro/variometer/bluetooth/lxnav/control/`
- Bluetooth settings UI:
  - `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/BluetoothVarioSettingsUseCase.kt`
  - `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/BluetoothVarioSettingsScreen.kt`
- External instrument fused-runtime seam:
  - `feature/flight-runtime/src/main/java/com/example/xcpro/external/ExternalInstrumentReadPort.kt`
- External live airspeed SSOT:
  - `feature/flight-runtime/src/main/java/com/example/xcpro/weather/wind/data/ExternalAirspeedRepository.kt`

## Open implementation gaps

### LX TAS to DF-cards

- `LxSentenceParser` already parses `LXWP0` airspeed as `airspeedKph`.
- `LxExternalRuntimeRepository` already stores `airspeedKph` in its own runtime
  snapshot.
- The value stops there today.
- No production callsite currently publishes LX airspeed into
  `ExternalAirspeedRepository`.

Result:

- live LX Bluetooth data does not yet drive the canonical `tas` card path

### Bluetooth settings metadata

- `LxSentenceParser` already parses `product`, `serial`, `softwareVersion`, and
  `hardwareVersion` from `LXWP1`
- `LxExternalRuntimeRepository` already stores merged `deviceInfo`
- control/use-case/UI state does not surface those fields yet

Result:

- the Bluetooth settings screen can show selected and active device labels, but
  not parsed LX identity/version fields

### Hardware validation

- the repo has a hardware-validation plan and fixture placeholder docs
- this folder does not yet contain sanitized real S100 capture evidence that
  closes the transport/parser assumptions

## Recommended next work order

1. Implement `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`.
2. Run the real-device validation flow from
   `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`.
3. Update `PIPELINE.md` again when TAS/metadata wiring lands.
4. Keep Garmin GLO 2 work separate from the LX S100 slice.

## Historical docs in this folder

These files remain useful but should be read as historical plan artifacts:

- `README_FIRST_BLUETOOTH_CODEX_PACK.md`
- `PHASE0_BASELINE_AND_BOUNDARIES.md`
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
- `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`
