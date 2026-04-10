# Bluetooth Fixture Notes

This directory remains the documentation-side placeholder for sanitized
Bluetooth capture notes for the LXNAV S100 work.

Committed executable fixture files now live under:

- `feature/variometer/src/test/resources/com/example/xcpro/variometer/bluetooth/lxnav/fixtures/`

Those resources are consumed only by JVM/unit fixture replay tests in
`:feature:variometer`; production code does not read this directory.

Fixture rules:

- keep one sentence per line
- prefix each sentence line with monotonic receive time as `<monoMs>|<raw sentence>`
- use `@session start` / `@session end` to preserve session boundaries
- use `@event monoMs=<n> type=error error=<ENUM>` for terminal reconnect/error markers
- normalize line endings to LF
- sanitize MAC addresses, serial numbers, and any pilot-identifying metadata
- keep sentence order and checksums intact
- serial placeholders in committed fixtures use synthetic tokens such as
  `SN0001`, `SN0002`, ... after sanitization; recompute the checksum after
  substitution
- if a sanitization rule changes sentence payload, recompute the checksum and
  document the rule beside the fixture or in the PR notes

Hard stop:

- if real capture proves parser or transport expansion is required for the v1
  fused fields, record the evidence and re-scope instead of widening Phase 6

See `../RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md` for the validation and
sanitization rules that govern future fixture files.
