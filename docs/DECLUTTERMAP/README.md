# DECLUTTERMAP README

## Purpose

This folder tracks the active close-proximity traffic declutter direction for
OGN and ADS-B map rendering.

Keep only current product direction and active implementation guidance at the
top level. Move execution prompts, audits, and superseded exploration docs into
`archive/`.

---

## Active Documents

Read these in order:

1. `xcpro-close-proximity-declutter-brief.md`
   - canonical product direction for close packed groups
2. `xcpro-phase-2a-close-proximity-declutter-plan.md`
   - active ownership and implementation contract for packed-group label control

Supporting repo-wide context:

- `../ARCHITECTURE/PIPELINE.md`
- `../ARCHITECTURE/CODING_RULES.md`
- `../ARCHITECTURE/ARCHITECTURE.md`

---

## Current Status

Status as of 2026-03-31:

- Phase 1 zoom-band declutter behavior is already in the traffic runtime path.
- Phase 2A packed-group label control is the current active documented contract.
- The narrow review-correction pass is implemented in the current branch:
  - selected-group fan-out radii scale from the packed-group footprint
  - ADS-B packed-group seeds align with the actual renderable subset
  - packed-group membership now excludes off-screen projected targets
  - ADS-B packed-group collision sizing now includes the outline halo
- There is no active top-level Phase 2B execution brief at the moment. Completed
  or superseded correction prompts belong in `archive/`.

If a later Phase 2B/2C follow-up starts, create a new active brief rather than
reusing archived execution prompts.

---

## Archiving Rule

Archive a declutter doc when any are true:

- it is a one-off execution prompt for a completed pass
- it describes an older implementation path that is no longer the active
  contract
- it is an audit or pass log rather than current product or architecture
  guidance
- its instructions conflict with the current active brief/plan

When archiving:

- keep a short superseded note at the top of the archived file
- link back to the active top-level documents
- do not leave duplicate "read this first" prompts in the live path
