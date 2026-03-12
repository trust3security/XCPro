# POLAR

This folder now has six focused documents plus the existing fallback hardening plan.

Quick answer:

- In XCPro today, the active polar is set by glider selection plus optional manual 3-point override.
- The authoritative owner is `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`.
- Runtime currently uses the polar for sink lookup, legacy netto, Levo glide-netto, speed-to-fly, and a racing-task finish-glide solver.
- Cards already have live wiring for `ld_curr`, `polar_ld`, `best_ld`, `netto`, `levo_netto`, `mc_speed`, `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0`.
- `final_gld`, `arr_alt`, `req_alt`, and `arr_mc0` are finish-glide cards for the racing-task finish target. They do not change the meaning of `ld_curr`, `polar_ld`, or `best_ld`.
- The current implementation is an MVP:
  - finish-glide validity currently depends on a racing finish altitude rule (`RacingFinishCustomParams.minAltitudeMeters`)
  - if that finish altitude rule is missing, the finish-glide cards invalidate with `no alt`
- Cards named `wpt_dist`, `wpt_brg`, `wpt_eta`, `task_spd`, `task_dist`, and `start_alt` are still placeholder-only today.
- The card feed type `RealTimeFlightData` now contains nav altitude plus finish-glide solution fields, but it still does not contain current-leg waypoint or task-performance fields.
- Built-in card templates were cleaned so they now prefer live glide/performance cards instead of placeholder-only final-glide/task cards.

Files:

- `01_XCPRO_POLAR_CODE_AUDIT_2026-03-12.md`
  - Where polar is defined, persisted, edited, and consumed in XCPro.
- `02_GLIDER_COMPUTER_POLAR_RESEARCH_2026-03-12.md`
  - Practical notes on how glider computers use polar, MacCready, wind, safety height, and final glide.
- `03_XCPRO_POLAR_RECOMMENDATIONS_2026-03-12.md`
  - What should be added next in XCPro, with architecture-safe placement.
- `04_XCPRO_POLAR_RELEASE_PLAN_2026-03-12.md`
  - Release-grade phased delivery plan with SSOT ownership, gates, tests, and rollback.
- `05_XCPRO_POLAR_PHASE0_PHASE1_CHANGE_PLAN_2026-03-12.md`
  - Executed change plan for the first delivery slice: template cleanup plus live `polar_ld` and `best_ld`.
- `06_XCPRO_TASK_AWARE_GLIDE_CARD_PLAN_2026-03-12.md`
  - Exact semantics and phased delivery plan for task-aware glide cards, plus implemented racing-task finish-glide MVP status:
    - `final_gld`
    - `arr_alt`
    - `req_alt`
    - `arr_mc0`
    - and the separation from `ld_curr` / `wpt_*`
- `POLAR_FALLBACK_REFACTOR_PLAN.md`
  - Existing hardening plan for fallback/default polar behavior.
