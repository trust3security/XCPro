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
- live LX `LXWP0` airspeed published into the canonical live external airspeed
  SSOT through the narrow `ExternalAirspeedWritePort` seam
- TAS-only LX live input now feeds the production `tas` card/runtime path
- TAS-only LX input no longer fabricates `IAS`; downstream display paths blank
  IAS when the value is non-finite
- MapScreen variometer now consumes LX `LXWP0` TE through the fused main-vario
  path and the TE outer-arc path
- the MapScreen Levo secondary label is still `levoNetto`, which is a separate
  fused output and not a direct raw-TE label

Not implemented yet:

- parsed LX device metadata shown in the Bluetooth settings UI
- distinct UI/card provenance for external Bluetooth airspeed vs
  wind-estimated airspeed
- targeted TAS-only card/unit-format proof for non-default user unit settings
- broader LX sentence-family support beyond `LXWP0` / `LXWP1`
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
  - implemented for the current LX S100 live-input slice
  - pressure altitude and TE vario are wired into fused runtime
  - LX `LXWP0` airspeed is wired into the canonical live airspeed path as
    TAS-only input
- Phase 5:
  - partially implemented
  - settings UI is operational
  - parsed device metadata is not rendered yet
  - external-airspeed provenance is not distinguished from wind-estimated
    airspeed in cards/UI yet
- Phase 6:
  - still open

### Follow-up implementation slice

- `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
  - partially implemented
  - this is now the active follow-up plan for provenance labeling, metadata UI,
    TAS-only unit/card proof, and any broader LX sentence support

## Current code anchors

- Bluetooth transport:
  - `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/AndroidBluetoothTransport.kt`
- LX parser:
  - `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/lxnav/LxSentenceParser.kt`
- LX runtime repository:
  - `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/lxnav/runtime/LxExternalRuntimeRepository.kt`
- LX control/use-case state:
  - `feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/lxnav/control/`
- Bluetooth settings UI:
  - `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/BluetoothVarioSettingsUseCase.kt`
  - `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/BluetoothVarioSettingsScreen.kt`
- External instrument fused-runtime seam:
  - `feature/flight-runtime/src/main/java/com/trust3/xcpro/external/ExternalInstrumentReadPort.kt`
- External live airspeed SSOT:
  - `feature/flight-runtime/src/main/java/com/trust3/xcpro/weather/wind/data/ExternalAirspeedRepository.kt`
- External live airspeed write seam:
  - `feature/flight-runtime/src/main/java/com/trust3/xcpro/weather/wind/data/ExternalAirspeedWritePort.kt`
- External live airspeed Hilt binding:
  - `feature/flight-runtime/src/main/java/com/trust3/xcpro/di/ExternalAirspeedModule.kt`
- TAS-only external airspeed interpretation:
  - `feature/flight-runtime/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
- TAS-only IGC emission guard:
  - `feature/map/src/main/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCase.kt`

## Open implementation gaps

### Broader S100 sentence coverage

- XCPro still supports only `LXWP0` and `LXWP1` in production.
- `LXWP2`, `LXWP3`, `PLXVF`, and `PLXVS` are still known-but-unsupported.
- So XCPro does not yet "use all S100 information"; it uses the current
  higher-value live-input subset only.

Result:

- the current production slice is good for live TAS / pressure altitude / TE
  vario, but broader S100 capabilities remain unconsumed

### MapScreen variometer ownership

- `LxExternalRuntimeRepository` publishes `totalEnergyVarioMps` into
  `ExternalInstrumentReadPort`
- `CalculateFlightMetricsRuntime` consumes that as `teVario`
- `SensorFrontEnd.buildSnapshot(...)` promotes fresh `teVario` to the main
  `bruttoVario` / `varioSource = "TE"` path
- `MapScreenUtils.convertToRealTimeFlightData(...)` carries both `teVario` and
  `levoNetto` into `RealTimeFlightData`
- `FlightDataManager.displayVarioFlow` and `needleVarioFlow` therefore reflect
  S100 TE when it is the active fused source
- `FlightDataManager.teArcVarioFlow` feeds the outer TE arc directly
- `OverlayPanels` renders:
  - main numeric/needle vario from fused display vario
  - outer arc from raw `teVario`
  - secondary label from `levoNetto`

Result:

- S100 TE does drive the main variometer widget
- S100 TE does not directly replace the Levo secondary label

### Card/source provenance and unit-proof

- `IAS` and `TAS` cards already respect user-selected units through the normal
  `UnitsFormatter` path.
- TAS-only LX input now renders through that path and IAS stays blank when
  non-finite.
- But UI/card subtitles still do not distinguish external Bluetooth airspeed
  from wind-estimated airspeed.
- There is still no targeted TAS-only card/unit test that proves:
  - `IAS = blank`
  - `TAS = formatted`
  - conversion follows non-default General units

Result:

- the runtime seam is correct, but operator-facing provenance and explicit
  TAS-only UI proof are still open

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

1. Finish the remaining follow-up in
   `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`.
2. Distinguish external Bluetooth airspeed from wind-estimated airspeed in
   cards/UI.
3. Add targeted TAS-only card/unit tests for non-default user speed units.
4. Decide explicitly whether broader S100 sentence families are in scope before
   adding `LXWP2` / `LXWP3` / `PLXV*` parsing.
5. Run the real-device validation flow from
   `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`.
6. Keep Garmin GLO 2 work separate from the LX S100 slice.

## Historical docs in this folder

These files remain useful but should be read as historical plan artifacts:

- `README_FIRST_BLUETOOTH_CODEX_PACK.md`
- `PHASE0_BASELINE_AND_BOUNDARIES.md`
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
- `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`
