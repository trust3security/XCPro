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
- `docs/OGN/CHANGE_PLAN_OGN_CONNECTIVITY_RELIABILITY_2026-03-01.md`
  - Implemented phased hardening for untimed-frame ordering, inbound stall liveness, and connected-session DDB refresh cadence.
- `docs/OGN/CHANGE_PLAN_OGN_QUALITY_HARDENING_2026-03-01.md`
  - Draft phased plan to lift OGN slice quality from `8.8/10` to `>=9.5/10` via ordering, DDB retry, and overlay performance hardening.
- `docs/OGN/CHANGE_PLAN_OGN_REMAINING_GAPS_2026-03-01.md`
  - Remaining-gaps hardening record (Phases 1/2/3/4 complete; optional self-heal follow-on only).
- `docs/OGN/CHANGE_PLAN_OGN_PHASE3_OVERLAY_LIFECYCLE_REGRESSION_LOCK_2026-03-01.md`
  - Phase 3 lifecycle regression lock implementation record (overlay init fast-path lifecycle + detach cancellation tests).

## Supporting Top-Level Plans

- `docs/OGN/CHANGE_PLAN_OGN_OVERLAY_RENDER_INIT_FASTPATH_2026-03-01.md`
  - implemented supporting sub-plan for the quality-hardening Phase 3 render
    hot-path optimization; keep as evidence/detail, not the primary current
    baseline
- `docs/OGN/CHANGE_PLAN_OGN_RELATIVE_ALTITUDE_COLOR_BANDS_2026-02-28.md`
  - standalone draft feature plan retained as supporting context; it is not
    listed as the current OGN release baseline in this folder index

Current docs include:
- per-install persisted OGN APRS login callsign (collision-safe vs shared static login)
- map-visibility gating semantics that avoid reconnect churn on transient pause/resume

Use these files for implementation and review decisions.

## Archived (historical / planning)

Historical plans, research notes, and superseded proposal docs were moved to:

- `docs/OGN/archive/`
- `docs/OGN/archive/AutoPlan/`

These files are kept for traceability only and should not be treated as current behavior contracts.
