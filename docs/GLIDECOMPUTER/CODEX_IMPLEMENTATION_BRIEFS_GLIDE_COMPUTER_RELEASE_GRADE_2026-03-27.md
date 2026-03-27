# CODEX_IMPLEMENTATION_BRIEFS_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27

Each section below is intended to be copied into Codex as its own task.
Run only one phase at a time.
Always run the matching PASS/FAIL review before moving on.

---

## Phase 0 implementation brief

```text
Use /plan first.

You are working in the local XCPro repository.

Goal:
Implement Phase 0 only for the glide-computer release-grade plan:
1) freeze the metric contract in-repo
2) remove production exposure of placeholder/unimplemented glide-computer cards
3) stop there

Read first:
- AGENTS.md
- docs/ARCHITECTURE/AGENT.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md
- docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md

Hard constraints:
- Do not add new runtime repositories in this phase
- Do not implement waypoint/task metrics yet
- Do not move business logic into UI
- Do not expand CompleteFlightData
- Do not use TaskRepository as runtime authority
- Keep scope narrow and low-risk

Required work:
- add the change-plan doc and metric-contract doc if they are not already in the repo
- audit the card catalogs and formatter availability for:
  - WPT DIST
  - WPT BRG
  - WPT ETA
  - TASK SPD
  - TASK DIST
  - TASK REMAIN DIST if cataloged
  - TASK REMAIN TIME if cataloged
  - START ALT
- remove or hide any of those that are still placeholders / no-op / not backed by authoritative runtime data
- keep already-implemented cards available
- add/update focused tests proving placeholder cards are not exposed in production
- update docs minimally so branch truth is clear

Acceptance criteria:
- no placeholder waypoint/task glide-computer cards are offered in production card selection
- metric semantics are documented in-repo
- no runtime owner changes happened yet
- touched tests pass
- enforceRules passes

Validation:
- run focused tests for touched card-library / formatter/catalog code
- ./gradlew enforceRules
- compile touched modules if needed

Required output:
1) concise plan
2) exact files changed and what each owns
3) commands run
4) validation results
5) which cards were hidden/removed and why
6) stop
```

---

## Phase 1 implementation brief

```text
Use /plan first.

You are working in the local XCPro repository.

Goal:
Implement Phase 1 only for the glide-computer release-grade plan:
1) harden source/validity for currently implemented glide-computer metrics
2) optionally add a low-risk FINAL DIST card if it is already backed by authoritative finish-distance data
3) stop there

Read first:
- AGENTS.md
- docs/ARCHITECTURE/AGENT.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md

Hard constraints:
- Do not implement waypoint/task repositories yet
- Do not broaden target kinds
- Do not move logic into feature:map UI classes
- Do not expand CompleteFlightData with task/glide-owned state

Required work:
- audit currently implemented cards and their source/validity behavior:
  - IAS
  - TAS
  - GS
  - current L/D
  - polar L/D
  - best L/D
  - netto
  - netto 30s
  - final glide / required L/D
  - arrival altitude
  - required altitude
  - arrival altitude MC0
- add explicit validity/source fields where cards currently rely on heuristic formatter guesses
- make IAS/TAS labels use the real airspeed source contract
- add `nettoAverage30sValid` (or equivalent) if missing
- add explicit validity for L/D fields instead of `> 1` style heuristics
- if finish-distance data already exists authoritatively, add a low-risk `FINAL DIST` or `TASK FINISH DIST` card end-to-end
- update focused tests and docs

Acceptance criteria:
- formatter/card code no longer infers critical validity heuristically when explicit validity should exist
- IAS/TAS source labeling is accurate
- NETTO 30S has explicit validity
- no new UI business logic
- tests and enforceRules pass

Validation:
- focused tests for touched runtime/adapter/formatter code
- ./gradlew enforceRules
- compile touched modules

Required output:
1) concise plan
2) exact files changed and ownership
3) commands run
4) validation results
5) which validity/source contracts were added
6) whether FINAL DIST was added or deferred
7) stop
```

---

## Phase 2 implementation brief

```text
Use /plan first.

You are working in the local XCPro repository.

Goal:
Implement Phase 2 only for the glide-computer release-grade plan:
1) add an upstream waypoint-navigation runtime seam
2) implement WPT DIST, WPT BRG, and WPT ETA
3) keep UI as consumer only
4) stop there

Read first:
- AGENTS.md
- docs/ARCHITECTURE/AGENT.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md
- docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md

Architecture rules:
- route authority remains in feature:tasks
- create the new waypoint-navigation owner in feature:map-runtime
- feature:map and formatters remain consumer only
- no TaskRepository runtime reads
- no duplicate route geometry logic
- use NavigationRouteRepository.route as the canonical route seam
- preserve replay determinism

Required work:
- add a canonical non-UI waypoint-navigation output contract, for example:
  - WaypointNavigationSnapshot
- add a dedicated non-UI owner, for example:
  - WaypointNavigationRepository
- derive it from:
  - FlightDataRepository.flightData
  - NavigationRouteRepository.route
  - any narrow, justified additional runtime input such as wind for ETA
- implement:
  - WPT DIST
  - WPT BRG
  - WPT ETA
- ensure active target point semantics follow the canonical route seam; do not reintroduce waypoint-center shortcuts if the route seam already exposes a better target point
- map the new values through the adapter layer into card-facing data
- re-enable the waypoint cards hidden in Phase 0 only when their data is authoritative
- add focused tests
- update docs

Acceptance criteria:
- WPT DIST, WPT BRG, and WPT ETA are computed upstream, not in UI or formatters
- they derive from the canonical route seam
- no duplicate route math in map UI
- tests cover invalid/no-task plus at least one boundary-aware case
- enforceRules passes

Validation:
- focused map-runtime tests
- touched map/card tests
- ./gradlew enforceRules
- compile touched modules

Required output:
1) concise plan
2) exact files changed and ownership
3) commands run
4) validation results
5) semantics used for WPT DIST/BRG/ETA
6) stop
```

---

## Phase 3 implementation brief

```text
Use /plan first.

You are working in the local XCPro repository.

Goal:
Implement Phase 3 only for the glide-computer release-grade plan:
1) add an upstream task-performance runtime seam
2) implement TASK SPD, TASK DIST, TASK REMAIN DIST, TASK REMAIN TIME, and START ALT
3) keep UI as consumer only
4) stop there

Read first:
- AGENTS.md
- docs/ARCHITECTURE/AGENT.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md

Architecture rules:
- task runtime authority remains TaskManagerCoordinator.taskSnapshotFlow
- route authority remains NavigationRouteRepository in feature:tasks
- new task-performance owner belongs in feature:map-runtime
- no TaskRepository runtime reads
- no UI-local calculations
- preserve replay determinism

Required work:
- freeze and implement these semantics:
  - TASK SPD = achieved task speed since accepted task start
  - TASK DIST = covered task distance since accepted task start
  - TASK REMAIN DIST = canonical remaining task distance
  - TASK REMAIN TIME = estimated remaining task time using an explicit documented policy
  - START ALT = nav altitude captured at accepted task start crossing with explicit altitude-reference semantics
- add a canonical non-UI output contract, for example:
  - TaskPerformanceSnapshot
- add a dedicated non-UI owner, for example:
  - TaskPerformanceRepository
- derive it from authoritative task/runtime seams and fused flight data only
- map these values into card-facing data through the adapter layer
- re-enable the task-performance cards hidden in Phase 0 only when authoritative
- add focused tests
- update docs

Acceptance criteria:
- all listed metrics are computed upstream and mapped only by adapters
- semantics are explicit and documented
- no TaskRepository runtime reads
- no UI business logic added
- tests and enforceRules pass

Validation:
- focused map-runtime tests
- touched map/card tests
- ./gradlew enforceRules
- compile touched modules

Required output:
1) concise plan
2) exact files changed and ownership
3) commands run
4) validation results
5) semantics used for each task-performance card
6) stop
```

---

## Phase 4 implementation brief

```text
Use /plan first.

You are working in the local XCPro repository.

Goal:
Implement Phase 4 only for the glide-computer release-grade plan:
1) harden the glide/polar contract for release trust
2) keep the architecture boundary intact
3) stop there

Read first:
- AGENTS.md
- docs/ARCHITECTURE/AGENT.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md
- docs/POLAR/* relevant docs if present

Hard constraints:
- do not broaden target kinds
- do not move logic into feature:map UI
- do not expand CompleteFlightData
- keep replay determinism
- keep scope on correctness/hardening

Required work:
- audit the IAS/TAS contract end-to-end at StillAirSinkProvider and downstream consumers
- make glide solve states explicit (valid / degraded / invalid) where current behavior is too implicit
- audit General Polar fields exposed in UI:
  - three-point polar
  - reference weight
  - user coefficients
  - bugs / ballast effects
- decide and implement the smallest release-grade fix:
  - wire exposed fields into the authoritative solver path if they are meant to be supported
  - or remove/hide/document any exposed field that is not truly supported
- add focused tests proving:
  - manual polar changes affect authoritative glide outputs
  - any newly-supported General Polar fields affect authoritative outputs as intended
  - degraded vs invalid states behave as documented
- update docs

Acceptance criteria:
- IAS vs TAS contract is explicit and consistent
- glide states are explicit
- no misleading General Polar controls remain exposed as if authoritative when they are not
- tests and enforceRules pass

Validation:
- focused profile/flight-runtime/map-runtime tests
- ./gradlew enforceRules
- compile touched modules

Required output:
1) concise plan
2) exact files changed and ownership
3) commands run
4) validation results
5) which General Polar fields are authoritative after this phase
6) stop
```

---

## Phase 5 implementation brief

```text
Use /plan first.

You are working in the local XCPro repository.

Goal:
Implement Phase 5 only for the glide-computer release-grade plan:
1) finalize docs
2) add any final golden/replay tests needed for confidence
3) run the full local proof
4) stop and report release readiness

Read first:
- AGENTS.md
- docs/ARCHITECTURE/AGENT.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md
- docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md

Required work:
- finalize docs so branch truth and durable ownership are clear
- add/update replay/golden tests for risky semantics where still missing
- ensure every shipped glide-computer card is either:
  - implemented with explicit validity/source semantics
  - or absent from production card selection
- run the full local proof:
  - ./gradlew enforceRules
  - ./gradlew testDebugUnitTest
  - ./gradlew assembleDebug

Acceptance criteria:
- docs are final and coherent
- full local proof passes
- no placeholder cards remain exposed
- branch is ready for push/PR

Required output:
1) concise plan
2) exact files changed
3) commands run
4) validation results
5) whether the branch is release-ready
6) stop
```
