# ADR_AAT_LIVE_SCORING_BOUNDARIES_2026-03-16

## Metadata

- Title: AAT live scoring uses a dedicated competition domain module and keeps
  task declaration separate
- Date: 2026-03-16
- Status: Proposed
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: ARCH-20260316-AAT-LIVE-SCORING
- Related change plan:
  - `docs/ARCHITECTURE/CHANGE_PLAN_AAT_LIVE_SCORING_2026-03-16.md`
- Supersedes: None
- Superseded by: None

## Context

- Problem:
  - XCPro already has explicit task declaration ownership in `feature:tasks`,
    but it has no production owner for organizer scoring config, live
    multi-pilot AAT state, official/provisional/projected row state, or
    accepted-track reconciliation.
  - The current task seam is mutable-by-design, so live scoring also needs an
    explicit published/frozen competition-day boundary.
  - Live OGN identity and timestamps are useful inputs, but they are not
    scoring-grade pilot identity or chronology authorities by themselves.
  - Existing AAT validation and calculator helpers are not a safe foundation for
    leaderboard authority.
- Why now:
  - the AAT live scoring docs require a real implementation path, and adding
    scoring into task editor state would create architecture drift immediately.
- Constraints:
  - preserve MVVM + UDF + SSOT
  - do not create `feature:competition -> feature:tasks` or
    `feature:competition -> feature:igc` back-edges
  - keep replay and deterministic scoring rules explicit
  - avoid hidden global mutable state
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/AAT-Scoring/06_REPO_MODULE_AND_SEAMS.md`

## Decision

Add a new module, `:feature:competition`, as the pure AAT live scoring domain
owner. Keep runtime composition in `feature:map-runtime`, keep organizer UI in
`feature:map`, and keep task declaration/editing in `feature:tasks`.

Required:

- ownership/boundary choice:
  - `TaskManagerCoordinator.taskSnapshotFlow` remains the authoritative read seam
    for active task definition before publish.
  - A new immutable `AatPublishedCompetitionDay` becomes the scoring input
    bundle after publish. It contains published config, frozen task snapshot,
    and frozen roster snapshot.
  - A new `AatCompetitionRuntimeRepository` in `feature:map-runtime` becomes the
    authoritative live scoring state owner.
  - Organizer scoring config is owned by a dedicated config repository, not the
    task sheet.
  - Scored `pilotId` is owned by the roster/publish path, not by OGN
    `competitionNumber`, registration, or transport `canonicalKey`.
  - Live-fix adapters must convert tracker inputs into normalized scoring fixes
    with explicit quality/degraded-state metadata.
  - Accepted-track reconciliation is fed through a narrow read seam over
    `feature:igc`, not direct UI/file access.
- dependency direction impact:
  - `feature:competition` stays pure and normalized.
  - `feature:map-runtime` composes `feature:tasks`, `feature:igc`, and
    `feature:competition`.
  - `feature:map` reads setup/leaderboard state and renders it only.
- API/module surface impact:
  - add `:feature:competition`
  - add normalized scoring models and ports
  - add `AatCompetitionRuntimeRepository.state` as the live scoring read seam
- time-base/determinism impact:
  - scoring decisions use source UTC fix/log timestamps and explicit closure
    timestamps
  - tracker receipt/last-seen monotonic times are transport diagnostics only and
    must not become scoring chronology
  - no wall-clock `now()` calls are allowed in scoring formulas
- concurrency/buffering/cadence impact:
  - runtime scoring is event-driven on incoming fixes, config changes, closure,
    and reconciliation events
  - initial runtime owner is host ViewModel-owned, not process-global

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Put scoring in `feature:tasks` | looks close to existing AAT code | breaks task SSOT boundary and forces IGC/runtime concerns into task editor ownership |
| Put everything in `feature:map-runtime` | avoids new module count | mixes pure domain rules with adapters and runtime state, making testing and ownership weaker |
| Put everything in `feature:map` | fastest UI-first path | collapses business rules into UI-facing module and violates layering |
| Create `:feature:scoring` with formulas only | simpler name | too narrow for config, statuses, projection, and reconciliation policy ownership |

## Consequences

### Benefits

- task declaration stays stable
- scoring gets one explicit domain owner
- runtime composition uses an existing higher-level dependency seam
- testing becomes cheaper because scoring stays normalized and pure

### Costs

- one new module is added
- adapter and mapper work is required in `feature:map-runtime`
- accepted-track read seams must be added deliberately

### Risks

- the new module could grow UI or task-specific leaks if review is weak
- adapter quality becomes critical because normalized inputs must preserve task
  geometry
- scoring could drift if mutable task edits after publish are not frozen and
  versioned explicitly
- traffic identity could be mistaken for scoring identity if roster ownership is
  left implicit
- official reconciliation depends on future `feature:igc` read contracts

## Validation

- Tests/evidence required:
  - scoring engine unit tests
  - published-day freeze tests
  - normalized task mapper tests
  - roster identity-resolution tests
  - degraded live-fix adapter tests
  - runtime determinism tests
  - config repository round-trip tests
  - setup and leaderboard ViewModel tests
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - live leaderboard updates should stay within the Phase 5 UI refresh target
    defined in the change plan
- Rollout/monitoring notes:
  - land the module and config foundation first
  - do not expose organizer UI until the runtime repository exists
  - do not expose official labeling until reconciliation exists

## Documentation Updates Required

- `ARCHITECTURE.md`: No change required yet.
- `CODING_RULES.md`: No change required yet.
- `PIPELINE.md`: Update after runtime wiring lands in code.
- `CONTRIBUTING.md`: No change required yet.
- `KNOWN_DEVIATIONS.md`: No change required unless implementation intentionally
  violates the split.

## Rollback / Exit Strategy

- What can be reverted independently:
  - setup UI, leaderboard UI, and reconciliation phases can be reverted without
    removing the domain module foundation
- What would trigger rollback:
  - task declaration regressions
  - non-deterministic scoring output
  - module boundary violations discovered during implementation
- How this ADR is superseded or retired:
  - supersede only if a later accepted ADR moves competition domain ownership to
    another explicit boundary without reintroducing duplicated SSOT
