# XCPro LiveFollow Change Plan v11 — Server/Transport Track

Date: 2026-03-19
Status: Active baseline for the next post-Phase-4 track
Supersedes for active use: `XCPro_LiveFollow_Change_Plan_v10_Server_Transport.md`

## Purpose

This version updates the post-Phase-4 plan after reviewing the app-side implementation and the
`LiveFollow_Server_Compatibility_Investigation_2026-03-19.md` investigation summary.

It locks down the next track as a **shared contract + server transport** phase and makes one
important separation explicit:

- **server/backend truth** is not the same thing as
- **XCPro client transport state**

That distinction was too loose in earlier wording.

---

## Active baseline

Use these docs together:

- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/LiveFollow_Server_Compatibility_Investigation_2026-03-19.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v11_Server_Transport.md`
- `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md`
- `docs/LIVEFOLLOW/LiveFollow_Phase5_Server_Transport_Codex_Prompts_v2.md`
- `docs/LIVEFOLLOW/LiveFollow_Next_Steps_v7.md`
- `docs/ARCHITECTURE/PIPELINE.md`

Treat older server/transport plan wording as historical context only.

---

## What is already established and must not regress

These app-side facts are already true after Phase 4:

- XCPro is **architecture-ready but transport-limited**
- `LiveOwnshipSnapshotSource` exports normalized ownship telemetry from the existing flight SSOT
- `LiveFollowSessionRepository` is the Android-side local session mirror owner
- `WatchTrafficRepository` is the watch arbitration owner
- pilot/watch ViewModels and routes are present
- unavailable adapters are explicit and honest
- replay remains side-effect free
- map/runtime remains render-only
- task truth remains external

This plan must not re-open app-side ownership questions. It must only define and implement
the next transport-capable track cleanly.

---

## Terminology (mandatory)

### 1. Server/backend
The real XCPro-side service that owns authoritative live session truth.

### 2. XCPro transport client
The Android-side implementation that replaces:
- `UnavailableLiveFollowSessionGateway`
- `UnavailableDirectWatchTrafficSource`

### 3. Shared contract
The wire-level agreement between server and XCPro client.

Do not use the word **backend** to mean both the server and the Android transport client.
Keep them separate in docs, code, prompts, and review.

---

## Current known reality

The investigation summary is still correct at a high level:

- XCPro currently does **not** ship a real backend session transport
- XCPro currently does **not** ship a real direct-watch transport
- the app will currently surface **unavailable** instead of talking to a server
- a real next step must include:
  1. a real `LiveFollowSessionGateway`
  2. a real `DirectWatchTrafficSource`
  3. a concrete wire protocol / endpoint contract
  4. auth/authorization and error mapping
  5. pilot telemetry upload

This plan keeps those points, but tightens who owns what.

---

## Ownership split for the next track

### Server/backend owns
Server-owned truth must include:

- live session lifecycle
- pilot/watch membership and authorization
- direct-watch authorization / entitlement
- authoritative session status
- authoritative watcher session binding
- telemetry ingest acceptance/rejection
- direct-watch event/feed production
- session event history if later retained

### XCPro transport client owns
Client transport-local state must include:

- network availability/unavailability as observed by the client
- retry/backoff state
- client-mapped transport error text
- local transport diagnostics
- mapping server failures into app-facing repository state

### Important
Do **not** make `transportAvailability` and `lastError` canonical server-owned wire fields by default.

They may exist in XCPro app state, but they are usually derived from:
- client connection state
- HTTP/WebSocket failure state
- client retry/error mapping

Server-owned payloads should focus on:
- session truth
- authorization
- server-originated state/capabilities
- direct-watch permission/availability at the domain level

---

## Shared contract rules

### 1. Typed identity is authoritative
The contract must support typed canonical identity.

Authoritative identity examples:
- `FLARM:HEX`
- `ICAO:HEX`
- `XC_CUSTOM:...` only if explicitly supported

Alias data such as callsign/CN/registration may be carried as non-authoritative support only.

### 2. Telemetry upload is mandatory
The contract must explicitly include a pilot telemetry upload path.

Do not assume `startPilotSession` is enough.
Do not leave upload as implied future work.

### 3. Watch source model must support both paths
The watch contract must not assume public OGN traffic alone is sufficient.

The system must support:
- OGN typed-target candidate path
- direct-watch stream path
- server/session truth that authorizes and shapes the watch experience

### 4. Replay must remain side-effect free
Replay mode must never:
- start a real session
- upload telemetry
- mutate backend state
- silently fall back to real transport

### 5. Time semantics must be split correctly
Inside XCPro, freshness/stale/offline logic remains monotonic-ms based.

Across the wire:
- do **not** require cross-device monotonic timestamps as canonical truth
- prefer wall/server timestamps plus ordering/sequence semantics
- allow XCPro to map incoming events into its own monotonic freshness logic locally

A future wire contract may carry:
- event wall timestamp
- fix wall timestamp
- sequence/event ordering token

But app-internal monotonic age remains app logic, not shared server truth.

---

## Required shared contract surface

### A. Session lifecycle operations
At minimum:
- start pilot session
- stop pilot session
- join watch session
- leave watch session

### B. Authoritative session state mirror
The client must be able to learn, directly or indirectly:
- `sessionId`
- role (pilot/watcher)
- lifecycle/state
- watch identity / target identity
- whether direct watch is authorized
- any authoritative domain-level availability/degraded flags
- server-declared failure reasons where relevant

### C. Pilot telemetry ingest
At minimum:
- canonical typed identity
- SI-unit position/speed/altitude fields
- optional alias/support data
- wall/fix timestamp(s) and/or server ordering semantics
- task/session association if required by the agreed contract

### D. Direct-watch feed
At minimum:
- canonical typed identity
- position sample fields
- freshness/order semantics
- source state/confidence or equivalent explicit feed semantics

### E. Auth/authorization
Must be specified before claiming end-to-end interoperability.

At minimum:
- who can start a session
- who can join a watch session
- who can receive direct-watch data
- how auth failures map into app state

---

## Phase structure for the next track

### Phase 5A — Shared contract freeze
Goal:
- produce the final app/server contract before transport code is written

Deliver:
- contract checklist pass
- server-owned vs client-owned field split
- endpoint/event definitions at a wire-contract level
- telemetry upload contract
- auth/error mapping rules

### Phase 5B — Server/backend implementation
Goal:
- implement real server-side session transport and telemetry/direct-watch support

Allowed:
- server session lifecycle
- telemetry ingest
- direct-watch event/feed
- auth/authorization
- domain/server persistence as required

Not allowed:
- changing app-side LiveFollow ownership rules
- callsign-only authoritative binding
- OGN-only simplification that breaks direct-watch architecture

### Phase 5C — XCPro client transport integration
Goal:
- replace unavailable adapters with real transport implementations

Allowed:
- real `LiveFollowSessionGateway`
- real `DirectWatchTrafficSource`
- pilot telemetry upload adapter
- client error mapping
- explicit retry/unavailable handling

Not allowed:
- new app-side ownership drift
- moving session truth into UI or map
- reintroducing a second ownship pipeline

### Phase 5D — End-to-end verification
Goal:
- prove the server and app actually interoperate

Deliver:
- app/server contract checklist pass
- real session start/stop/join/leave proof
- pilot telemetry upload proof
- direct-watch feed proof
- replay/privacy non-regression proof

---

## Minimal safe implementation order

1. Freeze the shared contract first
2. Implement server-side session lifecycle + telemetry ingest + direct-watch feed
3. Implement auth/authorization and error mapping
4. Implement the XCPro transport client
5. Run end-to-end verification
6. Only then consider notifications/share-link delivery

---

## Explicitly out of scope for this plan

Do not mix these into the contract freeze or first server transport pass:

- leaderboard/history features
- public share-link UX
- FCM delivery implementation
- backend-driven replay/history expansion
- task ownership changes
- map ownership changes
- second ownship pipeline
- callsign-only authoritative binding

---

## Review gate for the next track

The next track is ready to start coding only if:

- the app/server contract checklist passes
- server-owned truth is clearly separated from client transport state
- telemetry upload is explicit, not implied
- typed identity is preserved
- replay/privacy guarantees remain intact
- time semantics are split correctly between app-internal monotonic logic and wire/server semantics

---

## Human responsibility vs Codex responsibility

### Human responsibility
The human should:
1. freeze the contract before asking for broad implementation
2. reject any prompt/result that blurs server vs client transport ownership
3. keep backend and XCPro client work separated enough to review
4. verify end-to-end behavior before claiming interoperability

### Codex responsibility
Codex should:
1. audit the current app-side state first
2. identify contract gaps precisely
3. implement the smallest server-side slice that matches the contract
4. implement the smallest XCPro transport client that matches the same contract
5. report residual risks honestly

---

## Bottom line

The next phase is not “more LiveFollow UI”.

It is:
1. **shared contract freeze**
2. **server/backend transport implementation**
3. **XCPro client transport integration**
4. **end-to-end verification**

That is the clean path from a hardened app-side slice to real interoperability.
