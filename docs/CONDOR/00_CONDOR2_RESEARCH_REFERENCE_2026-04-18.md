# Condor 2 Research Reference

Research date: 2026-04-18

## Purpose

This note records:

- what Condor exposes externally
- what XCSoar already does with Condor
- what XCPro currently does with live GPS and fused flight data
- what a correct Condor 2 integration must preserve

This is a planning note, not a claim that Condor support already exists in
XCPro.

## Short answer

Condor 2 should be treated as a live simulator source that can provide:

- ownship position and ground track through NMEA GPS sentences
- instrument data through `$LXWP0`
- a PC-hosted serial stream that must be bridged to Android transport

For XCPro, the important architectural point is not the parser itself. The
important point is that Condor must become the selected live source feeding the
existing fused runtime path so that the map continues to render from
`flightData.gps`.

## What XCSoar already does

The local XCSoar repo provides a useful reference:

- `XCSoar/src/Device/Driver/Condor.cpp`
- `XCSoar/src/Device/Register.cpp`

Observed behavior from that code:

- XCSoar registers separate drivers for `Condor` and `Condor3`.
- The Condor-specific driver parses `$LXWP0`.
- Generic NMEA handling covers the position sentences.
- Condor 1/2 wind direction from `$LXWP0` is treated as the direction the wind
  is going to, so XCSoar applies a reciprocal conversion.
- Condor 3 uses a different wind-direction interpretation and disables that
  reciprocal conversion.

Useful conclusion:

- XCPro should implement Condor 2 first.
- Condor 2 and Condor 3 should not share one silent wind rule.
- Position ownership and instrument ownership are separable, but both should
  still feed one fused live pipeline.

## What official Condor docs say

Confirmed from current official Condor sources:

- Condor exposes real-time NMEA output over serial port.
- Condor 3 public product docs explicitly mention `GPGGA`, `GPRMC`, and
  `LXWP0`.
- Condor manuals describe NMEA output as a way to connect external navigation
  hardware/software.

Implication for Condor 2 planning:

- the first useful XCPro parser target remains `GGA`, `RMC`, and `LXWP0`
- the integration should stay transport-agnostic above the line-framing layer

## What XCPro does today

Current repo state on 2026-04-18:

- `FlightDataRepository` is the fused flight-data SSOT.
- `MapScreenViewModelStateBuilders.createMapLocationState()` maps map ownship
  from fused `flightData.gps`.
- live sensor startup is still controlled by foreground-service code that is
  hard-gated on Android location permission.
- `MapSensorsUseCase` still exposes phone-specific GPS status and phone sensor
  status by reading `UnifiedSensorManager` directly.
- DI still wires the live sensor source straight to `UnifiedSensorManager`.

Important local files:

- `feature/flight-runtime/src/main/java/com/trust3/xcpro/flightdata/FlightDataRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapSensorsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/di/SensorFusionModule.kt`
- `feature/map/src/main/java/com/trust3/xcpro/di/WindSensorModule.kt`
- `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt`

## What this means for Condor 2

For `CONDOR2_FULL`, the correct user-visible behavior is:

- Condor owns selected live ownship
- fused live `flightData.gps` reflects the Condor aircraft location
- the map shows the sim location
- replay-specific location binders are not involved

This is the key answer to the product question:

- if Condor is flying at Lake Keepit, the user should see Lake Keepit on the
  XCPro map
- if the phone GPS remains authoritative, the implementation is wrong

## Required first-slice sentences

Required for the first useful Condor 2 slice:

- `GPGGA`
- `GPRMC`
- `$LXWP0`

Why:

- `GGA` and `RMC` are enough to establish ownship position, altitude, speed,
  and track
- `LXWP0` is enough for baro altitude, TE vario, heading, and wind

## Condor 2 vs Condor 3

Condor 3 is out of scope for the first implementation, but the plan must avoid
painting itself into a corner.

Keep these separate:

- wind-direction policy
- supported transport setup details
- future driver/profile selection

Do not:

- hardcode one wind rule for all Condor versions
- name the feature or parser in a way that assumes Condor 2 and Condor 3 are
  identical

## Recommended architectural conclusion

The implementation should introduce:

- a dedicated simulator runtime owner
- a selected live-source seam that can switch between phone and Condor
- source-aware startup and status
- no replay-path reuse for live simulator ownship

The implementation should not introduce:

- a second flight-data SSOT
- map-owned simulator runtime
- variometer-owned full simulator ownership
- phone-permission assumptions baked into all live-source startup
- auto-upload side effects for simulator sessions

## Sources

Official Condor sources:

- Condor Help: connecting XCSoar to Condor
  - `https://condor-help.helpscoutdocs.com/article/41-connecting-xcsoar-to-condor`
- Condor 3 product page
  - `https://www.condorsoaring.com/v3discover/`
- Condor 3 manual
  - `https://downloads3.condorsoaring.com/manuals/Condor%203%20manual_en.pdf`

Local code references:

- `C:/Users/Asus/AndroidStudioProjects/XCSoar/src/Device/Driver/Condor.cpp`
- `C:/Users/Asus/AndroidStudioProjects/XCSoar/src/Device/Register.cpp`
- `C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`
- `C:/Users/Asus/AndroidStudioProjects/XCPro/app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt`

## Practical takeaway

The hard part is not decoding three sentence types. The hard part is putting
Condor on the correct live-source seam so that XCPro treats the simulator as
the authoritative live aircraft without weakening replay or module boundaries.
