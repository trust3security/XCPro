# PIPELINE.md Addendum Draft - LiveFollow

Date: 2026-03-17
Status: Draft text to merge into `docs/ARCHITECTURE/PIPELINE.md` when wiring lands

---

## LiveFollow Pipeline (new)

Purpose:
- document the pilot live-share and follower watch path,
- keep it separate from ordinary public OGN overlay semantics,
- make session/task/source ownership explicit.

### A) Pilot live-share path

Authoritative source:
- existing live flight pipeline remains the only source of ownship flight truth.
- LiveFollow does not create a second ownship pipeline.

Flow:

```text
Sensors
  -> SensorRegistry / UnifiedSensorManager
  -> FlightDataCalculatorEngine
  -> SensorFusionRepository.flightDataFlow
  -> FlightDataRepository (SSOT)
  -> exported ownship snapshot seam (`LiveOwnshipSnapshotSource`)
  -> LiveFollow start/upload use cases
  -> backend upload adapter
```

Rules:
- upload freshness gating uses monotonic time from the ownship snapshot,
- UI labels may use wall time at the boundary only,
- replay runtime may surface snapshot-shaped data for tests/local UI but must not trigger session side effects,
- `feature:livefollow` consumes the ownship export seam and must not read sensors directly.

### B) Live session truth

Authoritative source:
- XCPro backend owns session lifecycle, follow permissions, watcher membership, and session event history.

Android local owner:
- `LiveFollowSessionRepository`

Flow:

```text
pilot start/stop intent
  -> LiveFollow use case
  -> backend session API
  -> LiveFollowSessionRepository StateFlow
  -> ViewModel/UI
```

Rules:
- no ViewModel-owned persistent session truth,
- no OGN-owned session truth,
- no map-runtime-owned session truth.

### C) Task linkage for LiveFollow

Authoritative source:
- existing task repository/coordinator owners through task use-case seams.

Flow:

```text
Task SSOT
  -> task-facing use case
  -> TaskRenderSnapshot
  -> LiveFollow ViewModel / map runtime render consumers
```

Rules:
- LiveFollow does not own task truth,
- no direct coordinator manager escape hatches,
- no Composable direct task-manager calls,
- task rendering remains through the established task render-sync/runtime path.

### D) Follower watch path

Flow:

```text
FCM / deep link
  -> LiveFollowRoute(sessionId)
  -> LiveFollowSessionRepository
  -> WatchTrafficRepository
      -> OGN typed-target stream
      -> XCPro direct stream
  -> source arbitration use case
  -> LiveFollowViewModel
  -> map/runtime renderers
```

Authoritative owners:
- session/watch state: `LiveFollowSessionRepository`
- source arbitration: domain use cases + `WatchTrafficRepository`
- rendering: map/runtime UI only

### E) Watch mode vs ordinary OGN overlay mode

This is a non-negotiable distinction.

Ordinary OGN overlay path today:
- is preference-driven,
- renders `emptyList()` when ordinary OGN overlay is disabled,
- ties OGN glider trails to `showSciaEnabled` and selected OGN aircraft keys.

LiveFollow watch mode must not depend on that ordinary path.

Rules:
- an active watch session may continue even if `ognOverlayEnabled == false`,
- watch trails must not reuse selected OGN aircraft-key filtering,
- watch mode uses a dedicated repository/facade and dedicated trail state.

### F) Source arbitration state

Required states:

```text
WAITING_FOR_SOURCE
OGN_CONFIRMED
DIRECT_CONFIRMED
AMBIGUOUS_IDENTITY
STALE
OFFLINE
STOPPED
```

Rules:
- OGN authoritative only on confident typed-identity match,
- direct authoritative when fresher/allowed,
- ambiguous identity never silently binds a task,
- dwell/hysteresis/hold required to avoid flicker,
- degraded states are explicit user-visible outputs.

### G) Identity rules

- authoritative identity matching is typed (`FLARM:HEX`, `ICAO:HEX`, or verified XC custom key),
- CN/registration/callsign matching is non-authoritative only,
- task/session bind is blocked on ambiguity.

### H) Privacy and retention

- direct live path defaults to hot-state plus bounded short-term cache,
- no durable raw-track storage unless the user explicitly opts in,
- release logging must not emit location traces.

### I) Replay rule

Replay must never:
- start a live session,
- upload telemetry,
- notify followers,
- mutate backend watch/session state,
- publish OGN fallback data.
