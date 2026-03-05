# MAPSCREEN Upgrade Docs Index

Date: 2026-03-05  
Owner: XCPro Team  
Status: Active (phase-2 lanes green; phase-3 blocked on `MS-UX-01`, tracked by `RULES-20260305-12` expiring 2026-04-15)

## Purpose

This folder contains the production-grade phased implementation plan (IP) package
for MapScreen performance and efficiency upgrades, with a specific focus on:

- moving map responsiveness,
- overlay runtime efficiency,
- style/layer churn reduction,
- deterministic replay-safe behavior.

This package now includes:

- mandatory package-level artifact contracts (`artifacts/mapscreen/...`),
- statistical pass rules (percentiles + CI bounds),
- section-level quality scoring rules with promotion caps,
- AGENT.md minimum evidence alignment (`arch_gate`, timebase path citations, verification pass/fail summaries),
- resumable completion automation contract (`run_mapscreen_completion_contract.ps1`),
- unattended overnight automation contract (`run_mapscreen_overnight_agent_contract.ps1`).

## Recommended Read Order

1. `docs/MAPSCREEN/01_MAPSCREEN_PRODUCTION_GRADE_PHASED_IP_2026-03-05.md`
2. `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
3. `docs/MAPSCREEN/03_IMPLEMENTATION_WORKSTREAMS_AND_PHASE_GATES_2026-03-05.md`
4. `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`
5. `docs/MAPSCREEN/06_MAPSCREEN_COMPLETION_CONTRACT_2026-03-05.md`
6. `docs/MAPSCREEN/07_OVERNIGHT_AUTOMATED_AGENT_CONTRACT_2026-03-05.md`
7. `docs/MAPSCREEN/05_EXECUTION_BACKLOG_2026-03-05.md`

## Scope Summary

Primary hotspot classes in this IP:

1. Task/AAT drag causing full task style teardown + rebuild on move.
2. Weather animation frame ticks causing repeated traffic z-order churn.
3. Duplicate startup overlay reapply sequence.
4. ADS-B animation loop doing duplicate frame data traversal.
5. OGN publish path sorting full lists on each publish.

Production visual contract:

- UX/engineering SLO IDs (`MS-UX-*`, `MS-ENG-*`) are defined in
  `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`.
- Validation and rollback gates are defined in
  `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`.
- Merge is blocked on impacted SLO misses unless an approved, time-boxed
  `KNOWN_DEVIATIONS.md` entry exists.

## Architecture Guardrails

This package is constrained by:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/AGENT.md`
