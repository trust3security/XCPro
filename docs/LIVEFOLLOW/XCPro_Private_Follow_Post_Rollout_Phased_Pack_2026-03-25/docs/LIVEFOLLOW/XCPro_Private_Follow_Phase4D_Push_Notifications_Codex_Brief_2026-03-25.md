# XCPro Private Follow — Phase 4D Codex Brief
## Push token foundation and first notification set

**Date:** 2026-03-25  
**Status:** Planned implementation brief. Start only after Phase 4C is approved, rollout is green, and the notification service/environment decision is actually ready.  
**Scope:** Add push token registration and the first private-follow notifications.  
**Non-goal:** Do not add watcher counts, email-based discovery, or public-watch sign-in changes.

## Goal
Add the first notification layer without destabilizing the current live/watch flows.

When this phase is complete, XCPro should support:
- push token registration / refresh / revoke
- notification delivery for:
  - incoming follow request
  - follow request accepted
- optionally live-now notifications **only if** the team explicitly approves that noise and the phase remains manageable; otherwise keep live-now as a follow-up mini-phase
- deep links into the correct account/private-follow screens

## Why this phase is last in the low-risk sequence
This phase introduces the most operational complexity:
- external push provider setup
- device token lifecycle
- background delivery
- deep links
- notification noise and rate limits

That is why it comes after the read/control/safety phases.

## Current reality to preserve
Preserve:
- public `/api/v1/live/*`
- private-follow account/profile/request/live-entitlement behavior
- current Google auth and bearer flow
- current relationship and block behavior from earlier phases

## Required server capabilities
Add the minimal push surface needed.

### Preferred server additions
- `POST /api/v2/me/push-tokens`
- `DELETE /api/v2/me/push-tokens/{token_or_id}` or equivalent revoke endpoint

If the repo already has a better token-registration pattern, use it and document it.

### Notification triggers
Required minimum:
- incoming follow request
- follow request accepted

Optional in this phase only if explicitly approved:
- pilot goes live and viewer is eligible to receive live-now notifications

### Delivery rules
- be idempotent where practical
- rate-limit noisy events
- do not send notifications to blocked users
- respect relationship and entitlement state at send time

## Required app work
### Push token lifecycle
App-side:
- register token after sign-in / when refreshed
- revoke or clear association on sign-out when appropriate
- keep local state sane across token refresh

### Deep links
Add routing for:
- follow request -> Requests surface
- follow accepted -> target profile or follower/following surface
- live-now (if included) -> authorized watch path

### Notification settings
Do **not** build a huge settings matrix unless already needed. Keep v1 settings minimal.

## Task 1 — Confirm notification provider decision
Before coding, confirm the actual push provider/setup path available to XCPro.

If provider setup is not ready, stop and report it instead of building a half-integrated notification phase.

## Task 2 — Add push token registration/revocation
Server-side:
- add token storage if still missing
- add register/revoke endpoints
- keep tokens tied to stable authenticated users

App-side:
- send token to backend when available
- update registration on token refresh
- clear or revoke appropriately on sign-out

## Task 3 — Add follow-request and follow-accepted notifications
Server-side:
- emit notifications on incoming follow request
- emit notifications when request is accepted
- suppress sends when relationship/block state makes them invalid

App-side:
- receive and deep-link to the correct surfaces

## Task 4 — Optional live-now notification scope decision
By default, keep live-now notifications **out** unless the team explicitly approves them for this phase.

If included, they must:
- target only eligible followers
- respect block state and relationship state
- deep-link correctly into watch
- stay behind a clear product decision or feature flag

## Task 5 — Add tests
### Server tests
Add coverage for:
- push token register/revoke
- follow-request notification enqueue/send path
- follow-accepted notification enqueue/send path
- no sends to blocked users or invalid targets
- live-now path only if included

### App tests
Add coverage for:
- token registration flow
- sign-out cleanup behavior
- deep link routing from notifications
- notification-open state handling

## Task 6 — Update docs
Update:
- private-follow repo-state doc
- setup doc if external notification/provider setup becomes part of the required environment
- rollout/release docs only if the operator story changes materially

Do not rewrite the public deployed contract owner.

## Constraints
- implement Phase 4D only
- preserve all current public and private-follow flows
- no watcher counts
- no email discovery
- no public watch sign-in changes
- keep live-now out unless explicitly approved

## Acceptance criteria
Phase 4D is complete when:
- push token lifecycle works
- follow-request notifications work
- follow-accepted notifications work
- deep links route correctly
- no public/private follow regressions exist
- tests/docs are updated

## Report back expected from Codex
When done, report back with:
- exact token endpoints/storage added
- exact notification types shipped
- whether live-now was included or deferred
- exact tests run
- exact docs updated
- any remaining manual/provider setup still required
