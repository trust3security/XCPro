# Codex prompt ready

Paste this into Codex.

---

Implement an architecture-correct **Condor TCP listen mode** for XCPro so XCPro can connect to Condor on a Windows PC the same way XCSoar currently does via a COM->TCP bridge such as HW VSP3.

Read and follow these docs in this pack first:

- `README_CURRENT.md`
- `01_HOW_THE_XCSOAR_PATH_WORKS.md`
- `02_CONDOR_PROTOCOL_AND_XCSOAR_SEMANTICS.md`
- `03_XCPRO_PHASED_IP_CONDOR_TCP_LISTEN_MODE.md`
- `04_VALIDATION_AND_GOLDEN_CAPTURE_PLAN.md`
- `99_REFERENCES.md`

## Goal

XCPro must support this external workflow:

```text
Condor -> Windows COM/NMEA output -> external COM->TCP bridge -> Android phone -> XCPro
```

XCPro must behave like the working XCSoar setup, not just open a raw socket.

## Non-negotiable requirements

1. Add an explicit **Condor TCP** mode with a configurable listen port, default `4353`.
2. The Android app must act as the **incoming TCP listener** for this mode.
3. Keep transport/parsing **simulator-owned**, not map-owned and not UI-owned.
4. Preserve **Condor-specific semantics**, not generic NMEA only.
5. Prevent silent source fights with phone GPS / compass when Condor mode is active.
6. Add clear connection state UX.
7. Add tests and capture-driven validation hooks.

## Architecture constraints

- `feature:profile` owns persisted settings and user-facing configuration.
- recommended new `feature:simulator` owns transport, parser, normalization, and session health.
- `feature:flight-runtime` consumes normalized simulator ownship through a narrow port and remains SSOT.
- `app` owns DI and lifecycle actuation only.
- `feature:map` remains a pure consumer.

Fail the implementation if socket code or raw NMEA parsing leaks into map/UI layers.

## Protocol constraints

Condor emits:
- `GPGGA`
- `GPRMC`
- `LXWP0`

You must preserve Condor/XCSoar semantics, including:
- dedicated Condor handling for correct altitude behavior
- `LXWP0` field 1 treated as TAS
- Condor 2 wind-direction reciprocal correction
- simulator-owned heading / direction authority policy

## Required outputs

1. code implementation
2. tests
3. updated docs / ADR / pipeline notes
4. a short proof markdown showing:
   - what changed
   - where ownership lives
   - how it was tested
   - PASS / FAIL against the contracts

## Implementation sequence

Do the work in narrow phases:

### Phase 0
Lock/update docs and contracts first.

### Phase 1
Add persisted settings and source mode.

### Phase 2
Add or extend live source resolution so Condor mode is explicit and authoritative when healthy.

### Phase 3
Implement simulator-owned TCP listen transport, line decoding, parser, and normalization.

### Phase 4
Wire normalized simulator ownship into flight-runtime via a narrow seam.

### Phase 5
Add UI/UX for status and diagnostics.

### Phase 6
Add tests and proof docs.

## Required proof

Provide:
- file-by-file change summary
- architecture ownership summary
- acceptance checklist results
- gaps / risks if any remain

Do not hand-wave. If something is uncertain, say so explicitly.
