# ADS-B Documentation Index

This folder was reduced to a minimal, current set so future agents have one clear path.

## Canonical files

1. `docs/ADS-b/ADSB.md`
   - Runtime contract for ADS-B behavior.
2. `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
   - Active detailed phased plan to raise ADS-B connectivity from `88/100` to release-grade `96/100` (current working score: `95/100`).
3. `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md`
   - Supporting closure plan for UX, stale-housekeeping, and observability workstream details.
4. `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`
   - Detailed phased execution plan for the remaining release/e2e network-transition coverage deduction (`95 -> 96`).
5. `docs/ADS-b/CHANGE_PLAN_ADSB_SOCKET_ERROR_HARDENING_2026-03-01.md`
   - Completed phased execution plan for recurring ADS-B socket-error hardening baseline.
6. `docs/ADS-b/ADSB_AircraftMetadata.md`
   - Metadata source/import contract.
7. `docs/ADS-b/ADSB_CategoryIconMapping.md`
   - Category/typecode/icon mapping contract.
8. `docs/ADS-b/ADSB_Improvement_Plan.md`
   - Historical completed plan retained for audit trail; superseded for current execution.
9. `docs/ADS-b/CHANGE_PLAN_ADSB_DEFAULT_ICON_MEDIUM_PHASED_IP_2026-03-08.md`
   - Production-grade phased IP for replacing unknown/question-mark map fallback with
     `ic_adsb_plane_medium.png`, including phase scoring and release gates.
10. `docs/ADS-b/AGENT_CONTRACT_ADSB_DEFAULT_ICON_PHASE_EXECUTION_2026-03-08.md`
    - Autonomous execution contract for running Phase 0-4 with mandatory basic build
      (`assembleDebug`) after each phase and auto-proceed behavior.
11. `docs/ADS-b/CHANGE_PLAN_ADSB_ICON_CORRECTNESS_RELEASE_GRADE_2026-03-09.md`
   - Release-grade plan for eliminating avoidable metadata lookup starvation and
     improving icon correctness.
12. `docs/ADS-b/CHANGE_PLAN_ADSB_POSITION_FRESHNESS_REWIND_FIX_2026-03-10.md`
   - Production-grade phased IP for eliminating backward marker jumps caused by
     stale ADS-B geometry and for hardening provider position-freshness semantics.
13. `docs/ADS-b/AGENT_AUTOMATION_CONTRACT_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`
   - Autonomous execution contract for Phase 0 -> Phase 5 of the ADS-B
     position-freshness and rewind-fix workstream, using basic build checks only
     for this run.
14. `docs/ADS-b/EXECUTION_LOG_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`
   - Live execution log for the ADS-B position-freshness automation run.

## Notes

- Completed dated hardening plans were removed on 2026-02-20 to reduce drift and duplicate guidance.
- For current ADS-B connectivity score-lift work, follow `CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md` first.
- Use `CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md` as the supporting technical workstream plan.
- Use `CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md` for the remaining release/e2e transition-coverage closure work.
- The socket hardening baseline remains recorded in `CHANGE_PLAN_ADSB_SOCKET_ERROR_HARDENING_2026-03-01.md`.
- Use `CHANGE_PLAN_ADSB_DEFAULT_ICON_MEDIUM_PHASED_IP_2026-03-08.md` for
  default-icon UX and icon-resolution-latency hardening work.
- Use `CHANGE_PLAN_ADSB_ICON_CORRECTNESS_RELEASE_GRADE_2026-03-09.md` to execute the
  targeted release-grade icon correctness tightening pass.
- Use `CHANGE_PLAN_ADSB_POSITION_FRESHNESS_REWIND_FIX_2026-03-10.md` for ADS-B
  provider timing, freshness, stale-geometry, and reverse-jump hardening work.
- Use `AGENT_CONTRACT_ADSB_DEFAULT_ICON_PHASE_EXECUTION_2026-03-08.md` when
  executing the default-icon phased plan autonomously.
- Use `AGENT_AUTOMATION_CONTRACT_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`
  when executing the ADS-B position-freshness and rewind-fix workstream
  autonomously.
- Use `EXECUTION_LOG_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`
  as the canonical execution log for that automation run.
- If ADS-B runtime wiring changes, update `docs/ARCHITECTURE/PIPELINE.md` in the same change.
