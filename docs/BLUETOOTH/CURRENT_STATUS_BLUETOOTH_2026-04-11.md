# Bluetooth Current Status - 2026-04-11

## Purpose

This file is the authoritative current-state note for `docs/BLUETOOTH/`.

Use this before acting on older phase docs. Several older files in this folder
were written before production Bluetooth wiring landed and are now historical
records, not the current repo state.

## Short answer

LXNAV S100 Bluetooth support is implemented for both the live-flight slice and
the current broader settings/status slice.

Implemented now:

- bonded-device Bluetooth Classic SPP transport
- manifest permission and Bluetooth feature wiring
- newline-delimited line framing
- LX parser support for `LXWP0`, `LXWP1`, `LXWP2`, `LXWP3`, `PLXVF`, and `PLXVS`
- runtime ingestion, diagnostics, reconnect state, and live-source selection
- profile-owned Bluetooth settings UI for permission, bonded-device selection,
  connect, disconnect, connection-health text, parsed device metadata, active
  overrides, device/environment status, and configuration/status details
- fused runtime ingress for external pressure altitude, confirmed total-energy
  vario, and provisional generic external vario
- live LX airspeed published into the canonical live external airspeed SSOT
  through the narrow `ExternalAirspeedWritePort` seam
- live LX airspeed treated as IAS-first partial input; flight-runtime derives
  TAS centrally when enough altitude/QNH context exists
- new non-persistent live external-settings seam for S100 `MC`, bugs, ballast
  overload factor, QNH, and OAT
- `PLXVF`-only streams can drive the production widget/audio vario path through
  the fused `EXTERNAL` owner
- MapScreen variometer consumes:
  - `LXWP0` TE for main vario, audio, and TE outer arc when fresh
  - `PLXVF` provisional vario for main vario and audio when TE is absent
- S100-backed cards and widgets now include:
  - `VARIO`, `IAS`, `TAS`, `NETTO`
  - `MC`, `BUGS`, `BALLAST_FACTOR`, `OAT`
  - external-QNH display through the existing `QNH` card
  - read-only external mode for the ballast widget when S100 ballast factor is active

Not implemented yet:

- LX-specific widget/card source badges beyond the normal fused source labels
- broader use of `LXWP0` heading/wind fields
- broader use of `PLXVF` accel channels
- runtime use of `LXWP2` polar coefficients or audio volume beyond diagnostics
- runtime use of most `LXWP3` SC/filter/config values beyond diagnostics and derived QNH
- dedicated cards for `PLXVS` mode/voltage
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
  - implemented for `LXWP0`, `LXWP1`, `LXWP2`, `LXWP3`, `PLXVF`, and `PLXVS`
- Phase 4:
  - implemented for the current LX S100 live-input and live-override slice
  - pressure altitude, confirmed TE vario, and provisional generic external
    vario are wired into fused runtime
  - LX live airspeed is wired into the canonical live airspeed path as
    IAS-first partial input
- Phase 5:
  - implemented for the current broader S100 surface set
  - settings UI is operational, including parsed metadata and broader detail
    sections
  - S100-backed widget/audio/card behavior is operational on the existing XCPro
    surfaces
  - no LX-specific source badge is rendered in the widget/cards yet
- Phase 6:
  - still open

### Follow-up implementation slice

- `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
  - now primarily a historical plan artifact
  - the current broader wiring status is reflected in this file and
    `02_LX_S100_SENTENCE_CAPABILITIES_2026-04-10.md`

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
- External flight-settings override seam:
  - `feature/flight-runtime/src/main/java/com/trust3/xcpro/external/ExternalFlightSettingsReadPort.kt`
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

- XCPro now supports `LXWP0`, `LXWP1`, `LXWP2`, `LXWP3`, `PLXVF`, and `PLXVS`
  in production.
- `LXWP2` drives live `MC`, bugs, and ballast-factor surfaces through the
  separate external-flight-settings override seam.
- `LXWP3` drives derived live QNH and contributes broader config/status details
  in Bluetooth settings.
- `PLXVS` drives `OAT` plus settings-side mode/voltage visibility.

Result:

- XCPro now uses the higher-value live-input and live-override subset of the
  S100 sentence family on existing XCPro surfaces
- remaining gaps are about additional field usage and hardware validation, not
  basic sentence-family ingestion

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

- `VARIO`, `IAS`, `TAS`, `NETTO`, `MC`, `BUGS`, `BALLAST_FACTOR`, and `OAT`
  now populate from S100-backed owners when the S100 owns those values.
- the existing `QNH` card reflects the live external QNH override when active.
- `IAS` and `TAS` cards still respect user-selected units through the normal
  `UnitsFormatter` path.
- `NETTO` now renders `NO DATA` in S100 mode instead of falling back to the old
  mixed-source `NO POLAR` behavior.
- There is still no LX-specific source badge in cards/widget beyond the normal
  fused source labels.

Result:

- the runtime seam is correct and the S100 card/widget slice is source-consistent,
  but dedicated LX branding/provenance UI is still open

### Bluetooth settings metadata and diagnostics

- `LxSentenceParser` parses `product`, `serial`, `softwareVersion`, and
  `hardwareVersion` from `LXWP1`
- `LxExternalRuntimeRepository` stores merged device info, live overrides,
  environment status, and broader device/configuration state from `LXWP2`,
  `LXWP3`, and `PLXVS`
- control/use-case/UI state now surfaces:
  - parsed identity/version fields
  - active overrides
  - OAT/mode/voltage
  - polar/audio/configuration/status details

Result:

- the Bluetooth settings screen now exposes the broader parsed S100 information
  without creating a parallel in-flight LX UI

### Hardware validation

- the repo has a hardware-validation plan and fixture placeholder docs
- this folder does not yet contain sanitized real S100 capture evidence that
  closes the transport/parser assumptions

## Recommended next work order

1. Run the real-device validation flow from
   `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`.
2. Decide which remaining broader S100 fields should graduate from
   diagnostics/display-only into runtime authority, especially:
   - `LXWP0` heading/wind
   - `PLXVF` accel/g-load
   - `LXWP2` polar/audio-volume fields
   - `PLXVS` mode/voltage cards
3. Keep Garmin GLO 2 work separate from the LX S100 slice.

## Historical docs in this folder

These files remain useful but should be read as historical plan artifacts:

- `README_FIRST_BLUETOOTH_CODEX_PACK.md`
- `PHASE0_BASELINE_AND_BOUNDARIES.md`
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
- `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`
