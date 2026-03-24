# XCPro Private Follow Staging Smoke Guide

Date: 2026-03-24
Status: Current operational owner for private-follow staging smoke execution

## Purpose

Use this guide when the hardened private-follow rollout candidate reaches a real
staging environment.

This guide is intentionally operational:

- it matches the endpoints and app behavior implemented in repo now
- it records what must be observed before client rollout can proceed
- it does not claim staging proof until someone actually runs it

## Preconditions

Before starting the smoke pass:

1. Server rollout candidate is pinned to the intended release commit/tag.
2. Android rollout candidate is pinned to the intended release commit/tag.
3. `python scripts/private_follow_env_preflight.py` passes in the target server runtime.
4. Server migrations are applied in staging.
5. One real staging-capable Android build exists with the correct
   `XCPRO_GOOGLE_SERVER_CLIENT_ID`.

## Test Accounts

Prepare at least these actors:

1. Pilot account
2. Approved follower account
3. Signed-in non-follower account
4. Signed-out public viewer path

## Evidence To Capture

For each section below, record:

- exact build/commit under test
- pass/fail
- relevant response code or app outcome
- screenshots or notes for any mismatch

## Smoke Matrix

### A. Auth and profile

1. Google sign-in completes on a real device.
2. `POST /api/v2/auth/google/exchange` returns an XCPro bearer token.
3. `GET /api/v2/me` succeeds for the signed-in user.
4. Signed-out access to `/api/v2/*` fails with `401`.

### B. Relationship baseline

1. Viewer can search for the pilot.
2. Viewer can send a follow request when approval is required.
3. Pilot can accept the request.
4. Auto-approve creates an immediately usable accepted relationship.

### C. Pilot live start by visibility

Confirm `POST /api/v2/live/session/start` succeeds for:

- `off`
- `followers`
- `public`

### D. Public visibility behavior

When pilot starts with `public`:

1. Session appears in public browse / active list.
2. Public read by session path works.
3. Public read by share-code path works.
4. Share code exists.
5. Signed-in authenticated watch also works if the app path uses it.

### E. Followers visibility behavior

When pilot starts with `followers`:

1. Session does not appear in public browse / public active list.
2. Public read by session path fails.
3. Public read by share-code path fails.
4. No public share code is issued.
5. Approved follower can discover the live session through the authenticated path.
6. Approved follower can open watch successfully.
7. Unrelated signed-in non-follower cannot watch.
8. Pending requester cannot watch.

### F. Off visibility behavior

When pilot starts with `off`:

1. Session does not appear publicly.
2. Session does not appear to followers.
3. Only the owner can read it through the authenticated path.

### G. Visibility switching while live

Run all of:

- `public -> followers`
- `followers -> public`
- `public -> off`
- `off -> followers`

For each switch confirm:

1. Access changes take effect quickly.
2. Public list/read behavior updates correctly.
3. Share code exists only while the session is public.

### H. Existing write lane preservation

For an authenticated private-follow session, verify the existing write-token
lane still works:

1. `POST /api/v1/position`
2. `POST /api/v1/task/upsert`
3. `POST /api/v1/session/end`

Confirm:

- positions update correctly
- task data remains usable in watch
- session end behaves correctly

### I. Deterministic owned-session behavior

1. Start one authenticated owned session.
2. Start a second authenticated owned session.
3. Confirm the earlier non-ended owned session is ended automatically.

## Stop Conditions

Pause rollout if any of these occur:

- public `/api/v1/live/*` behavior regresses
- Google sign-in or token exchange is unstable
- non-public sessions leak into the public lane
- authorized followers cannot reliably watch follower-only sessions
- release build still exposes the configured dev account path

## Completion Rule

This guide is complete only when every section above has a recorded pass/fail
result against the actual staging environment.
