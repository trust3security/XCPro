# Phase 7 Round-Trip Tolerance Matrix

Purpose:

- document tolerance assumptions used in round-trip conversion tests

| Test | Fixture Shape | Assertion | Tolerance / Constraint |
| --- | --- | --- | --- |
| `IgcTextWriterTest.writerRoundTrip_preservesCoordinatesAltitudesAndTimeWithinTolerance` | generated canonical B lines with IAS/TAS extensions | first/second point round-trip parity | time delta exactly 5s, lat/lon +/- 0.0002 deg, altitude exact checks |
| `IgcTextWriterTest.writerRoundTrip_formatterToWriterToParser_preservesIasTas` | single line IGC text through writer + parser | IAS/TAS values preserved | exact match after one-hop serialization |
| `IgcTextWriterTest.writeLines_largeFileStress_hasNoBareLineEndings_andParses` | 10,000 B records | parser accepts canonical large payload | 10,000 points parsed, CRLF integrity preserved |
| `IgcTextWriterTest.writeLines_writesCanonicalCrlfOnly` | basic canonical line set | canonical newlines | no bare LF/CR; exact trailing CRLF count |

## Determinism Checks

- The round-trip tests are deterministic by design by asserting repeated writer outputs equal and by fixed fixtures.
- All round-trip parsing in this section uses `IgcParser` with injected fixed clock `FakeClock`.
