# LiveFollow Phase 5 — Current API Integration Codex Prompts v3

## Pass 1 — contract extraction / audit

Use this prompt first:

```text
Review the current LiveFollow state and propose the Phase 5A contract-freeze plan for the
currently deployed API only.

Read and follow:
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v12_Current_API_Integration.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v3.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v8.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/ServerInfo.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Freeze the exact current deployed API contract before implementation.

Do not implement yet.

I want a short audit that answers:
1. What exact request/response bodies are currently implemented for:
   - POST /api/v1/session/start
   - POST /api/v1/position
   - POST /api/v1/task/upsert
   - POST /api/v1/session/end
   - GET /api/v1/live/{session_id}
   - GET /api/v1/live/share/{share_code}
2. Which fields are server-owned vs client transport-local?
3. What telemetry upload fields and ordering semantics are already implemented?
4. What follow/read flow is actually implemented today?
5. What server gaps remain before XCPro can replace the unavailable adapters?
6. What exact docs should be updated in the same PR?

Constraints:
- Use the current deployed API as authoritative
- Treat `docs/LIVEFOLLOW/ServerInfo.md` as factual server context and provenance, not as the contract owner
- Do not use the older /api/v1/client/sessions/... API for current implementation work
- Do not implement yet
- Do not redesign app-side ownership
- Do not ask me to create files manually

Deliver:
- extracted current wire contract summary
- server-owned vs client-local field split
- remaining gaps
- recommended safe implementation order
- docs to update in the same PR
```

## Pass 2 — current API client integration

Only after Pass 1 is approved.

```text
Implement Phase 5B against the currently deployed LiveFollow API.

Read and follow:
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v12_Current_API_Integration.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v3.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v8.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/ServerInfo.md
- docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v1.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Replace the unavailable LiveFollow transport adapters in XCPro using the current deployed API.

Allowed scope:
- real LiveFollowSessionGateway implementation for current deployed API
- telemetry upload adapter for current deployed API
- follow/read adapter for current deployed API
- mapping of server DTOs into existing repository interfaces
- deterministic tests
- minimal module wiring required for build/test
- docs sync in the same PR

Forbidden:
- do not target /api/v1/client/sessions/... paths
- no backend path-family refactor
- no notifications/FCM
- no share-link product expansion
- no task ownership changes
- no second ownship pipeline
- no map ownership changes
- no hidden fake transport behavior

Requirements:
- preserve existing app-side repository/viewmodel ownership
- keep transport-local state local
- keep monotonic freshness logic local
- use wall-time/ordering semantics from the server contract
- use `ServerInfo.md` only as factual server context; contract ownership stays in `LiveFollow_Current_Deployed_API_Contract_v1.md`
- update PIPELINE.md if runtime wiring changes
- run:
  - ./gradlew enforceRules
  - ./gradlew testDebugUnitTest
  - ./gradlew assembleDebug

Deliver:
1. files changed
2. exact adapters implemented
3. endpoints used
4. tests added
5. whether any remaining server gaps still block full end-to-end use
6. exact docs updated
```
