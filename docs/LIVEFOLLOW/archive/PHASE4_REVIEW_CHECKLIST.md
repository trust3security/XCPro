# XCPro LiveFollow — Phase 4 Review Checklist

## Purpose
Use this checklist to review **Phase 4** of LiveFollow before merge.

Phase 4 is **hardening and final app-side doc/runtime sync only**.
A green CI run is necessary, but **not sufficient**. Manual review is required.

---

## Phase 4 intent
Phase 4 should make the merged LiveFollow app-side slice safer and clearer without expanding scope.

It should tighten:
- unavailable-adapter UX
- replay/privacy guarantees
- route/lifecycle edge cases
- risky test gaps
- final docs/runtime sync

It must **not** jump ahead into backend implementation, notification delivery, or feature expansion.

---

## ✅ REQUIRED — what must be present

Confirm the implementation includes only the following kinds of changes:

### Hardening behavior
- unavailable gateway/direct-source behavior is explicit and honest
- replay-blocked states are still surfaced correctly in pilot/watch flows
- invalid/missing route/session args fail safely
- explicit stop/leave behavior remains explicit
- no hidden production-looking fallback masks an unavailable transport

### Ownership preservation
Confirm these owners remain unchanged:
- `feature:flight-runtime` remains ownship truth owner
- `LiveFollowSessionRepository` remains local session/backend mirror truth owner
- `WatchTrafficRepository` remains watch arbitration truth owner
- `feature:traffic` remains raw OGN/public traffic truth owner
- task truth/render remains external and exported
- map/runtime remains a render-only consumer

### Time / replay / privacy
- monotonic time usage remains consistent
- no wall-clock decision drift is introduced
- replay still blocks side effects correctly
- no raw-track persistence/logging was added
- no privacy-regressing diagnostics were introduced

### Tests
There should be meaningful tests for:
- unavailable adapter UI-state behavior
- replay-blocked pilot/watch actions
- explicit stop/leave behavior
- invalid/missing route args
- any lifecycle/runtime bug fixed in this phase
- connected/instrumentation checks only if needed to prove real runtime behavior

### Docs
- `PIPELINE.md` matches the merged runtime path
- `LiveFollow_Next_Steps` status reflects the current phase correctly
- any clarified unavailable/replay/privacy wording is synced

---

## ❌ FORBIDDEN — reject if any of these appear

Phase 4 must NOT introduce:

### Backend / transport implementation
- Retrofit implementation
- WebSocket implementation
- real backend session client implementation
- real direct-watch transport implementation
- FCM delivery implementation
- background notification handler work

### Product/feature expansion
- share links
- leaderboard/history features
- new task-editing features
- public replay/raw-track features
- speculative future plumbing unrelated to hardening

### Architecture drift
- task ownership changes
- second ownship pipeline
- direct sensor reads from LiveFollow UI/VM
- direct coordinator / manager bypasses
- ordinary OGN overlay/facade state reused as watch truth
- map/runtime becoming the LiveFollow owner
- UI/VM-local source arbitration or freshness math

### Repo hygiene issues
- committed build artifacts
- generated files checked in
- unrelated docs or backend/server work mixed into the PR

---

## ⚠️ ARCHITECTURE VALIDATION

Confirm all of the following:

### Unavailable adapters
- unavailable adapters are still explicit, visible, and honestly named
- no silent NoOps were introduced
- no fake production behavior is being presented as real transport support

### Replay / privacy
- replay blocking still gates side-effecting actions
- UI wiring does not bypass replay rules
- no new storage/logging leaks position/track data by accident

### Lifecycle / route handling
- route failures are safe and explicit
- leaving watch remains explicit, not tied to disposal alone
- lifecycle/re-entry handling does not create hidden state mutations

### Map/task consumption
- map remains a prepared-state consumer only
- task consumption remains on the exported seam only
- no task attach/bind shortcut was added in the UI flow

---

## 🧪 TEST QUALITY CHECK

Review tests for quality, not just existence.

Confirm tests are:
- deterministic
- independent
- meaningful for risky behavior
- not duplicate noise
- using fakes/test doubles instead of real backend/network dependencies where possible

Specific coverage to look for:
- unavailable adapter state surfaces clearly
- replay blocks start/stop/join/leave where applicable
- invalid args or unavailable state fail safely
- explicit stop/leave behavior is preserved
- if connected/instrumentation tests exist, they prove real runtime behavior rather than superficial rendering

---

## 📄 DOC SYNC CHECK

Before merge, confirm whether any docs need follow-up updates.

Typical Phase 4 doc sync items:
- unavailable-adapter wording
- replay/privacy wording
- `PIPELINE.md` runtime ownership wording
- `LiveFollow_Next_Steps` status update after merge

Doc wording updates should be recorded, but they do not automatically block merge unless they hide a real architecture mismatch.

---

## 📊 FINAL DECISION

### APPROVE if:
- Phase 4 hardens the existing app-side slice without expanding scope
- no forbidden items were introduced
- ownership boundaries remain clean
- tests are meaningful and sufficient for the actual changes made
- verification is green
- docs match runtime reality

### REJECT if:
- Phase 4 drifted into backend/notification/product expansion
- unavailable transports are hidden behind fake success
- replay/privacy guarantees regressed
- map/runtime or task ownership boundaries regressed
- tests are weak, noisy, or missing on risky paths

---

## 🤖 Codex validation prompt

Use this prompt to audit the current implementation:

> Review the current LiveFollow Phase 4 implementation against `docs/LIVEFOLLOW/PHASE4_REVIEW_CHECKLIST.md`.
> Confirm:
> - all REQUIRED items are present
> - no FORBIDDEN items are present
> - architecture ownership constraints are respected
> - replay/privacy guarantees are still intact
> - tests are meaningful and deterministic
> - any doc wording that should be updated after Phase 4
>
> Provide:
> 1. PASS or FAIL
> 2. exact violations or gaps
> 3. whether code changes are required before merge
> 4. whether any docs should be updated after merge
