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
- `docs/SNAIL/CHANGE_PLAN_SYNTHETIC_THERMAL_REPLAY_VALIDATION_2026-04-06.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `scripts/qa/scaffold_snail_ground_validation.ps1`

Ground Validation
- Use `scripts/qa/scaffold_snail_ground_validation.ps1` or
  `scripts/qa/run_snail_ground_validation_scaffold.bat` to create the
  repeatable manual ground-test artifact pack under
  `artifacts/snail/ground/<timestamp>/`.
- Preferred deterministic baseline:
  - use the debug replay lane synthetic thermal actions (`THR` clean, `THN`
    wind-noisy) before falling back to a real thermal `.igc`.
- Attach the paired `pkg-f1` run id when available so lifecycle/cadence proof
  and manual trail validation stay linked.

Non-Negotiables
- Keep MVVM + UDF + SSOT boundaries.
- Keep replay deterministic.
- Keep time-base rules explicit (live monotonic/replay IGC).
- Keep map rendering logic in UI/runtime layer; keep business logic in domain/use-cases.
