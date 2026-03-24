# XCPro Private Follow Proposed API Contract v1

Date: 2026-03-23
Status: Proposed authenticated API contract for private follow and follower-only LiveFollow
Owner: XCPro
Depends on current deployed reality in:
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`
- `docs/LIVEFOLLOW/ServerInfo.md`

## Purpose

Define the proposed XCPro-Server wire contract for:

- account-backed pilot profiles
- search and discovery
- follow relationships
- follow requests and approval
- blocks
- follower/following lists
- follower-only live visibility
- authenticated live reads

This document is a proposal only.

It does not:

- replace the current deployed public LiveFollow contract
- claim that the private-follow lane is already deployed
- change current `/api/v1/live/*` behavior until the server implementation is real

---

## Contract Position Relative to Current LiveFollow

## 1. Public and private lanes coexist

### Current public lane stays intact
Current public LiveFollow remains available through the existing public contract.

### New private lane is authenticated
Private follow and follower-only live are added under a new authenticated `/api/v2/` lane.

## 2. Recommended compatibility rule

Keep these current upload endpoints unchanged and reusable for sessions started by the new authenticated lane:

- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

Only session creation, private discovery, and private reads need new authenticated endpoints.

## 3. Effective visibility rule

For any active session, effective visibility is one of:

- `off`
- `followers`
- `public`

Behavior:

- `off`: not readable by other viewers
- `followers`: readable only by authorized followers and the owner
- `public`: readable by the public lane and the authenticated lane

Important:

- a follower-only session must not appear in the public active list
- a public session may appear in the public active list even if it was started through the authenticated lane

---

## Auth and Identity Assumptions

## 4. Auth model

All `/api/v2/*` endpoints require:

```text
Authorization: Bearer <xcpro-user-access-token>
```

This contract assumes the server can resolve that token to a stable `user_id`.

Token acquisition or provider exchange is outside this LiveFollow contract.

That means:

- the app may use Google sign-in, email-link, or another provider
- the server must still end up with a stable XCPro account identity
- unauthenticated callers receive `401`

## 5. Auth scope rule

Unauthenticated viewers may still use the public `/api/v1/live/*` lane if that lane remains public.

But they may not use:

- profile search
- follow endpoints
- request handling
- follower/following lists
- follower-only live reads

---

## Base URL

`https://api.xcpro.com.au`

## Proposed authenticated endpoints

### Account / profile
- `GET /api/v2/me`
- `PATCH /api/v2/me/profile`
- `PATCH /api/v2/me/privacy`
- `POST /api/v2/me/push-tokens`

### Search / profile read
- `GET /api/v2/users/search`
- `GET /api/v2/users/{user_id}`

### Relationships
- `POST /api/v2/users/{user_id}/follow`
- `DELETE /api/v2/users/{user_id}/follow`
- `GET /api/v2/users/{user_id}/followers`
- `GET /api/v2/users/{user_id}/following`
- `GET /api/v2/follow-requests/incoming`
- `GET /api/v2/follow-requests/outgoing`
- `POST /api/v2/follow-requests/{request_id}/accept`
- `POST /api/v2/follow-requests/{request_id}/decline`
- `DELETE /api/v2/me/followers/{user_id}`
- `POST /api/v2/blocks`
- `DELETE /api/v2/blocks/{user_id}`

### Private live
- `POST /api/v2/live/session/start`
- `PATCH /api/v2/live/session/{session_id}/visibility`
- `GET /api/v2/live/following/active`
- `GET /api/v2/live/users/{user_id}`
- `GET /api/v2/live/session/{session_id}`

---

## Common Types and Enums

## 6. Profile fields

### Stored identity fields
- `user_id`
- `handle`
- `display_name`
- optional `comp_number`
- optional `avatar_url`

### Common counts
- `followers_count`
- `following_count`

`followers_count` and `following_count` count approved follow relationships only.
Pending requests do not count.

## 7. Privacy enums

### Discoverability
- `searchable`
- `hidden`

### Follow policy
- `approval_required`
- `auto_approve`
- `closed`

### Default live visibility
- `off`
- `followers`
- `public`

### Connection-list visibility
- `owner_only`
- `mutuals_only`
- `public`

This visibility governs both the followers list and following list.

## 8. Relationship-state enum

Returned from the point of view of the authenticated caller.

Values:

- `none`
- `outgoing_pending`
- `incoming_pending`
- `following`
- `followed_by`
- `mutual`
- `blocked`
- `blocked_by`

Guidance:

- `followed_by` is required so the caller can distinguish "this pilot follows me" from "no relationship"
- blocked states may be suppressed from search results entirely if preferred, but if returned they should be explicit

## 9. Pagination

List endpoints should support:

- `limit` - optional, default `50`, max `200`
- `cursor` - optional opaque cursor

List responses should return:

- `items`
- `next_cursor` (nullable)

---

## Shared Response Objects

## 10. Profile summary object

```json
{
  "user_id": "uuid",
  "handle": "pilot123",
  "display_name": "Jane Pilot",
  "comp_number": "JP",
  "avatar_url": null,
  "followers_count": 42,
  "following_count": 31,
  "relationship_state": "followed_by"
}
```

## 11. Privacy settings object

```json
{
  "discoverability": "searchable",
  "follow_policy": "approval_required",
  "default_live_visibility": "followers",
  "connection_list_visibility": "owner_only"
}
```

## 12. Request summary object

```json
{
  "request_id": "uuid",
  "created_at": "2026-03-23T05:10:00Z",
  "user": {
    "user_id": "uuid",
    "handle": "pilot123",
    "display_name": "Jane Pilot",
    "comp_number": "JP",
    "avatar_url": null,
    "followers_count": 42,
    "following_count": 31,
    "relationship_state": "incoming_pending"
  }
}
```

## 13. Live latest-position object

Use the same telemetry semantics as the current public contract:

```json
{
  "lat": -37.0,
  "lon": 145.0,
  "alt": 1245.0,
  "agl_meters": 423.0,
  "speed": 18.2,
  "heading": 142.0,
  "timestamp": "2026-03-23T05:10:00Z"
}
```

## 14. Authenticated live session summary object

```json
{
  "session_id": "uuid",
  "status": "active",
  "visibility": "followers",
  "owner": {
    "user_id": "uuid",
    "handle": "pilot123",
    "display_name": "Jane Pilot",
    "comp_number": "JP",
    "avatar_url": null
  },
  "relationship_state": "following",
  "followers_count": 42,
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

## 15. Authenticated full live-read object

This is the proposed private/authenticated superset of the current public live read.

```json
{
  "session_id": "uuid",
  "status": "active",
  "visibility": "followers",
  "owner": {
    "user_id": "uuid",
    "handle": "pilot123",
    "display_name": "Jane Pilot",
    "comp_number": "JP",
    "avatar_url": null
  },
  "relationship_state": "following",
  "followers_count": 42,
  "latest": {
    "lat": -37.0,
    "lon": 145.0,
    "alt": 1245.0,
    "agl_meters": 423.0,
    "speed": 18.2,
    "heading": 142.0,
    "timestamp": "2026-03-23T05:10:00Z"
  },
  "positions": [
    {
      "lat": -37.0,
      "lon": 145.0,
      "alt": 1245.0,
      "agl_meters": 423.0,
      "speed": 18.2,
      "heading": 142.0,
      "timestamp": "2026-03-23T05:10:00Z"
    }
  ],
  "task": null
}
```

`task` should reuse the same semantics as the current deployed contract:

- `null` if no task exists yet
- `null` after explicit task clear
- otherwise return the current task revision payload

---

## Account and Profile Endpoints

## 16. Read current user

### Endpoint
`GET /api/v2/me`

### Auth
Required.

### Response
```json
{
  "user_id": "uuid",
  "profile": {
    "user_id": "uuid",
    "handle": "pilot123",
    "display_name": "Jane Pilot",
    "comp_number": "JP",
    "avatar_url": null,
    "followers_count": 42,
    "following_count": 31,
    "relationship_state": "none"
  },
  "privacy": {
    "discoverability": "searchable",
    "follow_policy": "approval_required",
    "default_live_visibility": "followers",
    "connection_list_visibility": "owner_only"
  }
}
```

### Notes
- `relationship_state` for `GET /me` may be omitted or `none`
- this endpoint is the main bootstrap for signed-in account state

## 17. Update current user profile

### Endpoint
`PATCH /api/v2/me/profile`

### Auth
Required.

### Request body
```json
{
  "handle": "pilot123",
  "display_name": "Jane Pilot",
  "comp_number": "JP",
  "avatar_url": null
}
```

### Field rules

- all fields are optional and patch-like
- `handle` must be unique
- recommended handle rules:
  - lower-case
  - `3..24` characters
  - letters, numbers, `_`, `.`
- `display_name` must be nonblank if present
- `comp_number` is optional and nullable

### Response
Return the updated profile summary.

## 18. Update current user privacy

### Endpoint
`PATCH /api/v2/me/privacy`

### Auth
Required.

### Request body
```json
{
  "discoverability": "searchable",
  "follow_policy": "auto_approve",
  "default_live_visibility": "followers",
  "connection_list_visibility": "owner_only"
}
```

### Field rules

- all fields are optional and patch-like
- each enum must match the allowed values

### Response
Return the updated privacy object.

## 19. Register push token

### Endpoint
`POST /api/v2/me/push-tokens`

### Auth
Required.

### Request body
```json
{
  "platform": "android",
  "provider": "fcm",
  "token": "opaque",
  "device_id": "opaque-device-id",
  "app_version": "1.2.3"
}
```

### Rules

- last-write-wins per `(user_id, device_id, provider)` is acceptable
- duplicate registrations should be idempotent

### Response
```json
{
  "ok": true
}
```

---

## Search and Profile Read

## 20. Search users

### Endpoint
`GET /api/v2/users/search?q=<query>&limit=50&cursor=<cursor>`

### Auth
Required.

### Query rules

- `q` is required
- minimum query length should be `2` or `3` characters
- search must not expose raw email lookups
- exact handle match should rank first
- then prefix / normalized name and comp-number matches
- blocked users must be omitted
- hidden profiles must be omitted from search

### Response
```json
{
  "items": [
    {
      "user_id": "uuid",
      "handle": "pilot123",
      "display_name": "Jane Pilot",
      "comp_number": "JP",
      "avatar_url": null,
      "followers_count": 42,
      "following_count": 31,
      "relationship_state": "none"
    }
  ],
  "next_cursor": null
}
```

## 21. Read a user profile

### Endpoint
`GET /api/v2/users/{user_id}`

### Auth
Required.

### Behavior
- self-read is always allowed
- existing relationship or deep-link access may be allowed even when the target is hidden from search
- blocked viewers should not receive a normal profile response

### Response
```json
{
  "profile": {
    "user_id": "uuid",
    "handle": "pilot123",
    "display_name": "Jane Pilot",
    "comp_number": "JP",
    "avatar_url": null,
    "followers_count": 42,
    "following_count": 31,
    "relationship_state": "followed_by"
  },
  "follow_policy": "approval_required",
  "connection_list_visibility": "owner_only",
  "can_view_followers": false,
  "can_view_following": false
}
```

### Notes
- counts may be visible even when full lists are not
- `can_view_followers` and `can_view_following` are UI helpers

---

## Relationship Endpoints

## 22. Follow a user

### Endpoint
`POST /api/v2/users/{user_id}/follow`

### Auth
Required.

### Request body
No body.

### Behavior by target follow policy

#### `approval_required`
- create a pending request if one does not already exist
- return `outgoing_pending`

#### `auto_approve`
- create a follow relationship immediately
- return:
  - `following`, or
  - `mutual` if the target already follows the caller

#### `closed`
- do not create a request or follow relationship
- return `403`

### Additional rules

- caller cannot follow self
- blocks prevent follow creation
- repeated follow calls should be idempotent

### Response examples

#### Pending request created
```json
{
  "ok": true,
  "result": "pending",
  "relationship_state": "outgoing_pending",
  "deduped": false
}
```

#### Already pending
```json
{
  "ok": true,
  "result": "pending",
  "relationship_state": "outgoing_pending",
  "deduped": true
}
```

#### Auto-approved follow
```json
{
  "ok": true,
  "result": "following",
  "relationship_state": "following",
  "deduped": false
}
```

#### Auto-approved follow-back leading to mutual
```json
{
  "ok": true,
  "result": "following",
  "relationship_state": "mutual",
  "deduped": false
}
```

## 23. Unfollow a user or cancel an outgoing request

### Endpoint
`DELETE /api/v2/users/{user_id}/follow`

### Auth
Required.

### Behavior

- if the caller currently follows the target, remove the follow edge
- if the caller has a pending outgoing request, cancel it
- repeated deletes should be idempotent

### Response
```json
{
  "ok": true,
  "removed": "follow_relationship",
  "relationship_state": "followed_by"
}
```

Possible `removed` values:

- `follow_relationship`
- `pending_request`
- `none`

Resulting `relationship_state` after removal may be:

- `none`
- `followed_by`

## 24. List incoming follow requests

### Endpoint
`GET /api/v2/follow-requests/incoming?limit=50&cursor=<cursor>`

### Auth
Required.

### Behavior
Return only pending requests where the authenticated user is the target.

### Response
```json
{
  "items": [
    {
      "request_id": "uuid",
      "created_at": "2026-03-23T05:10:00Z",
      "user": {
        "user_id": "uuid",
        "handle": "viewer123",
        "display_name": "Viewer Pilot",
        "comp_number": "VP",
        "avatar_url": null,
        "followers_count": 12,
        "following_count": 18,
        "relationship_state": "incoming_pending"
      }
    }
  ],
  "next_cursor": null
}
```

## 25. List outgoing follow requests

### Endpoint
`GET /api/v2/follow-requests/outgoing?limit=50&cursor=<cursor>`

### Auth
Required.

### Behavior
Return only pending requests created by the authenticated user.

### Response
Same shape as incoming, but `relationship_state` will be `outgoing_pending`.

## 26. Accept a follow request

### Endpoint
`POST /api/v2/follow-requests/{request_id}/accept`

### Auth
Required.

### Behavior

- request must currently target the authenticated user
- request must be pending
- accept creates the follow edge `requester -> target`
- if the target already follows the requester, resulting relationship becomes `mutual`

### Response
```json
{
  "ok": true,
  "relationship_state_for_requester": "following",
  "relationship_state_for_target": "followed_by"
}
```

If the target already followed the requester:

```json
{
  "ok": true,
  "relationship_state_for_requester": "mutual",
  "relationship_state_for_target": "mutual"
}
```

## 27. Decline a follow request

### Endpoint
`POST /api/v2/follow-requests/{request_id}/decline`

### Auth
Required.

### Behavior

- request must currently target the authenticated user
- request must be pending
- request becomes resolved/declined
- no follow edge is created

### Response
```json
{
  "ok": true
}
```

## 28. List followers

### Endpoint
`GET /api/v2/users/{user_id}/followers?limit=50&cursor=<cursor>`

### Auth
Required.

### Authorization rules

- owner may always read their own followers list
- other viewers may read only if permitted by the target's `connection_list_visibility`
- blocked viewers must not be allowed

### Response
```json
{
  "total": 42,
  "items": [
    {
      "user_id": "uuid",
      "handle": "viewer123",
      "display_name": "Viewer Pilot",
      "comp_number": "VP",
      "avatar_url": null,
      "followers_count": 12,
      "following_count": 18,
      "relationship_state": "followed_by"
    }
  ],
  "next_cursor": null
}
```

## 29. List following

### Endpoint
`GET /api/v2/users/{user_id}/following?limit=50&cursor=<cursor>`

### Auth
Required.

### Authorization rules

Use the same list-visibility rules as the followers endpoint.

### Response
Same shape as followers.

## 30. Remove a follower from the current user

### Endpoint
`DELETE /api/v2/me/followers/{user_id}`

### Auth
Required.

### Behavior

- remove the follow edge `{user_id} -> me`
- do not automatically block the removed follower
- repeated deletes should be idempotent

### Response
```json
{
  "ok": true
}
```

### Notes
- this endpoint exists because "remove follower" is not the same action as "unfollow"

---

## Block Endpoints

## 31. Block a user

### Endpoint
`POST /api/v2/blocks`

### Auth
Required.

### Request body
```json
{
  "user_id": "uuid"
}
```

### Effects

Blocking must:

- create a block edge
- remove follow edges in both directions
- remove pending requests in both directions
- prevent future follow requests while the block exists
- immediately revoke follower-only live access

### Response
```json
{
  "ok": true
}
```

### Rules
- repeated block requests should be idempotent
- caller cannot block self

## 32. Unblock a user

### Endpoint
`DELETE /api/v2/blocks/{user_id}`

### Auth
Required.

### Response
```json
{
  "ok": true
}
```

### Notes
- unblock does not restore previous follow relationships
- users must follow again if needed

---

## Private Live Endpoints

## 33. Start an authenticated live session

### Endpoint
`POST /api/v2/live/session/start`

### Auth
Required.

### Request body
```json
{
  "visibility": "followers"
}
```

### Rules

- `visibility` is optional; if omitted, use the caller's `default_live_visibility`
- owner of the session is the authenticated user
- current uploader contract remains:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`

### Response example: followers-only
```json
{
  "session_id": "uuid4",
  "status": "active",
  "visibility": "followers",
  "share_code": null,
  "write_token": "opaque"
}
```

### Response example: public
```json
{
  "session_id": "uuid4",
  "status": "active",
  "visibility": "public",
  "share_code": "AB12CD34",
  "write_token": "opaque"
}
```

### Notes

- if `visibility` is `public`, generate/return a public `share_code`
- if `visibility` is `followers` or `off`, `share_code` should be `null`
- session ownership should be stored on the server as `owner_user_id`

## 34. Update live-session visibility

### Endpoint
`PATCH /api/v2/live/session/{session_id}/visibility`

### Auth
Required.

### Request body
```json
{
  "visibility": "public"
}
```

### Rules

- only the owning user may update session visibility
- switching to `public` generates or reactivates a `share_code`
- switching from `public` to `followers` or `off` revokes public visibility immediately

### Response
```json
{
  "session_id": "uuid4",
  "status": "active",
  "visibility": "public",
  "share_code": "AB12CD34"
}
```

## 35. List active live sessions from followed pilots

### Endpoint
`GET /api/v2/live/following/active?limit=50&cursor=<cursor>`

### Auth
Required.

### Behavior

Return active sessions where:

- the owner is currently followed by the caller or otherwise authorized
- session visibility permits the caller to view it

Recommended response:

```json
{
  "items": [
    {
      "session_id": "uuid",
      "status": "active",
      "visibility": "followers",
      "owner": {
        "user_id": "uuid",
        "handle": "pilot123",
        "display_name": "Jane Pilot",
        "comp_number": "JP",
        "avatar_url": null
      },
      "relationship_state": "following",
      "followers_count": 42,
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
  ],
  "next_cursor": null
}
```

## 36. Read the active live session for a given user

### Endpoint
`GET /api/v2/live/users/{user_id}`

### Auth
Required.

### Behavior

- resolve the user's current active session
- return it only if the caller is authorized to view it
- owner may always read their own active session

### Success response
Return the authenticated full live-read object.

### Not-found behavior

If the target user has no active session:

```json
{
  "code": "active_session_not_found",
  "detail": "The requested user does not currently have an active live session."
}
```

## 37. Read a live session by session id

### Endpoint
`GET /api/v2/live/session/{session_id}`

### Auth
Required.

### Behavior

- return the active session if the caller is authorized
- owner may always read their own session
- followers-only sessions must enforce follower authorization
- public sessions may also be readable through current public endpoints

### Success response
Return the authenticated full live-read object.

---

## Authorization and Visibility Rules

## 38. Follower authorization

A caller is authorized for a `followers` session only if:

- caller is the owner, or
- caller has an approved follow relationship to the owner, and
- no blocking rule prevents access

Pending requests are not authorized.

## 39. Public-lane visibility

A session with effective visibility `public`:

- may appear in `GET /api/v1/live/active`
- may be readable by current public read endpoints
- may expose a public `share_code`

A session with visibility `followers` or `off`:

- must not appear in `GET /api/v1/live/active`
- must not be readable through public `session_id` or `share_code` endpoints

## 40. Count visibility

`followers_count` and `following_count` are safe to expose on profiles unless product policy changes later.

Full follower/following lists must respect `connection_list_visibility`.

## 41. Hidden-profile behavior

Recommended behavior:

- hidden profiles do not appear in search results
- direct profile read may still work for existing relationships, notifications, and deep links
- hidden does not imply blocked

---

## Error Envelope and HTTP Semantics

## 42. Error envelope

Keep the current machine-readable envelope style:

```json
{
  "code": "error_code",
  "detail": "Human-readable message."
}
```

## 43. Recommended error codes

### Auth / identity
- `unauthenticated`
- `forbidden`

### Profile / validation
- `handle_already_taken`
- `invalid_handle`
- `profile_not_found`

### Relationship
- `cannot_follow_self`
- `cannot_block_self`
- `follow_policy_closed`
- `follow_blocked`
- `follow_request_not_found`
- `follow_request_not_pending`
- `follow_relationship_not_found`

### Live visibility
- `active_session_not_found`
- `not_authorized_to_view_session`
- `session_not_visible`

### Lists
- `not_authorized_to_view_followers`
- `not_authorized_to_view_following`

## 44. Status code guidance

Use:

- `200` for successful reads and idempotent mutations
- `201` for first-time creation where useful
- `400` for invalid self-actions or invalid state
- `401` for missing/invalid auth
- `403` for blocked or disallowed actions
- `404` for missing resources
- `409` for handle conflicts or incompatible state conflicts
- `422` for request validation failure

---

## Server Persistence Requirements

## 45. Required entities

The server will need, at minimum:

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

## 46. Required uniqueness and integrity constraints

Recommended constraints:

- unique `pilot_profiles.handle`
- unique follow pair `(follower_user_id, target_user_id)`
- unique block pair `(blocker_user_id, blocked_user_id)`
- at most one pending request per `(requester_user_id, target_user_id)`
- `owner_user_id` on live session must reference a valid account when the session was started via `/api/v2/live/session/start`

---

## Deployment and Documentation Guardrail

## 47. Do not rewrite deployed reality prematurely

Do not update the current deployed contract owner as if this proposed contract is already live.

Only after implementation and verification should the repo update:

- the current deployed contract owner
- `ServerInfo.md`
- any current-state summary that depends on deployed private follow behavior

Until then, this doc remains a proposal owner only.

---

## Recommendation

## 48. Recommended implementation order

Use this order:

1. approve this proposed contract
2. build account identity and profile primitives
3. build follow relationships and request handling
4. build follower-only live visibility and reads
5. keep the public lane stable throughout rollout

That sequence minimizes risk and keeps contract ownership clear.
