# Private Follow Current Repo State

Date: 2026-03-24
Status: Current implemented repo state for the authenticated private-follow lane

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

## Still Out Of Scope

These behaviors are still not implemented in this slice:

- follower-only live map gating
- authenticated `/api/v2/live/*` session ownership/visibility controls
- follower/following list screens
- block/mute/report
- email-based discovery
- push notifications

## Public LiveFollow Is Still Separate

The current public spectator lane remains the existing `/api/v1/live/*` path.

This private-follow work does not repurpose:

- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

Public share-code viewing remains separate from the authenticated relationship graph.
