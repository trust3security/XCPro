# XCPro LiveFollow Change Plan v14 — Friends Flying List + Single-Pilot Handoff

Date: 2026-03-21
Status: Proposed next product slice after current-API transport slice 2
Supersedes for this feature track: none
Depends on:
- app-side LiveFollow through transport slice 2 being merged
- server-side current API hardening slice 1 being merged

## Purpose

This plan introduces a product-shaped **Friends Flying** mode without jumping straight to
multi-glider map rendering.

The goal of this slice is:

1. the user opens XCPro
2. chooses **Friends Flying**
3. sees a bottom-sheet list of currently flying pilots
4. taps one pilot
5. the app opens the existing single-pilot watch flow for that pilot

This is intentionally a smaller and safer product step than:
- rendering many gliders at once on the map
- adding friend graph/privacy logic
- redesigning the server transport model

---

## Why this slice exists

Current LiveFollow transport work already supports:
- pilot start/stop
- position upload
- single-pilot watch by `session_id`
- single-pilot watch by `share_code`

What is missing is a **user-friendly discovery step** for the viewer.

Instead of asking the user to manually obtain and paste a raw session id, this slice adds:
- a server endpoint for active public pilots
- a UI list picker
- reuse of the existing single-pilot watch path

---

## Product scope

### In scope
- server endpoint to list active public pilots
- small pilot metadata needed for list display
- XCPro bottom sheet showing the active pilot list
- tap one pilot -> open the existing share-code watch route
- keep map render-only
- keep watch ownership in existing LiveFollow seams

### Out of scope
- multi-pilot simultaneous map rendering
- private “friends” graph / auth
- notifications / invites
- WebSockets / push
- richer server-side identity model beyond what is needed for list display
- task metadata expansion
- OGN-backed identity/server fusion work
- startup chooser UI (“Flying / Friends Flying”) if you want to keep this slice smaller

---

## Product decision for this slice

For now, **Friends Flying** means:

> “List all currently visible public LiveFollow pilots the backend can provide.”

This is intentionally broader than a true friend graph.
A future slice can filter this list to actual friend relationships.

---

## Architecture rules that stay true

- ownship truth stays where it is today
- session truth stays in `LiveFollowSessionRepository`
- single-pilot watch arbitration stays in `WatchTrafficRepository`
- map remains render-only
- replay remains side-effect free
- server remains the authority for active session/public-list data
- the bottom sheet is a selector, not a logic owner

---

## Minimal server additions

## New endpoint
Add:

`GET /api/v1/live/active`

This endpoint should return a list of currently visible public pilots that are:
- `active`
- or optionally `stale` if product wants them visible

Ended sessions should not be listed by default.

## Required response shape
Add a contract doc for this endpoint.
Minimum list item fields:

- `session` or `session_id`
- `share_code`
- `status`
- `display_label`
- `last_position_at`
- `latest`
  - `lat`
  - `lon`
  - `alt`
  - `speed`
  - `heading`
  - `timestamp`

Optional:
- `updated_at`
- `sort_key`
- `public_identity_hint` if deliberately supported

## Display label requirement
A useful list requires a human-usable label.

This slice therefore needs one minimal public label source.
Recommended approach:
- extend session start to accept an optional additive field such as:
  - `public_label`
- persist it with the session
- return it in the active-pilots list

Fallback if absent:
- use `share_code`

Important:
- this is a display field only
- it does not replace typed identity rules inside the app

---

## XCPro app additions

### Friends Flying entry
This slice adds a bottom-sheet picker for active pilots.

The picker may be launched from:
- an existing map launcher
- a temporary app entry
- a small top-level button

This slice does not require the final polished startup chooser yet.

### Bottom-sheet contents
Each row should show:
- display label
- status (active/stale)
- last known height if available
- maybe “last seen” if helpful

### Tap behavior
When the user taps a row:
- close the bottom sheet
- navigate to the existing watch-by-share-code flow
- reuse current single-pilot watch rendering

### Internal handoff key
For the first version:
- use `share_code` as the external/public watch token
- keep `session_id` internal where needed
- do not depend on the user seeing or typing a raw session id

---

## Phase order

### Phase 6A — contract + server endpoint
- freeze active-pilots list contract
- add `GET /api/v1/live/active`
- add minimal public label support if needed
- add deterministic server tests

### Phase 6B — XCPro bottom-sheet picker
- fetch active pilot list
- show bottom-sheet UI
- tap -> share-code watch handoff
- deterministic tests
- no map ownership drift

### Phase 6C — startup chooser UX (optional follow-on)
If wanted after 6A/6B:
- app logo at top
- “Flying” button
- “Friends Flying” button
- “Friends Flying” opens the bottom-sheet picker

This keeps the startup chooser separate from the server/list contract work.

---

## Acceptance criteria

This slice is complete only when:
- backend returns active public pilot list
- XCPro shows that list in a bottom sheet
- user can tap one pilot
- app opens existing watch flow for that pilot
- replay remains side-effect free
- no map ownership drift
- no session/watch ownership drift
