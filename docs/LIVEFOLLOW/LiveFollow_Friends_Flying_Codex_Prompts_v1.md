# LiveFollow Friends Flying — Codex Prompts v1

## Pass 1 — server/list seam audit

```text
Review the current XCPro-Server and XCPro app state and propose the Friends Flying list slice only.

Do not implement yet.

Goal:
Design the smallest next slice that adds:
- active public pilot list
- XCPro bottom-sheet picker
- tap -> existing single-pilot watch flow

I want you to answer:
1. What exact server endpoint and response shape should be added?
2. What minimal public label strategy should be used?
3. What exact XCPro app files should own the bottom-sheet picker?
4. How should tap hand off into the existing share-code watch flow?
5. What should remain explicitly out of scope for this slice?

Constraints:
- no multi-pilot map rendering
- no friend graph/privacy system
- no WebSockets
- no new ownership drift
- do not implement yet

Deliver:
- file/module ownership plan
- proposed endpoint shape
- required tests
- minimum safe order
```

## Pass 2A — XCPro-Server implementation

```text
Implement the LiveFollow Friends Flying server slice.

Repo/branch:
- XCPro-Server repo
- create a dedicated branch for this slice

Goal:
Add GET /api/v1/live/active and the minimal label support required for a useful active pilot list.

Required scope:
- GET /api/v1/live/active
- active/stale filtering
- display_label support
- deterministic tests

Forbidden:
- no endpoint redesign
- no friend-graph auth
- no WebSockets
- no push notifications
- no broad contract redesign

Deliver:
1. files changed
2. exact endpoint added
3. exact response shape
4. tests added
5. whether the slice is mergeable
```

## Pass 2B — XCPro app implementation

```text
Implement the LiveFollow Friends Flying bottom-sheet picker slice in the XCPro app repo.

Goal:
Show active pilots in a bottom sheet and hand off to the existing share-code watch flow.

Required scope:
- bottom-sheet list UI
- fetch active pilots list
- row tap -> existing share-code watch route
- deterministic tests
- keep map render-only

Forbidden:
- no multi-glider map rendering
- no task ownership drift
- no second ownship pipeline
- no WebSocket work
- no privacy/friend-graph implementation

Deliver:
1. files changed
2. exact UI added
3. exact handoff route used
4. tests added
5. whether the branch is mergeable
```
