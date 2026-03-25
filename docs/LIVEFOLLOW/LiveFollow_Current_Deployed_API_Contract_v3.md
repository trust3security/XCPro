# LiveFollow Current Deployed API Contract v3

Date: 2026-03-23
Status: Current deployed contract from server-code and app-code audit
Supersedes: `docs/LIVEFOLLOW/archive/2026-03-single-pilot-spectator-mvp/LiveFollow_Current_Deployed_API_Contract_v2.md`

## Purpose

This document is the XCPro-facing owner for the **current deployed LiveFollow
wire contract**.

This version bumps from `v2` because the active deployed reality now includes:

- `GET /api/v1/live/active`
- optional `agl_meters` on position upload and live reads
- explicit `clear_task` behavior as part of current task upsert
- `task: null` after clear
- machine-readable error responses with `code` plus `detail`

This doc also absorbs the current active-pilots contract details that were
previously split into a separate side doc.

It is not:

- the older `/api/v1/client/sessions/...` target API
- a future refactor proposal
- a product-status summary

---

## Base URL

`https://api.xcpro.com.au`

## Current authoritative endpoints

- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

---

## 1. Session Start

### Endpoint
`POST /api/v1/session/start`

### Auth
None.

### Request body
No request body is currently defined or read.

### Response body
```json
{
  "session_id": "<uuid4>",
  "share_code": "<8-char A-Z0-9>",
  "status": "active",
  "write_token": "<opaque>"
}
```

### Notes

- `share_code` and `write_token` are server-owned outputs.
- XCPro currently stores them as transport-local state.

---

## 2. Position Upload

### Endpoint
`POST /api/v1/position`

### Auth
Requires header:

```text
X-Session-Token: <write_token>
```

### Request body
```json
{
  "session_id": "string",
  "lat": 0.0,
  "lon": 0.0,
  "alt": 0.0,
  "agl_meters": 0.0,
  "speed": 0.0,
  "heading": 0.0,
  "timestamp": "datetime"
}
```

### Field rules

- `session_id`, `lat`, `lon`, `alt`, `speed`, `heading`, and `timestamp` are required
- `agl_meters` is optional and nullable
- `speed` is XCPro ground speed in meters per second
- `timestamp` is wall-clock UTC/ISO-8601-compatible time
- client monotonic fields are not part of this wire DTO and are rejected if sent

### Validation rules

- `lat` / `lon` must be in valid geographic range
- `alt` must be between `-1000` and `20000`
- `speed` must be between `0` and `1000`
- `heading` must be between `0` and `360`
- `timestamp` must not be more than `300s` in the future

### Ordering and dedupe semantics

- ordering is by `timestamp` only
- older than last sample -> `409`
- same timestamp plus exact same numeric payload -> `200 {"ok": true, "deduped": true}`
- same timestamp plus different payload -> `409`
- later sample implying a greater-than-500 km/h jump from the last point -> `400`
- accepted sample -> `200 {"ok": true}`

### Additional notes

- dedupe compares only against the latest stored sample
- `last_position_at` is server receipt time, not client timestamp
- naive timestamps are treated as UTC on readback

---

## 3. Task Upsert

### Endpoint
`POST /api/v1/task/upsert`

### Auth
Requires header:

```text
X-Session-Token: <write_token>
```

### Full task request body
```json
{
  "session_id": "string",
  "task_name": "string",
  "task": {
    "turnpoints": [
      {
        "name": "string",
        "type": "string",
        "lat": 0.0,
        "lon": 0.0,
        "radius_m": 1000.0
      }
    ],
    "start": {
      "type": "string",
      "radius_m": 1000.0
    },
    "finish": {
      "type": "string",
      "radius_m": 1000.0
    }
  }
}
```

### Explicit clear request body
```json
{
  "session_id": "string",
  "clear_task": true
}
```

### Validation rules

- clear payload must not include `task_name` or `task`
- `task_name` is required for non-clear upserts
- `turnpoints` must contain at least 2 objects
- each turnpoint requires:
  - nonblank `name`
  - nonblank `type`
  - `lat`
  - `lon`
- optional `radius_m` must satisfy:
  - `0 < radius_m <= 500000`
- optional `start` and `finish` must be objects if present

### Storage and revision semantics

- non-clear payloads are stored verbatim inside the current task revision
- explicit clear is stored as the current task revision
- live reads return `task: null` when the current revision is a clear
- first create -> `200 {"ok": true, "task_id": "<uuid>", "revision": 1, "cleared": false}`
- first clear on a session with no prior task -> `200 {"ok": true, "task_id": "<uuid>", "revision": 1, "cleared": true}`
- repeated identical current payload -> same revision plus `"deduped": true`
- repeated identical clear -> same revision plus `"deduped": true`
- otherwise revision increments by 1

---

## 4. Session End

### Endpoint
`POST /api/v1/session/end`

### Auth
Requires header:

```text
X-Session-Token: <write_token>
```

### Request body
```json
{
  "session_id": "string"
}
```

### Response body
```json
{
  "ok": true,
  "session_id": "string",
  "status": "ended",
  "ended_at": "<iso>"
}
```

### Notes

- repeated end is idempotent and returns the same shape

---

## 5. Active Pilots List

### Endpoint
`GET /api/v1/live/active`

### Auth
Public.

### Response body
The response is a JSON array, not an envelope object.

```json
[
  {
    "session_id": "string",
    "share_code": "string",
    "status": "active|stale",
    "created_at": "<iso>",
    "last_position_at": "<iso>",
    "latest": {
      "lat": 0.0,
      "lon": 0.0,
      "alt": 0.0,
      "agl_meters": 0.0,
      "speed": 0.0,
      "heading": 0.0,
      "timestamp": "<iso>"
    },
    "display_label": "Live ABC12345"
  }
]
```

### Field notes

- `session_id` is the internal session identifier
- `share_code` is the public watch token
- `status` is `active` or `stale`
- `display_label` is currently server-generated as `Live {share_code}`
- `latest` may be `null` if the latest-cache entry is missing
- `latest.agl_meters` is present and nullable

### Inclusion rules

- ended sessions are excluded
- sessions with no accepted position are excluded
- stale sessions remain listed with `status: "stale"`
- list ordering is by newest `last_position_at`, then newest `created_at`, then session id

---

## 6. Live Reads

### Endpoints

- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

These currently return the same payload shape.

### Auth

- share-code read is public
- session-id read is also public in the current server

### Response body
```json
{
  "session": "string",
  "share_code": "string",
  "status": "active|stale|ended",
  "created_at": "<iso>",
  "last_position_at": "<iso>|null",
  "ended_at": "<iso>|null",
  "latest": {
    "lat": 0.0,
    "lon": 0.0,
    "alt": 0.0,
    "agl_meters": 0.0,
    "speed": 0.0,
    "heading": 0.0,
    "timestamp": "<iso>"
  },
  "positions": [
    {
      "lat": 0.0,
      "lon": 0.0,
      "alt": 0.0,
      "agl_meters": 0.0,
      "speed": 0.0,
      "heading": 0.0,
      "timestamp": "<iso>"
    }
  ],
  "task": {
    "task_id": "string",
    "current_revision": 1,
    "updated_at": "<iso>",
    "payload": {
      "task_name": "string",
      "task": {}
    }
  }
}
```

### Important notes

- the key is `session`, not `session_id`
- `status` is lowercase
- `positions` contains up to 10 accepted positions, oldest to newest
- `latest.agl_meters` and `positions[*].agl_meters` are nullable
- `task` may be `null` before any task is shared and after an explicit clear

### Current XCPro implication

This remains enough for the current polling-based public-watch integration, but
it is still weaker than XCPro's richer internal direct-watch seam because it
does not expose:

- typed identity in the payload
- vertical speed
- monotonic age or cursor state

---

## 7. Root Health Endpoint

### Endpoint
`GET /`

### Response
```json
{
  "status": "XCPro backend running"
}
```

---

## Auth And Error Behavior

### Auth-protected endpoints
Require `X-Session-Token`:

- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

### Public endpoints

- `POST /api/v1/session/start`
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

### Error envelope

The current server returns machine-readable error payloads:

```json
{
  "code": "error_code",
  "detail": "message or validation detail list"
}
```

### Current auth failures

- `401 {"code":"missing_session_token","detail":"missing X-Session-Token header"}`
- `403 {"code":"session_token_unavailable","detail":"write token unavailable for this session"}`
- `403 {"code":"invalid_session_token","detail":"invalid session token"}`

### Current not-found behavior

- bad write `session_id` -> `404 {"code":"session_not_found","detail":"session not found"}`
- live-read miss -> `404 {"code":"session_not_found","detail":"not found"}`

### Current validation behavior

- request/model failures -> `422 {"code":"validation_error","detail":[...]}`
- task/position domain validation failures use `400` plus a specific `code`

---

## Lifecycle Semantics

### Stored server states

- `active`
- `ended`

### Computed read state

- `stale` is computed on read only

### Current stale rule

A session is `stale` when:

```text
now - last_position_at > 120s
```

If there is no accepted position yet, status remains `active`, but that session
does not appear in `GET /api/v1/live/active`.

### Ended behavior

- `POST /api/v1/session/end` sets stored status to `ended`
- `ended_at` is recorded
- ended sessions reject:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  with `409`

### Reactivation

A stale session becomes active again after a later accepted position.

### Task behavior

Task upserts, including clears, do not refresh staleness.

---

## Ownership Split

### Server-owned

- `session_id`
- `share_code`
- `write_token`
- session lifecycle/status
- authorization outcomes
- created/ended/last-position timestamps
- accepted position history and latest sample
- task id, revision, updated_at, and stored payload
- validation and dedupe decisions

### XCPro client transport-local

- `transportAvailability`
- `lastError`
- retry/backoff/reconnect state
- local pending-command state
- local monotonic freshness derivation
- direct-vs-OGN arbitration
- replay blocking state

### Important rule

Do not map the deployed server DTOs one-for-one onto XCPro's transport-local
state.

---

## Time Semantics

### Inside XCPro

- keep `fixMonoMs` internal
- keep monotonic freshness and stale/offline logic local

### On the wire

- use wall-clock `timestamp` semantics
- use ordering by wall time
- do not use device monotonic time as cross-system truth

### Current server reality

- the server orders uploads by `timestamp`
- live reads do not expose a transport cursor
- current server code rejects client monotonic fields on the wire DTO

---

## Current XCPro Alignment

- XCPro currently stores `write_token` and `share_code` as transport-local state
- XCPro uploads optional `agl_meters` when available
- XCPro uses `share_code` as the user-facing public watch key
- XCPro uses `GET /api/v1/live/active` for the Friends Flying active list
- XCPro uses `POST /api/v1/task/upsert` for both full task upserts and explicit clears

---

## Related Current Docs

- `docs/LIVEFOLLOW/LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
- `docs/LIVEFOLLOW/ServerInfo.md`
- `docs/LIVEFOLLOW/README.md`
- `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`
