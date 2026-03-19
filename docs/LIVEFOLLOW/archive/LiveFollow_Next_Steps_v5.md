# LiveFollow — What to do next (v5)

Date: 2026-03-19
Status: Updated for Phase 4 hardening implementation

## Active baseline

Use these as the active baseline for LiveFollow work:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v9.md`
- `docs/LIVEFOLLOW/PHASE4_REVIEW_CHECKLIST.md`
- `docs/LIVEFOLLOW/LiveFollow_Phase4_Codex_Prompts.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Treat older phase plans/checklists as historical context only.

---

## What is already done

### Phase 1 — Pure domain logic
Completed and merged:
- typed identity resolution
- source arbitration
- deterministic session state machine
- replay-safe policy
- deterministic tests

### Phase 2 — Contracts / repository seams
Completed and merged:
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`
- session/watch seam models
- deterministic seam/repository tests
- docs sync for monotonic ms field names

### Phase 3 — Viewer / pilot route wiring
Completed and merged:
- pilot start/stop/status flow
- follower route / entry handling
- pilot/watch ViewModels and UI state
- thin map-host render consumption
- explicit unavailable adapters
- runtime path documented in `PIPELINE.md`

### Phase 4 - Hardening and final app-side doc/runtime sync
Implemented on this branch:
- explicit owner-provided session transport availability from the session boundary
- explicit owner-provided direct-watch transport availability from the watch/direct boundary
- centralized task-attach unavailable copy owned by `feature:livefollow`
- pilot/watch presentation hardening for transport-limited builds
- route/ViewModel hardening for invalid args, same-session re-entry, replay-blocked join/leave, and explicit leave-only behavior
- deterministic JVM tests for pilot/watch UI state and ViewModel wiring
- doc/runtime sync for the transport-limited runtime path

---

## Immediate next step

Run the required repo gates for this Phase 4 branch, review against `PHASE4_REVIEW_CHECKLIST.md`, and merge only if the hardening-only scope remains intact.

---

## Phase order from here

### Phase 4 — Hardening and final app-side doc/runtime sync
Completed on this branch:
- unavailable-adapter UX hardening with explicit owner-provided transport availability
- replay/privacy verification through repository and ViewModel wiring
- route/lifecycle edge-case cleanup
- risky deterministic JVM test gap closure
- final docs/runtime sync

Connected/instrumentation tests remain unnecessary unless a device-only behavior appears that unit tests cannot prove.

### Next track after Phase 4
Only after Phase 4 is merged should you choose a new track, for example:
- backend/session transport
- direct-watch transport
- notifications/share-link delivery

Do not mix those into Phase 4.

---

## Architectural mistake still to avoid

Do not let hardening become a disguised backend or product-expansion phase.

If unavailable adapters are hidden, if replay/privacy guarantees become fuzzy, or if map/task ownership shifts during “cleanup,” the phase is going wrong.

Also avoid adding device/instrumentation tests that do not prove a real Phase 4 risk.

---

## What to tell Codex now

For Phase 4:
- start from the merged Phase 3 baseline
- keep the work minimal and hardening-focused
- keep unavailable transports explicit
- keep transport availability owner-provided, not inferred from command failure
- keep replay/privacy guarantees provable
- keep map/runtime render-only
- keep task truth external
- do the two-pass process in `LiveFollow_Phase4_Codex_Prompts.md`
