# Bluetooth Fixture Placeholder

This directory is reserved for future sanitized raw-capture fixtures for the
LXNAV S100 Bluetooth work.

Phase 0 intentionally keeps this as docs-only baseline state:

- no raw fixture files are committed yet
- no tests consume this directory yet
- no production code reads this directory

When real hardware capture is added in a later phase:

- keep one sentence per line
- normalize line endings to LF
- sanitize MAC addresses, serial numbers, and any pilot-identifying metadata
- keep sentence order and checksums intact

See `../RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md` for the validation and
sanitization rules that govern future fixture files.
