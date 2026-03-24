# LiveFollow Spectator Task Overlay Change Plan

## 0) Metadata

- Title: LiveFollow spectator task overlay v1 + watched glider scale
- Owner: Codex
- Date: 2026-03-22
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - Friends Flying currently shows the watched glider and telemetry, but not the pilot task.
  - The watched glider marker is smaller than desired for spectator viewing.
- Why now:
  - The approved spectator slice requires a clearer watched-aircraft marker and a single-pilot read-only task overlay.
- In scope:
  - Increase the Friends Flying watched glider marker scale using the existing glider asset.
  - Export pilot task geometry from the existing task SSOT into LiveFollow.
  - Upload task payload on share start and task change only.
  - Parse watched task payload from the current API live-read path.
  - Render watched task circles and legs in Friends Flying.
- Out of scope:
  - Multi-pilot spectator rendering.
  - New sensors, upload cadence changes, or a second telemetry pipeline.
  - Server API redesign or auth redesign.
  - Full pilot-screen task shell cloning in the viewer.
- User-visible impact:
  - Larger watched glider marker in Friends Flying.
  - Read-only watched task circles and legs on the map.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Pilot live task geometry | `TaskManagerCoordinator.taskSnapshotFlow` | `LiveFollowTaskSnapshotSource` boundary | Task mirrors in UI or session repo |
| LiveFollow session/share state | `LiveFollowSessionRepository` | `StateFlow<LiveFollowSessionSnapshot>` | ViewModel-owned session truth |
| Pilot-side uploaded task payload fingerprint | `CurrentApiLiveFollowSessionGateway` transport-local state | private gateway state only | repo/UI copies |
| Watched task payload from live-read API | `CurrentApiDirectWatchTrafficSource` transport-local watch state | watch data source flow -> `WatchTrafficSnapshot` | map-side authoritative caches |
| Watched task render state | `LiveFollowWatchViewModel` projection from watch state | `LiveFollowMapRenderState` | Composable-local task truth |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Pilot uploadable task snapshot | task SSOT export adapter | task mutations only | session repo -> gateway | `TaskManagerCoordinator.taskSnapshotFlow` | none | task cleared/invalidated, app restart | none | mapper + upload tests |
| Last uploaded task fingerprint | current API session gateway | successful task upsert only | gateway internal only | serialized task payload | none | new pilot session, gateway reset | none | gateway dedupe tests |
| Watched task snapshot | current API direct watch source | successful watch poll only | watch repo -> VM -> map | live-read JSON `task.payload` | server | watch stop/session switch | wall-read only at transport boundary | parser + watch tests |
| Watched task overlay state | map render projection | VM projection only | map runtime overlay | watched task snapshot | none | watch hidden/task missing | none | VM + map overlay tests |

### 2.2 Dependency Direction

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature:livefollow`
  - `feature:map`
  - `docs`
- Boundary risk:
  - `feature:livefollow` must not take a direct dependency on task UI/runtime owners.
  - Task export must arrive through a boundary interface implemented outside `feature:livefollow`.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt` | exports task SSOT into another feature boundary | adapter reads `TaskManagerCoordinator.taskSnapshotFlow` and maps to feature-local model | LiveFollow export is flow-based instead of pull-only |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/CurrentApiDirectWatchTrafficSource.kt` | parses current API live-read payload into transport-local watch state | keep parsing and transport ownership in the data source | extend payload parsing to keep task, not just aircraft |
| `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt` | dedicated spectator-only map overlay runtime | keep rendering in map runtime-only helper | add a second dedicated task overlay helper with disjoint layer/source IDs |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Pilot task export into LiveFollow | none | task export adapter (`feature:map`) | reuse task SSOT without adding reverse module dependency | mapper tests + gateway upload tests |
| Watched task read state | discarded by direct watch source | direct watch source + watch repo | keep live-read payload ownership on the watch transport path | parser + watch tests |
| Spectator task rendering | hidden/unavailable policy only | dedicated map overlay | render on map without reusing editable task runtime layers | map overlay tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapLiveFollowRuntimeLayer` task hook | map consumes local task seam but cannot render watched task | watched task arrives through `LiveFollowMapRenderState` | Phase 3 |
| Pilot task upload | no task transport wiring | session repo -> gateway `task/upsert` | Phase 1 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/livefollow/.../model/LiveFollowTaskModels.kt` | New | shared LiveFollow task transport/render models | shared by upload, watch parse, and VM projection | avoids leaking task-core models into transport state | No |
| `feature/livefollow/.../data/task/LiveFollowTaskSnapshotSource.kt` | New | boundary contract for pilot task export | LiveFollow owns what it needs from foreign task SSOT | prevents `feature:livefollow` -> `feature:tasks` direct dependency | No |
| `feature/map/.../livefollow/TaskCoordinatorLiveFollowTaskSnapshotSource.kt` | New | adapter from task SSOT to LiveFollow boundary | `feature:map` already depends on both features | `feature:tasks` cannot depend on LiveFollow | No |
| `feature/livefollow/.../data/session/LiveFollowSessionGateway.kt` | Existing | session transport contract | upload API belongs with session gateway | keeps repo orchestration out of UI | No |
| `feature/livefollow/.../data/session/CurrentApiLiveFollowSessionGateway.kt` | Existing | current API start/position/task/end transport | endpoint and dedupe state already live here | task upsert is transport, not repo/UI | No |
| `feature/livefollow/.../data/session/LiveFollowSessionRepository.kt` | Existing | session-owned side effect orchestration | already owns pilot upload timing | keeps upload timing out of ViewModel | No |
| `feature/livefollow/.../data/watch/CurrentApiDirectWatchTrafficSource.kt` | Existing | watch poll parsing and transport-local task state | current API live-read task lives in same payload | avoid second polling client | No |
| `feature/livefollow/.../data/watch/WatchTrafficModels.kt` | Existing | watched task read model in watch SSOT | watch repo exports one snapshot | avoids map-owned task authority | No |
| `feature/livefollow/.../watch/LiveFollowWatchUiState.kt` | Existing | VM map render projection | render input belongs in UI state | keep transport parsing out of UI | No |
| `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt` | Existing | watched glider overlay runtime | current marker scale belongs here | no server or VM ownership | No |
| `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchTaskOverlay.kt` | New | watched task map overlay runtime | read-only spectator task rendering belongs on map side | do not mix with editable task runtime/router | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | Existing | map-side handoff to spectator overlays | it already owns watched-aircraft runtime hookup | keep Composables render-only | No |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing | authoritative pipeline wiring update | required when wiring changes | global pipeline doc owner | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `LiveFollowTaskSnapshotSource` | `feature:livefollow` | `feature:map` adapter, `LiveFollowSessionRepository` | public within module dependency boundary | task SSOT export seam | keep as stable LiveFollow boundary |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Task upload trigger timing | none | event-driven on share start and task changes only |
| Task wire timestamps | wall | current deployed API orders live payloads by wall-clock timestamp only |
| Watch task freshness | none | task payload itself is not age-ranked locally |
| Watch aircraft freshness/arbitration | monotonic | existing local stale/offline/source arbitration path remains unchanged |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - HTTP task upsert/read: `IO`
  - task/watch/session flow orchestration: existing repository scopes
- Primary cadence/gating sensor:
  - task data is not sensor-driven; uploads happen on share start and task mutations only
- Hot-path latency budget:
  - no new work on position upload cadence; map overlay updates remain bounded to watch-task state changes

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - replay still blocks LiveFollow side effects
  - no new replay transport behavior is introduced

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| task sent on every position tick | AGENTS.md battery/network guardrails | unit test + review | session repo/gateway tests |
| duplicate task truth in UI/map | ARCHITECTURE.md SSOT | review + unit tests | task export adapter + UI state tests |
| malformed live-read task breaks watch position parsing | error handling rules | parser tests | direct watch source tests |
| watched task clobbers editable task layers | map/runtime ownership rules | unit tests + dedicated layer IDs | map overlay tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| watched glider remains readable and correctly rotated | `MS-UX-03` | existing spectator marker | larger marker with stable rotation | overlay tests + manual map check | Phase 3 |
| watched task overlay does not flicker or fight marker z-order | `MS-UX-04` | no watched task today | stable circles/legs below watched glider | map overlay tests + manual map check | Phase 3 |
| spectator overlay apply stays bounded | `MS-ENG-01` | existing watched glider only | no repeated teardown outside task changes | unit tests + required gates | Phase 4 |

## 3) Data Flow (Before -> After)

Before:

`Task SSOT -> no LiveFollow export`

`Pilot ownship snapshot -> session repo -> position upload`

`Watch live-read payload -> direct watch aircraft only -> watch repo -> VM -> watched glider render`

After:

`TaskManagerCoordinator.taskSnapshotFlow -> LiveFollowTaskSnapshotSource -> LiveFollowSessionRepository -> CurrentApiLiveFollowSessionGateway -> POST /api/v1/task/upsert`

`GET /api/v1/live/{session_id|share_code} -> CurrentApiDirectWatchTrafficSource (aircraft + task) -> WatchTrafficRepository -> LiveFollowWatchViewModel -> MapLiveFollowRuntimeLayer -> watched glider + watched task overlays`

## 4) Implementation Phases

### Phase 1
- Goal: add LiveFollow task models, boundary export seam, and pilot task upsert transport
- Files to change: LiveFollow task models/source, session repo/gateway, map export adapter
- Tests: gateway upload mapping/dedupe, session orchestration
- Exit criteria: task upsert occurs on share start and task changes only

### Phase 2
- Goal: parse watched task from live-read payload and expose it through watch state
- Files to change: current API payload parser, direct watch source, watch models/tests
- Tests: direct watch parser and repository state tests
- Exit criteria: watched task is present in watch UI render state when server payload contains it

### Phase 3
- Goal: render watched task and enlarge watched glider marker
- Files to change: map LiveFollow runtime layer, watched glider overlay, watched task overlay
- Tests: map runtime resolver tests, overlay unit tests
- Exit criteria: watched task circles/legs render in spectator mode and glider scale increases

### Phase 4
- Goal: docs + verification + self-audit
- Files to change: `PIPELINE.md`, plan doc, tests
- Tests: required gates plus focused slice tests
- Exit criteria: required gates run, pipeline updated, quality rescore recorded

## 5) Test Plan

- Unit tests:
  - task snapshot mapper/export
  - current API task upsert request mapping and dedupe
  - direct watch task parse
  - watch UI render policy
  - map overlay state/render helpers
- Replay/regression tests:
  - existing replay block behavior for LiveFollow remains unchanged
- UI/instrumentation tests:
  - not planned in this slice
- Degraded/failure-mode tests:
  - missing/invalid task payload stays non-fatal to aircraft watch
- Boundary tests for removed bypasses:
  - watched task now comes from watch state, not local task provider

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| server has no task delete endpoint | clearing a shared task cannot clear spectator overlay server-side | limit v1 to upsert-on-share-start/task-change and document the gap | Codex |
| task snapshot equality misses semantic differences | task change may not upload | use explicit simplified LiveFollow task model and equality-based flow dedupe | Codex |
| watched task overlay z-order conflicts with glider overlay | readability regression | use dedicated layer/source IDs and keep glider layer above task layers | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- Decision summary:
  - This slice adds a narrow feature seam and transport wiring without changing module ownership or durable dependency direction.

## 7) Acceptance Gates

- No architecture or coding-rule violations
- No duplicate task SSOT introduced
- Task geometry is not sent on position tick cadence
- Replay/live behavior remains unchanged
- `PIPELINE.md` updated for the new wiring
- Required checks run locally

## 8) Rollback Plan

- Revert the task upsert transport wiring independently from the map overlay if transport regressions appear.
- Revert the watched task overlay independently from the watched glider scale change if map regressions appear.
