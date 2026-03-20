# LiveFollow Next Steps v9

## Current state

App-side LiveFollow is complete through Phase 4, and the first XCPro current-API transport slice is now implemented:
- Phase 1: domain logic
- Phase 2: contracts / repository seams
- Phase 3: pilot/watch UI and route wiring
- Phase 4: hardening and doc sync
- Phase 5C slice 1:
  - `POST /api/v1/session/start`
  - `POST /api/v1/position`
  - `POST /api/v1/session/end`
  - `GET /api/v1/live/{session_id}`

This slice stores `session_id`, `share_code`, and `write_token` transport-locally, keeps `write_token`
out of UI state, preserves local freshness derivation, and leaves task upsert, share-code watch,
notifications, and WebSockets for later slices.

## Active baseline docs

Use these as the active baseline:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/ServerInfo.md`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v13_Current_API_Contract_Reconciliation.md`
- `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Role split for the active docs:
- `ServerInfo.md` = factual deployed-server reality and provenance note
- `LiveFollow_Current_Deployed_API_Contract_v2.md` = frozen contract owner for current implementation work
- `XCPro_LiveFollow_Change_Plan_v13_Current_API_Contract_Reconciliation.md` = execution plan
- `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md` = merge gate for contract readiness

Do not use the older `/api/v1/client/sessions/...` design docs for current implementation work.

## Next sequence

### Phase 5A - contract reconciliation
- land `LiveFollow_Current_Deployed_API_Contract_v2.md`
- review the extracted current contract against the checklist
- explicitly decide:
  - write_token storage
  - share_code storage/use
  - first watch key (`share_code` vs `session_id`)
  - upload gating for missing nullable fields
  - speed-unit assumption
- status: completed for slice 1

### Phase 5B - server hardening only if required
Only if Phase 5A says client integration is still blocked.
Likely candidates:
- machine-readable error codes
- minimal contract clarification
- minimal read-shape clarification if truly needed
- status: not required for slice 1

### Phase 5C - XCPro current API client integration
Current slice landed:
- real transport adapters replaced the unavailable session and direct-watch stubs for the first slice
- pilot upload is single-sample `POST /api/v1/position`
- watch polling is direct `GET /api/v1/live/{session_id}`
- existing app-side ownership boundaries remain unchanged

Remaining Phase 5C work:
- task upsert transport
- share-code watch path / share UX
- any richer server read mapping if later slices need it

### Phase 5D - end-to-end verification
- session start / write token
- position upload
- task upsert
- session end
- follow/read by share code
- stale / ended behavior
- replay remains side-effect free

## Remaining gaps after slice 1

The current deployed API still leaves these fuller-integration gaps:

1. current live-read payload is weaker than XCPro's richer direct-watch seam
2. position upload still requires non-null fields where XCPro ownship export is more nullable
3. task upsert is still not wired in XCPro
4. share-code watch/share UX is still not wired in XCPro
5. current server uses string `detail` errors, not stable machine-readable domain codes

## Out of scope right now

- future path-family refactor to `/api/v1/client/sessions/...`
- notifications / FCM
- share-link product expansion beyond the current server
- OGN/FLARM fusion redesign
- major DI/lifecycle refactors

## Human guidance

Use the frozen current-contract docs as the implementation baseline for follow-on slices.

Next focused XCPro tasks should cover:
1. task upsert transport
2. share-code based watch/share UX decisions
3. end-to-end runtime verification against the deployed API
