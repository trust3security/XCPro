# IGC_FILE_STRUCTURE_FIELD_REFERENCE_2026-03-08.md

## Purpose

Provide a field-level, byte-position reference for IGC records used by XCPro,
based on FAI IGC Technical Specification (Dec 2024 + AL9, Appendix A).

Primary source:

- https://www.fai.org/sites/default/files/2025-04/igc_specification_dec_2024_with_al9.pdf

## Scope Clarification

This reference focuses on the core IGC records relevant to XCPro flight logging:

- `A, H, I, J, B, C, E, F, K, L, G`

The full spec also defines additional/extended records (`D`, `M`, `N`, and spares),
which are listed in the index section below.

## File-Level Rules

1. Text format with line records; each line starts with one upper-case record letter.
2. Canonical line ending is `CRLF`.
3. Byte counting in `I`/`J` range definitions is 1-based from the first record letter of the target line.
4. `B` records are the flight-fix truth stream; do not append post-flight `B` records.
5. Security/signature (`G`) applies to protected in-flight records only.

## Record-Type Index

From the spec record-letter table:

- `A` FR manufacturer and FR serial no.
- `B` Fix.
- `C` Task/declaration.
- `D` Differential GNSS.
- `E` Event.
- `F` Satellite constellation.
- `G` Security.
- `H` File header.
- `I` Additions to `B` record.
- `J` Additions to `K` record.
- `K` Multiple-instance signed data.
- `L` Log/comments.
- `M` Additions to `N` record.
- `N` Multiple-instance unsigned data.
- `O/P/...` spare.

Count summary:

- Defined record letters in the main table: `14` (`A` through `N`, non-contiguous by semantics but contiguous by letter).
- `O` and later letters are reserved/spare in the base spec table.

## File Naming Structure

Long style (current standard for new recorders):

- `YYYY-MM-DD-MMM-XXXXXX-FF.IGC`
  - `YYYY-MM-DD`: UTC flight date
  - `MMM`: manufacturer 3-letter ID
  - `XXXXXX`: recorder serial ID
  - `FF`: flight number of day (`01`, `02`, ...)

Legacy short style:

- `YMDCXXXF.IGC`

## Recommended Record Order

Typical order before flight data:

1. `A`
2. `H` lines
3. `I` (optional, but required if extensions are used)
4. `J` (optional, if `K` extensions are used)
5. `C` declaration block (optional by workflow)

During flight timeline (time-ordered as applicable):

- `B`, `E`, `F`, `K` (and optionally `N` in extended usage)

File close:

- `L` (optional comments)
- `G` security lines (if signature mode used)
- post-flight `L` (allowed for post-flight metadata; no new `B` fixes after `G`)

## Detailed Record Structures

## A Record (`A`)

Purpose:

- Recorder identity: manufacturer ID + recorder serial ID.

Canonical format:

- `AMMMXXX...`
- `AMMMXXXXXX...`

Fields:

- Byte `1`: `A`
- Bytes `2..4`: `MMM` manufacturer ID (3 chars)
- Bytes `5..7` or `5..10`: recorder serial ID (`3` or `6` chars)
- Remaining bytes: optional additional text (if present, spec guidance is to separate using `-`)

Notes:

- Must be first record in the file.
- For non-IGC-approved devices, `XYY` manufacturer coding is used by convention.

## H Record (`H`)

Purpose:

- Header metadata (date, pilot, glider, recorder, firmware/hardware, datum, etc).

General format:

- `H` + source + 3-letter subtype + descriptive header text + `:` + value text

Field pattern:

- Byte `1`: `H`
- Byte `2`: source (`F` from recorder, `O` other source)
- Bytes `3..5`: subtype code (TLC)
- Bytes `6..n`: long-name/value payload (variable; usually `LONGNAME:VALUE`)

Required baseline header lines (spec-required in approved workflows):

- `HFDTE...` date (`DDMMYY[,flightNo]` semantics)
- `HFPLT...` pilot
- `HFGTY...` glider type
- `HFGID...` glider ID
- `HFDTM...` datum (WGS84)
- `HFRFW...` firmware version
- `HFRHW...` hardware version
- `HFFTY...` recorder type
- receiver/sensor/security lines per spec profile

Notes:

- `H` payload is variable length; no single fixed byte map beyond prefix bytes.

## I Record (`I`)

Purpose:

- Declares extension fields appended to each `B` record.

Canonical compact format:

- `INNSSFFCCCSSFFCCC...`

Fields:

- Byte `1`: `I`
- Bytes `2..3`: `NN` number of extension definitions
- Repeated group (`NN` times), 7 bytes each:
  - `SS` start byte in `B`
  - `FF` finish byte in `B`
  - `CCC` 3-letter code

Byte math for definition `k` (1-based):

- Group start = `4 + (k-1)*7`
- `SS` = `[start .. start+1]`
- `FF` = `[start+2 .. start+3]`
- `CCC` = `[start+4 .. start+6]`

Notes:

- Start/finish are inclusive ranges on the target `B` line.
- Common additions include `FXA`, `SIU`, `ENL`, `MOP`, `IAS`, `TAS`.

## J Record (`J`)

Purpose:

- Declares extension fields appended to each `K` record.

Canonical compact format:

- `JNNSSFFCCCSSFFCCC...`

Fields are identical in structure to `I`, but ranges reference `K` lines.

## B Record (`B`)

Purpose:

- Time-stamped flight fixes.

Basic canonical format:

- `BHHMMSSDDMMmmmNDDDMMmmmEVPPPPPGGGGG`

Fixed bytes (base record, no extensions):

- Byte `1`: `B`
- Bytes `2..7`: `HHMMSS` UTC time
- Bytes `8..14`: latitude digits `DDMMmmm`
- Byte `15`: latitude hemisphere `N`/`S`
- Bytes `16..23`: longitude digits `DDDMMmmm`
- Byte `24`: longitude hemisphere `E`/`W`
- Byte `25`: fix validity (`A` or `V`)
- Bytes `26..30`: pressure altitude (`PPPPP`)
- Bytes `31..35`: GNSS altitude (`GGGGG`)

Extension bytes:

- Byte `36+`: optional fields as defined by `I` ranges.

Fix validity semantics:

- `A`: 3D fix.
- `V`: 2D/no GNSS altitude or no GNSS data.

Altitude semantics:

- Pressure altitude: meters relative to ISA 1013.25 hPa datum.
- GNSS altitude: meters relative to WGS84 ellipsoid.

## C Record (`C`)

Purpose:

- Task/declaration block.

Declaration header line format:

- `CDDMMYYHHMMSSDDMMYYXXXXTT<text>`

Declaration header fixed fields:

- Byte `1`: `C`
- Bytes `2..7`: declaration date UTC (`DDMMYY`)
- Bytes `8..13`: declaration time UTC (`HHMMSS`)
- Bytes `14..19`: flight date (`DDMMYY`)
- Bytes `20..23`: task number (`XXXX`)
- Bytes `24..25`: number of turnpoints (`TT`)
- Bytes `26..n`: task text/description

Point line format (takeoff/start/turn/finish/landing lines):

- `CDDMMmmmNDDDMMmmmE<point-label-text>`

Point line fields:

- Byte `1`: `C`
- Bytes `2..8`: latitude `DDMMmmm`
- Byte `9`: `N`/`S`
- Bytes `10..17`: longitude `DDDMMmmm`
- Byte `18`: `E`/`W`
- Bytes `19..n`: point role + description text

Notes:

- Takeoff/landing lines are informational; official declaration validity is start->turns->finish.

## E Record (`E`)

Purpose:

- Event markers at specific times.

Canonical format:

- `EHHMMSSCCC[optional-payload]`

Fields:

- Byte `1`: `E`
- Bytes `2..7`: event time UTC (`HHMMSS`)
- Bytes `8..10`: event TLC code (for example `PEV`)
- Bytes `11..n`: optional event payload text/numeric data

Notes:

- Spec places `E` before the associated `B` at the same timestamp.

## F Record (`F`)

Purpose:

- Satellite constellation IDs used for fixes.

Canonical format:

- `FHHMMSSAABBCC...`

Fields:

- Byte `1`: `F`
- Bytes `2..7`: time UTC (`HHMMSS`)
- Bytes `8..n`: repeated 2-byte satellite IDs (`AA`, `BB`, ...)

Notes:

- Updated when constellation usage changes; not required every second.

## K Record (`K`)

Purpose:

- Less-frequent, time-stamped extension data.

Canonical format:

- `KHHMMSS<extension-data>`

Fields:

- Byte `1`: `K`
- Bytes `2..7`: time UTC (`HHMMSS`)
- Bytes `8..n`: payload defined by `J` record ranges

## D Record (`D`)

Purpose:

- Differential GNSS-related information record.

Canonical shape:

- `D<profile-defined-payload>`

Fields:

- Byte `1`: `D`
- Bytes `2..n`: profile-dependent DGPS payload (not used in XCPro baseline mode).

Notes:

- This is an advanced/rare record in consumer logger workflows.
- If emitted, payload schema must follow the exact profile declared by the producer and validator.

## M Record (`M`)

Purpose:

- Declares extension field ranges for `N` records (same concept as `I` for `B`, `J` for `K`).

Canonical compact format:

- `MNNSSFFCCCSSFFCCC...`

Fields:

- Byte `1`: `M`
- Bytes `2..3`: `NN` number of extension definitions
- Repeated group (`NN` times), 7 bytes each:
  - `SS` start byte in `N`
  - `FF` finish byte in `N`
  - `CCC` 3-letter code

Notes:

- Byte positions are inclusive and 1-based against the target `N` record.
- Treat malformed overlaps or out-of-range mappings as invalid.

## N Record (`N`)

Purpose:

- Multiple-instance unsigned data record (parallel concept to `K`, but unsigned).

Canonical shape:

- `N<instance-data>`

Fields:

- Byte `1`: `N`
- Bytes `2..n`: payload defined by `M` ranges and profile conventions.

Notes:

- Use `N` only when a clear producer/consumer contract exists for its payload semantics.
- XCPro baseline logging should keep `N` disabled unless a concrete interoperability requirement is approved.

## L Record (`L`)

Purpose:

- Logbook/comments (not time-stamped).

Canonical format:

- `LSSS<text>`

Fields:

- Byte `1`: `L`
- Bytes `2..4`: source/manufacturer code
  - manufacturer code (`MMM`) for recorder-generated signed comment payloads
  - other source codes such as `PLT`, `OOI`, `SOF` for external comments
- Bytes `5..n`: free text payload

Security note:

- `L` lines with manufacturer code are part of signed data when signature mode is active.

## G Record (`G`)

Purpose:

- Digital security signature data.

Canonical format:

- `G<security-chars>`
- can span multiple `G` lines

Fields:

- Byte `1`: `G`
- Bytes `2..n`: signature payload characters (printable, no non-printing chars)

Notes:

- Usually appears near end of in-flight data section.
- Post-flight `L` additions may appear after `G` without introducing new fixes.

## XCPro Implementation Notes

For XCPro v1 logging target:

1. Always generate parse-stable `A/H/B` core.
2. Generate `I` when writing any extension bytes in `B`.
3. Include `E` for key pilot/task/system events.
4. Keep `F` optional by capability flag if satellite IDs are unavailable from current sensor SSOT.
5. Treat `G` as optional security phase; do not claim approved-signature behavior when absent.

## Validation Checklist For Generated Files

- Record letter in column 1 for every line.
- UTC formatting valid (`HHMMSS`, `DDMMYY`).
- `B` line base length at least 35 chars before CRLF.
- `I`/`J` declared ranges do not overlap invalid indices.
- No `B` lines after `G` section.
- File parseable by XCPro `IgcParser` and fixture validators.
