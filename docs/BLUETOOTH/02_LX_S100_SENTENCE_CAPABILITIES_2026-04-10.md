# LX S100 / S10 Sentence Capability Reference

## Metadata

- Date: 2026-04-10
- Status: Draft reference

## Purpose

This document records:

- what the LXNAV S100 / S10 family can emit in the LX sentence stream
- what XCPro currently parses
- whether XCPro needs separate parsers per LX device

## Short Answer

- `S100` and `S10` are in the same LXNAV `S8x/S10x` family
- they should use the same base `LXWP*` parser in XCPro
- XCPro should identify the connected model using `LXWP1.product`
- extra support should be added sentence-by-sentence, not by creating
  `S100Parser`, `S10Parser`, etc.

## Sentence Family

For the S100 / S10 family, the relevant sentence family is:

- `LXWP0`
- `LXWP1`
- `LXWP2`
- `LXWP3`

Other LX-family devices may additionally use:

- `PLXVF`
- `PLXVS`
- `PLXVC`
- `PLXV0`

Those should be treated as additive handlers for broader LX support, not as proof that S100 and S10 need separate parsers.

## Field Reference

### `LXWP0` - Primary flight data

Representative shape:

```text
$LXWP0,Y,222.3,1665.5,1.71,,,,,,239,174,10.1
```

Field-level reference:

| Field index | Meaning | XCPro today |
|---|---|---|
| `0` | logger stored flag (`Y/N`) | ignored |
| `1` | airspeed in kph | parsed as `airspeedKph`; published into the live external airspeed path as an IAS-first partial sample |
| `2` | pressure altitude in m | parsed as `pressureAltitudeM`; published into the external instrument pressure seam |
| `3` | vario sample in m/s | parsed as `totalEnergyVarioMps`; published into the external instrument TE seam, promoted into fused main vario when fresh, and also exposed to the MapScreen TE outer arc |
| `4..8` | additional recent vario samples | ignored |
| `9` | heading | ignored |
| `10` | wind course | ignored |
| `11` | wind speed | ignored |

Important ambiguity:

- XCSoar's comment labels field `1` as IAS
- XCSoar then feeds it into true-airspeed handling
- for the current XCPro live slice, this field is treated as `IAS-first`
  external input and any missing TAS is derived centrally in flight-runtime
- when a fresh `PLXVF` IAS sample exists, XCPro prefers that value over
  `LXWP0[1]` for the active external airspeed sample

Important TE ownership note:

- `LXWP0[3]` does drive XCPro's main variometer path when it is the freshest
  valid TE source
- the MapScreen TE outer arc remains TE-only and reads this confirmed TE source

### `LXWP1` - Device info

| Field index | Meaning | XCPro today |
|---|---|---|
| `0` | product / instrument ID | parsed; surfaced in Bluetooth settings detail sections |
| `1` | serial number | parsed; surfaced in Bluetooth settings detail sections |
| `2` | software version | parsed; surfaced in Bluetooth settings detail sections |
| `3` | hardware version | parsed; surfaced in Bluetooth settings detail sections |
| `4` | license string | not parsed |

`LXWP1.product` is the right place to distinguish `S100` vs `S10` in UI and logging.

### `LXWP2` - Settings/state exchange

| Field index | Meaning | XCPro today |
|---|---|---|
| `0` | MacCready | parsed; published as a live external MC override and used by MC/STF surfaces |
| `1` | ballast | parsed as ballast overload factor; used by `BALLAST_FACTOR` and the read-only external ballast widget |
| `2` | bugs | parsed; published as a live external bugs override and used by bugs/polar runtime consumers |
| `3` | polar A | parsed; surfaced in Bluetooth settings detail sections only |
| `4` | polar B | parsed; surfaced in Bluetooth settings detail sections only |
| `5` | polar C | parsed; surfaced in Bluetooth settings detail sections only |
| `6` | audio volume | parsed; surfaced in Bluetooth settings detail sections only |

### `LXWP3` - QNH / altitude-offset and SC config

| Field index | Meaning | XCPro today |
|---|---|---|
| `0` | altitude offset | parsed; converted to a derived live external QNH override |
| `1` | SC mode | parsed; surfaced in Bluetooth settings detail sections only |
| `2` | vario filter | parsed; surfaced in Bluetooth settings detail sections only |
| `3` | TE filter | parsed; surfaced in Bluetooth settings detail sections only |
| `4` | TE level | parsed; surfaced in Bluetooth settings detail sections only |
| `5` | vario average | parsed; surfaced in Bluetooth settings detail sections only |
| `6` | vario range | parsed; surfaced in Bluetooth settings detail sections only |
| `7` | SC tab | parsed; surfaced in Bluetooth settings detail sections only |
| `8` | SC low | parsed; surfaced in Bluetooth settings detail sections only |
| `9` | SC speed | parsed; surfaced in Bluetooth settings detail sections only |
| `10` | SmartDiff | parsed; surfaced in Bluetooth settings detail sections only |
| `11` | glider name | parsed; surfaced in Bluetooth settings detail sections only |
| `12` | time offset | parsed; surfaced in Bluetooth settings detail sections only |

### `PLXVF` - Extended sVario/V7 flight data

Representative shape:

```text
$PLXVF,time,AccX,AccY,AccZ,Vario,IAS,PressAlt
```

Potential values:

- accelerometer channels
- vario / netto
- IAS
- pressure altitude

Current field handling:

| Field index | Meaning | XCPro today |
|---|---|---|
| `0` | time | ignored |
| `1` | `AccX` | ignored |
| `2` | `AccY` | ignored |
| `3` | `AccZ` | ignored |
| `4` | `Vario` | parsed as provisional `externalVarioMps`; promoted to the fused main vario/audio path when fresh and confirmed TE is absent |
| `5` | `IAS` | parsed as `indicatedAirspeedKph`; published into the live external airspeed path as IAS-only input |
| `6` | `PressAlt` | parsed as `pressureAltitudeM`; published into the external instrument pressure seam |

Important ownership note:

- XCPro treats `PLXVF[4]` as provisional generic external vario, not as
  confirmed TE.
- Confirmed TE ownership remains on `LXWP0[3]`.

### `PLXVS` - Extended status

Representative shape:

```text
$PLXVS,OAT,mode,voltage
```

Potential values:

- outside air temperature
- flight mode
- voltage

XCPro today:

| Field index | Meaning | XCPro today |
|---|---|---|
| `0` | OAT | parsed; published to the `OAT` card through the live external-settings seam |
| `1` | mode | parsed; surfaced in Bluetooth settings detail sections only |
| `2` | voltage | parsed; surfaced in Bluetooth settings detail sections only |

## XCPro Current Coverage

Current parser status in `LxSentenceParser.kt`:

| Sentence | Status |
|---|---|
| `LXWP0` | supported |
| `LXWP1` | supported |
| `LXWP2` | supported |
| `LXWP3` | supported |
| `PLXVF` | supported for vario / IAS / pressure altitude |
| `PLXVS` | supported |

Current production-use status:

- `LXWP0`
  - pressure altitude: used
  - TE vario: used
  - airspeed: used as IAS-first live input
- `LXWP1`
  - parsed and surfaced in Bluetooth settings UI
- `PLXVF`
  - pressure altitude: used
  - provisional external vario/audio source: used
  - IAS: used
- `LXWP2`
  - MC: used
  - ballast overload factor: used for display/widget surfaces
  - bugs: used
  - polar/audio-volume details: diagnostics/settings only
- `LXWP3`
  - altitude offset: used to derive live external QNH
  - SC/filter/config details: diagnostics/settings only
- `PLXVS`
  - OAT: used
  - mode/voltage: diagnostics/settings only

## What The S100 / S10 Family Can Provide

Assume the family can provide at least:

- airspeed
- pressure altitude
- TE / vario-related vertical speed
- product / serial / firmware / hardware identity
- MacCready
- ballast
- bugs
- polar coefficients
- audio volume
- altitude-offset / QNH-related information

Potentially available in wider LX variants:

- heading
- wind direction
- wind speed
- OAT
- voltage
- flight mode
- acceleration-derived channels

## Parser Strategy Recommendation

- Keep one shared LX parser keyed by sentence ID
- Keep one shared LX runtime repository
- Use `LXWP1.product` to identify the connected model
- Add support by sentence:
  - `LXWP2` for MC/ballast/bugs/settings
  - `LXWP3` for QNH/altitude-offset handling
  - `PLXVS` for OAT/mode/voltage

Do not:

- create `S100Parser` and `S10Parser` by default
- assume all LX airspeed fields have the same IAS/TAS meaning without validation
- treat `PLXVF` vario as confirmed TE without real-device validation

## Answer To The Device Question

For `S100` vs `S10`:

- same parser family: `Yes`
- separate parser per device: `No`

The likely future split is not per model. It is:

- one `LXWP*` parser path for S8x/S10x family devices
- optional extra handlers for `PLXV*` / `PLXVC` on other LX-family variants

## Sources

Official/manual references:

- LXNAV S8x/S10x manual:
  - https://gliding.lxnav.com/wp-content/uploads/manuals/S8x-S10xManualEnglishRev72.pdf
- LXNAV manuals index:
  - https://gliding.lxnav.com/lxdownloads/manuals/?product=LX+Styler

Field-level/code references:

- [LxSentenceParser.kt](C:/Users/Asus/AndroidStudioProjects/XCPro/feature/variometer/src/main/java/com/trust3/xcpro/variometer/bluetooth/lxnav/LxSentenceParser.kt)
- [Parser.cpp](C:/Users/Asus/AndroidStudioProjects/XCSoar/src/Device/Driver/LX/Parser.cpp)
- [DeviceInfo.hpp](C:/Users/Asus/AndroidStudioProjects/XCSoar/src/NMEA/DeviceInfo.hpp)
