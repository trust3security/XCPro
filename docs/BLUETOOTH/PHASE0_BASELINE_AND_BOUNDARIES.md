# Bluetooth Phase 0 Baseline and Boundaries

Date: 2026-04-09
Status: Historical baseline snapshot recorded on 2026-04-09
Scope: docs, contract freeze, and pre-implementation baseline
Superseded by: `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`

## Purpose

Freeze the Bluetooth implementation boundaries before any transport, parser,
runtime, or UI wiring lands.

This file is the Phase 0 baseline note for the LXNAV S100 Bluetooth work.
It exists to preserve the pre-implementation audit and ownership freeze that
existed before production Bluetooth wiring landed.

This file is not the current repo state.
Production Bluetooth transport/parser/runtime/settings code has since landed in
the repo. Use `CURRENT_STATUS_BLUETOOTH_2026-04-11.md` for the current state.

## Authoritative documents

- `CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md`
  - authoritative implementation contract
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
  - phase-by-phase execution brief
- `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`
  - later-phase hardware validation plan

## Ownership freeze

The upcoming Bluetooth work must keep these owners unchanged:

- `feature:variometer`
  - external-vario runtime owner for the Bluetooth slice
  - current anchors:
    - `feature/variometer/src/main/java/com/example/xcpro/hawk/HawkVarioPreviewReadPort.kt`
    - `feature/variometer/src/main/java/com/example/xcpro/hawk/HawkVarioUseCase.kt`
- `feature:flight-runtime`
  - fused flight truth owner
  - current anchor:
    - `feature/flight-runtime/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
- `feature:map`
  - consumer-only for Bluetooth work
  - current anchor:
    - `feature/map/src/main/java/com/example/xcpro/hawk/MapHawkRuntimeAdapters.kt`
  - important nuance:
    - the existing temporary HAWK adapters in `feature:map` do not authorize
      Bluetooth transport, sentence parsing, or external-vs-phone arbitration
      to move into `feature:map`

Related settings-side owner that remains unchanged:

- `feature:profile`
  - settings UI owner for the existing HAWK preview/settings path
  - current anchors:
    - `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsUseCase.kt`
    - `feature/profile/src/main/java/com/example/xcpro/screens/navdrawer/HawkVarioSettingsScreenRuntime.kt`

## Baseline repo state

This section describes the repo as audited on 2026-04-09.

On that date, Phase 0 confirmed the repo still had no production Bluetooth
wiring for the LXNAV S100 slice.

### Audit commands and results

The following audits were run against `app/`, `core/`, and `feature/` sources:

```text
rg -n -i "android\.bluetooth|\bbluetooth\b|rfcomm|gatt|BluetoothAdapter|BluetoothDevice|BluetoothSocket|BluetoothGatt|BluetoothLeScanner|createRfcommSocketToServiceRecord|createInsecureRfcommSocketToServiceRecord" app core feature -g "*.kt"
```

Result: no matches in production Kotlin sources.

```text
rg -n "android.permission.BLUETOOTH|android.permission.BLUETOOTH_CONNECT|android.permission.BLUETOOTH_SCAN|uses-feature.*bluetooth|<uses-permission[^>]*BLUETOOTH" app feature core -g "*.xml" -g "AndroidManifest.xml"
```

Result: no Bluetooth permission or feature wiring found in manifests/resources.

```text
rg -n "LXWP0|LXWP1|NmeaLine|LxSentence|BondedBluetoothDevice|ExternalFlightInputSelector|BluetoothTransport|BluetoothConnectionState" app core feature -g "*.kt"
```

Result: no Phase 1+ transport or parser symbols found in production Kotlin.

### Interpretation

- There is no Bluetooth Classic RFCOMM/SPP implementation in the repo baseline.
- There is no BLE/GATT implementation in the repo baseline.
- There is no Android Bluetooth permission wiring in the repo baseline.
- There is no LX sentence parser, line framer, connection-state model, or
  source-arbitration implementation for this slice yet.
- The current HAWK runtime/settings flow remains the relevant ownership
  reference only; it is not Bluetooth transport wiring.

## Explicit Phase 0 non-goals

This phase does not include:

- transport interfaces
- bonded-device discovery or connection logic
- Bluetooth permissions or scan flow
- BLE support
- line framing implementation
- parser implementation
- runtime snapshot state or field arbitration
- settings UI behavior changes
- declaration or writeback behavior
- map-owned Bluetooth logic

## Deferred to later phases

- Phase 1:
  - transport contracts
  - connection/error models
  - pure line framing
- Phase 2:
  - bonded-device RFCOMM/SPP transport
- Phase 3:
  - LX sentence parser and snapshot building
- Phase 4:
  - runtime repository and external-vs-phone arbitration
- Phase 5:
  - settings UI and diagnostics
- Phase 6:
  - hardening, hardware validation, and doc sync for active wiring

## Notes for Phase 1

- Keep the Bluetooth docs under `docs/BLUETOOTH/` as the authoritative phase
  execution pack.
- Treat `docs/FEATURES/Bluetooth/bluetooth.md` as background/reference context,
  not as the phase execution contract.
- Do not interpret the existing `feature:map` HAWK adapters as permission to
  put Bluetooth transport or parser logic in the map layer.
- If active runtime wiring lands in a later phase, update `PIPELINE.md` in the
  same change set that lands that wiring.
