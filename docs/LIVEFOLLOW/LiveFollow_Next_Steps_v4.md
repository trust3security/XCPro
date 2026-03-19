# LiveFollow - What to do next (v4)

Date: 2026-03-18
Status: Updated after Phase 3 route/runtime wiring landed on the implementation branch

## Active baseline

Use these as the active baseline for LiveFollow work:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v8.md`
- `docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md`
- `docs/LIVEFOLLOW/LiveFollow_Phase3_Codex_Prompts.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/PIPELINE_LiveFollow_Addendum.md`

Treat older phase plans/checklists as historical context only.

---

## What is already done

### Phase 1 - Pure domain logic
Completed and merged:
- typed identity resolution
- source arbitration
- deterministic session state machine
- replay-safe policy
- deterministic tests

### Phase 2 - Contracts / repository seams
Completed and merged:
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`
- session/watch seam models
- deterministic seam/repository tests
- docs sync for monotonic ms field names

### Phase 3 - Viewer / pilot route wiring
Implemented on the active branch:
- `LiveFollowPilotViewModel` / `LiveFollowPilotScreen`
- `LiveFollowWatchViewModel` / watch entry route wiring
- `livefollow/watch/{sessionId}` validation, join, and map handoff
- thin map-host runtime consumption through prepared render state only
- explicit unavailable adapters for backend session transport and direct watch transport
- `PIPELINE.md` runtime wiring update

---

## Immediate next step

Review the Phase 3 branch against `docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md`,
merge once the required repo gates are green, then move to Phase 4 hardening.

---

## Phase order from here

### Phase 4 - Hardening and final doc sync
Next:
- replay/privacy hardening
- quality rescore / PR summary hygiene
- final doc sync
- instrumentation/lifecycle smoke checks if still needed

---

## Architectural mistake still to avoid

Do not let LiveFollow become a disguised patch to `feature:map`.

If the feature only works when ordinary OGN overlay prefs are enabled, or if it needs raw task manager/coordinator internals directly, the design is wrong.

Also avoid pushing backend/notification implementation into follow-up phases just because routes/screens now exist.

---

## What to tell Codex now

For follow-up LiveFollow work:
- keep `feature/livefollow` as the owner for LiveFollow UI/state wiring
- keep map/runtime render-only
- consume task snapshots from the exported task render/use-case seam only
- do not reintroduce task manager/coordinator escape hatches
- do not bind watch mode to ordinary OGN overlay or SCIA trail preferences
- keep replay side-effect free
- keep backend/session transport and direct-watch transport explicit when unavailable
