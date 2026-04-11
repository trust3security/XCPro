# Bluetooth Docs Index

This folder is the active Bluetooth planning, status, and validation pack for XCPro.

The pack lives directly under `docs/BLUETOOTH/`.
There is no nested `xcpro_bluetooth_codex_pack/` directory in the current repo layout.

## Start here

1. `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`
2. `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
3. `CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md`
4. `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`
5. `02_LX_S100_SENTENCE_CAPABILITIES_2026-04-10.md`

## Current status

- LXNAV S100 Bluetooth transport, parsing, runtime ingestion, reconnect/state
  handling, and settings UI already exist in production code.
- Pressure altitude and total-energy vario already enter the fused runtime
  through the external instrument seam.
- LX airspeed/TAS to DF-cards and parsed device metadata in Bluetooth settings
  are still open follow-up work.
- Garmin GLO 2 remains research only and is not implemented.

## File guide

- `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`
  - authoritative current implementation status for this folder
  - read this before acting on older phase docs
- `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
  - follow-up implementation plan for LX TAS/DF-card wiring and settings metadata
- `README_FIRST_BLUETOOTH_CODEX_PACK.md`
  - original phased-execution pack, now kept as historical context plus current-state pointers
- `CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md`
  - master architecture contract for the phased Bluetooth work
- `PHASE0_BASELINE_AND_BOUNDARIES.md`
  - historical Phase 0 baseline audit captured before production Bluetooth wiring landed
- `GLO2.md`
  - Garmin GLO 2 external GNSS note
  - records confirmed device behavior, likely NMEA families, current XCPro repo
    seams, and recommended connected-device UI text
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
  - execution brief for Codex, limited to the requested phase
- `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`
  - review prompts for pass/fail checks after each phase
- `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`
  - hardware validation plan for later phases
- `fixtures/README.md`
  - docs-only placeholder for future sanitized raw-capture fixtures

## Historical docs

- `README_FIRST_BLUETOOTH_CODEX_PACK.md`
- `PHASE0_BASELINE_AND_BOUNDARIES.md`
- `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
- `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`

These remain useful, but they no longer describe the current repo state by
themselves. Use `CURRENT_STATUS_BLUETOOTH_2026-04-11.md` first.
