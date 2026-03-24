# XCPro Private Follow — Rollout & Release Checklist
## Staging validation, deployment sequencing, smoke tests, rollback, and doc follow-through

**Status:** Approved rollout/release checklist  
**Scope:** Roll out the private-follow lane from repo code to deployed environments in a controlled way.  
**Non-goal:** No new feature work in this pass. Do not expand scope into notifications, follower/following list screens, blocks, watcher counts, or broader social polish.

## Goal
The private-follow lane is now implemented in repo across:
- XCPro account/auth foundation
- relationship search/request/accept/decline/auto-approve
- follower-only live entitlement
- authenticated v2 live start/read/discovery
- public v1 live gating so non-public sessions do not leak

The goal of this checklist is to make rollout safe and verifiable.

This rollout must preserve the current public LiveFollow lane while turning on the private-follow lane in deployed environments.

## Current product / architecture understanding
This feature is about **which signed-in XCPro viewers can see which pilot's live XCPro flight while that pilot is actively flying**.

Important distinctions that must remain true during rollout:
- `Allow all XCPro followers` means auto-approve signed-in XCPro viewers.
- `Public` remains a separate live-visibility mode.
- Public `/api/v1/live/*` remains available for public sessions.
- Non-public sessions must not leak into public browse/read paths.
- The existing v1 write-token upload lane remains in use for position/task/end.

## Known implemented repo behavior to preserve
The rollout should assume repo code already contains:
- Google sign-in via Credential Manager on Android
- server token exchange at `POST /api/v2/auth/google/exchange`
- bearer-authenticated `/api/v2/*`
- profile/privacy foundation
- follow request / accept / decline / auto-approve flow
- `owner_user_id` on live sessions
- per-session `visibility = off | followers | public`
- authenticated v2 live start/read/discovery
- non-public sessions hidden from public v1 reads
- public `share_code` returned only for public sessions
- deterministic owned-session behavior: authenticated start ends any existing non-ended owned session before creating the next one

## Release principles
- **Server first, then client.**
- **Migrations before app dependence.**
- **Do not break public v1 LiveFollow.**
- **Do not treat repo-only behavior as deployed truth until staging/prod verification is complete.**
- **Do not merge rollout uncertainty into product docs silently.**

## Task 1 — Freeze the release scope
Before any deployment work:
- lock the server commit/branch intended for rollout
- lock the Android app commit/branch intended for rollout
- confirm no unrelated partial private-follow work is bundled into the same deploy unless intentionally approved
- confirm no unfinished feature flags or debug shortcuts remain exposed in release builds

### Required scope for this rollout
Included:
- account/auth exchange path used by private-follow
- profile/privacy state required by private-follow
- relationship endpoints and app surfaces already implemented
- follower-only live entitlement and authenticated live discovery/watch
- public-lane gating for non-public sessions

Excluded:
- push notifications
- follower/following list screens if still missing
- remove follower / block flows if still missing
- watcher counts
- broader UI redesign

## Task 2 — Confirm environment and external setup
Private-follow rollout is blocked unless the real environment setup is complete.

### Android / app config
Confirm release/staging builds have the correct Google server client ID wiring.

Required Android secret/config:
- `XCPRO_GOOGLE_SERVER_CLIENT_ID`

### Server env vars
Confirm deployed server environment has the required values:
- `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS`
- `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET`
- optional `XCPRO_PRIVATE_FOLLOW_BEARER_TTL_SECONDS`

### External Google setup
Confirm the Google OAuth client configuration matches the Credential Manager Google sign-in flow and the server audience validation.

### Required pass condition
Do not proceed unless:
- Android config matches the server audience expectation
- the server can verify real device Google ID tokens in the target environment
- bearer-token signing secret is set and stable

## Task 3 — Confirm database migration readiness
Before server deploy, confirm all required private-follow migrations exist, are ordered correctly, and are ready to apply.

### At minimum verify these are included
- Phase 1 account/profile/privacy schema migrations
- `7c2d6f9b1e4a_add_private_follow_phase2_relationship_tables.py`
- `9f4a6d2c1b7e_add_live_session_owner_and_visibility.py`

### Required checks
- migration history is linear and sane in the target environment
- no manual DB hotfix is required to make the app work
- session visibility columns/defaults are valid for existing rows
- rollout will not strand existing public sessions in an invalid state

### Strong recommendation
Take a DB backup or snapshot before production migration if that is part of your normal operational pattern.

## Task 4 — Server-first staging deploy
Deploy the server to staging before any staging client build depends on it.

### Staging server release must include
- auth exchange endpoint
- private-follow relationship endpoints
- live session ownership + visibility support
- authenticated `/api/v2/live/*` endpoints
- public-lane gating

### Required staging server checks
Verify in staging, before using a new app build:
- server starts cleanly
- migrations apply cleanly
- existing public endpoints still respond
- `/api/v2/auth/google/exchange` works with a real staging-capable client token
- `/api/v2/*` returns `401` for missing/invalid auth

## Task 5 — Run pre-release automated verification again
Even if tests already passed locally, rerun the approved verification commands on the release candidate you actually intend to ship.

### Server
Run:
- `python -m unittest app.tests.test_livefollow_api`

### Android app
Run:
- `./gradlew.bat enforceRules`
- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

### If your release flow has a release build verification path
Run that too and report it explicitly.

### Fail the rollout if
- any test suite fails
- the app no longer assembles cleanly
- public LiveFollow regression appears in automated checks

## Task 6 — Staging smoke test matrix
Run these tests on a staging environment with at least:
- one signed-in pilot account
- one approved follower account
- one non-follower signed-in account
- one signed-out/public viewer path

### A. Auth and profile
1. signed-in user can complete Google sign-in
2. server exchange returns an XCPro bearer token
3. `/api/v2/me*` works for the signed-in user
4. signed-out access to `/api/v2/*` fails cleanly with `401`

### B. Relationship baseline
1. viewer can search the pilot
2. viewer can send follow request when approval is required
3. pilot can accept request
4. auto-approve pilot setting creates a usable accepted relationship without manual acceptance

### C. Pilot live start by visibility
Test all three:
- `off`
- `followers`
- `public`

For each mode confirm session start succeeds.

### D. Public visibility behavior
When pilot starts with `public`:
- session appears in public browse / active list
- public read by session/share path works
- share code exists
- signed-in authenticated watch also works if supported by the app path

### E. Followers visibility behavior
When pilot starts with `followers`:
- session does **not** appear in public browse / public active list
- public read by session/share path fails
- no public share code is issued
- approved follower can discover the live session through the authenticated path
- approved follower can open watch successfully
- unrelated signed-in non-follower cannot watch
- pending requester cannot watch

### F. Off visibility behavior
When pilot starts with `off`:
- session does not appear publicly
- session does not appear to followers
- only the owner can read it through the authenticated path

### G. Visibility switching while live
Start in one mode and switch live session visibility while the session remains active.

Test:
- `public -> followers`
- `followers -> public`
- `public -> off`
- `off -> followers`

For each switch confirm:
- access changes take effect quickly
- public list/read behavior updates correctly
- share code is created only when session becomes public
- share code is removed/invalidated when session becomes non-public

### H. Existing write lane preservation
For a session started through the authenticated private-follow path, verify the existing write-token path still works:
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

Confirm:
- positions update correctly
- task overlay/task data remains usable in watch
- session end behaves correctly

### I. Deterministic owned-session behavior
Verify the implemented rule:
- starting a new authenticated session ends any existing non-ended owned session first

Confirm the resulting behavior is deterministic and acceptable in staging.

## Task 7 — Production deploy order
Use this order unless there is a better already-proven internal release pattern.

### Step 1 — Server
- deploy server code
- apply migrations
- verify environment variables
- run immediate server smoke checks

### Step 2 — Staging/limited production validation
- verify real-device sign-in
- verify private-follow live entitlement on the deployed server
- verify public v1 lane remains healthy

### Step 3 — Client rollout
- only roll out the app build after the server is confirmed healthy
- use staged rollout if available
- be ready to pause rollout if auth exchange or entitlement fails in the real environment

## Task 8 — Production smoke tests immediately after deploy
Run these as soon as the server is live and again after the client build reaches a real test device in production-like conditions.

### Minimum production smoke test list
1. public pilot starts a public session
2. public session appears in public browse
3. public watch still works via the existing public path
4. signed-in pilot starts a `followers` session
5. approved follower can see/watch it
6. non-follower cannot see/watch it
7. pilot can switch `followers -> public`
8. pilot can switch `public -> followers`
9. signed-in pilot can still upload positions/task/end through the existing write-token path
10. no obvious regression in existing Friends Flying public browse/watch flow

## Task 9 — Monitoring and support watch
For the first rollout window, actively watch for:
- auth exchange failures
- Google audience / issuer mismatches
- bearer-token verification failures
- migration errors
- visibility-state mismatches
- public sessions disappearing unexpectedly
- non-public sessions leaking publicly
- watch polling failures in the authenticated path
- app crashes around sign-in, browse tabs, or watch routing

### If you have logging/analytics/alerts
Add or watch signals around:
- `/api/v2/auth/google/exchange` success/failure counts
- `/api/v2/live/session/start` success/failure counts
- unauthorized live-read attempts
- public-lane `session_not_visible` or equivalent failures
- client watch-load failures

## Task 10 — Rollback plan
Have a rollback plan before production rollout begins.

### Server rollback considerations
- know whether migrations are backward-compatible with the previous server build
- if not fully reversible, be explicit about the lowest-risk rollback path
- be prepared to disable or pause client rollout if server rollback is partial only

### Client rollback considerations
- if the new client expects private-follow endpoints that are unhealthy, pause staged rollout immediately
- if necessary, fall back to a build that still preserves the public lane without depending on the new private-follow behavior

### Product priority during rollback
If forced to choose, preserve:
1. public LiveFollow health
2. basic app stability
3. private-follow functionality

## Task 11 — Docs update after verified deployment
Only after staging/prod verification is complete, update docs to reflect reality.

### Required rule
Do **not** rewrite public deployed-contract docs just because repo code exists.

### After verified deployment, update as appropriate
- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md`
  - note that the feature is not only implemented in repo but deployed in the verified environment if that becomes true
- `docs/LIVEFOLLOW/README_current.md`
  - update navigation if the current status wording needs to reflect deployment
- any release notes / setup note if environment requirements changed during rollout

### Important contract ownership rule
`docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md` remains the public deployed contract owner unless and until you intentionally decide to publish a verified deployed contract owner for the authenticated private-follow lane.

Do not blur:
- public deployed contract reality
- private-follow repo state
- private-follow proposal docs

## Task 12 — Final pass/fail gate
Approve the rollout only if all of the following are true:
- server migrations applied cleanly
- Google sign-in + server exchange works in the real target environment
- authenticated `/api/v2/*` works with real bearer auth
- public v1 LiveFollow still works
- follower-only sessions do not leak publicly
- approved followers can watch follower-only sessions
- unauthorized users cannot watch follower-only sessions
- visibility switching works while live
- write-token upload/task/end still works
- no critical crash/regression exists in the app
- docs still describe reality honestly

Reject or pause the rollout if any of the following are true:
- public v1 LiveFollow regressed
- sign-in / token exchange is unstable in the target environment
- sessions leak across visibility boundaries
- authenticated viewers cannot reliably watch authorized sessions
- the app build depends on server behavior that is not yet healthy in production

## Report back expected from Codex / release owner
When rollout validation is complete, report back with:
- exact server commit/build deployed
- exact app commit/build deployed
- migrations applied
- environment/config confirmed
- staging smoke-test results
- production smoke-test results
- any issues found and how they were resolved
- whether rollout was completed, paused, or rolled back
- exact docs updated after deployment verification
