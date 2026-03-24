## 0) Metadata

- Title: Friends Flying browse/watch shell cleanup
- Owner: Codex
- Date: 2026-03-22
- Issue/PR: TBD
- Status: Complete

## 1) Scope

- Problem statement:
  Friends Flying still behaves like a transient modal chooser layered over the map.
  The list can fully disappear, the old watch card remains too large for the new
  chooser-owned product flow, and active/stale rows are not clearly grouped.
- Why now:
  Startup chooser and active-pilots parser fixes are already in place, so the
  remaining usability problem is the browse/watch shell on top of the map.
- In scope:
  Persistent peek/expanded Friends Flying sheet behavior, active/stale grouping,
  and a compact top-anchored watched-pilot panel.
- Out of scope:
  Backend, transport, auth, multi-pilot map, and Flying-mode UI changes.
- User-visible impact:
  Friends Flying remains re-openable, browsing no longer shows the old card, and
  single-pilot watch context moves to a compact top panel.
- Rule class touched: Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active public pilots list | `FriendsFlyingRepository` | `FriendsFlyingSnapshot` via use case/viewmodel | UI-local authoritative pilot list state |
| Single watched pilot session/watch state | `LiveFollowWatchViewModel` + watch/session repositories | `LiveFollowWatchUiState` | Friends Flying route-local watch mirrors |
| Friends Flying sheet expanded vs peek state | Friends Flying route UI only | local scaffold state | repository/viewmodel persistence of sheet chrome |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Friends Flying pilot sections | `FriendsFlyingUiState` mapper | snapshot mapper only | `FriendsFlyingViewModel.uiState` | `FriendsFlyingSnapshot.items` | none | refreshed from repo snapshot | Wall for recency labels | `FriendsFlyingUiStateTest` |
| Friends Flying sheet peek/expanded | route composable | route composable only | Friends Flying screen | user drag/tap/select actions | none | route leaves back stack | n/a | manual + screen behavior assertions via UI mapping tests |
| Compact watch panel summary | `buildLiveFollowWatchUiState` | watch/session state owners only | map runtime layer | watch session + watch traffic snapshot | none | watch stop / feedback dismiss | existing watch state timebase only | `LiveFollowWatchUiStateTest` |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  `app`, `feature:livefollow`, `feature:map`
- Boundary risk:
  the Friends Flying route can accidentally become a new watch/session owner if it
  starts storing selected-pilot state instead of delegating to the existing watch
  ViewModel.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | already orchestrates map-entry-scoped LiveFollow actions from the nav graph | reuse shared map-entry-scoped ViewModel wiring | Friends Flying will call the shared watch ViewModel directly instead of routing through a transient entry screen |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | already owns map-side watch overlay injection | keep watch panel rendering on the map side | render a compact panel instead of the old verbose card |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Friends Flying row-to-watch handoff | nav route bounce through share-entry route | shared map-entry watch ViewModel from `AppNavGraph` | preserve sheet visibility and avoid transient route flash | `FriendsFlyingViewModelTest` + manual browse/watch flow |
| Watch panel layout | verbose map card in watch entry file | compact top panel in same watch map-host seam | chooser now owns mode selection | `LiveFollowWatchUiStateTest` + manual map verification |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Friends Flying selection -> transient watch route -> map handoff | route bounce just to invoke shared watch join logic | invoke shared watch ViewModel directly from the Friends Flying route shell | Phase 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/LIVEFOLLOW/LIVEFOLLOW_Friends_Flying_Browse_Watch_Shell_Cleanup_Plan_2026-03-22.md` | New | slice intent and ownership record | required in-repo plan for non-trivial shell change | not runtime code | No |
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | Existing | nav-graph orchestration into shared watch ViewModel | this is the current map-entry orchestration owner | not a feature ViewModel concern | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingScreen.kt` | Existing | sheet scaffold and grouped list rendering | route UI owns display-only sheet chrome | not repository/domain logic | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/friends/FriendsFlyingUiState.kt` | Existing | list grouping/sorting/presentation mapping | presentation mapper already lives here | UI should not sort/group raw models directly | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchUiState.kt` | Existing | compact watch-panel display fields | presentation mapper already owns watch UI labels | map runtime layer should render, not derive labels | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | Existing | compact map-host panel rendering | current map-host watch surface already lives here | avoid inventing a new watch shell owner | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/friends/FriendsFlyingUiStateTest.kt` | Existing | grouped-list regressions | current UI mapper tests already live here | closest test owner | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/watch/LiveFollowWatchUiStateTest.kt` | Existing | compact watch-panel mapping regressions | current watch UI-state tests already live here | closest test owner | No |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Friends Flying recency labels | Wall | existing user-facing "updated X ago" behavior |
| Watch fix age label | Existing watch age source | already owned by watch UI state |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  unchanged; existing ViewModel coroutines only
- Primary cadence/gating sensor:
  existing repository/watch state flows
- Hot-path latency budget:
  no new map hot-path collectors or frame loops

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  unchanged; Friends Flying remains replay-blocked by the existing repository logic

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| No active pilots | Degraded | Friends Flying UI mapper | empty/active-empty message in sheet | manual refresh | `FriendsFlyingUiStateTest` |
| Stale pilots only | Degraded | Friends Flying UI mapper | active-empty message plus secondary stale section | stale rows remain selectable | `FriendsFlyingUiStateTest` |
| Watch join feedback/error | Recoverable | watch ViewModel/UI mapper | compact top panel message instead of old card | reopen sheet and select again or dismiss | `LiveFollowWatchUiStateTest` |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Friends Flying starts owning watch state | `ARCHITECTURE.md` layering/SSOT | review + unit tests | `AppNavGraph.kt`, `FriendsFlyingViewModelTest.kt` |
| UI sorts/groups raw active-pilot models ad hoc | `CODING_RULES.md` UI rendering only | unit tests | `FriendsFlyingUiStateTest.kt` |
| Compact panel hides critical watch feedback | `CODEBASE_CONTEXT_AND_INTENT.md` degraded states explicit | unit tests | `LiveFollowWatchUiStateTest.kt` |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Friends Flying sheet remains visibly re-openable | MS-UX-04 style discoverability support | fully hideable modal sheet | persistent peek state | manual + focused UI mapping verification | slice completion |
| Watch context no longer occupies the center | MS-UX-01 support | verbose top-center card | compact top-safe-area panel | manual map verification | slice completion |

## 3) Data Flow (Before -> After)

Before:

`FriendsFlyingViewModel -> event -> nav route bounce -> watch entry route -> shared watch ViewModel -> map host`

After:

`FriendsFlyingViewModel -> event -> shared watch ViewModel -> map host`

## 4) Implementation Phases

- Phase 0:
  lock reference files and update the plan.
- Phase 3 UI shell:
  update Friends Flying sheet scaffold and grouped list rendering.
- Phase 3 watch shell:
  update AppNavGraph handoff and compact map watch panel.
- Phase 4:
  add/update focused tests and run required verification.

## 5) Test Plan

- Unit tests:
  `FriendsFlyingUiStateTest`, `LiveFollowWatchUiStateTest`
- Replay/regression tests:
  existing replay-blocked Friends Flying coverage remains
- UI/instrumentation tests:
  manual verification only for this slice
- Degraded/failure-mode tests:
  stale-only list and watch feedback mapping
- Boundary tests for removed bypasses:
  selection still emits the existing share-code watch handoff event

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Transparent scaffold route behaves differently than modal sheet | map/sheet interaction regression | keep content transparent and verify manually from chooser -> Friends Flying -> select pilot | Codex |
| Compact panel loses important error visibility | reduced watch-debug visibility | keep feedback/dismiss/stop affordances in the compact panel | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file:
- Decision summary:
  This slice reuses existing owners and does not add a new durable boundary.
- Why this belongs in an ADR instead of plan notes:
  n/a

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Replay behavior remains deterministic
- Friends Flying sheet keeps a visible peek state
- Watch card is replaced with a compact top panel

## 8) Rollback Plan

- What can be reverted independently:
  Friends Flying sheet scaffold changes and compact watch panel changes
- Recovery steps if regression is detected:
  revert the affected UI shell changes, keep parser/startup chooser work intact, rerun required verification
