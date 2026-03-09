# IGC File Generation Spec (XCPro)

This document is a practical, implementation-focused guide for generating **professional IGC flight logs** from XCPro so pilots can download and upload their flights to analysis/scoring sites.

It is based on:
- FAI/IGC GNSS Flight Recorder Technical Specification (2nd Ed. with AL7, 31 Jan 2022)
- FAI/CIVL Sporting Code Section 7H - CIVL Flight Recorder Specification (2024 Edition, effective 1 May 2024)

Primary references:
- https://www.fai.org/sites/default/files/igc_fr_specification_2022_with_al7_2022-1-31.pdf
- https://www.fai.org/sites/default/files/civl/documents/sporting_code_s7_h_-_civl_flight_recorder_specification_2024.pdf

---

## 1) What an IGC file is

An **IGC file** is a plain-text, line-oriented flight log.  
Each line is a **record**. The **first character** of the line is the record type (e.g., `A`, `H`, `B`, `I`, `F`, `G`).

Most tools care about:
- **Header / metadata** (`A` + `H`)
- **Fixes / track points** (`B`, repeated many times)
- Optional extras (`I`, `F`, `C`, `E`, `L`, `G`)

---

## 2) Compliance levels: decide what you are building

### 2.1 Analysis-grade / "professional export" (recommended default)
You generate a valid **A + H + B** file (optionally `I/F/C/E/L`).  
This is enough for most pilot workflows (upload to common XC sites, analyze in viewers, etc.).

### 2.2 "Validated" IGC (security / G record)
A `G` record is a **digital signature** used to verify that the file's contents match what the recorder actually recorded.
Implementing this properly is a security product feature (secret/private key storage + validator tooling).

CIVL (HPG) explicitly allows simpler signature schemes (e.g., HMAC-SHA256) but still requires a validator program.

If XCPro does **not** provide a validation ecosystem, **omit `G`** and mark security status in headers accordingly.

---

## 3) File encoding + line endings (critical)
- Use **ASCII / UTF-8 without weird characters** (stick to printable ASCII in records).
- Use **CRLF** line endings: `\r\n`.
- Don't insert extra whitespace inside fixed-width records (especially `B` and `I`).

---

## 4) Recommended record order

A typical order that stays compatible with the spec and common parsers:

1. `A` (must be first)
2. `H...` (header lines)
3. Optional: `I` (B-record extensions)
4. Optional: `J` (K-record extensions)
5. Optional: `C` (pre-flight declaration)
6. Optional: `L` (comments)
7. Optional: initial `F` (satellite constellation)
8. Flight timeline:
   - `B` fixes (many lines)
   - Optional `E` events
   - Optional updates `F`, `K`
9. Optional: `G` signature (if supported)

---

## 5) File naming

### 5.1 Long file name style (modern; recommended)
Example format:
`YYYY-MM-DD-MMM-XXXXXX-FF.IGC`

Where:
- `YYYY-MM-DD` = **UTC date of the first valid fix** in the file
- `MMM` = 3-character manufacturer identifier
- `XXXXXX` = unique recorder serial ID (6 alphanumeric chars)
- `FF` = flight number of the day (`01`, `02`, ...)

**Non-IGC devices** should use a manufacturer code starting with `X`:
- `XYY` where `YY` can be replaced by manufacturer-identifying characters

Practical XCPro suggestion:
- `MMM = XCP` (X + "CP")
- `XXXXXX` = device/app instance id (stable, not per-flight)
- `FF` = per-UTC-day increment, starting at `01`

### 5.2 Date used for filename
Use the **UTC date of the first valid `B` fix** (not local date).

---

## 6) Record types you must implement

## 6.1 `A` record - Recorder identification (must be first line)

### Format
`A` + `MMM` + `NNN...` + optional `-TEXT`

- `MMM`: 3 chars (alphanumeric). For XCPro (non-IGC), use `X??` such as `XCP`.
- `NNN...`: serial id, **3+ chars**, commonly **6** chars if you're using the long filename scheme.

### Example
`AXCP00A1B2`

---

## 6.2 `H` records - Header ("holds" metadata)

### General format
`H` + Source + 3-letter code + optional long name + `:` + value

- Source:
  - `F` = created by the recorder/app
  - `O` = created later by another source (OO / post-processing)

### "Required" header fields (strongly recommended for compatibility)
These are the standard header topics used by IGC/CIVL tools:

- `DTE` date (UTC)
- `PLT` pilot in charge
- `CM2` crew 2 (use `NIL` if not applicable)
- `GTY` glider type
- `GID` glider ID / registration
- `DTM` GPS datum (use `WGS84`)
- `RFW` firmware version (XCPro app version)
- `RHW` hardware version (device model/build)
- `FTY` recorder type (manufacturer + model)
- `GPS` GNSS receiver details
- `PRS` pressure sensor details (if you have pressure altitude)
- `FRS` security status (useful even if you don't sign)

**Missing values**
Use:
- `NIL` = not applicable
- `NKN` = not known

### Altitude-type headers (important for non-IGC / HPG)
For non-IGC devices, altitude types must be declared using:

- `HFALGALTGPS:<ELL|GEO|NKN|NIL>`
- `HFALPALTPRESSURE:<ISA|MSL|NKN|NIL>`

Notes:
- **IGC gliding** expects GNSS altitude above the **WGS84 ellipsoid** ? `ELL`.
- **CIVL / HPG** expects GNSS altitude above the **WGS84 geoid** ? `GEO`.
- Pressure altitude for most cases should be `ISA` (International Standard Atmosphere).

If you do **not** record GNSS altitude:
- set `HFALGALTGPS:NIL`
- every `B` record must use fix validity `V` and `GGGGG=00000`

If you do **not** record pressure altitude:
- set `HFALPALTPRESSURE:NIL`
- set pressure altitude field `PPPPP=00000` in `B` records

### Example header block (XCPro, HPG-friendly)
```
AXCP00A1B2
HFDTEDATE:080326,01
HFPLTPILOTINCHARGE:DOE JOHN
HFCM2CREW2:NIL
HFGTYGLIDERTYPE:PARAGLIDER
HFGIDGLIDERID:NKN
HFDTMGPSDATUM:WGS84
HFRFWFIRMWAREVERSION:XCPro 2.3.1
HFRHWHARDWAREVERSION:Pixel 8 / Android 15
HFFTYFRTYPE:XCPro,Mobile
HFGPSRECEIVER:NKN
HFPRSPRESSALTSENSOR:NKN
HFFRSSECURITY:UNSIGNED
HFALGALTGPS:GEO
HFALPALTPRESSURE:ISA
```

---

## 6.3 `I` record - Define extra fields appended to each `B` record (optional)

Use this only if you really record extra sensor values per fix that can't be derived later.

### Rules
- At most **one** `I` line per file.
- Must appear **after `H`** and **before the first `B`**.
- Defines **byte positions** of extension fields in the `B` line.
- Byte counting starts at the first `B` of the `B` record line as **byte 1**.

### Format
`I` + `NN` + repeating groups: `SSFFCCC`

- `NN`: number of extension fields (2 digits)
- `SS`: start byte (2 digits)
- `FF`: finish byte (2 digits)
- `CCC`: 3-letter code of the extension (examples below)

Common extension codes:
- `FXA` Fix accuracy (Estimated Position Error), usually 3 digits in meters
- `SIU` Satellites In Use (2 digits)
- `ENL` Environmental Noise Level (3 digits)
- `MOP` motor/prop-related data (3 digits)

Typical placement after the 35-byte base `B` data:
- `FXA` bytes **3`6-`38**
- `SIU` bytes **3`9-`40**
- `ENL` bytes **4`1-`43**
- `MOP` bytes **4`4-`46**

### Examples
- Only FXA:
  - `I013638FXA`
- FXA + SIU + ENL + MOP:
  - `I043638FXA3940SIU4143ENL4446MOP`

If you output an `I` record, every `B` line must include the extension digits exactly at those positions.

---

## 6.4 `B` record - Fixes (the flight track)

### Basic format (no spaces)
`BHHMMSSDDMMmmmNDDDMMmmmEVPPPPPGGGGG`

This base record (without CRLF) is **35 bytes**.

Fields:
- `HHMMSS` UTC time (6)
- `DDMMmmmN` latitude (8)
- `DDDMMmmmE` longitude (9)
- `V` fix validity: `A` or `V` (1)
- `PPPPP` pressure altitude meters (5)
- `GGGGG` GNSS altitude meters (5)
- end of line: `CRLF`

Fix validity:
- `A` = valid 3D fix (includes GNSS altitude)
- `V` = invalid/2D/no GNSS altitude or no GNSS position

Altitude rules:
- Pressure altitude must be relative to **ICAO ISA** (1013.25 hPa sea-level datum).
- Negative altitude formatting uses a leading `-` instead of a leading zero.
  - Example: `-10m` ? `-0010` (still 5 chars)

GNSS altitude:
- For IGC gliding: reference **WGS84 ellipsoid**
- For CIVL/HPG: reference **WGS84 geoid**
- If GNSS altitude not available (2D fix), write `00000` and validity `V`.

### GNSS drop-out handling (must do this)
If GNSS data is partially/fully missing:
- Set fix validity `V`
- If GNSS altitude missing ? `GGGGG=00000`
- If no GNSS position:
  - time continues (from RTC or last reliable time source)
  - repeat last known lat/long values in the record
  - pressure altitude continues if you have it
  - GNSS altitude stays `00000`

### Example B lines
```
B1234563746494N12225164WA0012300145
B1235013746498N12225170WA0012500149
```

---

## 6.5 `F` record - Satellite constellation (optional)
If you can obtain the actual satellite IDs used for fixes, you may output `F` records.

Rules:
- Don't update more often than about **every 5 minutes** unless satellites-in-use really changed.

Format (no spaces):
`FHHMMSSAABBCCDD...`

- time `HHMMSS`
- then 2 chars per satellite ID (alphanumeric)

---

## 6.6 `E` record - Events (optional)
Event records log discrete events (pilot button press, setting change, etc.).  
Do **not** spam events per fix.

General shape:
`EHHMMSSCCC[optional data]`

Example:
- `E104533PEV` (pilot event at 10:45:33 UTC)

---

## 6.7 `C` record - Task / declaration (optional)
The `C` record is used for **pre-flight declarations**. It's a multi-line group and more complex than fixes.
Only implement it if XCPro supports declarations.

High-level rules:
- Must appear before the first `B` fix line.
- Includes:
  - declaration date/time line
  - then waypoint lines (takeoff/start/turnpoints/finish/landing) using WGS84 coordinates

---

## 6.8 `L` record - Logbook / comments (optional)
Free-form comments.  
Can appear before or after `G` (if you sign). Post-flight comments should generally be after `G` so they're not part of the signed data.

---

## 6.9 `G` record - Security signature (optional)
If implemented, `G` holds the signature and may span multiple `G` lines.

Rules:
- Must use **printable characters only** (avoid non-printing chars).

If you do not support signature validation, omit `G` entirely.

---

## 7) Coordinate conversion: decimal degrees ? IGC `DDMMmmm` / `DDDMMmmm`

IGC uses **degrees + minutes**, where minutes have **3 decimal places** (thousandths of a minute).

### Latitude field (8 chars)
`DDMMmmmN` or `DDMMmmmS`

### Longitude field (9 chars)
`DDDMMmmmE` or `DDDMMmmmW`

### Conversion algorithm (important rounding edge cases)
Given `x` in decimal degrees:

1. `absx = abs(x)`
2. `deg = floor(absx)`
3. `minutes_total = (absx - deg) * 60`
4. `min_int = floor(minutes_total)`
5. `mmm = round((minutes_total - min_int) * 1000)`

Handle rounding carry:
- If `mmm == 1000`, set `mmm = 0` and increment `min_int`
- If `min_int == 60`, set `min_int = 0` and increment `deg`

Then format:
- Latitude: `deg` as 2 digits, `min_int` as 2 digits, `mmm` as 3 digits
- Longitude: `deg` as 3 digits, `min_int` as 2 digits, `mmm` as 3 digits

Hemisphere:
- latitude: `N` if x>=0 else `S`
- longitude: `E` if x>=0 else `W`

---

## 8) Altitude formatting (meters ? 5 chars)

Input should be integer meters (round if needed).

- `alt >= 0`: `"{alt:05d}"`
- `alt < 0`: `"-" + "{abs(alt):04d}"`

Examples:
- `0` ? `00000`
- `12` ? `00012`
- `1234` ? `01234`
- `-10` ? `-0010`
- `-123` ? `-0123`

If altitude not available per spec dropout rules:
- use `00000`

---

## 9) Practical XCPro defaults (good real-world output)
- Fix interval: **`1-`5 seconds** (CIVL allows up to 30s for continuity, but XC flying benefits from shorter)
- Record baseline fixes:
  - = 20 seconds on the ground before takeoff
  - = 20 seconds on the ground after landing
- Always set datum header: `HFDTMGPSDATUM:WGS84`
- Provide `NIL` / `NKN` instead of leaving header fields blank.

---

## 10) End-to-end generation algorithm (implementation outline)

1. **Collect metadata**
   - Pilot name, glider type/id, device/app version, sensor details
2. **Collect fixes**
   - Each fix: UTC time, lat, lon, pressure alt (optional), GNSS alt (optional), validity
3. **Determine file date + flight number**
   - Use UTC date of first valid fix
   - Flight number = count flights already generated for that UTC day + 1
4. **Build text lines**
   - `A`
   - `H...`
   - optional `I`
   - optional other headers (C/L/F)
   - `B` lines
   - optional `G`
5. **Write as CRLF**
   - Ensure `\r\n` after every record line
6. **Sanity checks**
   - `A` is first
   - monotonic UTC time in `B`
   - correct fixed widths in `B`
   - no spaces inside `B` / `I`
   - lat/lon in correct ranges and formatting
   - `GGGGG=00000` when invalid / dropout

---

## 11) "Known pitfalls" that break parsers
- Using local time instead of UTC in `B`
- Using decimal degrees in `B` (must be DDMMmmm)
- Missing leading zeros (fixed width matters)
- Writing LF-only instead of CRLF
- Forgetting to zero GNSS altitude on 2D fixes / dropouts
- Not declaring altitude datum (`HFALG...` / `HFALP...`) for non-IGC recorders
- Wrong byte indices in `I` record (byte 1 is the `B`)

---

## 12) Minimal working example (A + H + B)

```
AXCP00A1B2
HFDTEDATE:080326,01
HFPLTPILOTINCHARGE:DOE JOHN
HFCM2CREW2:NIL
HFGTYGLIDERTYPE:PARAGLIDER
HFGIDGLIDERID:NKN
HFDTMGPSDATUM:WGS84
HFRFWFIRMWAREVERSION:XCPro 2.3.1
HFRHWHARDWAREVERSION:Pixel 8 / Android 15
HFFTYFRTYPE:XCPro,Mobile
HFGPSRECEIVER:NKN
HFPRSPRESSALTSENSOR:NKN
HFFRSSECURITY:UNSIGNED
HFALGALTGPS:GEO
HFALPALTPRESSURE:ISA
B1234563746494N12225164WA0012300145
B1235013746498N12225170WA0012500149
```

---

## 13) Implementation notes for Codex
When implementing, treat this as a strict formatter problem:

- Build a dedicated formatter for:
  - `formatA(manufacturer3, serialId)`
  - `formatH(source, code3, longNameOpt, value)`
  - `formatB(fix)` with fixed widths + dropout rules
  - `decimalDegreesToIGCLat/Long()`
  - `formatAltitude5()`
- Add unit tests verifying:
  - exact string length of a base `B` record = 35
  - correct carry behavior when rounding `mmm` to 1000
  - negative altitude formatting
  - dropout conditions force `V` and `00000` for GNSS altitude

If you later add `G` signing:
- freeze exactly what bytes/records are protected and how you canonicalize CRLF, because signature validity is extremely sensitive to formatting.
