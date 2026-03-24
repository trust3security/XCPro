# Private Follow Current Repo State

Date: 2026-03-24  
Status: Repo implementation and rollout hardening complete; rollout remains paused pending real staging/prod environment setup and verified staging execution.

## Purpose

This document owns the current repo reality for XCPro private follow.

It is separate from:

- the current public/deployed LiveFollow spectator summary
- future proposal briefs
- server provenance/deployment notes

Use it to answer:

- what authenticated private-follow behavior is implemented in the repo now
- what release candidates are currently frozen for rollout
- what the next operator step is
- what still remains manual/external before rollout can proceed
- which authenticated endpoints exist for the private-follow lane

It does not replace the deployed public LiveFollow contract owner:  
`LiveFollow_Current_Deployed_API_Contract_v3.md`.

## Current Rollout Status

Private follow is now **repo-complete and rollout-hardening-complete**.

What that means:

- the app/server implementation is present in repo
- repo-side rollout blockers were fixed
- release candidates were pinned
- fresh-db Alembic bootstrap is now safe in repo
- dev/static bearer shortcuts are now hardened for release/prod safety

What it does **not** mean yet:

- staging/prod environment configuration has been proven
- real-device Google sign-in and server token exchange have been proven
- staging smoke execution has been completed
- production rollout is approved

So the current state is:

- **repo readiness:** yes
- **rollout approval:** not yet
- **next mode of work:** operational staging execution, not new feature implementation

## Current Pinned Release Candidates

### Server RC
- commit: `b696f039540480468195087fa3f44338338f6fba`
- tag: `private-follow-rollout-rc-server-2026-03-24`
- status: clean pinned server rollout candidate

### App RC
- commit: `c25d0f6520686643c2670502048fee3a83b91ca9`
- tag: `private-follow-rollout-rc-app-2026-03-24`
- clean frozen worktree: `C:/Users/Asus/AndroidStudioProjects/XCPro_private_follow_rollout_rc_app`
- note: the main app worktree may still contain unrelated local files, but they are not part of the frozen RC

## Next Operator Step

Do this next, in order:

1. set the real staging environment values
2. deploy the pinned **server** RC to staging first
3. apply migrations
4. run the private-follow env preflight in staging
5. install the pinned app RC on a real Android device
6. prove Google sign-in and `POST /api/v2/auth/google/exchange`
7. run the staging smoke matrix end to end
8. make a `go / pause / fix specific blocker` rollout decision

Do **not** resume feature work unless staging exposes a real blocker bug.

## Current Operator Docs

Use these docs for the next operational pass:

- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`
- `XCPro_Private_Follow_Staging_Execution_Brief_2026-03-24.md`

Operator support docs:

- `XCPro_Private_Follow_Staging_Operator_Inputs_Worksheet_2026-03-24.md`
- `XCPro_Private_Follow_Staging_Smoke_Results_Template_2026-03-24.md`
- `XCPro_Private_Follow_Operator_Handoff_Message_2026-03-24.md`

Proposal/reference docs still relevant for later work:

- `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
- `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
- `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md`
- `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md`

Keep the Google setup note active while it remains the owner for Google
Credential Manager sign-in, XCPro-Server token exchange, staging/prod env rules,
and required Android/server environment setup.

Completed Phase 1 and Phase 3 execution briefs/checklists are historical and
now live under `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`.
Completed one-off cleanup/remediation briefs are historical and now live under
`docs/LIVEFOLLOW/archive/2026-03-private-follow-rollout-hardening/`.

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
- fresh-db Alembic bootstrap now upgrades an empty DB through the current
  private-follow chain without manual pre-seeding of the legacy public tables
- release Android builds hard-disable the configured dev bearer sign-in seam
- server-side static bearer auth now loads only when
  `XCPRO_RUNTIME_ENV=dev` and `XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH=1`
- repo now includes a private-follow environment preflight script at
  `XCPro_Server/scripts/private_follow_env_preflight.py`
- repo now includes a staging smoke owner doc at
  `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`

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

## Rollout Hardening Notes

These rollout blockers are now fixed in repo:

- fresh empty DB upgrade to head is bootstrap-safe in Alembic
- release/prod app builds do not expose `Use configured dev account`
- server-side static/dev bearer auth is fail-closed unless explicitly enabled
  for local dev
- release candidates are pinned for both server and app
- release owners now have one preflight command and one staging smoke owner doc

These steps still remain external/manual before rollout can proceed:

- set real staging/prod server env, especially:
  - `XCPRO_RUNTIME_ENV`
  - `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS`
  - `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET`
  - optional `XCPRO_PRIVATE_FOLLOW_BEARER_TTL_SECONDS`
- verify Google OAuth audience configuration against a real device token exchange
- run the env preflight successfully in the real staging environment
- deploy the pinned server RC to staging and apply migrations
- install/test the pinned app RC on a real Android device
- run the staging smoke matrix
- run production smoke after deploy if staging passes

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
