# XCPro LiveFollow Change Plan v9

Date: 2026-03-19
Status: Active baseline after Phase 3 merge
Supersedes for active use: `XCPro_LiveFollow_Change_Plan_v8.md` for implementation sequencing

## Purpose

This version captures what was learned after Phase 3 was implemented and merged.
It is the execution plan for **Phase 4**.

It locks down:
- the exact scope of Phase 4 hardening
- what runtime polishing is allowed
- what replay/privacy work is required
- what tests and docs must be finished before calling the app-side LiveFollow slice hardened
- what remains explicitly out of scope until a later backend/transport track

---

## Active implementation baseline

Use these docs together:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v9.md`
- `docs/LIVEFOLLOW/PHASE4_REVIEW_CHECKLIST.md`
- `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v5.md`
- `docs/LIVEFOLLOW/LiveFollow_Phase4_Codex_Prompts.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Treat older phase plans/checklists as historical context only.
Treat `PIPELINE_LiveFollow_Addendum.md` as historical context now that `PIPELINE.md` contains the real runtime path.

---

## What is already established and must not regress

The following remain true:
- `feature:flight-runtime` remains ownship truth owner.
- `LiveOwnshipSnapshotSource` remains the exported ownship seam for LiveFollow.
- `LiveFollowSessionRepository` remains local session/backend mirror truth owner.
- `WatchTrafficRepository` remains watch arbitration truth owner.
- `feature:traffic` remains raw OGN/public traffic truth owner.
- task truth remains external and must still be consumed only through exported task render/use-case seams.
- map/runtime remains a render-only consumer.
- Phase 3 route/viewmodel/UI wiring exists and is the current user-facing entry point.
- unavailable adapters are explicit, visible, and not silent NoOps.

Phase 4 must **harden** this baseline, not redesign ownership.

---

## Human responsibility vs Codex responsibility

### Human responsibility
The human should:
1. work from a fresh Phase 4 branch based on merged `main`
2. provide the active Phase 4 docs/checklist to Codex
3. reject scope creep into backend/notification/product expansion
4. verify the diff and runtime behavior before merge
5. merge only when the review checklist passes

### Codex responsibility
Codex should:
1. audit the merged Phase 3 state first
2. propose the smallest Phase 4 hardening set that closes real risks
3. keep Phase 4 headless where possible and UI-only where necessary
4. add only the minimum tests and doc sync required
5. run verification and summarize results and residual risks

### Important
The human should **not** be manually creating Kotlin source files or deciding package names for this slice.

---

## Phase 4 scope

Phase 4 is **hardening and final app-side doc/runtime sync**.

### Allowed
Implement only:
1. unavailable-adapter UX hardening
2. route/lifecycle edge-case cleanup
3. replay/privacy verification and any minimal code fixes needed to preserve those guarantees
4. missing deterministic tests for risky runtime/UI behavior
5. connected/instrumentation tests only if Phase 3 behavior cannot be proven well enough with unit tests
6. final LiveFollow doc/status sync for the merged runtime path
7. small presentation/runtime polish only where it directly reduces ambiguity or failure confusion

### Explicitly out of scope
Do **not** add:
- backend/network implementation
- Retrofit/WebSocket implementations
- real production gateway/direct source transports
- FCM delivery implementation or background notification handlers
- share links
- leaderboard/history/replay expansion
- task ownership changes
- second ownship pipeline
- direct task manager/coordinator access from UI/VM/Composables
- map/runtime-owned session truth
- camera/gesture/animation policy changes
- durable raw-track/history storage

---

## Mandatory Phase 4 hardening goals

### 1. Unavailable transport behavior must stay explicit
If backend/direct transports remain unavailable in this phase:
- the pilot/watch UI must surface that honestly
- failure/unavailable states must be user-understandable
- there must be no silent fallback that looks like a working transport
- there must be no fake production backend behavior

### 2. Replay/privacy guarantees must be re-verified
Phase 4 must confirm:
- replay still blocks side effects
- UI wiring does not bypass replay blocking
- no raw-track persistence was introduced by accident
- no location logging/diagnostic output violates the repo’s privacy expectations

### 3. Lifecycle/route behavior must be safe
Phase 4 must confirm:
- invalid/missing session arguments fail safely
- watch session leave remains explicit, not tied to Composable disposal
- route re-entry / process-lifecycle edge cases do not silently mutate session truth

### 4. Map stays thin
Any remaining Phase 3/4 map interaction must remain:
- render-only
- task-snapshot-consumer only
- free of business logic, source arbitration, or session truth ownership

### 5. Docs must match reality
Before merge, docs must reflect:
- the real runtime path in `PIPELINE.md`
- the current active baseline doc set
- any clarified unavailable-adapter or replay/privacy rules

---

## Minimal Phase 4 implementation slices

### Slice A — Unavailable-adapter UX hardening
Allowed:
- clearer user-facing unavailable/error text
- clearer pilot/watch status-state mapping
- small presentation-state cleanup for blocked/unavailable conditions

Not allowed:
- adding actual network transports
- hiding unavailable state behind fake success

### Slice B — Lifecycle and route hardening
Allowed:
- route argument validation cleanup
- explicit stop/leave flow verification
- minor ViewModel/state fixes for re-entry or stale retained state

Not allowed:
- widening scope into new navigation surfaces or share-link plumbing

### Slice C — Replay/privacy hardening
Allowed:
- test additions
- narrow bug fixes if replay/privacy guarantees are not fully enforced
- doc wording updates that clarify privacy/replay expectations

Not allowed:
- new persistence behavior
- analytics/event logging that exposes sensitive movement data

### Slice D — Final doc and test sync
Allowed:
- `PIPELINE.md` corrections if Phase 3/4 clarified runtime ownership
- `LiveFollow_Next_Steps` status update
- checklist wording cleanup
- instrumentation/lifecycle smoke checks if actually required by the implemented runtime behavior

---

## Dependencies and module wiring

Phase 4 should prefer **no new broad dependencies**.

Allowed only if justified:
- minimal test dependencies required for lifecycle/UI proof
- narrow updates to existing feature/livefollow, feature/map, or app wiring if they close a concrete hardening issue

Avoid:
- new backend/transport dependencies
- new broad app-level wiring
- new task/map ownership dependencies when the existing seams already exist

---

## Required tests for Phase 4

### Deterministic tests
Minimum expected coverage:
- unavailable gateway/direct-source state surfaces correctly in pilot/watch UI state
- replay still blocks start/stop/join/leave side effects through the UI wiring
- watch leave remains explicit and is not triggered by disposal alone
- invalid/missing route args still fail safely
- no UI-local freshness or source-math drift was introduced

### Lifecycle / integration smoke checks
Add connected/instrumentation tests only if they prove behavior that unit tests cannot prove well enough, for example:
- route handoff across real nav lifecycle
- explicit stop/leave UI behavior where Compose runtime matters

If skipped, Codex must state why unit tests are sufficient.

---

## Verification required before merge

Required unless a repo-wide issue blocks them:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew connectedDebugAndroidTest` only if Phase 4 adds or changes behavior that truly requires device/runtime proof; if skipped, explain why

---

## Phase 4 merge gate

Phase 4 is ready to merge only if:
- unavailable runtime states are explicit and honest
- replay/privacy guarantees are re-proven
- no new backend/notification/product scope was added
- no ownership boundaries regressed
- docs match the merged runtime reality
- tests and verification are sufficient for the actual changes made

---

## What comes after Phase 4

After Phase 4, the app-side LiveFollow slice should be considered **hardened but transport-limited**.

Only then should you choose a next track, for example:
1. backend/session transport implementation
2. direct-watch transport implementation
3. notification/share-link delivery track

Do not mix those into Phase 4.
