# XCPro Private Follow Change Plan

Date: 2026-03-23
Status: Proposed implementation/change plan for XCPro app + XCPro-Server
Owner: XCPro
Depends on:
- `docs/LIVEFOLLOW/LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`
- `docs/LIVEFOLLOW/ServerInfo.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Proposed_API_Contract_v1.md`

## Purpose

Turn the private-follow product brief and proposed `/api/v2` contract into an ordered, implementation-ready plan for:

- XCPro app work
- XCPro-Server work
- rollout sequencing
- verification
- compatibility with the current public LiveFollow lane

This document is the implementation/change-plan owner for the proposed private-follow slice.

It does not:

- replace the current deployed LiveFollow contract
- claim the private-follow lane is already deployed
- change the currently approved next slice inside the existing Friends Flying MVP, which remains the read-only Task tab

---

## Key Clarification

This feature is about **viewer entitlement to a pilot's live flight while that pilot is flying using XCPro**.

Roles in this plan:

- `pilot` / `owner` = the XCPro user who is flying and transmitting a live session
- `viewer` / `follower` = the signed-in XCPro user who wants to watch that pilot live

Important clarification:

- `search`
- `follow`
- `request approval`
- `auto-approve` / `Allow all XCPro followers`

are all controls on **which signed-in viewers may see which pilot's live flights**.

This is **not** primarily a generic social feed or chat feature.
It is a relationship/permission layer for live flight visibility, plus enough follower identity and follow-back behavior to make that permission model usable.

---

## Current Reality to Preserve

During rollout, preserve these facts:

- current LiveFollow has a public/share-code lane
- current Friends Flying is still spectator-only, map-first, and read-only
- `GET /api/v1/live/active` remains the current public discovery source
- current public live read payload is still identity-light
- current upload/write flow remains session-token based

Implication:

- private follow must be added as a **new authenticated lane**
- the current public lane must continue to work while private follow is being introduced
- future/private behavior must not be written into the deployed v3 contract until the server actually supports it

---

## Product Outcome We Are Building

When this slice is done, XCPro should support the following end-to-end outcome:

1. A signed-in XCPro user can create an account/profile.
2. That user can search for a pilot by handle, display name, or comp number.
3. The user can follow that pilot.
4. The pilot's follow policy decides whether that becomes:
   - immediate follow
   - pending request
   - rejected/closed
5. The pilot can see who follows them and how many followers they have.
6. The pilot can follow followers back.
7. Mutual follow gives a friend-like state without needing a separate friend primitive.
8. When the pilot starts flying with XCPro, they can choose live visibility:
   - `off`
   - `followers`
   - `public`
9. A signed-in authorized viewer can watch follower-only live flights.
10. Public live/share-code flow continues to exist separately.

---

## Non-Goals for This Slice

Do not expand this slice into all-purpose social networking.

Out of scope for v1:

- text chat or DMs
- comments/reactions
- historical flight sharing/privacy redesign
- group ACL lists beyond followers/public/off
- multi-glider viewer redesign
- changing the existing public LiveFollow MVP into a signed-in-only feature
- requiring classic email/password as the only authentication model
- advanced recommendation/ranking systems
- exposing raw email search
- making watcher-count a required v1 deliverable

Optional-later, not required now:

- `watchers_now_count`
- live-start notifications with per-pilot notification preferences
- public mutuals list
- profile QR/deep-link invites
- account deletion/export/privacy-law workflow hardening beyond normal v1 expectations

---

## Recommended Auth Decision

### Recommendation

Require an XCPro account for the **private follow lane**.

Do **not** require classic email/password as the only auth path.

Recommended v1 auth options:

- `Continue with Google`
- `Continue with email link`

Optional later:

- passkeys
- email/password fallback if you specifically want it

### Why

Private follow needs a stable `user_id`.
The server does not need to care whether that user authenticated via Google, email link, or another provider, as long as it can resolve a bearer token to a stable XCPro account.

### Practical rule

- public LiveFollow may remain usable without sign-in
- private search/follow/follower-only watch requires sign-in

---

## Recommended Data Model

Use these primitives:

- `users`
- `auth_identities`
- `pilot_profiles`
- `privacy_settings`
- `follow_edges` (directed approved follows)
- `follow_requests`
- `blocks`
- `device_push_tokens`
- `live_sessions.owner_user_id`
- `live_sessions.visibility`

Derived state, not stored as a separate primitive:

- `following`
- `followed_by`
- `mutual`

Do **not** create a separate `friends` table for v1.
Mutual follow is enough.

---

## Required Product Rules

These rules should be implemented exactly unless intentionally changed in the brief/contract:

1. Only signed-in XCPro users can search accounts or follow pilots.
2. Anonymous/public viewers cannot see follower-only flights.
3. `Allow all XCPro followers` means auto-approve signed-in XCPro followers.
4. `Allow all XCPro followers` does **not** mean anonymous public live access.
5. `Public` live remains a separate choice from follow policy.
6. A pilot can disable new followers with `closed`.
7. A pilot can remove a follower later without blocking them.
8. Blocking removes both directions of follow plus pending requests.
9. Blocking immediately removes follower-only live visibility.
10. Hidden profiles should not appear in search.
11. Search must not support raw email lookup.
12. Follower and following counts count approved relationships only.
13. Follower/following list visibility is separate from follower count visibility.
14. The current public LiveFollow lane must keep working during rollout.
15. Follower-only sessions must not leak into the public active list or public share-code watch path.

---

## Delivery Strategy

Build this as a new lane in parallel with the current public lane.

Recommended sequence:

### Phase 0
Freeze docs, naming, scope, and compatibility rules.

### Phase 1
Account foundation.

### Phase 2
Search + follow graph + requests + blocks.

### Phase 3
Follower-only live visibility.

### Phase 4
Notifications, follower management polish, rollout hardening.

This order reduces risk because:

- account identity is required before relationship state can be trustworthy
- relationship state is required before follower-only ACL can be correct
- live ACL should not be built before the account and follow graph are solid

---

## Phase 0 - Docs and Design Freeze

### Goal

Ensure product, API, and implementation naming are aligned before code starts.

### Shared tasks

#### PLAN-01
Confirm the canonical vocabulary:

- `pilot`
- `viewer`
- `follower`
- `mutual`
- `follow_policy`
- `default_live_visibility`
- `connection_list_visibility`

#### PLAN-02
Freeze the enums used across app + server:

- `approval_required | auto_approve | closed`
- `off | followers | public`
- `owner_only | mutuals_only | public`
- `none | outgoing_pending | incoming_pending | following | followed_by | mutual | blocked | blocked_by`

#### PLAN-03
Freeze the lane terminology:

- **Public LiveFollow lane** = current share-code/public-watch flow
- **Private Follow lane** = new signed-in/follower-aware flow

#### PLAN-04
Decide the repo/doc placement for this new feature.
Recommended docs to keep active as proposals until deployment:

- `docs/LIVEFOLLOW/XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Proposed_API_Contract_v1.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Change_Plan_2026-03-23.md`

### Exit criteria

- product brief agreed
- proposed API contract agreed
- this change plan agreed
- no ambiguity remains about whether this is a viewer-live-visibility feature versus a generic social feed

---

## Phase 1 - Account Foundation

### Goal

Create stable signed-in XCPro identity, profile fields, privacy settings, and push-token plumbing without changing the public LiveFollow lane.

### XCPro-Server tasks

#### SERVER-01 - Add account tables
Create server-side persistence for:

- `users`
- `auth_identities`
- `pilot_profiles`
- `privacy_settings`
- `device_push_tokens`

Minimum profile data:

- `user_id`
- `handle`
- `display_name`
- nullable `comp_number`
- nullable `avatar_url`

Minimum privacy data:

- `discoverability`
- `follow_policy`
- `default_live_visibility`
- `connection_list_visibility`

#### SERVER-02 - Add bearer-auth resolution
Add middleware or auth dependency that resolves:

```text
Authorization: Bearer <token>
```

to a stable XCPro `user_id`.

This can be backed by Google/email-link/provider verification, but the LiveFollow layer should only depend on the resolved account identity.

#### SERVER-03 - Create `GET /api/v2/me`
Return:

- current user id
- current profile
- current privacy settings

This becomes the bootstrap endpoint for signed-in state.

#### SERVER-04 - Create `PATCH /api/v2/me/profile`
Implement patch updates for:

- `handle`
- `display_name`
- `comp_number`
- `avatar_url`

Rules:

- handle unique
- lower-case normalization
- stable validation rules

#### SERVER-05 - Create `PATCH /api/v2/me/privacy`
Implement patch updates for:

- `discoverability`
- `follow_policy`
- `default_live_visibility`
- `connection_list_visibility`

#### SERVER-06 - Create `POST /api/v2/me/push-tokens`
Store device push token registrations for later request/live notifications.

#### SERVER-07 - Handle normalization + indexing
Add normalized/search-friendly columns or indexes for:

- handle exact match
- handle prefix
- display name prefix/normalized lookup
- comp number prefix/normalized lookup

#### SERVER-08 - Keep `/api/v1/live/*` untouched
Explicitly verify no behavior change to:

- session start
- position upload
- task upsert
- session end
- public active list
- public live reads

### XCPro app tasks

#### APP-01 - Add account gate only where needed
Introduce a private-feature auth gate.

Rule:

- public LiveFollow remains usable without sign-in
- private follow/search/connections require sign-in

#### APP-02 - Add sign-in entry points
Build the first-screen auth options:

- `Continue with Google`
- `Continue with email link`

Do not require passwords for v1.

#### APP-03 - Add profile onboarding flow
After first sign-in, collect:

- display name
- handle
- optional comp number
- discoverability
- follow policy
- default live visibility
- connection list visibility

Recommended first defaults:

- `discoverability = searchable`
- `follow_policy = approval_required`
- `default_live_visibility = followers`
- `connection_list_visibility = owner_only`

#### APP-04 - Add signed-in account store/bootstrap
On sign-in, call `GET /api/v2/me` and hydrate:

- profile
- privacy settings
- sign-in state

#### APP-05 - Add profile/privacy settings screen
Allow the pilot to later change:

- handle
- display name
- comp number
- discoverability
- follow policy
- default live visibility
- follower/following list visibility

#### APP-06 - Register push token
After sign-in and FCM readiness, call `POST /api/v2/me/push-tokens`.

### Verification

Server:

- unit tests for handle uniqueness/validation
- tests for privacy patch behavior
- auth-required tests for `/api/v2/me*`
- regression tests proving `/api/v1/live/*` behavior is unchanged

App:

- first-run auth flow test
- profile onboarding state test
- settings round-trip test
- offline/error handling for profile bootstrap

### Exit criteria

- a signed-in XCPro user can create a profile and configure privacy
- the app can bootstrap signed-in state from `/api/v2/me`
- push token registration works
- current public LiveFollow still behaves exactly the same

---

## Phase 2 - Search, Follow Graph, Requests, Blocks

### Goal

Build the relationship layer that determines which viewers may watch which pilots.

### XCPro-Server tasks

#### SERVER-09 - Add directed follow model
Store approved relationships as directed edges:

- `follower_user_id`
- `target_user_id`

Do not use a separate mutual/friend table.

#### SERVER-10 - Add follow request model
Store pending requests separately.

Minimum fields:

- `request_id`
- `requester_user_id`
- `target_user_id`
- `created_at`

Deduping rule:

- repeated request should not create duplicates

#### SERVER-11 - Add block model
Store block edges.

Block consequences:

- remove follow edges both directions
- remove pending requests both directions
- suppress search visibility between the two users
- prevent future follow attempts until unblocked

#### SERVER-12 - Create `GET /api/v2/users/search`
Implement signed-in pilot search.

Rules:

- min query length `2` or `3`
- exact handle rank first
- then prefix/normalized display name / comp number
- no raw email search
- hidden profiles excluded
- blocked users excluded
- return relationship state per row

#### SERVER-13 - Create `GET /api/v2/users/{user_id}`
Return profile summary plus view permissions and relationship state.

#### SERVER-14 - Create `POST /api/v2/users/{user_id}/follow`
Behavior by target follow policy:

- `approval_required` -> pending request
- `auto_approve` -> immediate follow edge
- `closed` -> reject with policy error

#### SERVER-15 - Create `DELETE /api/v2/users/{user_id}/follow`
Remove caller's follow edge toward target.

#### SERVER-16 - Create request-list endpoints
Implement:

- `GET /api/v2/follow-requests/incoming`
- `GET /api/v2/follow-requests/outgoing`

#### SERVER-17 - Create request-action endpoints
Implement:

- `POST /api/v2/follow-requests/{request_id}/accept`
- `POST /api/v2/follow-requests/{request_id}/decline`

Accept should create an approved follow edge.

#### SERVER-18 - Create follower/following list endpoints
Implement:

- `GET /api/v2/users/{user_id}/followers`
- `GET /api/v2/users/{user_id}/following`

Apply `connection_list_visibility` rules.

Counts should remain visible even if full lists are not.

#### SERVER-19 - Create remove-follower endpoint
Implement:

- `DELETE /api/v2/me/followers/{user_id}`

This removes the other person's edge toward the owner without blocking them.

#### SERVER-20 - Create block/unblock endpoints
Implement:

- `POST /api/v2/blocks`
- `DELETE /api/v2/blocks/{user_id}`

#### SERVER-21 - Relationship-state composer
Create one canonical server-side function that computes the caller-relative relationship state:

- `none`
- `outgoing_pending`
- `incoming_pending`
- `following`
- `followed_by`
- `mutual`
- `blocked`
- `blocked_by`

Do not duplicate relationship-state logic in many endpoints inconsistently.

#### SERVER-22 - Add abuse/rate limits
Add basic controls for:

- search query spam
- follow request spam
- repeated accept/decline thrash

### XCPro app tasks

#### APP-07 - Add `Connections` entry point
Create a dedicated signed-in area for relationship management.

Recommended tabs/screens:

- `Find Pilots`
- `Requests`
- `Followers`
- `Following`

Keep this separate from the current Friends Flying spectator shell.

#### APP-08 - Build pilot search UX
Search result card should show:

- avatar/initials
- display name
- `@handle`
- optional comp number
- follower count
- relationship state CTA

Button mapping:

- `none` -> `Follow`
- `outgoing_pending` -> `Requested`
- `following` -> `Following`
- `followed_by` -> `Follow back`
- `mutual` -> `Mutual`

#### APP-09 - Build incoming/outgoing request UX
Incoming request cards should expose:

- requester identity
- follower/follow-back context if relevant
- `Accept`
- `Decline`
- overflow `Block`

Outgoing request list should show pending state cleanly.

#### APP-10 - Build followers/following list UX
Followers list should support:

- `Follow back`
- `Remove follower`
- optional `Block`

Following list should support:

- `Unfollow`
- view profile

#### APP-11 - Build profile summary sheet/screen
For a pilot profile, show:

- display name
- handle
- comp number
- follower count
- following count
- relationship action
- follow-policy result state

#### APP-12 - Respect hidden/closed/blocked states
Build soft, privacy-respecting UX for:

- hidden profiles not appearing in search
- closed followers rejecting new follows
- blocked states not over-disclosing what happened

#### APP-13 - Support follow-back / mutual labeling
Wherever relationship state is shown, surface `Mutual` clearly once both edges exist.

### Verification

Server:

- search ranking and filtering tests
- request dedupe tests
- follow-policy tests for `approval_required | auto_approve | closed`
- block cascade tests
- list-visibility tests
- relationship-state tests for all combinations

App:

- search UX tests
- follow CTA state transitions
- request accept/decline/block flows
- follower list actions
- hidden/blocked UX tests

### Exit criteria

- signed-in users can search pilots
- follow/request/auto-approve all work
- pilots can see who follows them and follow back
- mutual state appears correctly
- blocks work and remove access correctly

---

## Phase 3 - Private Live Visibility

### Goal

Attach the relationship layer to live-session visibility so a pilot can fly with XCPro and limit live viewing to followers.

### XCPro-Server tasks

#### SERVER-23 - Extend live session ownership
Store on live session:

- `owner_user_id` (nullable for legacy/public flow if needed)
- `visibility = off | followers | public`

#### SERVER-24 - Create `POST /api/v2/live/session/start`
Authenticated session start should:

- require bearer auth
- resolve owner user
- apply requested or default visibility
- create session tied to `owner_user_id`
- return session metadata suitable for the signed-in lane
- continue returning a write token for existing upload endpoints if you keep the upload endpoints unchanged

#### SERVER-25 - Decide share-code behavior for private sessions
Recommended rule:

- `followers` visibility sessions do not expose a usable public `share_code`
- `public` visibility sessions may expose or retain `share_code`

Implementation choices are allowed, but the effective public/private access rule must match the proposal docs.

#### SERVER-26 - Create `PATCH /api/v2/live/session/{session_id}/visibility`
Allow the owner to change a current session between:

- `off`
- `followers`
- `public`

Rules:

- only owner can change visibility
- visibility change takes effect immediately on reads/discovery

#### SERVER-27 - Create `GET /api/v2/live/following/active`
Return active live sessions visible to the signed-in caller because they are authorized followers (or mutuals).

Payload should include:

- owner identity
- relationship state
- follower count
- latest telemetry summary
- session visibility

#### SERVER-28 - Create `GET /api/v2/live/users/{user_id}`
Return the live session for a specific pilot if the caller is authorized to view it.

#### SERVER-29 - Create `GET /api/v2/live/session/{session_id}`
Return authenticated session read for follower-only or public sessions.

This can be the authenticated superset of the public live read.

#### SERVER-30 - Reuse existing upload endpoints safely
Keep or reuse:

- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

Important:

- existing upload semantics should remain stable
- the new authenticated lane should not break current write behavior

#### SERVER-31 - Enforce private live ACL rules
For `followers` visibility:

- owner can read
- authorized followers can read
- anonymous/public users cannot read
- blocked users cannot read
- removed followers cannot read

#### SERVER-32 - Prevent public leakage
Ensure follower-only sessions:

- do not appear in `GET /api/v1/live/active`
- are not readable through public share-code watch
- do not expose typed identity publicly unless session visibility is `public`

#### SERVER-33 - Preserve current task semantics
Authenticated live reads should preserve current task rules:

- `task = null` before any task exists
- `task = null` after explicit clear
- existing task payload semantics remain intact

#### SERVER-34 - Optionally add owner-visible live summary fields
Optional but useful:

- viewer entitlement summary
- visibility label
- maybe `followers_count`

Do not make `watchers_now_count` mandatory for v1.

### XCPro app tasks

#### APP-14 - Add pre-flight visibility selector
When a signed-in pilot starts a live session, show a compact selector:

- `Off`
- `Followers`
- `Public`

Rules:

- prefill from account default
- allow one-tap override before going live

#### APP-15 - Route signed-in pilots through the authenticated session-start path
If the user is signed in and using private-follow-capable LiveFollow, call `/api/v2/live/session/start`.

If not signed in or if using the legacy/public path, keep current behavior available.

#### APP-16 - Keep existing upload path stable
After session start, continue position/task/end uploads through the existing upload path unless/until you intentionally migrate them.

#### APP-17 - Add `Following Live` discovery surface
Add a signed-in viewer list for live pilots the caller is allowed to watch.

Recommended placement:

- `LiveFollow > Following Live`

#### APP-18 - Reuse the current watched-pilot map experience
Do not redesign the current spectator viewer from scratch.

For follower-only watch:

- reuse watched glider rendering
- reuse telemetry strip
- reuse task overlay / future task tab work where possible

The relationship layer should decide **who** can watch; the existing viewer shell should continue to decide **how** the live pilot is watched.

#### APP-19 - Add visibility status to pilot controls
When flying, the pilot should be able to see whether current live visibility is:

- Off
- Followers
- Public

#### APP-20 - Handle authorization failures gracefully
If a viewer loses access because of:

- visibility change
- follower removal
- block
- session end

show a clear but privacy-respecting state and exit or refresh appropriately.

### Verification

Server:

- ACL tests for owner/follower/non-follower/blocked/anonymous
- public-leak regression tests
- authenticated active-list tests
- visibility-change tests
- session-start tests for signed-in ownership
- current upload compatibility tests

App:

- pre-flight visibility selector tests
- signed-in session-start routing tests
- following-live list tests
- viewer authorization-loss tests
- regression tests proving current public LiveFollow still works

### Exit criteria

- a signed-in pilot can fly with visibility `followers`
- authorized followers can discover and watch that pilot live
- non-followers cannot
- current public lane still works for `public` sessions and legacy public usage

---

## Phase 4 - Notifications and Relationship Polish

### Goal

Make the follow/live experience feel complete without changing the underlying permission model.

### XCPro-Server tasks

#### SERVER-35 - Emit follow-request notifications
When `approval_required` creates a pending request, notify the target pilot.

#### SERVER-36 - Emit auto-approve/new-follower notifications
When `auto_approve` creates an immediate follower, send a quieter notification to the pilot.

#### SERVER-37 - Emit accept notifications
When a request is accepted, notify the requester.

#### SERVER-38 - Optional live-start notifications
Optional for v1 if time permits:

- notify followers when a pilot they follow goes live

Keep this behind a preference if you add it.

#### SERVER-39 - Add idempotent notification eventing
Ensure repeated retries or duplicate state transitions do not spam notifications.

### XCPro app tasks

#### APP-21 - Add deep links from notifications
Targets:

- incoming request -> `Connections > Requests`
- follow accepted -> profile or following list
- live started -> watched-pilot handoff

#### APP-22 - Finalize notification copy
Recommended starter copy:

- `New follow request` / `@viewer wants to follow your XCPro flights.`
- `You're now following @pilot`
- `Follow request accepted` / `You can now follow @pilot on XCPro.`
- `@pilot is live now` / `Tap to watch this flight.`

#### APP-23 - Add follower-management polish
Nice-to-have UI polish:

- remove-follower confirmation
- block confirmation
- mutual badge
- empty states for requests/followers/following

#### APP-24 - Add follower-count surfacing consistently
Show follower count in:

- search results
- profile summary
- optional live session summary cards

### Verification

- push registration end-to-end
- deep links open the correct screen/state
- no duplicate notification spam
- follow state remains consistent after notification-driven actions

### Exit criteria

- pilots receive follow-related notifications
- viewers get the right follow acceptance/live start callbacks if enabled
- follower-management UX feels complete

---

## Rollout Plan

### Recommended rollout flags

Use feature flags so the new lane can be introduced gradually.

Suggested flags:

- `accounts_enabled`
- `connections_enabled`
- `private_follow_enabled`
- `private_live_visibility_enabled`
- `follow_notifications_enabled`

### Suggested rollout order

1. Internal dev-only
2. Small trusted alpha group
3. Wider beta with signed-in accounts
4. Public availability for private follow
5. Optional promotion of private-follow UI in main navigation

### Safe rollout principle

Do not remove the public LiveFollow lane while the private lane is new.

---

## Screen/Navigation Plan

### New signed-in area

Add `Connections` with:

- `Find Pilots`
- `Requests`
- `Followers`
- `Following`

### LiveFollow split

Recommended signed-in discovery split:

- `Following Live`
- `Public Live`

This keeps the relationship feature separate from the existing spectator shell while still allowing both discovery modes.

### Why this matters

The current Friends Flying shell is map-first spectator UI.
Relationship management belongs in its own signed-in area, while live-watching can still reuse the existing map/watch experience.

---

## Suggested Error Codes

Use the same machine-readable `code` + `detail` style as the current deployed API.

Recommended additions:

- `profile_not_searchable`
- `follow_requests_disabled`
- `follow_request_already_exists`
- `already_following`
- `not_following`
- `follow_blocked`
- `cannot_follow_self`
- `not_authorized_to_view_session`
- `session_not_visible`
- `relationship_action_not_allowed`

---

## Test Matrix

### Server mandatory tests

- profile create/update
- handle uniqueness and normalization
- privacy updates
- search filtering and ranking
- request creation and dedupe
- auto-approve follow creation
- closed follow policy rejection
- accept/decline behavior
- remove follower behavior
- block cascade behavior
- relationship-state composition
- connection-list visibility rules
- authenticated session start
- follower-only read ACL
- follower-only session hidden from public active list
- public lane regression tests
- task-null-after-clear behavior still correct in authenticated reads

### App mandatory tests

- auth gate only for private features
- onboarding happy path
- profile/settings updates
- search results and CTA state mapping
- request handling flows
- follower/following lists
- follow-back -> mutual transition
- pre-flight visibility selector
- following-live discovery list
- follower-only watch handoff
- authorization loss while watching
- public LiveFollow regression

### Manual QA scenarios

1. Viewer requests to follow pilot with `approval_required`.
2. Pilot accepts; viewer can watch follower-only flight.
3. Pilot uses `auto_approve`; follow becomes immediate.
4. Pilot uses `closed`; new follows cannot be created.
5. Pilot removes follower; viewer loses follower-only access.
6. Pilot blocks viewer; all follow state disappears and access ends.
7. Pilot switches a live session from `followers` to `public` and back.
8. Anonymous viewer cannot access follower-only flight.
9. Signed-in mutual followers each see the other as `Mutual`.
10. Public lane still works unchanged for public live/share-code watch.

---

## Open Decisions to Make Explicit Before Coding

These are not blockers to the plan, but they should be settled intentionally:

1. Handle rules:
   - exact character set
   - min/max length
   - rename policy
2. Whether hidden profiles are reachable by direct deep link when not blocked.
3. Whether follower counts are visible to everyone or only signed-in users.
4. Whether full follower/following lists should default to owner-only.
5. Whether `public` live started from the authenticated lane should still mint a share code.
6. Whether live-start notifications ship in v1 or later.
7. Whether classic email/password is offered as a fallback or omitted.
8. Whether `watchers_now_count` is deferred entirely.

Recommended defaults:

- handle lower-case, unique, stable
- hidden profiles searchable by nobody, but direct profile read allowed where relationship/deep-link rules permit
- counts visible broadly, lists private by default
- public authenticated live may still expose share-code/public watch
- live-start notifications optional, not blocking v1
- no mandatory password system for v1
- defer `watchers_now_count`

---

## Recommended First Coding Order

If you want the smallest realistic path to value, do the coding in this order:

1. `SERVER-01` to `SERVER-08`
2. `APP-01` to `APP-06`
3. `SERVER-09` to `SERVER-22`
4. `APP-07` to `APP-13`
5. `SERVER-23` to `SERVER-34`
6. `APP-14` to `APP-20`
7. `SERVER-35` to `SERVER-39`
8. `APP-21` to `APP-24`

That ordering gets the foundations right before touching live ACL.

---

## Definition of Done

This private-follow slice is done when all of the following are true:

- signed-in XCPro accounts exist for the private-follow lane
- pilots can create discoverable profiles and configure follow/live privacy
- viewers can search pilots and follow them
- approval-required, auto-approve, and closed follow policies all work correctly
- pilots can see followers, follow back, and become mutuals
- pilots can start live with `off`, `followers`, or `public`
- authorized followers can watch follower-only flights
- unauthorized viewers cannot watch follower-only flights
- public LiveFollow remains intact
- the feature is documented as proposed until the server is actually deployed
- once deployed, the current deployed contract docs can be updated separately to reflect reality

---

## Final Recommendation

Proceed with this feature as a **new authenticated viewer-entitlement lane for live flight visibility**, not as a loose social add-on and not as a rewrite of the existing public spectator MVP.

The key product truth to preserve during implementation is:

> search, follow, request approval, and auto-approve all exist so a viewer can be allowed or denied access to a pilot's live XCPro flight when that pilot is flying.

Everything else in this plan is in service of making that rule durable, understandable, and safe.
