# LiveFollow Server Compatibility Investigation

Date: 2026-03-19
Author: Codex
Status: Investigation summary

---

## Purpose

This document summarizes:

1. what the current XCPro LiveFollow design says the server/backend should own,
2. what the current app-side code expects from that server/backend,
3. what is actually implemented in the app today,
4. whether the current XCPro build will work against a real LiveFollow server.

This is an investigation summary, not a wire-level protocol spec.

---

## Executive Summary

## Bottom line

The current LiveFollow implementation in XCPro is **transport-limited** and **will not communicate with a real server yet**.

The repository contains:

- a documented **intended** backend/session contract,
- an app-side **gateway interface** for session lifecycle operations,
- an app-side **direct watch source interface** for direct follower traffic,
- route/ViewModel/repository/state-machine/UI wiring around those interfaces,
- explicit **unavailable placeholder adapters** bound in DI instead of real transport implementations.

That means:

- the design is far enough along to understand what the server should provide,
- but the app does **not** currently ship a real backend session transport,
- the app does **not** currently ship a real direct-watch transport,
- and LiveFollow start/join operations will surface **unavailable** rather than actually calling a backend.

---

## What the LiveFollow docs say the backend should own

The LiveFollow design places these responsibilities on the XCPro backend:

- live session lifecycle,
- follow permissions,
- watcher membership,
- session event history.

The docs also describe the intended data flow:

### Pilot side

```text
Sensors
  -> existing flight runtime
  -> FlightDataRepository / exported ownship seam
  -> LiveOwnshipSnapshotSource
  -> Live session use cases
  -> backend upload adapter
```

### Follower side

```text
FCM / session route
  -> LiveFollowSessionRepository
  -> WatchTrafficRepository
      -> OGN typed target stream (when confident)
      -> XCPro direct stream (when available/allowed)
  -> LiveFollow use cases
  -> ViewModel
  -> map/runtime renderers
```

This means the backend is expected to sit behind:

- a session API / gateway for pilot start-stop and watch join-leave,
- a telemetry ingest/upload path,
- a direct watch stream or equivalent direct-watch data source,
- optional route/deep-link/notification entry points in a later phase.

---

## What XCPro currently expects from the server/backend

The app-side session boundary is the `LiveFollowSessionGateway` interface.

It expects the transport layer to support these operations:

- `startPilotSession(request)`
- `stopCurrentSession(sessionId)`
- `joinWatchSession(sessionId)`
- `leaveSession(sessionId)`

The gateway is also expected to expose ongoing state with:

- `sessionId`
- `role`
- `lifecycle`
- `watchIdentity`
- `directWatchAuthorized`
- `transportAvailability`
- `lastError`

So, even though endpoint names and JSON payloads are not yet specified in-repo, the app-side functional contract is already clear:

1. the pilot can start/stop a live session,
2. the watcher can join/leave a session by `sessionId`,
3. the backend mirrors authoritative session truth back into the app,
4. the backend can indicate whether direct watch is authorized,
5. transport availability and last transport error are first-class UI-facing state.

---

## Pilot-side payload/identity expectations

### Pilot start request

The current app builds a `StartPilotLiveFollowSession` containing:

- `pilotIdentity`
- optional `taskId`

The pilot identity is built from:

- a canonical aircraft identity,
- optional alias data such as callsign.

### Canonical identity requirement

The docs are explicit that typed identity is mandatory and that callsign/CN/registration matching is not authoritative session binding.

The intended identity system is:

- `FLARM`
- `ICAO`
- `XC_CUSTOM`

with typed canonical keys such as:

- `FLARM:AB12CD`
- `ICAO:ABC123`

The current app also enforces this direction by refusing to start sharing unless it can derive a canonical ownship identity from configured FLARM or ICAO information.

### Telemetry shape

The LiveFollow docs define a normalized ownship snapshot for telemetry/upload logic. At a high level it includes:

- latitude / longitude,
- GPS altitude MSL,
- pressure altitude MSL,
- groundspeed,
- track,
- vertical speed,
- monotonic fix time,
- optional wall-clock fix time,
- position quality,
- vertical quality,
- canonical aircraft identity,
- ownship source label.

The current app-side model mirrors this design closely and derives it from the existing `FlightDataRepository`/flight runtime rather than building a second ownship pipeline.

---

## Timebase and freshness expectations

The LiveFollow design requires:

- monotonic time for freshness gating,
- monotonic time for stale/offline transitions,
- wall time only for display/storage/boundary use,
- replay to be side-effect-free.

This means the backend contract should be compatible with:

- freshness decisions based on monotonic-age semantics on the app side,
- explicit stale/offline state transitions,
- replay mode hard-blocking any live session side effects.

If a future backend contract uses only wall-clock timestamps, it may still be usable, but it is not the preferred contract implied by the current app-side architecture.

---

## Watch-side expectations

The follower/watch path is designed around two possible sources:

1. OGN typed-target data,
2. XCPro direct stream data.

The `WatchTrafficRepository` is responsible for combining:

- current session state,
- OGN traffic candidates,
- direct watch traffic source data,
- source arbitration rules,
- stale/offline session-state rules.

The intended source arbitration states are:

- `OGN_CONFIRMED`
- `DIRECT_CONFIRMED`
- `AMBIGUOUS_IDENTITY`
- `WAITING_FOR_SOURCE`
- `STALE`
- `OFFLINE`
- `STOPPED`

This implies the server-side design should not assume:

- generic OGN traffic alone is enough,
- ordinary OGN overlay preferences are equivalent to LiveFollow session truth,
- callsign matching is sufficient for authoritative session binding.

Instead, the app expects:

- session truth from the session transport,
- direct-watch availability from a direct-watch transport boundary,
- typed identity-based arbitration between OGN and direct streams.

---

## What is actually implemented today

## Implemented today

The following pieces are already present:

- `LiveOwnshipSnapshotSource` exporting normalized ownship data from the existing flight SSOT,
- `LiveFollowSessionRepository` as the local owner of session state mirrored from the gateway,
- `WatchTrafficRepository` as the watch arbitration owner,
- pilot/watch ViewModels and screens,
- navigation route entry for `livefollow/pilot` and `livefollow/watch/{sessionId}`,
- replay-side-effect blocking,
- route validation and same-session re-entry protections on the watch flow.

## Not implemented today

The current Phase 4 plan explicitly says these are out of scope in the current app-side slice:

- backend/network implementation,
- Retrofit/WebSocket implementations,
- real production gateway/direct source transports,
- FCM delivery implementation,
- background notification handlers.

This is not just a docs gap. The current DI wiring binds placeholder implementations:

- `UnavailableLiveFollowSessionGateway`
- `UnavailableDirectWatchTrafficSource`

Those implementations expose explicit **transport unavailable** state and always fail commands instead of contacting a real backend.

That is intentional: the docs describe the current slice as **hardened but transport-limited**.

---

## Why the current app will not work with a real server yet

Even if a real server exists today, XCPro currently has no production transport implementation wired to talk to it.

The blockers are:

1. no concrete `LiveFollowSessionGateway` implementation for real network I/O,
2. no concrete `DirectWatchTrafficSource` implementation for real direct watch updates,
3. no upload adapter implementation for pilot telemetry,
4. no in-repo endpoint/message/auth contract yet,
5. no real FCM/deep-link/session-notification transport implementation.

Because of the current placeholder bindings:

- starting a pilot session returns an unavailable failure,
- joining a watch session returns an unavailable failure,
- direct watch state remains explicitly unavailable,
- the UI is designed to surface this honestly rather than simulate success.

So:

- the current design is coherent enough to guide backend design,
- the current build is not yet capable of real server communication.

---

## What a compatible backend should provide

Based on the current app-side contract and docs, a future compatible backend should provide at least:

### 1. Session lifecycle transport

Operations equivalent to:

- start pilot session,
- stop pilot session,
- join watch session,
- leave watch session.

### 2. Authoritative session state response

The app needs to learn, directly or indirectly:

- active `sessionId`,
- whether the local user is pilot or watcher,
- lifecycle state,
- watch identity,
- whether direct watch is authorized,
- transport availability / error state.

### 3. Telemetry ingest path

A server-side intake compatible with:

- typed canonical identity,
- SI units,
- monotonic freshness-friendly semantics,
- optional wall-clock timestamp,
- no replay side effects.

### 4. Direct watch feed

A direct-watch source compatible with:

- lat/lon,
- altitude,
- groundspeed,
- track,
- vertical speed,
- fix timestamps,
- canonical identity,
- source state/confidence.

### 5. Typed identity discipline

The server should be designed around authoritative typed identities, not callsign-only matching.

The current app-side direction strongly favors:

- `FLARM:HEX`
- `ICAO:HEX`
- optional alias data only as non-authoritative support.

---

## Compatibility risk areas

The biggest server/app mismatch risks are:

### 1. Identity mismatch

If the server expects only:

- callsign,
- registration,
- competition number,

then it is not aligned with XCPro’s intended authoritative matching model.

### 2. Transport assumption mismatch

If the server design assumes:

- only public OGN data,
- no separate direct watch path,

then it does not match the intended watch arbitration architecture.

### 3. Timebase mismatch

If the server assumes wall-clock-only freshness semantics, it may not align cleanly with the app’s monotonic freshness and stale/offline logic.

### 4. Session authority mismatch

If the server expects the app or map layer to own session truth locally without mirrored backend authority, that conflicts with the current architecture.

### 5. Replay/privacy mismatch

Any backend design that allows replay-mode side effects, silent fallback uploads, or implicit history persistence would conflict with current LiveFollow rules.

---

## Final answer

## Does the current server-side expectation make sense?

Yes, at a high level. The docs define a coherent architecture:

- backend owns session truth,
- app exports normalized ownship telemetry,
- watcher state merges session truth + OGN + direct stream,
- typed identity is authoritative,
- replay is side-effect-free.

## Will the current XCPro app work with it today?

No.

The current XCPro build is intentionally transport-limited and uses explicit unavailable adapters instead of real backend/direct-watch transports.

## What must happen next for real interoperability?

At minimum:

1. implement a real `LiveFollowSessionGateway`,
2. implement a real `DirectWatchTrafficSource`,
3. define a concrete wire protocol / endpoint contract,
4. define auth/authorization and error mapping,
5. wire pilot telemetry upload and watch updates to those transports.

Until that happens, the current app is best understood as:

- **architecture-ready**
- **UI/state-machine ready**
- **not network-integrated yet**

---

## Files reviewed

Primary design and status docs:

- `docs/LIVEFOLLOW/livefollow_v2.md`
- `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v9.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/PIPELINE_LiveFollow_Addendum.md`

Primary implementation files:

- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/LiveFollowSessionGateway.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/UnavailableLiveFollowSessionGateway.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/LiveFollowSessionRepository.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/LiveFollowSessionModels.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/ownship/FlightDataLiveOwnshipSnapshotSource.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/model/LiveOwnshipSnapshotModels.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/DirectWatchTrafficSource.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/UnavailableDirectWatchTrafficSource.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/WatchTrafficRepository.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/pilot/LiveFollowPilotUseCase.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchUseCase.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchViewModel.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt`
- `feature/livefollow/src/main/java/com/example/xcpro/livefollow/di/LiveFollowModule.kt`
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
