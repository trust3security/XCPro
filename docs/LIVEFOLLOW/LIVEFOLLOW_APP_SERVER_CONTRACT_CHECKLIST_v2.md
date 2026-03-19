# XCPro LiveFollow — App/Server Contract Checklist v2

## Purpose
Use this checklist to verify that the XCPro app-side LiveFollow slice and the real server/backend
contract are actually aligned.

This is the gate between:
- hardened-but-transport-limited XCPro app code, and
- real server/transport implementation.

A green build alone is not enough.
A coherent server design alone is not enough.
Manual contract review is required.

---

## Required baseline

Before using this checklist, confirm:
- the app-side LiveFollow slice is merged through Phase 4
- unavailable adapters are still explicit in the app baseline
- the server/backend side has at least a proposed session/direct-watch/telemetry contract
- both sides are being reviewed against the same version of the contract docs

---

## ✅ REQUIRED — what must agree

### 1. Ownership agreement
Confirm both sides agree that:

Server/backend owns:
- authoritative live session lifecycle
- pilot/watch membership and authorization
- direct-watch authorization
- authoritative session state
- telemetry ingest acceptance/rejection
- direct-watch feed production

XCPro client owns:
- local repository mirror state
- UI-facing transport availability mapping
- retry/unavailable/error presentation
- app-internal freshness/stale/offline derivation

Reject any design that makes these ownership boundaries fuzzy.

---

### 2. Session lifecycle agreement
Confirm the contract supports all of:

- start pilot session
- stop pilot session
- join watch session
- leave watch session

Confirm both sides agree on:
- required inputs
- required outputs
- failure cases
- auth failure handling
- idempotency/retry expectations where relevant

---

### 3. Authoritative session-state agreement
Confirm the server can provide, directly or indirectly, the session truth the app needs:

- `sessionId`
- role (pilot/watcher)
- lifecycle/state
- watch identity / target identity
- whether direct watch is authorized
- any authoritative domain-level availability or degraded state

Important:
- do **not** require `transportAvailability` or `lastError` to be canonical server-owned fields
- those may exist in app state, but they are usually client transport-local mapping concerns

---

### 4. Telemetry upload agreement
Confirm there is an explicit pilot telemetry ingest contract.

At minimum, both sides should agree on:
- canonical typed identity field(s)
- SI-unit telemetry fields
- required/optional task or session association
- timestamp semantics
- error behavior for invalid/missing identity or data

Reject any plan where telemetry upload is only implied and not actually specified.

---

### 5. Direct-watch feed agreement
Confirm there is an explicit direct-watch feed contract.

At minimum, both sides should agree on:
- how a watcher gets direct-watch samples
- required sample fields
- typed identity
- freshness/order semantics
- authorization expectations

Reject any design that assumes public OGN traffic alone replaces the direct-watch path.

---

### 6. Typed identity agreement
Confirm both sides agree that authoritative identity is typed.

Allowed authoritative identities:
- `FLARM:HEX`
- `ICAO:HEX`
- other explicit typed forms only if clearly specified

Not authoritative by themselves:
- callsign
- competition number
- registration

Reject any contract that falls back to callsign/CN/registration as authoritative session binding.

---

### 7. Time semantics agreement
Confirm both sides agree on this split:

Inside XCPro:
- monotonic ms is used for freshness/stale/offline logic

Across the wire:
- server/wall/sequence-friendly semantics are used
- cross-device monotonic timestamps are **not** treated as shared truth

Allowed:
- server event timestamp
- fix wall timestamp
- ordering/sequence token

Reject:
- requiring device-local monotonic time as canonical cross-system server truth
- assuming wall-clock-only semantics are enough without considering app-side freshness mapping

---

### 8. Replay/privacy agreement
Confirm both sides agree that replay is side-effect free.

Replay must not:
- start real sessions
- upload telemetry
- mutate backend state
- silently fall back to real transport

Also confirm:
- no raw-track history or persistence is added by accident
- privacy-sensitive telemetry is not logged inappropriately

---

### 9. Auth and error mapping agreement
Confirm the contract defines:
- who may start a session
- who may join a watch session
- who may receive direct-watch data
- how auth failures map to app state
- how unavailable/degraded server conditions map to app state

Reject any design where the app has to guess domain authorization from generic transport errors.

---

## ❌ FORBIDDEN — reject if any of these appear

### Server-side mistakes
- server assumes app or map owns session truth
- server assumes callsign-only authoritative identity
- server assumes public OGN-only watch behavior is sufficient
- server allows replay-mode side effects
- server requires client-local monotonic timestamps as canonical shared truth

### App/client-side mistakes
- app transport layer hides unavailable transport behind fake success
- app transport layer invents session truth that is not server-backed
- app transport implementation creates a second ownship pipeline
- app transport implementation moves logic into map/UI ownership

### Contract mistakes
- no explicit telemetry upload contract
- no explicit direct-watch feed contract
- no clear auth/error mapping
- client-local transport state treated as if it were always server-owned domain state
- wall-time / monotonic semantics left vague

---

## ⚠️ REVIEW NOTES

### `transportAvailability` / `lastError`
Treat these carefully.

They may be valid app-facing fields, but they are usually:
- client transport-local state
- derived from connection/HTTP/stream behavior
- not authoritative server domain truth

If the server does expose availability/degraded status, be explicit whether it is:
- domain/server capability state, or
- client transport-local state

Do not blur those.

### Telemetry upload
The app-side architecture clearly needs pilot telemetry upload.
If the contract does not explicitly define it, the contract is incomplete.

---

## 📊 FINAL DECISION

### APPROVE if:
- server/backend truth and client transport-local state are clearly separated
- session lifecycle contract is explicit
- telemetry upload is explicit
- direct-watch feed is explicit
- typed identity rules align
- time semantics are split correctly
- replay/privacy rules align
- auth/error mapping is explicit

### REJECT if:
- the contract is still only high-level and missing telemetry/upload/feed details
- server vs client ownership is still blurred
- transport-local state is being treated as canonical server truth
- callsign/CN/registration are treated as authoritative
- direct-watch is missing
- replay/privacy semantics are not preserved

---

## Suggested audit prompt

> Review the current XCPro LiveFollow app-side implementation and the proposed server/backend contract against `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v2.md`.
>
> Confirm:
> - server-owned truth and client transport-local state are clearly separated
> - session lifecycle, telemetry upload, and direct-watch feed are explicitly specified
> - typed identity, time semantics, replay/privacy, and auth/error mapping agree
>
> Provide:
> 1. PASS or FAIL
> 2. exact contract gaps
> 3. whether the gap is server-side, XCPro-client-side, or shared-contract-side
> 4. whether implementation should start yet
