# XCPro Private Follow — Phase 4B Codex Brief
## Cancel request, unfollow, remove follower, and relationship management polish

**Date:** 2026-03-25  
**Status:** Planned implementation brief. Start only after Phase 4A is approved and the current rollout is green.  
**Scope:** Add the missing relationship management controls without adding block/unblock or notifications.  
**Non-goal:** Do not add block/unblock, push notifications, watcher counts, email discovery, or public-watch sign-in changes.

## Goal
Make the follow graph manageable in-app.

When this phase is complete, a signed-in XCPro user should be able to:
- cancel an outgoing pending follow request
- unfollow an approved relationship
- remove a follower without blocking them
- follow back from the follower surfaces and reach `mutual` where appropriate

## Current reality to preserve
Preserve:
- current public `/api/v1/live/*`
- current authenticated `/api/v2/*`
- current account/profile/privacy behavior
- current follower-only live entitlement
- current follower/following list screens from Phase 4A

## Required product rules
Implement these rules exactly unless the brief is intentionally changed:
- a pilot can remove a follower later without blocking them
- counts count approved relationships only
- follow-back should still result in `mutual` without creating a separate `friends` table
- remove follower is not the same as block

## Preferred server surface
Keep the new mutation surface small and explicit.

### Preferred additions
Choose endpoint shapes consistent with existing repo patterns, but the resulting behavior must support:
- cancel outgoing pending request
- unfollow approved relationship
- remove follower without block

Preferred examples:
- `POST /api/v2/follow-requests/{id}/cancel`
- `DELETE /api/v2/users/{user_id}/follow`
- `POST /api/v2/followers/{user_id}/remove`

If the repo already has a better pattern, use it and document the final choice clearly.

## Required app work
Add clean relationship controls in the new list/profile surfaces.

### Minimum app behaviors
- from `Outgoing Requests`, user can cancel a pending request
- from `Following`, user can unfollow
- from `Followers`, owner can remove follower
- relationship state and counts update correctly after each mutation

### UX rule
Use soft confirmation only where really needed. Avoid turning basic list management into modal overload.

## Task 1 — Audit current relationship actions and state transitions
Before coding, inspect:
- the current follow-request create/accept/decline flow
- state transitions currently used in repository/viewmodel/UI models
- where list rows and profile actions can host these controls with minimal churn

Report what was reused.

## Task 2 — Add relationship management mutations
Server-side:
- add the mutation endpoints needed for cancel/unfollow/remove follower
- enforce caller authorization correctly
- update relationship state deterministically
- keep counts correct
- do not add block semantics here

## Task 3 — Wire app-side controls
App-side:
- add cancel / unfollow / remove follower actions in the right surfaces
- update rows and counts optimistically only if the repo already handles that safely; otherwise use explicit reloads
- keep UI state clear after success/failure

## Task 4 — Keep block out of scope
Do **not** add in this phase:
- block / unblock
- search suppression based on block
- live-entitlement hard removal by block
- push notifications
- watcher counts

If you discover that block is required to make remove follower work, stop and report it. Do not quietly merge Phase 4C into this phase.

## Task 5 — Add tests
### Server tests
Add coverage for:
- cancel outgoing request
- unfollow approved relationship
- remove follower without block
- count updates after each mutation
- authorization failures for callers who should not mutate the relationship

### App tests
Add coverage for:
- cancel action from outgoing requests
- unfollow action from following list
- remove follower action from follower list
- resulting state/count updates
- error handling for failed mutations

## Task 6 — Update docs
Update at minimum:
- private-follow repo-state doc
- README if current operational/reference navigation needs to mention the new screens/controls

Do not change the public deployed contract owner.

## Constraints
- implement Phase 4B only
- preserve Phase 4A surfaces
- no block/unblock
- no notifications
- no watcher counts
- no public-lane changes

## Acceptance criteria
Phase 4B is complete when:
- outgoing requests can be cancelled
- approved relationships can be unfollowed
- followers can be removed without blocking
- counts update correctly
- public LiveFollow is unaffected
- current private-follow live entitlement is unaffected
- tests and docs are updated

## Report back expected from Codex
When done, report back with:
- exact endpoints added
- exact app controls added
- exact state/count rules enforced
- exact tests run
- exact docs updated
- anything intentionally deferred to Phase 4C
