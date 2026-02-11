# ADSB_Adaptive_Polling_Credit_Budget_Change_Plan.md

## 0) Metadata

- Title: ADS-B Adaptive Polling + Credit Budget Guardrails
- Owner: XCPro Team
- Date: 2026-02-11
- Issue/PR: TBD
- Status: In progress

### Implementation Update (2026-02-11)

- Completed:
  - Adaptive polling interval selection in `AdsbTrafficRepository` (traffic + movement + empty-streak aware).
  - Credit-budget floor logic with fallback request-rate accounting when credit headers are missing.
  - Emergency icon behavior: if target is `<1 km` and inbound on collision trajectory, icon switches to red emergency variant.
  - Unit tests for adaptive polling, budget floors, emergency risk classification, and emergency icon mapping.
- Pending:
  - Optional debug/snapshot observability extensions.
  - ADS-B docs refresh in `docs/ADS-b/ADSB.md`.

## 1) Scope

- Problem statement:
  - ADS-B polling currently uses a fixed 10s interval, even when no nearby traffic exists.
  - This can consume OpenSky credits quickly and increase `429` rate-limit events.
- Why now:
  - OpenSky API credit limits are finite and area-based for `/states/all`.
  - Nearby-only traffic behavior is best served by dynamic cadence.
- In scope:
  - Add adaptive polling cadence in ADS-B repository loop.
  - Add credit-budget-aware minimum interval logic.
  - Add emergency collision-risk icon highlighting (`<1 km` + inbound trajectory => red icon).
  - Keep existing `429` retry behavior and auth retry behavior.
  - Add unit tests for policy and repository loop behavior.
  - Add compliance checklist and release gate.
- Out of scope:
  - Provider migration (Firehose/ADSBx/custom backend).
  - UI redesign.
  - Replay pipeline behavior changes.
- User-visible impact:
  - Same ADS-B overlay behavior near user.
  - Lower update frequency when scene is quiet/stable.
  - Fewer disconnect/backoff oscillations under quota pressure.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B live targets | `AdsbTrafficRepositoryImpl` | `StateFlow<List<AdsbTrafficUiModel>>` | UI/runtime caches as authority |
| ADS-B connection/debug snapshot | `AdsbTrafficRepositoryImpl` | `StateFlow<AdsbTrafficSnapshot>` | ViewModel-local mirror state |
| ADS-B overlay enabled preference | `AdsbTrafficPreferencesRepository` | `Flow<Boolean>` | ViewModel-persisted bool |
| ADS-B icon size preference | `AdsbTrafficPreferencesRepository` | `Flow<Int>` | Runtime-only authoritative size |
| Adaptive poll policy state | `AdsbTrafficRepositoryImpl` (private) | Included in snapshot/debug fields | UI-owned cadence policy |
| Emergency collision-risk classification | `AdsbTrafficStore` / repository domain path | `AdsbTrafficUiModel.isEmergencyCollisionRisk` | UI-side collision policy logic |

### 2.2 Dependency Direction

Flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/OpenSkyProviderClient.kt` (optional: header parsing hardening only)
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt` (optional debug panel fields only)
  - tests under `feature/map/src/test/.../adsb/`
- Boundary risk:
  - Keep policy decisions in repository/domain path only.
  - No business policy in Composables or ViewModel.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Poll interval selection | Fixed constant in repository loop | Repository policy function/object | Dynamic behavior without UI leakage | Unit tests for interval decisions |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `delay(POLL_INTERVAL_MS)` in ADS-B loop | Fixed delay ignores context/credits | `delay(computeNextPollDelayMs(...))` | Phase 2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Poll loop timestamps (`lastPollMonoMs`, `lastSuccessMonoMs`) | Monotonic | Stable delta calculations |
| Backoff and retry delays | Monotonic | No wall-time drift coupling |
| Credit-budget window counters | Monotonic | Deterministic testability with injected `Clock` |

Explicitly forbidden:
- Monotonic vs wall comparisons
- Replay vs wall comparisons

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Network + repository loop: injected IO dispatcher (existing)
  - Pure policy math: same coroutine context (no Main work)
- Primary cadence source:
  - Repository-controlled adaptive interval.
- Hot-path latency budget:
  - Preserve current fetch/map update path; no added heavy work in UI.

### 2.5 Replay Determinism

- Deterministic for same replay input: Yes (unchanged)
- Randomness used: optional jitter remains deterministic under fake/injected clock.
- Replay/live divergence rules:
  - ADS-B remains a live internet source; replay path unchanged.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Business logic moved into UI | ARCHITECTURE/CODING_RULES UI constraints | review + unit tests | `AdsbTrafficRepository*Test` |
| New direct wall-time usage | Time base rules | `enforceRules` + tests | `AdsbTrafficRepository.kt` |
| Duplicate authority for cadence state | SSOT rules | review | repository-private state only |
| Hidden mutable singleton | DI rules | review + `enforceRules` | no new singleton objects with mutable global state |

## 3) Data Flow (Before -> After)

Before:

`OpenSky /states/all -> OpenSkyProviderClient -> AdsbTrafficRepository (fixed 10s) -> store select -> AdsbTrafficUseCase -> MapScreenViewModel -> MapOverlayManager -> AdsbTrafficOverlay`

After:

`OpenSky /states/all -> OpenSkyProviderClient -> AdsbTrafficRepository (adaptive interval + credit floor + existing 429 retry) -> store select -> AdsbTrafficUseCase -> MapScreenViewModel -> MapOverlayManager -> AdsbTrafficOverlay`

## 4) Implementation Phases

### Phase 1: Baseline policy scaffolding [Completed]

- Goal:
  - Introduce a private policy function and state variables without behavior change (still returns 10s).
- Files:
  - `AdsbTrafficRepository.kt`
  - `AdsbTrafficModels.kt` (optional debug fields)
- Tests:
  - New unit tests for policy defaults.
- Exit criteria:
  - Build/test unchanged behavior with fixed interval parity.

### Phase 2: Adaptive interval policy [Completed]

- Goal:
  - Select interval by runtime context.
- Inputs:
  - `withinRadiusCount`
  - recent center movement distance
  - consecutive empty polls
  - selected cap/staleness signals from existing store outputs
- Proposed intervals (initial conservative values):
  - HOT: 10s (nearby traffic or movement)
  - WARM: 20s
  - COLD: 40s
  - QUIET: 60s
- Files:
  - `AdsbTrafficRepository.kt`
  - tests
- Exit criteria:
  - Unit tests prove expected interval transitions.

### Phase 3: Credit budget floor [Completed]

- Goal:
  - Prevent avoidable quota exhaustion by enforcing a minimum interval when credits are low.
- Inputs:
  - `remainingCredits` response header when present
  - local request counter fallback when header is unavailable
  - estimated request cost from bbox area tier (square degrees)
- Policy:
  - `nextDelay = max(adaptiveDelay, budgetFloorDelay)`
  - Keep strict override for server-provided `retryAfter` on `429`.
- Files:
  - `AdsbTrafficRepository.kt`
  - optional `OpenSkyProviderClient.kt` header parse hardening
  - tests
- Exit criteria:
  - Budget tests pass and do not suppress `429` retry semantics.

### Phase 3A: Emergency collision-risk icon path [Completed]

- Goal:
  - Mark aircraft as emergency risk when very close and inbound, and render with red icon.
- Inputs:
  - Distance to user (`<1,000 m`)
  - Heading alignment toward user (target track within tolerance of bearing-to-user)
- Files:
  - `AdsbTrafficModels.kt`
  - `AdsbTrafficStore.kt`
  - `AdsbGeoJsonMapper.kt`
  - `AdsbTrafficOverlay.kt`
  - `AdsbAircraftIcon.kt`
  - ADS-B tests for store/mapper/icon IDs
- Exit criteria:
  - Emergency-risk targets show red icon variant on map.

### Phase 4: Snapshot/debug observability [Pending]

- Goal:
  - Expose policy reason and selected interval for diagnostics.
- Files:
  - `AdsbTrafficModels.kt`
  - `AdsbTrafficRepository.kt`
  - optional `MapScreenContent.kt` debug panel
- Exit criteria:
  - Debug output shows poll profile and next delay.

### Phase 5: Docs and rollout guardrails [Pending]

- Goal:
  - Document operational and compliance behavior.
- Files:
  - `docs/ADS-b/ADSB.md` (add adaptive policy summary)
  - `docs/ARCHITECTURE/PIPELINE.md` only if wiring changes (not expected)
- Exit criteria:
  - Docs updated, no architecture drift.

## 5) Test Plan

- Unit tests:
  - Policy selection by traffic/movement/empty streak.
  - Budget floor behavior by credit thresholds.
  - `429` path still honors server retry delay.
  - 401 refresh path unchanged.
- Replay/regression tests:
  - Replay determinism unaffected (existing replay tests remain green).
- UI/instrumentation tests:
  - Optional: debug panel labels if new snapshot fields are surfaced.
- Degraded/failure-mode tests:
  - Missing headers.
  - Network errors with exponential backoff.
  - Empty responses over long durations.
- Boundary tests for removed bypasses:
  - Assert fixed `POLL_INTERVAL_MS` is no longer sole delay determinant.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Polling becomes too slow and misses nearby traffic onset | Delayed symbol appearance | Keep HOT at 10s and trigger HOT on center movement/nearby sightings | XCPro Team |
| OpenSky headers unavailable/inaccurate in API-client flow | Budget logic uncertainty | Fallback local request accounting + conservative floors | XCPro Team |
| Hidden architecture drift into ViewModel/UI | Rule violations | Keep all policy in repository; add tests and review checklist | XCPro Team |
| Compliance mismatch for commercial use | Legal/operational risk | Release gate requiring written OpenSky permission for for-profit use | Product + Legal |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling explicit in code/tests
- Replay behavior unchanged and deterministic
- `KNOWN_DEVIATIONS.md` unchanged unless explicit approved exception
- OpenSky compliance gates satisfied before release:
  - Confirm allowed usage category (non-profit/government vs for-profit).
  - If for-profit/commercial, obtain written OpenSky permission before shipping.
  - Ensure OpenSky attribution/citation obligations are met in app/docs/release notes.
  - Do not use multiple accounts to bypass API restrictions.

## 8) Rollback Plan

- Revert independently:
  - Adaptive interval function
  - Budget floor function
  - Snapshot/debug additions
- Recovery steps:
  - Restore fixed 10s polling constant path.
  - Keep existing 429/401 handling as baseline.
  - Re-run required checks and verify map overlay updates.

## 9) External Compliance Notes (Verified 2026-02-11)

- OpenSky REST:
  - `/states/all` is rate-limited and credit-based.
  - API credits apply to all endpoints except `/states/own`.
  - `/states/all` credits are based on queried square-degree area tier.
  - `429` responses provide retry guidance header.
  - OpenSky notes rate-limit headers may not work correctly for API client flow; local fallback accounting is required.
  - OAuth2 client-credentials flow is required for newer accounts and recommended for programmatic access.
- OpenSky licensing/terms:
  - Terms frame default data use around non-profit research/education/government.
  - For-profit/commercial use requires written permission.
  - Attribution/citation obligations apply when publishing/disclosing OpenSky-derived outputs.

Primary sources:
- https://openskynetwork.github.io/opensky-api/rest.html
- https://opensky-network.org/about/terms-of-use
- https://opensky-network.org/about/faq
