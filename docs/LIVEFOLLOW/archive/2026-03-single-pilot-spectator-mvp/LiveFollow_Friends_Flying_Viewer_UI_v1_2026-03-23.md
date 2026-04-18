# LiveFollow Friends Flying Viewer UI v1

## 0) Metadata

- Title: Friends Flying viewer UI v1
- Owner: Codex
- Date: 2026-03-23
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  Friends Flying still carries a top watched-pilot card that competes with the
  map, the expanded sheet can take over too much of the viewport, and the
  watched-pilot UI can incorrectly surface stale display state after a task
  clear even though liveness should remain driven only by live position
  freshness.
- Why now:
  Single-pilot spectator mode, watched glider rendering, telemetry, and task
  clear transport are already in place. The next slice is a UI cleanup plus a
  local correctness fix that should land without changing backend or transport
  contracts.
- In scope:
  remove the top watch card, keep the bottom telemetry strip, add a map-first
  tabbed Friends Flying sheet with Active-tab search, constrain expanded sheet
  height so map remains visible, and separate watched-pilot liveness from task
  state / selection-hint fallback.
- Out of scope:
  backend/server changes, transport/state contract changes, multi-glider
  behavior, and Flying-mode redesign.
- User-visible impact:
  Friends Flying becomes map-first, the watched task can disappear without
  falsely stale-marking the watched pilot, and pilot browsing gains tabs plus
  search without covering the entire map.
- Rule class touched: Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Watched-pilot freshness/liveness | `WatchTrafficRepository` + `LiveFollowSessionStateMachine` | `WatchTrafficSnapshot.sourceState` / `ageMs` | Friends Flying UI-local stale state |
| Friends Flying browse list | `FriendsFlyingRepository` | `FriendsFlyingSnapshot` -> `FriendsFlyingUiState` | route-local pilot list mirrors |
| Watch selection hint during join/switch | `LiveFollowWatchViewModel` route feedback | `LiveFollowWatchUiState` | Friends Flying route becoming a second watch-state owner |
| Bottom-sheet tabs/search chrome | Friends Flying composables | local Compose state only | repository/ViewModel-owned search or tab state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Watched source state | `WatchTrafficRepository` | repository evaluation loop only | `LiveFollowWatchViewModel` -> `LiveFollowWatchUiState` | direct/OGN samples + state machine | none | session stop / session key change | Monotonic | `WatchTrafficRepositoryTest` |
| Friends Flying search query | Friends Flying route UI | text field input only | sheet composables | user text input | none | route recreation / user clear | none | `FriendsFlyingUiStateTest` helper coverage |
| Friends Flying selected tab | Friends Flying route UI | tab clicks only | sheet composables | user selection | none | route recreation | none | manual + compile |
| Watch panel telemetry labels | `LiveFollowWatchUiState` mapper | mapper only | telemetry strip + map runtime layer | watch snapshot + route feedback | none | normal watch/session updates | existing watch age path + wall labels | `LiveFollowWatchUiStateTest` |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  `feature:livefollow`, `feature:map`, docs only.
- Boundary risk:
  Friends Flying could accidentally become the owner of watched freshness if the
  stale fix were implemented in sheet UI instead of the watch presentation/data
  path.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/friends/FriendsFlyingScreen.kt` | existing Friends Flying sheet shell | keep route-local sheet chrome and list rendering | split expanded sheet content into a focused file |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | existing watched overlay/task attachment seam | keep map overlay attachment in map runtime layer | remove only the top watch card callback/render plumbing |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/watch/LiveFollowWatchUiState.kt` | existing watched presentation owner | keep stale/task separation in UI-state mapper | tighten selection-hint fallback so live watch state wins once resolved |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| None | n/a | n/a | This slice is a UI cleanup plus mapper correction; ownership stays the same | tests + review |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| none | existing AppNavGraph handoff into the shared watch ViewModel is already correct | retain | n/a |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/LIVEFOLLOW/LiveFollow_Friends_Flying_Viewer_UI_v1_2026-03-23.md` | New | slice plan and ownership record | required for non-trivial change | not runtime code | No |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/friends/FriendsFlyingScreen.kt` | Existing | route shell, sheet state, and map-first scaffold wiring | route UI already owns this | avoid pushing UI chrome into ViewModel | Yes |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/friends/FriendsFlyingSheetContent.kt` | New | expanded sheet header/tabs/search/content layout | focused UI owner for the new sheet behavior | keep `FriendsFlyingScreen.kt` from becoming a mixed-responsibility file | Yes |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/friends/FriendsFlyingUiState.kt` | Existing | browse row mapping plus pure search filtering helper | presentation mapper already lives here | filtering does not belong in repositories or composables ad hoc | No |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/watch/LiveFollowWatchUiState.kt` | Existing | watch presentation fallback rules and stale/task separation | canonical watch UI mapper | sheet UI must not own freshness logic | No |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | Existing | watched telemetry strip host only after top-card removal | current watch map-host seam already lives here | avoid duplicating watch HUD rendering elsewhere | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | Existing | map attachment/detachment and watch HUD callsite | current map runtime owner | not Friends Flying sheet responsibility | No |
| `feature/livefollow/src/test/java/com/trust3/xcpro/livefollow/friends/FriendsFlyingUiStateTest.kt` | Existing | search/filter mapping regressions | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/trust3/xcpro/livefollow/watch/LiveFollowWatchUiStateTest.kt` | Existing | stale/task separation regressions in watch UI mapper | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/trust3/xcpro/livefollow/data/watch/WatchTrafficRepositoryTest.kt` | Existing | repository guard that task clear does not drive stale state | liveness owner lives here | not a UI-only test | No |
| `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayerTest.kt` | Existing | watched task attachment regression after host cleanup | closest map-layer owner | n/a | No |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Friends Flying list recency labels | Wall | existing active-pilot list contract is user-facing wall-time age text |
| Watched pilot freshness / stale transitions | Monotonic | existing state machine and direct/OGN sample ages remain authoritative |
| Search/tab state | none | UI-only state, not time-based |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  unchanged; existing ViewModel and repository flows only.
- Primary cadence/gating sensor:
  existing watch evaluation cadence and active-pilots refresh path.
- Hot-path latency budget:
  no new per-frame map work; search is in-memory list filtering on the existing
  row models.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  unchanged; Friends Flying remains replay-blocked by the existing repository
  policy.

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| Watched pilot has no task | Read-only unavailable task overlay only | map runtime + watch UI mapper | task overlay absent, watched pilot remains live if positions remain fresh | none beyond normal watch polling | `LiveFollowWatchUiStateTest`, `WatchTrafficRepositoryTest` |
| Watched pilot really becomes stale | Degraded | watch repository/state machine | telemetry strip can still show stale once freshness actually times out | existing stale/offline thresholds | existing watch tests |
| Active-tab search has no matches | Recoverable | Friends Flying sheet UI | show no-match message, keep refresh/list shell intact | edit or clear search | `FriendsFlyingUiStateTest` |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Task clear incorrectly drives watched stale state | `ARCHITECTURE.md` SSOT / no duplicate truth | unit tests | `WatchTrafficRepositoryTest.kt`, `LiveFollowWatchUiStateTest.kt` |
| Friends Flying screen becomes oversized mixed UI owner | file-responsibility guidance | review + split | `FriendsFlyingScreen.kt`, `FriendsFlyingSheetContent.kt` |
| Top card accidentally survives in shared watch host | UX acceptance | code review + manual verification | `LiveFollowWatchEntryRoute.kt`, `MapLiveFollowRuntimeLayer.kt` |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Expanded sheet keeps a visible map strip | FF-UI-01 | expanded sheet can dominate viewport | expanded height capped so map remains visible | manual + compile | slice complete |
| Friends Flying browse feels map-first | FF-UI-02 | top card competes with map | no top card, tabs/search live in bottom sheet | manual + review | slice complete |

## 3) Data Flow (Before -> After)

Before:

`FriendsFlyingSheet -> list rows -> OpenWatch(selection hint) -> LiveFollowWatchUiState -> top card + telemetry strip + map overlays`

After:

`FriendsFlyingSheet tabs/search -> filtered list rows -> OpenWatch(selection hint) -> LiveFollowWatchUiState (live watch state authoritative once resolved) -> telemetry strip + map overlays`

## 4) Implementation Phases

- Phase 0:
  add change-plan doc, confirm reference files, and lock ownership.
- Phase 3 UI wiring:
  split Friends Flying expanded sheet content, add tabs and Active-tab search,
  constrain expanded height, and remove the top watch card renderer.
- Phase 3 presentation correctness:
  tighten watch UI fallback rules so task-clear and stale display state are
  separated.
- Phase 4:
  add/update focused tests and run staged verification through `pr-ready`.

## 5) Test Plan

- Unit tests:
  `FriendsFlyingUiStateTest`, `LiveFollowWatchUiStateTest`,
  `WatchTrafficRepositoryTest`, `MapLiveFollowRuntimeLayerTest`
- Replay/regression tests:
  existing replay-blocked Friends Flying coverage remains unchanged
- UI/instrumentation tests:
  manual verification for bottom-sheet height, tabs, and search
- Degraded/failure-mode tests:
  no-task watch remains live, stale still only appears from freshness timeout

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Selection hint still leaks stale state after watch resolves | false stale UI in spectator mode | make live watch labels authoritative once session share code matches selection hint and lock with tests | Codex |
| Expanded sheet still feels too tall on some screens | UX regression | explicit max-height cap plus manual verification on typical phone layouts | Codex |
| Shared watch host cleanup unintentionally affects non-Friends routes | broader spectator UI change | keep map overlay/telemetry behavior intact and document the shared-host assumption | Codex |

## 7) Acceptance Gates

- Top watch card removed from Friends Flying viewer
- Watched task clear no longer falsely stale-marks the watched pilot
- Expanded sheet leaves map visible at the top
- Tabs and Active-tab search are present
- No backend or transport contracts changed
- Required verification passes
