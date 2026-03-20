# LiveFollow Next Steps v9

## Current state

App-side LiveFollow is complete through Phase 4:
- Phase 1: domain logic
- Phase 2: contracts / repository seams
- Phase 3: pilot/watch UI and route wiring
- Phase 4: hardening and doc sync

The next track is still **current deployed API integration**, but the real server-code audit now shows
that XCPro transport adapter work is not ready to start blindly.

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

### Phase 5A — contract reconciliation
- land `LiveFollow_Current_Deployed_API_Contract_v2.md`
- review the extracted current contract against the checklist
- explicitly decide:
  - write_token storage
  - share_code storage/use
  - first watch key (`share_code` vs `session_id`)
  - upload gating for missing nullable fields
  - speed-unit assumption
- do not code yet

### Phase 5B — server hardening only if required
Only if Phase 5A says client integration is still blocked.
Likely candidates:
- machine-readable error codes
- minimal contract clarification
- minimal read-shape clarification if truly needed

### Phase 5C — XCPro current API client integration
After 5A passes, or after 5B if needed:
- implement real transport adapters against the deployed API
- replace unavailable adapters
- preserve existing app-side ownership boundaries
- update docs in the same PR

### Phase 5D — end-to-end verification
- session start / write token
- position upload
- task upsert
- session end
- follow/read by share code
- stale / ended behavior
- replay remains side-effect free

## Current proven blockers from the server-code audit

The real server audit found these current mismatches:

1. current live-read payload is weaker than XCPro’s richer direct-watch seam
2. position upload currently requires non-null fields where XCPro ownship export is more nullable
3. session gateway abstraction is not one-to-one with current server endpoints
4. `share_code` and `write_token` are real server outputs but not yet fully surfaced/mapped in XCPro
5. current server uses FastAPI default string `detail` errors, not stable machine-readable domain codes

These blockers should be resolved in docs first, then in code only where necessary.

## Out of scope right now

- future path-family refactor to `/api/v1/client/sessions/...`
- notifications / FCM
- share-link product expansion beyond the current server
- OGN/FLARM fusion redesign
- major DI/lifecycle refactors

## Human guidance

Use a docs-only PR for the contract-reconciliation update first.
Then use a new Codex task for:
1. Pass 1 contract reconciliation review
2. Pass 2A minimal server hardening if required
3. Pass 2B XCPro adapter implementation only after the contract is genuinely ready

Do not start adapter coding on assumptions.
