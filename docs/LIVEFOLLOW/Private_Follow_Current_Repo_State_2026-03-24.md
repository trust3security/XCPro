# Private Follow Current Repo State

Date: 2026-03-24
Status: Current implemented repo state for the authenticated private-follow lane after Phase 3 live entitlement landed in repo

## Purpose

This document owns the current repo reality for XCPro private follow.

It is separate from:

- the current public/deployed LiveFollow spectator summary
- future proposal briefs
- server provenance/deployment notes

Use it to answer:

- what authenticated private-follow behavior is implemented in the repo now
- what still remains out of scope
- which authenticated endpoints exist for the private-follow lane

It does not replace the deployed public LiveFollow contract owner:
`LiveFollow_Current_Deployed_API_Contract_v3.md`.

## Current Durable References

- `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
- `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md`
- `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md`

Keep the Google setup note active while it remains the owner for Google
Credential Manager sign-in, XCPro-Server token exchange, and required
Android/server environment setup.
Completed Phase 1 and Phase 3 execution briefs/checklists are historical and
now live under `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`.

## Implemented In Repo Now

The authenticated private-follow lane now includes:

- Google sign-in via Credential Manager and `POST /api/v2/auth/google/exchange`
- XCPro-Server-issued bearer tokens for authenticated `/api/v2/*`
- signed-in XCPro account state in the app
- signed-in vs signed-out Manage Account flow
- server-owned `users`, `auth_identities`, `pilot_profiles`, and `privacy_settings`
- authenticated `GET /api/v2/me`
- authenticated `PATCH /api/v2/me/profile`
- authenticated `PATCH /api/v2/me/privacy`
- authenticated handle search with `GET /api/v2/users/search?q=...`
- follow request creation with `POST /api/v2/follow-requests`
- incoming request list with `GET /api/v2/follow-requests/incoming`
- outgoing request list with `GET /api/v2/follow-requests/outgoing`
- accept with `POST /api/v2/follow-requests/{id}/accept`
- decline with `POST /api/v2/follow-requests/{id}/decline`
- persisted `follow_requests` and `follow_edges` on the server
- authenticated live-session start with `POST /api/v2/live/session/start`
- authenticated live-session visibility updates with
  `PATCH /api/v2/live/session/{session_id}/visibility`
- authenticated following-live discovery with `GET /api/v2/live/following/active`
- authenticated per-user live lookup with `GET /api/v2/live/users/{user_id}`
- authenticated live-session read with `GET /api/v2/live/session/{session_id}`
- server-owned live sessions with durable `owner_user_id` and per-session
  `visibility`
- session visibility values of `off`, `followers`, and `public`
- public `share_code` exposure only when a session is effectively `public`
- public `GET /api/v1/live/active`, `GET /api/v1/live/{session_id}`, and
  `GET /api/v1/live/share/{share_code}` now hide non-public sessions
- authenticated session start ends any existing non-ended owned session before
  creating the next one
- app uses authenticated v2 session start when bearer auth exists and falls
  back to public v1 start only for signed-out public sharing
- pilot UI includes live visibility selection
- viewer browse splits into `Public` and `Following`
- authorized session-id watch uses authenticated live reads while public
  share-code watch stays on the public lane
- public sessions can still use the public/share-code lane and can also be read
  through the authenticated lane when entitlement allows
- the existing write-token upload path is still reused for
  `POST /api/v1/position`
- the existing write-token upload path is still reused for
  `POST /api/v1/task/upsert`
- the existing write-token upload path is still reused for
  `POST /api/v1/session/end`

## Proposal Vs Implemented Differences

- The Phase 3 live-entitlement lane matches the proposed v2 endpoint shape for
  authenticated session start, visibility patch, following-active discovery,
  per-user live lookup, and session read.
- The broader proposal still includes not-yet-implemented endpoints for push
  tokens, follower/following list reads, remove follower, and blocks.
- The implemented relationship-create path remains
  `POST /api/v2/follow-requests` plus incoming/outgoing/accept/decline
  endpoints, rather than the proposal's broader
  `POST/DELETE /api/v2/users/{user_id}/follow` surface.

## Still Out Of Scope

These behaviors remain out of scope or not yet implemented:

- full follower/following list screens
- block/remove follower controls
- watcher counts
- email-based discovery
- push notifications
- requiring sign-in for the public watch lane
- rollout sequencing between server migration/deploy and client rollout

## Public LiveFollow Is Still Separate

The current public spectator lane remains the existing `/api/v1/live/*` path.

This private-follow work does not repurpose:

- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

Public share-code viewing remains separate from the authenticated relationship
graph.
Private-follow repo implementation does not automatically mean public
deployed-contract status.
Do not treat this repo-state doc as a deployed public contract owner.
