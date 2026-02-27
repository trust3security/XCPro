# OGN Docs Index

This folder now separates current runtime contracts from historical plans.

## Current (authoritative)

- `docs/OGN/OGN.md`
- `docs/OGN/OGN_PROTOCOL_NOTES.md`
- `docs/OGN/OGN_APRS_TEST_VECTORS.md`
- `docs/ARCHITECTURE/PIPELINE.md` (cross-module wiring contract)

## Active Change Plans

- `docs/OGN/CHANGE_PLAN_OGN_SOURCE_TIME_ORDERING_2026-02-27.md`
  - Implemented phased plan for OGN marker anti-rewind/source-time ordering and plausibility gating.

Current docs include:
- per-install persisted OGN APRS login callsign (collision-safe vs shared static login)
- map-visibility gating semantics that avoid reconnect churn on transient pause/resume

Use these files for implementation and review decisions.

## Archived (historical / planning)

Historical plans, research notes, and superseded proposal docs were moved to:

- `docs/OGN/archive/`
- `docs/OGN/archive/AutoPlan/`

These files are kept for traceability only and should not be treated as current behavior contracts.
