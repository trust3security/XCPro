# LiveFollow — What to do next (v5)

Date: 2026-03-19
Status: Updated after Phase 3 merge

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

---

## Immediate next step

Start Phase 4 on a new branch and do it in two passes:
1. seam/audit pass
2. implementation pass

Do **not** jump straight into code without the seam pass.

---

## Phase order from here

### Phase 4 — Hardening and final app-side doc/runtime sync
Now:
- unavailable-adapter UX hardening
- replay/privacy verification
- route/lifecycle edge-case cleanup
- risky test gap closure
- final docs/runtime sync
- connected/instrumentation tests only if truly needed

Still do **not** add backend/network implementation or notification delivery.

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
- keep replay/privacy guarantees provable
- keep map/runtime render-only
- keep task truth external
- do the two-pass process in `LiveFollow_Phase4_Codex_Prompts.md`
