# XCPro Real-Time AAT Package for Codex

## Purpose

This package is a codex-ready implementation brief for adding a **real-time AAT leaderboard** to XCPro without pretending that in-flight results are already official.

The package assumes:

- XCPro already has a meaningful amount of **single-pilot AAT geometry and calculator code**.
- The missing capability is mainly **competition-level aggregation, live projection, rule-aware configuration, and final reconciliation**.
- The safest product model is:
  - **Official** = finished or outlanded and validated from accepted FR logs
  - **Provisional** = finished or outlanded from live tracking but not yet log-validated
  - **Projected** = still airborne, ranked by a deterministic live projection

## Why a dedicated AAT Setup page is recommended

Yes — add it.

Without a dedicated setup/config page, the implementation will end up with hidden assumptions about:

- rules profile
- classic vs alternative scoring
- handicaps
- minimum task time
- allowed geometries
- finish closure handling
- leaderboard visibility
- algorithm version / hash

That is too much implicit behavior for competition scoring.

The setup page should be **organizer/admin-facing**, not pilot-editable in flight.

## Read order for Codex

1. `CODEX_PROMPT.md`
2. `01_RULES_REFERENCE.md`
3. `02_REALTIME_ENGINE_SPEC.md`
4. `03_AAT_SETUP_PAGE_SPEC.md`
5. `04_PHASED_IMPLEMENTATION_PLAN.md`
6. `05_TEST_PLAN.md`

## Key decisions already made

1. **Build a live projected leaderboard, not “official live scoring.”**
2. **Default rules profile = current FAI Annex A-compatible behavior.**
3. **Treat custom geometries or local variants as a separate custom profile.**
4. **Ship Classic scoring first.**
5. **Alternative scoring should be configurable only if already present; otherwise gate it and clearly label it unsupported in V1.**
6. **Do not create a second AAT module if one already exists. Extend the real one.**
7. **Keep domain math and scoring out of UI layers.**
8. **Persist an algorithm version + config hash for auditability.**

## Important corrections to existing internal docs

The uploaded internal AAT docs are useful, but Codex should correct these points:

- `Speed = Distance / MAX(elapsed_time, minimum_time)` is the **AAT marking speed formula**, not the entire day score.
- Current FAI Annex A does **not** make “keyhole” or “start sector” a default AAT geometry. If XCPro already supports them, they belong under a **custom/local rules profile**, not the default FAI profile.
- A cylinder start exists in Annex A, but it is noted as not to be used **without a specific waiver**.
- Before finish closure, pilots not fully accounted for should not appear in the ranking even though scorers may use assumptions to keep preliminary results representative.

## Repo hygiene expectations for Codex

Before editing code:

- inspect the actual repo for the existing AAT package path
- reuse existing AAT calculator / validator / path optimizer code where possible
- do not duplicate package trees because the uploaded docs show inconsistent historical paths
- route all new rules/config through a single source of truth

## Minimum V1 deliverable

A pull request is acceptable only if it includes all of the following:

- organizer-facing AAT setup/config support
- FAI-profile validation guardrails
- multi-pilot live state model
- projected/provisional/official status model
- live leaderboard for AAT
- classic scoring day-parameter computation
- reconciliation path from live tracking to accepted FR logs
- automated tests

## Suggested delivery cut line

If the repo or time budget forces a narrower first release, the minimum acceptable cut line is:

- Rules profile + setup page
- Live state aggregation
- Provisional/projected leaderboard
- Classic scoring only
- Final log reconciliation
- Tests

Anything else can stay behind feature flags.

## Output expectation for Codex

Codex should produce:

1. a repo-specific implementation plan after inspection
2. the actual code changes
3. tests
4. a short note listing any assumptions or TODOs that could not be resolved from the repo itself
