# ADS-B Documentation Index

This folder keeps the active ADS-B runtime/reference docs. Legacy prompt-run
documents were moved to `docs/ADS-b/archive/` on 2026-03-28 so the active path
is smaller and clearer.

## Active files

1. `docs/ADS-b/ADSB.md`
   - Runtime contract for ADS-B behavior.
2. `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
   - Primary connectivity hardening plan.
3. `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md`
   - Supporting connectivity UX, stale-housekeeping, and observability plan.
4. `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`
   - Remaining release/e2e transition-coverage plan.
5. `docs/ADS-b/CHANGE_NOTE_ADSB_STALE_OFFLINE_RECOVERY_2026-03-28.md`
   - Field bug note for the "stayed red until restart" recovery failure and fix.
6. `docs/ADS-b/CHANGE_PLAN_ADSB_SOCKET_ERROR_HARDENING_2026-03-01.md`
   - Completed socket hardening baseline.
7. `docs/ADS-b/ADSB_AircraftMetadata.md`
   - Metadata source/import contract.
8. `docs/ADS-b/ADSB_CategoryIconMapping.md`
   - Category/typecode/icon mapping contract.
9. `docs/ADS-b/CHANGE_PLAN_ADSB_DEFAULT_ICON_MEDIUM_PHASED_IP_2026-03-08.md`
   - Default-icon hardening plan.
10. `docs/ADS-b/CHANGE_PLAN_ADSB_ICON_CORRECTNESS_RELEASE_GRADE_2026-03-09.md`
    - Icon correctness hardening plan.
11. `docs/ADS-b/CHANGE_PLAN_ADSB_POSITION_FRESHNESS_REWIND_FIX_2026-03-10.md`
    - Position freshness and stale-geometry hardening plan.
12. `docs/ADS-b/CHANGE_PLAN_ADSB_CATEGORY_METADATA_ICON_REFINEMENT_PHASED_IP_2026-03-16.md`
    - Category plus metadata/typecode icon refinement plan.
13. `docs/ADS-b/archive/README.md`
    - Archive index for retired ADS-B prompt/execution docs.

## Notes

- Follow `CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md` first for
  current ADS-B connectivity work.
- Use `CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md` for
  transition-recovery and release-coverage closure work.
- Use `CHANGE_NOTE_ADSB_STALE_OFFLINE_RECOVERY_2026-03-28.md` for the March 28
  field-reported recovery bug and the verification status of that fix.
- Historical completed plans such as `archive/ADSB_Improvement_Plan.md` now live
  under the archive folder rather than the active index.
- If ADS-B runtime wiring changes, update `docs/ARCHITECTURE/PIPELINE.md` in the
  same change.
