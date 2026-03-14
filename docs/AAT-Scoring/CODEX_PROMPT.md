# Codex Prompt — XCPro Real-Time AAT

You are working inside the XCPro codebase.

Read these files in order before making any code changes:

1. `README.md`
2. `01_RULES_REFERENCE.md`
3. `02_REALTIME_ENGINE_SPEC.md`
4. `03_AAT_SETUP_PAGE_SPEC.md`
5. `04_PHASED_IMPLEMENTATION_PLAN.md`
6. `05_TEST_PLAN.md`

## Your job

Implement a **real-time AAT leaderboard** for XCPro in a way that is honest about official vs provisional vs projected scoring.

## Hard constraints

- Do **not** create a second parallel AAT module if one already exists.
- Inspect the repo first and extend the real AAT implementation path.
- Keep geometry/scoring math out of UI layers.
- Use a single source of truth for AAT competition config.
- Default to **FAI-compatible profile** behavior.
- Treat custom geometries/rules as a **custom profile**, not default FAI behavior.
- Implement **Classic scoring first**.
- Do not fake Alternative scoring if it is not already implemented.
- Persist algorithm version + config hash.

## Required outcome

Add:

- an organizer-facing AAT setup/config screen
- rules-profile validation
- multi-pilot live AAT state
- credited-fix optimization for achieved areas
- live projected/provisional leaderboard
- finish closure handling
- FR-log reconciliation into official results
- tests

## Expected workflow

1. Inspect the repo and identify the real AAT package path.
2. Write a short repo-specific plan.
3. Implement Phase 1 through Phase 5 from the phased plan if feasible.
4. If something in the spec conflicts with the repo, adapt names/paths but keep the behavior.
5. Add tests from the test plan where practical.
6. Leave a short assumptions/TODO note if any part could not be completed.

## If the repo already contains partial implementations

- prefer refactoring and extending over duplication
- keep public behavior stable where possible
- add feature flags or warnings rather than silently changing competition behavior

## If time becomes tight

Ship this cut line first:

- config foundation
- setup page
- live pilot state aggregation
- projected/provisional leaderboard
- classic scoring
- finish closure
- tests

Do not label anything as official live scoring unless it is explicitly reconciled from accepted FR logs.
