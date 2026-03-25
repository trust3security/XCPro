# LiveFollow App/Server Contract Checklist v4

## Purpose

Use this checklist as a recurring audit gate whenever the **current deployed
LiveFollow wire contract** changes.

This checklist is not the contract owner. Contract ownership stays in:

- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`

---

## Required

### Current deployed endpoints are explicit
Confirm the contract doc defines:

- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

### Request and response bodies are frozen
Confirm the contract doc freezes the wire shapes for:

- session start
- position upload
- task upsert
- session end
- active-pilots list
- live read by session id
- live read by share code
- root health

### Auth model is explicit
Confirm the docs clearly define:

- which endpoints require `X-Session-Token`
- which endpoints are public
- that `session/start` returns:
  - `session_id`
  - `share_code`
  - `status`
  - `write_token`

### Telemetry rules are explicit
Confirm the contract defines:

- required position fields
- optional `agl_meters`
- that client monotonic fields are rejected on the wire
- speed units and wall-time timestamp semantics
- ordering, dedupe, and impossible-jump rules

### Task rules are explicit
Confirm the contract defines:

- full task upsert shape
- explicit `clear_task` shape
- that clear cannot be combined with `task_name` or `task`
- revision and dedupe behavior
- that live reads return `task: null` after clear

### Active-pilots list behavior is explicit
Confirm the contract defines:

- that `GET /api/v1/live/active` returns a JSON list
- the list-item fields
- inclusion rules for active/stale/ended/never-started sessions
- current `display_label` behavior
- `latest` nullability and `agl_meters` behavior

### Live-read behavior is explicit
Confirm the contract defines:

- public read by both `session_id` and `share_code`
- the `latest` and `positions` payload shapes
- optional `agl_meters` in read payloads
- `task` nullability before any task and after clear
- that the current read payload is still a degraded transport relative to XCPro's richer internal watch seam

### Error behavior is explicit
Confirm the contract defines:

- machine-readable `code` plus `detail` responses
- current 401 / 403 / 404 / 409 patterns
- `422` validation behavior and payload shape

### Ownership and time semantics are explicit
Confirm the contract distinguishes:

- server-owned fields and decisions
- client-transport-local state
- wall-time wire semantics versus XCPro-local monotonic freshness

---

## Forbidden

Reject the contract update if any of these happen:

- mixing the current deployed API with older future-target APIs as if both are current
- leaving duplicate current owners for the same wire contract
- keeping a separate active-pilots contract active after its details were merged into the main contract
- mapping server DTOs one-to-one onto client-local fields like `transportAvailability` or `lastError`
- treating cross-device monotonic timestamps as shared truth
- mixing product-planning content into `ServerInfo.md`
- presenting future refactor ideas as deployed reality

---

## Validation Questions

Before approving a contract change, confirm:

1. Does the deployed contract doc reflect real current server behavior rather than planned behavior?
2. If a separate side doc exists, does it still have a unique purpose and a non-conflicting owner?
3. Are all new additive fields, endpoints, and nullability rules described in the contract owner?
4. Did the current-state summary and `README.md` get updated if the active doc canon changed?

---

## Decision

### Approve if:

- the current deployed contract is frozen and explicit
- there is a single obvious contract owner
- current docs do not conflict with one another
- the error, time, auth, and ownership rules are all clear

### Reject if:

- deployed behavior is still described by superseded plan docs
- current docs disagree about the active contract
- live-read, task-clear, or active-list behavior is still ambiguous
