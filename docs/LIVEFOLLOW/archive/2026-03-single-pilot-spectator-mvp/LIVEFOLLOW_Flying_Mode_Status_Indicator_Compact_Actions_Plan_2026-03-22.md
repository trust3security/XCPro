## 0) Metadata

- Title: Flying mode status indicator and compact actions
- Owner: Codex
- Date: 2026-03-22
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  Flying mode currently has working auto-start/share commands but no compact
  in-map status light. The old debug/standalone surfaces are too large for the
  chooser-owned map flow.
- Why now:
  Startup chooser, Friends Flying browse/watch shell, and watched-pilot polish
  are already in place. The remaining product gap is a discreet Flying share
  status/control experience on the map.
- In scope:
  tiny top-safe-area share status indicator, lightweight temporary controls,
  exact starting/live/failed/stopped visual mapping, and removal of the large
  centered debug LiveFollow launcher card.
- Out of scope:
  backend, transport, auth, multi-pilot map, Friends Flying behavior, and a
  large persistent Flying panel/card.
- User-visible impact:
  Flying mode gets an always-visible status light and lightweight share
  controls without cluttering the map.
- Rule class touched: Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Pilot sharing/session truth | `LiveFollowSessionRepository` via `LiveFollowPilotUseCase` | `sessionState` | map-local authoritative sharing state |
| Pilot sharing UI/command state | `LiveFollowPilotViewModel` | `LiveFollowPilotUiState` | duplicate map-host sharing state owners |
| Flying-vs-Friends gate | app-shell runtime state (`allowFlightSensorStart`) | map input threading | repository/global singleton mode flag |
| Overlay expanded/dismissed state | map overlay UI only | local Compose state | repository/viewmodel persistence of temporary menu chrome |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Flying share indicator state | `LiveFollowPilotUiState` mapper | pilot ViewModel/action state only | map overlay + standalone pilot screen | session snapshot + action state | none | share start/stop/result updates | n/a | `LiveFollowPilotUiStateTest` |
| Last share failure hint | pilot ViewModel action state | start/stop command runner only | `LiveFollowPilotUiState` builder | command results | none | cleared on next share command start or successful stop/start | n/a | `LiveFollowPilotViewModelTest` |
| Temporary status surface open/closed | pilot map overlay composable | local UI only | Flying map overlay | user tap/dismiss | none | dismiss/tap outside | n/a | manual + focused compile coverage |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  `app`, `feature:map`, `feature:livefollow`
- Boundary risk:
  the map host could become a second pilot sharing owner if it starts caching
  command/status state instead of reading the pilot ViewModel.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | existing map-side LiveFollow overlay seam | keep overlay injection on the map side | add pilot overlay beside the existing watch overlay |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/watch/LiveFollowWatchEntryRoute.kt` | existing compact top-safe-area LiveFollow overlay host | reuse top-safe-area overlay placement and lightweight controls | render a tiny tappable indicator plus menu instead of a card |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSectionInputs.kt` | existing app-shell flags threaded to content/overlay seams | reuse explicit input threading | add Flying-mode gate to overlay inputs |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Flying share status visibility on map | none / legacy standalone pilot screen only | pilot map overlay host | map is now the authoritative Flying shell | unit tests + compile + manual flow |
| Debug LiveFollow chooser card | map scaffold content host | removed | startup chooser already owns mode entry and the card conflicts with the slice | compile + manual flow |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| centered debug `TemporaryLiveFollowLauncher` in map scaffold host | duplicate mode-entry surface on map | startup chooser remains the only mode-entry owner | Phase 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/LIVEFOLLOW/LIVEFOLLOW_Flying_Mode_Status_Indicator_Compact_Actions_Plan_2026-03-22.md` | New | slice plan and ownership record | required non-trivial change plan | not runtime code | No |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotUiState.kt` | Existing | derived share indicator state mapping | pilot presentation mapping already lives here | keep map UI free of pilot-state derivation logic | No |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotViewModel.kt` | Existing | start/stop command ownership and failure hint tracking | command owner already lives here | avoid map-side command bookkeeping | No |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotMapStatusHost.kt` | New | tiny indicator and temporary surface rendering | focused Flying overlay UI | not suitable for the standalone pilot screen file | Yes, intentional split |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt` | Existing | compose both watch and pilot overlays | current map-side LiveFollow seam | keep overlay composition out of app/nav layers | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSectionInputs.kt` | Existing | overlay input contract | existing content/overlay input owner | avoid hidden globals | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt` | Existing | thread Flying gate to content/overlay inputs | current screen input assembly owner | not ViewModel/domain state | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt` | Existing | pass overlay inputs to LiveFollow runtime layer | current content host owner | not a new overlay owner | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldContentHost.kt` | Existing | remove obsolete centered debug launcher | current debug launcher owner | avoid stale duplicate mode-entry UI | No |
| `feature/livefollow/src/test/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotUiStateTest.kt` | Existing | indicator-state mapping regressions | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotViewModelTest.kt` | Existing | auto-start failure and command-state regressions | closest owner | n/a | No |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Indicator animation cadence | Compose UI animation clock only | visual-only, not authoritative state |
| Pilot share status | existing session/action state | no new time-derived pilot status logic |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  unchanged; existing ViewModel coroutines only
- Primary cadence/gating sensor:
  existing pilot session/ownship/action-state flows
- Hot-path latency budget:
  no per-frame domain work; only small Compose animations on the overlay

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  unchanged; the pilot state remains blocked through existing side-effects rules.

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| Auto-start or explicit start failure | Recoverable | pilot ViewModel/UI state | flashing red indicator + compact Retry action | reuse existing start-sharing path | `LiveFollowPilotViewModelTest`, `LiveFollowPilotUiStateTest` |
| Explicit stop | User Action | pilot ViewModel/UI state | solid red indicator + compact Start sharing action | no auto-restart | `LiveFollowPilotUiStateTest` |
| Starting | Degraded / transitional | pilot UI state + overlay UI | green pulsing indicator + starting message | wait for existing start path completion | `LiveFollowPilotUiStateTest` |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Map host becomes a second pilot-state owner | `ARCHITECTURE.md` SSOT | review + unit tests | `MapLiveFollowRuntimeLayer.kt`, `LiveFollowPilotViewModelTest.kt` |
| Failure vs stopped become ambiguous | UX requirement for exact mapping | unit tests | `LiveFollowPilotUiStateTest.kt` |
| Large map card remains in Flying mode | startup chooser owns mode entry | review + compile | `MapScreenScaffoldContentHost.kt` |

## 3) Data Flow (Before -> After)

Before:

`LiveFollowPilotViewModel -> standalone pilot screen only`

After:

`LiveFollowPilotViewModel -> LiveFollowPilotUiState -> map overlay host (Flying only) -> tiny status indicator + temporary compact controls`

## 4) Implementation Phases

- Phase 0:
  add plan and lock state ownership.
- Phase 3 pilot state:
  extend existing pilot UI/action state with exact indicator mapping.
- Phase 3 map shell:
  add compact overlay host, thread Flying gate, and remove centered debug launcher.
- Phase 4:
  add/update focused tests and run verification.

## 5) Test Plan

- Unit tests:
  `LiveFollowPilotUiStateTest`, `LiveFollowPilotViewModelTest`
- Replay/regression tests:
  existing replay-blocked pilot coverage remains
- UI/instrumentation tests:
  manual verification for chooser -> Flying -> start/live/stop/fail/retry

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| `allowFlightSensorStart` overreaches beyond the chooser-selected Flying session | indicator may appear in the wrong shell | thread it only as a map overlay gate and keep pilot state authoritative in the pilot ViewModel | Codex |
| Removing the debug launcher hides a developer convenience path | debug-only entry change | startup chooser and direct routes still exist; document the assumption in the final report | Codex |

## 7) Acceptance Gates

- Tiny Flying indicator is always visible in Flying mode only
- Starting/live/failed/stopped use the exact required color/animation mapping
- Tap opens a lightweight temporary control surface
- Live shows share code + Stop
- Failed/stopped show Retry/Start through the existing pilot command owner
- No large persistent map card remains
