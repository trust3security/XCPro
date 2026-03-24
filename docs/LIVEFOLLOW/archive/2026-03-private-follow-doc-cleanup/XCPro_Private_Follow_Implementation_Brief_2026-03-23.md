# XCPro Private Follow + Followers Live Visibility Implementation Brief

Date: 2026-03-23
Status: Proposed implementation brief
Owner: XCPro

## 1. Purpose

Define how XCPro should add an authenticated private-follow system on top of the current LiveFollow spectator stack, without rewriting or misrepresenting the current deployed public contract.

This brief covers:
- product model
- XCPro app UI and state flow
- XCPro-Server data model and API proposal
- rollout order
- test and architecture guardrails

This brief does not change the current deployed contract by itself.

## 2. Current Reality to Preserve

Today LiveFollow is still a public/share-code oriented single-pilot spectator MVP.

Current facts:
- Friends Flying is spectator-only, map-first, and read-only.
- The current deployed public contract includes:
  - `POST /api/v1/session/start`
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
  - `GET /api/v1/live/active`
  - `GET /api/v1/live/{session_id}`
  - `GET /api/v1/live/share/{share_code}`
- `share_code` is the public watch key today.
- The current public read payload is still identity-light and does not expose typed account identity.
- The currently approved next LIVEFOLLOW slice is still the read-only Task tab for the watched pilot.

Implication:
- Private follow must be treated as a **new authenticated slice**, not folded into the current deployed v3 contract as if it already exists.
- The current public LiveFollow flow should remain working during rollout.

## 3. Product Goal

Allow XCPro users to:
- create an XCPro account
- search for other XCPro pilots
- follow them
- be approved or auto-approved based on the pilot's settings
- see who follows them and how many followers they have
- follow followers back
- derive a mutual-follow relationship similar to a lightweight "friend" concept
- allow live flight visibility to followers only, or publicly, per the pilot's settings

## 4. Recommended Product Model

### 4.1 Keep two parallel LiveFollow lanes

#### Lane A: Public LiveFollow (existing)
- existing share-code/public spectator flow
- current `GET /api/v1/live/active`
- current `GET /api/v1/live/share/{share_code}`
- keep working unchanged during rollout

#### Lane B: Private Follow (new)
- requires signed-in XCPro account
- pilot discovery/search
- follower relationships
- approval / auto-approve / closed follow policy
- follower-only live visibility
- follower lists and follower counts

### 4.2 Core relationship primitive

Use **one-way follow** as the primitive.

Derived state:
- `following`: viewer follows pilot
- `mutual`: both users follow each other
- UI label may say `Friend` or `Mutual`, but backend primitive should stay one-way follow

That keeps the model flexible for fans, spectators, teammates, and mutuals.

## 5. User Settings Model

These settings should be separate, not overloaded.

### 5.1 Discoverability
- `searchable`
- `hidden`

### 5.2 Follow policy
- `approval_required`
- `auto_approve`
- `closed`

Meaning:
- `approval_required`: new follows create pending requests
- `auto_approve`: any signed-in XCPro account can follow immediately
- `closed`: no new followers allowed

### 5.3 Live visibility default
- `off`
- `followers`
- `public`

Meaning:
- `off`: no live visibility
- `followers`: only approved followers can view when live
- `public`: public share/public live path remains available

### 5.4 Follower list visibility
- `owner_only`
- `mutuals_only`
- `public`

Recommended default:
- follower count visible
- full follower list private to owner
- mutual relationships visible where helpful

## 6. Relationship State Model

Use these app/domain states:
- `none`
- `outgoing_pending`
- `incoming_pending`
- `following`
- `mutual`
- `blocked`

Derived display examples:
- `Follow`
- `Requested`
- `Following`
- `Follows You`
- `Mutual`
- hidden/disabled for blocked states

## 7. Metrics to Keep Separate

Do not mix these concepts:
- `followers_count` = approved followers of a pilot
- `following_count` = accounts that pilot follows
- `watchers_now_count` = current viewers of a live flight

These are different product concepts and should remain distinct in the app and server models.

## 8. Accounts / Authentication Recommendation

Private follow requires a stable XCPro account identity.

It does **not** require classic email/password specifically.

Recommended v1 account strategy:
- XCPro account record with stable `user_id`
- at least one low-friction auth method:
  - email link sign-in, or
  - Sign in with Google
- optional later:
  - passkeys
- optional fallback only if needed:
  - email/password

Reason:
- follow requests, blocks, follower counts, and follower-only live visibility require stable user identity
- classic passwords add reset/support overhead that is not required for the feature itself

## 9. Search / Discovery Model

Search should support:
- `@handle`
- display name
- comp number / callsign

Search should **not** expose raw email search.

Recommended search behavior:
- auth required
- minimum 2 or 3 characters
- exact handle match first
- then prefix display-name/comp-number match
- rate-limited
- blocked users hidden
- non-searchable profiles omitted
- each result includes current relationship state

## 10. XCPro App UX

## 10.1 Do not overload current Friends Flying shell

Friends Flying is currently a spectator-only public-watch shell.

Keep follower management in a separate area:

### New app area: Connections
Tabs / sections:
- `Following`
- `Followers`
- `Requests`
- `Find Pilots`

### LiveFollow additions
Add separate live sections:
- `Following Live`
- `Public Live`

This keeps social/account management separate from the current map-first spectator shell.

## 10.2 Primary user flows

### Flow A: Search and follow
1. User signs in.
2. User opens `Find Pilots`.
3. User searches `@pilot`, display name, or comp number.
4. Result card shows current relationship state.
5. User taps `Follow`.
6. Outcome depends on pilot follow policy:
   - `approval_required` -> `Requested`
   - `auto_approve` -> `Following`
   - `closed` -> unavailable

### Flow B: Pilot handles incoming request
1. Pilot receives push notification and in-app request item.
2. Pilot opens `Requests`.
3. Pilot can:
   - `Accept`
   - `Decline`
   - `Block`
4. Accepted request becomes `following`.
5. If pilot already follows back, both sides become `mutual`.

### Flow C: Pilot goes live
1. Signed-in pilot starts live session.
2. App shows visibility selector or applies stored default:
   - `Off`
   - `Followers`
   - `Public`
3. Followers can see pilot in `Following Live` when visibility is `Followers`.
4. Public viewers can still use the public lane when visibility is `Public`.

## 10.3 Recommended screens

### Account/Profile setup
- display name
- `@handle`
- optional comp number
- searchable toggle
- follow policy setting
- default live visibility setting
- follower-list visibility setting

### Pilot profile screen
- avatar/initials
- display name
- `@handle`
- comp number
- followers count
- following count
- mutual indicator if applicable
- action button:
  - Follow / Requested / Following / Mutual / Remove / Block

### Requests inbox
- incoming requests
- outgoing requests
- quick accept/decline/block

### Following list
- all followed pilots
- whether they follow you back
- current live status badge if live

### Followers list
- follower identities
- quick follow-back
- remove follower
- block user

## 11. Notification Copy

### Follow request received
Title: `New follow request`
Body: `@viewer wants to follow your XCPro flights.`

### Request accepted
Title: `Follow request accepted`
Body: `You can now follow @pilot on XCPro.`

### Auto-approved follow
Viewer body: `You are now following @pilot.`
Pilot body: `@viewer is now following you.`

### Mutual follow
Title: `You now follow each other`
Body: `You and @pilot are now mutuals on XCPro.`

### Pilot goes live for followers
Title: `@pilot is live now`
Body: `Tap to watch this flight.`

### Sharing ended
Title: `Sharing ended`
Body: `@pilot stopped sharing this flight.`

### Blocked / declined
Do not send punitive or explicit blocked notifications.
Use quiet/generic UI state instead.

## 12. XCPro App Architecture Plan

Align with repo rules:
- `data -> repository`
- `domain -> usecase`
- `ui -> viewmodel / compose`
- Hilt DI
- Repository as SSOT
- no business logic in Compose
- no raw manager/controller escape hatches

### 12.1 New data layer pieces
- `AccountRepository`
- `ProfileRepository`
- `FollowRepository`
- `FollowRequestsRepository`
- `PushTokenRepository`
- `PrivateLiveRepository`

### 12.2 New domain/use cases
- `SearchUsersUseCase`
- `GetUserProfileUseCase`
- `FollowUserUseCase`
- `UnfollowUserUseCase`
- `AcceptFollowRequestUseCase`
- `DeclineFollowRequestUseCase`
- `BlockUserUseCase`
- `ObserveFollowersUseCase`
- `ObserveFollowingUseCase`
- `ObserveFollowRequestsUseCase`
- `RegisterPushTokenUseCase`
- `ObserveFollowingLiveUseCase`
- `UpdateLiveVisibilityUseCase`

### 12.3 New ViewModels
- `ConnectionsViewModel`
- `FindPilotsViewModel`
- `FollowRequestsViewModel`
- `PilotProfileViewModel`
- `FollowingLiveViewModel`
- `LivePrivacySettingsViewModel`

### 12.4 UI rule
All business state transitions belong in repositories/use cases.
Compose screens only:
- render state
- emit intents
- collect lifecycle-aware flows

## 13. XCPro-Server Data Model

Suggested tables/entities:
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

### 13.1 Suggested fields

#### users
- `id`
- `created_at`
- `status`

#### auth_identities
- `id`
- `user_id`
- `provider`
- `provider_subject`
- `email`
- `email_verified`

#### pilot_profiles
- `user_id`
- `handle`
- `display_name`
- `comp_number`
- `avatar_url`
- `searchable`

#### privacy_settings
- `user_id`
- `follow_policy`
- `default_live_visibility`
- `follower_list_visibility`

#### follow_relationships
- `follower_user_id`
- `target_user_id`
- `created_at`
- unique pair

#### follow_requests
- `id`
- `requester_user_id`
- `target_user_id`
- `status` (`pending|accepted|declined|cancelled`)
- `created_at`
- `resolved_at`

#### blocks
- `blocker_user_id`
- `blocked_user_id`
- `created_at`

#### live_sessions
Add:
- `owner_user_id`
- `visibility` (`off|followers|public`)
- optional `watchers_now_count` cache if needed later

## 14. XCPro-Server API Proposal

Keep current `/api/v1/live/*` endpoints unchanged for the public lane.

Add a new authenticated lane, preferably `/api/v2/`.

## 14.1 Profile / account
- `GET /api/v2/me`
- `PATCH /api/v2/me/profile`
- `PATCH /api/v2/me/privacy`
- `POST /api/v2/me/push-tokens`

## 14.2 Search / profile read
- `GET /api/v2/users/search?q=...`
- `GET /api/v2/users/{userId}`

## 14.3 Follow relationships
- `POST /api/v2/users/{userId}/follow`
- `DELETE /api/v2/users/{userId}/follow`
- `GET /api/v2/users/{userId}/followers`
- `GET /api/v2/users/{userId}/following`
- `GET /api/v2/follow-requests/incoming`
- `GET /api/v2/follow-requests/outgoing`
- `POST /api/v2/follow-requests/{id}/accept`
- `POST /api/v2/follow-requests/{id}/decline`
- `POST /api/v2/blocks`
- `DELETE /api/v2/blocks/{userId}`

## 14.4 Private live
- `POST /api/v2/live/session/start`
- `PATCH /api/v2/live/session/{sessionId}/visibility`
- `GET /api/v2/live/following/active`
- `GET /api/v2/live/users/{userId}`

## 14.5 Payload additions needed for private mode
Current public live payload is identity-light, so private mode DTOs should add typed identity:
- `owner_user_id`
- `handle`
- `display_name`
- optional `comp_number`
- `relationship_state`
- `followers_count`
- `watchers_now_count` (optional later)

## 15. API Behavior Rules

### 15.1 Public lane stays public
Current public endpoints remain current until explicitly replaced.

### 15.2 Follower-only sessions must not leak into public lane
- follower-only sessions should not appear in public active list
- follower-only sessions should not be readable through public share-code/session-id endpoints
- `share_code` can remain null/unused for follower-only visibility

### 15.3 Error envelope
Keep current machine-readable style:
```json
{
  "code": "error_code",
  "detail": "message"
}
```

Suggested new codes:
- `cannot_follow_self`
- `follow_request_already_exists`
- `follow_requests_disabled`
- `profile_not_searchable`
- `follow_blocked`
- `not_authorized_to_view_session`
- `session_not_visible`
- `follow_relationship_not_found`

## 16. Suggested DTO examples

### User search result
```json
{
  "user_id": "uuid",
  "handle": "pilot123",
  "display_name": "Jane Pilot",
  "comp_number": "JP",
  "followers_count": 42,
  "relationship_state": "outgoing_pending"
}
```

### Private following-live item
```json
{
  "user_id": "uuid",
  "handle": "pilot123",
  "display_name": "Jane Pilot",
  "session_id": "uuid",
  "visibility": "followers",
  "status": "active",
  "latest": {
    "lat": -37.0,
    "lon": 145.0,
    "alt": 1245.0,
    "agl_meters": 423.0,
    "speed": 18.2,
    "heading": 142.0,
    "timestamp": "2026-03-23T05:10:00Z"
  }
}
```

## 17. Rollout Phases

### Phase 0 - Docs and approval
Create and approve:
- product flow doc
- proposed API contract doc
- change plan doc

Do not update the current deployed contract doc as if this is already live.

### Phase 1 - Account foundation
Ship:
- XCPro account identity
- profile onboarding
- `@handle`
- discoverability
- follow policy
- default live visibility

### Phase 2 - Search and relationship layer
Ship:
- user search
- follow request flow
- auto-approve support
- accept / decline / block
- followers/following lists
- follower counts
- follow-back / mutual state

### Phase 3 - Private live visibility
Ship:
- signed-in live session association with `owner_user_id`
- per-flight visibility selector
- follower-only live discovery
- authenticated watch endpoint

### Phase 4 - Polish
Ship:
- push notifications
- remove follower
- cancel pending request
- follower-list visibility rules
- live viewer counts if desired
- profile deep links

## 18. First Valuable Slice

Smallest useful release:
1. XCPro accounts
2. handles/profile
3. search pilots
4. follow request + accept/decline/block + auto-approve
5. follower-only live visibility

That is enough to prove the private-follow model without overbuilding social features.

## 19. Testing and Verification

### App-side
- pure JVM tests for relationship state transitions
- ViewModel tests for request/accept/decline flows
- repository tests for SSOT correctness
- Compose tests for search results, request states, follower list, request inbox
- integration tests for follower-only live discovery

### Server-side
- request creation / dedupe / accept / decline / block tests
- search visibility tests
- authorization tests for follower-only live reads
- privacy policy tests (`approval_required`, `auto_approve`, `closed`)
- regression tests so current public v1 flow remains unchanged

### Required repo checks
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew connectedDebugAndroidTest` when UI/integration paths change materially

## 20. Docs to Create Next

Recommended docs:
- `docs/LIVEFOLLOW/Private_Follow_Product_Flow_v1.md`
- `docs/LIVEFOLLOW/Private_Follow_Proposed_API_Contract_v1.md`
- `docs/LIVEFOLLOW/Private_Follow_Change_Plan_2026-03-23.md`

Do **not** replace these current owners until behavior is actually deployed:
- `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
- `LiveFollow_Current_Deployed_API_Contract_v3.md`
- `ServerInfo.md`

## 21. Recommendation

Yes: create the brief first.

This feature is large enough that XCPro should:
- approve the product model first
- approve the API/account shape second
- then implement in phases

Trying to build it directly into the current public LiveFollow code and docs without a separate plan will make contract ownership, privacy rules, and app state flow harder to reason about.
