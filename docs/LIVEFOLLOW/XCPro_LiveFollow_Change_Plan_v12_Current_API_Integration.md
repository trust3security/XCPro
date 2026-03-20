# XCPro LiveFollow — Change Plan v12 (Current Deployed API Integration)

## Purpose

This plan replaces the generic server/transport framing from v11 with the **current deployed API**
as the implementation baseline.

Key decision:
- Use the **currently deployed API** as the authoritative contract for current implementation work.
- Do **not** target the older `/api/v1/client/sessions/...` path family unless the task is an
  explicit future API refactor.

This plan is for the next real track after LiveFollow app-side Phases 1–4:
1. freeze the **current deployed API** as the shared contract,
2. extract/freeze the exact request/response bodies from server code,
3. integrate XCPro to that deployed API,
4. identify only the remaining genuine server gaps.

---

## Authoritative current API baseline

Base URL:
- `https://api.xcpro.com.au`

Current deployed endpoints:
- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`
- `GET /`

Current auth model:
- `POST /api/v1/session/start` returns:
  - `session_id`
  - `share_code`
  - `status`
  - `write_token`
- write endpoints use:
  - `X-Session-Token: <write_token>`
- follower/public read uses:
  - `share_code`
  - no write token

Current verified server behavior:
- session lifecycle: `active`, `stale`, `ended`
- ended sessions reject further writes
- position validation
- retry-safe position dedupe
- task snapshot upsert
- task revisioning
- identical task-upsert dedupe
- HTTPS behind Caddy on `api.xcpro.com.au`
- PostgreSQL + Redis in Docker

---

## What this changes from v11

v11 assumed a more generic server/transport planning phase and still had some ambiguity around
future nested routes and current deployed routes.

v12 makes these decisions explicit:
- **current deployed API first**
- freeze the wire shapes from the server code that exists now
- integrate XCPro to the deployed API now
- treat the older `/api/v1/client/sessions/...` contract as a future refactor only

---

## What must be preserved from the app side

Do not redesign these Android-side facts:

- `FlightDataLiveOwnshipSnapshotSource` remains the ownship export seam.
- `LiveFollowSessionRepository` remains the Android-side local session truth owner.
- `WatchTrafficRepository` remains the Android-side watch arbitration owner.
- map remains a render-only consumer.
- typed identity remains authoritative; aliases remain aliases only.
- explicit unavailable adapters are replaced by real transport adapters, not hidden.

---

## Current known contract gaps

The endpoint surface is known, but the exact wire contract is still too loose in these places:

1. exact request/response bodies for:
   - session start
   - position upload
   - task upsert
   - session end
   - live session read
   - live read by share code

2. stable error-code mapping

3. telemetry upload rules:
   - required vs optional fields
   - sequence / ordering semantics
   - typed identity carriage

4. follow/read mapping:
   - how `GET /api/v1/live/{session_id}` differs from `GET /api/v1/live/share/{share_code}`
   - whether public-follow should be integrated first using `share_code`
   - whether any stronger private/direct-watch contract is still missing

5. client-local vs server-owned fields:
   - server owns session lifecycle/status, share code, authorization outcomes
   - client transport owns `transportAvailability`, `lastError`, reconnect/retry state,
     local pending state, and local monotonic freshness derivation

6. time semantics:
   - XCPro keeps `fixMonoMs` internally
   - wire/server contract must use wall/ordering-friendly semantics, not cross-device monotonic truth

---

## Phase 5A — Contract freeze (docs only)

Create and land:
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v1.md`
- keep `docs/LIVEFOLLOW/ServerInfo.md` as the factual deployed-server reality/provenance note
- update checklist/prompts/next-steps to reference the current deployed API as authoritative

Document ownership in this phase:
- `ServerInfo.md` records what server reality was inspected
- `LiveFollow_Current_Deployed_API_Contract_v1.md` owns the frozen XCPro-facing contract
- this plan owns execution order and scope only

The contract-freeze doc must define:
- endpoint-by-endpoint request bodies
- endpoint-by-endpoint response bodies
- error codes
- required headers
- public vs token-protected paths
- typed identity fields
- telemetry upload field semantics
- wall-time / sequence semantics
- which fields are server-owned vs client transport-local

No code changes in 5A.

---

## Phase 5B — XCPro transport integration against the current deployed API

After 5A is frozen and reviewed, implement XCPro-side adapters for the current deployed API.

### Replace / implement
- real `LiveFollowSessionGateway`
- telemetry upload adapter for:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
- follow/read adapter for:
  - `GET /api/v1/live/share/{share_code}`
  - and/or `GET /api/v1/live/{session_id}` if the contract confirms intended use

### Preserve
- `LiveFollowSessionRepository` as Android local truth
- `WatchTrafficRepository` as Android watch arbitration truth
- local freshness/stale/offline logic stays local
- map remains render-only

### Do not add yet
- Retrofit/WebSocket if the current server does not require it
- notifications/FCM
- leaderboards/history/share-link expansion
- task ownership changes
- second ownship pipeline

---

## Phase 5C — Server gap fill only where required by the frozen current contract

Only after 5A reveals real gaps.

Possible examples:
- missing error-code standardization
- missing read shape needed by the XCPro client
- explicit capability/degraded flags
- stronger authorization semantics
- direct-watch/private-follow transport if the current public polling model is insufficient

Do not refactor the deployed path family to `/api/v1/client/sessions/...` in this phase unless that
is made an explicit migration project.

---

## Phase 5D — End-to-end verification

Verify:
- session start from XCPro against the deployed API
- token/header behavior
- position upload
- task upsert
- session end
- follower/public read by share code
- lifecycle transitions (`active`, `stale`, `ended`)
- replay remains side-effect free
- error-code mapping is stable
- unavailable/degraded transport surfaces honestly in XCPro

---

## Out of scope

- future path-family refactor to `/api/v1/client/sessions/...`
- notifications / FCM
- share links beyond current server capability
- OGN/FLARM fusion redesign
- task ownership changes
- new map ownership
- second ownship pipeline
- broad DI/lifecycle refactors

---

## Required same-PR doc sync when Phase 5 code lands

If any runtime wiring lands, update in the same PR:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v8.md`
- this plan if the scope/status changes

---

## Merge gate

Do not start implementation until:
1. the current deployed API contract is frozen in docs,
2. the contract passes the checklist review,
3. the client/server ownership split is explicit,
4. the app-side adapter plan is reviewed against the current server reality.
