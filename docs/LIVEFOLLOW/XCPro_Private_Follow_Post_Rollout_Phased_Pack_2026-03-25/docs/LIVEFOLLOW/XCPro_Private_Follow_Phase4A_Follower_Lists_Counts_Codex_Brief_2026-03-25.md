# XCPro Private Follow — Phase 4A Codex Brief
## Followers / Following lists, counts, visibility enforcement, and follow-back entry paths

**Date:** 2026-03-25  
**Status:** Approved implementation brief for the next post-rollout private-follow phase. Do **not** start until the current rollout has a clear `go`.  
**Scope:** Add follower/following list reads, counts, and app screens using the existing private-follow relationship graph.  
**Non-goal:** Do not add block/unblock, remove follower, unfollow, cancel request, push notifications, watcher counts, email-based discovery, or public-watch sign-in changes.

## Goal
Make the relationship graph visible and usable without changing the current auth/live-entitlement foundation.

When this phase is complete, a signed-in XCPro user should be able to:
- see follower/following counts
- open `Followers` and `Following` screens
- understand the relationship state of each row
- optionally follow back from a `followed_by` row using the existing request/follow path
- have full list visibility correctly enforced by `connection_list_visibility`

## Current reality to preserve
Preserve all current behavior that already exists in repo:
- Google sign-in and `/api/v2/auth/google/exchange`
- current `/api/v2/me*`
- current follow-request create/incoming/outgoing/accept/decline flow
- current follower-only live entitlement and `off | followers | public`
- current public `/api/v1/live/*` lane
- current v1 write-token upload/task/end path

Do not break public LiveFollow.

## Product rules to preserve
- counts and full lists are not the same concept
- full lists are governed by `connection_list_visibility`
- follow policy and live visibility remain separate
- `Allow all XCPro followers` still means auto-approve signed-in followers, not public live access
- follow-back should create `mutual` without inventing a separate `friends` primitive

## Preferred server surface
Use the existing `/api/v2/*` lane and keep the new surface minimal.

### Required endpoints
Preferred shapes:
- `GET /api/v2/users/{user_id}/followers`
- `GET /api/v2/users/{user_id}/following`

Optional if it fits the repo better:
- `GET /api/v2/me/followers`
- `GET /api/v2/me/following`

### Required payload behavior
For each row, include enough data to render:
- user identity (`user_id`, `handle`, `display_name`, optional `comp_number`, avatar/initials if already supported)
- relationship summary from the caller's point of view
- counts if the surface needs them

### Required list visibility behavior
Enforce `connection_list_visibility` exactly:
- `owner_only`: only the owner can read full lists
- `mutuals_only`: owner always can; non-owner only if they are mutual with the target
- `public`: any signed-in caller may read the list

### Counts behavior
Counts may still be visible even when full lists are not. If the repo already surfaces counts somewhere else, keep that behavior consistent.

### Pagination rule
If the repo already uses cursor or page-based list pagination patterns, follow them. If not, keep the first version simple but explicit and documented.

## Required app work
### Navigation / entry points
Add a clean entry path to:
- `Followers`
- `Following`

Prefer to place this within the existing private-follow / Manage Account area rather than redesigning the public Friends Flying flow.

### Screens
Build:
- `FollowersScreen`
- `FollowingScreen`

Each row should show:
- display name
- handle
- optional comp number
- relationship state
- optional count context only if the surface already has it

### Follow-back path
If a row is `followed_by`, the user should have a clear path to follow back.

Preferred implementation:
- reuse the existing create-follow-request flow
- let target follow policy determine whether the result is immediate or pending

Do not invent a new server primitive for follow-back.

### Loading / empty / error states
Handle all three clearly.

### Signed-out rule
Signed-out users should not see broken private-follow list surfaces.

## Task 1 — Audit current relationship and profile code paths
Before coding, inspect:
- the current account/profile repository and DTOs
- the current relationship request/search surfaces
- where counts are already rendered, if anywhere
- whether there is already a reusable list-row UI model

Report what was reused.

## Task 2 — Add follower/following list read endpoints
Server-side:
- add the required list read endpoints
- add any repository/data-access helpers needed
- compute relationship summary for each returned row
- enforce list visibility rules correctly
- ensure blocked-state assumptions are not invented in this phase if block is not implemented yet

## Task 3 — Surface counts cleanly
If follower/following counts are not already available where needed, add them in the minimal consistent place.

Preferred options:
- current-user profile payload
- profile summary payload used by list screens
- search/profile models if needed

Do not add watcher counts.

## Task 4 — Build app list screens
App-side:
- add view models and UI state for followers/following screens
- add repositories/use cases if needed to match repo patterns
- render rows with relationship state
- allow navigation from the existing account/private-follow area

## Task 5 — Add follow-back entry path
App-side:
- where a user is in `followed_by`, provide a clear action to follow back
- reuse the existing follow-request/create path
- update row state correctly after success

Server-side:
- no new special follow-back endpoint unless absolutely necessary

## Task 6 — Keep scope disciplined
Do **not** add in this phase:
- unfollow
- cancel request
- remove follower
- block / unblock
- notifications
- push token plumbing
- watcher counts
- email-based discovery
- sign-in requirement for the public lane

If you find that one of those is required to make this phase coherent, stop and report it instead of quietly expanding scope.

## Task 7 — Add tests
### Server tests
Add coverage for:
- follower list read by owner
- following list read by owner
- `owner_only` visibility enforcement
- `mutuals_only` visibility enforcement
- `public` visibility enforcement
- counts behavior where surfaced
- relationship-state correctness in returned rows

### App tests
Add coverage for:
- followers screen load/empty/error states
- following screen load/empty/error states
- row rendering for `following`, `followed_by`, `mutual`, `outgoing_pending`
- follow-back happy path using the existing follow request flow
- signed-out/private-follow gating where touched

Run repo-native verification.

## Task 8 — Update docs
After implementation, update at minimum:
- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md` (or a date-bumped successor if the repo uses that pattern)
- `docs/LIVEFOLLOW/README_current.md` if the active operator/reference navigation needs it

Do **not** rewrite the public deployed contract owner doc as if this private-follow behavior is public deployed reality.

## Constraints
- implement Phase 4A only
- preserve the current public lane
- preserve current private-follow live behavior
- do not invent watcher counts
- do not invent raw-email search
- do not change current auth decisions
- follow existing repo patterns and AGENTS guidance

## Acceptance criteria
Phase 4A is complete when:
- a signed-in user can read followers and following lists where allowed
- follower/following counts are surfaced where intended
- list visibility rules are enforced correctly
- a `followed_by` relationship has a clear follow-back path
- the public LiveFollow lane is unaffected
- current follower-only live entitlement is unaffected
- tests were added and passed
- docs were updated honestly

## Report back expected from Codex
When done, report back with:
- exact endpoints added
- exact app screens/flows added
- exact count surfaces added or changed
- exact visibility rules enforced
- exact tests run
- exact docs updated
- anything intentionally deferred to Phase 4B
