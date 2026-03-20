# LiveFollow Current Deployed API Contract v1

## Status

This document freezes the **current deployed API** for current implementation work.

It is not the future nested `/api/v1/client/sessions/...` API.
That older path family is future refactor only and is out of scope for this contract.

## Base URL

`https://api.xcpro.com.au`

## Endpoints

### POST /api/v1/session/start
Authoritative current endpoint for starting a pilot live-follow session.

Expected current documented response fields:
- `session_id`
- `share_code`
- `status`
- `write_token`

Open item to extract from server code before implementation:
- exact request body
- exact response body
- exact error codes

### POST /api/v1/position
Authoritative current endpoint for pilot telemetry upload.

Auth:
- `X-Session-Token: <write_token>`

Open item to extract/freeze from server code:
- exact request body
- validation rules
- dedupe semantics
- exact response body
- exact error codes

### POST /api/v1/task/upsert
Authoritative current endpoint for task snapshot upsert.

Auth:
- `X-Session-Token: <write_token>`

Open item to extract/freeze from server code:
- exact request body
- revisioning semantics
- dedupe semantics
- exact response body
- exact error codes

### POST /api/v1/session/end
Authoritative current endpoint for ending a pilot session.

Auth:
- `X-Session-Token: <write_token>`

Open item to extract/freeze from server code:
- exact request body if any
- exact response body
- behavior for already-ended sessions
- exact error codes

### GET /api/v1/live/{session_id}
Current deployed read endpoint.

Open item to extract/freeze from server code:
- whether this is public or restricted
- intended client use
- exact response body
- exact error codes

### GET /api/v1/live/share/{share_code}
Current deployed public follow/read endpoint.

Auth:
- no write token required

Open item to extract/freeze from server code:
- exact response body
- polling semantics
- exact error codes

## Auth model

Current documented model:
- `session/start` returns a `write_token`
- write endpoints require header:
  - `X-Session-Token: <write_token>`
- public follow/read uses `share_code`

## Ownership split

### Server-owned
- `session_id`
- `share_code`
- session lifecycle/status
- token issuance
- authorization outcomes
- telemetry ingest validation/dedupe
- task revisioning
- server response payload fields

### XCPro client transport-local
- `transportAvailability`
- `lastError`
- retry/backoff/reconnect state
- local pending command state
- local freshness/stale/offline derivation
- direct-vs-OGN arbitration

## Time semantics

### Inside XCPro
- `fixMonoMs` stays internal for freshness/stale/offline logic

### On the wire
- use wall-clock / ordering friendly semantics
- do not use device monotonic time as cross-system truth

Open item to freeze from server code:
- exact timestamp field names already in use
- any sequence/order token semantics already implemented

## Telemetry contract note

The app-side transport integration should assume the minimum required sample set includes:
- typed identity
- SI position fields
- SI altitude/speed fields where available
- wall-clock timestamp
- any implemented ordering/sequence field

These exact shapes must be extracted from the current server code before coding the XCPro adapter.

## Follow/read note

Current server reality supports public follow/read by share code.
If the current deployed API is enough for follow/watch, XCPro should integrate that now rather than inventing a stronger direct-watch contract prematurely.

If a stronger private/direct-watch feed is still missing after extraction, treat that as a concrete server gap for a later Phase 5C step.
