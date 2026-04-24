# Bluetooth Current Status - 2026-04-11

## Purpose

This file is the authoritative current-state note for `docs/BLUETOOTH/`.

Use this before acting on older phase docs. Several older files in this folder
were written before production Bluetooth wiring landed and are now historical
records, not the current repo state.

## Short answer

LXNAV S100 Bluetooth support is implemented for the current live flight-data
slice and still incomplete for broader device/settings/status coverage.

Implemented now:

- bonded-device Bluetooth Classic SPP transport
- manifest permission and Bluetooth feature wiring
- newline-delimited line framing
- LX parser support for `LXWP0`, `LXWP1`, and `PLXVF`
- runtime ingestion, diagnostics, and reconnect state
- profile-owned Bluetooth settings UI for permission, bonded-device selection,
  connect, disconnect, and connection-health text
- fused runtime ingress for external pressure altitude, confirmed total-energy
  vario, and provisional generic external vario
- live LX `LXWP0` airspeed published into the canonical live external airspeed
  SSOT through the narrow `ExternalAirspeedWritePort` seam
- live LX airspeed is now treated as IAS-first partial input; flight-runtime
  derives TAS centrally when enough altitude/QNH context exists
- `PLXVF`-only streams can drive the production widget/audio vario path through
  the fused `EXTERNAL` owner
- MapScreen variometer consumes:
  - `LXWP0` TE for main vario, audio, and TE outer arc when fresh
  - `PLXVF` provisional vario for main vario and audio when TE is absent
- the MapScreen secondary label switches to S100-backed `NETTO` or `---` while
  S100 vario is the active widget source

Not implemented yet:

- parsed LX device metadata shown in the Bluetooth settings UI
- LX-specific widget/card source badges beyond the normal fused source labels
- broader LX sentence-family support beyond `LXWP0` / `LXWP1` / `PLXVF`
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
  - implemented for `LXWP0`, `LXWP1`, and `PLXVF`; unsupported LX sentences are
    still ignored safely
- Phase 4:
  - implemented for the current LX S100 live-input slice
  - pressure altitude, confirmed TE vario, and provisional generic external
    vario are wired into fused runtime
  - LX live airspeed is wired into the canonical live airspeed path as
    IAS-first partial input
- Phase 5:
  - partially implemented
  - settings UI is operational
  - S100-backed widget/audio/card behavior is operational on the existing XCPro
    surfaces
  - parsed device metadata is not rendered yet
  - no LX-specific source badge is rendered in the widget/cards yet
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

- XCPro now supports `LXWP0`, `LXWP1`, and `PLXVF` in production.
- `LXWP2`, `LXWP3`, and `PLXVS` remain unsupported.
- So XCPro still does not "use all S100 information"; it uses the current
  higher-value live-input subset only.

Result:

- the current production slice is good for live IAS/TAS, pressure altitude, TE,
  and provisional external vario, but broader S100 capabilities remain
  unconsumed

### MapScreen variometer ownership

- `LxExternalRuntimeRepository` publishes:
  - confirmed `totalEnergyVarioMps`
  - provisional `externalVarioMps`
  - live external airspeed through `ExternalAirspeedWritePort`
- `CalculateFlightMetricsRuntime` consumes those through the normal fused
  runtime seams
- `SensorFrontEnd.buildSnapshot(...)` promotes fresh vario with priority:
  `TE -> EXTERNAL -> PRESSURE -> BARO -> GPS`
- `MapScreenUtils.convertToRealTimeFlightData(...)` carries the fused outputs
  into `RealTimeFlightData`
- `FlightDataManager.displayVarioFlow`, `needleVarioFlow`, and the purple audio
  needle therefore reflect S100 vario when it is the active fused source
- `FlightDataManager.teArcVarioFlow` still feeds the outer TE arc from confirmed
  TE only
- `OverlayPanels` renders:
  - main numeric/needle vario from fused display vario
  - outer arc from raw `teVario`
  - secondary label from S100-backed `NETTO` or `---` while S100 vario is the
    active widget source

Result:

- S100 does drive the main variometer widget and audio through the fused main
  vario path
- the outer TE arc remains TE-only
- the secondary label is source-consistent in S100 mode instead of mixing back
  to phone-derived Levo-netto

### Card/source provenance and unit-proof

- `VARIO`, `IAS`, `TAS`, and `NETTO` cards now populate from the S100-backed
  fused outputs when the S100 owns those values.
- `IAS` and `TAS` cards still respect user-selected units through the normal
  `UnitsFormatter` path.
- `NETTO` now renders `NO DATA` in S100 mode instead of falling back to the old
  mixed-source `NO POLAR` behavior.
- There is still no LX-specific source badge in cards/widget beyond the normal
  fused source labels.

Result:

- the runtime seam is correct and the S100 card/widget slice is source-consistent,
  but dedicated LX branding/provenance UI is still open

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
2. Surface parsed LX device metadata in the Bluetooth settings UI.
3. Decide explicitly whether broader S100 sentence families are in scope before
   adding `LXWP2` / `LXWP3` / `PLXVS`.
4. Run the real-device validation flow from
   `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`.
5. Keep Garmin GLO 2 work separate from the LX S100 slice.

## Historical docs in this folder

These files remain useful but should be read as historical plan artifacts:

- `README_FIRST_BLUETOOTH_CODEX_PACK.md`
- `PHASE0_BASELINE_AND_BOUNDARIES.md`
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
- `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`
