\# XCPro LiveFollow — Phase 1 Review Checklist



\## Purpose

This checklist is used to validate the Phase 1 implementation of LiveFollow before merging.



\*\*Do NOT rely on green CI alone.\*\*  

Manual review is required to prevent architecture drift.



\---



\## ✅ REQUIRED — What MUST be present



Confirm that the implementation includes ONLY the following:



\- `feature/livefollow` module wiring (buildable module)

\- Pure domain models (no Android/UI dependencies)

\- Aircraft identity resolver

\- Live source arbitrator (OGN vs DIRECT)

\- LiveFollow session state machine

\- Replay policy (pure logic, no side effects)

\- Deterministic unit tests covering:

&#x20; - identity resolution

&#x20; - arbitration decisions

&#x20; - state transitions

&#x20; - stale/offline behavior

&#x20; - replay-safe behavior



\---



\## ❌ FORBIDDEN — Reject if ANY of these appear



The following must NOT be introduced in Phase 1:



\- Any map-related code

\- Any UI code

\- Jetpack Compose usage

\- ViewModel logic (unless trivial test scaffolding)

\- Backend/network code (Retrofit, WebSocket, API calls)

\- FCM / notifications

\- Repository or persistence wiring

\- Task ownership changes

\- Sensor pipeline logic

\- Any second ownship pipeline

\- Direct coordinator/manager access bypassing architecture

\- Package names using:

&#x20; - `com.example.\*`

\- Any code placed outside the intended module without justification



\---



\## ⚠️ ARCHITECTURE VALIDATION



Confirm:



\- Map remains a \*\*consumer only\*\*

\- Task ownership remains \*\*external\*\*

\- Domain logic is \*\*pure and deterministic\*\*

\- No use of `System.currentTimeMillis()` in domain logic

\- Uses injected/abstracted time (monotonic where required)

\- No hidden side effects

\- No duplication of existing pipelines



\---



\## 🧪 TEST QUALITY CHECK



Ensure tests are:



\- Deterministic (no randomness)

\- Independent (no shared state pollution)

\- Cover all state transitions

\- Cover ambiguous identity cases

\- Cover source switching with hysteresis (no flapping)



\---



\## 📊 FINAL DECISION



\### APPROVE if:

\- All required items exist

\- No forbidden items detected

\- Architecture rules are respected

\- Tests are meaningful and pass



\### REJECT if:

\- Any forbidden items are present

\- Architecture drift is introduced

\- Tests are missing, weak, or non-deterministic



\---



\## 🤖 Codex Validation Prompt



Use this to validate implementation:



> Review the current Phase 1 LiveFollow implementation against `PHASE1\_REVIEW\_CHECKLIST.md`.  

> Confirm:

> - All required components exist  

> - No forbidden elements are present  

> - Architecture constraints are respected  

> - Tests are complete and deterministic  

>  

> Provide a pass/fail summary and list any violations.



\---

