# LiveFollow Phase 5 — Current API Integration Codex Prompts v4

## Pass 1 — contract reconciliation review

Status: Historical / archived reference
Scope: Phase 5 current deployed API prompt pack used during the first current-API transport integration work
Superseded by: docs/LIVEFOLLOW/LiveFollow_Next_Steps_v10.md and the Friends Flying v14 planning set
Note: Keep for historical reference only; do not use as the active planning baseline

Use this prompt first:

```text
Review the extracted current LiveFollow deployed API contract and decide whether XCPro transport integration can begin cleanly.

Read and follow:
- docs/LIVEFOLLOW/archive/XCPro_LiveFollow_Change_Plan_v13_Current_API_Contract_Reconciliation.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md
- docs/LIVEFOLLOW/archive/LiveFollow_Next_Steps_v9.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/ServerInfo.md
- docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Review the frozen current deployed API contract and decide whether Phase 5B can start, or whether a small Phase 5B-server-hardening step is required first.

Do not implement yet.

I want a short audit that answers:
1. Are the current request/response bodies explicit enough for adapter implementation?
2. Are server-owned vs client-local fields split correctly?
3. Are the adapter decisions explicit enough for:
   - write_token storage
   - share_code storage/use
   - watch key usage (`share_code` vs `session_id`)
   - upload gating for missing nullable app fields
   - speed-unit assumption
4. Is a small server hardening step required before XCPro client transport integration?
5. What exact docs should be updated in the same PR?

Constraints:
- Use the current deployed API as authoritative
- Treat `docs/LIVEFOLLOW/ServerInfo.md` as factual server context and provenance, not as the contract owner
- Do not use the older /api/v1/client/sessions/... API for current work
- Do not implement yet
- Do not redesign app-side ownership
- Do not ask me to create files manually

Deliver:
- PASS/FAIL on “ready for XCPro transport adapter implementation”
- remaining contract gaps, if any
- whether server hardening is required first
- recommended safe implementation order
- docs to update in the same PR
```

## Pass 2A — small server hardening (only if Pass 1 says required)

```text
Implement only the minimum server hardening required before XCPro current-API transport integration can start cleanly.

Read and follow:
- docs/LIVEFOLLOW/archive/XCPro_LiveFollow_Change_Plan_v13_Current_API_Contract_Reconciliation.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md
- docs/LIVEFOLLOW/archive/LiveFollow_Next_Steps_v9.md
- docs/LIVEFOLLOW/ServerInfo.md
- docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md

Goal:
Make the smallest server-side changes required to unblock clean XCPro adapter work.

Allowed examples:
- machine-readable error code addition
- explicit speed-unit clarification in code/docs
- minimal contract cleanup needed for first watch/read integration

Forbidden:
- no endpoint family refactor
- no `/api/v1/client/sessions/...` migration
- no WebSocket redesign
- no notification/FCM work
- no unrelated backend cleanup
- no broad data-model redesign

After implementation, run the smallest relevant verification and report:
- files changed
- exact server hardening implemented
- exact docs updated
- whether XCPro transport adapter work can now start
```

## Pass 2B — XCPro current API client integration

Only after Pass 1 approves direct start, or after Pass 2A completes.

```text
Implement Phase 5C against the currently deployed LiveFollow API.

Read and follow:
- docs/LIVEFOLLOW/archive/XCPro_LiveFollow_Change_Plan_v13_Current_API_Contract_Reconciliation.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md
- docs/LIVEFOLLOW/archive/LiveFollow_Next_Steps_v9.md
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/ServerInfo.md
- docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md
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
- store write_token/share_code according to the frozen decisions
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
