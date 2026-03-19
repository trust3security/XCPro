# XCPro LiveFollow — Phase 1 Review Checklist (v3)

## Purpose

Use this checklist to validate the Phase 1 LiveFollow implementation before merge.

**Do NOT rely on green CI alone.**
Manual review is required to prevent architecture drift and logic bugs.

---

## ✅ REQUIRED — What MUST be present

Confirm that the implementation includes only the following kinds of additions:

- `feature/livefollow` module wiring with minimal scaffold
- Pure domain models
- Aircraft identity resolver
- Live source arbitrator (OGN vs DIRECT)
- LiveFollow session state machine
- Replay policy (pure logic, no side effects)
- Deterministic unit tests covering identity, arbitration, transitions, stale/offline, and replay-safe behavior

---

## ❌ FORBIDDEN — Reject if ANY of these appear

The following must NOT be introduced in Phase 1:

- Any map-related code
- Any UI code
- Jetpack Compose usage
- ViewModel logic (unless trivial test scaffolding)
- Backend/network code (Retrofit, WebSocket, API calls)
- FCM / notifications
- Repository or persistence wiring
- Task ownership changes
- Sensor pipeline logic
- Any second ownship pipeline
- Direct coordinator/manager access bypassing architecture
- A new generic or duplicate package/namespace tree inconsistent with repo-native conventions
- Any code placed outside the intended module without justification

---

## ⚠️ ARCHITECTURE VALIDATION

Confirm:

- Map remains a **consumer only**
- Task ownership remains **external**
- Domain logic is **pure and deterministic**
- No use of `System.currentTimeMillis()` in domain logic
- Uses injected/abstracted monotonic time where required
- No hidden side effects
- No duplication of existing pipelines
- `feature/livefollow` uses the repo-native namespace/package convention already used by nearby modules

---

## 🧪 TEST QUALITY CHECK

Ensure tests are:

- Deterministic (no randomness)
- Independent (no shared state pollution)
- Explicit about transition coverage, not just one happy path
- Cover ambiguous identity cases
- Cover source switching with hysteresis (no flapping)

Minimum targeted transition coverage expected:

- `LIVE_OGN -> AMBIGUOUS`
- `LIVE_DIRECT -> AMBIGUOUS`
- `STALE -> LIVE_OGN`
- `STALE -> LIVE_DIRECT`
- `OFFLINE -> LIVE_OGN`
- `OFFLINE -> LIVE_DIRECT`
- hold live until stale threshold
- stale after stale threshold
- offline after offline threshold

If some of these are already covered by an existing broader deterministic test, the reviewer should require the exact test name/path to be cited.

---

## 🔐 LOGIC SAFETY CHECK

Confirm:

- Ambiguous identity takes precedence over retained `LIVE_OGN`, `LIVE_DIRECT`, `STALE`, or `OFFLINE`
- A previously bound target does **not** remain silently live when identity is now ambiguous
- Replay-safe behavior is tested explicitly

---

## 📊 FINAL DECISION

### APPROVE if:
- All required items exist
- No forbidden items detected
- Architecture rules are respected
- Tests are meaningful and pass
- Ambiguity precedence is correct
- Transition matrix coverage is adequate

### REJECT if:
- Any forbidden items are present
- Architecture drift is introduced
- Tests are missing, weak, or non-deterministic
- Ambiguity can be masked by retained live/stale/offline state

---

## 🤖 Codex Validation Prompt

Use this to validate implementation:

> Review the current Phase 1 LiveFollow implementation against `PHASE1_REVIEW_CHECKLIST_v3.md`.
> Confirm:
> - All required components exist
> - No forbidden elements are present
> - Architecture constraints are respected
> - Tests are complete and deterministic
> - Ambiguity precedence is correct
> - Transition coverage matches the checklist
>
> Provide a pass/fail summary and list any violations.

