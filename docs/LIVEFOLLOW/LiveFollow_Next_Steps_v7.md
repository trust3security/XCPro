# LiveFollow — What to do next (v7)

Date: 2026-03-19
Status: Updated after the app-side LiveFollow slice was hardened through Phase 4

## Active baseline

Use these as the active baseline for the next LiveFollow track:
- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/LiveFollow_Server_Compatibility_Investigation_2026-03-19.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v11_Server_Transport.md`
- `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md`
- `docs/LIVEFOLLOW/LiveFollow_Phase5_Server_Transport_Codex_Prompts_v2.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Treat older server/transport plan wording as historical context only.

---

## What is already done

### Phase 1 — Pure domain logic
Completed and merged:
- typed identity resolution
- source arbitration
- deterministic session state machine
- replay-safe policy
- deterministic tests

### Phase 2 — Contracts / repository seams
Completed and merged:
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`
- seam models
- deterministic repository tests

### Phase 3 — Viewer / pilot route wiring
Completed and merged:
- pilot/watch routes
- pilot/watch ViewModels and UI state
- thin map-host render consumption
- explicit unavailable adapters
- runtime path documented in `PIPELINE.md`

### Phase 4 — Hardening and final app-side doc/runtime sync
Completed and merged:
- explicit owner-provided transport availability
- replay/privacy verification through repository and ViewModel wiring
- route/lifecycle hardening
- deterministic JVM test gap closure
- final app-side runtime/doc sync

---

## What the app is now

The current XCPro LiveFollow slice is:

- architecture-ready
- UI/state-machine ready
- hardened on the app side
- still transport-limited until real server/client transports are implemented

The app still uses explicit unavailable adapters for:
- session transport
- direct-watch transport

That is intentional and should not be hidden.

---

## Immediate next step

Freeze the **shared app/server contract** before writing real transport code.

Do not start with Retrofit/WebSocket/client code first.
Do not start with server endpoints first without freezing the contract.
Do not treat the investigation summary as the final wire spec by itself.

Use:
- `XCPro_LiveFollow_Change_Plan_v11_Server_Transport.md`
- `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md`
- `LiveFollow_Phase5_Server_Transport_Codex_Prompts_v2.md`

---

## Next track after Phase 4

### Phase 5A — Shared contract freeze
Next:
- separate server-owned truth from client transport-local state
- freeze session lifecycle contract
- freeze telemetry upload contract
- freeze direct-watch feed contract
- freeze auth/error mapping
- freeze time semantics split

### Phase 5B — Server/backend implementation
After contract freeze:
- implement session lifecycle transport
- implement telemetry ingest/upload
- implement direct-watch feed
- implement auth/authorization + error mapping

### Phase 5C — XCPro client transport integration
After server contract is real:
- replace `UnavailableLiveFollowSessionGateway`
- replace `UnavailableDirectWatchTrafficSource`
- add real telemetry upload adapter
- preserve existing app-side ownership and replay/privacy behavior

### Phase 5D — End-to-end verification
After both sides are implemented:
- app/server contract checklist pass
- real start/stop/join/leave proof
- telemetry upload proof
- direct-watch proof
- replay/privacy non-regression proof

---

## Architectural mistake still to avoid

Do not blur:
- server/backend truth
- XCPro client transport-local state

Examples of mistakes:
- treating `transportAvailability` as if it must always be a canonical server-owned field
- requiring cross-device monotonic timestamps as shared wire truth
- leaving telemetry upload implicit
- assuming public OGN-only watch behavior is enough
- reintroducing callsign/CN/registration as authoritative session identity

---

## What to tell Codex now

For the next LiveFollow track:
- audit the app-side state first
- freeze the shared contract first
- keep server and XCPro transport work reviewable as separate slices
- keep typed identity authoritative
- keep telemetry upload explicit
- keep replay side-effect free
- keep app monotonic freshness logic internal to XCPro
- run the contract checklist before claiming interoperability
