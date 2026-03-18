# LiveFollow — What to do next (v4)

Date: 2026-03-18
Status: Updated after Phase 2 merge and docs sync

## Active baseline

Use these as the active baseline for LiveFollow work:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v8.md`
- `docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md`
- `docs/LIVEFOLLOW/LiveFollow_Phase3_Codex_Prompts.md`
- `docs/ARCHITECTURE/PIPELINE_LiveFollow_Addendum.md`

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

---

## Immediate next step

Start Phase 3 on a new branch and do it in two passes:
1. seam/audit pass
2. implementation pass

Do **not** jump straight into code without the seam pass.

---

## Phase order from here

### Phase 3 — Viewer / pilot route wiring
Now:
- pilot controls
- follower route / entry handling
- ViewModel/state wiring
- thin map-render consumption
- exported task snapshot/use-case seam consumption only

Still do **not** add backend/network implementation or notification delivery.

### Phase 4 — Hardening and final doc sync
After Phase 3 merge:
- replay/privacy hardening
- `PIPELINE.md` update
- quality rescore / PR summary hygiene
- final doc sync
- instrumentation/lifecycle smoke checks if still needed

---

## Architectural mistake still to avoid

Do not let LiveFollow become a disguised patch to `feature:map`.

If the feature only works when ordinary OGN overlay prefs are enabled, or if it needs raw task manager/coordinator internals directly, the design is wrong.

Also avoid pushing backend/notification implementation into Phase 3 just because routes/screens now exist.

---

## What to tell Codex now

For Phase 3:
- start from the merged Phase 2 baseline
- use `feature/livefollow`
- keep Phase 3 focused on ViewModel/UI/route wiring only
- keep map/runtime render-only
- consume task snapshots from the exported task render/use-case seam only
- do not reintroduce task manager/coordinator escape hatches
- do not bind watch mode to ordinary OGN overlay or SCIA trail preferences
- keep replay side-effect free
- use the two-pass process in `LiveFollow_Phase3_Codex_Prompts.md`
