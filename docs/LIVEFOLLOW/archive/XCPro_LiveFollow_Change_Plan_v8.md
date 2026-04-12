# XCPro LiveFollow Change Plan v8

Date: 2026-03-18
Status: Active baseline after Phase 2 merge
Supersedes for active use: `XCPro_LiveFollow_Change_Plan_v7.md` for implementation sequencing

## Purpose

This version captures what was learned after Phase 1 and Phase 2 were implemented and merged.
It is the execution plan for **Phase 3**.

It locks down:
- the approved implementation target
- the proven Phase 1/2 ownership boundaries
- the exact scope of Phase 3
- what UI/route wiring is allowed
- what map wiring is allowed
- what remains explicitly out of scope until Phase 4 or later

---

## Active implementation baseline

Use these docs together:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v8.md`
- `docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md`
- `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v4.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/PIPELINE_LiveFollow_Addendum.md`

Treat older phase plans and checklists as historical context only.

---

## Approved implementation target

### Phase 3 module target
Continue implementing in the repo-native buildable module under:

`feature/livefollow`

### Still explicitly rejected
Do **not** move implementation under:

`domain/livetask`

Do **not** turn `feature:map` into the logic owner for LiveFollow.

---

## Proven ownership baseline from earlier phases

The following are already established and must remain true:

- `feature:flight-runtime` remains ownship truth owner.
- `LiveOwnshipSnapshotSource` exports ownship snapshot data for LiveFollow.
- `LiveFollowSessionRepository` owns local session/backend mirror truth.
- `WatchTrafficRepository` owns watch arbitration truth.
- `feature:traffic` remains raw OGN/public traffic truth owner.
- task truth remains owned by existing task seams and exported render/use-case snapshots.
- map/runtime remains a render-only consumer.

Phase 3 must **consume** these seams, not recreate them.

---

## Human responsibility vs Codex responsibility

### Human responsibility
The human should:
1. work from the correct branch
2. provide the active Phase 3 docs/checklist to Codex
3. review scope and architecture drift
4. verify results and CI
5. merge only when the review checklist passes

### Codex responsibility
Codex should:
1. inspect nearby repo-native feature/UI modules and existing app navigation patterns
2. keep all new work scoped to Phase 3 only
3. add minimal ViewModel/UI/route wiring on top of the merged Phase 2 seams
4. add deterministic tests
5. run verification
6. summarize files changed, results, and risks

### Important
The human should **not** be manually creating Kotlin source files or deciding package names for this slice.

### Doc sync correction
If Phase 3 lands real route/runtime wiring, update `docs/ARCHITECTURE/PIPELINE.md` in the same PR.
Do not rely on the addendum alone once the path is buildable in the app.

---

## Phase 3 scope

Phase 3 is **viewer / pilot route wiring**.

### Allowed
Implement only:
1. `feature/livefollow` ViewModel/state holders needed to drive pilot and follower flows
2. pilot controls for starting/stopping LiveFollow using the existing session repository/use-case boundaries
3. follower route / entry handling using existing session/watch state
4. render-ready UI state mapping for source, stale/offline, ambiguity, and session lifecycle
5. thin map-render consumption only
6. task render snapshot consumption through the existing exported task seam only
7. route/deep-link/session-argument handling needed to open the follower flow
8. deterministic VM/UI tests and Compose/instrumentation tests only where actually needed

### Explicitly out of scope
Do **not** add:
- backend/network implementation
- Retrofit/WebSocket implementations
- FCM delivery implementation or background notification handlers
- new repository owners
- task ownership changes
- second ownship pipeline
- direct task manager/coordinator access from UI
- business math in UI
- map/runtime-owned session truth
- ordinary OGN overlay preference semantics as watch truth
- leaderboard / comparison extras
- public share links
- durable raw-track features

---

## Mandatory repo-native rules

### 1. Keep map render-only
Map/runtime may consume prepared render state.
It must not own:
- session truth
- source arbitration
- identity resolution
- stale/offline derivation
- task truth

### 2. Keep task truth external
UI/ViewModel may consume exported task snapshots/render snapshots only.
Forbidden:
- direct task manager calls from screens or Composables
- new coordinator escape hatches
- UI-local task reconstruction

### 3. Keep watch mode independent of ordinary OGN overlay state
A watch session must not disappear just because:
- public OGN overlay is off
- SCIA/show-trail prefs are off
- selected OGN aircraft state changes

### 4. UI renders prepared state only
The UI layer may:
- render source badges
- render stale/offline/degraded state
- route user intents
- expose pilot/follower controls

The UI layer may **not**:
- perform identity matching
- perform source arbitration
- implement freshness logic
- perform replay side-effect blocking
- attach tasks through ad hoc matching logic

### 5. Minimal wiring rule
Add only the minimum app/navigation/feature wiring required to make the flows usable.

Allowed:
- navigation/route registration
- route argument parsing
- ViewModel wiring
- minimal DI additions required to resolve existing repositories/use cases in UI
- minimal map-render adapter/consumer wiring

Forbidden:
- speculative future plumbing
- unrelated app-level wiring
- new background services
- backend transport scaffolding

---

## Phase 3 implementation slices

### Slice A â€” ViewModel and UI-state mapping
Add:
- pilot-facing LiveFollow state mapping
- follower/watch state mapping
- explicit source/degraded/session UI state models
- replay-safe action enable/disable mapping using existing repository outputs

Required behavior:
- ambiguity is rendered honestly
- stale/offline states are explicit and user-visible
- no fabricated precision when source is degraded
- controls are disabled when replay blocks side effects

### Slice B â€” Pilot controls
Add pilot entry points to:
- start a live session
- stop the current session
- view current local session status

Pilot controls must route through existing Phase 2 owners.
No direct repository mutation from Composables.

### Slice C â€” Follower route / entry handling
Add route/entry handling for:
- open a watch session from explicit session args / route input
- render active watch state
- surface waiting/ambiguous/stale/offline/stop/error state clearly

Allowed in this phase:
- route/deep-link/session-argument handling if needed for entry

Not allowed in this phase:
- full FCM delivery implementation
- backend notification plumbing

### Slice D â€” Thin map-render consumption
If map integration is needed in Phase 3, it must be thin:
- consume prepared render state only
- consume exported task render snapshots only
- render watch target/task geometry without owning logic

If a thin adapter is sufficient, prefer that over broad map wiring.

---

## Dependencies and module wiring

Phase 3 may add only the minimal dependencies required to consume:
- existing LiveFollow repositories/use cases
- existing task render snapshot/use-case seam
- minimal app/navigation route registration
- minimal map/runtime render-consumer seam if needed

Avoid broad dependencies on:
- `:feature:map` unless there is no thinner render-consumer path
- `:feature:tasks` if a task-facing exported snapshot/use-case seam already exists

If a new dependency is added, it must be justified in the PR summary.

---

## Required tests for Phase 3

### ViewModel / UI-state tests
Minimum expected coverage:
- pilot controls enabled/disabled correctly based on session/replay state
- follower UI state renders WAITING / AMBIGUOUS / LIVE_OGN / LIVE_DIRECT / STALE / OFFLINE / STOPPED correctly
- ambiguity/degraded state is surfaced honestly
- no business math duplicated in the ViewModel

### Route / entry tests
Minimum expected coverage:
- follower route opens using session/entry arguments correctly
- invalid/missing route args fail safely
- route entry does not bypass repository/session truth

### Map/task-consumer tests
Minimum expected coverage:
- task render comes from exported task snapshot/use-case seam only
- ambiguous target does not attach a task
- ordinary OGN overlay off does not kill an active watch session UI state
- map/runtime consumes render state only

### Replay / side-effect tests
Minimum expected coverage:
- replay blocks pilot start/stop actions as appropriate
- replay does not accidentally allow session side effects through UI wiring

### Instrumentation / Compose tests
Add only where they actually prove route wiring or rendering behavior that unit tests cannot cover.
Do not add noisy instrumentation just for coverage theater.

---

## Verification required

Codex must run and report:

- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Also run this when the Phase 3 diff introduces meaningful UI/route behavior that needs it:

- `./gradlew connectedDebugAndroidTest`

If connected tests are not run, Codex must say why.

---

## Phase 3 merge gates

Do not treat Phase 3 as complete until all are true:
- pilot and follower flows are reachable in debug
- UI renders only prepared state
- no business math is in UI/VM beyond state mapping
- watch mode is still independent of ordinary OGN overlay prefs
- task rendering consumes exported task snapshots only
- replay side effects remain blocked
- required repo gates pass
- Phase 3 review checklist passes

---

## Known risks to watch in review

- LiveFollow becomes a disguised patch to `feature:map`
- Composables or ViewModels reach directly into task manager/coordinator internals
- watch mode accidentally depends on public OGN overlay prefs again
- UI invents its own stale/offline/arbitration logic
- route/entry wiring grows into backend/FCM implementation prematurely
- map dependencies become broader than render-only needs

---

## Handoff after Phase 3

After Phase 3 merge, move to **Phase 4** only:
- hardening
- replay/privacy verification
- final doc sync
- quality rescore / PR summary hygiene

Do not treat Phase 3 as the place to sneak backend implementation in.

