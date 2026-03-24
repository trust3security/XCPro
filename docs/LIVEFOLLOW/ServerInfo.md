# LiveFollow ServerInfo

## Purpose

This file records factual deployed-server context and provenance for the
current LiveFollow stack.

It is not:

- the deployed-contract owner
- the product-status summary
- a future API design note

Contract ownership stays in
`docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`.
Current product/status ownership stays in
`docs/LIVEFOLLOW/LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`.

---

## Current Baseline

- Baseline date from current server-code audit: `2026-03-23`
- Current deployed base URL: `https://api.xcpro.com.au`

### Current deployed endpoints

- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

### Current auth mechanism

- `POST /api/v1/session/start` returns:
  - `session_id`
  - `share_code`
  - `status`
  - `write_token`
- write endpoints use:
  - `X-Session-Token: <write_token>`
- public reads currently include:
  - `GET /api/v1/live/active`
  - `GET /api/v1/live/{session_id}`
  - `GET /api/v1/live/share/{share_code}`

### Current verified server behavior

- session lifecycle supports `active`, computed `stale`, and `ended`
- `GET /api/v1/live/active` is part of the current deployed contract
- active list excludes ended sessions and sessions with no accepted position
- active list preserves stale status
- active list keeps a session even if the latest-cache entry is missing
- position upload accepts optional `agl_meters`
- live reads and active-list `latest` relay `agl_meters` when present
- task upsert accepts explicit `clear_task`
- a clear is stored as the current task revision and live reads return `task: null`
- repeated identical clears dedupe without advancing revision
- re-adding a task after clear restores `task` in live reads
- machine-readable error responses use `code` plus `detail`
- request-validation failures return `422` with `code: validation_error`
- ended sessions reject further position and task writes
- deployed API is served over HTTPS behind Caddy
- backend uses PostgreSQL plus Redis in Docker

### Current XCPro app integration facts

These are factual app-side integrations that now exist in the XCPro repo:

- Friends Flying fetches `GET /api/v1/live/active`
- LiveFollow position upload maps optional `agl_meters`
- task upsert is wired for both full task payloads and explicit clears

---

## Provenance

### Server repo / location

- Inspected server repo:
  - `C:\Users\Asus\AndroidStudioProjects\XCPro_Server`

### Server branch / commit inspected

- Branch: `feature/livefollow-phase6-active-pilots-endpoint`
- Commit: `99a9565858afee59c430c59009f28b1de9fb5b7a`

### In-repo factual sources for this note

- `C:\Users\Asus\AndroidStudioProjects\XCPro_Server\app\main.py`
- `C:\Users\Asus\AndroidStudioProjects\XCPro_Server\app\tests\test_livefollow_api.py`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`

---

## Operator Note

Treat this file as deployed-server reality and provenance only.

- If server code says something different, update this file.
- If the wire contract shape changes, update the deployed-contract doc.
- If product behavior changes, update the current-state summary doc.
- Do not use this file as the planning owner for future slices.
