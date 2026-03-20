# LiveFollow Phase 5 — Server/Transport Codex Prompts (v2)

Use these prompts only **after** the app-side LiveFollow slice is merged through Phase 4.

The correct order is:
1. shared contract seam/audit pass
2. server/backend implementation pass
3. XCPro client transport integration pass
4. app/server contract audit pass

Do not collapse all four into one giant Codex task.

---

## Pass 1 — Shared contract seam/audit

```text
Review the current LiveFollow state after Phase 4 and propose the shared app/server contract plan only.

Read and follow:
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Server_Compatibility_Investigation_2026-03-19.md
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v11_Server_Transport.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v7.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Design the next transport track only.

Do not implement yet.

I want a short audit that answers:
1. What app-side LiveFollow state is already fixed and must be preserved?
2. What exact shared contract is still missing for:
   - session lifecycle transport
   - authoritative session-state mirror
   - pilot telemetry upload
   - direct-watch feed
   - auth/authorization
   - error mapping
3. Which fields are server-owned truth and which are client transport-local state?
4. What time semantics should be used on the wire vs inside XCPro?
5. What is the minimum safe implementation order?

Constraints:
- Do not redesign app-side ownership already merged in XCPro
- Do not treat callsign/CN/registration as authoritative identity
- Do not assume public OGN-only watch behavior is enough
- Do not require cross-device monotonic timestamps as canonical shared truth
- Do not implement yet
- Do not ask the human to create files manually

Deliver:
- proposed shared contract plan
- exact server-owned vs client-owned field split
- contract gaps
- implementation order
- docs that must be updated before coding
```

---

## Pass 2 — Server/backend implementation

```text
Implement the server/backend side of the approved LiveFollow transport contract.

Read and follow:
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Server_Compatibility_Investigation_2026-03-19.md
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v11_Server_Transport.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v7.md

Goal:
Implement only the server/backend transport slice.

Required scope:
- session lifecycle transport
- authoritative session-state transport
- telemetry ingest/upload path
- direct-watch feed path
- auth/authorization + error mapping

Forbidden:
- no UI work
- no app-side ViewModel changes
- no fake success transport
- no callsign-only authoritative identity
- no replay-mode side effects
- no assumption that public OGN alone replaces direct watch

Important:
- keep server-owned truth and client transport-local state separated
- do not require cross-device monotonic timestamps as canonical shared truth
- make telemetry upload explicit, not implied

Deliver:
1. files changed
2. endpoints/events/contracts implemented
3. auth/error mapping implemented
4. residual risks
5. any docs that must be updated
```

---

## Pass 3 — XCPro client transport integration

```text
Implement the XCPro client transport integration for LiveFollow using the approved shared contract and server implementation.

Read and follow:
- docs/LIVEFOLLOW/livefollow_v2.md
- docs/LIVEFOLLOW/LiveFollow_Server_Compatibility_Investigation_2026-03-19.md
- docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v11_Server_Transport.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md
- docs/LIVEFOLLOW/LiveFollow_Next_Steps_v7.md
- docs/ARCHITECTURE/PIPELINE.md
- AGENTS.md
- AGENT.md
- ARCHITECTURE.md
- CODING_RULES.md
- CODEBASE_CONTEXT_AND_INTENT.md
- CONTRIBUTING.md
- KNOWN_DEVIATIONS.md

Goal:
Replace the explicit unavailable adapters with real transport implementations.

Required scope:
- real `LiveFollowSessionGateway`
- real `DirectWatchTrafficSource`
- explicit pilot telemetry upload adapter
- auth/error mapping into app repository state
- preserve existing app-side ownership and UI state contracts

Forbidden:
- no second ownship pipeline
- no map-owned session truth
- no task ownership changes
- no UI-local freshness/source math
- no fake production fallback
- no replay-mode side effects

Important:
- app transport-local state stays client-owned
- server-owned truth stays server-owned
- app monotonic ms remains internal freshness logic, not shared server truth

Deliver:
1. files changed
2. transport implementations added
3. upload path added
4. tests added
5. docs updated
6. residual risks
```

---

## Pass 4 — App/server contract audit

```text
Review the current XCPro LiveFollow implementation and the implemented server/backend contract against docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md.

Confirm:
- server-owned truth and client transport-local state are clearly separated
- session lifecycle, telemetry upload, and direct-watch feed are explicitly implemented
- typed identity rules align
- wire/server vs app-internal time semantics align
- replay/privacy rules align
- auth/error mapping aligns

Provide:
1. PASS or FAIL
2. exact contract gaps
3. whether the gap is server-side, XCPro-client-side, or shared-contract-side
4. whether end-to-end implementation is actually ready
```
