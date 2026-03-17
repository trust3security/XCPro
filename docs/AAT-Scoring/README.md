# XCPro Real-Time AAT Package for Codex

## Canonical location

Use `docs/AAT-Scoring` as the canonical AAT-LiveScoring planning folder for
this repo.

There is no separate `docs/AAT-LiveScoring` folder at the moment. Keep the work
here unless the repo explicitly renames the package later.

## Purpose

This package is a Codex-ready implementation brief for adding a real-time AAT
leaderboard to XCPro without pretending that in-flight results are already
official.

The package now assumes the repo-specific architecture decision below:

- `feature:tasks` remains the owner of task declaration and editing.
- `feature:competition` is the recommended new pure domain module for AAT live
  scoring.
- `feature:map-runtime` composes task snapshots, live fixes, config, and
  accepted tracks into one live competition state.
- `feature:map` owns organizer-facing setup and leaderboard UI.

## Why a dedicated AAT setup page is still recommended

Without a dedicated organizer-facing setup/config page, the implementation will
hide critical scoring decisions inside task editor UI or scoring defaults.

That is not acceptable for competition scoring.

The setup page should own explicit choices for:

- rules profile
- scoring system
- minimum task time enforcement
- projection mode
- finish closure handling
- leaderboard visibility
- algorithm version and config hash

## Read order for Codex

1. `CODEX_PROMPT.md`
2. `01_RULES_REFERENCE.md`
3. `02_REALTIME_ENGINE_SPEC.md`
4. `03_AAT_SETUP_PAGE_SPEC.md`
5. `06_REPO_MODULE_AND_SEAMS.md`
6. `04_PHASED_IMPLEMENTATION_PLAN.md`
7. `05_TEST_PLAN.md`
8. `../ARCHITECTURE/CHANGE_PLAN_AAT_LIVE_SCORING_2026-03-16.md`
9. `../ARCHITECTURE/ADR_AAT_LIVE_SCORING_BOUNDARIES_2026-03-16.md`

## Key decisions already made

1. Build a live projected leaderboard, not "official live scoring."
2. Keep task editing and competition scoring as separate authorities.
3. Create a new pure scoring domain module rather than expanding task editor
   state.
4. Keep runtime composition in `feature:map-runtime`, not in `feature:tasks`.
5. Treat custom geometries or local variants as a separate custom profile.
6. Ship Classic scoring first.
7. Keep domain math and scoring out of UI layers.
8. Persist an algorithm version and config hash for auditability.

## Important repo-specific corrections

- The current production AAT validation and calculator stack is not a safe
  leaderboard authority.
- `AATValidationBridge` is lossy and should not normalize task definition for
  scoring.
- `AATTaskCalculator.calculateFlightResult` is not scoring-grade authority.
- `TaskUiState` is task editor projection state, not competition runtime state.

## Minimum V1 deliverable

A pull request is acceptable only if it includes all of the following:

- organizer-facing AAT setup/config support
- FAI-profile validation guardrails
- multi-pilot live state model
- projected/provisional/official status model
- live leaderboard for AAT
- Classic scoring first
- reconciliation path from live tracking to accepted tracks
- automated tests

## Output expectation for Codex

Codex should produce:

1. a repo-specific implementation plan after inspection
2. the architecture decision and module split
3. the actual code changes
4. tests
5. a short note listing any unresolved assumptions
