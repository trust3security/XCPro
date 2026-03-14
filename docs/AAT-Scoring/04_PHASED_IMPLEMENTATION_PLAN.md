# 04 — Phased Implementation Plan

## Delivery philosophy

Ship this in phases so each phase leaves the repo in a valid, testable state.

The order below is chosen to reduce rework:

1. make rules/config explicit
2. correct/finish the domain engine
3. add live aggregation
4. add leaderboard UI
5. reconcile with official logs
6. add optional/custom extras

---

## Phase 0 — Repo Discovery and Guardrails

### Goal

Understand what already exists so no duplicate AAT subsystem is created.

### Tasks

- inspect actual AAT package path(s)
- locate existing:
  - task calculators
  - geometry classes
  - tracking pipeline
  - scoring pipeline
  - config persistence
  - UI framework (Compose / XML / other)
- identify whether there is already:
  - handicap support
  - competition roster model
  - results table / leaderboard UI
  - FR-log ingestion path

### Deliverables

- repo-specific short plan
- list of files/classes to extend
- no new behavior yet

### Exit criteria

- Codex knows exactly where the real AAT code lives
- Codex has identified whether setup page should be new or an extension of an existing admin/settings screen

---

## Phase 1 — Rules Profile + Config Foundation

### Goal

Create the configuration backbone that all later phases depend on.

### Tasks

- add `RulesProfile`
- add `ScoringSystem`
- add `LeaderboardMetric`
- add `ProjectionMode`
- add `AatCompetitionConfig`
- add config validation
- add config hash generation
- persist draft + published config
- create initial AAT setup screen skeleton

### Deliverables

- serializable config model
- validator
- setup page stub with save/load
- FAI vs custom profile toggles

### Exit criteria

- no AAT leaderboard code depends on hidden constants anymore
- a saved task config can be loaded and validated

---

## Phase 2 — Official/Provisional Single-Pilot Engine Hardening

### Goal

Make the per-pilot AAT engine rules-correct before competition-level aggregation.

### Tasks

- implement or verify:
  - area achievement by segment intersection
  - candidate point generation
  - credited-fix optimizer
  - explicit finished / outlanded-last-leg / outlanded-earlier-leg marking-distance paths
  - marking time and marking speed formulas
  - handicap transforms
- separate official vs provisional result calculation paths

### Deliverables

- trusted single-pilot AAT scoring core
- unit tests for each rule path

### Exit criteria

- one pilot can be scored correctly from both live data and FR-log data
- credited-fix logic is no longer just “area centers” or “current target points”

---

## Phase 3 — Live Pilot State Aggregation

### Goal

Add competition-level live state that updates as tracker fixes arrive.

### Tasks

- create per-pilot live state model
- integrate tracker fix ingestion
- update start/finish/area status in real time
- store provisional credited fixes
- add status transitions:
  - not started -> started
  - started -> achieved prefix
  - achieved prefix -> finished
  - achieved prefix -> outlanded
  - airborne -> finish-closed-unfinished

### Deliverables

- live state repository
- recompute loop
- pilot status cards / debug logs

### Exit criteria

- all pilots on a task can be represented at once
- every tracker fix can trigger a deterministic pilot-state update

---

## Phase 4 — Projection Engine + Live Leaderboard

### Goal

Rank the field in real time.

### Tasks

- implement projection modes:
  - optimize to min time
  - head home now
  - auto wrapper
- compute projected per-pilot outputs
- implement classic day-parameter engine
- compute projected/provisional classic scores
- build leaderboard ranking service
- add organizer-facing leaderboard screen

### Deliverables

- competition-level real-time AAT leaderboard
- clear official / provisional / projected labeling
- stable rank ordering

### Exit criteria

- XCPro can show who is leading right now in a defensible projected/provisional sense
- hidden pending-accounting pilots do not leak into visible ranking before finish closure

---

## Phase 5 — Finish Closure + Official Reconciliation

### Goal

Move from live standings to officialized standings cleanly.

### Tasks

- implement finish closure timestamp handling
- convert airborne pilots to outlanded-at-closure
- accept FR-log input
- recompute official scored results from accepted logs
- preserve live-to-official delta for audit/debug
- refresh leaderboard after officialization

### Deliverables

- finalization path
- official result rows
- audit-safe comparison of live vs official numbers

### Exit criteria

- a class day can move from live operation to final accepted results without manual rewrite of scoring logic

---

## Phase 6 — Polish, Custom Rules, and Optional Features

### Goal

Support repo-specific extras without compromising the default FAI path.

### Tasks

- expose custom geometries behind custom profile
- add richer confidence markers for sparse trackers
- add drill-down pilot detail screen
- add exports / debug traces
- add feature flags for unsupported scoring modes

### Deliverables

- cleaner UX
- safer extension points
- better operator visibility into why scores changed

### Exit criteria

- custom features no longer masquerade as standard FAI behavior
- debugging live scoring differences is practical

---

## Recommended ship cut lines

### Cut line A — Strong V1

Ship phases 1 through 5.

This is the recommended release.

### Cut line B — Minimum viable internal beta

Ship phases 1 through 4 only if:

- finish closure is still handled
- official log reconciliation is at least stubbed with obvious warnings
- output is clearly labeled non-official

Do not call this production-ready scoring.

---

## Suggested PR strategy

Prefer multiple coherent PRs over one giant PR.

### PR 1

- Phase 1 config foundation
- setup page skeleton
- validators

### PR 2

- Phase 2 per-pilot engine hardening
- unit tests

### PR 3

- Phase 3 live state aggregation
- tracker integration

### PR 4

- Phase 4 projection + leaderboard
- leaderboard UI

### PR 5

- Phase 5 finish closure + FR reconciliation
- final integration tests

---

## Risks and mitigations

### Risk: existing repo already has half-built leaderboard logic

Mitigation:

- inspect first
- refactor/replace surgically
- do not create duplicated state models

### Risk: custom task geometry is already deeply used

Mitigation:

- keep it under custom profile
- do not break old behavior unnecessarily
- separate compliance labeling from feature support

### Risk: sparse tracker data makes live scoring noisy

Mitigation:

- use segment intersection
- keep confidence metadata
- clearly label projected rows

### Risk: Alternative scoring requested too early

Mitigation:

- support config field now
- gate execution path
- keep V1 honest and Classic-first

---

## Definition of done

The implementation is done when:

- organizers can configure the task/rules explicitly
- pilots update live through a single scoring pipeline
- the leaderboard reflects official/provisional/projected status correctly
- finish closure and official FR-log reconciliation are covered
- automated tests cover the critical rule paths
