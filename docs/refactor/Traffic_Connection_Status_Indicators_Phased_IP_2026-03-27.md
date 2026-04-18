# Traffic Connection Status Indicators Phased IP

## 0) Metadata

- Title: Replace traffic failure cards with compact OGN and ADS-B map status indicators
- Owner: XCPro Team
- Date: 2026-03-27
- Issue/PR: TBD
- Status: Draft

## Verdict

`Ready with corrections`

Implementation can start once it follows the rules captured here: keep OGN and ADS-B repository snapshots as the only authoritative source of connection truth, hide legacy card surfacing without deleting the card code, and place the new traffic indicators so they do not collide with the existing top-right `Sharing Live` host.

## Current Investigation Summary

Current map behavior is asymmetric:

- ADS-B already surfaces user-visible status through `AdsbPersistentStatusBadge(...)` and `AdsbIssueFlashBadge(...)` in `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficPanelsAndSheets.kt`.
- OGN failure surfacing currently lives in `OgnDebugPanel(...)` in `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanelsOgn.kt`.
- ADS-B debug detail also lives in a panel path through `AdsbDebugPanel(...)` in `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanelsAdsb.kt`.
- Visibility is owned by `rememberTrafficDebugPanelVisibility(...)` in `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficContentUiState.kt`.
- OGN and ADS-B debug panels are gated by `debugPanelsEnabled = BuildConfig.DEBUG` from `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`.
- ADS-B persistent status is not purely debug-gated and already appears as a real bottom-left card when ADS-B is unhealthy.
- The requested visual reference already exists in `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotMapStatusHost.kt`: a compact top-right circular indicator using `CircleShape` and `Icons.Filled.Lens`.

Failure-state ownership already exists:

- OGN uses `OgnTrafficSnapshot.connectionState` and `lastError` from `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`.
- ADS-B uses `AdsbTrafficSnapshot.connectionState`, `authMode`, `lastError`, and `lastNetworkFailureKind` from `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficModels.kt`.
- ADS-B explicitly reports offline and socket/network failures in runtime paths such as `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt` and `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeLoopTransitions.kt`.
- OGN socket and stream failures flow through `OgnTrafficRepositoryRuntimeConnectionPolicies.kt`, which promotes the snapshot to `OgnConnectionState.ERROR`.

## 1) Scope

- Problem statement:
  Current OGN and ADS-B failure surfacing uses bottom-left cards/panels. The user wants those cards hidden while preserving the code, and wants two compact map indicators instead: one for OGN and one for ADS-B, visually similar to `Sharing Live`, using green for healthy and red for failed.
- Why now:
  The current UX is inconsistent and visually heavy. ADS-B has a persistent card path, OGN mostly has a debug-only panel path, and both compete with map space during degraded conditions such as phone-signal loss or socket failures.
- In scope:
  Investigate current card/panel surfacing, define status-color mapping, define where the new indicators should live, define how legacy cards are hidden but retained, and define the smallest safe implementation sequence.
- Out of scope:
  Changing repository connection semantics, adding new network detectors, changing live-follow ownership, or deleting legacy card/panel composables.
- User-visible impact:
  Two compact traffic indicators appear on the map instead of bottom-left traffic status cards. OGN gets a green/red indicator. ADS-B gets a green/red indicator.
- Rule class touched:
  Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN connection truth | OGN traffic repository snapshot owner | `OgnTrafficSnapshot.connectionState` and `lastError` | UI-local mutable connection state |
| ADS-B connection truth | ADS-B traffic repository snapshot owner | `AdsbTrafficSnapshot.connectionState`, `authMode`, `lastError`, `lastNetworkFailureKind` | UI-local mutable connection state |
| Traffic indicator presentation state | traffic map UI layer | derived UI model from traffic snapshots | repository mirror state or persisted indicator state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| OGN connection indicator UI state | `rememberMapTrafficContentUiState(...)` derived UI owner | snapshot change only | `MapTrafficRuntimeLayer -> indicator host` | `OgnTrafficSnapshot.connectionState`, `lastError`, overlay-enabled flag | None | clears when OGN overlay disabled or state becomes hidden | None new | mapping unit tests |
| ADS-B connection indicator UI state | `rememberMapTrafficContentUiState(...)` derived UI owner | snapshot change only | `MapTrafficRuntimeLayer -> indicator host` | `AdsbTrafficSnapshot.connectionState`, `authMode`, `lastError`, overlay-enabled flag | None | clears when ADS-B overlay disabled or state becomes hidden | None new | mapping unit tests |
| Legacy traffic card surfacing | `MapTrafficPanelsAndSheetsLayer(...)` render path | render-callsite only | traffic map UI layer | existing panel-visibility state | None | inactive after indicator rollout | None | wiring tests or UI assertions |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  `feature:traffic` UI files and a small `feature:map` overlay-input threading change.
- Boundary risk:
  Traffic status must stay derived from existing snapshots. No new repository state, no ViewModel-owned connection truth, and no `feature:traffic` dependency on `feature:livefollow`.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotMapStatusHost.kt` | compact top-right circle indicator already used on MapScreen | top-right host, circular surface, simple color-coded state | traffic needs two indicators and must avoid overlap with pilot status host |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficPanelsAndSheets.kt` | current traffic status surfacing path | keep sheets and traffic UI ownership in the traffic runtime layer | remove active status-card surfacing while keeping card code in repo |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Traffic connection problem surfacing on MapScreen | bottom-left cards/panels in `MapTrafficPanelsAndSheetsLayer(...)` | compact top-right traffic indicator host in traffic UI layer | solve the user request without changing repository truth | mapping tests plus manual map validation |
| Top-right indicator spacing relative to `Sharing Live` | implicit single-owner top-right usage | explicit layout offset or shared strip policy owned by map/traffic UI wiring | avoid visual collision | screenshot/manual validation and UI assertions if added |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapTrafficPanelsAndSheetsLayer(...)` card calls | bottom-left status cards directly surface failure state | traffic indicator host surfaces derived state; legacy card composables stay unsurfaced | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Traffic_Connection_Status_Indicators_Phased_IP_2026-03-27.md` | New | change plan and investigation record | required non-trivial planning artifact | not production code | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficContentUiState.kt` | Existing | derive traffic indicator UI state from existing snapshots | this file already owns traffic presentation derivation | not repository or ViewModel because the state is display-only | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficPanelsAndSheets.kt` | Existing | keep sheets; stop actively surfacing legacy traffic status cards | current active callsite already lives here | not repository or map runtime because this is purely Compose render wiring | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficRuntimeLayer.kt` | Existing | host the new traffic indicator composable | this is the traffic runtime Compose entrypoint | not `feature:map` because traffic UI ownership should stay in traffic slice | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt` | New | pure mapping policy from snapshot states to hidden/green/red indicator model | keeps status policy testable and out of composables | not in repository because it is presentation policy only | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicators.kt` | New | render OGN and ADS-B indicator dots and optional tap detail UI | focused UI file for the new visuals | not in existing panel file because rendering responsibilities differ and should stay small | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt` | Existing | pass pilot-status visibility or top-right reservation into traffic runtime layer | this file already composes traffic and live-follow map runtime hosts together | not repository or ViewModel because this is map overlay composition only | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `MapTrafficRuntimeLayer(..., pilotStatusIndicatorVisible: Boolean, ...)` or equivalent top-right reservation input | traffic map UI layer | `feature:map` runtime composition | keep as narrow as existing callsite allows | traffic indicators must avoid the `Sharing Live` host | no shim needed; update callsite in the same change |
| `MapTrafficConnectionIndicatorModel` | traffic UI layer | traffic UI tests and indicator host | `internal` | pure testable mapping owner | keep internal |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| OGN/ADS-B green-red-hidden indicator mapping policy | `MapTrafficConnectionIndicatorModel.kt` | indicator host and tests | keeps presentation rules in one pure owner instead of scattering state checks across composables | No |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Traffic indicator state transitions | None new | derived directly from existing snapshots; no new timer needed in Phase 1 |
| Optional visual pulse or dwell, if added later | frame/monotonic display time only | must remain display-only and non-authoritative |

No new replay or wall-time logic is required for the requested green/red indicators.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  Compose/UI only for rendering; no new runtime loop.
- Primary cadence/gating sensor:
  existing traffic snapshot updates.
- Hot-path latency budget:
  keep the indicator derivation constant-time and allocation-light so it does not widen map recomposition pressure.

### 2.5 Replay Determinism

- Deterministic for same input:
  Yes.
- Randomness used:
  No.
- Replay/live divergence rules:
  none. The new indicators only read existing snapshot state and render it.

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| OGN overlay disabled | User Action | traffic UI derivation | indicator hidden | none | mapping unit tests |
| OGN connected | Healthy | OGN snapshot owner | green OGN indicator | none | mapping unit tests |
| OGN error from socket/stream failure | Degraded | OGN snapshot owner | red OGN indicator | repository reconnect policy remains unchanged | mapping unit tests |
| OGN disconnected/connecting without explicit error | Unavailable but non-failure | OGN snapshot owner | indicator hidden in Phase 1 | existing repository connect flow remains unchanged | mapping unit tests |
| ADS-B disabled | User Action | ADS-B snapshot owner | indicator hidden | none | mapping unit tests |
| ADS-B active and credentials healthy | Healthy | ADS-B snapshot owner | green ADS-B indicator | none | mapping unit tests |
| ADS-B error, offline, backing off, or credential auth failed | Degraded | ADS-B snapshot owner | red ADS-B indicator | repository retry and auth fallback remain unchanged | mapping unit tests |

Correction captured here:

- Do not treat disabled or precondition-waiting states as red. Red is for explicit unhealthy/degraded states only.
- For ADS-B, `AuthFailed` remains red even when anonymous fallback keeps traffic flowing, because the current product already surfaces that as a user-visible issue.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Duplicate traffic connection state in UI | `ARCHITECTURE.md` authoritative state contract | unit test + review | `MapTrafficConnectionIndicatorModelTest` |
| Business/state policy leaking into composables | responsibility matrix in `ARCHITECTURE.md` | unit test + review | pure model file plus UI host tests |
| Top-right indicator overlap with `Sharing Live` | map overlay UX contract in `CONTRIBUTING.md` and `MAPSCREEN` docs | review + manual map validation | map runtime callsite plus screenshot/manual check |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| traffic indicators do not flicker or overlap with top-right status UI during failure-state transitions | `MS-UX-04` | current top-right host has one indicator owner | no overlap and no redundant show/hide churn during health transitions | manual map validation plus targeted UI assertion if added | Phase 2 |
| added traffic indicators do not materially widen root recomposition pressure | `MS-ENG-09` | current traffic runtime composition | no avoidable broad recomposition from indicator derivation | targeted unit/compose measurement or review-backed profiling if touched | Phase 3 |

## 3) Data Flow (Before -> After)

Before:

`OgnTrafficRepository / AdsbTrafficRepository -> MapScreenViewModel bindings -> MapTrafficUiBinding -> rememberMapTrafficContentUiState(...) -> TrafficDebugPanelVisibility -> bottom-left cards/panels`

After:

`OgnTrafficRepository / AdsbTrafficRepository -> MapScreenViewModel bindings -> MapTrafficUiBinding -> rememberMapTrafficContentUiState(...) -> MapTrafficConnectionIndicatorModel -> top-right traffic indicators`

Legacy card and panel code remains in repo but is no longer actively surfaced from the runtime layer.

## 4) Implementation Phases

### Phase 0: Baseline and Scope Lock

- Goal:
  Lock the exact current traffic status surfaces and the state-mapping rules before UI changes start.
- Files to change:
  this plan doc only.
- Ownership/file split changes in this phase:
  none.
- Tests to add/update:
  none.
- Exit criteria:
  current ADS-B and OGN surfacing paths are documented, and the green/red/hidden mapping rules are agreed.

### Phase 1: Pure Mapping Owner

- Goal:
  Add a pure indicator-model owner that maps traffic snapshots to hidden/green/red states without adding new runtime truth.
- Files to change:
  `MapTrafficContentUiState.kt`, `MapTrafficConnectionIndicatorModel.kt`, new unit tests.
- Ownership/file split changes in this phase:
  indicator policy is moved out of composables into a pure file.
- Tests to add/update:
  mapping tests for OGN connected/error/disabled-like states and ADS-B active/error/backoff/auth-failed/disabled states.
- Exit criteria:
  indicator state is fully derived and unit-tested with no Compose rendering required.

### Phase 2: UI Wiring and Legacy Card Hiding

- Goal:
  Render two compact traffic indicators on the map and stop actively surfacing legacy traffic cards.
- Files to change:
  `MapTrafficConnectionIndicators.kt`, `MapTrafficRuntimeLayer.kt`, `MapTrafficPanelsAndSheets.kt`, `MapScreenContentRuntime.kt`.
- Ownership/file split changes in this phase:
  traffic failure surfacing moves from bottom-left cards to the new indicator host, while sheets remain where they are.
- Tests to add/update:
  targeted UI/render assertions if available, plus wiring tests where practical.
- Exit criteria:
  OGN and ADS-B each show a green or red indicator when enabled, legacy cards no longer appear, and the new indicators do not collide with `Sharing Live`.

### Phase 3: Hardening and Detail Recovery

- Goal:
  Preserve operator detail after card removal and verify map behavior does not regress.
- Files to change:
  optional detail menu or accessibility text files, plus any targeted test files.
- Ownership/file split changes in this phase:
  none.
- Tests to add/update:
  content-description or tap-detail coverage if detail UI is added.
- Exit criteria:
  failure reasons remain discoverable without bringing back bottom-left cards, and impacted overlay SLO evidence is collected when required.

Recommended scope cut:

- Keep Phase 2 minimal.
- Do not add pulse, dwell, or amber state in the first implementation.
- If detail text is needed, prefer a small tap menu using the existing `Sharing Live` pattern rather than reintroducing status cards.

## 5) Test Plan

- Unit tests:
  `MapTrafficConnectionIndicatorModelTest` for OGN and ADS-B mapping rules.
- Replay/regression tests:
  not required beyond normal determinism confidence because no new time or replay path is introduced.
- UI/instrumentation tests:
  optional Compose/UI assertion for indicator presence and top-right non-overlap if the test seam exists.
- Degraded/failure-mode tests:
  explicit red-state tests for ADS-B offline/error/backoff/auth failure and OGN error.
- Boundary tests for removed bypasses:
  confirm legacy card render path is unsurfaced from the active traffic layer.
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| UI state policy | unit tests | traffic indicator model tests |
| Map overlay composition | wiring review plus targeted UI checks | traffic runtime layer inspection and manual validation |
| Failure/degraded behavior | unit tests | red/green/hidden mapping tests |
| Performance-sensitive path | impacted SLO evidence when required | map overlay validation for `MS-UX-04` and review of recomposition scope |

Required checks for the implementation change set:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When a device is available and the map overlay wiring changed materially:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| traffic indicators overlap the existing `Sharing Live` host | broken top-right map UI | thread top-right reservation/visibility into traffic runtime layer or stack the traffic indicators below pilot status | implementation owner |
| disabled or startup-wait states show false red | noisy and misleading UX | use hidden for disabled or non-error waiting states | implementation owner |
| card removal also removes failure detail | loss of diagnosis value | add accessible text and optionally a tap menu in Phase 3 | implementation owner |
| OGN and ADS-B status rules drift apart again | inconsistent UX | keep both mappings in one pure model owner with tests | implementation owner |

## 6A) ADR / Durable Decision Record

- ADR required:
  No.
- ADR file:
  None.
- Decision summary:
  This is a presentation-layer change that preserves existing repository ownership and module boundaries.
- Why this belongs in a plan instead of an ADR:
  No durable boundary or ownership model is being changed.

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Legacy card code remains available but is not surfaced from the active map runtime layer
- OGN and ADS-B mapping rules are explicit and unit-tested
- Top-right indicator placement does not collide with `Sharing Live`
- For map overlay behavior changes, impacted SLO validation is captured or a documented deviation is approved

## 8) Rollback Plan

- What can be reverted independently:
  the new traffic indicator host, the runtime-layer callsite changes, and the hidden-card surfacing change.
- Recovery steps if regression is detected:
  restore `MapTrafficPanelsAndSheetsLayer(...)` card surfacing, remove the indicator host callsite, and re-run the targeted traffic UI tests and required Gradle gates.

## Recommended Next Step

Implement Phase 1 and Phase 2 in the `targeting` branch, with one pure indicator-model file, one focused traffic-indicator composable file, and a small map runtime threading change to reserve space below the existing `Sharing Live` host.
