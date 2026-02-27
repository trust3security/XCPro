# SCIA Docs

Purpose
- This folder is the canonical knowledge base for SCIA behavior (OGN glider trail/wake rendering).
- It captures current architecture wiring, known failure/performance risks, and the execution plan to reach release-grade and genius-grade quality.
- It is intended for future contributors and coding agents working on SCIA.

Scope
- SCIA only (OGN glider trails), not ownship snail trail.
- Includes toggle behavior, selection behavior, map rendering behavior, crash hardening, and UX pause/jank investigation.
- Includes phased implementation and validation plans.

Read Order
1. `01_SYSTEM_MAP_AND_CURRENT_BEHAVIOR.md`
2. `02_CRASH_AND_PAUSE_INVESTIGATION_2026-02-27.md`
3. `05_PHASED_IMPLEMENTATION_PLAN_SCIA_50_AIRCRAFT_2026-02-27.md`
4. `04_TEST_AND_VALIDATION_PLAN_SCIA_2026-02-27.md`

Archived
- `archive/03_IMPLEMENTATION_PLAN_GENIUS_GRADE_SCIA_2026-02-27.md` (superseded by `05_PHASED_IMPLEMENTATION_PLAN_SCIA_50_AIRCRAFT_2026-02-27.md`)

Related Docs
- `docs/OGN/OGN.md`
- `docs/OGN/OGN_PROTOCOL_NOTES.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Non-Negotiables
- Keep MVVM + UDF + SSOT boundaries.
- Keep replay determinism and time-base correctness.
- Keep heavy business/domain logic out of UI.
- Keep map runtime resilient to non-fatal data/render failures.
