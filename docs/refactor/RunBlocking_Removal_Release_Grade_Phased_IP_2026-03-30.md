# Production runBlocking removal: startup and variometer audio hardening

## 0) Metadata

- Title: Production runBlocking removal: startup and variometer audio hardening
- Owner: Codex
- Date: 2026-03-30
- Issue/PR: TBD
- Status: Completed (`runBlocking` removed from the targeted production paths; OGN test-lane blocker cleared; required local gates passed)

## 1) Scope

- Problem statement:
  - Production `runBlocking` still exists in two runtime-critical callsites:
    - `app/src/main/java/com/trust3/xcpro/XCProApplication.kt`
    - `feature/variometer/src/main/java/com/trust3/xcpro/audio/VarioBeepController.kt`
  - `XCProApplication` blocks process startup while resetting SCIA startup state.
  - `VarioBeepController` blocks the caller thread during stop and also creates a hidden child scope.
  - `VarioAudioEngine` still exposes a convenience default scope and then nests another internal scope before creating the beep controller.
- Why now:
  - This is a low-churn production improvement with direct runtime benefit.
  - It removes forbidden blocking behavior from startup and audio teardown paths.
  - It also creates a cleaner starting point before the separate `feature:traffic` test-lane stabilization slice.
- In scope:
  - remove production `runBlocking` from `XCProApplication`
  - remove production `runBlocking` from `VarioBeepController`
  - make variometer audio scope ownership explicit
  - preserve the product rule that SCIA state does not survive app restart
  - ensure no stale SCIA state is surfaced during cold start while the reset runs asynchronously
  - add focused regression tests and verify the existing `runBlocking` enforcement remains sufficient for this slice
- Out of scope:
  - `feature:traffic` unit-test lane stabilization
  - broader variometer pipeline redesign
  - replay behavior changes
  - SCIA feature redesign or settings UX changes
- User-visible impact:
  - lower startup-thread blocking risk
  - lower risk of audio stop stalls
  - same SCIA startup behavior from the user's perspective: cleared on fresh process start
- Rule class touched: Invariant + Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| persisted SCIA enabled flag | `OgnTrafficPreferencesRepository` | `showSciaEnabledFlow` | app/UI-local SCIA truth |
| persisted OGN target selection | `OgnTrafficPreferencesRepository` | `targetEnabledFlow`, `targetAircraftKeyFlow` | app/UI-local target reset state |
| persisted SCIA selected aircraft set | `OgnTrailSelectionPreferencesRepository` | `selectedAircraftKeysFlow` | app/UI-local trail selection truth |
| process-start SCIA reset bootstrap state | new `OgnSciaStartupResetCoordinator` | read-only bootstrap/reset-safe state seam | ad hoc booleans in `Application`, ViewModels, or UI |
| variometer audio runtime lifecycle | `VarioAudioEngine` | existing engine/controller lifecycle methods | hidden child scope in `VarioBeepController` |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `startupResetState` | `OgnSciaStartupResetCoordinator` | coordinator start/completion/failure path only | combined into SCIA/selection repo read seams and app startup assertions | one-shot process-start reset job | none | process restart or explicit test reset | n/a | startup coordinator tests, cold-start repo tests |
| reset-safe SCIA enabled read | `OgnTrafficPreferencesRepository` | repository flow mapping only | `showSciaEnabledFlow` consumers | persisted DataStore value + `startupResetState` | repository | bootstrap complete or bootstrap failure policy resolution | n/a | repo cold-start gating tests |
| reset-safe SCIA selected aircraft read | `OgnTrailSelectionPreferencesRepository` | repository flow mapping only | `selectedAircraftKeysFlow` consumers | persisted DataStore value + `startupResetState` | repository | bootstrap complete or bootstrap failure policy resolution | n/a | repo cold-start gating tests |
| beep loop lifecycle | `VarioAudioEngine` / `VarioBeepController` | engine start/stop and controller start/stop only | internal job ownership plus focused tests | caller-owned scope | none | stop/release | n/a | audio stop idempotency and non-blocking tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `app`
  - `feature:traffic`
  - `feature:variometer`
  - `scripts/ci`
- Boundary risk:
  - the startup reset coordinator must not live in `app` if `feature:traffic` repositories need to consume its state; that would reverse dependency direction.
  - the fix must not push SCIA startup policy into UI or ViewModels.
  - audio stop behavior must not introduce blocking teardown elsewhere to compensate for removing `runBlocking`.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/flight-runtime/src/main/java/com/trust3/xcpro/sensors/FlightStateRepository.kt` | explicit runtime owner with its own documented scope | keep long-lived scope ownership explicit inside a named runtime owner | this slice needs a one-shot startup coordinator in `feature:traffic`, not a continuous collector |
| `feature/map/src/main/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCase.kt` | runtime bridge with explicit owner scope and thin orchestration | keep orchestration thin and push policy into the right owner instead of UI | this slice has no replay/state-machine work; it only needs startup/audio ownership cleanup |
| `app/src/main/java/com/trust3/xcpro/service/VarioForegroundService.kt` | Android lifecycle host owning async startup work | let Android lifecycle host trigger async work without blocking the main thread | `Application` startup does not have a natural cancel-on-destroy path, so the one-shot coordinator contract must be explicit |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| SCIA startup reset execution trigger | `XCProApplication.onCreate` blocking callsite | `OgnSciaStartupResetCoordinator` triggered by `XCProApplication` | keep startup host thin and make reset state explicit | coordinator + application tests |
| reset-safe SCIA startup reads | implicit ordering from blocking startup | traffic repositories combining persisted value with startup reset state | preserve behavior without blocking app startup | repository gating tests |
| beep-loop scope ownership | hidden child scope in `VarioBeepController` | caller-owned audio scope via `VarioAudioEngine` | remove hidden scope owner | audio unit tests |
| beep-loop stop quiescence | caller-thread `runBlocking` join | non-blocking cancel + immediate tone stop + owner-scoped cleanup | remove thread blocking while preserving shutdown safety | audio stop tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `XCProApplication.onCreate` | `runBlocking { sciaStartupResetter.resetForFreshProcessStart() }` | injected startup coordinator `startIfNeeded()` | Phase 2 |
| `VarioBeepController.stopInternal` | `runBlocking { runningJob.join() }` on caller thread | non-blocking cancel path with immediate tone stop | Phase 1 |
| `VarioBeepController` constructor | private child scope created from caller scope | consume caller-owned scope directly | Phase 1 |
| `VarioAudioEngine` constructor | convenience default scope in production path | explicit scope ownership for production wiring; keep any convenience path narrow/internal | Phase 1 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/RunBlocking_Removal_Release_Grade_Phased_IP_2026-03-30.md` | new | execution plan and release gates | canonical plan owner for this slice | do not hide plan state in global docs | no |
| `app/src/main/java/com/trust3/xcpro/XCProApplication.kt` | existing | Android process-start trigger only | app lifecycle host | must not own reset policy or persisted state | no |
| `app/src/main/java/com/trust3/xcpro/SciaStartupResetter.kt` | existing | actual SCIA reset side effects against repositories | already the focused reset side-effect owner | do not move reset I/O into `Application` | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnSciaStartupResetCoordinator.kt` | new | one-shot startup reset state owner and async launch coordination | feature-owned so traffic repos can depend on it without reversing module boundaries | `app` cannot own a state source consumed by feature repos | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt` | existing | reset-safe SCIA and target-selection reads | canonical persisted OGN preference owner | UI must not gate these values ad hoc | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt` | existing | reset-safe selected-aircraft reads | canonical persisted trail-selection owner | UI must not gate these values ad hoc | no |
| `feature/variometer/src/main/java/com/trust3/xcpro/audio/VarioAudioEngine.kt` | existing | explicit audio runtime scope owner | already owns engine lifecycle | `VarioBeepController` should not own a second long-lived scope | no |
| `feature/variometer/src/main/java/com/trust3/xcpro/audio/VarioBeepController.kt` | existing | beep loop logic only | canonical beep-loop behavior owner | engine should not absorb beep timing logic | no |
| `app/src/test/java/com/trust3/xcpro/XCProApplicationTest.kt` | existing | startup trigger regression coverage | direct application host coverage | avoid proving startup only via integration tests | no |
| `app/src/test/java/com/trust3/xcpro/SciaStartupResetterTest.kt` | existing | reset side-effect coverage | direct reset owner coverage | no need to duplicate repository assertions elsewhere | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt` | existing | reset-safe startup read coverage for SCIA/target | canonical OGN preference test owner | keep DataStore read semantics close to repo | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepositoryTest.kt` | existing | reset-safe startup read coverage for selected aircraft | canonical trail-selection repo test owner | keep DataStore read semantics close to repo | no |
| `feature/variometer/src/test/java/com/trust3/xcpro/audio/VarioBeepControllerTest.kt` | new | non-blocking stop/idempotency coverage | smallest owner for beep-loop stop semantics | do not rely on instrumentation for pure coroutine behavior | no |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnSciaStartupResetCoordinator` (or narrow `StateSource` interface if preferred during implementation) | `feature:traffic` | `XCProApplication`, traffic preference repositories, tests | public within module graph | feature-owned startup reset state seam | stable narrow contract; keep consumer list explicit |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| `OgnSciaStartupResetCoordinator` one-shot scope | run startup reset asynchronously without blocking app startup | injected IO dispatcher | process lifetime or completion of one-shot work | `Application` should remain a thin trigger host, not a coroutine-work owner |
| `VarioAudioEngine` runtime scope | own audio loop lifecycle and hand it to the beep controller | existing caller-provided scope / audio owner scope | engine stop/release / owner cancellation | beep controller must not create its own long-lived child owner |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| SCIA startup reset-safe read policy | `feature/traffic/.../OgnSciaStartupResetCoordinator.kt` plus traffic repos | traffic repos, app startup tests | startup reset policy belongs with SCIA feature ownership, not `Application` or UI | no |
| audio stop non-blocking teardown policy | `feature/variometer/src/main/java/com/trust3/xcpro/audio/VarioBeepController.kt` | engine/tests | beep-loop stop behavior belongs in beep-loop owner | no |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| none planned | n/a | n/a | n/a | n/a | n/a |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| SCIA startup reset sequencing | n/a | one-shot startup ordering only; no clock math needed |
| audio stop teardown sequencing | n/a | cancellation-driven; no clock math should be required |
| any optional test-only wait helper | test dispatcher / monotonic test scheduler only | tests may need deterministic quiescence proof without production blocking |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - app startup reset work: `IO`
  - audio stop cancellation and loop work: existing audio owner scope / non-main runtime scope
- Primary cadence/gating sensor:
  - none; both paths are lifecycle/teardown driven, not cadence driven
- Hot-path latency budget:
  - app startup main-thread blocking from this slice = `0 ms`
  - audio stop must return promptly and silence output immediately without caller-thread blocking

### 2.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path (`AppLogger` / Platform Edge) | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| `XCProApplication` startup failure | platform edge or `AppLogger` if touched during implementation | low | single startup error only | none |
| variometer audio stop/start diagnostics | `AppLogger` | low | rate-limited where repeated | none |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added; this slice must not change replay behavior

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| startup reset still in progress | Unavailable | startup reset coordinator + repos | SCIA/selection reads stay reset-safe during bootstrap | complete async reset, then expose persisted state if policy allows | startup repo tests |
| startup reset failure | Degraded | startup reset coordinator | SCIA remains in safe cleared state for this process start; diagnostic logged | no automatic re-enable from stale persisted state in the same startup path | coordinator/application tests |
| audio stop requested while loop active | Recoverable | `VarioBeepController` | tone stops immediately; no visible stall | cancel loop and let owner scope finish cleanup asynchronously | beep controller tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| none added | n/a | n/a | n/a | this slice should not introduce new IDs or timestamps |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| `VarioAudioEngine` | convenience default scope constructor/path | only if narrowed to test/internal use | production must use caller-owned scope | keep narrow or remove |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| production `runBlocking` returns later | `CODING_RULES.md` coroutines rules | static guard + review | `scripts/ci/enforce_rules.ps1` |
| startup reset becomes async but stale SCIA flashes at cold start | SSOT + explicit degraded-state handling | unit tests | traffic repo tests + application test |
| beep stop returns but leaves audio running | runtime correctness | unit tests | `VarioBeepControllerTest.kt` |
| beep controller reintroduces hidden scope owner | scope ownership rules | review + test | `VarioAudioEngine.kt`, `VarioBeepController.kt` |

## 3) Data Flow (Before -> After)

Before:

`XCProApplication.onCreate -> runBlocking -> SciaStartupResetter -> OGN preference repositories`

`VarioAudioController -> VarioAudioEngine(default scope + internal scope) -> VarioBeepController(internal scope) -> runBlocking stop join`

After:

`XCProApplication -> OgnSciaStartupResetCoordinator.startIfNeeded() -> SciaStartupResetter async reset`

`OgnTrafficPreferencesRepository/OgnTrailSelectionPreferencesRepository -> reset-safe reads until startup reset completes`

`VarioAudioController -> VarioAudioEngine(explicit owner scope) -> VarioBeepController(no child scope, no caller-thread blocking stop)`

## 4) Implementation Phases

### Phase 0 - Baseline and acceptance lock

- Goal:
  - lock the exact behavior this slice must preserve before code changes begin
- Files to change:
  - plan doc only
- Ownership/file split changes in this phase:
  - choose `feature:traffic` as the owner for startup reset bootstrap state
- Tests to add/update:
  - none yet
- Exit criteria:
  - startup reset host/owner split is explicit
  - audio scope owner is explicit
  - acceptance statement is locked:
    - no production `runBlocking`
    - no stale SCIA on cold start
    - no audio stop stall

### Phase 1 - Variometer audio hardening

- Goal:
  - remove blocking audio stop and hidden beep-controller scope ownership
- Files to change:
  - `feature/variometer/src/main/java/com/trust3/xcpro/audio/VarioAudioEngine.kt`
  - `feature/variometer/src/main/java/com/trust3/xcpro/audio/VarioBeepController.kt`
  - `feature/variometer/src/test/java/com/trust3/xcpro/audio/VarioBeepControllerTest.kt`
- Ownership/file split changes in this phase:
  - `VarioAudioEngine` becomes the explicit audio runtime scope owner
  - `VarioBeepController` stops creating a long-lived child scope
- Tests to add/update:
  - stop is idempotent
  - stop cancels active loop without caller-thread blocking
  - tone stops immediately on stop
- Exit criteria:
  - no production `runBlocking` in audio path
  - no hidden child scope in `VarioBeepController`
  - existing controller behavior remains otherwise unchanged
- Current status:
  - implemented
  - focused verification passed:
    - `./gradlew :feature:variometer:testDebugUnitTest --tests "com.trust3.xcpro.audio.VarioBeepControllerTest"`
    - `./gradlew :feature:variometer:testDebugUnitTest`

### Phase 2 - SCIA startup hardening

- Goal:
  - remove startup blocking while preserving reset-safe SCIA behavior from first observable app state
- Files to change:
  - `app/src/main/java/com/trust3/xcpro/XCProApplication.kt`
  - `app/src/main/java/com/trust3/xcpro/SciaStartupResetter.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnSciaStartupResetCoordinator.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
  - `app/src/test/java/com/trust3/xcpro/XCProApplicationTest.kt`
  - `app/src/test/java/com/trust3/xcpro/SciaStartupResetterTest.kt`
  - `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
  - `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepositoryTest.kt`
- Ownership/file split changes in this phase:
  - `Application` remains only the startup trigger host
  - `feature:traffic` owns bootstrap state and reset-safe read policy
- Tests to add/update:
  - cold-start SCIA reads stay false/empty during bootstrap
  - post-reset reads remain cleared
  - startup failure path stays safe and does not crash app startup
- Exit criteria:
  - no production `runBlocking` in app startup path
  - no stale SCIA or selected-aircraft state visible during cold start
  - reset failure keeps safe startup state and reports diagnostics
- Current status:
  - implemented
  - focused verification passed:
    - `./gradlew :feature:traffic:testDebugUnitTest --tests "com.trust3.xcpro.ogn.OgnTrafficPreferencesRepositoryTest" --tests "com.trust3.xcpro.ogn.OgnTrailSelectionPreferencesRepositoryTest"`
    - `./gradlew :app:testDebugUnitTest --tests "com.trust3.xcpro.AppOgnSciaStartupResetCoordinatorTest" --tests "com.trust3.xcpro.XCProApplicationTest" --tests "com.trust3.xcpro.SciaStartupResetterTest"`
    - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenViewModelOverlayPreferenceTest"`

### Phase 3 - Enforcement and merge proof

- Goal:
  - make the fix durable and repo-grade
- Files to change:
  - none required if the existing `runBlocking` rule and repo gates remain sufficient
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none beyond the focused regressions unless enforcement exposes a gap
- Exit criteria:
  - production `runBlocking` is blocked by local enforcement
  - required checks pass
  - doc sync is complete
- Current status:
  - completed
  - no `runBlocking` remains in `app/src/main`, `feature/variometer/src/main`, or `feature/traffic/src/main`
  - the OGN traffic test harness was stabilized with narrow teardown and mock-retention fixes
  - required local checks passed:
    - `./gradlew enforceRules`
    - `./gradlew testDebugUnitTest`
    - `./gradlew assembleDebug`

## 5) Test Plan

- Unit tests:
  - `VarioBeepControllerTest`
  - `XCProApplicationTest`
  - `SciaStartupResetterTest`
  - `OgnTrafficPreferencesRepositoryTest`
  - `OgnTrailSelectionPreferencesRepositoryTest`
- Replay/regression tests:
  - none new; replay behavior must remain unchanged
- UI/instrumentation tests (if needed):
  - not required initially; add only if a cold-start UI race remains ambiguous after repository tests
- Degraded/failure-mode tests:
  - startup reset failure remains safe
  - audio stop while active loop is running remains safe
- Boundary tests for removed bypasses:
  - explicit search/enforcement proving no production `runBlocking`
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | startup reset-safe read tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | n/a; no time-base logic added |
| Persistence / settings / restore | round-trip / restore / migration tests | repo startup reset-safe read tests |
| Ownership move / bypass removal / API boundary | boundary lock tests | coordinator + app trigger tests, `runBlocking` static guard |
| UI interaction / lifecycle | UI or instrumentation coverage | only if startup cold-start ambiguity remains |
| Performance-sensitive path | benchmark, metric, or SLO artifact | n/a |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| async startup reset allows stale SCIA state to surface briefly | user-visible regression | keep reset-safe read policy inside traffic repos until bootstrap completes | Codex |
| removing blocking join changes audio teardown ordering | latent audio regression | stop tone immediately, cancel loop, add focused stop-path tests | Codex |
| startup coordinator becomes a hidden mutable singleton | architecture drift | keep owner explicit, narrow, feature-owned, and one-shot only | Codex |
| new enforcement rule catches unrelated legacy `runBlocking` | merge friction | scope the initial guard to production `src/main` and fix only true production callsites in this slice | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: n/a
- Decision summary:
  - the slice stays within existing module boundaries; it adds a narrow feature-owned startup reset coordinator and tightens existing runtime ownership
- Why this belongs in an ADR instead of plan notes:
  - not required unless implementation expands the startup coordinator into a broader reusable app-bootstrap contract

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SCIA or audio state owner introduced
- No production `runBlocking` remains in `src/main`
- App startup no longer blocks the main thread for this SCIA reset
- SCIA remains reset-safe during cold start
- Variometer audio stop no longer blocks the caller thread
- Replay behavior remains unchanged
- `KNOWN_DEVIATIONS.md` unchanged

## 8) Rollback Plan

- What can be reverted independently:
  - audio stop/scope cleanup
  - startup reset coordinator + reset-safe repo gating
  - production `runBlocking` static guard
- Recovery steps if regression is detected:
  - revert the affected phase only
  - keep focused tests
  - rerun `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug`
