# XCPro Private Follow Product and UX Brief

Date: 2026-03-23
Status: Proposed product and UX brief for an authenticated private-follow lane
Owner: XCPro
Depends on current deployed reality in:
- `docs/LIVEFOLLOW/LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`
- `docs/LIVEFOLLOW/ServerInfo.md`

## Purpose

Define how XCPro should add:

- signed-in XCPro accounts for private follow
- pilot search and discovery
- follow requests and approvals
- an "allow all followers" mode
- follower counts and follower identity lists
- mutual follow / friend-like behavior
- follower-only live visibility

This brief is the product and UX owner for the proposed feature.

It does not:

- change the current deployed public LiveFollow contract by itself
- replace the current single-pilot spectator MVP docs
- change the currently approved next slice inside Friends Flying, which remains the read-only Task tab

---

## Current Reality to Preserve

Today LiveFollow is still a public/share-code oriented spectator MVP.

Current facts to preserve during rollout:

- Friends Flying is spectator-only, map-first, and read-only.
- The public lane currently revolves around:
  - `POST /api/v1/session/start`
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
  - `GET /api/v1/live/active`
  - `GET /api/v1/live/{session_id}`
  - `GET /api/v1/live/share/{share_code}`
- `share_code` is the current public watch key.
- The current public live payload is identity-light and not sufficient for account-based follower relationships.

Implication:

- private follow must be added as a new authenticated lane beside the current public lane
- the public/share-code flow should keep working during rollout
- private follower behavior should not be presented as deployed reality until the server supports it

---

## Product Goal

Let an XCPro user:

- sign in to an XCPro account
- create a pilot profile with a unique handle
- search for another XCPro pilot
- request to follow that pilot
- be auto-approved or manually approved depending on the pilot's settings
- see who follows them and how many followers they have
- follow their followers back
- become "mutuals" when both sides follow each other
- choose whether live visibility is:
  - off
  - followers only
  - public

This should feel familiar to users coming from social apps, but safer for real-time location than a generic friend system.

---

## Core Product Decisions

## 1. Keep two LiveFollow lanes

### Lane A: Public LiveFollow
This is the current lane.

Characteristics:

- works without signed-in follow relationships
- uses the public active list and share-code watch flow
- remains the spectator MVP path
- continues to support open/public viewing

### Lane B: Private Follow
This is the new lane.

Characteristics:

- requires a signed-in XCPro account
- adds pilot discovery and profile identity
- adds follow relationships
- adds request/approval/auto-approve behavior
- adds follower-only live visibility
- adds follower lists and follower counts

These two lanes should coexist. Private follow is not a rewrite of the public lane.

## 2. Use one-way follow as the primitive

Backend primitive:

- user A follows user B

Derived social states:

- `following`
- `followed_by`
- `mutual`

A "friend" concept should be derived from mutual follow, not stored as a separate primitive.

Why this is the right model:

- it handles fans, spectators, teammates, and mutuals
- it supports "follow back" naturally
- it avoids overcomplicating the data model

## 3. Separate follow policy from live visibility

These are different controls and should not be overloaded.

A pilot may choose:

- who can become a follower
- who can see live flights
- whether their follower/following lists are visible

That means:

- "Allow all followers" does not automatically mean "public to the anonymous internet"
- "Public live" remains a separate choice from follow approval policy

## 4. Accounts are required for private follow

Private follow needs stable account identity.

Recommendation:

- require an XCPro account for pilot search, follow, follower-only watch, and request handling
- do not require classic email/password as the only auth path
- recommended v1 auth options:
  - Sign in with Google
  - email-link / magic-link sign-in
- passwords are optional and not required for the product model

Anonymous or non-signed-in users may still use the public LiveFollow lane.

---

## Account and Privacy Model

## 5. Account/profile fields

Minimum profile fields:

- `user_id`
- `handle`
- `display_name`
- optional `comp_number`
- optional avatar/initials

Recommended handle rules:

- unique
- lower-case
- stable once created, with limited edit support later

## 6. Privacy and control settings

### 6.1 Discoverability
Whether the pilot can be found in search.

Values:

- `searchable`
- `hidden`

Meaning:

- `searchable`: appears in pilot search
- `hidden`: omitted from search, but may still be reachable by an existing relationship, request, or direct deep link

### 6.2 Follow policy
How new followers are handled.

Stored values:

- `approval_required`
- `auto_approve`
- `closed`

User-facing labels:

- `Require approval`
- `Allow all XCPro followers`
- `No new followers`

Meaning:

- `approval_required`: new follow attempts become pending requests
- `auto_approve`: any signed-in XCPro user can follow immediately
- `closed`: no new followers can be added

Important clarification:

- `Allow all XCPro followers` means any signed-in XCPro account can follow without manual approval
- it does not mean public anonymous live visibility
- public anonymous visibility is controlled by live visibility, not follow policy

### 6.3 Default live visibility
How new live sessions should be exposed by default.

Values:

- `off`
- `followers`
- `public`

Meaning:

- `off`: flight is not available to viewers
- `followers`: only authorized followers can watch
- `public`: public live/watch lane remains available

### 6.4 Connection list visibility
Who can see the pilot's followers/following lists.

Stored values:

- `owner_only`
- `mutuals_only`
- `public`

User-facing label:

- `Who can see my followers and following`

Meaning:

- `owner_only`: only the pilot sees the full lists
- `mutuals_only`: the pilot sees the full lists; other users see the lists only if they are mutual with the pilot
- `public`: lists are visible to all signed-in viewers

Recommended default:

- counts visible
- full lists private to owner
- pilot can opt in to broader list visibility later

---

## Relationship State Model

## 7. Stored primitives

Server-side primitives:

- follow edge: `follower_user_id -> target_user_id`
- optional pending request
- block edge

## 8. Aggregate relationship state

For XCPro app UI, compute one relationship summary from the caller's point of view.

Recommended states:

- `none`
- `outgoing_pending`
- `incoming_pending`
- `following`
- `followed_by`
- `mutual`
- `blocked`
- `blocked_by`

Display mapping examples:

- `none` -> `Follow`
- `outgoing_pending` -> `Requested`
- `incoming_pending` -> `Review request`
- `following` -> `Following`
- `followed_by` -> `Follows you`
- `mutual` -> `Mutual`
- `blocked` / `blocked_by` -> hide actions or show a blocked state

Important:

- `followed_by` matters because a pilot needs to see who follows them and optionally follow back
- do not collapse `followed_by` into `none`

## 9. Counts that must stay separate

Do not mix these concepts:

- `followers_count` = approved followers of a pilot
- `following_count` = accounts the pilot follows
- `watchers_now_count` = people actively watching a live flight right now

These are different product concepts.

Recommended v1 behavior:

- ship follower/following counts
- make viewer-count/watcher-count optional later

---

## Concrete XCPro Flows

## 10. Flow A - first-time account setup

1. User opens a feature that requires account identity.
2. If not signed in, show an auth sheet:
   - `Continue with Google`
   - `Continue with email link`
3. After sign-in, user lands on profile onboarding.
4. User sets:
   - display name
   - handle
   - optional comp number
   - discoverability
   - follow policy
   - default live visibility
   - connection list visibility
5. Account is created and the user enters the signed-in social/live experience.

Guardrail:

- do not block public spectator use on sign-in
- only gate private follow features behind sign-in

## 11. Flow B - search and follow

1. Signed-in user opens `Connections > Find Pilots`.
2. User searches by:
   - `@handle`
   - display name
   - comp number
3. Search results show:
   - avatar/initials
   - display name
   - handle
   - optional comp number
   - followers count
   - relationship state/action button
4. User taps `Follow`.

Outcome by target follow policy:

### Target policy: `approval_required`
- create a pending request
- button changes to `Requested`
- viewer state becomes `outgoing_pending`
- target gets:
  - push notification
  - in-app request item

### Target policy: `auto_approve`
- create the follow relationship immediately
- viewer state becomes:
  - `following`, or
  - `mutual` if the target already follows the viewer
- target gets a quiet "new follower" notification/inbox item

### Target policy: `closed`
- do not create a request
- show a soft unavailable state
- do not reveal unnecessary private details

## 12. Flow C - incoming requests

1. Pilot receives a follow request notification.
2. Pilot opens `Connections > Requests`.
3. Request card shows:
   - requester's avatar/initials
   - display name
   - handle
   - optional comp number
   - mutual/followed-by context if available
4. Pilot chooses:
   - `Accept`
   - `Decline`
   - `Block`

Outcomes:

### Accept
- requester becomes `following`
- if pilot already follows requester, both become `mutual`

### Decline
- no follow edge is created
- no punitive push is sent to requester
- requester sees a quiet generic state if they revisit the profile

### Block
- remove pending request
- remove existing follow edges in either direction
- prevent future follow attempts until unblocked
- immediately remove live visibility access

## 13. Flow D - follow back and mutuals

1. Pilot opens their `Followers` list.
2. Pilot sees who follows them.
3. Pilot can tap `Follow back`.

Outcomes:

- if the follower uses `approval_required`, normal request rules apply from the pilot's side
- if the follower uses `auto_approve`, the follow-back becomes immediate
- once both directed follows exist, both users show `Mutual`

This gives the "sort of like Facebook friends" effect without needing a separate friend table.

## 14. Flow E - remove follower, unfollow, block

### Unfollow
Viewer action on a profile they already follow:

- remove their follow edge
- resulting relationship becomes:
  - `none`, or
  - `followed_by` if the other person still follows them

### Remove follower
Pilot action inside `Followers` list:

- removes the follower's edge toward the pilot
- pilot does not automatically block them
- they may follow again later, depending on policy

### Block
Available from profile, followers list, requests list.

Blocking should:

- remove all follow edges both directions
- remove all pending requests both directions
- remove access to follower-only live sessions immediately
- hide each user from the other in search and relationship lists

## 15. Flow F - pilot goes live

1. Signed-in pilot starts a live session.
2. App uses the saved default live visibility, but should allow a quick override before the session goes public.
3. Live visibility choices:
   - `Off`
   - `Followers`
   - `Public`

Recommended UX:

- show a compact pre-flight visibility selector
- remember the last choice unless the pilot changes account-level defaults

Meaning:

### Off
- live session exists only for the pilot's app/server needs
- not visible to followers or the public

### Followers
- visible only to authenticated viewers who are authorized followers
- does not appear in the public active list
- does not expose a public share code

### Public
- visible in the public live lane
- share/public watch behavior remains available
- may also be visible to followers in the authenticated lane

## 16. Flow G - viewer discovers and watches live pilots

### Signed-in follower view
Add a signed-in live discovery surface:

- `LiveFollow > Following Live`
- shows followed pilots who are currently live and visible to the caller

Tapping an item should open the watched-pilot map experience.

### Public viewer lane
Keep the existing public lane:

- `LiveFollow > Public Live` or current public entrypoint
- uses the current public active/share-code model

This separation keeps the social model clean and avoids overloading the existing Friends Flying MVP shell.

## 17. Flow H - anonymous/public user behavior

A user without an XCPro account should still be able to:

- browse public LiveFollow
- use public share-code watch

But they should not be able to:

- search pilots by XCPro account profile
- follow pilots
- view follower-only live sessions
- see follower/following lists that require sign-in

---

## Recommended Screens and Navigation

## 18. New app area: Connections

Recommended sections:

- `Following`
- `Followers`
- `Requests`
- `Find Pilots`

This should be a dedicated signed-in area, not stuffed inside the current Friends Flying watch shell.

## 19. Recommended screens

### 19.1 Profile onboarding
Fields:

- display name
- handle
- optional comp number
- discoverability
- follow policy
- default live visibility
- connection list visibility

### 19.2 Pilot profile screen
Show:

- avatar/initials
- display name
- handle
- comp number
- followers count
- following count
- relationship badge
- primary action button
- overflow actions:
  - unfollow
  - remove follower where appropriate
  - block

### 19.3 Find Pilots
Search-first screen:

- query input
- recent searches (optional later)
- result cards with relationship CTA
- exact handle hits first

### 19.4 Requests
Two sections:

- `Incoming`
- `Outgoing`

Actions:

- accept
- decline
- cancel request
- block

### 19.5 Followers
Show:

- who follows the current user
- their relationship state
- quick follow-back action
- remove follower
- block

### 19.6 Following
Show:

- who the current user follows
- live badge if they are live and visible
- quick unfollow

### 19.7 Live privacy settings
Inside account/profile settings:

- default live visibility
- follow policy
- discoverability
- connection list visibility

### 19.8 Live start visibility sheet
Shown when starting a live session:

- `Off`
- `Followers`
- `Public`

Should be fast, clear, and easy to dismiss.

---

## Notification Copy

## 20. Required notification copy for v1

### New follow request
Title: `New follow request`
Body: `@viewer wants to follow your XCPro flights.`

### Follow request accepted
Title: `Follow request accepted`
Body: `You can now follow @pilot on XCPro.`

### New follower (auto-approved or quiet accepted state)
Title: `New follower`
Body: `@viewer is now following you on XCPro.`

### Mutual follow
Title: `You now follow each other`
Body: `You and @pilot are now mutuals on XCPro.`

## 21. Optional notification copy for later phases

### Pilot is live now
Title: `@pilot is live now`
Body: `Tap to watch this flight.`

### Sharing ended
Title: `Sharing ended`
Body: `@pilot stopped sharing this flight.`

### Declined / blocked
Do not send explicit punitive push copy.
Use quiet in-app state instead.

---

## Acceptance, Privacy, and Moderation Rules

## 22. Rules that should be non-negotiable

- only signed-in XCPro accounts can use private follow
- private live access is never granted before authorization
- `auto_approve` is immediate follow approval for signed-in XCPro users
- `public` live is separate from `auto_approve`
- block overrides follow and request state
- blocking removes access immediately
- a pilot can disable new followers via `closed`
- a pilot can remove followers later without blocking them
- current public LiveFollow should keep working during rollout

## 23. Search and visibility rules

- hidden profiles are omitted from search
- blocked users do not see each other in search
- follower/following counts may be visible even when full lists are not
- the pilot always sees their own full follower/following lists
- other viewers see full lists only if allowed by connection-list visibility

## 24. Request and abuse-control rules

Recommended baseline behavior:

- one active pending request per pair
- repeated follow taps should be idempotent, not create duplicate requests
- quiet decline is preferred over punitive feedback
- search and follow mutations must be rate-limited server-side
- block should be idempotent

Specific rate-limit numbers can stay operational and do not need to be frozen in this brief.

---

## XCPro App Implementation Plan

## 25. App architecture guardrails

Keep implementation aligned with the existing XCPro architecture:

- repository is the single source of truth
- business logic belongs in repositories and use cases, not Compose
- ViewModels own UI state
- UI renders state and emits intents
- keep transport DTOs separate from richer app-local UI/domain state

Avoid:

- controller-style escape hatches
- embedding follow-policy decisions in Compose
- binding server DTOs one-to-one to every local watcher-state concept

## 26. Recommended app-side modules

### Data layer
Add:

- `AccountRepository`
- `ProfileRepository`
- `FollowRepository`
- `FollowRequestsRepository`
- `PushTokenRepository`
- `PrivateLiveRepository`

### Domain layer
Add use cases such as:

- `GetCurrentUserUseCase`
- `UpdateProfileUseCase`
- `UpdatePrivacySettingsUseCase`
- `SearchUsersUseCase`
- `GetUserProfileUseCase`
- `FollowUserUseCase`
- `UnfollowUserUseCase`
- `RemoveFollowerUseCase`
- `AcceptFollowRequestUseCase`
- `DeclineFollowRequestUseCase`
- `BlockUserUseCase`
- `UnblockUserUseCase`
- `ObserveFollowersUseCase`
- `ObserveFollowingUseCase`
- `ObserveRequestsUseCase`
- `RegisterPushTokenUseCase`
- `ObserveFollowingLiveUseCase`
- `UpdateLiveVisibilityUseCase`

### UI/ViewModel layer
Add:

- `ConnectionsViewModel`
- `FindPilotsViewModel`
- `FollowersViewModel`
- `FollowingViewModel`
- `FollowRequestsViewModel`
- `PilotProfileViewModel`
- `LivePrivacySettingsViewModel`
- `FollowingLiveViewModel`

## 27. Recommended UI models

Suggested UI/domain models:

- `RelationshipSummary`
- `PilotProfileUiModel`
- `FollowerRowUiModel`
- `FollowRequestUiModel`
- `LiveAudiencePolicyUiModel`
- `FollowingLiveItemUiModel`

These should be richer than raw transport DTOs.

## 28. App integration tasks

### Auth and account
- add sign-in entry for Google and email-link
- store authenticated account context
- gate private follow features on account presence

### Connections area
- add signed-in navigation entry
- build search, followers, following, requests surfaces

### Profile/privacy
- build onboarding and settings editing flow
- support handle validation and conflict errors

### Live flow
- add live visibility picker to signed-in live-session start
- add `Following Live` discovery surface
- keep current public live path intact

### Push
- register FCM token to backend
- deep-link new follow requests into `Requests`
- deep-link accepted requests into target profile
- later: deep-link live-now notifications into watch mode

---

## XCPro-Server Workstreams

## 29. Required server capabilities

The server must add:

- authenticated user identity resolution
- pilot profile storage
- privacy settings storage
- follow edges
- follow requests
- blocks
- push token storage
- live-session owner association
- live-session visibility and authorization

## 30. Recommended persistence entities

At minimum:

- `users`
- `auth_identities`
- `pilot_profiles`
- `privacy_settings`
- `follow_relationships`
- `follow_requests`
- `blocks`
- `device_push_tokens`
- `live_sessions` extended with:
  - `owner_user_id`
  - `visibility`

## 31. Reuse current upload contract where possible

To reduce churn:

- keep current upload shapes for:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
- add a new authenticated session-start path for private/account-owned sessions
- add new authenticated read/discovery endpoints for follower-only visibility

This reduces app/server risk while adding identity and privacy.

---

## Rollout Plan

## 32. Phase 0 - docs and approval

Approve:

- this product/UX brief
- the proposed authenticated API contract
- a later implementation change plan

Keep current deployed docs unchanged until behavior is real.

## 33. Phase 1 - accounts and profile foundation

Ship:

- account identity
- profile onboarding
- unique handle
- discoverability
- follow policy
- connection list visibility
- default live visibility

## 34. Phase 2 - follow graph and request handling

Ship:

- search pilots
- request to follow
- auto-approve
- requests inbox
- accept / decline / block
- followers list
- following list
- follower/following counts
- follow-back / mutual states
- push notification for incoming follow request

## 35. Phase 3 - follower-only live

Ship:

- signed-in live session owner association
- live visibility selector
- follower-only active list
- authenticated live watch read
- remove leakage into public lane when visibility is not public

## 36. Phase 4 - polish

Add:

- live-now notifications to followers
- connection-list visibility polish
- profile deep links
- watcher counts if desired
- remove follower UX polish
- stronger abuse tooling if needed

---

## First Valuable Slice

## 37. Smallest useful release

The minimum release that proves the model is:

1. XCPro account identity
2. profile + handle
3. pilot search
4. follow request / accept / decline / auto-approve / block
5. followers-only live visibility

This is enough to validate the product without building every social feature up front.

---

## Acceptance Criteria

## 38. Product acceptance criteria

The slice should be considered complete when all of these are true:

### Accounts
- a user can sign in and create a profile
- a unique handle is enforced

### Search and follow
- a signed-in user can search a searchable pilot
- a follow attempt becomes pending or immediate based on the target's policy
- duplicate follow taps are safe and do not create duplicate requests

### Request handling
- a pilot receives a request notification and sees the request in-app
- accept creates the relationship correctly
- decline does not leak punitive details
- block immediately removes access

### Social visibility
- a pilot can see who follows them
- follower count is visible
- pilot can follow followers back and reach `mutual`

### Live visibility
- a pilot can start a live session as `followers`
- authorized followers can watch it
- non-followers cannot watch it
- anonymous/public viewers cannot see follower-only sessions
- public live still works separately

---

## Recommendation

## 39. Recommended next artifact order

Use this order:

1. approve this product/UX brief
2. approve `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
3. write a concrete implementation change plan for app and server work
4. implement in phases while keeping public LiveFollow stable

That order will reduce mistakes and keep contract ownership clear.
