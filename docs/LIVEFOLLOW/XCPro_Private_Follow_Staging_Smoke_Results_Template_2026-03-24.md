# XCPro Private Follow — Staging Smoke Results Template
## Record pass/fail against the actual staging environment

**Status:** Fill during staging execution  
**Purpose:** Record the smoke outcomes in a consistent format.  
**Owner:** Use together with `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`.

## Test Metadata

- **Date:** `__________________`
- **Operator:** `__________________`
- **Server commit/tag:** `b696f039540480468195087fa3f44338338f6fba / private-follow-rollout-rc-server-2026-03-24`
- **App commit/tag:** `c25d0f6520686643c2670502048fee3a83b91ca9 / private-follow-rollout-rc-app-2026-03-24`
- **Staging environment URL/host:** `__________________`
- **Real device used:** `__________________`

## Preflight

- **Env preflight result:** `PASS / FAIL`
- **Notes:** `__________________________________________________________`

## A. Auth and profile

| Check | Result | Evidence / notes |
|---|---|---|
| Google sign-in completes on real device |  |  |
| `/api/v2/auth/google/exchange` returns XCPro bearer token |  |  |
| `GET /api/v2/me` succeeds |  |  |
| Signed-out `/api/v2/*` fails with `401` |  |  |

## B. Relationship baseline

| Check | Result | Evidence / notes |
|---|---|---|
| Viewer can search for pilot |  |  |
| Viewer can send follow request |  |  |
| Pilot can accept request |  |  |
| Auto-approve creates usable relationship |  |  |

## C. Pilot live start by visibility

| Visibility | Result | Evidence / notes |
|---|---|---|
| `off` |  |  |
| `followers` |  |  |
| `public` |  |  |

## D. Public visibility behavior

| Check | Result | Evidence / notes |
|---|---|---|
| Session appears in public browse / active list |  |  |
| Public read by session path works |  |  |
| Public read by share-code path works |  |  |
| Share code exists |  |  |
| Signed-in authenticated watch also works |  |  |

## E. Followers visibility behavior

| Check | Result | Evidence / notes |
|---|---|---|
| Session does not appear in public browse |  |  |
| Public read by session path fails |  |  |
| Public read by share-code path fails |  |  |
| No public share code is issued |  |  |
| Approved follower can discover live session |  |  |
| Approved follower can open watch |  |  |
| Non-follower cannot watch |  |  |
| Pending requester cannot watch |  |  |

## F. Off visibility behavior

| Check | Result | Evidence / notes |
|---|---|---|
| Session does not appear publicly |  |  |
| Session does not appear to followers |  |  |
| Only owner can read through authenticated path |  |  |

## G. Visibility switching while live

| Switch | Result | Evidence / notes |
|---|---|---|
| `public -> followers` |  |  |
| `followers -> public` |  |  |
| `public -> off` |  |  |
| `off -> followers` |  |  |

## H. Existing write lane preservation

| Check | Result | Evidence / notes |
|---|---|---|
| `POST /api/v1/position` still works |  |  |
| `POST /api/v1/task/upsert` still works |  |  |
| `POST /api/v1/session/end` still works |  |  |

## I. Deterministic owned-session behavior

| Check | Result | Evidence / notes |
|---|---|---|
| Starting a second owned session ends the earlier non-ended owned session |  |  |

## Final Decision

- **Recommendation:** `GO / PAUSE / FIX SPECIFIC BLOCKER`
- **Blocking issue if not GO:** `__________________________________________________________`
- **Notes / screenshots / logs location:** `__________________________________________________________`
