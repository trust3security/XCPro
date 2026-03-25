# XCPro Private Follow — Phase 3 Codex Brief
## Follower-only live entitlement, session visibility, and authorized watch flow

**Status:** Approved implementation brief for Phase 3  
**Scope:** Implement the private-follow live-entitlement lane on top of the existing Phase 1 auth/profile foundation and the already-implemented Phase 2 search/request/accept/decline relationship graph.  
**Non-goal:** Do not redesign the current public Friends Flying spectator MVP, do not require sign-in for the public watch lane, and do not add push notifications, blocks, or full follower/following list screens in this phase.

## Goal
XCPro now has enough private-follow foundation to stop planning and wire the actual value:

- signed-in XCPro identity
- profile/privacy state
- search
- follow requests
- accept/decline
- auto-approve / `Allow all XCPro followers`
- relationship-state resolution

The missing piece is **live entitlement**.

The goal of this phase is to make those private-follow relationships control **which signed-in XCPro viewers can see which pilot's live XCPro flight while that pilot is actively flying**.

That means this phase must add:

- authenticated live-session ownership
- per-session live visibility (`off | followers | public`)
- follower-only live discovery
- follower-only live reads
- public-lane gating so non-public sessions do not leak into public endpoints
- app UI for pilots to choose visibility and viewers to watch followed pilots who are live

## Product clarification
This private-follow feature is about **viewer entitlement to live flights**.

Roles:
- `pilot` / `owner` = the signed-in XCPro user who is flying and transmitting a live session
- `viewer` / `follower` = the signed-in XCPro user who wants to watch that pilot live

Important rule:
- `search`
- `follow`
- `request approval`
- `auto-approve` / `Allow all XCPro followers`

are all about **which signed-in XCPro viewers may see a pilot's live flight while that pilot is flying with XCPro**.

They are not the same thing as public visibility.

Important distinction:
- `Allow all XCPro followers` = auto-approve signed-in XCPro followers
- `Public` live = anonymous/public visibility through the current public lane

Those are different settings and must stay separate.

## Current baseline to assume
Assume the following is already present in the repo before this phase starts:

### Account/auth foundation
- signed-in XCPro account state exists
- `/api/v2/me*` foundation exists
- Google sign-in uses Credential Manager
- app exchanges Google ID token with XCPro-Server
- XCPro-Server issues the bearer token used by `/api/v2/*`

### Relationship foundation
- `follow_requests` and `follow_edges` exist
- relationship states already include:
  - `none`
  - `outgoing_pending`
  - `incoming_pending`
  - `following`
  - `followed_by`
  - `mutual`
- server endpoints already exist for:
  - search
  - create follow request
  - incoming requests
  - outgoing requests
  - accept
  - decline
- app-side Manage Account already exposes:
  - search
  - incoming requests
  - outgoing requests

### Public lane to preserve
The current public LiveFollow lane still exists and must continue to work:

- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

Do not repurpose that public lane into a signed-in-only feature.

## Desired end state
After Phase 3 is complete, XCPro should support this end-to-end behavior:

1. A signed-in pilot can start a live session in the private-follow lane with:
   - `off`
   - `followers`
   - `public`

2. The server stores:
   - stable `owner_user_id`
   - effective session `visibility`

3. A session with visibility `followers`:
   - is visible to the owner
   - is visible to approved followers
   - is **not** visible in the public active list
   - is **not** readable through the public read endpoints
   - has no public share code

4. A session with visibility `off`:
   - is visible only to the owner
   - is not visible to followers
   - is not visible publicly

5. A session with visibility `public`:
   - remains visible through the current public lane
   - may have a public `share_code`
   - may also be readable through the authenticated `/api/v2` lane

6. A signed-in viewer can list currently live pilots they are allowed to see via a follower-aware endpoint.

7. A signed-in authorized viewer can open a followed pilot's live watch session.

8. Existing `POST /api/v1/position`, `POST /api/v1/task/upsert`, and `POST /api/v1/session/end` continue to work with the returned write token.

9. No push notifications, block/report/mute, or follower/following list screens are required in this phase.

## Recommended implementation approach
Build the private-follow live lane as a **parallel authenticated lane** on top of the existing live session model.

### Preferred endpoint shape
Use:

- `POST /api/v2/live/session/start`
- `PATCH /api/v2/live/session/{session_id}/visibility`
- `GET /api/v2/live/following/active`
- `GET /api/v2/live/users/{user_id}`
- `GET /api/v2/live/session/{session_id}`

Keep the current write path:
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

That allows the app to start an authenticated owned session without rewriting the already-working uploader path.

## Task 1 — Audit the current live/session implementation before changing it
Before coding, inspect both repos and answer these questions:

### Server
- What table/model currently owns live sessions?
- Where are `share_code`, `write_token`, `status`, and liveness fields stored?
- How does `GET /api/v1/live/active` currently decide what is public?
- How do current public live reads work?
- Is there already any notion of owner user identity on sessions?
- Is there already any safe place to add `visibility`?

### App
- Where does XCPro currently call `POST /api/v1/session/start`?
- Which UI currently starts a LiveFollow session?
- Where can a minimal visibility selector fit without redesigning the public spectator UI?
- Which watch flow currently consumes public live reads by `session_id` or `share_code`?

Report back clearly with what existing code paths were reused.

Do not skip this audit.

## Task 2 — Add server session ownership and visibility foundation
In XCPro-Server, extend the live-session model so follower-only entitlement can be enforced.

### Required server-side additions
Add whatever schema changes are required so a live session can store:

- `owner_user_id`
- `visibility = off | followers | public`

### Compatibility rule
Preserve compatibility with the current public lane.

That means:
- existing public v1 sessions must still work
- a session created through the private-follow lane can still reuse the current write-token model
- anonymous/public v1 sessions must not be broken by the new ownership fields

### Recommended ownership rule
For sessions created through `POST /api/v2/live/session/start`:
- `owner_user_id` is required
- `visibility` is explicit or derived from the caller's `default_live_visibility`

For legacy/current public `POST /api/v1/session/start`:
- preserve existing behavior
- do not make auth mandatory there in this phase

### Session selection rule
If a user can end up with multiple non-ended sessions, choose one deterministic read rule and document it.
Preferred long-term rule:
- at most one active/stale owned session per user

If enforcing that now is too risky, implement a deterministic fallback and report it clearly.

## Task 3 — Add authenticated live-session start
Implement:

### `POST /api/v2/live/session/start`
Auth required.

### Request body
Allow:
```json
{
  "visibility": "followers"
}
```

`visibility` may be omitted, in which case the server uses the caller's `default_live_visibility`.

### Required behavior
- caller becomes the session owner
- response includes:
  - `session_id`
  - `status`
  - `visibility`
  - `write_token`
  - `share_code`
- if `visibility = public`, return a public `share_code`
- if `visibility = followers` or `off`, return `share_code: null`
- returned `write_token` must work with the existing write endpoints

### Required response example
```json
{
  "session_id": "uuid4",
  "status": "active",
  "visibility": "followers",
  "share_code": null,
  "write_token": "opaque"
}
```

### Important constraint
Do not silently change `POST /api/v1/session/start` semantics in this phase.

## Task 4 — Add live-session visibility update
Implement:

### `PATCH /api/v2/live/session/{session_id}/visibility`
Auth required.

### Request body
```json
{
  "visibility": "public"
}
```

### Required behavior
- owner only
- owner can switch between:
  - `off`
  - `followers`
  - `public`
- switching from `public` to `followers` or `off` removes public visibility immediately
- switching to `public` generates or reactivates a public `share_code`
- switching to `followers` or `off` must not leave a valid public watch path behind

### Recommended response
```json
{
  "session_id": "uuid4",
  "status": "active",
  "visibility": "public",
  "share_code": "AB12CD34"
}
```

## Task 5 — Add authenticated follower-only discovery and reads
Implement these endpoints:

### `GET /api/v2/live/following/active`
Auth required.

This endpoint returns live sessions that the caller is currently authorized to watch because:
- they follow the pilot and the pilot's session visibility is `followers`, or
- the session is `public` and the caller is also allowed through the authenticated lane

### Minimum response shape
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
        "comp_number": "JP"
      },
      "relationship_state": "following",
      "latest": {
        "lat": -37.0,
        "lon": 145.0,
        "alt": 1245.0,
        "agl_meters": 423.0,
        "speed": 18.2,
        "heading": 142.0,
        "timestamp": "2026-03-24T05:10:00Z"
      }
    }
  ],
  "next_cursor": null
}
```

### `GET /api/v2/live/users/{user_id}`
Auth required.

Required behavior:
- resolve the target user's current active/stale session
- owner may always read their own session
- followers-only sessions require an approved follow edge
- pending requests are not authorized
- `off` visibility is owner-only
- if the user has no current session, return a machine-readable error

### `GET /api/v2/live/session/{session_id}`
Auth required.

Required behavior:
- return the session if the caller is authorized
- owner may always read their own session
- authorized followers may read a followers-only session
- public sessions may also be readable through the current public endpoints

### Read payload rule
Use one consistent authenticated live-read shape.
It should include at minimum:
- session identity
- owner identity
- visibility
- live status
- latest
- positions
- task

Do not make the app stitch together separate DTOs to render one watch session.

## Task 6 — Enforce public-lane gating correctly
This is a core requirement of the phase.

### Required public-lane behavior after Phase 3
A session with visibility `public`:
- may appear in `GET /api/v1/live/active`
- may be readable by `GET /api/v1/live/{session_id}`
- may be readable by `GET /api/v1/live/share/{share_code}`

A session with visibility `followers` or `off`:
- must **not** appear in `GET /api/v1/live/active`
- must **not** be readable by the public session-id endpoint
- must **not** be readable by the public share-code endpoint

### Error rule
For public attempts to read a non-public session, return a clear, tested, documented result.
Preferred behavior:
- `404`
- machine-readable code, preferably `session_not_visible`

If the current codebase strongly prefers `session_not_found`, that is acceptable only if:
- it is consistent
- it is tested
- it is documented in the repo-state docs

### Important constraint
Do not break the current public behavior for genuinely public sessions.

## Task 7 — Add minimal pilot-side app UI for live visibility
Add the smallest durable pilot-side UI needed to control session visibility.

### Required pilot behaviors
A signed-in pilot must be able to:
- start live with:
  - `Off`
  - `Followers`
  - `Public`
- see the currently active visibility for the session
- change session visibility while the session is active

### Preferred UI approach
Use the existing LiveFollow entry/start surface and existing in-flight LiveFollow controls if possible.

Good options:
- add a visibility selector to the startup chooser / start flow
- add a compact in-session control to change visibility without redesigning Flying mode

### Constraints
- keep the UI small and durable
- do not redesign the whole LiveFollow pilot UI in this phase
- do not tie this phase to the public spectator Task-tab work

## Task 8 — Add minimal viewer-side app UI for followed pilots who are live
This phase must give viewers a usable path to watch followed pilots who are currently live.

### Preferred approach
Reuse the relationship/account surface that already exists instead of redesigning Friends Flying.

Unless the repo already has a better entry point, add:
- a `Following Live` or `Live Now` section within Manage Account or the existing private-follow area
- list rows sourced from `GET /api/v2/live/following/active`
- tap-to-watch routing into the existing single-watched-pilot watch experience

### Required viewer behaviors
A signed-in viewer must be able to:
- see followed pilots who are currently live and visible to them
- tap one pilot
- open the watch flow
- have the watch flow use the authenticated live read endpoint when needed

### Sign-in behavior
If the viewer is signed out:
- do not show broken private-follow live UI
- show a clear sign-in CTA where appropriate

### Scope guard
Do not redesign the public Friends Flying bottom-sheet tabs in this phase.

## Task 9 — Keep the current write flow intact
Unless there is a hard technical blocker, preserve:
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`

The authenticated `POST /api/v2/live/session/start` should hand back the token/session information needed to continue using the current uploader path.

Report clearly if any of those write paths had to change.

## Task 10 — Add tests
### Server tests
Add or update coverage for:
- authenticated `POST /api/v2/live/session/start`
- visibility defaulting from profile
- `PATCH /api/v2/live/session/{session_id}/visibility`
- public session gets share code
- `followers` / `off` sessions do not get public share codes
- owner can read own session
- approved follower can read followers-only session
- pending requester cannot read followers-only session
- unrelated signed-in user cannot read followers-only session
- `off` visibility blocks non-owner reads
- `GET /api/v2/live/following/active` includes only authorized sessions
- `GET /api/v1/live/active` excludes followers-only/off sessions
- public `GET /api/v1/live/{session_id}` and `GET /api/v1/live/share/{share_code}` reject non-public sessions
- public sessions still behave normally

### App tests
Add or update coverage for:
- pilot start flow with visibility selection
- in-session visibility update flow
- viewer `Following Live` list load/empty/error states
- tap-to-watch flow for an authorized followed pilot
- signed-out viewer behavior
- unauthorized watch error handling
- regression coverage for the existing public watch path if app code touches it

Use repo-native test placement and style.

## Task 11 — Update docs so current reality is not ambiguous
This phase must leave the docs clearer, not messier.

### Required private-follow repo-state update
Update the current private-follow repo-state doc if it exists.
Preferred target:
- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md`

If the repo prefers date-bumped snapshots instead of in-place updates, create a new dated repo-state doc and update references accordingly.

The current repo-state doc should say:
- what account/auth behavior now exists
- what relationship behavior now exists
- what follower-only live behavior now exists
- which endpoints now exist in repo code
- what is still not yet implemented:
  - push notifications
  - block/remove follower if still missing
  - follower/following list screens if still missing
  - live-now alerts
  - watcher counts
  - any remaining polish

### Required README update
Update:
- `docs/LIVEFOLLOW/README.md`

It should now clearly point readers to:
1. public LiveFollow current canon
2. private-follow product/API/change-plan docs
3. private-follow current repo-state doc
4. archive location for completed phase-specific execution briefs/checklists

### Contract ownership rule
Do **not** update `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md` to claim new private-follow behavior unless the server is actually deployed and verified as deployed reality.

If new authenticated `/api/v2/live/*` behavior exists in repo code but is not yet a deployed public contract, document it in:
- the private-follow repo-state doc, and
- the proposed private-follow contract doc if implementation choices diverged

Do not present repo-only or future behavior as deployed reality.

## Task 12 - Archive completed phase-specific planning docs
After Phase 3 lands, archive old execution docs that are now historical.

### Archive location used by the docs cleanup
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`

### Historical docs already archived there
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/XCPro_Private_Follow_Phase1_Accounts_Profile_Codex_Brief_2026-03-23.md`
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/XCPro_Private_Follow_Phase1_Review_Checklist_v1.md`

### Keep active only if they still own unique current behavior
- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md` while it remains
  the owner for Google sign-in and XCPro-Server exchange environment/setup
  requirements
- any private-follow Phase 2 execution brief/checklist if one exists and is
  not yet superseded by the current repo-state doc and change plan
- any private-follow one-off review prompt that is still needed as an active
  owner rather than archived historical context

### Keep active
Do **not** archive these unless there is a deliberate replacement:
- `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
- `XCPro_Private_Follow_Change_Plan_2026-03-23.md`

### Architecture doc audit
If this file exists:
- `docs/ARCHITECTURE/CHANGE_PLAN_PRIVATE_FOLLOW_GOOGLE_SERVER_AUTH_2026-03-24.md`

then audit it.

Keep it active only if it still owns durable architecture rules not already captured elsewhere.
Otherwise archive it under the repo's normal architecture-doc archive pattern and mention that in the report.

## Constraints
- implement Phase 3 only
- do not silently expand into notifications, block/report/mute, or full social/friends redesign
- do not break the current public LiveFollow lane
- do not require sign-in for public watch
- do not mix deployed public contract docs with repo-only future/private behavior
- keep `Allow all XCPro followers` separate from `Public`
- keep follower counts separate from current watcher counts
- prefer small durable UI changes over large redesigns
- document any intentionally deferred work clearly

## Acceptance criteria
Phase 3 is complete when:

- a signed-in pilot can start a live session with `off | followers | public`
- the server stores owner identity and session visibility
- public sessions still work through the existing public lane
- followers-only/off sessions do not leak into the public lane
- an approved follower can discover and open a live followed pilot session
- an unauthorized viewer cannot read a followers-only session
- the owner can always read their own session
- the existing write flow remains usable
- relevant tests were added and passed
- docs were updated so current repo behavior and archive status are clear

## Report back expected from Codex
When done, report back with:

- exact schema/model changes for session ownership/visibility
- exact `/api/v2/live/*` endpoints implemented
- exact public-lane gating changes
- exact app screens/flows added or updated
- whether the write flow stayed on the current v1 uploader/task/end path
- exact `.md` files updated
- exact `.md` files archived and the archive folder created
- exact tests run
- any intentionally deferred items that remain for the next phase
