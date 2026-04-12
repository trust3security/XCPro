# LX S100 Bluetooth DF-Card Wiring Plan

## Metadata

- Date: 2026-04-10
- Status: Partially implemented; updated after production seam review on 2026-04-12
- Scope: LX S100 live Bluetooth data to DF-cards and Bluetooth settings metadata

## Goal

When an LX S100 is connected to XCPro, the production Bluetooth path should use
the highest-value live instrument data safely, and any remaining follow-up
should stay within the existing ownership boundaries.

Target user-visible outcomes:

- `vario` card shows fresh LX TE vario
- MapScreen variometer main readout and needle use fresh LX TE when it is the
  active fused source
- MapScreen variometer outer arc uses raw LX TE
- MapScreen Levo secondary label remains `levoNetto`, not raw LX TE
- `tas` card shows LX airspeed
- `baro_alt` reuses LX pressure altitude when external data is active
- Bluetooth settings show product, serial, software version, and hardware version

## Implemented Now

- `LxExternalRuntimeRepository` publishes `LXWP0` airspeed into the live
  external airspeed path through `ExternalAirspeedWritePort`.
- `CalculateFlightMetricsRuntime` treats LX `LXWP0` airspeed as `TAS only`.
- flight-runtime no longer fabricates `IAS` from TAS-only LX input.
- existing card formatting already suppresses IAS when non-finite and formats
  TAS through `UnitsFormatter`, so user-selected speed units still apply.
- IGC live-sample mapping now preserves TAS when IAS is unavailable.

Still not implemented from the original draft:

- parsed LX metadata shown in Bluetooth settings UI
- distinct card/UI provenance for external Bluetooth airspeed vs
  wind-estimated airspeed
- targeted TAS-only UI-format proof for non-default speed units
- broader LX sentence-family support beyond `LXWP0` / `LXWP1`

## Current Repo Findings

- `feature/variometer/.../LxSentenceParser.kt` already parses:
  - `LXWP0`
  - `LXWP1`
- `feature/variometer/.../LxExternalRuntimeRepository.kt` already stores:
  - `airspeedKph`
  - `pressureAltitudeM`
  - `totalEnergyVarioMps`
  - `deviceInfo`
- `feature/flight-runtime/.../ExternalInstrumentReadPort.kt` currently exposes only:
  - `pressureAltitudeM`
  - `totalEnergyVarioMps`
- `feature/flight-runtime/.../weather/wind/data/ExternalAirspeedRepository.kt` already exists as the live external airspeed SSOT
- `feature/flight-runtime/.../weather/wind/data/ExternalAirspeedWritePort.kt` now exists as the narrow live external airspeed write seam
- `feature/variometer/.../LxExternalRuntimeRepository.kt` now publishes LX `LXWP0` airspeed into that seam
- `dfcards-library/.../CardFormatSpec.kt` already renders:
  - `vario`
  - `ias`
  - `tas`
  - `baro_alt`
- `feature/profile/.../BluetoothVarioSettingsUseCase.kt` does not yet expose parsed LX metadata to UI

## Scope Decisions

- Treat the single `LXWP0` airspeed field as `TAS only` for this slice
- Reuse the existing `baro_alt` card instead of introducing a new pressure-altitude card
- Keep `ias` blank for LX TAS-only input
- Keep `ExternalInstrumentReadPort` pressure/TE only; use `ExternalAirspeedWritePort` for live external TAS
- Do not introduce a new `iasDisplayValid` state seam; use non-finite IAS plus existing formatter behavior
- Bluetooth settings metadata UI remains follow-up work, not part of the landed production slice
- Keep replay behavior unchanged

## Ownership

| State | Owner | Notes |
|---|---|---|
| LX Bluetooth session and parsed runtime values | `LxExternalRuntimeRepository` | authoritative LX runtime owner |
| External pressure altitude and TE vario seam | `ExternalInstrumentReadPort` | keep this seam pressure/TE only |
| Live external airspeed sample | `ExternalAirspeedRepository` | canonical live airspeed SSOT |
| Live external airspeed write boundary | `ExternalAirspeedWritePort` | narrow write seam owned by flight-runtime |
| Fused flight metrics | `CalculateFlightMetricsRuntime` -> `FlightDataRepository` | no card-side recomputation |
| Bluetooth settings metadata UI state | `LxBluetoothControlUseCase` | UI-safe derived state |

## Planned Wiring

### 1. Pressure altitude and TE vario

- Keep `LxExternalRuntimeRepository` publishing pressure altitude and TE vario through `ExternalInstrumentReadPort`
- Keep `CalculateFlightMetricsRuntime` authoritative for freshness and fallback
- Keep `SensorFrontEnd.buildSnapshot(...)` authoritative for promoting fresh
  TE into the fused `bruttoVario` / `varioSource = "TE"` path
- Keep `MapScreenUtils` / `FlightDataManager` responsible only for projecting
  fused `displayVario`, `needleVario`, and raw `teVario` into UI flows
- Add explicit downstream state so cards know when pressure altitude is external

### 2. TAS

- Keep the LX-specific publisher near the LX runtime owner
- Publish LX TAS through `ExternalAirspeedWritePort` into `ExternalAirspeedRepository`
- Do not create a second airspeed authority
- Keep TAS-only adaptation in flight-runtime, not in the Bluetooth parser

### 3. IAS behavior

- For LX S100, missing IAS must stay missing
- flight-runtime returns non-finite IAS for TAS-only external samples
- existing `IAS` card formatting already treats non-finite IAS as blank
- Result:
  - flight-runtime does not fabricate IAS
  - `ias` card remains blank for TAS-only LX input without a new projection seam

### 4. Bluetooth settings metadata

- Extend control/use-case state with:
  - product
  - serial
  - software version
  - hardware version
- Keep the screen render-only
- This remains follow-up work; it is not part of the currently landed code

## Data Mapping

| Sentence | Parsed value | Destination | UI surface |
|---|---|---|---|
| `LXWP0` | `totalEnergyVarioMps` | `ExternalInstrumentReadPort` | `vario`, MapScreen main vario, MapScreen TE arc |
| `LXWP0` | `pressureAltitudeM` | `ExternalInstrumentReadPort` | `baro_alt` |
| `LXWP0` | `airspeedKph` | `ExternalAirspeedWritePort` -> `ExternalAirspeedRepository` | `tas` |
| `LXWP1` | `product/serial/software/hardware` | control/use-case mapping | Bluetooth settings |

Important ownership note:

- raw LX TE reaches the MapScreen widget in two ways:
  - as promoted fused main vario through `displayVario` / `needleVario`
  - as raw `teVario` for the outer arc
- the MapScreen Levo secondary label remains `levoNetto`, computed in
  `CalculateFlightMetricsRuntime` from `baselineVario`, TAS, wind, and polar
  conditions

Out of scope for this slice:

- `LXWP2`
- `LXWP3`
- `PLXVF`
- `PLXVS`

Reference:

- `docs/BLUETOOTH/02_LX_S100_SENTENCE_CAPABILITIES_2026-04-10.md`

## File Plan

Implemented files:

- `feature/variometer/.../LxExternalRuntimeRepository.kt`
- `feature/flight-runtime/.../weather/wind/data/ExternalAirspeedWritePort.kt`
- `feature/flight-runtime/.../di/ExternalAirspeedModule.kt`
- `feature/flight-runtime/.../weather/wind/model/WindInputs.kt`
- `feature/flight-runtime/.../CalculateFlightMetricsRuntime.kt`
- `feature/flight-runtime/.../FusionBlackboard.kt`
- `feature/map/.../IgcRecordingUseCase.kt`
- `feature/variometer/.../LxBluetoothControlState.kt`
- `feature/variometer/.../LxBluetoothControlUseCase.kt`
- `docs/ARCHITECTURE/PIPELINE.md`

Follow-up files if the remaining work proceeds:

- `dfcards-library/.../CardFormatSpec.kt`
- `dfcards-library/.../CardDataFormatterTest.kt`
- `feature/profile/.../BluetoothVarioSettingsUseCase.kt`
- `feature/profile/.../BluetoothVarioSettingsScreen.kt`
- `feature/variometer/.../LxSentenceParser.kt` only if broader LX sentence support is explicitly in scope

## Test Plan

- `feature:variometer`
  - LX runtime publishes TAS, pressure altitude, TE vario, metadata
  - LX runtime clears values on disconnect/error
- `feature:flight-runtime`
  - LX TAS drives `trueAirspeed`
  - TAS-only LX input keeps `IAS` non-finite
  - replay still ignores live LX data
- `dfcards-library`
  - `vario` renders LX TE path
  - MapScreen variometer main readout/needle reflect LX TE when active
  - MapScreen TE outer arc reflects raw `teVario`
  - MapScreen Levo secondary label stays separate from raw TE
  - `tas` renders LX-fed TAS
  - `ias` stays blank for TAS-only LX input
- targeted proof should also cover non-default user speed units
- `feature:profile`
  - settings UI shows parsed metadata when available

## Verification

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Acceptance Gates

- No duplicate SSOT owners introduced
- `ExternalInstrumentReadPort` remains pressure altitude and TE only
- Live LX TAS uses `ExternalAirspeedWritePort` -> `ExternalAirspeedRepository`
- `ias` does not falsely display LX TAS-only data
- `vario`, `tas`, and `baro_alt` show LX-fed values
- if metadata UI remains in scope, Bluetooth settings show parsed LX identity
- Replay remains deterministic

## Narrow Seam Review Follow-Up

The 2026-04-12 narrow seam/code pass found:

- the current runtime seam is correct for the intended v1 goal of using S100 as
  a higher-quality live TAS / pressure altitude / TE source
- XCPro still does not use all S100 sentence families; `LXWP2`, `LXWP3`,
  `PLXVF`, and `PLXVS` remain unsupported by design
- external Bluetooth airspeed is still not distinguishable from wind-estimated
  airspeed in card subtitles/source labels
- an explicit TAS-only + non-default-units card test is still missing
