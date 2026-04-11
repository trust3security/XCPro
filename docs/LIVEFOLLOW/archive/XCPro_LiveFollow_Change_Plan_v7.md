# XCPro LiveFollow Change Plan v7

Date: 2026-03-18
Status: Active baseline after Phase 1 green run
Supersedes for active use: `XCPro_LiveFollow_Change_Plan_v4.md` and `XCPro_LiveFollow_Change_Plan_v5.md`

## Purpose

This version captures what was learned during actual Phase 1 implementation and review.
It is the clean baseline to use after the Phase 1 green run and before starting Phase 2.

It locks down:
- the approved implementation target
- the repo-native namespace/package rule
- the proven Phase 1 merge gates
- the key state-machine safety rule for ambiguity
- the handoff to Phase 2

---

## Active implementation baseline

Use these docs together:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v7.md`
- `docs/LIVEFOLLOW/PHASE1_REVIEW_CHECKLIST_v3.md`
- `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v3.md`
- `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/PIPELINE_LiveFollow_Addendum.md`

Treat older plan files as historical context only.

---

## Approved implementation target

### Phase 1 and onward module target
Use a repo-native buildable module under:

`feature/livefollow`

### Explicitly rejected target
Do **not** implement LiveFollow under:

`domain/livetask`

Reason:
- it is not a real Gradle module in the current repo
- it is not registered in `settings.gradle.kts`
- code placed there will not be built or tested unless separately promoted into a module

---

## Repo-native namespace / package rule

Use the repo's **existing namespace/package convention** already used by nearby feature modules.

Do **not**:
- invent a new placeholder package tree just because it looks cleaner
- create a duplicate package tree for the same responsibility
- churn working code solely to replace an already repo-native namespace

The correct rule is:
> use the repo-native namespace/package convention; do not invent a new generic or duplicate package tree.

---

## Human responsibility vs Codex responsibility

### Human responsibility
The human should:
1. work from the correct branch
2. provide the active docs/checklist to Codex
3. review scope and architecture drift
4. verify results and CI
5. merge only when checklist and plan gates are satisfied

### Codex responsibility
Codex should:
1. inspect nearby feature modules and follow repo-native patterns
2. create or extend `feature/livefollow` with the minimum required wiring
3. implement only the current phase scope
4. add deterministic tests
5. run verification
6. summarize files changed, results, and risks

### Important
The human should **not** be manually creating Kotlin source files or choosing package names for this slice.

---

## Mandatory repo-native rules

### 1. Minimal scaffold rule
Keep module wiring minimal.

Allowed:
- `settings.gradle.kts` registration if needed
- minimal `build.gradle.kts`
- minimal `AndroidManifest.xml` only if required by existing feature-module pattern

Forbidden:
- UI scaffolding
- navigation scaffolding
- DI scaffolding
- resources unless strictly required
- speculative future plumbing

### 2. Ownership rule
LiveFollow must not create duplicate ownership.

Forbidden in this work:
- map-owned session truth
- task-owned duplication
- repository duplication
- second ownship pipeline
- silent coordinator bypasses

### 3. Scope rule
Each phase must stay narrow. Do not pull later-phase work forward.

---

## Phase 1 final scope (implemented / merge gate)

Phase 1 is **pure domain logic only**.

Allowed:
1. typed aircraft identity model + resolution
2. live source arbitration (OGN vs DIRECT)
3. deterministic session state machine
4. replay-safe pure policy for side-effect blocking
5. deterministic unit tests

Forbidden:
- UI
- Compose
- map rendering
- `MapScreenViewModel` changes
- backend/networking
- Retrofit/WebSocket code
- FCM/notifications
- repository/server wiring
- task ownership changes
- sensor usage in ViewModels
- wall-clock logic in domain code

---

## Phase 1 proven logic gates

### Ambiguity precedence rule
If current identity input is `AMBIGUOUS`, the resulting session state must be `AMBIGUOUS`.

Ambiguity must take precedence over retained:
- `LIVE_OGN`
- `LIVE_DIRECT`
- `STALE`
- `OFFLINE`

A previously live bind must **not** silently survive when identity is now ambiguous.

### Time-base rule
Use **monotonic time only** for:
- freshness
- stale threshold
- offline threshold
- hysteresis / dwell

Do not use wall time in domain logic.

### Replay rule
Replay-related conditions must block live side effects through pure deterministic policy logic.

---

## Phase 1 required test proof

Minimum targeted coverage required:
- exact match
- mismatch
- ambiguous identity
- no-match case
- OGN selected when valid and fresh
- DIRECT selected when OGN stale or unresolved
- hysteresis prevents flapping
- `LIVE_OGN -> AMBIGUOUS`
- `LIVE_DIRECT -> AMBIGUOUS`
- `STALE -> LIVE_OGN`
- `STALE -> LIVE_DIRECT`
- `OFFLINE -> LIVE_OGN`
- `OFFLINE -> LIVE_DIRECT`
- hold live until stale threshold
- stale after stale threshold
- offline after offline threshold
- replay-safe behavior

If a requested case is already explicitly covered, point to the exact existing test rather than duplicating noise.

---

## Phase 1 completion criteria

Do not treat Phase 1 as complete until all are true:
- scope stayed pure-domain only
- review checklist passes
- ambiguity precedence is correct
- transition coverage is explicit and deterministic
- `./gradlew enforceRules` passes
- `./gradlew testDebugUnitTest` passes
- `./gradlew assembleDebug` passes

---

## Phase 2 direction

After Phase 1 merge, move to **contracts / repository seams**, not UI.

Phase 2 should define and wire the next safe boundaries, such as:
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`
- backend/session adapter seams
- DI only where required for those seams

Do not jump straight to map/UI just because Phase 1 is green.

---

## Architectural mistake still to avoid

Do not let LiveFollow become a disguised patch to `feature:map`.

If the feature only works when ordinary OGN overlay prefs are enabled, or if it requires task manager/coordinator internals directly, the design is wrong.


