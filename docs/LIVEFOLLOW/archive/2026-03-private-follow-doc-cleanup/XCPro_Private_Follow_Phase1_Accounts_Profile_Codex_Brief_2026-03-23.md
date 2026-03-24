# XCPro Private Follow — Phase 1 Codex Brief
## Accounts, profiles, privacy foundation for the private-follow lane

**Status:** Approved implementation brief for Phase 1  
**Scope:** Add the account/profile/privacy foundation required for XCPro private follow, without yet implementing search, follow requests, follower lists, or follower-only live gating.  
**Non-goal:** Do not change the current public LiveFollow lane or claim future private-follow behavior is already deployed unless the server/app code and docs are updated together.

## Goal
XCPro needs a private-follow lane where signed-in XCPro viewers can eventually search for pilots, follow them, be approved or auto-approved, and then view follower-only live flights.

Phase 1 is the foundation for that work.

The goal of this phase is to:
- add XCPro account identity plumbing for the private-follow lane
- add a stable account/profile model on XCPro-Server
- add pilot profile onboarding/editing in XCPro
- add privacy settings needed by later follow/live slices
- keep the current public/share-code LiveFollow lane intact

This phase is intentionally **not** the follow/search/live-entitlement phase.

## Product clarification
This private-follow system is about **viewer entitlement to a pilot's live XCPro flights**.

Roles:
- `pilot` / `owner` = the XCPro user who is flying and transmitting a live session
- `viewer` / `follower` = the signed-in XCPro user who wants to watch that pilot live

In later phases:
- `search`
- `follow`
- `request approval`
- `auto-approve` / `Allow all XCPro followers`

are all about **which signed-in viewers can see which pilot's live flights while that pilot is flying with XCPro**.

Phase 1 does not implement those behaviors yet. It lays the groundwork so later phases can add them cleanly.

## Current reality to preserve
Preserve these current facts while implementing Phase 1:
- current LiveFollow is still the single-pilot spectator MVP
- Friends Flying is still spectator-only, map-first, and read-only
- the current public lane still revolves around:
  - `POST /api/v1/session/start`
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
  - `GET /api/v1/live/active`
  - `GET /api/v1/live/{session_id}`
  - `GET /api/v1/live/share/{share_code}`
- `share_code` remains the current public watch key
- current public live payload is still identity-light

Implication:
- add the private-follow foundation as a **new authenticated lane**, preferably under `/api/v2/`
- do **not** break or repurpose the existing public LiveFollow endpoints
- do **not** make public LiveFollow require sign-in in this phase

## Desired end state
After Phase 1 is complete, XCPro should have:

1. a working signed-in XCPro account state for the private-follow lane
2. a stable backend `user_id` model for signed-in users
3. pilot profile fields stored on the server:
   - `handle`
   - `display_name`
   - optional `comp_number`
   - optional avatar/initials support if easy within repo patterns
4. pilot privacy settings stored on the server:
   - `discoverability`
   - `follow_policy`
   - `default_live_visibility`
   - `connection_list_visibility`
5. app UI to:
   - sign in / sign out
   - create or finish a profile on first sign-in
   - edit profile later
   - edit privacy settings later
6. authenticated `/api/v2` endpoints for the app to read/update the current user profile and privacy settings
7. no search/follow/request/follower-only-live behavior yet
8. no regressions in the current public LiveFollow lane

## Recommended auth decision
### Product decision
Require an XCPro account for the private-follow lane.

Do **not** require classic email/password as the only auth path.

Preferred auth options for the private-follow lane:
- `Continue with Google`
- `Continue with email link`

Optional later:
- passkeys
- password fallback if the repo already has durable support for it

### Implementation rule
Before coding, audit the app and server for any existing auth/account infrastructure.

- If the repo already has a real auth stack, reuse it.
- If the repo already has Firebase/Auth provider setup, integrate with that rather than inventing a parallel system.
- If there is **no** durable auth stack yet, do **not** invent a weak custom password system just to satisfy this phase.

If no real provider integration exists yet, it is acceptable to build the account/profile/private-follow **application seam** cleanly and leave provider-exchange details behind an auth abstraction, but you must clearly report what is implemented versus what remains provider-specific.

## Phase 1 data model
Add only the tables/entities needed for the foundation.

### Required server-side primitives
- `users`
- `auth_identities`
- `pilot_profiles`
- `privacy_settings`

### Not yet required in Phase 1
Do **not** implement these in Phase 1 unless they are trivial schema placeholders with no active behavior:
- `follow_edges`
- `follow_requests`
- `blocks`
- `device_push_tokens`

### Required profile fields
At minimum store:
- `user_id`
- `handle`
- `display_name`
- optional `comp_number`
- timestamps (`created_at`, `updated_at`)

### Required privacy fields
Store:
- `discoverability = searchable | hidden`
- `follow_policy = approval_required | auto_approve | closed`
- `default_live_visibility = off | followers | public`
- `connection_list_visibility = owner_only | mutuals_only | public`

Important clarification:
- `auto_approve` is the backend state behind the user-facing label `Allow all XCPro followers`
- that setting is about later viewer follow approval behavior
- it does **not** mean anonymous public live access
- public anonymous live remains a separate visibility choice

## Phase 1 API scope
Implement only the current-user foundation endpoints needed now.

### Required endpoints
Add authenticated `/api/v2/*` endpoints for:
- `GET /api/v2/me`
- `PATCH /api/v2/me/profile`
- `PATCH /api/v2/me/privacy`

### Optional in Phase 1
Only add if clearly useful and low-risk:
- `POST /api/v2/me/push-tokens` — optional, not required in Phase 1
- `GET /api/v2/users/{user_id}` — optional, not required in Phase 1

### Do not implement yet
Do **not** implement in Phase 1:
- `GET /api/v2/users/search`
- any follow endpoint
- any follow-request endpoint
- follower/following lists
- block endpoints
- follower-only live endpoints

## Task 1 — Audit existing auth/account infrastructure
Before adding code, inspect both repos and determine:
- whether XCPro already has any auth/account feature
- whether XCPro-Server already validates bearer tokens or has a user model
- whether Firebase/Auth, Google sign-in, or email-link infrastructure already exists
- whether any existing settings/profile screen can host the new UI

Report back clearly with:
- what already exists
- what you reused
- what you had to add
- any provider-specific setup that remains external to repo code

Do not skip this audit.

## Task 2 — Add server account foundation
In XCPro-Server:
- add the required tables/entities for `users`, `auth_identities`, `pilot_profiles`, and `privacy_settings`
- add migrations or schema changes using the repo's existing migration pattern
- make `handle` unique and case-normalized
- keep privacy settings attached to the user/profile in a way that is easy for later phases to read

### Required validation rules
At minimum enforce:
- `handle` is required after onboarding completes
- `handle` uniqueness is enforced case-insensitively
- `handle` normalization is deterministic
- `display_name` is required and trimmed
- `comp_number` is optional and nullable
- privacy enum values must be one of the allowed values

### Recommended handle behavior
Choose a simple durable rule, for example:
- lowercase
- letters/digits/underscore/dot only
- length-bounded

If the repo already has a preferred pattern, follow it.

## Task 3 — Add authenticated `/api/v2` user endpoints
In XCPro-Server, implement:

### `GET /api/v2/me`
Return the current signed-in user's profile + privacy state.

Minimum response shape:
```json
{
  "user_id": "string",
  "handle": "pilot123",
  "display_name": "Pilot Name",
  "comp_number": "ABC",
  "privacy": {
    "discoverability": "searchable",
    "follow_policy": "approval_required",
    "default_live_visibility": "followers",
    "connection_list_visibility": "owner_only"
  }
}
```

### `PATCH /api/v2/me/profile`
Allow updating:
- `handle`
- `display_name`
- `comp_number`

Enforce validation and uniqueness.

### `PATCH /api/v2/me/privacy`
Allow updating:
- `discoverability`
- `follow_policy`
- `default_live_visibility`
- `connection_list_visibility`

### Error behavior
Use machine-readable error envelopes consistent with the current server style:
```json
{
  "code": "error_code",
  "detail": "message or validation detail"
}
```

At minimum handle:
- missing/invalid bearer token
- handle already taken
- invalid enum values
- validation failure

## Task 4 — Add auth resolution seam on the server
Add authenticated access for `/api/v2/*`.

### Required behavior
- `/api/v2/*` endpoints require `Authorization: Bearer <token>`
- missing or invalid token returns `401`
- token resolution must produce a stable `user_id`

### Important constraint
Do **not** introduce a fake production auth model that conflicts with the intended Google/email-link direction.

If the repo already has auth provider integration, use it.
If it does not, build a clean auth boundary and clearly report what still depends on external provider setup.

## Task 5 — Add XCPro app account state
In XCPro app:
- add signed-in vs signed-out account state
- add an account entry point in a sensible place, likely existing settings/profile/navigation infrastructure
- show a signed-out screen or CTA when the user has not yet signed in
- show a signed-in account/profile screen when the user is signed in

### Required app behaviors
- user can sign in
- app can fetch `GET /api/v2/me`
- app stores enough authenticated session state to call `/api/v2/*`
- user can sign out cleanly
- auth failure returns the user to a sensible signed-out or re-auth state

## Task 6 — Add first-run profile onboarding in XCPro
After sign-in, if the user does not yet have a finished profile, show onboarding.

Minimum onboarding fields:
- `handle`
- `display_name`
- optional `comp_number`

### Required onboarding rules
- block completion until required fields are valid
- show handle-taken errors clearly
- after successful save, user lands on a normal signed-in profile/settings state

### Important constraint
Do not start Phase 2 features here.
No pilot search. No follow requests. No followers lists.

## Task 7 — Add profile edit screen in XCPro
After onboarding, the user must be able to:
- view current handle/display name/comp number
- edit and save them
- see server validation errors inline or clearly in UX

This screen should be stable enough to remain the owner-facing profile editor for later phases.

## Task 8 — Add privacy settings screen in XCPro
Add UI for these settings:
- `Discoverability`
  - `Searchable`
  - `Hidden`
- `Follow policy`
  - `Require approval`
  - `Allow all XCPro followers`
  - `No new followers`
- `Default live visibility`
  - `Off`
  - `Followers`
  - `Public`
- `Connection list visibility`
  - `Only me`
  - `Mutuals only`
  - `Public`

### Important wording rule
Use user-facing labels in the app, but keep server enums stable and explicit.

### Important product rule
Even though the settings appear now, the actual follow/search/live-entitlement behavior tied to them is implemented in later phases.
Phase 1 is about storing and editing the settings correctly.

## Task 9 — Keep the current public LiveFollow lane unchanged
Do not change current public behavior in this phase:
- do not change `POST /api/v1/session/start`
- do not change `POST /api/v1/position`
- do not change `POST /api/v1/task/upsert`
- do not change `POST /api/v1/session/end`
- do not change `GET /api/v1/live/active`
- do not change `GET /api/v1/live/{session_id}`
- do not change `GET /api/v1/live/share/{share_code}`

Do not make public watch require sign-in.
Do not leak private-follow assumptions into the current public payloads.

## Task 10 — Add tests
### Server tests
Add coverage for:
- authenticated access required on `/api/v2/*`
- `GET /api/v2/me`
- `PATCH /api/v2/me/profile`
- `PATCH /api/v2/me/privacy`
- handle uniqueness/case normalization
- invalid enum values
- validation errors

### App tests
Add coverage for:
- signed-in / signed-out state transitions
- onboarding happy path
- handle validation / handle-taken error handling
- profile edit save flow
- privacy settings load/save flow
- auth failure / expired session handling

Use repo-native test styles and placement.

## Task 11 — Update docs to match reality
If Phase 1 code changes are implemented, update docs carefully.

### Required rule
Do **not** leave the docs in a state where future proposal docs are the only description of now-deployed behavior.

### Required documentation outcome
If `/api/v2/me` / `/api/v2/me/profile` / `/api/v2/me/privacy` become real server behavior, document them as deployed reality somewhere current.

Preferred approach:
- create a current deployed contract doc for the private-follow/account lane, or
- version-bump the current deployed contract owner if the team intentionally wants one combined current owner

But do **not** present future `/api/v2` proposals as deployed unless they are actually implemented and tested.

### Also update if needed
- `README_current.md` if the current LIVEFOLLOW canon changes
- private-follow proposal docs if implementation choices differ from the proposal

## Constraints
- implement Phase 1 only
- do not silently implement Phase 2 features
- do not break the public LiveFollow lane
- do not require classic email/password unless the repo already has durable support for it and you intentionally choose to use it
- do not invent insecure auth just to get the phase over the line
- do not rewrite current deployed docs speculatively
- follow existing repo patterns, naming, and architecture where possible
- report any external setup dependency clearly (for example provider console setup)

## Acceptance criteria
Phase 1 is complete when:
- signed-in XCPro account state exists in the app
- the server can resolve a signed-in user to a stable `user_id`
- user profile fields can be created/read/updated
- privacy settings can be created/read/updated
- handle uniqueness is enforced
- app onboarding/profile/privacy flows work end to end
- no search/follow/request/follower-only-live behavior is shipped yet
- current public LiveFollow behavior still works unchanged
- relevant tests were added and passed
- docs were updated to reflect any newly deployed `/api/v2` behavior

## Report back expected from Codex
When done, report back with:
- auth/account infrastructure found and reused
- server schema/entities added
- exact `/api/v2` endpoints implemented
- exact app screens/flows added
- any provider-specific external setup still required
- exact docs updated
- exact tests run
- any intentionally deferred items that remain for Phase 2
