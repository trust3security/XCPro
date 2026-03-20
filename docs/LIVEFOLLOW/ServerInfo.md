# LiveFollow ServerInfo

## Purpose

This file records factual deployed-server context for the current LiveFollow API work.

It is not:
- the contract owner
- the change plan
- a future API design note

Contract ownership stays in `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v1.md`.

---

## Current baseline

- Baseline date from current in-repo server notes: `2026-03-19`
- Current deployed base URL: `https://api.xcpro.com.au`

### Current deployed endpoints

- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

### Current auth mechanism

- `POST /api/v1/session/start` returns:
  - `session_id`
  - `share_code`
  - `status`
  - `write_token`
- Write endpoints use header:
  - `X-Session-Token: <write_token>`
- Public follow/read by `share_code` does not require a write token.

### Current verified server behavior

- session lifecycle supports `active`, `stale`, and `ended`
- position validation is implemented
- position upload dedupe is retry-safe
- task snapshot upsert is implemented
- task revisioning is implemented
- identical task upserts dedupe correctly
- ended sessions reject further writes
- deployed API is served over HTTPS behind Caddy
- backend uses PostgreSQL plus Redis in Docker

---

## Provenance

### Server repo / location

- Not recorded in this repo yet.
- Must be captured during the Phase 5A server-code extraction pass before claiming the wire contract is fully frozen from code.

### Server branch / commit inspected

- Not recorded in this repo yet.
- Must be captured during the Phase 5A server-code extraction pass.

### In-repo factual sources for this note

- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v12_Current_API_Integration.md`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v1.md`

---

## Observed gaps relative to contract freeze

- Exact request bodies are not frozen yet for all endpoints.
- Exact response bodies are not frozen yet for all endpoints.
- Stable domain error-code mapping is not frozen yet.
- Timestamp and ordering field names still need extraction from server code.
- Intended XCPro use of `GET /api/v1/live/{session_id}` versus `GET /api/v1/live/share/{share_code}` still needs explicit confirmation from server code.

---

## Operator note

Treat this file as deployed-server reality and provenance only.

- If server code says something different, update this file.
- If the contract shape changes, update the contract doc.
- If future API design changes are proposed, record them in the plan docs instead of here.
