# XCPro LiveFollow v2 - Repo-Native Sensor, Telemetry, and Watch Contract

Date: 2026-03-17
Status: Draft
Target repo: trust3security/XCPro

---

## Purpose

Define the LiveFollow telemetry and watch contract in a way that fits XCPro's current architecture.

This document exists to stop three bad outcomes:
1. creating a second ownship pipeline,
2. treating OGN overlay state as session truth,
3. reintroducing raw coordinator or map-runtime escape hatches.

This is a feature-intent and boundary document. It does not replace:
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

---

## Scope

This document defines:
- the pilot-side telemetry handoff into LiveFollow,
- the follower/watch-side source model,
- allowed ownership boundaries,
- time base and unit contracts,
- source arbitration states,
- replay, privacy, and retention rules.

This document does not define:
- backend database schema in detail,
- public API field names,
- AAT scoring,
- final UI copy,
- OGN uplink relay implementation.

---

## Core repo-aligned rules

1. Sensors belong only to the existing pilot-side flight runtime.
2. LiveFollow must consume an exported ownship snapshot seam. It must not build a second sensor fusion stack.
3. Follower devices must not use their own sensors for the watched aircraft.
4. Map runtime is render-only for LiveFollow. It must not own session truth, task truth, or source arbitration.
5. Task linkage remains owned by the existing task SSOT and exported task render/use-case seams.
6. Replay must be side-effect free: no upload, no session start, no FCM, no backend state mutation.
7. Typed identity is mandatory. CN/rego/callsign matching is never authoritative session binding.

---

## Architecture placement

### Existing owners that remain authoritative

- Ownship flight state:
  - existing live flight runtime and `FlightDataRepository` pipeline
- Task runtime / render state:
  - task repository/coordinator owners exported through task use-case seams
- OGN public traffic state:
  - OGN repository / traffic facade
- Live session lifecycle and permissions:
  - XCPro backend

### New seams to add

#### 1. `LiveOwnshipSnapshotSource`
A domain/data boundary that exports a normalized pilot ownship snapshot for LiveFollow.

Responsibilities:
- read from existing flight/runtime authority only,
- normalize units and timestamps,
- expose a stream suitable for upload gating,
- expose no Android UI or MapLibre types.

Forbidden:
- direct sensor ownership,
- direct map dependency,
- session ownership,
- UI lifecycle logic.

#### 2. `LiveFollowSessionRepository`
Authoritative Android-side owner for local live session state mirrored from backend.

Responsibilities:
- active local session state,
- watch entry state,
- source arbitration inputs,
- stale/offline derivation using injected timebase,
- replay side-effect blocking.

#### 3. `WatchTrafficRepository`
Watch-mode repository that merges session-approved traffic inputs for the follower experience.

Responsibilities:
- subscribe to session metadata,
- consume OGN typed-target stream when confidently matched,
- consume XCPro direct stream when present/allowed,
- arbitrate which source is authoritative for viewer state,
- expose watch render state.

Forbidden:
- dependence on ordinary OGN overlay toggle semantics,
- dependence on SCIA/session-local selected trail aircraft preferences,
- dependence on map visibility for session truth.

#### 4. `feature:livefollow`
UI owner for pilot live-share controls and follower watch screens.

Responsibilities:
- render live session state,
- render source badge / stale/offline state,
- route user intents to use cases,
- remain a consumer of task render snapshots and watch render state.

Forbidden:
- map-runtime ownership,
- direct repository mutation from Composables,
- coordinator escape hatches.

---

## Pilot telemetry contract

LiveFollow must consume a normalized snapshot from the existing ownship runtime.

### Canonical model

```text
LiveOwnshipSnapshot
- latDeg: Double
- lonDeg: Double
- gpsAltitudeMslMeters: Double?
- pressureAltitudeMslMeters: Double?
- groundSpeedMs: Double?
- trackDeg: Double?
- verticalSpeedMs: Double?
- fixMonotonicNanos: Long
- fixWallEpochMs: Long?          // boundary/display/storage only
- positionQuality: PositionQuality
- verticalQuality: VerticalQuality
- canonicalAircraftIdentity: CanonicalAircraftIdentity
- sourceLabel: OwnshipSourceLabel
```

### Canonical sub-models

```text
CanonicalAircraftIdentity
- transportType: FLARM | ICAO | XC_CUSTOM
- canonicalKey: String           // typed key, e.g. FLARM:AB12CD
- verified: Boolean
```

```text
PositionQuality
- state: VALID | DEGRADED | INVALID
- confidence: HIGH | MEDIUM | LOW | UNKNOWN
```

```text
VerticalQuality
- state: VALID | DEGRADED | INVALID | UNAVAILABLE
- confidence: HIGH | MEDIUM | LOW | UNKNOWN
```

```text
OwnshipSourceLabel
- LIVE_FLIGHT_RUNTIME
- REPLAY_RUNTIME
- UNKNOWN
```

### Contract rules

- Internal units are SI only.
- Field names must encode units.
- `fixMonotonicNanos` is the authoritative live freshness input.
- `fixWallEpochMs` is optional and boundary-only.
- `canonicalAircraftIdentity` must already be typed before upload logic sees it.
- Unknown or unsupported source labels must fail safe.

---

## Time base rules for LiveFollow

### Pilot side

- Upload freshness gating: monotonic time only.
- UI labels like "last updated": wall time may be derived at the boundary.
- Replay samples may be observed by the snapshot source, but replay must hard-block all side effects.

### Follower side

- Freshness/staleness/offline transitions: injected monotonic clock.
- Human-readable timestamps: wall time boundary only.
- Task progress calculations must not compare monotonic time against wall time.

---

## Correct data flow

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

### Task rendering side

```text
existing task SSOT
  -> task use-case seam
  -> exported task render snapshot
  -> LiveFollow UI / map runtime consumer
```

---

## Watch-mode source arbitration

The follower experience needs an explicit state machine.

### Required states

```text
WatchSourceState
- OGN_CONFIRMED
- DIRECT_CONFIRMED
- AMBIGUOUS_IDENTITY
- WAITING_FOR_SOURCE
- STALE
- OFFLINE
- STOPPED
```

### Rules

- OGN wins only when typed identity match is confident and fresh.
- XCPro direct wins when session-authorized direct telemetry is available and fresher/more trustworthy.
- Ambiguous identity never silently binds a task.
- State changes must use dwell/hysteresis/hold to avoid flicker.
- UI renders degraded states honestly; no fabricated precision.

---

## Dedicated watch mode requirement

LiveFollow watch mode is not the same thing as ordinary OGN public overlay mode.

Therefore:
- a watch session must not depend on `ognOverlayEnabled`,
- a watch trail must not depend on `showSciaEnabled`,
- a watch trail must not depend on selected OGN aircraft keys,
- a watch session must remain valid even when the ordinary public OGN overlay is off.

Ordinary traffic overlay state remains separate.

---

## Task linkage rule

LiveFollow does not own task truth.

LiveFollow may consume task snapshots/render snapshots exported by the existing task use-case seam.
It must not:
- construct a second task owner,
- query manager internals directly from UI,
- add new manager/coordinator escape hatches,
- route task rendering around the existing render-sync path.

---

## Replay rules

Replay must remain side-effect free.

### Hard block list

Replay mode must never:
- start a live session,
- upload telemetry,
- emit FCM notifications,
- mutate backend session state,
- publish OGN fallback data.

### Implementation expectation

Replay blocking is enforced in domain/use-case logic and repository wiring, not only in UI.

---

## Privacy and storage rules

- Direct live fallback does not imply durable raw track storage by default.
- Default server retention should be hot state plus bounded short-term watch cache.
- Durable history requires explicit user opt-in.
- Release logging must not include location traces.
- Pilot-only artifacts such as IGC ownership remain pilot-scoped and are not exposed to followers by default.

---

## Responsibilities of `feature:livefollow`

LiveFollow decides:
- whether a local session can start,
- whether a telemetry tick is eligible for upload,
- how watch-mode source arbitration resolves,
- how session/watch state maps to UI.

LiveFollow does not decide:
- sensor fusion,
- task ownership,
- map overlay internals,
- ordinary public OGN overlay preference semantics.

---

## Phase alignment with `AGENT.md`

### Phase 0 - Baseline
- add docs/contracts,
- lock current task/map/OGN behavior with regression tests,
- identify affected flow in `PIPELINE.md`.

### Phase 1 - Pure logic
- implement quality/confidence and source-arbitration models/use cases,
- define typed identity and replay-block rules,
- unit test hysteresis/stale/offline transitions.

### Phase 2 - Repository / SSOT wiring
- add `LiveOwnshipSnapshotSource`, `LiveFollowSessionRepository`, `WatchTrafficRepository`,
- wire ports/adapters through DI,
- expose Flow/StateFlow only.

### Phase 3 - ViewModel + UI wiring
- add `feature:livefollow` VM/screens,
- add pilot controls and follower route,
- bind map/runtime as render-only consumer.

### Phase 4 - Hardening
- verify threading and cadence,
- verify replay blocking,
- update `PIPELINE.md`,
- update change plan if architecture moved,
- run required checks and publish quality rescore.

---

## Codex brief to keep

Implement LiveFollow using these constraints:
- source pilot telemetry from an exported ownship snapshot seam,
- do not create a second ownship pipeline,
- do not depend on ordinary OGN overlay prefs for watch mode,
- keep task truth behind the existing task use-case/render snapshot seam,
- keep map runtime render-only,
- typed identity only for authoritative binding,
- replay must be side-effect free,
- use explicit SI field names and explicit timebase handling,
- implement source arbitration as a documented state machine with hysteresis.

---

## Mental model

- Sensors = aircraft instruments
- Existing ownship runtime = aircraft systems bus
- OGN = public radar feed
- XCPro direct = private relay feed
- LiveFollow = session/watch orchestration
- Backend = session authority
- Map = display surface
