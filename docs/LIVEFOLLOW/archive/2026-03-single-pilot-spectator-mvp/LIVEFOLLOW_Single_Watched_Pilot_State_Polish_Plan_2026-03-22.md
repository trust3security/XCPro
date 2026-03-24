## 0) Metadata

- Title: Single watched pilot state polish
- Owner: Codex
- Date: 2026-03-22
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  Friends Flying can open and watch a pilot, but the selected state is still
  weakly expressed. The top panel does not always update clearly during pilot
  switches, the list does not visibly reflect the current selection, and the
  user lacks an explicit clear-to-browse action.
- Why now:
  Startup chooser, active-pilot parsing, persistent sheet, and compact watch
  panel are already in place. The next usability gap is clarity of the selected
  watched pilot state inside that shell.
- In scope:
  selected pilot identification, selected-row highlighting, explicit clear/stop
  watching, and clearer stale/unavailable watched-pilot messaging.
- Out of scope:
  backend, transport, auth, multi-pilot map, and Flying-mode redesign.
- User-visible impact:
  the watched pilot is obvious, switching pilots is direct, and clearing the
  watch returns cleanly to Friends Flying browse mode without leaving the map.
- Rule class touched: Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active public pilots list | `FriendsFlyingRepository` | `FriendsFlyingSnapshot` via use case/viewmodel | UI-local authoritative pilot list mirrors |
| Selected watched pilot / watch join intent | `LiveFollowWatchViewModel` | `LiveFollowWatchRouteFeedback` + `LiveFollowWatchUiState` | route-local selected pilot state in Friends Flying |
| Friends Flying sheet chrome | Friends Flying route UI only | scaffold state | repository/viewmodel storage of expanded vs peek |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Friends Flying pilot rows | `FriendsFlyingUiState` mapper | snapshot mapper only | `FriendsFlyingViewModel.uiState` | `FriendsFlyingSnapshot.items` | none | refreshed from repo snapshot | Wall | `FriendsFlyingUiStateTest` |
| Current selected watch target hint | `LiveFollowWatchRouteFeedback` | `handleWatchShareEntry`, `stopWatching`, `dismissFeedback` | `LiveFollowWatchViewModel.uiState` and Friends Flying route wiring | explicit pilot selection + command results | none | stop watching, dismiss invalid feedback, successful state replacement | n/a plus existing watch age | `LiveFollowWatchViewModelTest`, `LiveFollowWatchUiStateTest` |
| Selected row styling | Friends Flying route UI | composable params only | `FriendsFlyingScreen` | watch UI state's selected share code | none | watch selection clears or changes | n/a | UI mapper + viewmodel tests |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  `app`, `feature:livefollow`
- Boundary risk:
  Friends Flying route could incorrectly become a second watch-state owner if it
  stores watched-pilot state instead of consuming the existing watch owner.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | current map-entry-scoped LiveFollow orchestration | keep shared map-entry watch ViewModel as the owner of watch intent | send a richer selection payload instead of only share code |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | current map overlay consumes a single watch UI state | keep top panel rendering driven by one watch UI state | none |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingScreen.kt` | current persistent sheet browse shell | keep sheet state local and list rendering in Friends Flying UI | split row rendering into a focused file because the screen file is near the line budget |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Friends Flying row-tap payload richness | bare share-code event | Friends Flying event still owned by the ViewModel, but includes display-only watch selection hint | keep switching/highlight/top-panel labels aligned during join transitions | FriendsFlyingViewModelTest + LiveFollowWatchViewModelTest |
| Selected watched-pilot list highlight | none | Friends Flying route UI derived from watch UI state | UI-only reflection of shared watch owner | UI state tests + manual browse/watch flow |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| none | existing direct handoff from Friends Flying into shared watch ViewModel is already the desired path | retain as-is | n/a |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/LIVEFOLLOW/LIVEFOLLOW_Single_Watched_Pilot_State_Polish_Plan_2026-03-22.md` | New | slice plan and ownership record | required non-trivial change plan | not runtime code | No |
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | Existing | route orchestration between Friends Flying and shared watch owner | nav graph already owns map-entry-scoped handoff | not a feature ViewModel responsibility | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingViewModel.kt` | Existing | emit row-tap watch selection event | list ViewModel already owns selection intents | not UI business | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingUiState.kt` | Existing | row display mapping | presentation mapping already lives here | keep UI free of ad hoc mapping logic | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingScreen.kt` | Existing | scaffold and expanded-content wiring only | route shell owner | avoid growing row rendering in an already large file | Yes |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingPilotRows.kt` | New | pilot row rendering and selected styling | focused UI split for row responsibilities | not viewmodel/domain logic | Yes, split target |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchViewModel.kt` | Existing | selected watch target hint and clear behavior | watch owner already lives here | avoid introducing route-local watch mirrors | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchUiState.kt` | Existing | compact panel labels, selected share code, stale/unavailable wording | watch presentation mapper already lives here | map host should render, not derive | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | Existing | compact panel action affordance and wording | current top panel renderer lives here | avoid duplicating panel chrome elsewhere | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/friends/FriendsFlyingViewModelTest.kt` | Existing | selection event regressions | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/friends/FriendsFlyingUiStateTest.kt` | Existing | row model regressions | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/watch/LiveFollowWatchUiStateTest.kt` | Existing | stale/unavailable/pending-switch panel regressions | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/watch/LiveFollowWatchViewModelTest.kt` | Existing | selected-target and clear behavior regressions | closest owner | n/a | No |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Friends Flying recency labels | Wall | current browse list uses user-facing “updated X ago” labels |
| Watch fix age label | existing watch age source | already owned by watch snapshot/UI mapping |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  unchanged; existing ViewModel coroutines and flows only
- Primary cadence/gating sensor:
  existing watch/session and friends list flows
- Hot-path latency budget:
  no new per-frame map work or timer loops

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  unchanged; Friends Flying remains replay-blocked through existing repository
  policy and watch behavior remains driven by existing session/watch inputs.

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| Stale watched pilot | Degraded | watch UI mapper | top panel clearly marked stale with recency/summary retained when possible | user can switch pilots or stop watching | `LiveFollowWatchUiStateTest` |
| Watched pilot no longer live / unavailable | Unavailable | watch UI mapper | top panel shows explicit unavailable wording and does not reintroduce centered card | user can reopen sheet and select another pilot | `LiveFollowWatchUiStateTest` |
| Invalid or failed watch join | Recoverable | watch ViewModel/UI mapper | top panel shows feedback and selection can be changed again | reopen sheet or dismiss feedback | `LiveFollowWatchViewModelTest` |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Duplicate selected-pilot ownership in Friends Flying | `ARCHITECTURE.md` SSOT | review + unit tests | `AppNavGraph.kt`, `LiveFollowWatchViewModelTest.kt` |
| Row mapping business rules drift into UI | `CODING_RULES.md` UI rendering only | unit tests | `FriendsFlyingUiStateTest.kt` |
| Pending switch still shows old watched pilot in top panel | degraded-state clarity | unit tests | `LiveFollowWatchUiStateTest.kt` |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Selected pilot is obvious in panel and list | MS-UX-04 support | weak/implicit selection | explicit panel identity + selected list row | unit tests + manual flow | slice completion |
| Clear returns to browse-only without losing Friends Flying shell | MS-UX-01 support | generic stop action only | compact clear action with panel disappearance and persistent sheet | unit tests + manual flow | slice completion |

## 3) Data Flow (Before -> After)

Before:

`FriendsFlyingViewModel -> OpenWatch(shareCode) -> AppNavGraph -> LiveFollowWatchViewModel -> LiveFollowWatchUiState -> map host`

After:

`FriendsFlyingViewModel -> OpenWatch(selection hint) -> AppNavGraph -> LiveFollowWatchViewModel route feedback -> LiveFollowWatchUiState -> map host + FriendsFlyingScreen selected-row highlight`

## 4) Implementation Phases

- Phase 0:
  add change plan and lock reference patterns.
- Phase 3 state wiring:
  add watch selection hint and selected-share-code exposure in the shared watch owner.
- Phase 3 UI wiring:
  split Friends Flying row rendering, add selected-row styling, and expose clear/stop watching in the top panel.
- Phase 4:
  add/update focused tests and run required verification.

## 5) Test Plan

- Unit tests:
  `FriendsFlyingViewModelTest`, `FriendsFlyingUiStateTest`,
  `LiveFollowWatchUiStateTest`, `LiveFollowWatchViewModelTest`
- Replay/regression tests:
  existing replay-blocked Friends Flying coverage remains
- UI/instrumentation tests:
  manual verification for chooser -> Friends Flying -> select/switch/clear
- Degraded/failure-mode tests:
  stale and unavailable watched-pilot messaging, selected-row state after switch

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Selection hint outlives the real watch state after failures | stale selection/highlight | keep hint inside watch route feedback and clear/reset with stop/dismiss/command results | Codex |
| Friends Flying screen file grows beyond line budget | maintainability drift | split row rendering into `FriendsFlyingPilotRows.kt` before adding selected styling | Codex |

## 7) Acceptance Gates

- No duplicate selected-pilot SSOT
- Selected row follows the current watch target
- Clear/stop watching returns to browse-only Friends Flying mode
- Stale/unavailable watched-pilot state is explicit in the top panel
- Required verification passes
