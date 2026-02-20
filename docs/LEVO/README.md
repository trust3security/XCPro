
# Levo Vario Docs Index

This folder documents the Levo variometer pipeline (live and replay).
Start with the architecture map, then go deeper as needed.

Recommended reading order:
1) `ARCHITECTURE.md`
2) `CODING_RULES.md`
3) `CONTRIBUTING.md`
4) `docs/LEVO/levo.md`

Core documents:
- `levo.md` - end-to-end pipeline map, invariants, and common pitfalls.
- `levo-architecture-diagram.svg` - diagram used in the map.
- `levo-replay.md` - replay-specific rules, pitfalls, and debugging notes.
- `LEVO_TE_ENERGY_COMPENSATION_IMPLEMENTATION_PLAN_2026-02-20.md` - detailed TE hardening plan.

Notable implementation notes:
- Pneumatic needle response is implemented in `NeedleVarioDynamics` and
  documented in `levo.md` (needle is separate from numeric/audio).

Related core docs:
- `ARCHITECTURE.md`
- `CODING_RULES.md` (time base and sensor cadence rules)
- `CONTRIBUTING.md`

When to update:
- If you change the vario pipeline, replay behavior, or audio mapping,
  update `levo.md`.
- If you change time base logic or sensor cadence rules, update
  `CODING_RULES.md`.

