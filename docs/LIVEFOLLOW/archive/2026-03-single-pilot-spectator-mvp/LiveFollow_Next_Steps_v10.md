# LiveFollow Next Steps v10

## Current state

Current LiveFollow MVP already works for:
- pilot start/stop
- position upload
- watch by session_id
- watch by share_code

## Recommended next product slice

Build **Friends Flying** as:
- bottom-sheet list of currently active pilots
- tap one pilot
- hand off to the existing single-pilot watch flow

This is intentionally smaller than multi-glider map rendering.

## Next sequence

### Phase 6A — server active-pilot list
- add `GET /api/v1/live/active`
- add minimal display label support
- deterministic server tests

### Phase 6B — XCPro bottom-sheet picker
- fetch active pilots
- show bottom-sheet list
- tap -> share-code watch handoff
- deterministic app tests

### Phase 6C — startup chooser UX (optional)
Later:
- logo
- “Flying”
- “Friends Flying”
- Friends Flying opens the picker

## Out of scope
- multi-glider simultaneous map rendering
- friend graph/privacy model
- WebSockets/push
- notifications
- server identity enrichment beyond what the list needs
