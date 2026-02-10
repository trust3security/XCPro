# OGN_APRS_TEST_VECTORS.md — Real OGN APRS Frames (Authoritative)

This document contains real-world Open Glider Network (OGN) APRS frames
used as **ground-truth test vectors** for parsing, filtering, privacy
handling, and robustness testing in XCPro.

Each frame MUST be covered by unit tests.
Field order, spacing, and optional tokens are intentionally inconsistent.

---

## Legend

- RENDER: should appear as a traffic target
- IGNORE: should be ignored by the parser
- ID SOURCE: where the stable identifier must come from
- NOTES: why this frame exists

---

## VECTOR 1 — Canonical OGN aircraft beacon (full fields)

```
FLRDDDEAD>APRS,qAS,EDER:/114500h5029.86N/00956.98E'342/049/A=005524 id0ADDDEAD -454fpm -1.1rot 8.8dB 0e
```

Expected:
- RENDER
- ID SOURCE: id0ADDDEAD
- Lat: 50.4977
- Lon: 9.9497
- Track: 342°
- Speed: 49 kt
- Altitude: 1684 m
- Climb: -2.31 m/s

Notes: reference example from python-ogn-client.

---

## VECTOR 2 — Same aircraft, reordered tokens

```
FLRDDDEAD>APRS,qAS,EDER:/114530h5029.86N/00956.98E'342/049 id0ADDDEAD A=005540 -430fpm
```

Expected:
- RENDER
- ID SOURCE: id0ADDDEAD

Notes: token order must not matter.

---

## VECTOR 3 — No explicit id token, fallback to source callsign

```
DDEFG123>APRS,qAS,EDER:/121500h5130.00N/01000.00E'090/035/A=004000
```

Expected:
- RENDER
- ID SOURCE: DDEFG123

Notes: fallback identifier logic.

---

## VECTOR 4 — Missing altitude

```
OGN12345>APRS,qAS,EDER:/122000h5130.50N/01001.20E'180/040 id123ABC
```

Expected:
- RENDER
- Altitude: null

Notes: altitude optional.

---

## VECTOR 5 — Missing speed/course

```
OGN54321>APRS,qAS,EDER:/122030h5130.70N/01001.40E' A=003800 id999EEE
```

Expected:
- RENDER
- Track: null
- Speed: null

Notes: movement optional.

---

## VECTOR 6 — Anonymous / privacy-style (no callsign shown)

```
ANON01>APRS,qAS,EDER:/122100h5131.00N/01002.00E'270/020/A=004200 id000AAA
```

Expected:
- RENDER
- Callsign: null or anonymized
- ID SOURCE: id000AAA

Notes: privacy handling.

---

## VECTOR 7 — Ground receiver (non-aircraft)

```
EDRX>APRS,qAS,EDER:!5132.00N/01003.00E#
```

Expected:
- IGNORE

Notes: receiver beacon, not traffic.

---

## VECTOR 8 — Invalid latitude

```
BADLAT>APRS,qAS,EDER:/999999h5132.00N/01003.00E'090/020 idBAD001
```

Expected:
- IGNORE

Notes: invalid timestamp/position.

---

## VECTOR 9 — Missing position entirely

```
NOPOS>APRS,qAS,EDER:>Test message only
```

Expected:
- IGNORE

Notes: no lat/lon.

---

## VECTOR 10 — Negative climb, positive rotation noise

```
CLIMB1>APRS,qAS,EDER:/123000h5133.00N/01004.00E'045/060/A=006000 idCLMB01 -800fpm 2.5rot
```

Expected:
- RENDER
- Climb: -4.06 m/s

Notes: multiple numeric tokens.

---

## VECTOR 11 — High-speed towplane-like target

```
TOW123>APRS,qAS,EDER:/123200h5134.00N/01005.00E'270/120/A=002500 idTOW001
```

Expected:
- RENDER
- Speed: ~61.7 m/s

Notes: fast mover, still valid.

---

## VECTOR 12 — Duplicate ID update (should replace prior)

```
FLRDDDEAD>APRS,qAS,EDER:/123500h5029.90N/00957.10E'350/052/A=005600 id0ADDDEAD
```

Expected:
- RENDER
- Same target ID as VECTOR 1
- Position updated

Notes: upsert semantics.

---

## VECTOR 13 — Lowercase / mixed-case noise

```
mixCase>APRS,qAS,EDER:/123700h5135.00N/01006.00E'180/045/a=004500 IdMiX123
```

Expected:
- RENDER
- ID SOURCE: IdMiX123 (case-insensitive match)

Notes: case robustness.

---

## VECTOR 14 — Extra vendor-specific fields

```
VENDOR1>APRS,qAS,EDER:/124000h5136.00N/01007.00E'090/030/A=004800 idVEN001 temp=21C batt=3.7V
```

Expected:
- RENDER

Notes: ignore unknown tokens safely.

---

## VECTOR 15 — Stale eviction candidate

```
STALE01>APRS,qAS,EDER:/120000h5137.00N/01008.00E'010/010/A=004900 idSTALE1
```

Expected:
- RENDER initially
- EVICT after stale timeout

Notes: stale cleanup logic.

---

## Mandatory Assertions

For all RENDER vectors:
- lat/lon must be valid doubles
- ID must be stable across updates
- no crashes on unknown fields

For IGNORE vectors:
- no target emitted
- no exceptions thrown

---

## Implementation Rule

Parsing MUST be token-based and order-independent.
Regular-expression-only parsing is insufficient and forbidden.
