# 04 - Phased Implementation Plan

## Delivery philosophy

Ship this in phases so each phase leaves the repo in a valid, testable state.

The repo-specific order below is chosen to preserve the current task SSOT while
adding a new competition-scoring authority.

High-level rule:

- `feature:tasks` remains the owner of task declaration and editing.
- `feature:competition` becomes the owner of pure AAT live scoring domain logic.
- `feature:map-runtime` becomes the owner of runtime composition and live
  competition state.
- `feature:map` becomes the owner of organizer-facing setup and leaderboard UI.
- live scoring runs against a frozen published competition day, not the mutable
  task editor snapshot.

---

## Phase 0 - Discovery, ADR, and Guardrails

### Goal

Lock the module boundary before code is written so scoring does not leak into
task editor state.

### Tasks

- confirm the current authoritative seams:
  - `TaskManagerCoordinator.taskSnapshotFlow`
  - `TaskRepository.state`
  - `IgcFlightLogRepository`
  - `MapTasksUseCase`
- confirm tracker identity and timestamp quality seams:
  - OGN `competitionNumber`
  - OGN `canonicalKey`
  - source timestamp vs `lastSeenMillis`
- reconcile older scoring docs so implementation does not start from stale
  assumptions about mutable task input or raw tracker identity
- write the repo-specific change plan
- write the module-boundary ADR
- identify temporary bridge points that are allowed in early phases

### Deliverables

- approved change plan
- approved ADR
- repo-specific module and seam proposal

### Exit criteria

- there is one explicit answer for where scoring state will live
- there is one explicit answer for where task definition will stay
- no implementation task depends on extending `TaskUiState`

---

## Phase 1 - New Competition Module Foundation

### Goal

Create the new pure scoring domain module and its normalized contracts.

### Tasks

- add `:feature:competition`
- add package roots:
  - `com.example.xcpro.competition.aat.config`
  - `com.example.xcpro.competition.aat.model`
  - `com.example.xcpro.competition.aat.roster`
  - `com.example.xcpro.competition.aat.engine`
  - `com.example.xcpro.competition.aat.policy`
  - `com.example.xcpro.competition.aat.ports`
- add normalized models:
  - `AatPublishedCompetitionDay`
  - `AatTaskDefinitionSnapshot`
  - `AatCompetitionConfig`
  - `AatRosterSnapshot`
  - `AatPilotFix`
  - `AatPilotFixQuality`
  - `AatAcceptedTrack`
  - `AatPilotScoringState`
  - `AatPilotResult`
  - `AatLeaderboardRow`
- add config validation and config hash generation
- add published-day hash generation from config + task snapshot + roster snapshot
- keep the module free of Compose, ViewModels, MapLibre, and direct IGC/task
  dependencies

### Deliverables

- compiling `feature:competition` module
- normalized scoring contracts
- config validator and hash policy owner

### Exit criteria

- scoring formulas no longer need to read task editor models directly
- no new scoring code is added to `feature:tasks`

---

## Phase 2 - Organizer Config and Persistence

### Goal

Make organizer scoring configuration, publish freeze, and roster identity
explicit without using the task sheet as the state owner.

### Tasks

- add draft config persistence owner in `feature:map-runtime`
- add published competition-day persistence owner in `feature:map-runtime`
- add published vs draft states
- create `Task` -> `AatTaskDefinitionSnapshot` adapter in `feature:map-runtime`
- add roster source and `AatPilotIdentityResolver` in `feature:map-runtime`
- publish must freeze:
  - config
  - current task snapshot
  - current roster snapshot
- build organizer-facing setup screen in `feature:map`
- wire setup UI to draft and published-day repositories
- keep task min/max time editing in task UI, but do not let task UI own
  leaderboard policy
- detect task changes after publish and require republish

### Deliverables

- `AatCompetitionDraftRepository`
- `AatPublishedCompetitionDayRepository`
- `AatPilotRosterRepository`
- organizer setup page skeleton
- saved draft and published-day paths

### Exit criteria

- rules profile, scoring system, visibility policy, finish closure policy, and
  projection mode are stored in explicit config
- the scored day is frozen at publish time
- task edits after publish do not silently alter scoring inputs
- no leaderboard policy is hidden inside UI constants or `RulesBTTab`

---

## Phase 3 - Single-Pilot Scoring Engine Hardening

### Goal

Replace the current scoring-like helpers with a trusted single-pilot scorer in
the new module.

### Tasks

- implement scoring-grade area achievement
- implement credited-fix selection from real track intersection
- implement completed / outlanded / finish-closed-unfinished / DNF result paths
- implement official vs provisional result production
- add handicap hooks behind explicit policy
- require stable roster `pilotId` on domain inputs
- keep edit-time validation separate from scoring-time evaluation
- treat current `AATValidationBridge` and `AATTaskCalculator.calculateFlightResult`
  as non-authoritative compatibility code

### Deliverables

- `AatSinglePilotScorer`
- `AatProjectionEngine`
- `AatOfficialResultReconciler`
- unit tests for each rules path

### Exit criteria

- one pilot can be scored from live fixes or accepted tracks using the same
  domain models
- credited fixes are no longer area centers or current UI targets by fallback

---

## Phase 4 - Live Runtime Repository in `feature:map-runtime`

### Goal

Add the runtime authority that composes the published competition day, live
fixes, and accepted tracks into one live competition state.

### Tasks

- add `AatCompetitionRuntimeRepository` in `feature:map-runtime`
- add adapters:
  - `TaskCoordinatorAatTaskDefinitionAdapter`
  - tracker/live-fix adapter
  - roster identity resolver
  - published competition-day repository adapter
- process pilot fixes deterministically in arrival order
- maintain per-pilot live state and provisional result state
- publish `StateFlow<AatCompetitionState>`
- consume published competition day, not mutable live task/editor state
- preserve fix quality/confidence from adapters into runtime state

### Deliverables

- runtime repository
- deterministic event-processing loop
- per-pilot live scoring state
- explicit degraded/unmatched pilot states

### Exit criteria

- all pilots on a task can be represented at once
- leaderboard consumers read one runtime state owner instead of recomputing in
  UI code
- raw OGN targets are not used as scoring models directly

---

## Phase 5 - Projection and Leaderboard UI

### Goal

Rank the field in real time and render it through dedicated organizer UI.

### Tasks

- implement projection modes:
  - optimize to minimum time
  - head home now
  - auto wrapper
- compute projected rows for airborne pilots
- compute provisional rows for unfinished but no-longer-airborne pilots
- add organizer leaderboard screen in `feature:map`
- add pilot detail drill-down and confidence labeling
- surface stale-published-day and unmatched-pilot warnings in organizer UI when
  relevant

### Deliverables

- live leaderboard state
- organizer leaderboard screen
- official / provisional / projected labels

### Exit criteria

- XCPro can show who is leading now without claiming those rows are official
- hidden or not-yet-accounted-for pilots do not leak into the visible ranking
- operators can see when live results are degraded because roster matching or
  source timing quality is weak

---

## Phase 6 - Finish Closure and Official Reconciliation

### Goal

Move from live standings to final standings using accepted tracks and one
scoring authority.

### Tasks

- add finish closure handling
- convert airborne pilots to closure-state results when required by policy
- add a narrow accepted-track read seam over `feature:igc`
- reconcile official results from accepted tracks
- reconcile accepted tracks to the same roster-owned `pilotId`
- preserve live vs official delta for audit/debug

### Deliverables

- finish closure path
- official reconciliation path
- accepted-track adapter

### Exit criteria

- live standings can move to official standings without changing scoring
  formulas
- accepted-track reconciliation is explicit and testable

---

## Phase 7 - Custom Rules, Flags, and Polish

### Goal

Support repo-specific extras without corrupting the default FAI profile.

### Tasks

- expose keyhole and start-sector only under `CUSTOM_LOCAL_RULES`
- add feature flags for unsupported scoring variants
- add exports, traces, and operator debug views
- add richer sparse-tracker confidence markers

### Deliverables

- custom/local rules path
- debugging and audit tooling
- cleaner organizer UX

### Exit criteria

- custom geometries do not masquerade as default FAI behavior
- operators can explain why a score changed

---

## Recommended ship cut lines

### Cut line A - Strong V1

Ship phases 1 through 6.

This is the recommended first release.

### Cut line B - Internal beta only

Ship phases 1 through 5 only if:

- finish closure policy is already explicit in config
- official reconciliation is visibly not yet final
- every row is labeled non-official until reconciliation lands

Do not call this production-ready scoring.

---

## Suggested PR strategy

Prefer multiple coherent PRs over one giant PR.

### PR 1

- Phase 1 competition module foundation
- ADR and change plan references

### PR 2

- Phase 2 organizer config and persistence
- setup UI skeleton

### PR 3

- Phase 3 single-pilot scoring hardening
- unit tests

### PR 4

- Phase 4 live runtime repository
- task/tracker adapters

### PR 5

- Phase 5 projection and leaderboard UI

### PR 6

- Phase 6 finish closure and official reconciliation
- final integration tests

---

## Risks and mitigations

### Risk: scoring gets bolted onto task sheet state

Mitigation:

- keep task declaration and competition state in different authorities
- reject leaderboard state in `TaskUiState`

### Risk: existing AAT helpers look complete but are not

Mitigation:

- treat current validation and calculator helpers as compatibility code
- replace scoring authority with the new module before multi-pilot work

### Risk: `feature:competition` starts pulling in task and IGC internals

Mitigation:

- normalize all input through ports
- keep adapters in `feature:map-runtime`

### Risk: sparse trackers make live rows noisy

Mitigation:

- store confidence metadata
- label projected rows clearly
- reconcile with accepted tracks later

---

## Definition of done

The implementation is done when:

- organizers configure AAT live scoring through explicit config
- live scoring uses one runtime state owner
- official / provisional / projected states are explicit
- finish closure and accepted-track reconciliation are covered
- automated tests cover the critical scoring and boundary paths
