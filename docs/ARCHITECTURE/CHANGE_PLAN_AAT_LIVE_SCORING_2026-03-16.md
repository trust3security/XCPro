# CHANGE_PLAN_AAT_LIVE_SCORING_2026-03-16

## Purpose

Use this plan before implementing AAT live scoring in XCPro.

Goal: add organizer-configured real-time AAT competition scoring without
violating the existing task SSOT or hiding scoring state inside task editor UI.

Read first:

1. `ARCHITECTURE.md`
2. `CODING_RULES.md`
3. `PIPELINE.md`
4. `CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/AAT-Scoring/06_REPO_MODULE_AND_SEAMS.md`

## 0) Metadata

- Title: AAT Live Scoring Boundary Split and Phased Delivery
- Owner: XCPro Team
- Date: 2026-03-16
- Issue/PR: ARCH-20260316-AAT-LIVE-SCORING
- Status: Draft

## 1) Scope

- Problem statement:
  - The repo has strong task declaration/editing ownership but no production
    owner for competition scoring config, per-pilot live results, leaderboard
    rows, or official reconciliation.
  - Existing AAT validation/scoring-like helpers are not scoring-grade
    authority.
- Why now:
  - `docs/AAT-Scoring` requires organizer-facing live scoring, and the current
    repo shape would drift badly if that work were added into `feature:tasks`.
- In scope:
  - new competition domain module
  - draft config ownership
  - published competition-day freeze
  - roster and identity resolution
  - live runtime repository
  - organizer setup UI and leaderboard UI
  - accepted-track reconciliation seam
- Out of scope:
  - replacing task declaration/editor architecture
  - changing replay/variometer ownership
  - non-AAT competition formats
- User-visible impact:
  - organizers get explicit setup and live leaderboard flows without changing
    the pilot task editor
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active AAT task definition | `TaskManagerCoordinator.taskSnapshotFlow` | read-only task snapshot for setup and publish only | `TaskUiState`, leaderboard state, setup UI copies as authority |
| Draft organizer scoring config | `AatLiveScoringSetupViewModel` | UI state only | hidden mutable config in Composables or repositories before save |
| Published competition day | `AatPublishedCompetitionDayRepository` in `feature:map-runtime` | `StateFlow<AatPublishedCompetitionDay?>` | direct runtime dependence on mutable task sheet or draft config |
| Pilot roster snapshot | `AatPilotRosterRepository` in `feature:map-runtime` | `StateFlow<AatRosterSnapshot?>` or publish-time query | ad hoc pilot creation from traffic feed labels |
| Live competition runtime state | `AatCompetitionRuntimeRepository` in `feature:map-runtime` | `StateFlow<AatCompetitionState>` | per-screen mutable leaderboard copies |
| Accepted official tracks | narrow read seam in `feature:igc` | query interface | direct file scanning from UI/runtime |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Active task definition | `TaskManagerCoordinator` | task coordinator mutations only | `taskSnapshotFlow` | task engine state | existing task persistence | task clear/load/switch | n/a | coordinator snapshot tests |
| Draft setup form | `AatLiveScoringSetupViewModel` | organizer intents | ViewModel state | published config + local edits | none | screen close or explicit discard | wall for timestamps only if shown | ViewModel tests |
| Published competition day | `AatPublishedCompetitionDayRepository` | publish/clear APIs only | repository flow | draft config + frozen task snapshot + frozen roster snapshot | map-runtime published-day store | explicit clear/reset or republish | wall for publish metadata; task/fix times remain source UTC | round-trip and stale-publish tests |
| Pilot roster snapshot | `AatPilotRosterRepository` | roster refresh and publish-time freeze only | repository flow or query | organizer roster input / external class roster source | map-runtime roster store if persisted | explicit refresh/reset or class/day change | n/a | roster round-trip and identity-resolution tests |
| Live competition state | `AatCompetitionRuntimeRepository` | fix ingestion, closure, reconcile APIs | repository flow | published competition day + resolved pilot fixes + accepted tracks | optional snapshot later, not V1 | clear, republish, host teardown | source fix UTC timestamps only | deterministic runtime tests |
| Official results | `AatCompetitionRuntimeRepository` after reconcile | reconcile API only | repository flow | accepted tracks + published competition day | optional export layer later | new reconciliation, task day reset | source log UTC timestamps | reconciliation tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - new `feature:competition`
  - `feature:map-runtime`
  - `feature:map`
  - `feature:igc` read seam in later phase
- Any boundary risk:
  - high if scoring is added to `feature:tasks`
  - high if `feature:competition` depends directly on `feature:tasks` or
    `feature:igc`

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md` | existing cross-feature SSOT decision | one authoritative runtime read seam | add a second authority for competition state, not task state |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapTasksUseCase.kt` | higher-level composition over task state | use `feature:map-runtime` as composition layer | compose scoring domain and accepted-track adapters there |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileStorage.kt` | DataStore-backed JSON snapshot with recoverable read status | keep draft/published-day storage behind a repository snapshot flow that survives read errors | competition storage will persist scoring-day JSON rather than profile JSON |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| organizer scoring config | implicit/task rules UI | `AatCompetitionDraftStore` + setup ViewModel | make scoring policy explicit | config round-trip tests |
| published competition day freeze | no real owner | `AatPublishedCompetitionDayRepository` | prevent mutable task edits from silently rewriting scoring | publish and stale-task tests |
| pilot roster identity for scoring | implicit tracker labels / future ad hoc mapping | `AatPilotRosterRepository` + `AatPilotIdentityResolver` | keep `pilotId` authoritative and stable | identity resolution tests |
| live leaderboard state | no real owner | `AatCompetitionRuntimeRepository` | one runtime authority | deterministic runtime tests |
| scoring formula ownership | partial helpers in `feature:tasks` | `feature:competition` engines | stop mixing edit-time and scoring-time logic | scorer unit tests |
| official reconciliation | no real owner | `AatOfficialResultReconciler` plus accepted-track adapter | unify live and official result paths | reconcile tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `TaskUiState` / `TaskRepository.state` | task UI projection treated as broader runtime | `AatCompetitionRuntimeRepository.state` for scoring UI | Phase 4-5 |
| mutable `taskSnapshotFlow` after publish | live runtime would follow edited task implicitly | `AatPublishedCompetitionDay` freeze at publish | Phase 2 |
| `AATValidationBridge` | lossy task normalization for competition checks | `TaskCoordinatorAatTaskDefinitionAdapter` | Phase 2 |
| `AATTaskCalculator.calculateFlightResult` | placeholder single-pilot scoring | `AatSinglePilotScorer` | Phase 3 |
| raw OGN `competitionNumber` / `canonicalKey` as pilot identity | transport identity treated as scoring identity | `AatPilotIdentityResolver` + roster snapshot | Phase 2-4 |
| direct UI access to IGC internals | ad hoc future risk | narrow accepted-track read seam | Phase 6 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/competition/.../config/AatCompetitionConfig.kt` | New | scoring config model | pure domain contract | must not live in UI or task module | No |
| `feature/competition/.../model/AatPublishedCompetitionDay.kt` | New | immutable published scoring day bundle | pure domain contract | runtime must not invent its own frozen snapshot shape | No |
| `feature/competition/.../roster/AatRosterSnapshot.kt` | New | scored pilot roster contract | pure domain identity owner | traffic feed must not define scoring roster | No |
| `feature/competition/.../engine/AatSinglePilotScorer.kt` | New | canonical AAT scoring formula owner | pure domain math | not UI, not map-runtime adapter | No |
| `feature/competition/.../engine/AatLeaderboardRanker.kt` | New | row visibility and ordering | pure domain policy | avoid ranking logic in UI | No |
| `feature/map-runtime/.../data/AatPublishedCompetitionDayRepository.kt` | New | published day SSOT and persistence | publish/freeze belongs in composition layer | competition module must stay storage-free | No |
| `feature/map-runtime/.../roster/AatPilotRosterRepository.kt` | New | roster load/freeze owner | map-runtime composes external roster inputs | task and traffic modules are wrong owners | No |
| `feature/map-runtime/.../roster/AatPilotIdentityResolver.kt` | New | map tracker/log identity to scored pilot IDs | lives beside adapters using transport models | competition module must not know OGN/IGC identities | No |
| `feature/map-runtime/.../runtime/AatCompetitionRuntimeRepository.kt` | New | live scoring state authority | composes tasks + fixes + config | `feature:competition` must stay pure | No |
| `feature/map-runtime/.../adapter/TaskCoordinatorAatTaskDefinitionAdapter.kt` | New | task snapshot adapter | depends on `feature:tasks` | competition module must not depend on tasks | No |
| `feature/map-runtime/.../adapter/OgnAatPilotFixAdapter.kt` | New | accepted live-fix adapter with quality flags | depends on OGN transport models | runtime must not ingest raw OGN targets | No |
| `feature/map-runtime/.../adapter/IgcAcceptedTrackAdapter.kt` | New | accepted-track adapter | depends on `feature:igc` | competition module must not depend on IGC | No |
| `feature/map/.../competition/aat/AatLiveScoringSetupViewModel.kt` | New | setup screen state | UI orchestration owner | config repository should not own form draft | No |
| `feature/map/.../ui/competition/aat/AatLiveLeaderboardScreen.kt` | New | leaderboard rendering | UI only | scoring math must stay out | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `:feature:competition` module | new module | map-runtime, map tests | public module | isolate scoring domain | durable |
| `AatTaskDefinitionPort` | `feature:competition` | map-runtime adapters | public | normalized task input | durable |
| `AatCompetitionDraftStore` | `feature:competition` | map-runtime repository | public | explicit draft persistence contract | durable |
| `AatPublishedCompetitionDayStore` | `feature:competition` | map-runtime publish/runtime repository | public | freeze and load immutable scoring day | durable |
| `AatPilotRosterSource` | `feature:competition` | map-runtime publish/runtime adapters | public | explicit scoring roster input seam | durable |
| `AatAcceptedTrackSource` | `feature:competition` | map-runtime reconcile path | public | official track input seam | durable |
| `AatCompetitionRuntimeRepository.state` | `feature:map-runtime` | map ViewModels/UI | public cross-module | one live scoring owner | durable |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| host ViewModel-owned scoring runtime | live competition state must survive recompositions and config changes | `Default` for scoring, `IO` for persistence | host ViewModel cleared | avoid singleton hidden state; OGN traffic already uses a singleton runtime and scoring should not mirror that pattern |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| `TaskCoordinatorAatTaskDefinitionAdapter` | `feature:map-runtime` | bridge from existing `Task` models | future canonical task-definition export if added | if task module exposes normalized AAT snapshot directly | adapter mapper tests |
| `AatPilotIdentityResolver` | `feature:map-runtime` | bridge transport identities into scored `pilotId` | future first-class roster service if added | if repo gains a canonical competition roster feature | identity resolution tests |
| `IgcAcceptedTrackAdapter` | `feature:map-runtime` | bridge over existing IGC module until narrow query exists | `feature:igc` accepted-track query | once feature:igc exports final read seam | reconcile adapter tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| single-pilot AAT scoring rules | `AatSinglePilotScorer.kt` | runtime repository, official reconcile | one place for official/provisional math | No |
| airborne projection policy | `AatProjectionEngine.kt` | runtime repository | one place for projected rows | No |
| leaderboard row visibility | `AatLeaderboardRanker.kt` | leaderboard ViewModel | prevents UI-side ranking drift | No |
| finish closure transformation | `AatFinishClosurePolicyEvaluator.kt` | runtime repository | explicit closure rules owner | No |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| none planned | n/a | n/a | avoid hidden globals in V1 | V1 uses injected instances and ViewModel-owned runtime | n/a |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| pilot fix timestamp | Wall (UTC from source fix/log) | scoring depends on actual fix chronology |
| accepted-track timestamp | Wall (UTC from accepted track) | official reconciliation must match log chronology |
| finish closure timestamp | Wall (UTC organizer policy time) | official closure rule is day-time based |
| live-fix receipt / stale detection | Monotonic | transport health only; not valid for scoring chronology |
| runtime processing loop | Monotonic for scheduling only | must not alter derived scoring output |

Explicitly forbidden comparisons:

- monotonic vs wall for scoring decisions
- replay vs wall for scoring decisions

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - scoring and ranking on `Default`
  - config and accepted-track persistence on `IO`
  - UI collection on `Main`
- Primary cadence/gating sensor:
  - event-driven on incoming fixes, config changes, closure, and reconcile events
- Hot-path latency budget:
  - per-fix scoring update should remain below 50 ms for a moderate field size in
    local tests

### 2.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path (`AppLogger` / Platform Edge) | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| live runtime repository | `AppLogger` only | pilot IDs and positions | debug-only, no raw accepted-track dumps in release | trim verbose debug logs after stabilization |
| accepted-track adapter | `AppLogger` only | file names and pilot IDs | no raw track content in logs | keep only failure codes after V1 |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - same task snapshot, config, fix sequence, closure time, and accepted tracks
    must produce the same results

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| no active AAT task | Unavailable | setup/leaderboard ViewModels | disable scoring UI with explanation | retry when task snapshot changes | ViewModel tests |
| draft or published-day storage read failure | Recoverable | draft/publish repositories | show storage warning and keep setup/runtime unavailable until recovery | repository flow emits degraded snapshot and retries deterministically | repository tests |
| invalid published config | User Action | draft/publish repositories + setup UI | block start/publish and show validation errors | edit and republish | config validation tests |
| task changes after publish | User Action | publish repository + setup UI | show published day stale badge; require republish | keep running last published day or stop scoring explicitly | publish and runtime tests |
| pilot cannot be matched to roster | Degraded | roster repository + identity resolver | show unmatched pilot count; keep those pilots out of ranking | retry when roster or identity hints change | roster and runtime tests |
| sparse or missing tracker fixes | Degraded | runtime repository | show projected/provisional rows with lower confidence | continue deterministic scoring with confidence markers | runtime tests |
| untimed or fallback-accepted live fixes | Degraded | live-fix adapter + runtime repository | confidence marker on affected pilot/result | deterministic degraded acceptance policy; never rewrite chronology from `lastSeenMillis` | adapter and runtime tests |
| accepted track missing for pilot | Recoverable | reconcile path | keep pilot provisional until resolved | retry when accepted track appears | reconcile tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| competition day ID | publish repository | deterministic hash from task snapshot hash + config hash + roster hash + day | Yes | publish freeze defines the scored day |
| leaderboard row identity | runtime repository | stable `pilotId` + result status | Yes | runtime owner publishes row state |
| scored `pilotId` | roster source | roster-defined stable ID | Yes | traffic and log identities are transport hints, not scoring identity |
| result timestamps | source fixes/logs | source UTC timestamps | Yes | scoring must follow source chronology |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| accepted-track source | fake/in-memory source in tests | No | return no accepted tracks and keep provisional status | test-only |
| live fix source | fake flow in tests | No | empty field, empty leaderboard | test-only |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| scoring state leaks into task UI | SSOT + UDF | review + unit tests | ViewModel/repository boundary tests |
| competition module depends on tasks/igc internals | dependency direction | enforceRules + review | module dependency checks |
| published task silently drifts after organizer edits | SSOT ownership | unit tests + review | publish repository / stale-publish tests |
| traffic identity misattributes live fixes | ownership boundary | unit tests + review | roster resolver / adapter tests |
| untimed or rewound source frames alter scoring order | time-base / determinism | unit tests | live-fix adapter tests |
| accepted-track reconcile path bypasses narrow seam | ownership boundary | unit tests + review | adapter tests |
| scorer nondeterminism | replay determinism | repeat-run unit tests | scorer/runtime tests |
| UI-side ranking drift | business rule ownership | unit tests | `AatLeaderboardRankerTest` |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| leaderboard refresh after fix | AAT-LIVE-001 | n/a | under 200 ms UI refresh on local debug run | manual slice proof + unit timing guard where practical | Phase 5 |
| setup publish to runtime propagation | AAT-LIVE-002 | n/a | visible within one config save cycle | ViewModel/runtime tests | Phase 2 |

## 3) Data Flow (Before -> After)

Describe end-to-end flow as text:

```text
Organizer Setup UI (feature:map)
  -> AatLiveScoringSetupViewModel
  -> AatCompetitionDraftStore (feature:map-runtime)
  -> publish freeze
  -> AatPublishedCompetitionDayRepository (feature:map-runtime)
  -> AatCompetitionConfig / AatPublishedCompetitionDay (feature:competition)

TaskManagerCoordinator.taskSnapshotFlow (feature:tasks)
  -> TaskCoordinatorAatTaskDefinitionAdapter (feature:map-runtime)
  -> AatTaskDefinitionSnapshot (feature:competition)
  -> publish freeze only

Roster source
  -> AatPilotRosterRepository / AatPilotIdentityResolver (feature:map-runtime)
  -> AatRosterSnapshot / scored pilotId (feature:competition)

Tracker fixes / accepted tracks
  -> map-runtime adapters
  -> scored pilotId + explicit fix quality
  -> AatCompetitionRuntimeRepository (feature:map-runtime)
  -> scoring engines in feature:competition
  -> leaderboard ViewModel / UI in feature:map
```

## 4) Implementation Phases

- Phase 0
  - Goal: approve module split and ADR.
  - Files to change: docs only.
  - Ownership/file split changes in this phase: none in code; decision recorded.
  - Tests to add/update: none.
  - Exit criteria: plan and ADR accepted.

- Phase 1
  - Goal: add `feature:competition` with normalized contracts.
  - Files to change: new competition module files.
  - Ownership/file split changes in this phase:
    - scoring domain leaves ad hoc task helpers and gets its own module
    - published day, roster, and fix-quality contracts become explicit.
  - Tests to add/update:
    - config validator tests
    - model hash tests
    - published-day hash tests
  - Exit criteria:
    - module compiles and has no direct dependency on `feature:tasks` or `feature:igc`
    - runtime inputs are modeled as published day + roster + normalized fixes.

- Phase 2
  - Goal: add draft persistence, publish freeze, roster resolution, and organizer setup UI.
  - Files to change:
    - map-runtime draft/published-day repositories
    - task adapter
    - roster repository and identity resolver
    - map setup UI/ViewModel
  - Ownership/file split changes in this phase:
    - organizer config moves out of task rules UI
    - publish becomes the only path that freezes task + roster for scoring.
  - Tests to add/update:
    - config and published-day round-trip
    - stale-publish tests
    - roster identity-resolution tests
    - setup ViewModel tests
  - Exit criteria:
    - published competition day is explicit and loadable
    - task edits after publish are detected as stale instead of silently mutating scoring.

- Phase 3
  - Goal: harden single-pilot scorer in the new module.
  - Files to change:
    - competition engines
    - temporary bridges from task geometry helpers as needed
  - Ownership/file split changes in this phase:
    - scoring formulas stop living in compatibility helpers.
  - Tests to add/update:
    - scorer and credited-fix tests
  - Exit criteria:
    - one pilot can be scored correctly from normalized inputs.

- Phase 4
  - Goal: add runtime repository in `feature:map-runtime`.
  - Files to change:
    - runtime repository
    - fix adapters
    - state publisher
  - Ownership/file split changes in this phase:
    - live competition state gets one runtime owner
    - raw traffic models stop at adapters; runtime consumes normalized scored fixes only.
  - Tests to add/update:
    - deterministic event-order tests
    - degraded fix-quality tests
  - Exit criteria:
    - multi-pilot live state works through one repository
    - runtime uses published day, not mutable task editor state.

- Phase 5
  - Goal: add leaderboard and pilot detail UI.
  - Files to change:
    - leaderboard ViewModel and screens
  - Ownership/file split changes in this phase:
    - UI reads live state only; no scoring in Compose.
  - Tests to add/update:
    - ViewModel and UI tests as needed
  - Exit criteria:
    - leaderboard renders projected/provisional/official rows correctly.

- Phase 6
  - Goal: add finish closure and accepted-track reconciliation.
  - Files to change:
    - competition reconcile engine
    - igc read seam
    - map-runtime accepted-track adapter
  - Ownership/file split changes in this phase:
    - official results gain one explicit path.
  - Tests to add/update:
    - reconcile and closure tests
  - Exit criteria:
    - official results derive from accepted-track input.

## 5) Test Plan

- Unit tests:
  - `AatCompetitionConfigValidatorTest`
  - `AatPublishedCompetitionDayRepositoryTest`
  - `AatSinglePilotScorerTest`
  - `AatProjectionEngineTest`
  - `AatLeaderboardRankerTest`
  - `TaskSnapshotToAatTaskDefinitionMapperTest`
  - `AatPilotIdentityResolverTest`
  - `OgnAatPilotFixAdapterTest`
  - `AatCompetitionRuntimeRepositoryDeterminismTest`
  - `AatLiveScoringSetupViewModelTest`
  - `AatOfficialResultReconcilerTest`
- Replay/regression tests:
  - same fix sequence must produce the same live state repeatedly
- UI/instrumentation tests (if needed):
  - setup publish flow
  - leaderboard state rendering
- Degraded/failure-mode tests:
  - invalid config
  - missing accepted tracks
  - sparse tracker confidence
- Boundary tests for removed bypasses:
  - no `TaskUiState` authority for leaderboard
  - no direct accepted-track read from UI
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | scorer, projection, ranking tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | runtime determinism tests |
| Persistence / settings / restore | Round-trip / restore / migration tests | draft/published-day repository tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | adapter and ViewModel boundary tests |
| UI interaction / lifecycle | UI or instrumentation coverage | setup/leaderboard tests |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | Phase 5 slice proof |

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
| scoring gets added to task module anyway | High | enforce module split in ADR and code review | XCPro Team |
| normalized task mapping loses geometry detail | High | explicit mapper tests and no lossy validation bridge reuse | XCPro Team |
| task edits after publish silently change scored day | High | published-day freeze plus stale-publish detection | XCPro Team |
| live traffic identity maps to wrong pilot | High | roster-owned `pilotId` and explicit resolver tests | XCPro Team |
| untimed or rewound traffic frames corrupt chronology | High | quality-tagged fix adapter and source-time acceptance tests | XCPro Team |
| runtime repository becomes hidden app-global state | Medium | keep host ViewModel-owned first | XCPro Team |
| official reconciliation depends on weak IGC read seam | Medium | add narrow accepted-track contract before Phase 6 completion | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file: `docs/ARCHITECTURE/ADR_AAT_LIVE_SCORING_BOUNDARIES_2026-03-16.md`
- Decision summary:
  - keep task declaration and competition scoring as separate authorities
  - add `:feature:competition`
  - freeze task + roster into a published competition day before runtime scoring
  - keep scoring pilot identity separate from traffic identity
  - compose in `feature:map-runtime`
  - render in `feature:map`
- Why this belongs in an ADR instead of plan notes:
  - it changes module boundaries, cross-module API shape, and long-lived
    ownership rules

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Ownership/boundary/public API decisions are captured in the ADR
- For map/overlay/replay interaction changes: impacted visual SLOs pass
  (or approved deviation is recorded)
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - setup UI
  - accepted-track reconciliation
  - leaderboard UI
- Recovery steps if regression is detected:
  - keep task module untouched
  - disable competition runtime wiring behind one feature flag if needed
  - revert later phases without undoing the competition module foundation
