# XCPro Private Follow — Post-Rollout Low-Risk IP
## Phased plan for the remaining private-follow backlog after staging/prod rollout is green

**Date:** 2026-03-25  
**Status:** Planned implementation path. Do **not** start coding until the current private-follow rollout has a clear `go` result from staging.  
**Scope:** Turn the remaining not-yet-implemented private-follow backlog into a low-risk sequence of app + server slices.  
**Non-goal:** Do not reopen the already-shipped account/follow/live-entitlement foundation. Do not mix these phases into the current rollout pass.

## Why this plan exists
The current private-follow repo-state says these items still remain out of scope or not yet implemented:
- full follower/following list screens
- block/remove follower controls
- watcher counts
- email-based discovery
- push notifications
- requiring sign-in for the public watch lane
- rollout sequencing between server migration/deploy and client rollout

The current rollout/release docs also explicitly exclude new feature work like follower/following list screens, blocks, and watcher counts from the rollout pass.

So the right move is:
1. finish rollout first
2. then ship the remaining backlog in small, low-risk phases
3. keep the current public LiveFollow lane stable the whole time

## Start gate
Do **not** start Phase 4A until all of these are true:
- staging env preflight passes
- real-device Google sign-in and `/api/v2/auth/google/exchange` are proven
- the staging smoke matrix passes
- the current private-follow rollout has a clear `go` result

If rollout is still paused, keep this as a planning pack only.

## Recommendation
Yes, proceed with the remaining private-follow backlog **after rollout is green** — but do **not** treat all remaining items as equal.

### Build next
1. follower/following list reads and screens
2. relationship management controls (`cancel request`, `unfollow`, `remove follower`)
3. block / unblock and search/entitlement hardening
4. push foundation and notifications

### Defer for now
1. watcher counts
2. email-based discovery
3. requiring sign-in for the public watch lane

## Why this order is low-risk
### Phase 4A first — read surfaces
Follower/following lists and counts are the lowest-risk missing value because they mostly expose already-existing relationship data and list-visibility rules. They also make the relationship model actually usable without changing the live entitlement path.

### Phase 4B second — relationship controls
`remove follower`, `cancel outgoing`, and `unfollow` add necessary management controls but stay inside the existing follow graph. They are riskier than list reads because they mutate relationship state, but still do not change auth or live-session transport.

### Phase 4C third — block/unblock
Blocks are important, but they are cross-cutting because they affect search visibility, relationship state, pending requests, follower-only live entitlement, and UI state. They deserve their own pass.

### Phase 4D last — notifications
Push tokens and notification delivery add external dependencies, device behavior, and noisy product surfaces. This is the highest operational risk of the remaining backlog and should come last.

## Deliberate deferrals
### Watcher counts
Watcher counts are explicitly optional-later. They should not be mixed into the management phases because they introduce new aggregation, privacy, and real-time update questions.

### Email-based discovery
The plan should continue to avoid raw-email lookup. Handle/display-name/comp-number search is already the intended model.

### Requiring sign-in for the public watch lane
That is a broader product/privacy decision that would change the public LiveFollow lane. Do not mix it into the current private-follow polish work.

## Current reality to preserve in every phase
Every phase in this plan must preserve:
- the current public `/api/v1/live/*` lane
- the current signed-in private-follow lane under `/api/v2/*`
- current Google sign-in + server token exchange
- current v1 write-token upload/task/end path
- the separation between follow policy and live visibility
- the separation between follower counts and watcher counts

## Phase map
## Phase 4A — Followers / Following Lists + Counts
### Goal
Make the relationship graph visible and navigable.

### Deliverables
- authenticated follower/following list read endpoints
- counts surfaced in the right places
- app screens for `Followers` and `Following`
- relationship rows with clear states
- follow-back entry path using the existing follow-request create path where possible
- enforcement of `connection_list_visibility`

### Explicit non-goals
- no block/unblock
- no remove follower
- no push notifications
- no watcher counts
- no email search
- no public watch sign-in changes

### Exit condition
A pilot can see who follows them, who they follow, and the counts, with list visibility rules enforced correctly.

## Phase 4B — Relationship Management Controls
### Goal
Let users manage existing relationships without requiring block.

### Deliverables
- cancel outgoing follow request
- unfollow approved relationship
- remove follower without blocking
- follow-back / mutual polish in the list/profile surfaces
- correct count updates after mutations

### Explicit non-goals
- no block/unblock yet
- no push notifications yet
- no watcher counts

### Exit condition
Users can safely manage relationships, and a pilot can remove a follower without blocking them.

## Phase 4C — Block / Unblock and Abuse-Control Hardening
### Goal
Add the missing safety boundary for private-follow.

### Deliverables
- block/unblock endpoints and storage
- immediate cleanup of both follow directions and pending requests on block
- blocked users hidden from each other in search/list/profile surfaces
- blocked users lose follower-only live entitlement immediately
- clear blocked-state UI handling

### Explicit non-goals
- no push notifications yet
- no watcher counts

### Exit condition
Blocking is correct, immediate, and consistent across search, relationship state, and live access.

## Phase 4D — Push Foundation + Notifications
### Goal
Add push infrastructure and the first notification set without destabilizing the live lane.

### Deliverables
- push token registration / refresh / revoke
- backend push delivery plumbing
- notification types for:
  - incoming follow request
  - follow request accepted
  - optionally live-now notifications, but only if the team explicitly approves that extra noise in this phase
- app deep links into the right screens
- quiet, rate-limited delivery rules

### Explicit non-goals
- watcher counts unless explicitly split into a later phase
- email discovery
- public watch sign-in requirement

### Exit condition
Notifications are delivered reliably, deep-link correctly, and do not regress the current live flows.

## Deferred / separate decision track
These should stay out of the low-risk implementation queue until there is a separate product decision:
- watcher counts
- email-based discovery
- requiring sign-in for the public watch lane

## Per-phase operating rules
Every phase must:
- keep current public LiveFollow stable
- preserve the authenticated private-follow lane already in repo
- follow existing XCPro repo patterns: repository/domain/viewmodel/UI separation and SSOT boundaries
- update the private-follow repo-state doc when repo reality changes
- avoid writing future/private behavior into the current public deployed contract owner
- ship with a phase-specific Codex brief and a phase-specific pass/fail review checklist

## Recommended artifact order
1. this phased IP
2. Phase 4A Codex brief
3. Phase 4A review checklist
4. only after Phase 4A passes: Phase 4B implementation
5. only after Phase 4B passes: Phase 4C implementation
6. only after Phase 4C passes: Phase 4D implementation

## Recommendation summary
Proceed — but only after rollout is green, and in this order:
- **4A:** follower/following lists + counts
- **4B:** relationship controls
- **4C:** block/unblock
- **4D:** push notifications

Keep watcher counts, email discovery, and public-watch sign-in changes out of this sequence.
