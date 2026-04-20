# Condor protocol and XCSoar semantics

## Sentence set that matters

Condor’s NMEA output supports these sentences:

- `GPGGA`
- `GPRMC`
- `LXWP0`

That means the incoming stream is not just basic GPS. It includes an LX-style extension sentence carrying important soaring/simulator data.

## Why generic NMEA is not enough

If XCPro only reads `GGA` / `RMC`, it may get position, groundspeed, course, and time, but it will miss Condor-specific soaring data carried in `LXWP0`.

Worse, XCSoar has a dedicated Condor driver because Condor’s semantics differ from generic LX devices in ways that matter for correct results.

## What XCSoar’s Condor driver actually does

The XCSoar Condor driver parses `LXWP0` and uses it to supply:

- barometric altitude
- true airspeed
- total-energy vario
- external wind

That is the reason the Condor path is not equivalent to “generic TCP GPS”.

## Important Condor-specific behavior

### 1) The Condor manual explicitly says to choose the Condor driver for correct altitude readings
That is not cosmetic. It means Condor needs dedicated interpretation.

### 2) XCSoar’s Condor driver treats field 1 as TAS
The source comment is explicit that Condor uses TAS there, not IAS.

### 3) XCSoar’s Condor driver flips wind direction for Condor 1.x / Condor 2
The source comment is explicit that Condor 1.1.4 and Condor 2 output the wind direction the wind is going **to**, not the direction it is coming **from**. XCSoar corrects this by taking the reciprocal.

### 4) Condor has its own dedicated driver separate from the generic LX parser
That is the strongest signal that XCPro should not just bolt Condor onto a generic parser path and hope for parity.

## LXWP0 field map used by XCSoar

Based on XCSoar source, the key `LXWP0` fields are interpreted like this:

| Field index | Meaning in XCSoar Condor handling | Notes |
|---|---|---|
| 0 | logger stored | not central here |
| 1 | airspeed | Condor uses **TAS** |
| 2 | baro altitude | handled with Condor-specific altitude semantics |
| 3 | total-energy vario | important for soaring display / calculations |
| 4-8 | unknown / ignored here | not needed for the minimum path |
| 9 | heading of plane | present in source comment |
| 10 | wind course | corrected for Condor 2 |
| 11 | wind speed | KPH in source comment |

## Example LXWP0 line

Example from XCSoar source:

```text
$LXWP0,Y,222.3,1665.5,1.71,,,,,,239,174,10.1
```

This is useful as a parser sanity fixture, but it should not be your only test data.

## What XCPro should do with the stream

### Standard NMEA path
Use the standard sentences in the feed for:
- fix / position
- UTC time / date
- ground track / ground speed

### Condor extension path
Use `LXWP0` for:
- baro altitude
- true airspeed
- total-energy vario
- heading / wind information as required by the app’s data model

### Condor 2 normalization
Before the rest of the app sees the data:
- apply the Condor-specific wind correction
- preserve Condor-specific altitude handling
- preserve TAS semantics
- keep one explicit simulator heading authority

## Architecture implication

The parser layer should produce a **normalized simulator ownship model**.

Do not leak raw NMEA parsing details directly into map/UI code.

Recommended pattern:

```text
TCP listener
-> line decoder
-> sentence parser
-> Condor normalizer
-> simulator ownship frame
-> flight-runtime integration
```

## What not to do

### Do not do this
- parse a few NMEA lines in a ViewModel
- inject simulator socket code into the map feature
- keep phone GPS active and “whichever updates last wins”
- treat `LXWP0` as optional if the goal is XCSoar parity

### Do this
- keep a dedicated simulator-owned parser/normalizer seam
- make Condor mode an explicit source mode
- test against real captured Condor output
- compare XCPro against XCSoar side by side

## Minimum parity target

To claim parity with the working XCSoar setup, XCPro should be able to consume the same incoming Condor stream and produce the same practical categories of ownship data:

- position
- track / course
- speed
- altitude
- vario
- wind
- simulator-driven heading behavior where relevant
