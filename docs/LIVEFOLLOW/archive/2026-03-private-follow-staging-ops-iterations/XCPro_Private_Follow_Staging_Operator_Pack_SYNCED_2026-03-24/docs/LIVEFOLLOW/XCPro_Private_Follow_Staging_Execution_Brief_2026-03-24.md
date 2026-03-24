# XCPro Private Follow — Staging Execution Brief
## Real environment setup, server-first staging deploy, real-device auth proof, and smoke execution

**Status:** Approved staging-execution brief  
**Scope:** Execute the next real-world step for XCPro private-follow: staging environment setup, staging deployment, real-device Google exchange proof, and the staging smoke matrix.  
**Non-goal:** No new feature work, no docs cleanup pass, no proposal/design changes, no public deployed-contract rewrite.

## Goal
Repo-side private-follow implementation and rollout hardening are done.

The remaining blockers are now operational:
- real staging environment variables and secrets
- real Google OAuth audience alignment
- real-device Google sign-in and server exchange proof
- server-first staging deploy
- staging smoke execution

The goal of this brief is to get XCPro from "repo-ready" to a real staging go/no-go decision.

## Release candidates to use
Use only these candidates for this pass.

### Server RC
- commit: `b696f039540480468195087fa3f44338338f6fba`
- tag: `private-follow-rollout-rc-server-2026-03-24`

### App RC
- commit: `c25d0f6520686643c2670502048fee3a83b91ca9`
- tag: `private-follow-rollout-rc-app-2026-03-24`
- clean frozen worktree: `C:/Users/Asus/AndroidStudioProjects/XCPro_private_follow_rollout_rc_app`

Do not introduce new code changes unless staging finds a real bug that must be fixed to proceed.

## Current operator docs to follow
Treat these as the active supporting docs during execution:
- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md`
- `docs/LIVEFOLLOW/Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`

## Task 1 — Freeze scope and confirm the exact artifacts
Before touching staging:
- confirm the server deploy will use the pinned server RC commit/tag above
- confirm the Android installable build will use the pinned app RC commit/tag above
- confirm no unrelated local worktree changes are part of the release artifact
- confirm no one is continuing feature work on top of these candidates for this pass

### Required output
Report back:
- exact server commit/tag being deployed
- exact app commit/tag/build being tested
- whether both are clean/frozen artifacts

## Task 2 — Set staging environment values
On the staging server, configure the required env vars.

### Required
- `XCPRO_RUNTIME_ENV=staging`
- `XCPRO_GOOGLE_SERVER_CLIENT_ID` or `XCPRO_GOOGLE_SERVER_CLIENT_IDS`
- `XCPRO_PRIVATE_FOLLOW_BEARER_SECRET`

### Optional
- `XCPRO_PRIVATE_FOLLOW_BEARER_TTL_SECONDS`

### Must not be enabled in staging
- `XCPRO_ALLOW_DEV_STATIC_BEARER_AUTH`

That should be unset or explicitly `0`.

### Important rule
The Android build must use the same Google server client ID that the staging server expects for audience verification.

## Task 3 — Run staging env preflight
On the staging server, run the env preflight script:

```bash
python scripts/private_follow_env_preflight.py
```

### Pass condition
Do not continue until the preflight passes in the actual staging environment.

### Fail condition
If it fails, report exactly:
- which env vars are missing or invalid
- whether the Google audience expectation is mismatched
- whether any dev-mode auth setting is still enabled unexpectedly

## Task 4 — Deploy the pinned server RC to staging first
Deploy the server before testing the new app build against staging.

### Required steps
- deploy the pinned server RC
- apply Alembic migrations
- verify service starts cleanly
- verify the migration chain reaches head cleanly in the target environment

### Required quick checks after deploy
Verify:
- public `/api/v1/live/*` endpoints still respond for public sessions
- `/api/v2/*` returns `401` for missing/invalid auth
- the server is using the intended runtime env and secrets

## Task 5 — Prove real-device Google sign-in and token exchange
Use a real Android device with the pinned app RC installed.

### Required proof points
Verify:
1. Google sign-in succeeds on the device
2. the app receives a Google ID token
3. `POST /api/v2/auth/google/exchange` succeeds against staging
4. the app receives a valid XCPro bearer token
5. authenticated `/api/v2/me` works after exchange

### Hard rule
If this real-device auth proof fails, stop. Do not continue to the staging smoke matrix until this works.

## Task 6 — Run the staging smoke matrix end to end
Use the staging smoke guide doc for the detailed flow.

### Minimum test actors
- one pilot account
- one approved follower account
- one signed-in non-follower account
- one signed-out/public viewer path

### Required visibility modes to test
- `public`
- `followers`
- `off`

### Required live switching tests
- `public -> followers`
- `followers -> public`
- `public -> off`
- `off -> followers`

### Required behavior to verify
#### Public mode
- session appears in public browse/active list
- public read/watch works
- public share code exists

#### Followers mode
- session does not appear in public browse/active list
- public read/watch does not work
- no public share code is issued
- approved follower can discover and watch
- unrelated signed-in non-follower cannot watch
- pending requester cannot watch

#### Off mode
- session is owner-only
- it is not visible publicly
- it is not visible to followers

#### Existing write path
For a session started through the authenticated path, verify the existing v1 write-token lane still works:
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

#### Deterministic ownership behavior
Verify the implemented rule that starting a new authenticated session ends any existing non-ended owned session first.

## Task 7 — Make a go / no-go call for production rollout
Approve production rollout only if all of the following are true:
- staging env preflight passes
- real-device Google exchange works
- staging smoke matrix passes
- public v1 LiveFollow remains healthy
- follower-only sessions do not leak publicly
- approved followers can watch
- unauthorized viewers cannot watch
- no critical app regression or crash appears

Pause rollout if any of those fail.

## Task 8 — If staging passes, use this production order
If the staging pass is clean:
1. deploy the pinned server RC to production
2. apply migrations
3. run production smoke on the server/public behavior
4. release the pinned app RC in a staged rollout
5. run production smoke again on the full path
6. monitor auth exchange, visibility, and watch failures closely

## Task 9 — Keep docs honest
Do not rewrite the public deployed contract owner just because repo code exists.

Only after verified staging/production proof should docs be updated to reflect actual deployed status.

### For now
Keep these distinctions intact:
- public deployed contract owner = `LiveFollow_Current_Deployed_API_Contract_v3.md`
- private-follow repo implementation owner = `Private_Follow_Current_Repo_State_2026-03-24.md`
- rollout execution owners = rollout checklist + staging smoke guide

## Constraints
- no new feature work
- no docs cleanup pass in this task
- no public deployed-contract rewrite
- no server/app behavior changes unless staging reveals a real blocking bug
- do not deploy the app before server staging proof exists
- keep public `/api/v1/live/*` healthy

## Acceptance criteria
This staging-execution pass is complete when:
- the staging env is configured correctly
- env preflight passes in staging
- the pinned server RC is deployed to staging
- real-device Google exchange works
- the staging smoke matrix passes or clearly identifies the exact remaining blocker
- a clean go/no-go recommendation exists for production rollout

## Report back expected
When done, report back with:
- exact staging server env status
- env preflight result
- exact server commit/tag deployed to staging
- exact app commit/tag/build installed on device
- real-device Google exchange result
- staging smoke results by scenario
- final recommendation: `go`, `pause`, or `fix specific blocker`
- exact docs updated, if any, after verified staging execution
