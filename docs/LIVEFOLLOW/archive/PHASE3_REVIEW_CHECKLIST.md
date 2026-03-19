# XCPro LiveFollow — Phase 3 Review Checklist

## Purpose
Use this checklist to review **Phase 3** of LiveFollow before merge.

Phase 3 is **viewer / pilot route wiring only**.
A green CI run is necessary, but **not sufficient**. Manual review is required.

---

## Phase 3 intent
Phase 3 should make LiveFollow **usable in the app** without breaking the ownership boundaries already established in Phases 1 and 2.

It should add:
- ViewModel/state wiring
- pilot controls
- follower route / entry handling
- thin map-render consumption

It must **not** jump ahead into backend implementation, notification delivery, or broad map/task ownership changes.

---

## ✅ REQUIRED — what must be present

Confirm the implementation includes only the following kinds of changes:

### Module / wiring
- `feature/livefollow` remains the implementation target
- wiring is minimal and repo-native
- any new app/navigation wiring is limited to what Phase 3 actually needs
- any new dependency on task/map runtime seams is narrow and justified

### ViewModel / UI state
- LiveFollow ViewModel(s) or equivalent state holders exist
- pilot-facing UI state is derived from existing repository/use-case outputs
- follower/watch UI state is derived from existing repository/use-case outputs
- no domain logic is reimplemented in UI/VM
- replay-blocked states are surfaced honestly

### User flows
- pilot controls to start/stop LiveFollow are wired through the existing session seam
- follower route / entry handling exists
- follower flow can render active/degraded/offline/stopped states
- invalid or missing route/session arguments fail safely

### Thin map/task consumption
- map/runtime consumes prepared render state only
- task geometry/render state comes from exported task render/use-case seams only
- no direct task manager/coordinator calls are introduced in UI/VM/Composables

### Tests
There should be meaningful tests for:
- pilot controls enabled/disabled state
- follower UI state mapping for WAITING / AMBIGUOUS / LIVE_OGN / LIVE_DIRECT / STALE / OFFLINE / STOPPED
- route/entry handling
- ambiguous identity does not attach a task
- public OGN overlay off does not kill active watch session UI state
- replay-safe action blocking through UI wiring

---

## ❌ FORBIDDEN — reject if any of these appear

Phase 3 must NOT introduce:

### Backend / transport implementation
- Retrofit implementation
- WebSocket implementation
- backend session client implementation
- direct-watch network implementation
- FCM delivery implementation
- background notification handler work

### Architecture drift
- task ownership changes
- second ownship pipeline
- direct sensor reads from LiveFollow UI/VM
- direct coordinator / manager bypasses
- watch mode built on ordinary OGN overlay / SCIA selected-aircraft preference state
- UI/VM-local source arbitration or freshness math
- duplicate state/identity/task models that should reuse existing seams

### Map / task drift
- `MapScreenViewModel` becoming the LiveFollow owner
- map overlay logic owning session/watch truth
- direct task attachment through ad hoc matching in UI
- broad task/map dependency additions when a thinner seam exists

### Repo hygiene issues
- committed build artifacts
- generated files checked in
- unrelated docs or backend/server work mixed into the PR

---

## ⚠️ ARCHITECTURE VALIDATION

Confirm all of the following:

### ViewModel placement
- UI/VM live in repo-native feature locations
- ViewModel consumes existing Phase 2 seams instead of duplicating them
- state mapping remains a presentation concern only

### Pilot flow
- pilot start/stop actions route through existing LiveFollow session owner
- replay-blocking behavior remains enforced, not bypassed by UI state
- controls do not mutate repositories directly from Composables

### Follower/watch flow
- follower route uses session/watch truth from the existing repositories
- watch UI remains independent of ordinary OGN overlay prefs
- degraded states are rendered honestly
- stale/offline source state comes from existing prepared state, not UI math

### Map/task consumption
- task render comes via exported task snapshot/use-case seam only
- map/runtime is a render-only consumer
- ambiguous identity does not cause silent task bind in the UI flow

### Naming / units
- existing monotonic ms usage is preserved
- field names remain explicit and repo-native
- no new time-base confusion is introduced in UI/VM code

---

## 🧪 TEST QUALITY CHECK

Review tests for quality, not just existence.

Confirm tests are:
- deterministic
- independent
- meaningful for risky UI/route behavior
- not duplicate noise
- using fakes/test doubles rather than real backend/network dependencies where possible

Specific coverage to look for:
- pilot control state in replay / non-replay modes
- follower route opens correct watch flow from entry args
- invalid route args fail safely
- WAITING / AMBIGUOUS / STALE / OFFLINE / STOPPED UI state mapping
- ordinary OGN overlay off does not kill active watch session UI state
- ambiguous target does not attach task
- if Compose/instrumentation tests exist, they prove real Phase 3 behavior rather than superficial rendering

---

## 📄 DOC SYNC CHECK

Before merge, confirm whether any docs need follow-up updates.

Typical Phase 3 doc sync items:
- route/entry wording if implementation clarified it
- map render-consumer ownership wording
- repo-native dependency notes if Phase 3 added narrow task/map seams
- Next Steps doc status update after merge

Doc wording updates should be recorded, but they do not automatically block merge unless they hide a real architecture mismatch.

---

## 📊 FINAL DECISION

### APPROVE if:
- all required Phase 3 UI/route elements are present
- no forbidden items were introduced
- ownership boundaries remain clean
- tests are meaningful and deterministic
- verification is green

### REJECT if:
- Phase 3 drifted into backend/notification implementation
- ordinary OGN overlay/facade state was reused as watch truth
- UI/VM owns business logic that belongs to existing seams
- task render/use-case seams were bypassed
- map/runtime became the LiveFollow owner
- tests are weak, noisy, or missing on risky paths

---

## 🤖 Codex validation prompt

Use this prompt to audit the current implementation:

> Review the current LiveFollow Phase 3 implementation against `docs/LIVEFOLLOW/PHASE3_REVIEW_CHECKLIST.md`.
> Confirm:
> - all REQUIRED items are present
> - no FORBIDDEN items are present
> - architecture ownership constraints are respected
> - tests are meaningful and deterministic
> - any doc wording that should be updated after Phase 3
>
> Provide:
> 1. PASS or FAIL
> 2. exact violations or gaps
> 3. whether code changes are required before merge
> 4. whether any docs should be updated after merge
