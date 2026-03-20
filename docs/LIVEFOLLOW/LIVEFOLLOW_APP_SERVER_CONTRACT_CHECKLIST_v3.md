# LiveFollow App/Server Contract Checklist v3

## Purpose

Use this checklist to validate the **current deployed API contract** for LiveFollow before
starting implementation work against the server.

This checklist assumes:
- the current deployed API is authoritative for current work
- the older nested `/api/v1/client/sessions/...` API is future design only
- app-side LiveFollow Phases 1–4 are already merged

---

## ✅ REQUIRED

### Current deployed API is explicit
Confirm the docs clearly define the currently deployed endpoints:
- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

### Current auth model is explicit
Confirm the docs clearly define:
- `session/start` returns `session_id`, `share_code`, `status`, `write_token`
- write endpoints require `X-Session-Token`
- public follow/read by `share_code` requires no write token

### Wire bodies are frozen
Confirm the docs define request/response bodies for:
- session start
- position upload
- task upsert
- session end
- live read by session id
- live read by share code

### Ownership split is explicit
Confirm the docs distinguish:

**Server-owned**
- session lifecycle/status
- share code generation
- write token issuance
- server authorization outcomes
- server response payload fields
- telemetry ingest validation/dedupe
- task revisioning

**Client transport-local**
- `transportAvailability`
- `lastError`
- reconnect/retry state
- local pending-command state
- local monotonic freshness derivation
- direct-vs-OGN arbitration

### Time semantics are explicit
Confirm the docs say:
- XCPro keeps `fixMonoMs` internally
- wire/server contract uses wall-time plus ordering/sequence semantics
- device monotonic time is not cross-system truth

### Telemetry upload is explicit
Confirm the contract defines:
- required telemetry fields
- optional telemetry fields
- typed identity carriage
- ordering/sequence semantics
- validation/error behavior

### Follow/read contract is explicit
Confirm the docs explain:
- intended use of `GET /api/v1/live/{session_id}`
- intended use of `GET /api/v1/live/share/{share_code}`
- whether public-follow polling is the first transport XCPro should integrate
- whether any stronger private/direct-watch feed remains out of scope/currently missing

### Error codes are explicit
Confirm stable domain error codes are defined for at least:
- unauthorized
- forbidden
- invalid token
- session not found
- invalid session id/share code
- session ended
- invalid telemetry sample
- rate limited (if implemented)
- transport/service unavailable

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

---

## ⚠️ VALIDATION QUESTIONS

Before approving the contract, confirm:

1. Can XCPro start a session using the documented current API?
2. Can XCPro upload pilot telemetry using the documented current API?
3. Can XCPro upsert task snapshots using the documented current API?
4. Can XCPro end a session using the documented current API?
5. Can XCPro watch/follow using the documented current API without inventing a new server endpoint?
6. Are any remaining missing capabilities clearly labeled as future server gap-fill rather than hidden assumptions?

---

## FINAL DECISION

### APPROVE if:
- current deployed API is the only active contract for current work
- wire bodies are frozen enough to code against
- ownership split is explicit
- time semantics are explicit
- telemetry upload and follow/read flows are defined
- the app-side integration path is clear

### REJECT if:
- current vs future API shapes are mixed
- telemetry upload is still hand-wavy
- follow/read transport is still ambiguous
- client-local state is leaking into server DTOs
- coding would still require guessing endpoint or payload behavior
