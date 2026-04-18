# POLAR

This folder now keeps only the active polar reference material.
Superseded plan/status docs were archived on 2026-04-07 in
`archive/2026-04-doc-pass/`.

## Current XCPro reference

- Active polar authority: `feature/profile/src/main/java/com/trust3/xcpro/glider/GliderRepository.kt`
- Runtime sink/L-D/final-glide seam: `GliderRepository -> PolarStillAirSinkProvider`
- Source priority:
  - manual 3-point polar when valid
  - selected model polar when usable
  - default club fallback polar when neither is usable
- Fallback behavior is live in code and surfaced in the polar preview/help UI.

## Use these docs now

- `../ARCHITECTURE/PIPELINE.md`
  - current runtime wiring, owners, and card-feed seams
- `../ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md`
  - current glide/polar metric semantics and status
- `../ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md`
  - active change plan for remaining glide-computer hardening
- `../GLIDECOMPUTER/README_GLIDE_COMPUTER_RELEASE_GRADE_PACK_2026-03-27.md`
  - workflow pack for the current glide-computer release-grade work
- `../Cards/CurrentLD/README.md`
  - Current L/D review pack, including branch truth for `ld_curr` and
    `ld_vario`, wind distinctions, and external review questions

## Active docs in this folder

- `02_GLIDER_COMPUTER_POLAR_RESEARCH_2026-03-12.md`
  - background note on how glider computers use polar, MacCready, wind,
    safety height, and final glide
- `03_XCPRO_GENERAL_POLAR_CURRENT_LD_SEAM_AUDIT_2026-04-07.md`
  - branch-truth audit of active General Polar inputs, bugs/ballast behavior,
    deferred fields, and what `currentLD` / `ld_curr` plus
    `currentLDAir` / `ld_vario` actually require

## Archived docs

- `archive/2026-04-doc-pass/README.md`
  - archive rationale and replacement map for the superseded polar plans
