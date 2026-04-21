# XCPro Condor TCP / Wi-Fi Connectivity Pack

## Goal

Make **XCPro** connect to **Condor on a PC** the same way **XCSoar** already does in the working setup:

```text
Condor on PC
-> NMEA serial output
-> virtual COM bridge on Windows
-> TCP over local Wi-Fi
-> Android phone running XCPro
-> XCPro ingests the same Condor feed XCSoar uses
```

This pack is not code. It is the research + architecture pack needed so Codex can implement the feature correctly instead of bolting on a random socket reader.

## Bottom line

XCPro does **not** just need “TCP support”.

To match the working XCSoar path, XCPro needs all of the following:

1. **A phone-side incoming TCP listener** on a configurable port, with `4353` as the practical default.
2. **Full NMEA stream ingestion** for the Condor feed.
3. **Condor-specific LXWP0 handling**, not just generic NMEA parsing.
4. **Condor 2 compatibility logic**, especially the wind-direction correction and Condor-specific altitude handling.
5. **Single-source ownership in simulator mode**, so phone GPS / compass do not silently fight the Condor stream.
6. **Clear connection UX**, because this mode depends on local IP, firewall state, port choice, and stream health.

## What the working XCSoar setup proves

The working XCSoar path proves that the following model works in practice:

```text
PC side:
  Condor -> COM port -> HW VSP3 -> TCP client connection to phone

Phone side:
  XCSoar -> TCP port configured -> Condor driver selected
```

The PC bridge is external. XCSoar itself does not implement HW VSP3. It simply exposes a TCP device endpoint and applies Condor-specific parsing/normalization to the incoming stream.

XCPro should do the same.

## What must be preserved in XCPro

### Preserve the external-bridge model
Do **not** make XCPro depend on HW VSP3 specifically.  
Support a **generic incoming TCP/NMEA stream**. That allows:

- HW VSP3
- com0com + hub4com
- custom Python bridge
- future Bluetooth-serial bridge
- any other COM->TCP bridge

### Preserve Condor-specific semantics
Do **not** treat this as “just another GPS source”.

Condor sends standard NMEA plus the LXWP0 extension, and XCSoar has a dedicated Condor driver because Condor’s semantics are not identical to a generic LX device.

### Preserve architecture integrity
The transport and parser should be simulator-owned, not map-owned and not UI-owned.

## Pack contents

- `01_HOW_THE_XCSOAR_PATH_WORKS.md`
- `02_CONDOR_PROTOCOL_AND_XCSOAR_SEMANTICS.md`
- `03_XCPRO_PHASED_IP_CONDOR_TCP_LISTEN_MODE.md`
- `04_VALIDATION_AND_GOLDEN_CAPTURE_PLAN.md`
- `05_CODEX_PROMPT_READY.md`
- `99_REFERENCES.md`

## Recommended usage

1. Read `01` and `02` first so the data path and protocol are fully understood.
2. Use `03` as the implementation contract / phase IP.
3. Use `04` to force proof, fixture capture, and side-by-side validation against XCSoar.
4. Paste `05` into Codex to drive the implementation.
5. Keep `99` in the repo docs pack for source traceability.

## The hard truth

If XCPro only adds a raw TCP socket and pipes lines somewhere loose in the app, it will probably “connect” but still be wrong.

The actual target is:

- same connection model as XCSoar
- same Condor sentence handling
- same source ownership model while simulator mode is active
- same effective ownship / nav inputs reaching the rest of the app
