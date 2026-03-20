# LiveFollow Next Steps v8

## Current state

App-side LiveFollow is complete through Phase 4:
- Phase 1: domain logic
- Phase 2: contracts / repository seams
- Phase 3: pilot/watch UI and route wiring
- Phase 4: hardening and doc sync

The next track is no longer generic app-side work.
It is **current deployed API integration**.

---

## Active baseline docs

Use these as the active baseline:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/ServerInfo.md`
- `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v1.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v12_Current_API_Integration.md`
- `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v3.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Role split for the active docs:
- `ServerInfo.md` = factual deployed-server reality and provenance note
- `LiveFollow_Current_Deployed_API_Contract_v1.md` = frozen contract owner for current implementation work
- `XCPro_LiveFollow_Change_Plan_v12_Current_API_Integration.md` = execution plan

Do not use the older `/api/v1/client/sessions/...` design docs for current implementation work.

---

## Next sequence

### Phase 5A — contract freeze
- land `ServerInfo.md` in the repo if not already present
- add/freeze `LiveFollow_Current_Deployed_API_Contract_v1.md`
- review against the contract checklist
- do not code yet

`ServerInfo.md` is the factual server-reality note.
Do not use it as a substitute for the contract doc.

### Phase 5B — XCPro current API client integration
- implement real transport adapters against the deployed API
- replace unavailable adapters
- preserve existing app-side ownership boundaries
- update docs in the same PR

### Phase 5C — server gap fill only if required
- only after 5A/5B reveals concrete server gaps
- do not refactor to the nested future API unless made explicit

### Phase 5D — end-to-end verification
- session start / write token
- position upload
- task upsert
- session end
- follow/read by share code
- stale / ended behavior
- replay remains side-effect free

---

## Out of scope right now

- future path-family refactor to `/api/v1/client/sessions/...`
- notifications / FCM
- share-link expansion beyond the deployed server
- OGN/FLARM fusion redesign
- major DI/lifecycle refactors

---

## Human guidance

Use a docs-only PR for 5A first.
Then use a new Codex task for:
1. Pass 1 contract extraction / audit
2. Pass 2 client integration

Do not mix current deployed API work with future API refactor ideas.
