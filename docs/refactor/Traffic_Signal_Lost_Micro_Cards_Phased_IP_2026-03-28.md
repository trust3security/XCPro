# Traffic Signal Lost Micro-Cards Phased IP

## 0) Metadata

- Title: Add compact ADS-B and OGN "signal lost" micro-cards on MapScreen
- Owner: XCPro Team
- Date: 2026-03-28
- Issue/PR: TBD
- Status: Draft

## Verdict

`Ready with corrections`

This change is ready to implement once the visual-height contract is locked:

- `ADS-B signal lost` and `OGN connection lost` must render as single-line micro-cards in the existing top-right stack, or
- the current top-right stack offset contract must be widened in the same change set so `Sharing Live` cannot overlap them.

## 1) Scope

- Problem statement:
  The current top-right traffic status UI is icon-only. The requested behavior is that when ADS-B signal is lost, a very small card displays `ADS-B signal lost`, and when OGN connection is lost, a very small card displays `OGN connection lost`.
- Why now:
  The current red-dot failure state is compact but too implicit. The user wants explicit lost-state wording without bringing back large bottom-left status cards.
- In scope:
  - derive explicit lost-card presentation from the existing OGN and ADS-B snapshots
  - render small top-right cards only for lost/unhealthy connection states
  - keep the current healthy-state traffic UI as compact as possible
  - preserve the existing top-right stack with `Sharing Live`
  - brighten OGN glider relative-altitude map colors for `ABOVE -> green` and `BELOW -> blue`
- Out of scope:
  - changing repository retry/network semantics
  - adding new connectivity detection sources
  - changing LiveFollow state ownership
  - restoring large bottom-left status cards
  - replay/timebase changes
  - changing OGN relative-altitude band thresholds or semantics
- User-visible impact:
  - healthy traffic state remains compact
  - explicit lost-state text appears for ADS-B and OGN when the corresponding connection is lost
- Rule class touched:
  Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN connection truth | OGN traffic repository snapshot owner | `OgnTrafficSnapshot.connectionState`, `lastError` | UI-local mutable OGN state |
| ADS-B connection truth | ADS-B traffic repository snapshot owner | `AdsbTrafficSnapshot.connectionState`, `authMode`, `lastError`, `lastNetworkFailureKind`, `networkOnline` | UI-local mutable ADS-B state |
| Lost-card presentation state | traffic map UI derivation owner | derived UI model from the existing snapshots | repository-owned card state or persisted card state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| OGN lost-card UI state | `rememberMapTrafficContentUiState(...)` derived UI owner | snapshot change only | `MapTrafficRuntimeLayer -> top-right traffic host` | `OgnTrafficSnapshot.connectionState`, `lastError`, overlay-enabled flag | None | clear when OGN overlay disabled or state returns healthy/startup-hidden | None new | mapping tests + UI rendering tests |
| ADS-B lost-card UI state | `rememberMapTrafficContentUiState(...)` derived UI owner | snapshot change only | `MapTrafficRuntimeLayer -> top-right traffic host` | `AdsbTrafficSnapshot.connectionState`, `lastError`, `lastNetworkFailureKind`, `networkOnline`, overlay-enabled flag | None | clear when ADS-B overlay disabled or state returns healthy/non-lost degraded | None new | mapping tests + UI rendering tests |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  Primarily `feature:traffic` UI/model files. `feature:map` top-right stack wiring is touched only if the new card height exceeds the current stack slot budget.
- Boundary risk:
  Do not create a second connection-truth owner in UI. The lost-card text must stay derived from repository snapshots.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt` | already owns compact traffic status mapping from snapshots | keep presentation policy pure and testable | extend from dot-only states to dot-or-lost-card states |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficPanelsAndSheets.kt` | already renders a copy-bearing ADS-B status badge | reuse compact badge copy styling and explicit text ownership | render in the top-right traffic host instead of bottom-left card surfacing |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotMapStatusHost.kt` | existing top-right status host that must not overlap | preserve the current top-right stacking contract | traffic may need a larger slot if the micro-card height grows |

### 2.2A.0 Narrow Seam / Code Pass (Phase 0)

Actual code pass findings:

- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  exposes only `OgnTrafficSnapshot.connectionState` plus `lastError` for OGN
  health. There is no OGN snapshot subtype that distinguishes RF/signal-loss
  from other transport failures, so Phase 0 should freeze the OGN copy as
  `OGN connection lost`, not `OGN signal lost`.
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`
  stamps `OgnConnectionState.ERROR` from caught connection/runtime failures and
  returns to `CONNECTING` / `CONNECTED` / `DISCONNECTED` explicitly. That makes
  OGN Phase 0 wording a connection-state contract, not a richer cause contract.
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficModels.kt`
  already exposes the exact Phase 0 discriminators needed for wording:
  `connectionState`, `authMode`, `lastHttpStatus`, `lastNetworkFailureKind`,
  `networkOnline`, and `lastError`.
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbProviderClient.kt`
  proves ADS-B network failures are typed as `DNS`, `TIMEOUT`, `CONNECT`,
  `NO_ROUTE`, `TLS`, `MALFORMED_RESPONSE`, and `UNKNOWN`. Phase 0 should lock
  `ADS-B signal lost` to a conservative transport-loss subset only, not to all
  provider failures.
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt`
  explicitly stamps offline waiting as `AdsbConnectionState.Error(ADSB_ERROR_OFFLINE)`
  with `lastNetworkFailureKind = NO_ROUTE`. This is a strong Phase 0 anchor for
  when `ADS-B signal lost` is allowed.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficPanelsAndSheets.kt`
  and `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanelsSupport.kt`
  already contain broader wording such as `ADS-B Offline`, `ADS-B Backoff`,
  `ADS-B Credential Issue`, and verbose debug reasons. These files are useful
  references but must not become the copy authority for the new top-right
  wording contract.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt`
  and its current tests show the existing red-dot model collapses
  `AuthFailed`, generic `BackingOff`, and `Error` into the same red state. That
  is exactly why Phase 0 must freeze the wording/exclusion rules before Phase 1
  extends the model.

Phase 0 scope cut:

- keep Phase 0 to this plan doc only; no production code edits
- make snapshot-field semantics the wording authority, not current UI strings
- freeze `OGN connection lost` as the only OGN lost-state copy in the first
  implementation
- freeze `ADS-B signal lost` only for ADS-B states backed by offline/transport
  failure evidence from `connectionState`, `lastNetworkFailureKind`,
  `networkOnline`, or `ADSB_ERROR_OFFLINE`
- explicitly exclude `AuthFailed`, HTTP `429`, `MALFORMED_RESPONSE`, TLS,
  and generic/unknown provider failures from the first `signal lost` contract

### 2.2A.1 Narrow Seam / Code Pass (Phase 1)

Actual code pass findings:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt`
  is only 83 lines today and already owns the icon-only status mapping from
  snapshots. This is the narrowest safe Phase 1 owner for the new lost-card
  presentation policy.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficContentUiState.kt`
  is already 316 lines and mixes multiple derived traffic concerns. Phase 1
  should keep this file to wiring only:
  add or rename one derived field and call the pure builder; do not place new
  lost-state policy branches here.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficPanelsAndSheets.kt`
  has reusable compact-card styling, but its `persistentAdsbStatusPresentation(...)`
  models broader ADS-B degraded states (`Offline`, `Backoff`, `Credential Issue`,
  `Active`). Phase 1 must not reuse that mapping as-is for the narrower
  `signal lost` wording.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanelsSupport.kt`
  also has broader debug-oriented predicates and labels. It can be a reference
  for network-failure signals only; it should not become the direct owner of the
  new user-facing `signal lost` copy.

Phase 1 scope cut:

- keep all new policy in `MapTrafficConnectionIndicatorModel.kt`
- keep `MapTrafficContentUiState.kt` to one narrow derived-state wiring change
- keep `MapTrafficPanelsAndSheets.kt` and `MapTrafficDebugPanelsSupport.kt`
  read-only in Phase 1 unless a tiny pure predicate extraction is proven
  necessary

### 2.2A.2 Narrow Seam / Code Pass (Phase 2)

Actual code pass findings:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicators.kt`
  is only 93 lines today and already owns the top-right traffic column layout,
  pill rendering, stack spacing, and `followingIndicatorTopOffset()` reserve
  math. This is the narrowest safe Phase 2 owner for dot-vs-micro-card
  rendering and any required reserved-height export.
- `feature/traffic/src/test/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorsUiTest.kt`
  is only 40 lines and already hosts an isolated Compose test for the traffic
  status host. This is the narrowest safe Phase 2 test owner for copy
  presence/absence and accessibility regression coverage.
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`
  is already 417 lines and currently consumes only a count-based traffic offset.
  Phase 2 must keep this file thin:
  consume a traffic-owned reserved-height seam if the lost-card height differs,
  but do not duplicate traffic card constants or branching here.
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt`
  and `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/pilot/LiveFollowPilotMapStatusHost.kt`
  already consume a generic `Dp` offset and do not own traffic layout policy.
  They should stay read-only unless a signature-only pass-through is unavoidable.
- Inference from current code:
  `followingIndicatorTopOffset()` reserves `24.6.dp` per visible traffic row
  (`10.8 + 0.9 + 0.9 + 12`). A text-bearing micro-card may exceed that budget,
  so Phase 2 must treat the offset seam as part of the implementation path, not
  as a late cleanup.
- Because the current UI test is host-isolated and text-presence based, the
  single-line contract should be encoded directly in the renderer
  (`maxLines = 1`, no wrap/overflow growth) rather than left to manual review
  alone.

Phase 2 scope cut:

- keep dot-vs-micro-card rendering, spacing, and reserved-height math in
  `MapTrafficConnectionIndicators.kt`
- keep `MapScreenContentRuntime.kt` to one thin consumer update if the
  traffic-owned reserve seam changes
- keep `MapTrafficRuntimeLayer.kt`, `MapLiveFollowRuntimeLayer.kt`, and
  `LiveFollowPilotMapStatusHost.kt` read-only unless a signature-only pass
  through is unavoidable

### 2.2A.3 Narrow Seam / Code Pass (Companion OGN Glider Palette)

Actual code pass findings:

- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnRelativeAltitudeFeatureMapper.kt`
  already owns the band-to-style choice for OGN gliders:
  `ABOVE`, `BELOW`, and `NEAR/UNKNOWN`. The user request does not require
  changing this mapping; it only asks for brighter colors.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt`
  owns the actual tint constants for those glider variants:
  `RELATIVE_GLIDER_ABOVE_TINT`, `RELATIVE_GLIDER_BELOW_TINT`, and
  `RELATIVE_GLIDER_NEAR_TINT`. This is the narrowest safe owner for the
  brightness change.
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
  only registers the tinted style images from those constants. It should stay
  read-only unless the image IDs or tint-generation flow itself must change.

Companion palette scope cut:

- keep the band rules as `ABOVE -> green`, `BELOW -> blue`, `NEAR/UNKNOWN -> black`
- change brightness in `OgnTrafficOverlayConfig.kt` only unless validation
  proves a deeper overlay change is required
- do not mix this request with the traffic connection indicator red/green tones;
  this palette request is for actual OGN glider map markers

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Lost-state wording for top-right traffic UI | none; current top-right host is icon-only | existing traffic presentation-model owner | keep explicit wording derived in one pure place | unit tests |
| Compact lost-state rendering | icon-only top-right traffic host | same top-right traffic host with an additional small-card mode | satisfy the UX request without changing repository truth | UI tests + manual map validation |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AdsbPersistentStatusBadge(...)` for this specific "signal lost" ask | copy-bearing status exists on a separate bottom-left path | top-right traffic host becomes the visible lost-state surface | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Traffic_Signal_Lost_Micro_Cards_Phased_IP_2026-03-28.md` | New | change plan and scope lock | required planning artifact | not production code | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt` | Existing | pure mapping from traffic snapshots to healthy-dot vs lost-card presentation | already owns traffic status presentation derivation and is still small | not in composables because the policy must stay testable | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicators.kt` | Existing | top-right traffic status rendering and reserved-height contract for following indicators | already owns the top-right traffic stack rendering and offset math | not in `feature:map`; traffic UI ownership should stay in traffic slice | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt` | Existing, conditional | relative-altitude OGN glider tint constants | already owns the `ABOVE` / `BELOW` / `NEAR` tint values | not in mapper because band selection and color constants are separate responsibilities | No |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficContentUiState.kt` | Existing | thin wiring of the new presentation model into traffic UI state | current file already passes the status model into the runtime UI state | not repository/viewmodel because this is display-only; keep this change thin because the file is already 316 lines | No |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModelTest.kt` | Existing | locks mapping rules | existing focused unit-test owner | not instrumentation because no runtime dependency is needed | No |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorsUiTest.kt` | Existing | locks rendering mode and copy presence/absence | existing focused UI test owner | not broader map tests because the host is isolated | No |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt` | Existing, conditional | thin consumer of traffic-owned reserved top-right height for `Sharing Live` stacking | map shell already composes traffic and `Sharing Live` hosts together | not repository/viewmodel because this is layout-only and must not own traffic sizing constants | Only if card height exceeds current stack slot |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| extended traffic presentation model (dot vs lost-card) | traffic UI model owner | traffic UI host and tests | `internal` | keep rendering decisions out of the composable | evolve existing internal model; no public API |
| traffic-owned reserved-height seam for following indicators | traffic/map UI seam | `feature:map` shell + LiveFollow host | narrow existing UI seam only | prevent `Sharing Live` overlap if card height grows beyond the current dot step | evolve `followingIndicatorTopOffset()` or replace it in the same seam; do not duplicate constants in `feature:map` |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Lost-card show/hide state | None new | derived directly from current snapshots; no timer is required in the first implementation |

No new dwell timer should be introduced in the first implementation. If later UX wants dismissal delay or pulse behavior, that must stay display-only and use frame/monotonic time.

### 2.4 Replay Determinism

- Deterministic for same input:
  Yes.
- Randomness used:
  No.
- Replay/live divergence rules:
  None. The lost-card presentation is a pure projection of existing snapshot state.

### 2.5 Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| OGN overlay disabled | User Action | traffic UI derivation | no card | none | unit tests |
| OGN connected | Healthy | OGN snapshot owner | compact healthy state only; no lost card | none | unit tests |
| OGN connecting/disconnected startup states | Unavailable / startup | OGN snapshot owner | no lost card in Phase 1 | existing repository connect flow unchanged | unit tests |
| OGN `ERROR` | Degraded | OGN snapshot owner | show `OGN connection lost` micro-card | existing reconnect policy unchanged | unit tests + UI test |
| ADS-B disabled | User Action | ADS-B snapshot owner | no card | none | unit tests |
| ADS-B active | Healthy | ADS-B snapshot owner | compact healthy state only; no lost card | none | unit tests |
| ADS-B `Error` or network-loss-backed `BackingOff` caused by transport loss/offline | Degraded | ADS-B snapshot owner | show `ADS-B signal lost` micro-card | existing retry policy unchanged | unit tests + UI test |
| ADS-B auth failure / quota / non-signal degraded states | Degraded but not signal-loss | ADS-B snapshot owner | do not label as `signal lost` | keep existing degraded handling | unit tests |

Critical correction:

- `signal lost` must not become a catch-all label for `AuthFailed`, quota exhaustion, or generic non-network ADS-B degradation.
- Phase 0 freezes `OGN connection lost` because the current OGN snapshot models
  connection health, not signal-loss subtypes.
- Phase 0 also freezes `ADS-B signal lost` to a conservative transport-loss
  subset only:
  offline wait / `ADSB_ERROR_OFFLINE`, `networkOnline == false`, or
  `lastNetworkFailureKind` that indicates unreachable path loss
  (`DNS`, `TIMEOUT`, `CONNECT`, `NO_ROUTE`).
- TLS failures, malformed provider responses, quota/rate limiting, and
  credential/auth failures must stay out of the first `signal lost` contract
  unless product intent is explicitly widened in a later plan revision.
- If the current snapshot data is insufficient to distinguish network-loss backoff from non-network backoff, Phase 1 must resolve that in the pure presentation model before any copy is rendered.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Duplicate connection truth in UI | `ARCHITECTURE.md` authoritative state contract | unit test + review | `MapTrafficConnectionIndicatorModelTest` |
| Business/policy leakage into composables | `ARCHITECTURE.md` responsibility matrix | unit test + review | model file + renderer test |
| False-positive `signal lost` wording for auth/quota failures | `CODEBASE_CONTEXT_AND_INTENT.md` honest outputs over fabricated precision | unit tests | `MapTrafficConnectionIndicatorModelTest` |
| Top-right overlap with `Sharing Live` | `CONTRIBUTING.md` + map visual contract | review + manual validation + conditional map compile proof | map shell callsite and renderer tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| lost-state card appears without colliding with `Sharing Live` | `MS-UX-04` | current top-right stack already stable for dot-only traffic states | no overlap and no z-order flicker during traffic health transitions | manual map validation plus targeted UI assertions | Phase 2 |
| micro-card rendering does not widen top-right recomposition churn materially | `MS-ENG-09` | current top-right status rendering is small and stable | no unnecessary additional recomposition drivers beyond snapshot changes | review-backed measurement if needed | Phase 3 |

## 3) Data Flow (Before -> After)

Before:

`OgnTrafficRepository / AdsbTrafficRepository -> MapTrafficUiBinding -> MapTrafficConnectionIndicatorModelBuilder -> icon-only top-right traffic status host`

After:

`OgnTrafficRepository / AdsbTrafficRepository -> MapTrafficUiBinding -> extended traffic presentation model -> top-right traffic host renders healthy compact state or explicit lost-state micro-card`

No new repository state is added.

## 4) Implementation Phases

### Phase 0: Wording and Lost-State Rule Lock

- Goal:
  Lock exact copy and exact state-to-copy contract from the current snapshot
  fields before UI changes start.
- Files to change:
  this plan only.
- Ownership/file split changes in this phase:
  none; this phase only freezes wording authority and exclusion rules.
- Narrow seam rule for this phase:
  - do not derive final copy from `persistentAdsbStatusPresentation(...)`
  - do not derive final copy from `debugReasonLabel()` or other debug labels
  - keep OGN wording anchored to `OgnTrafficSnapshot.connectionState` only
  - keep ADS-B wording anchored to conservative snapshot evidence for
    transport loss only
- Tests to add/update:
  none.
- Exit criteria:
  - copy is frozen as `ADS-B signal lost` and `OGN connection lost`
  - OGN `signal lost` wording is explicitly rejected for the first
    implementation because the current OGN snapshot does not model signal-loss
    subtypes
  - ADS-B non-signal degraded states are explicitly excluded from the
    `signal lost` label unless the mapping owner proves they are transport-loss
    states
  - the plan names which existing strings are reference-only and not wording
    authorities

### Phase 1: Pure Presentation Model Extension

- Goal:
  Extend the existing pure traffic presentation model so it can express healthy compact state vs lost-state micro-card state without adding new authorities.
- Files to change:
  - `MapTrafficConnectionIndicatorModel.kt`
  - `MapTrafficContentUiState.kt`
  - `MapTrafficConnectionIndicatorModelTest.kt`
- Ownership/file split changes in this phase:
  none; existing pure model owner stays the owner, and `MapTrafficContentUiState.kt`
  remains wiring-only.
- Narrow seam rule for this phase:
  - do not modify `MapTrafficPanelsAndSheets.kt` in Phase 1
  - do not route the new `signal lost` wording through `shouldSurfacePersistentAdsbStatus(...)`
    in `MapTrafficContentUiState.kt`
  - do not route the new wording through `MapTrafficDebugPanelsSupport.kt` labels directly
- Tests to add/update:
  - OGN `ERROR` -> `OGN connection lost`
  - OGN `CONNECTED` -> no lost card
  - ADS-B offline/network-error -> `ADS-B signal lost`
  - ADS-B auth failure/quota/backoff-not-signal -> not `signal lost`
  - keep at least one regression test proving the builder still returns compact
    healthy-state output for connected OGN / active ADS-B
- Exit criteria:
  all copy/mapping rules are unit-tested with no Compose rendering required,
  and the phase remains confined to `feature:traffic`

### Phase 2: Top-Right Micro-Card Rendering

- Goal:
  Render a very small single-line card in the existing top-right traffic host for explicit lost states.
- Files to change:
  - `MapTrafficConnectionIndicators.kt`
  - `MapTrafficConnectionIndicatorsUiTest.kt`
  - `MapScreenContentRuntime.kt` only if the traffic-owned reserved-height seam changes
- Ownership/file split changes in this phase:
  none; renderer stays in the existing traffic host file unless it exceeds file-budget or responsibility bounds
- Narrow seam rule for this phase:
  - do not put micro-card sizing or spacing constants in `MapScreenContentRuntime.kt`
  - do not add traffic-specific layout logic to `MapLiveFollowRuntimeLayer.kt` or `LiveFollowPilotMapStatusHost.kt`
  - do not introduce runtime measurement state (`onGloballyPositioned`, layout callbacks, or remembered size state) for the first implementation; keep the stack reservation deterministic from the traffic host seam
  - encode the single-line contract in the renderer itself rather than relying only on manual review
- Tests to add/update:
  - UI test for copy presence when lost
  - UI test for copy absence in healthy state
  - existing accessibility assertions updated if needed
  - reserve-height regression updated if the lost-card path changes the current fixed-step offset seam
- Exit criteria:
  - `ADS-B signal lost` and `OGN connection lost` render as compact single-line cards
  - `Sharing Live` remains non-overlapping via the traffic-owned reserve seam, not duplicated map constants
  - no bottom-left large card is reintroduced

### Phase 3: Hardening and Visual Contract Check

- Goal:
  Verify the top-right stack remains stable under state transitions, lock manual
  validation guidance, and optionally bundle the requested OGN glider palette
  brightening.
- Files to change:
  focused tests or minor stack-step constant wiring if required; optionally
  `OgnTrafficOverlayConfig.kt` for the brighter OGN glider `ABOVE` / `BELOW`
  tint swap.
- Tests to add/update:
  conditional stack-step/offset tests if the card height changes the existing
  slot contract; manual map validation for brighter OGN glider green/blue
  contrast if the palette tweak is bundled.
- Exit criteria:
  - no overlap/flicker during healthy -> lost -> healthy transitions
  - map-shell compile/tests pass
  - if bundled, OGN glider `ABOVE` green and `BELOW` blue are visibly brighter
    while the relative-altitude band semantics remain unchanged
  - any impacted map visual evidence expectations are documented for merge-ready validation

## 5) Test Plan

- Unit tests:
  `MapTrafficConnectionIndicatorModelTest`
- Replay/regression tests:
  none new beyond normal compile/test confidence; no new replay logic is introduced
- UI/instrumentation tests:
  `MapTrafficConnectionIndicatorsUiTest`
- Degraded/failure-mode tests:
  explicit ADS-B network-loss vs auth/quota distinction, explicit OGN error mapping
- Boundary tests for removed bypasses:
  not required unless the implementation also unsurface existing large-card paths
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Presentation policy | unit tests | mapping tests in `MapTrafficConnectionIndicatorModelTest` |
| UI rendering | Compose/UI test | `MapTrafficConnectionIndicatorsUiTest` |
| Map-shell top-right layout | compile + targeted manual validation | `:feature:map:compileDebugKotlin` and screenshot/manual check if offset changes |

Required checks for the implementation change set:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Smallest useful pre-PR proof for this slice:

```bash
./gradlew :feature:traffic:testDebugUnitTest --tests "com.trust3.xcpro.map.ui.MapTrafficConnectionIndicatorModelTest" --tests "com.trust3.xcpro.map.ui.MapTrafficConnectionIndicatorsUiTest"
./gradlew :feature:map:compileDebugKotlin
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| `signal lost` card is shown for auth failure or quota issues | misleading UX | lock mapping rules in Phase 1 and test them explicitly | implementation owner |
| card height exceeds the current top-right stack slot and overlaps `Sharing Live` | broken map UI | keep card single-line and height-bounded, or widen the stack-offset contract in the same change | implementation owner |
| map shell duplicates traffic chip sizing constants during Phase 2 | brittle layout seam | keep all reserve-height math traffic-owned and let `feature:map` consume one narrow offset seam only | implementation owner |
| brighter OGN glider tints reduce contrast on some basemaps | reduced readability | validate brightened `ABOVE` green and `BELOW` blue on standard and satellite map styles before merge | implementation owner |
| renderer file becomes mixed-purpose or too large | review friction | split into a dedicated micro-card renderer file only if Phase 2 pushes the file past responsibility comfort | implementation owner |

## 6A) ADR / Durable Decision Record

- ADR required:
  No.
- Decision summary:
  This is a presentation-layer refinement that preserves existing state ownership and module boundaries.

## 7) Acceptance Gates

- No duplicate SSOT ownership introduced
- `signal lost` wording is reserved for true lost-connection states only
- No new timebase, replay, or DI changes
- Top-right micro-cards remain compact and non-overlapping with `Sharing Live`
- If bundled, OGN glider `ABOVE` / `BELOW` colors are brighter without changing band semantics
- Targeted unit/UI tests cover the mapping and rendering contract

## 8) Rollback Plan

- What can be reverted independently:
  the presentation-model extension and the micro-card renderer changes
- Recovery steps if regression is detected:
  restore the current compact traffic host behavior, remove the lost-card rendering path, and re-run the focused traffic tests
