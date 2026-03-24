# LIVEFOLLOW_Friends_Flying_Observer_Mode_v1_Plan_2026-03-22

## 0) Metadata

- Title: Friends Flying Observer Mode v1
- Owner: Codex
- Date: 2026-03-22
- Issue/PR: n/a
- Status: In progress

## 1) Scope

- Problem statement: Friends Flying can open the list and watch state, but the map is not visible behind the sheet and the selected watched pilot is not rendered on the map with an observer HUD.
- Why now: This is the core single-pilot spectator value of Friends Flying and is the next functional blocker after the active-pilots parser fix.
- In scope:
  - restore map visibility in Friends Flying
  - render one watched pilot marker from existing watch runtime state
  - one-shot camera focus on watch target selection
  - compact bottom telemetry strip using already-available watch data
  - preserve clear/switch/list behavior
- Out of scope:
  - multi-pilot rendering
  - backend or transport changes
  - Flying mode changes
  - full pilot-screen mirroring
- User-visible impact: Friends Flying becomes a real read-only observer mode for one watched glider.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Current watched pilot/session state | `LiveFollowWatchViewModel` | `LiveFollowWatchUiState` | Friends Flying route-local watch state |
| Friends Flying list state | `FriendsFlyingViewModel` | `FriendsFlyingUiState` | map-layer copies of active-pilot list data |
| Map runtime objects and watched-aircraft overlay handles | map UI/runtime layer | `MapScreenState` + map-only helpers | ViewModel-owned `MapLibreMap` state |
| One-shot watched-pilot camera focus | map UI/runtime layer | callback from map shell into camera owner | watch-domain camera policies |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Watched pilot map render state | `LiveFollowWatchViewModel` | watch use case updates only | `MapLiveFollowRuntimeLayer` | session snapshot + watch snapshot | none | clear watch target / stop watching / unavailable runtime | mixed existing watch state contract | watch UI state + map runtime tests |
| Watched pilot telemetry labels | `LiveFollowWatchViewModel` | watch use case updates only | `LiveFollowWatchMapHost` | watch snapshot + selection hint | none | clear watch target / stop watching | wall/monotonic formatting already owned by watch UI mapping | watch UI state tests |
| Focused watched share code | map UI/runtime layer | runtime layer only | map runtime layer only | selected share code + resolved map render state | none | clear selection / new selection | n/a | map runtime tests |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched: `app`, `feature:map`, `feature:livefollow`
- Any boundary risk: map rendering must not pull map runtime ownership into the watch ViewModel.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt` | existing aircraft marker overlay | MapLibre source/layer self-heal pattern | watched pilot uses a distinct source/layer/icon and stays in `feature:map` to avoid module inversion |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | existing watched-pilot chrome host | keep watch display mapping in watch UI layer | extend with bottom telemetry strip only |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Friends Flying visible shell over map | standalone full-screen list route | app shell composing map + sheet together | fixes hidden-map bug without moving list state ownership | manual + compile/unit tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Friends Flying route body | empty full-screen scaffold body masking the map | compose map host and sheet in the same route shell | implementation |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | Existing | route wiring for map vs Friends Flying shell composition | app navigation owner | navigation should stay in app layer | maybe helper split if file grows |
| `app/src/main/java/com/example/xcpro/MapRouteHost.kt` | New | shared app-level map shell host with optional overlay | avoids duplicating map-route composition | not domain logic | no |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | Existing | map-side bridge from watch UI state into overlay/focus rendering | current watch/map seam already lives here | keeps map runtime out of watch ViewModel | no |
| `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt` | New | watched pilot MapLibre source/layer runtime | map runtime object owner | should not live in ViewModel or livefollow module | no |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSectionInputs.kt` | Existing | overlay seam inputs | existing map shell contract owner | narrow API threading | no |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt` | Existing | binds new map overlay/focus callbacks | map shell input assembly | current place for overlay callbacks | no |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowMapRenderState.kt` | Existing | watched map render payload | watch UI contract | already owns map-facing watch state | no |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchUiState.kt` | Existing | watch display mapping and telemetry labels | watch UI state owner | telemetry labels belong with other watch labels | no |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | Existing | top panel + bottom telemetry strip chrome | existing watched-pilot map chrome host | keep all watch chrome together | maybe later |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing | pipeline wiring update | required by repo contract | canonical architecture doc | no |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| watched-pilot map render payload includes share code | `feature:livefollow` watch UI state | `feature:map` runtime layer | internal/module | lets map side suppress stale previous-target rendering during switches | keep as long as watch map overlay needs identity match |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Watched pilot freshness labels | existing watch UI mapping time base | already defined by watch snapshot mapping |
| One-shot focus trigger | selection identity only | not time-derived |

### 2.4 Threading and Cadence

- Dispatcher ownership: existing Compose/main-thread UI orchestration, repository/watch flows unchanged
- Primary cadence/gating sensor: existing watch state flow emissions
- Hot-path latency budget: map overlay update must stay lightweight and render-only

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules: Friends Flying remains unavailable during replay per existing list/watch conventions

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| No watched position yet after selection | Degraded | watch UI + map runtime | top panel/telemetry can show selection hint; marker stays hidden until resolved | wait for current join/runtime path | watch/map tests |
| Watched pilot stale | Degraded | existing watch SSOT | preserve stale wording and last known marker if present | existing watch state behavior | watch UI tests |
| Watched pilot unavailable | Unavailable | existing watch SSOT | preserve unavailable wording; clear marker if no position exists | user can reopen sheet and switch | watch UI tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| map runtime logic drifting into ViewModel | MVVM/UDF layering | review + unit tests | map runtime tests |
| wrong pilot marker shown during switch | SSOT / ownership | unit test | `MapLiveFollowRuntimeLayerTest` |
| fake telemetry fields | business/display integrity | unit test + review | `LiveFollowWatchUiStateTest` |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Friends Flying map stays visible | FF-OBS-1 | broken | map visible with sheet over map | manual + compile smoke | slice complete |
| Selected pilot becomes obvious on map | FF-OBS-2 | absent | one watched marker rendered after selection | unit/manual | slice complete |

## 3) Data Flow (Before -> After)

Before:

`Startup chooser -> map route -> friends route full-screen scaffold -> FriendsFlyingViewModel list only`

After:

`Startup chooser/drawer -> friends route shell -> shared MapScreen host -> MapLiveFollowRuntimeLayer consumes LiveFollowWatchUiState -> watched-aircraft overlay + focus + telemetry strip`

## 4) Implementation Phases

- Phase 1
  - Goal: restore map visibility in Friends Flying shell
  - Files: app nav + shared map route host
  - Tests: compile + route-adjacent unit coverage where practical
  - Exit criteria: map visible behind Friends Flying sheet
- Phase 2
  - Goal: render and focus watched pilot from existing watch state
  - Files: map runtime layer + watched-aircraft overlay + watch map payload
  - Tests: map runtime pure tests
  - Exit criteria: watched pilot marker appears and focus triggers once per target
- Phase 3
  - Goal: add compact bottom telemetry strip
  - Files: watch UI state + watch map host
  - Tests: watch UI state tests
  - Exit criteria: speed/altitude and other available fields render without inventing missing data

## 5) Test Plan

- Unit tests:
  - `MapLiveFollowRuntimeLayerTest`
  - `LiveFollowWatchUiStateTest`
- Replay/regression tests:
  - existing replay-blocked Friends Flying state remains
- UI/instrumentation tests:
  - none planned unless local unit proof is insufficient
- Degraded/failure-mode tests:
  - switching target before session resolves
  - unavailable/stale telemetry labels
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Ownership move / shell integration | Boundary lock tests + compile | map runtime tests + `assembleDebug` |
| UI interaction / lifecycle | UI or instrumentation coverage | targeted unit tests + manual reasoning + compile |
| Performance-sensitive path | metric or SLO artifact when needed | repo `pr-ready` verification and map overlay kept lightweight |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

- Risk: composing Friends Flying over the map could regress other map overlays.
  - Mitigation: keep the change route-local and reuse existing map host instead of new screen architecture.
- Risk: switching pilots could briefly show the wrong marker.
  - Mitigation: gate map marker/focus on resolved watch share code matching the selected share code.
- Risk: requested telemetry fields may not all exist in current watch runtime.
  - Mitigation: only render existing watch fields and report missing AGL explicitly.
