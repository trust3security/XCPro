# OGN_APRS_TEST_VECTORS.md - APRS Parser Vectors (Current Code)

Purpose:
Reference APRS frames and expected parser behavior for `OgnAprsLineParser`.

Primary test file:
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnAprsLineParserTest.kt`

## Vector 1 - Canonical Aircraft Beacon

Frame:
`FLRDDDEAD>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0ADDDEAD -454fpm -1.1rot 8.8dB`

Expected:
- parsed target emitted
- callsign: `FLRDDDEAD`
- destination: `APRS`
- lat/lon: `50.497666`, `9.949666`
- altitude: `1683.7152 m`
- track: `342.0 deg`
- speed: `25.207756 m/s`
- vertical speed: `-2.30632 m/s`
- device id: `DDDEAD`
- signal: `8.8 dB`

## Vector 2 - Receiver To-Call (`OGNSDR`)

Frame:
`LILH>OGNSDR,TCPIP*,qAC,GLIDERN2:/132201h4457.61NI00900.58E&/A=000423`

Expected:
- ignored (`destination == OGNSDR`)

## Vector 3 - Status Message Payload

Frame:
`LKHE>OGNSDR,qAS,glidern5:>164507h CpuLoad=0.10 GPS: -0.4 / 6 satellites`

Expected:
- ignored (payload type not in `!`, `=`, `/`, `@`)

## Vector 4 - Backslash Symbol Table Position

Frame:
`ICA4CA6A4>OGADSB,qAS,SpainAVX:/091637h3724.87N\00559.81W^085/165/A=001275 id254CA6A4 -832fpm 0rot fnA3:RYR5VV regEI-DYO modelB738`

Expected:
- parsed target emitted
- callsign: `ICA4CA6A4`
- destination: `OGADSB`
- lat/lon: `37.4145`, `-5.996833`
- ground speed: `84.88326 m/s`
- vertical speed: `-4.22656 m/s`
- device id: `4CA6A4`

## Vector 5 - Device Id Fallback From Callsign

Frame:
`ICA484D20>APRS,TCPIP*,qAC,GLIDERN1:!4903.50N/07201.75W^110/064/A=001000`

Expected:
- parsed target emitted
- device id extracted from callsign suffix: `484D20`

## Notes

- Parser is intentionally conservative:
  - ignores unsupported frames
  - keeps unknown comment tokens as raw text
- Parsing uses tolerant regex extraction for known fields.
