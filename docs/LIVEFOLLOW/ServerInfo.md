# LiveFollow ServerInfo

## Purpose

This file records factual deployed-server context for the current LiveFollow API work.

It is not:
- the contract owner
- the change plan
- a future API design note

Contract ownership stays in `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md`.

---

## Current baseline

- Baseline date from current server-code audit: `2026-03-20`
- Current deployed base URL: `https://api.xcpro.com.au`

### Current contract status

- Phase 5 current-API transport slice 1/2 is now implemented in XCPro.
- The current deployed wire contract has now been extracted from real server code.
- Contract ownership is frozen in `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md`.
- Historical phase-5 gate/plan docs now live under `docs/LIVEFOLLOW/archive/`.
- Active follow-on planning now lives in:
  - `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v10.md`
  - `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v14_Friends_Flying_List.md`
- `GET /api/v1/live/active` is not part of the frozen deployed contract yet.

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

- Inspected server repo:
  - `C:\Users\Asus\AndroidStudioProjects\XCPro_Server`

### Server branch / commit inspected

- Branch: `main`
- Commit: `696bb110ea5d3b8189d75dfaed5c19e7b07465ea`

### In-repo factual sources for this note

- `docs/LIVEFOLLOW/archive/XCPro_LiveFollow_Change_Plan_v13_Current_API_Contract_Reconciliation.md`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md`
- `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`

---

## Remaining gaps after XCPro transport slice 2

- Current live-read payload is still weaker than the XCPro direct-watch seam.
- `POST /api/v1/task/upsert` is part of the deployed contract but is not yet wired in XCPro.
- Current server errors are still stringly typed for long-lived client transport mapping.
- End-to-end runtime verification against the deployed API still needs a final proof pass.
- Friends Flying server/app work remains future scope:
  - `GET /api/v1/live/active`
  - display-label support
  - active-pilot picker UI

---

## Operator note

Treat this file as deployed-server reality and provenance only.

- If server code says something different, update this file.
- If the contract shape changes, update the contract doc.
- If future API design changes are proposed, record them in the plan docs instead of here.
