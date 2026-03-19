# LiveFollow — What to do next (v3)

Date: 2026-03-18
Status: Updated after Phase 1 green run and review

## Active baseline

Use these as the active baseline for LiveFollow work:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v7.md`
- `docs/LIVEFOLLOW/PHASE1_REVIEW_CHECKLIST_v3.md`
- `docs/ARCHITECTURE/PIPELINE_LiveFollow_Addendum.md`

Do **not** keep implementing from:
- the old `livefollow.md`
- `XCPro_LiveFollow_Change_Plan_v4.md`
- older checklist versions as active merge gates

Treat older versions as historical context only.

---

## Repo-wide docs

Still do **not** update `AGENTS.md`, `ARCHITECTURE.md`, or `CODING_RULES.md` unless repo policy changes.

Do update:
- `PIPELINE.md` when wiring lands
- the active LiveFollow plan/checklist when implementation learnings reveal missing constraints

---

## Current implementation stance

### Phase 1
Phase 1 is green and should be reviewed/merged only if:
- scope stayed pure-domain only
- ambiguity precedence is correct
- transition coverage is explicit and deterministic
- required repo gates passed

Phase 1 includes only:
- pure domain models
- identity resolution
- source arbitration
- session state machine logic
- replay-safe pure policy
- deterministic tests

### Explicitly rejected target
Do **not** place implementation in `domain/livetask` unless that path is intentionally turned into a real Gradle module first.

### Phase 1 module target
Use the repo-native buildable module under `feature/livefollow`.

---

## Immediate next step

If Phase 1 review is clean:
1. Merge the Phase 1 PR
2. Pull latest `main`
3. Start Phase 2 on a new branch

If Phase 1 review is **not** clean:
1. Fix the specific review finding
2. Re-run repo gates
3. Re-check against `PHASE1_REVIEW_CHECKLIST_v3.md`
4. Merge only after the checklist passes

---

## Phase order from here

### Phase 2 — Contracts / repository seams
Next after Phase 1 merge:
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`
- backend/session adapter seams
- DI only where required for those seams

Still do **not** add UI/map behavior just because the domain core is ready.

### Phase 3 — Viewer/pilot route wiring
After contracts are stable:
- pilot controls
- follower route / entry handling
- thin map-render consumption

### Phase 4 — Hardening and doc sync
- `PIPELINE.md` update
- replay/privacy hardening
- PR quality rescore and final doc sync

---

## What to tell Codex now

For any remaining Phase 1 fixes or validation:
- use `feature/livefollow`
- do not create duplicate top-level domain modules
- do not ask the human to create Kotlin files manually
- keep scope to pure domain logic + tests only
- do not let ambiguity silently retain a prior live bind
- use repo-native namespace/package conventions
- run required repo gates at the end

For Phase 2:
- start from the merged Phase 1 baseline
- keep Phase 2 focused on contracts/seams
- do not jump straight to map/UI or backend feature richness

---

## Architectural mistake still to avoid

Do not let LiveFollow become a disguised patch to `feature:map`.

If the feature only works when ordinary OGN overlay prefs are enabled, or if it needs task manager/coordinator internals directly, the design is wrong.

