# XCPro Private Follow — Phase 4C Codex Brief
## Block / unblock, search suppression, and immediate access removal

**Date:** 2026-03-25  
**Status:** Planned implementation brief. Start only after Phase 4B is approved and rollout is green.  
**Scope:** Add block/unblock with correct cross-cutting behavior across search, relationship state, and follower-only live entitlement.  
**Non-goal:** Do not add notifications, watcher counts, email discovery, or public-watch sign-in changes.

## Goal
Close the missing safety boundary for private-follow.

When this phase is complete, blocking should:
- remove both follow directions
- remove pending requests
- hide users from each other's search and list/profile surfaces
- immediately remove follower-only live visibility between the blocked pair
- surface a sane blocked-state UI without leaking unnecessary details

## Current reality to preserve
Preserve all already-working behavior in:
- public `/api/v1/live/*`
- private-follow account/profile/search/request/live-entitlement flows
- follower/following list screens and relationship controls from Phases 4A and 4B

## Required product rules
Implement these rules exactly unless intentionally changed:
- blocked users do not see each other in search
- blocking removes both directions of follow plus pending requests
- blocking immediately removes follower-only live visibility
- block should be idempotent
- quiet decline / quiet blocked handling is preferred over punitive feedback

## Preferred server surface
Add a minimal explicit block surface.

### Preferred additions
- `POST /api/v2/blocks`
- `DELETE /api/v2/blocks/{user_id}`

Or equivalent repo-consistent endpoints if the existing routing pattern strongly prefers a different shape.

### Required server behavior
On block:
- create the block edge idempotently
- remove any follow edges between the pair
- remove any pending follow requests between the pair
- ensure search excludes the pair
- ensure follower-only live reads/discovery no longer grant access

On unblock:
- remove the block edge only
- do not silently restore old follow relationships or requests

## Required app work
Add block/unblock controls in the right place.

### Preferred surfaces
- pilot profile / account management surface
- row action sheet from follower/following/search results if consistent with repo UX

### Required UI behavior
- blocked users should not appear in search results for each other
- blocked relationship state should be handled explicitly if a direct path still reaches the profile
- unblock should be possible from a sensible management surface

## Task 1 — Audit cross-cutting effects
Before coding, inspect:
- current search query/filter path
- current relationship-state computation
- current authenticated live entitlement checks
- current follower/following list query path

Report what cross-cutting points were touched.

## Task 2 — Add block storage and endpoints
Server-side:
- add blocks storage if still missing
- add create/delete block endpoints
- make create idempotent
- make delete idempotent or clearly not-found safe per repo conventions

## Task 3 — Enforce block effects everywhere required
Server-side:
- remove follow edges and pending requests on block
- update search filters
- update list reads
- update authenticated live read/discovery authorization

App-side:
- reflect blocked state correctly
- remove or hide actions that no longer make sense

## Task 4 — Keep notifications out of scope
Do **not** add in this phase:
- push token plumbing
- notification delivery
- live-now alerts
- watcher counts
- email discovery

## Task 5 — Add tests
### Server tests
Add coverage for:
- block idempotency
- unblock behavior
- follow edges removed on block
- pending requests removed on block
- blocked users removed from search
- blocked users cannot read follower-only live sessions
- unblock does not restore old relationships

### App tests
Add coverage for:
- block action path
- unblock action path
- blocked state UI handling
- search/list suppression behavior where testable in app state

## Task 6 — Update docs
Update the private-follow repo-state doc and any other current docs needed to reflect the new safety boundary.

Do not rewrite the public deployed contract owner.

## Constraints
- implement Phase 4C only
- preserve public LiveFollow
- preserve non-blocked private-follow behavior
- no notifications
- no watcher counts

## Acceptance criteria
Phase 4C is complete when:
- block/unblock exists and is correct
- block immediately removes relationship + pending request + follower-only live access
- blocked users do not see each other in search
- current public lane remains unaffected
- tests and docs are updated

## Report back expected from Codex
When done, report back with:
- exact block endpoints/storage added
- exact cross-cutting effects implemented
- exact tests run
- exact docs updated
- anything intentionally deferred to Phase 4D
