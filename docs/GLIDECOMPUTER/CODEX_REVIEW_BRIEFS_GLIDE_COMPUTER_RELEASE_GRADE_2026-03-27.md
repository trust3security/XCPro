# CODEX_REVIEW_BRIEFS_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27

Each section below is intended to be copied into Codex as a strict PASS/FAIL review after the matching implementation phase.
Do not skip these reviews.

---

## Phase 0 PASS/FAIL review brief

```text
You are reviewing a local XCPro implementation branch.

Do NOT modify source files.
Do NOT fetch, pull, push, open PRs, or use GitHub.
You may run read-only inspection commands and validation commands only.

Goal:
Review Phase 0 of the glide-computer release-grade plan only.

Read first:
- AGENTS.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md

Check explicitly:
1) PASS/FAIL: metric contract docs exist and are coherent
2) PASS/FAIL: placeholder waypoint/task cards are no longer exposed in production selection
3) PASS/FAIL: no runtime-owner changes happened in this phase
4) PASS/FAIL: no UI business logic was added
5) PASS/FAIL: focused validation passed

Inspect or run:
- git status --short
- git diff --stat
- rg -n "WPT DIST|WPT BRG|WPT ETA|TASK SPD|TASK DIST|START ALT" dfcards-library feature
- inspect touched card-catalog / formatter files directly
- run the focused tests the implementation reported
- ./gradlew enforceRules

Required output:
1) OVERALL: PASS or FAIL
2) Scope control: PASS/FAIL
3) Acceptance criteria table with evidence
4) Validation table
5) Out-of-scope changes
6) Minimal fix list to reach PASS
7) Whether Phase 1 may start: YES/NO

Be strict.
```

---

## Phase 1 PASS/FAIL review brief

```text
You are reviewing a local XCPro implementation branch.

Do NOT modify source files.
Do NOT fetch, pull, push, open PRs, or use GitHub.

Goal:
Review Phase 1 of the glide-computer release-grade plan only.

Read first:
- AGENTS.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md

Check explicitly:
1) PASS/FAIL: explicit source/validity fields replaced heuristic formatter guesses where intended
2) PASS/FAIL: IAS/TAS labels use the actual airspeed source contract
3) PASS/FAIL: NETTO 30S has explicit validity
4) PASS/FAIL: L/D cards no longer rely on brittle heuristics for validity
5) PASS/FAIL: if FINAL DIST was added, it uses authoritative finish-distance data only
6) PASS/FAIL: no waypoint/task runtime seam work started yet
7) PASS/FAIL: no UI business logic was added
8) PASS/FAIL: focused validation passed

Inspect or run:
- git status --short
- git diff --stat
- rg -n "tasValid|iasValid|airspeedSource|nettoAverage30s|currentLD|polarBestLd|polarLdCurrentSpeed" feature dfcards-library
- inspect touched adapter/formatter/runtime files directly
- run the focused tests the implementation reported
- ./gradlew enforceRules

Required output:
1) OVERALL: PASS or FAIL
2) Scope control: PASS/FAIL
3) Acceptance criteria table with evidence
4) Validation table
5) Out-of-scope changes
6) Minimal fix list to reach PASS
7) Whether Phase 2 may start: YES/NO

Be strict.
```

---

## Phase 2 PASS/FAIL review brief

```text
You are reviewing a local XCPro implementation branch.

Do NOT modify source files.
Do NOT fetch, pull, push, open PRs, or use GitHub.

Goal:
Review Phase 2 of the glide-computer release-grade plan only.

Read first:
- AGENTS.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md

Check explicitly:
1) PASS/FAIL: route authority remains task-owned and boundary-aware
2) PASS/FAIL: a non-UI waypoint-navigation owner exists
3) PASS/FAIL: WPT DIST / WPT BRG / WPT ETA are computed upstream, not in UI or formatters
4) PASS/FAIL: the new owner derives from authoritative seams only
5) PASS/FAIL: no TaskRepository runtime reads exist on this path
6) PASS/FAIL: at least one boundary-aware case is covered by focused tests
7) PASS/FAIL: no duplicate route math leaked into feature:map
8) PASS/FAIL: focused validation passed

Inspect or run:
- git status --short
- git diff --stat
- rg -n "WaypointNavigation|WPT DIST|WPT BRG|WPT ETA|NavigationRouteRepository" feature dfcards-library
- rg -n "TaskRepository" feature/map-runtime feature/map feature/tasks
- inspect touched runtime/adapter/formatter files directly
- run the focused tests the implementation reported
- ./gradlew enforceRules

Required output:
1) OVERALL: PASS or FAIL
2) Scope control: PASS/FAIL
3) Acceptance criteria table with evidence
4) Validation table
5) Out-of-scope changes
6) Architecture violations
7) Minimal fix list to reach PASS
8) Whether Phase 3 may start: YES/NO

Be strict.
```

---

## Phase 3 PASS/FAIL review brief

```text
You are reviewing a local XCPro implementation branch.

Do NOT modify source files.
Do NOT fetch, pull, push, open PRs, or use GitHub.

Goal:
Review Phase 3 of the glide-computer release-grade plan only.

Read first:
- AGENTS.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md

Check explicitly:
1) PASS/FAIL: a non-UI task-performance owner exists
2) PASS/FAIL: TASK SPD / TASK DIST / TASK REMAIN DIST / TASK REMAIN TIME / START ALT are computed upstream
3) PASS/FAIL: semantics for those metrics are explicit and documented
4) PASS/FAIL: no TaskRepository runtime reads exist on this path
5) PASS/FAIL: feature:map remains consumer only
6) PASS/FAIL: focused tests cover start-altitude and task-speed semantics
7) PASS/FAIL: focused validation passed

Inspect or run:
- git status --short
- git diff --stat
- rg -n "TaskPerformance|TASK SPD|TASK DIST|START ALT|taskSnapshotFlow" feature dfcards-library
- rg -n "TaskRepository" feature/map-runtime feature/map feature/tasks
- inspect touched runtime/adapter/formatter files directly
- run the focused tests the implementation reported
- ./gradlew enforceRules

Required output:
1) OVERALL: PASS or FAIL
2) Scope control: PASS/FAIL
3) Acceptance criteria table with evidence
4) Validation table
5) Out-of-scope changes
6) Architecture violations
7) Minimal fix list to reach PASS
8) Whether Phase 4 may start: YES/NO

Be strict.
```

---

## Phase 4 PASS/FAIL review brief

```text
You are reviewing a local XCPro implementation branch.

Do NOT modify source files.
Do NOT fetch, pull, push, open PRs, or use GitHub.

Goal:
Review Phase 4 of the glide-computer release-grade plan only.

Read first:
- AGENTS.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- relevant docs/POLAR files if touched

Check explicitly:
1) PASS/FAIL: IAS/TAS contract is explicit and consistent end-to-end
2) PASS/FAIL: glide states are explicit (valid / degraded / invalid) where intended
3) PASS/FAIL: General Polar controls exposed to users are truly authoritative or clearly unsupported
4) PASS/FAIL: manual polar changes demonstrably affect authoritative glide outputs
5) PASS/FAIL: no broad target-kind work started
6) PASS/FAIL: no UI business logic was added
7) PASS/FAIL: focused validation passed

Inspect or run:
- git status --short
- git diff --stat
- rg -n "StillAirSinkProvider|IAS|TAS|GlideState|degraded|threePointPolar|referenceWeight|userCoefficients|bugs|ballast" feature core docs
- inspect touched runtime/profile files directly
- run the focused tests the implementation reported
- ./gradlew enforceRules

Required output:
1) OVERALL: PASS or FAIL
2) Scope control: PASS/FAIL
3) Acceptance criteria table with evidence
4) Validation table
5) Out-of-scope changes
6) Architecture violations
7) Minimal fix list to reach PASS
8) Whether Phase 5 may start: YES/NO

Be strict.
```

---

## Phase 5 PASS/FAIL review brief

```text
You are reviewing a local XCPro implementation branch.

Do NOT modify source files.
Do NOT fetch, pull, push, open PRs, or use GitHub.

Goal:
Review the final Phase 5 state for release readiness.

Read first:
- AGENTS.md
- docs/ARCHITECTURE/CHANGE_PLAN_GLIDE_COMPUTER_RELEASE_GRADE_2026-03-27.md
- docs/ARCHITECTURE/GLIDE_COMPUTER_METRIC_CONTRACT_MATRIX_2026-03-27.md
- docs/ARCHITECTURE/PIPELINE.md
- docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md

Check explicitly:
1) PASS/FAIL: every shipped glide-computer card is implemented with explicit validity/source semantics or absent from production selection
2) PASS/FAIL: route authority remains task-owned and boundary-aware
3) PASS/FAIL: glide/navigation/task-performance runtime owners remain non-UI and upstream
4) PASS/FAIL: feature:map remains consumer only
5) PASS/FAIL: no TaskRepository runtime reads exist on the glide-computer path
6) PASS/FAIL: docs are final and coherent
7) PASS/FAIL: full local proof passed
8) PASS/FAIL: no out-of-scope target-kind/generalization work slipped in

Inspect or run:
- git status --short
- git diff --stat
- rg -n "WPT DIST|WPT BRG|WPT ETA|TASK SPD|TASK DIST|TASK REMAIN|START ALT|FINAL DIST" dfcards-library feature
- rg -n "TaskRepository" feature/map-runtime feature/map feature/tasks
- inspect touched docs and runtime files directly
- ./gradlew enforceRules
- ./gradlew testDebugUnitTest
- ./gradlew assembleDebug

Required output:
1) OVERALL: PASS or FAIL
2) Scope control: PASS/FAIL
3) Acceptance criteria table with evidence
4) Validation table
5) Out-of-scope changes
6) Architecture violations
7) Minimal fix list to reach PASS
8) Whether the branch is ready for push/PR: YES/NO

Be strict.
```
