# Current L/D

This folder is the Current L/D and air-relative L/D review pack.

It is written so an external reviewer can answer:

- what XCPro ships today
- what "Current L/D" usually means in glider-computer practice
- whether wind should be part of this metric
- how XCPro now splits over-ground and through-air glide-efficiency concepts

If you want one file to hand to ChatGPT Pro first, use:

- `current-ld.md`
  - single-file branch-truth brief covering `ld_curr`, `ld_vario`, wind,
    polar, bugs, ballast, and the still-missing current-vs-polar concept

If the specific question is "how should wind be part of Current L/D?" use:

- `wind-ld.md`
  - wind-specific release-grade decision brief comparing redefining `ld_curr`
    versus keeping wind-aware behavior additive

## Read in this order

1. `current-ld.md`
   - shortest single-file brief for external review
2. `wind-ld.md`
   - wind-specific release-grade recommendation and comparison brief
3. `02_XCPRO_CURRENT_LD_IMPLEMENTATION_AUDIT_2026-04-07.md`
   - branch truth first
4. `01_CURRENT_LD_RESEARCH_2026-04-07.md`
   - terminology and vendor distinctions
5. `03_XCPRO_CURRENT_LD_IMPLEMENTATION_NOTES_2026-04-07.md`
   - option analysis for possible product directions
6. `04_CURRENT_LD_REVIEW_QUESTIONS_2026-04-07.md`
   - direct review questions for external analysis

## Current branch truth

- XCPro ships two separate glide-efficiency cards.
- Over-ground measured metric:
  - card ID `ld_curr`
  - title `L/D CURR`
  - runtime fields `currentLD/currentLDValid`
  - calculated from recent GPS path distance divided by barometric altitude
    loss
- Through-air measured metric:
  - card ID `ld_vario`
  - title `L/D VARIO`
  - runtime fields `currentLDAir/currentLDAirValid`
  - calculated from chosen true airspeed divided by TE sink
- Wind is not an explicit input to `currentLD`.
- Wind still lives in a separate owner path built around `WindState`.
- `currentVsPolar` is still not implemented.

So current XCPro now exposes:

- `ld_curr`
  - recent
  - measured
  - path-based / over-ground
  - non-polar
  - not a final-glide metric
- `ld_vario`
  - recent
  - measured
  - through-air
  - non-polar
  - not a final-glide metric

## Questions for external review

- Should `Current L/D` default to a recent over-ground measured glide ratio?
- Should wind be part of this metric at all?
- If wind or TAS is included, should XCPro still call it `Current L/D`?
- Is a second air-referenced metric safer than changing the meaning of
  existing `ld_curr`?

## Files in this folder

- `current-ld.md`
  - single-file handoff brief for external review
- `wind-ld.md`
  - wind-specific decision brief for external review and release-grade planning
- `01_CURRENT_LD_RESEARCH_2026-04-07.md`
  - terminology, vendor examples, and why wind changes metric meaning
- `02_XCPRO_CURRENT_LD_IMPLEMENTATION_AUDIT_2026-04-07.md`
  - branch-truth owner paths, current inputs, and what each metric does not use
- `03_XCPRO_CURRENT_LD_IMPLEMENTATION_NOTES_2026-04-07.md`
  - updated option analysis now that XCPro ships both `ld_curr` and `ld_vario`
- `04_CURRENT_LD_REVIEW_QUESTIONS_2026-04-07.md`
  - direct prompts for external review

## Related repo docs

- `docs/POLAR/03_XCPRO_GENERAL_POLAR_CURRENT_LD_SEAM_AUDIT_2026-04-07.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md`
- `docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md`
