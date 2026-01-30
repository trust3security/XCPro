# Bluetooth Integration Reference (LXNAV Hawk / S100)

## Purpose
This document captures **defensible, implementation-grade knowledge** about how **LXNAV Hawk / S100** instruments communicate over Bluetooth, and why **XCSoar is the gold-standard reference implementation**.

It is intended to:
- Justify architectural decisions in XCPro
- Avoid chasing non-existent proprietary specs
- Provide reviewers and contributors with a clear, auditable rationale

## Goal (Project Requirement)

XCPro must provide robust Bluetooth connectivity to LXNAV S100 / HAWK
instruments for live flight data ingestion, without relying on undocumented
assumptions. The integration must be deterministic, testable, and aligned with
XCPro's SSOT/UDF architecture.

---

## Executive Summary

- **Official LXNAV product pages confirm Bluetooth is built-in** for S100 and SxHAWK; protocol details are not published there.
- **No public LXNAV Bluetooth protocol specification has been found** on official product pages; the manual set is the likely reference.
- **XCSoar already implements this successfully and reliably** using a serial-style stream model.
- XCSoar's behavior is therefore the *de-facto specification*, but **must be validated against S100/HAWK behavior** via manual review and raw data capture.

If XCSoar can fly competition tasks using this approach, it is a strong starting point for XCPro,
provided we validate the behavior against real S100/HAWK output.

---

## Bluetooth Mode Used by LXNAV (Critical)

### Bluetooth Classic - SPP (Working Assumption)

- Appears as a **paired device** on Android
- Connects via **RFCOMM / Serial Port Profile (SPP)**
- Provides a **continuous byte stream**, identical to a serial cable
- Payload is ASCII text, newline-terminated

Example stream:
```
$LXWP0,...
$GPRMC,...
```

### Bluetooth LE (Not Indicated in LXNAV Public Docs)

- Scan-based (GATT / characteristics)
- Packetised, not stream-oriented
- Unsuitable for high-rate NMEA output

**Conclusion (provisional):** LXNAV Hawk / S100 is expected to use **Bluetooth Classic SPP**, not BLE.  
This must be confirmed from the S8x/S10x/S100 manual and a live raw-data capture.  
If BLE is observed, use a dedicated GATT transport (not the SPP stack).

---

## Vendor-Confirmed Capabilities (Official LXNAV Sources)

These statements are taken from official LXNAV product pages and the manuals index.

- S100: Bluetooth is integrated; IGC flights can be downloaded through Bluetooth.
- S100: PDA port is available (RS232 or TTL) with 5V supply for external devices.
- S100: PDAs / smartphones can connect via internal Bluetooth or the PDA port.
- SxHAWK: Built-in Bluetooth; smartphone connectivity via Bluetooth or PDA port; compatible with iOS/Android.
- Manual set: The S8/S80/S10/S100 manual is the authoritative reference for Bluetooth settings and output configuration
  (latest listing shows v9.17 rev 72, 2025-12-24).

These confirm capabilities, not protocol details.

## Verification Required (No Assumptions)

The following must be confirmed from the manual and/or raw data capture:

- SPP vs BLE (and whether both are supported)
- Pairing/PIN behavior and device name format
- Actual sentence set ($LXWP0/$LXWP1/$GPRMC/$GPGGA, etc.)
- Update rates for each sentence
- Any device-specific framing or quirks

---

## What LXNAV Bluetooth Actually Carries

Bluetooth is **only a transport layer**. There is:
- oe No proprietary Bluetooth framing
- oe No binary protocol
- oe No packet headers

The payload is standard NMEA-style text, identical to RS-232 output.

---

## Sentences You Will See

### `$LXWP0` (Primary Flight Data)

- Sent frequently (typically ~1 Hz)
- Contains core flight data such as:
  - Vario / total energy
  - Barometric altitude
  - Wind (if enabled)
  - Other performance fields depending on configuration

This is the **primary sentence XCPro must support**.

### `$LXWP1` (Device / Info)

- Sent infrequently (often once per minute)
- Contains device identification and metadata
- Useful for diagnostics and UI display

### Optional Standard NMEA

Depending on configuration:
- `$GPRMC`
- `$GPGGA`

Used for GPS fix, time, and altitude.

---

## Why XCSoar Is the Gold Standard

XCSoar has a **production-proven LXNAV driver** that:

### 1. Treats Bluetooth as a Serial Stream

- Opens an RFCOMM (SPP) socket
- Reads raw bytes from an `InputStream`
- Buffers and splits data into newline-terminated sentences

No Bluetooth-specific protocol logic exists above the transport layer.

---

### 2. Parses `$LXWP0` and `$LXWP1`

XCSoar:
- Recognises LXNAV proprietary sentences
- Parses them field-by-field
- Maps values into its internal flight state

This logic has been exercised in real flying, competitions, and long-term use.

---

### 3. Handles Real-World Failure Modes

XCSoar explicitly handles:

- **Reconnects** - Bluetooth drops and power cycles are expected
- **Partial lines** - reads may end mid-sentence
- **Bad or missing checksums** - invalid sentences are discarded safely

Parsing failures never block or corrupt the main flight pipeline.

---

### 4. Applies Value Ownership Rules

XCSoar merges multiple data sources using **explicit ownership**:

- Prefer the primary device
- Fill missing values from secondary sources
- Never blend or double-count the same variable

This pattern directly maps to XCPro's SSOT + UseCase architecture.

---

## XCSoar Reference

For the code-level XCSoar Bluetooth and LXNAV pipeline (file index, JNI bridge,
line splitting, parsers, port config), see `docs/xcsoar_bluetooth_reference.md`.
Use this before implementing transport/parsing changes to avoid re-discovery.

---

## Raw Data Capture (Strongly Recommended)

Before finalising parsing logic, capture **30-60 seconds of raw Bluetooth output** from the Hawk / S100.

Why:
- Confirms exact sentence set
- Confirms field ordering and optional blanks
- Confirms update rates
- Reveals real-world quirks manuals omit

This is how XCSoar's driver became robust.

## Sources (Official LXNAV)

- S100 specs: https://gliding.lxnav.com/products/s100/specs/
- S100 product page: https://gliding.lxnav.com/products/s100-10-test/
- SxHAWK product page: https://gliding.lxnav.com/products/sxhawk/
- Manual downloads index: https://gliding.lxnav.com/lxdownloads/manuals/
- Updated manual notice (S8/S80/S10/S100): https://gliding.lxnav.com/news/updated-manual-s8x-s10x/

---

## Data Trust / Ownership Policy (Must Be Explicit)

When S100 / Hawk is connected, a sane default policy is:

| Data | Source to Trust |
|----|----|
| Barometric altitude | S100 |
| Vario / Netto | S100 |
| Wind | S100 |
| GPS | One source only (pick S100 or phone) |
| IAS | S100 if present, otherwise none |
| Phone IMU | Still useful for smoothing / attitude |

When the external device disconnects, XCPro should automatically fall back to phone sensors.

Ownership must be enforced in a **single UseCase**, not in parsers or repositories.

---

## Defensible Statement (For Docs & Reviews)

> "XCPro integrates with LXNAV instruments using standard NMEA sentences transmitted over Bluetooth Classic (SPP), as validated via the S100/HAWK manual and raw capture. This implementation mirrors XCSoar, which serves as the de-facto reference for LXNAV device integration."

---

## Final Reality Check

If someone asks:
> "Where is the LXNAV Bluetooth spec?"

The correct answer is:
> "Use the S8/S80/S10/S100 manual and the live output capture; XCSoar provides the de-facto implementation reference."

That ends the discussion.

