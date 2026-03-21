# LiveFollow Current Deployed API Contract v2

Date: 2026-03-20
Status: Frozen from current server-code audit

## Purpose

This document freezes the **currently deployed LiveFollow API** for current implementation work.

It is the XCPro-facing contract owner for the current server reality.

It is **not**:
- the older `/api/v1/client/sessions/...` target API
- a future refactor proposal
- a generic backend design note

## Base URL

`https://api.xcpro.com.au`

## Current authoritative endpoints

- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

---

## 1. Session start

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
- XCPro client transport must decide where to store them.
- The current XCPro app contract does not yet expose them one-for-one in the UI-facing gateway snapshot.

---

## 2. Position upload

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
  "speed": 0.0,
  "heading": 0.0,
  "timestamp": "datetime"
}
```

### Required fields
All fields above are currently required by the deployed server.

### Validation rules
- `lat` / `lon` must be in valid geographic range
- `alt` must be between `-1000` and `20000`
- `speed` must be between `0` and `1000`
- `heading` must be between `0` and `360`
- `timestamp` must not be more than `300s` in the future

### Ordering / dedupe semantics
- ordering is by `timestamp` only
- older than last sample -> `409`
- same timestamp + exact same numeric payload -> `200 {"ok": true, "deduped": true}`
- same timestamp + different payload -> `409`
- later sample implying >500 km/h jump from last point -> `400`
- accepted sample -> `200 {"ok": true}`

### Additional notes
- dedupe compares only against the latest stored sample
- `last_position_at` is server receipt time, not client timestamp
- naive timestamps are treated as UTC

### Current XCPro implication
XCPro must explicitly freeze:
- altitude source precedence
- upload gating when `alt` / `speed` / `heading` / `fixWallMs` are missing
- speed unit assumption / confirmation
- local monotonic ordering remains app-local and must not be sent as shared truth

---

## 3. Task upsert

### Endpoint
`POST /api/v1/task/upsert`

### Auth
Requires header:

```text
X-Session-Token: <write_token>
```

### Request body
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

### Required task rules
- `turnpoints` must contain at least 2 objects
- each turnpoint requires:
  - nonblank `name`
  - nonblank `type`
  - `lat`
  - `lon`
- optional `radius_m` must satisfy:
  - `0 < radius_m <= 500000`

### Optional start/finish rules
If present, `task.start` and `task.finish` must be objects.
Optional `type` must be nonblank if present.
Optional `radius_m` follows the same allowed range.

### Storage / revisioning
- everything else inside `task` is stored verbatim
- first create -> `200 {"ok": true, "task_id": "<uuid>", "revision": 1}`
- exact current-payload match -> same revision plus `"deduped": true`
- otherwise revision increments by 1
- dedupe compares only with the current revision

---

## 4. Session end

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
First end:
```json
{
  "ok": true,
  "session_id": "string",
  "status": "ended",
  "ended_at": "<iso>"
}
```

Repeated end:
- idempotent
- returns the same response shape

---

## 5. Live reads

### GET /api/v1/live/{session_id}
### GET /api/v1/live/share/{share_code}

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
    "speed": 0.0,
    "heading": 0.0,
    "timestamp": "<iso>"
  },
  "positions": [
    {
      "lat": 0.0,
      "lon": 0.0,
      "alt": 0.0,
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
- `task` may be null

### Current XCPro implication
This is enough for a first polling-based follow integration, but it does **not** yet provide:
- `fixMonoMs`
- `verticalSpeedMs`
- server-supplied typed identity in the read payload
- direct-watch authorization as a read payload field

XCPro should treat this as a **degraded public-watch transport** unless/until a stronger feed is added.

---

## 6. Root health endpoint

### Endpoint
`GET /`

### Response
```json
{
  "status": "XCPro backend running"
}
```

---

## Auth and error behavior

### Auth-protected endpoints
Require `X-Session-Token`:
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

### Public endpoints
- `POST /api/v1/session/start`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

### Current auth failures
- `401 {"detail":"missing X-Session-Token header"}`
- `403 {"detail":"write token unavailable for this session"}`
- `403 {"detail":"invalid session token"}`

### Current not-found behavior
- write routes do session lookup before auth:
  - bad `session_id` -> `404 {"detail":"session not found"}`
- live reads use:
  - `404 {"detail":"not found"}`

### Current validation / error envelope
There is no custom machine-readable error wrapper yet.
Current behavior is FastAPI default:
- handler-thrown errors -> `{"detail":"..."}`
- request/model failures -> 422 with default Pydantic detail list

---

## Lifecycle semantics

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

If there is no accepted position yet, status remains `active`.

### Ended behavior
- `POST /api/v1/session/end` sets stored status to `ended`
- `ended_at` is recorded
- ended sessions reject:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  with `409 "session already ended"`

### Reactivation
A stale session becomes active again after an accepted position.

### Task behavior
Task upserts do **not** refresh staleness.

---

## Ownership split

### Server-owned
- `session_id`
- `share_code`
- `write_token`
- session lifecycle/status
- authorization outcomes
- created/ended/last-position timestamps
- accepted position history/latest sample
- task id, revision, updated_at, stored payload
- server validation and dedupe decisions

### XCPro client transport-local
- `transportAvailability`
- `lastError`
- retry/backoff/reconnect state
- local pending command state
- local monotonic freshness derivation (`fixMonoMs`, `ageMs`, stale/offline)
- direct-vs-OGN arbitration
- replay blocking state

### Important rule
Do **not** map the current server DTOs one-for-one onto XCPro’s transport-local state.

---

## Time semantics

### Inside XCPro
- keep `fixMonoMs` internal
- keep monotonic freshness/stale/offline logic local

### On the wire
- use `timestamp` / `fixWallMs` style wall-clock semantics
- use ordering by wall time / sequence-friendly semantics
- do not use device monotonic time as cross-system truth

### Current server reality
- the server currently orders by `timestamp`
- no separate sequence/cursor token is currently defined for write ingest
- live reads do not expose a transport cursor

---

## Current XCPro integration notes after transport slice 2

1. `write_token` and `share_code` are now stored as transport-local state in the current XCPro gateway implementation.
2. Position upload now gates on required fields and skips non-increasing timestamps before hitting the server.
3. Public watch by `share_code` is the first user-facing watch path; `session_id` read remains available.
4. `POST /api/v1/task/upsert` remains part of the deployed contract but is not yet wired in XCPro.
5. Current public live read remains a degraded transport relative to the richer XCPro direct-watch seam because it does not expose typed identity, vertical speed, or monotonic age.
6. Server-side machine-readable error codes remain a future hardening improvement, not a frozen deployed-contract field today.

## Recommended next move

1. Keep this file as the frozen owner for the deployed wire contract.
2. Use `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v10.md` and `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v14_Friends_Flying_List.md` for active follow-on planning.
3. Wire the remaining XCPro task-upsert and end-to-end verification work against this contract as separate slices.
4. Keep future Friends Flying work additive to this contract unless the deployed server shape actually changes.
