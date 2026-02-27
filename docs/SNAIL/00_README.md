# SNAIL Trail Docs

Purpose
- This folder is the canonical knowledge base for ownship snail-trail behavior.
- It captures current architecture, known rendering issues, and implementation plans.
- It is intended for future contributors and agents working on trail behavior.

Scope
- Ownship snail trail only (`feature/map/.../trail/*` and related map wiring).
- Includes thermal/circling behavior, live/replay differences, and UI settings.
- Does not cover OGN glider trails (`OgnGliderTrailOverlay`) except where explicitly compared.

Read Order
1. `01_SYSTEM_MAP_AND_CURRENT_BEHAVIOR.md`
2. `02_THERMAL_JERK_ROOT_CAUSE_2026-02-27.md`
3. `03_IMPLEMENTATION_PLAN_THERMAL_SMOOTHING_2026-02-27.md`
4. `04_TEST_AND_VALIDATION_PLAN_THERMAL_SMOOTHING_2026-02-27.md`

Related Docs
- `docs/refactor/REFACTOR_SNAIL_TRAIL.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Non-Negotiables
- Keep MVVM + UDF + SSOT boundaries.
- Keep replay deterministic.
- Keep time-base rules explicit (live monotonic/replay IGC).
- Keep map rendering logic in UI/runtime layer; keep business logic in domain/use-cases.

