# POLAR

This folder now has four focused documents plus the existing fallback hardening plan.

Quick answer:

- In XCPro today, the active polar is set by glider selection plus optional manual 3-point override.
- The authoritative owner is `feature/profile/src/main/java/com/example/xcpro/glider/GliderRepository.kt`.
- Runtime currently uses the polar for sink lookup, legacy netto, Levo glide-netto, and speed-to-fly.
- Runtime does not yet implement a destination-aware final glide computer with arrival height, required altitude, required glide ratio, or task/airport final glide.
- Cards already have live wiring for `ld_curr`, `polar_ld`, `best_ld`, `netto`, `levo_netto`, and `mc_speed`.
- Cards named `final_gld`, `wpt_dist`, `wpt_brg`, `wpt_eta`, `task_spd`, `task_dist`, and `start_alt` are still placeholder-only today.
- The current card feed type `RealTimeFlightData` does not yet contain waypoint, task, or final-glide solution fields, so those placeholder cards cannot be made real without extending the data contract first.
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
- `POLAR_FALLBACK_REFACTOR_PLAN.md`
  - Existing hardening plan for fallback/default polar behavior.
