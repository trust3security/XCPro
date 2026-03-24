# LIVEFOLLOW Startup Chooser Change Plan

## 0) Metadata

- Title: LIVEFOLLOW Startup Chooser Slice
- Owner: Codex
- Date: 2026-03-22
- Issue/PR: local startup chooser slice
- Status: Complete

## 1) Scope

- Problem statement:
  - XCPro launched straight into `map`, which triggered pilot-side permission/sensor startup before the user chose between Flying and Friends Flying.
- Why now:
  - The Friends Flying list/watch slice already exists and the next approved product step is a launch-time chooser.
- In scope:
  - chooser start route
  - Flying handoff into the existing pilot sharing path plus existing map
  - Friends Flying handoff into the existing bottom-sheet list and watch flow
  - launch-time permission gating so pilot-only permission is not requested on app start
- Out of scope:
  - new backend endpoints
  - transport changes
  - multi-pilot map work
  - watch architecture redesign
- User-visible impact:
  - cold launch now opens a chooser with `Flying` and `Friends Flying`
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Launch-time sensor-start gate | `MainActivityScreen` app-shell UI state | Compose state passed into `AppNavGraph` | repository/domain copies |
| LiveFollow pilot session truth | `LiveFollowSessionRepository` | existing `state` flow | chooser-local mirrors |
| Friends Flying list truth | `FriendsFlyingRepository` | existing `state` flow | chooser-owned cached lists |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `allowFlightSensorStart` | `MainActivityScreen` | chooser Flying/Friends selection, pilot-route entry | `AppNavGraph` -> `MapScreen` | user launch choice | none | process restart / app-shell reset | n/a | manual launch tests |
| one-shot Flying auto-start map signal | `NavBackStackEntry.savedStateHandle` for `map` | startup navigation helper | `AppNavGraph` map route | chooser Flying action | none | consumed immediately on map entry | n/a | `LiveFollowPilotViewModelTest` plus manual launch test |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  - `app`
  - `feature:map`
  - `feature:livefollow`
- Any boundary risk:
  - startup chooser must not become a LiveFollow session owner; it only routes into existing owners.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `app/src/main/java/com/example/xcpro/appshell/settings/MapGeneralSettingsNavigation.kt` | one-shot map saved-state navigation signal | saved-state request/consume helper | signal is startup/livefollow-specific instead of settings-specific |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | existing map-backed LiveFollow handoff | ensure `map` host exists before watch flow consumes shared state | Flying path uses pilot auto-start instead of watch join |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| launch-time location permission request | `MainActivity` on app launch | chooser Flying action | pilot-only permission must not fire on launch or Friends path | manual launch test + `assembleDebug` |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MainActivity.onCreate()` | unconditional location permission/service start | chooser-owned Flying action | complete |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `app/src/main/java/com/example/xcpro/startup/StartupChooserScreen.kt` | New | chooser rendering and permission/action forwarding | app-shell entry UI | not domain/data; not a LiveFollow SSOT owner | no |
| `app/src/main/java/com/example/xcpro/startup/StartupChooserNavigation.kt` | New | chooser route and one-shot map handoff helpers | app-shell navigation concern | avoids spreading startup routing rules across screens | no |
| `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | Existing | route registration and route-to-route handoff | navigation owner already lives here | avoids new navigation host layer | no |
| `app/src/main/java/com/example/xcpro/MainActivityScreen.kt` | Existing | app-shell launch gate state | screen already owns root nav/controller composition | not domain state | no |
| `app/src/main/java/com/example/xcpro/MainActivity.kt` | Existing | remove app-launch pilot side effect | activity previously owned it | chooser now owns launch request timing | no |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/pilot/LiveFollowPilotViewModel.kt` | Existing | one-shot auto-start orchestration using existing use case | ViewModel already owns pilot intent orchestration | start command must stay out of UI | no |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreen.kt` | Existing | map UI parameter threading only | map UI shell already owns these arguments | avoids hidden global gate | no |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | Existing | combine app-shell gate with existing replay gate | map runtime entry point already owns sensor-start wiring | avoids repository pollution | no |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| new startup chooser state | n/a | no new time-dependent logic |

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - unchanged from existing app/runtime owners
- Primary cadence/gating sensor:
  - unchanged
- Hot-path latency budget:
  - unchanged

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - chooser only changes whether live sensors are allowed to start before the user chooses Flying; replay/watch ownership is unchanged.

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| Flying permission denied | User Action | chooser UI | existing location-permission-required toast | user can tap `Flying` again later | manual |
| no active pilots | Unavailable | existing Friends Flying UI state | existing empty-state message | refresh remains available | existing Friends Flying tests/manual |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| startup chooser accidentally owns session data | `ARCHITECTURE.md` SSOT / ownership rules | review + unit tests on existing owners | this plan, `LiveFollowPilotViewModelTest` |
| app launch still triggers pilot permission | `AGENTS.md` slice brief / permission rule | manual review + assemble/runtime validation | `MainActivity.kt`, `StartupChooserScreen.kt` |
| auto-start bypasses existing pilot command path | `CODING_RULES.md` UseCase/ViewModel boundary | JVM test | `LiveFollowPilotViewModelTest` |

## 3) Data Flow (Before -> After)

Before:

`App launch -> MainActivity permission request -> map -> user finds downstream flow`

After:

`App launch -> StartupChooserScreen -> map host -> existing LiveFollow pilot/friends/watch flows`

## 4) Implementation Phases

- Phase 1:
  - Goal: add chooser route/UI and remove launch-time permission request.
  - Files to change: `MainActivity.kt`, `MainActivityScreen.kt`, `AppNavGraph.kt`, startup files.
- Phase 2:
  - Goal: gate map sensor startup and add one-shot pilot auto-start handoff.
  - Files to change: `MapScreen.kt`, `MapScreenRoot.kt`, `LiveFollowPilotViewModel.kt`.
- Phase 3:
  - Goal: add regression coverage and run required local gates.
  - Files to change: `LiveFollowPilotViewModelTest.kt`.

## 5) Test Plan

- Unit tests:
  - `LiveFollowPilotViewModelTest` auto-start immediate/waiting cases
- Replay/regression tests:
  - none required; replay logic unchanged
- UI/instrumentation tests (if needed):
  - deferred; manual launch path verification used for this slice
- Degraded/failure-mode tests:
  - permission denial handled by chooser toast (manual)
- Boundary tests for removed bypasses:
  - `MainActivity` no longer requests permission on launch (manual)

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| hidden map route assumptions after changing start destination | wrong back stack / chooser resurfacing unexpectedly | central `ensureMapRoute(...)` helper | Codex |
| auto-start runs before ownship snapshot exists | sharing fails on Flying entry | wait for snapshot, then call existing `startSharing()` path once | Codex |
| Flying path lacks visible failure UI on map | lower observability for missing identity/transport issues | keep diff small for this slice; preserve old pilot screen for detailed status | follow-on slice |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file:
- Decision summary:
  - no durable module/API ownership move; this is a local app-entry integration slice.
- Why this belongs in an ADR instead of plan notes:
  - not required

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Existing pilot/friends/watch owners remain authoritative
- Startup chooser becomes the launch route
- Flying does not start sharing until explicit user action
- Friends Flying does not request pilot-only permission on app launch

## 8) Rollback Plan

- What can be reverted independently:
  - chooser route files
  - `MainActivity` launch-time permission removal
  - pilot auto-start helper
- Recovery steps if regression is detected:
  - revert startup chooser routing and restore prior `map` start destination while keeping Friends Flying list/watch code intact
