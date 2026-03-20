# LiveFollow App/Server Contract Checklist v4

## Purpose

Use this checklist to validate the **extracted current deployed API contract** before starting
XCPro transport adapter implementation.

This checklist assumes:
- the current deployed simple API is authoritative for current work
- the older nested `/api/v1/client/sessions/...` API is future design only
- app-side LiveFollow Phases 1–4 are already merged
- the real server code has now been inspected

---

## ✅ REQUIRED

### Current deployed API is explicit
Confirm the docs clearly define:
- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

### Current request/response bodies are frozen
Confirm the docs define exact request/response bodies for:
- session start
- position upload
- task upsert
- session end
- live read by session id
- live read by share code
- root health

### Current auth model is explicit
Confirm the docs clearly define:
- which endpoints require `X-Session-Token`
- which endpoints are public
- that `session/start` returns:
  - `session_id`
  - `share_code`
  - `status`
  - `write_token`

### Lifecycle semantics are explicit
Confirm the docs clearly define:
- active / stale / ended behavior
- stale threshold
- ended-session write rejection
- reactivation after a later accepted position
- whether task upsert affects stale/active status

### Ownership split is explicit
Confirm the docs distinguish:

**Server-owned**
- `session_id`
- `share_code`
- `write_token`
- session lifecycle/status
- authorization outcomes
- server validation/dedupe decisions
- server response payload fields

**Client transport-local**
- `transportAvailability`
- `lastError`
- retry/backoff/reconnect state
- local pending-command state
- local monotonic freshness derivation
- direct-vs-OGN arbitration

### Time semantics are explicit
Confirm the docs say:
- XCPro keeps `fixMonoMs` internally
- the wire/server contract uses wall-time / ordering semantics
- device monotonic time is not cross-system truth

### Telemetry upload rules are explicit
Confirm the contract defines:
- all currently required telemetry fields
- which app-side nullable fields must be gated before upload
- speed-unit assumption / confirmation
- ordering semantics
- dedupe behavior
- jump validation behavior

### Follow/read integration rules are explicit
Confirm the docs explain:
- whether current first XCPro watch integration should use `share_code`
- whether `session_id` read is still needed for client UX
- that current live-read shape is a degraded polling transport relative to the app’s richer direct-watch seam
- whether missing read fields (typed identity, vertical speed, monotonic age) are acceptable for first integration

### Error behavior is explicit
Confirm the docs clearly state:
- current FastAPI-style `detail` behavior
- current 401 / 403 / 404 / 409 / 422 patterns
- whether a server hardening step for machine-readable error codes is required before XCPro transport work

### Adapter decisions are explicit
Before approving transport work, confirm the docs explicitly decide:
- where `write_token` is stored
- where `share_code` is stored
- whether pilot UI surfaces `share_code`
- how `join/leave` are represented when the current server has no explicit watch-membership endpoints

---

## ❌ FORBIDDEN

Reject if any of these happen:

- mixing the current deployed API and the older nested target API as if both are current
- client code planned against `/api/v1/client/sessions/...` for current work
- server DTOs defined one-to-one with client-local fields like `transportAvailability` or `lastError`
- cross-device monotonic timestamps treated as shared truth
- app-side repository/state-machine ownership moved into the server contract
- map/UI ownership concerns mixed into the server wire contract
- future refactor ideas presented as deployed reality
- XCPro transport coding started before upload gating / token storage / watch key decisions are frozen

---

## ⚠️ VALIDATION QUESTIONS

Before approving the contract, confirm:

1. Is the first XCPro watcher integration meant to use `share_code` as the external/public key?
2. Is `GET /api/v1/live/{session_id}` still intentionally public and intentionally used by the client?
3. Is the current server read payload rich enough for first watch integration, or does Phase 5B need a small server gap fill first?
4. Are machine-readable error codes needed before adapter coding starts?
5. Are altitude/speed/heading/timestamp upload requirements compatible with XCPro ownship reality without unsafe guessing?

---

## 📊 FINAL DECISION

### APPROVE if:
- all required contract details are frozen
- no forbidden mixing or ownership drift exists
- remaining adapter decisions are explicit
- it is clear whether server hardening is required before XCPro integration

### REJECT if:
- current bodies are still only partially known
- token/share-code storage and watch-key decisions are unresolved
- telemetry upload rules remain ambiguous
- the current server read payload limitations are being ignored
- implementation is about to start on assumptions rather than a frozen contract

---

## 🤖 Codex validation prompt

Use this prompt to audit the current extracted contract:

> Review the current extracted LiveFollow deployed API contract against `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`.
> Confirm:
> - all REQUIRED contract items are frozen
> - no FORBIDDEN contract mistakes are present
> - the remaining adapter decisions are explicit
> - whether a small server hardening slice is required before XCPro transport adapter implementation
>
> Provide:
> 1. PASS or FAIL
> 2. exact remaining gaps
> 3. whether server hardening is required first
> 4. whether XCPro client transport integration can safely begin
