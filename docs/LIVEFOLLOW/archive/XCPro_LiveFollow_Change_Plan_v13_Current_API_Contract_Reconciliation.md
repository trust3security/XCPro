# XCPro LiveFollow — Change Plan v13 (Current API Contract Reconciliation)

Date: 2026-03-20
Status: Historical phase-5 execution plan after current-API slices 1 and 2 shipped
Supersedes for active Phase 5 work: `XCPro_LiveFollow_Change_Plan_v12_Current_API_Integration.md`

## Status note (2026-03-21)

Phase 5 current-API slices 1 and 2 shipped.

Shipped from this plan:
- current-API transport slices 1 and 2
- share-code watch flow
- pilot UI surfacing and copying `share_code`

Deferred from this plan:
- `POST /api/v1/task/upsert` transport
- richer watch/read payload mapping beyond the current public payload
- stronger server error model

Current active next-step planning has moved to:
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v14_Friends_Flying_List.md`
- `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v10.md`

## Purpose

This version takes the current deployed API from “known surface” to “extracted real contract”.

The previous plan (v12) was correct to treat the deployed simple API as authoritative.
The server-code audit now confirms the exact wire shape and the real gaps that still block
clean XCPro transport adapter implementation.

This plan is for the next real track after LiveFollow app-side Phases 1–4:
1. reconcile the frozen server contract with XCPro’s current transport seams,
2. decide the remaining adapter-mapping rules,
3. make only the minimum server hardening changes if needed,
4. then implement XCPro transport adapters,
5. then run end-to-end verification.

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
- read endpoints are currently public:
  - `GET /api/v1/live/{session_id}`
  - `GET /api/v1/live/share/{share_code}`

Use the current deployed simple API as implementation truth.
Do not target `/api/v1/client/sessions/...` unless that becomes a separate migration project.

## What must be preserved from XCPro

Do not redesign these app-side facts:

- `FlightDataLiveOwnshipSnapshotSource` remains the ownship export seam.
- `LiveFollowSessionRepository` remains Android-side local session truth owner.
- `WatchTrafficRepository` remains Android-side watch arbitration owner.
- map remains a render-only consumer.
- typed identity remains authoritative; aliases remain aliases only.
- explicit unavailable adapters are replaced by real transport adapters, not hidden.
- app-local monotonic freshness (`fixMonoMs`, stale/offline) stays local.

## Real mismatches found in the server-code audit

These are the concrete gaps now confirmed from real code:

### 1. Position upload shape is stricter than XCPro’s ownship snapshot
Server requires non-null:
- `alt`
- `speed`
- `heading`
- `timestamp`

XCPro ownship snapshot currently allows nullable:
- altitude
- speed
- track
- wall time

So before coding adapters, freeze:
- upload gating rules
- altitude source precedence
- speed-unit assumption
- heading requirement behavior

### 2. Session gateway is not one-to-one with current server endpoints
Server:
- `POST /api/v1/session/start` reads no body
- there is no current server endpoint for watch `join` / `leave`

XCPro current gateway abstraction expects:
- `startPilotSession(request)`
- `stopCurrentSession(sessionId)`
- `joinWatchSession(sessionId)`
- `leaveSession(sessionId)`

So before coding adapters, freeze:
- how `join/leave` map to public polling state instead of server session membership
- whether watch integration should use `share_code` as the primary external key

### 3. Current live-read payload is weaker than XCPro direct-watch seam
Server live read currently exposes:
- `lat`
- `lon`
- `alt`
- `speed`
- `heading`
- `timestamp`
- optional task block

It does not expose:
- `fixMonoMs`
- `verticalSpeedMs`
- typed identity in read payload
- direct-watch authorization in read payload

So the first XCPro watch transport must be treated as a degraded polling integration unless server read shape is hardened first.

### 4. Server returns share_code and write_token, but current app flow does not yet surface them
Before integration, freeze:
- where `share_code` is stored
- where `write_token` is stored
- whether pilot UI shows `share_code` now or later
- ensure `write_token` stays transport-local / non-UI

### 5. Error model is still too stringly typed
Server currently returns FastAPI default `detail` strings and default 422 bodies.
That is workable for manual use, but brittle for long-lived client transport mapping.

Strong recommendation:
- add stable machine-readable error codes on the server before or during transport integration

## Phase order

### Phase 5A — Contract reconciliation (docs only)
Land and review:
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md`
- keep `docs/LIVEFOLLOW/ServerInfo.md` as factual deployed-server reality/provenance note
- update checklist/prompts/next-steps to reflect the real extracted contract
- explicitly record the remaining adapter decisions:
  - `share_code` vs `session_id` watch usage
  - `write_token` / `share_code` storage
  - upload gating rules
  - speed-unit assumption
  - degraded public-watch behavior

No code changes in 5A.

### Phase 5B — Minimal server hardening only if needed
Before XCPro client integration, strongly consider a small server hardening slice for:
- machine-readable error codes
- speed-unit clarification in contract/docs
- any minimal read-payload clarification required for first watch integration

Do not broaden scope into:
- future nested API refactor
- WebSockets
- notifications/FCM
- OGN/FLARM fusion redesign

### Phase 5C — XCPro transport integration against the current deployed API
Only after 5A and any required 5B hardening:

Implement:
- real `LiveFollowSessionGateway`
- telemetry upload adapter for:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
- follow/read adapter for:
  - `GET /api/v1/live/share/{share_code}` first
  - `GET /api/v1/live/{session_id}` only if the UX still truly needs it
- mapping of server DTOs into existing repository interfaces
- local derivation of app freshness on receipt

Preserve:
- Android-side ownership and local monotonic freshness logic
- render-only map ownership
- transport-local state as local state

### Phase 5D — End-to-end verification
Verify:
- session start
- token/header behavior
- position upload
- task upsert
- session end
- public follow/read by share code
- lifecycle transitions (`active`, `stale`, `ended`)
- replay remains side-effect free
- error-code mapping is stable
- unavailable/degraded transport surfaces honestly in XCPro

## Out of scope

- future path-family refactor to `/api/v1/client/sessions/...`
- notifications / FCM
- share-link product expansion beyond current server capability
- OGN/FLARM fusion redesign
- task ownership changes
- new map ownership
- second ownship pipeline
- broad DI/lifecycle refactors

## Required same-PR doc sync when Phase 5 code lands

If any runtime wiring lands, update in the same PR:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/LIVEFOLLOW/archive/LiveFollow_Next_Steps_v9.md`
- this plan if scope/status changes

## Merge gate

Do not start XCPro transport adapter implementation until:
1. the extracted server contract is frozen in docs,
2. the contract passes the checklist review,
3. the unresolved adapter-mapping rules are explicitly decided,
4. the need for a small server hardening slice has been decided.
