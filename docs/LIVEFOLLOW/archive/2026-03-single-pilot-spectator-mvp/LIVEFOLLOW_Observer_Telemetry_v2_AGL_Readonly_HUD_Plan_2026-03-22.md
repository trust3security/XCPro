# LIVEFOLLOW Observer Telemetry v2

## Metadata

- Title: Observer telemetry v2 - AGL trace and read-only HUD parity
- Owner: Codex
- Date: 2026-03-22
- Status: Complete

## Scope

- Problem statement:
  - Friends Flying observer mode already shows a compact watched-pilot HUD, but its telemetry labels are not fully explicit and AGL is not available end to end.
- In scope:
  - Trace watched telemetry from Flying SSOT through LiveFollow upload/watch/runtime/HUD.
  - Tighten the spectator HUD to use explicit labels.
  - Keep browse-to-watch telemetry parity aligned during pilot switching.
  - Carry optional real watched AGL end to end without inventing a fallback value.
- Out of scope:
  - Multi-glider rendering.
  - Auth/session redesign.
  - Full pilot-screen cloning.
  - Backend contract changes outside this repo.

## Architecture Contract

### SSOT ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Ownship live flight telemetry | `FlightDataRepository` / `CompleteFlightData` | `StateFlow<CompleteFlightData?>` | UI or ViewModel mirrors |
| LiveFollow ownship upload snapshot | `FlightDataLiveOwnshipSnapshotSource` | `StateFlow<LiveOwnshipSnapshot?>` | Ad-hoc upload DTO builders in UI |
| Watched pilot truth | `WatchTrafficRepository` | `StateFlow<WatchTrafficSnapshot>` | UI-side telemetry caches |
| Spectator HUD state | `LiveFollowWatchViewModel` | `StateFlow<LiveFollowWatchUiState>` | Extra map/runtime HUD state |
| Friends-list selection hint | `FriendsFlyingUiState` / `FriendsFlyingPilotSelection` | event payload only | long-lived second watch owner |

### State contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `LiveOwnshipSnapshot.aglMeters` | `FlightDataLiveOwnshipSnapshotSource` | ownship projection only | pilot session upload path | `CompleteFlightData.agl` when `aglTimestampMonoMs > 0` | none | next ownship snapshot lacks valid AGL or watch/pilot flow stops | live monotonic owner timestamp | ownship source + upload mapper tests |
| `WatchTrafficSnapshot.aircraft` | `WatchTrafficRepository` | repository evaluation only | `LiveFollowWatchUseCase.watchState` | direct watch or OGN source | none | watcher stops, lookup changes, source goes unavailable | monotonic freshness + optional wall fix time | watch repo + UI state tests |
| `LiveFollowWatchRouteFeedback.selectedTarget` | `LiveFollowWatchViewModel` | watch entry / clear actions | `LiveFollowWatchUiState` | Friends Flying selection event | none | clear, stop, or feedback reset | wall-time labels only | VM + UI state tests |

### Reference pattern

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/friends/FriendsFlyingUiState.kt` | existing browse-time row shaping | event carries display-only selection hint | add heading to keep switch-time HUD consistent |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/watch/LiveFollowWatchUiState.kt` | existing watch HUD projection owner | ViewModel-owned render state only | add `panelAglLabel` only from true watched transport data |

### File ownership plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/livefollow/.../FriendsFlyingUiState.kt` | Existing | friends-list row + selection hint shaping | existing browse selection owner | not a watch repo concern | No |
| `app/.../AppNavGraph.kt` | Existing | route handoff mapping only | existing navigation seam | not ViewModel business logic | No |
| `feature/livefollow/.../LiveFollowWatchUiState.kt` | Existing | spectator HUD projection | existing watch UI-state owner | not map runtime truth | No |
| `feature/livefollow/.../LiveFollowWatchTelemetry.kt` | New | explicit read-only HUD field list | small pure formatter shared by UI/tests | keeps composable render logic thinner | No |
| `docs/LIVEFOLLOW/LIVEFOLLOW_Observer_Telemetry_v2_AGL_Readonly_HUD_Plan_2026-03-22.md` | New | execution record + AGL gap | task-specific plan, not global policy | avoids polluting durable architecture docs | No |

### Time base

| Value | Time Base | Why |
|---|---|---|
| watched fix freshness (`ageMs`) | Monotonic | source arbitration and stale/offline state already use monotonic freshness |
| spectator “Updated X ago” labels | Wall | user-facing relative label only |
| AGL from flight runtime | Monotonic freshness on live owner | existing flight SSOT freshness contract |

### Replay determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - LiveFollow watch stays blocked during replay.
  - No new replay behavior is introduced in this slice.

## Telemetry Trace

### Flying-side telemetry already available locally

- `CompleteFlightData` already contains:
  - GPS altitude MSL
  - pressure altitude MSL
  - ground speed
  - track / heading inputs
  - vertical speed / vario outputs
  - AGL (`agl`, `aglTimestampMonoMs`)
  - many additional flight metrics not exported to LiveFollow

### Telemetry already exported into LiveFollow ownship snapshot

- `FlightDataLiveOwnshipSnapshotSource` now exports:
  - latitude / longitude
  - GPS altitude MSL
  - pressure altitude MSL
  - AGL (`aglMeters`) when the live SSOT has a known `aglTimestampMonoMs`
  - ground speed
  - track
  - vertical speed
  - fix monotonic / wall timestamps
  - quality + identity metadata

### Telemetry already sent to the server

- `LiveFollowCurrentApiPositionMapper` / `CurrentApiPositionUploadRequest` now send:
  - `lat`
  - `lon`
  - `alt` (pressure altitude MSL preferred, GPS altitude MSL fallback)
  - optional `agl_meters`
  - `speed`
  - `heading`
  - `timestamp`

### Watched runtime/client telemetry already present

- `CurrentApiLivePoint` / `DirectWatchAircraftSample` / `WatchAircraftSnapshot` now carry:
  - latitude / longitude
  - altitude MSL
  - optional AGL
  - ground speed
  - heading / track
  - fix timestamps
- Direct watch currently does not carry vertical speed from the transport.
- The spectator HUD now renders:
  - status
  - speed
  - altitude MSL
  - AGL when the watched direct transport carries it
  - heading
  - freshness / recency

### AGL path after this slice

`CompleteFlightData.agl`
-> `FlightDataLiveOwnshipSnapshotSource` exports optional `LiveOwnshipSnapshot.aglMeters`
-> `LiveFollowCurrentApiPositionMapper` serializes optional wire `agl_meters`
-> current API live-read parsing maps optional `CurrentApiLivePoint.aglMeters`
-> `CurrentApiDirectWatchTrafficSource` carries `DirectWatchAircraftSample.aglMeters`
-> `WatchTrafficRepository` exposes `WatchAircraftSnapshot.aglMeters`
-> `LiveFollowWatchUiState.panelAglLabel`
-> `LiveFollowWatchTelemetry` renders explicit `AGL`

Server contract note:
- This app slice requires an additive transport field, `agl_meters`, on LiveFollow position upload and live-read payloads.
- The client keeps the field optional so older payloads still parse without inventing AGL.

## Tests

- Unit tests:
  - `FlightDataLiveOwnshipSnapshotSourceTest`
  - `LiveFollowCurrentApiPositionMapperTest`
  - `CurrentApiLiveFollowSessionGatewayTest`
  - `CurrentApiDirectWatchTrafficSourceTest`
  - `WatchTrafficRepositoryTest`
  - `LiveFollowWatchUiStateTest`
  - `LiveFollowWatchTelemetryTest`
- Required checks after slice completion:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## ADR

- ADR required: No
- Reason:
  - No durable boundary move is being made in this slice; the change extends existing LiveFollow model/transport owners with one additive optional telemetry field.
