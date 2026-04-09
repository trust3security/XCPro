# Current L/D

This folder is the Current L/D review and product-contract pack.

It is written so an external reviewer can answer:

- what XCPro ships today
- what the visible fused `ld_curr` card means now
- which raw metrics still exist underneath it
- how wind, active polar, bugs, and ballast matter without breaking ownership

If you want one file to hand to ChatGPT Pro first, use:

- `current-ld.md`
  - single-file branch-truth brief covering the visible fused `ld_curr`, the
    raw metrics beneath it, and the still-missing `currentVsPolar` concept

If the specific question is "how should wind be part of Current L/D?" use:

- `wind-ld.md`
  - wind-specific release-grade brief describing the implemented wind rule and
    zero-wind fallback behavior

## Read in this order

1. `current-ld.md`
   - current branch-truth brief for the visible fused card and raw metrics
2. `wind-ld.md`
   - implemented wind rule, zero-wind fallback, and thermal behavior
3. `../../ARCHITECTURE/CHANGE_PLAN_CURRENT_LD_FUSED_2026-04-08.md`
   - execution record of the fused Current L/D rework
4. `../../ARCHITECTURE/CHANGE_PLAN_CURRENT_LD_THERMAL_STATE_POLISH_2026-04-09.md`
   - next-step pilot-facing polish plan for showing `THERMAL` instead of a
     misleading generic subtitle while the fused Current L/D is held or timed
     out in thermalling/circling/turning
5. `../../ARCHITECTURE/ADR_CURRENT_LD_PILOT_FUSED_METRIC_2026-04-08.md`
   - durable semantic/ownership decision
6. `02_XCPRO_CURRENT_LD_IMPLEMENTATION_AUDIT_2026-04-07.md`
   - pre-fused raw-metric audit and historical branch-truth reference

## Current branch truth

- XCPro now ships one fused visible Current L/D card plus two raw glide
  metrics underneath it.
- Visible pilot-facing metric:
  - card ID `ld_curr`
  - title `L/D CURR`
  - visible runtime fields `pilotCurrentLD/pilotCurrentLDValid`
  - fused from wind-aware air-data logic with zero-wind fallback
- Raw over-ground measured metric kept internally:
  - runtime fields `currentLD/currentLDValid`
  - still calculated from recent GPS path distance divided by barometric
    altitude loss
- Raw through-air measured metric:
  - card ID `ld_vario`
  - title `L/D VARIO`
  - runtime fields `currentLDAir/currentLDAirValid`
  - calculated from chosen true airspeed divided by TE sink
- Wind is now an explicit input to the visible fused `ld_curr` card when wind
  is trustworthy.
- Missing/stale/low-confidence wind falls back to zero-wind behavior instead of
  invalidating the visible Current L/D card.
- Wind still lives in a separate owner path built around `WindState`.
- `currentVsPolar` is still not implemented.

So current XCPro now exposes:

- visible `ld_curr`
  - fused
  - pilot-facing
  - wind-aware with zero-wind fallback
  - protected against circling geometry
  - not a final-glide metric
- raw `currentLD`
  - recent
  - measured
  - path-based / over-ground
  - internal/raw/degraded-fallback metric
- `ld_vario`
  - recent
  - measured
  - through-air
  - non-polar
  - not a final-glide metric

## Questions for external review

- Does the visible fused `ld_curr` card now match the intended pilot meaning?
- Is the zero-wind fallback the right degraded behavior when wind is not
  trustworthy?
- Should future work keep `currentVsPolar` separate from the visible Current
  L/D card?

## Files in this folder

- `current-ld.md`
  - current branch-truth brief for the implemented fused Current L/D
- `wind-ld.md`
  - implemented wind rule and zero-wind fallback brief
- `../../ARCHITECTURE/CHANGE_PLAN_CURRENT_LD_FUSED_2026-04-08.md`
  - phased execution record for the fused visible card rework
- `../../ARCHITECTURE/CHANGE_PLAN_CURRENT_LD_THERMAL_STATE_POLISH_2026-04-09.md`
  - narrow follow-up phased IP for pilot-facing `THERMAL` subtitle behavior on
    the visible `ld_curr` card
- `../../ARCHITECTURE/ADR_CURRENT_LD_PILOT_FUSED_METRIC_2026-04-08.md`
  - durable semantic/ownership decision for visible `ld_curr`
- `01_CURRENT_LD_RESEARCH_2026-04-07.md`
  - terminology, vendor examples, and pre-implementation background
- `02_XCPRO_CURRENT_LD_IMPLEMENTATION_AUDIT_2026-04-07.md`
  - historical raw-metric audit before the fused visible-card rework
- `03_XCPRO_CURRENT_LD_IMPLEMENTATION_NOTES_2026-04-07.md`
  - pre-rework option analysis
- `04_CURRENT_LD_REVIEW_QUESTIONS_2026-04-07.md`
  - historical external-review prompt pack

## Related repo docs

- `docs/POLAR/03_XCPRO_GENERAL_POLAR_CURRENT_LD_SEAM_AUDIT_2026-04-07.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md`
- `docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md`
