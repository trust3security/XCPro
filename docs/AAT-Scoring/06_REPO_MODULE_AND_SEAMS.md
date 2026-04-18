# 06 - Repo Module and Seam Proposal

## Purpose

This is the repo-specific architecture proposal for AAT-LiveScoring.

The key decision is:

- create a new pure domain module for competition scoring
- keep runtime composition outside that module
- keep task declaration ownership where it already is

Recommended module name:

- `:feature:competition`

Avoid `:feature:scoring` unless the module is truly math-only. In this repo the
new boundary must own config, statuses, projection, and reconciliation policy,
not just formulas.

---

## Recommended module split

| Module | Owns | Must not own |
|---|---|---|
| `feature:tasks` | task declaration, task editing, target editing, task persistence, edit-time validation | leaderboard state, organizer scoring config, live competition state, official reconciliation |
| `feature:competition` | pure AAT live scoring domain, draft/published day models, roster models, scoring models, policy, ranking, reconciliation rules, domain ports | Compose UI, ViewModels, `TaskManagerCoordinator`, `IgcFlightLogRepository`, MapLibre, DataStore/file I/O |
| `feature:map-runtime` | runtime composition, publish/freeze flow, adapters from task snapshot/live fixes/IGC, config persistence, roster resolution, live competition repository | task editor UI, scoring formulas duplicated from `feature:competition` |
| `feature:map` | organizer setup UI, leaderboard UI, pilot detail UI, host ViewModels | scoring formulas, file/log parsing, task SSOT |
| `feature:igc` | IGC recording, finalization, recovery, accepted-track read seam when added | AAT scoring math, leaderboard state |
| `app` | DI wiring only | feature business logic |

---

## New module: `:feature:competition`

### Package map

| Package | Owns | Notes |
|---|---|---|
| `com.trust3.xcpro.competition.aat.config` | config models and validator | rules profile, scoring mode, projection mode, visibility, finish closure |
| `com.trust3.xcpro.competition.aat.model` | normalized scoring models | no `Task`, no IGC file models |
| `com.trust3.xcpro.competition.aat.roster` | roster and identity models | stable pilot identity for scoring, not transport identity |
| `com.trust3.xcpro.competition.aat.engine` | pure scoring engines | deterministic, replay-safe for same input sequence |
| `com.trust3.xcpro.competition.aat.policy` | rule and ranking policy owners | canonical formulas and visibility policy |
| `com.trust3.xcpro.competition.aat.ports` | input/output contracts | implemented in `feature:map-runtime` or `feature:igc` |

### Exact core models

These models belong in `feature:competition`.

- `AatCompetitionDraft`
- `AatCompetitionConfig`
- `AatPublishedCompetitionDay`
- `AatRulesProfile`
- `AatScoringSystem`
- `AatProjectionMode`
- `AatLeaderboardVisibilityPolicy`
- `AatFinishClosurePolicy`
- `AatTaskDefinitionSnapshot`
- `AatTaskSnapshotHash`
- `AatTaskStart`
- `AatTaskFinish`
- `AatTaskArea`
- `AatPilotIdentity`
- `AatRosterSnapshot`
- `AatRosterEntry`
- `AatPilotIdentityMatch`
- `AatPilotFix`
- `AatPilotFixSource`
- `AatPilotFixQuality`
- `AatAcceptedTrack`
- `AatPilotProgress`
- `AatPilotScoringState`
- `AatPilotResult`
- `AatResultStatus`
- `AatLeaderboardRow`
- `AatCompetitionState`

### Exact public ports

These contracts belong in `feature:competition` and are implemented elsewhere.

```kotlin
package com.trust3.xcpro.competition.aat.ports

interface AatTaskDefinitionPort {
    fun activeTask(): AatTaskDefinitionSnapshot?
}

interface AatCompetitionDraftStore {
    suspend fun loadDraft(dayId: String): AatCompetitionConfig?
    suspend fun saveDraft(config: AatCompetitionConfig)
}

interface AatPublishedCompetitionDayStore {
    suspend fun loadPublished(dayId: String): AatPublishedCompetitionDay?
    suspend fun publish(
        dayId: String,
        config: AatCompetitionConfig,
        taskSnapshot: AatTaskDefinitionSnapshot,
        roster: AatRosterSnapshot
    ): AatPublishedCompetitionDay
}

interface AatPilotRosterSource {
    suspend fun loadRoster(dayId: String): AatRosterSnapshot?
}

interface AatLivePilotFixSource {
    val fixes: kotlinx.coroutines.flow.Flow<AatPilotFix>
}

interface AatAcceptedTrackSource {
    suspend fun loadAcceptedTrack(dayId: String, pilotId: String): AatAcceptedTrack?
}
```

If handicap data is external, add:

```kotlin
interface AatHandicapPolicySource {
    suspend fun handicapFor(pilotId: String): Double?
}
```

### Exact engines

These are the canonical pure-domain owners.

- `AatCompetitionConfigValidator`
- `AatCompetitionPublishValidator`
- `AatSinglePilotScorer`
- `AatProjectionEngine`
- `AatLeaderboardRanker`
- `AatOfficialResultReconciler`
- `AatFinishClosurePolicyEvaluator`

### Canonical policy ownership

| Concern | Canonical owner |
|---|---|
| single-pilot AAT scoring rules | `AatSinglePilotScorer` |
| airborne projection policy | `AatProjectionEngine` |
| official vs provisional vs projected row visibility | `AatLeaderboardRanker` |
| finish closure transformation | `AatFinishClosurePolicyEvaluator` |
| config validation and hash | `AatCompetitionConfigValidator` |

---

## `feature:map-runtime` ownership

This module already depends on both `feature:tasks` and `feature:igc`, so it is
the correct composition layer.

### Exact packages to add

| Package | Owns |
|---|---|
| `com.trust3.xcpro.map.competition.aat.runtime` | live runtime repository and runtime state |
| `com.trust3.xcpro.map.competition.aat.adapter` | adapters from task snapshot, trackers, and accepted tracks |
| `com.trust3.xcpro.map.competition.aat.roster` | roster loading and identity resolution |
| `com.trust3.xcpro.map.competition.aat.mapper` | mapping from repo-specific models into competition models |
| `com.trust3.xcpro.map.competition.aat.data` | draft/published persistence implementation |

### Exact runtime files

- `AatCompetitionRuntimeRepository.kt`
- `AatCompetitionRuntimeState.kt`
- `MapAatCompetitionUseCase.kt`
- `AatPublishedCompetitionDayRepository.kt`
- `AatCompetitionPublishService.kt`
- `TaskCoordinatorAatTaskDefinitionAdapter.kt`
- `TaskSnapshotToAatTaskDefinitionMapper.kt`
- `AatPilotRosterRepository.kt`
- `AatPilotIdentityResolver.kt`
- `OgnAatPilotFixAdapter.kt`
- `OgnTargetToAatPilotFixMapper.kt`
- `IgcAcceptedTrackAdapter.kt`
- `AatCompetitionDraftDataStore.kt`
- `AatCompetitionPublishedDayDataStore.kt`

Persistence recommendation:

- follow the `ProfileStorage` pattern already in the repo: DataStore-backed
  snapshot flow with explicit read-status/degraded handling
- keep the repository as the authoritative in-memory read owner; persistence is
  for restore and publish durability, not a second live SSOT

### Runtime repository contract

This should be the authoritative runtime owner for live competition state.

```kotlin
package com.trust3.xcpro.map.competition.aat.runtime

interface AatCompetitionRuntimeRepository {
    val state: kotlinx.coroutines.flow.StateFlow<AatCompetitionState>

    suspend fun start(dayId: String)
    suspend fun ingestPilotFix(fix: AatPilotFix)
    suspend fun closeFinish(closureUtcMs: Long)
    suspend fun reconcileOfficialResults()
    suspend fun clear()
}
```

Professional recommendation:

- make the first implementation caller-owned by a host ViewModel
- do not make it a process-global singleton

### Published competition-day freeze

This is the most important seam to keep explicit.

- organizer edits happen against draft config plus the current mutable task
  snapshot
- publish captures an immutable `AatPublishedCompetitionDay`
- that published day must include:
  - published config
  - frozen `AatTaskDefinitionSnapshot`
  - frozen `AatRosterSnapshot`
  - algorithm version/hash
  - task snapshot hash and roster snapshot hash
- live scoring runtime starts from the published day, not from the mutable task
  sheet
- if the organizer changes the task after publish, setup UI must mark the
  published day as stale and require republish; runtime must not silently switch
  to the edited task

### Roster and identity seam

Live traffic identity is useful input, but it is not the scoring roster owner.

- scoring `pilotId` must come from `AatRosterSnapshot`
- `competitionNumber`, registration, and transport `canonicalKey` are matching
  hints only
- `AatPilotIdentityResolver` in `feature:map-runtime` should map traffic/log
  identity into roster `pilotId`
- unmatched live traffic must stay unranked until resolved; do not auto-create
  scored pilots from traffic feed labels

### Live-fix quality seam

Raw tracker targets also need normalization before they become scoring fixes.

- `AatPilotFix.eventTimeUtcMs` is the scoring chronology authority
- `lastSeenMillis` or local receipt time may support diagnostics only
- if a source frame is untimed, out-of-order, or accepted only through a
  degraded fallback, the mapped fix must carry explicit `AatPilotFixQuality`
- scoring engines may use degraded fixes provisionally, but UI/runtime must be
  able to expose that lower confidence explicitly
- raw `OgnTrafficTarget` must never be treated as a scoring model directly

---

## `feature:map` ownership

`feature:map` should own only organizer-facing UI and ViewModels.

### Exact packages to add

| Package | Owns |
|---|---|
| `com.trust3.xcpro.map.ui.competition.aat` | setup and leaderboard screens |
| `com.trust3.xcpro.map.ui.competition.aat.detail` | pilot detail UI |
| `com.trust3.xcpro.map.competition.aat` | host ViewModels only |

### Exact UI files

- `AatLiveScoringSetupScreen.kt`
- `AatLiveLeaderboardScreen.kt`
- `AatPilotDetailScreen.kt`
- `AatLiveScoringSetupViewModel.kt`
- `AatLiveLeaderboardViewModel.kt`

These screens read and render state. They do not calculate scores.

---

## `feature:tasks` stays focused

### Keep in `feature:tasks`

- `TaskManagerCoordinator`
- `TaskSheetViewModel`
- `TaskRepository`
- `AATTaskManager`
- task persistence
- AAT target editing
- task geometry editing
- edit-time validation and linting

### Reuse from `feature:tasks`

- `TaskManagerCoordinator.taskSnapshotFlow`
- `AATTaskCoreMappers` as reference only
- `AreaBoundaryCalculator` and related geometry helpers as algorithm inputs when
  safe to normalize

### Do not reuse as scoring authority

- `TaskUiState`
- `TaskRepository.state`
- `AATValidationBridge`
- `ComprehensiveAATValidator`
- `AATFlightPathValidator`
- `AATTaskCalculator.calculateFlightResult`

---

## `feature:igc` stays focused

### Keep in `feature:igc`

- recording
- finalization
- recovery
- declaration metadata

### Add later in `feature:igc`

Only when official reconciliation lands:

- `IgcAcceptedTrackQuery.kt` or equivalent narrow read contract

That interface should expose accepted track content, not scoring policy.

---

## Example end-to-end data flow

```text
TaskManagerCoordinator.taskSnapshotFlow
  -> TaskCoordinatorAatTaskDefinitionAdapter (map-runtime)
  -> AatTaskDefinitionSnapshot (competition)
  -> AatCompetitionPublishService (map-runtime freeze step)

Organizer setup UI (map)
  -> AatCompetitionDraftStore / AatPublishedCompetitionDayStore (map-runtime)
  -> AatCompetitionConfig / AatPublishedCompetitionDay (competition)

Roster source
  -> AatPilotRosterRepository / AatPilotIdentityResolver (map-runtime)
  -> AatRosterSnapshot (competition)

Tracker fixes / accepted tracks
  -> adapters (map-runtime / igc)
  -> resolved `pilotId` + normalized fix quality
  -> AatCompetitionRuntimeRepository (map-runtime)
  -> AatSinglePilotScorer / AatProjectionEngine / AatLeaderboardRanker (competition)
  -> leaderboard UI state (map)
```

---

## What must not happen

- Do not add leaderboard rows or scoring config to `TaskUiState`.
- Do not let `TaskSheetViewModel` become the owner of competition state.
- Do not call `IgcFlightLogRepository` directly from Compose or ViewModels.
- Do not let `feature:competition` depend on `feature:tasks` or `feature:igc`.
- Do not reuse `AATValidationBridge` as the scoring input normalization layer.
- Do not let task edits after publish silently rewrite live competition scoring.
- Do not treat OGN `competitionNumber` or `canonicalKey` as the authoritative
  scoring pilot ID.
- Do not use tracker `lastSeenMillis` as scoring chronology.

---

## Why this is the professional split

- It preserves the current SSOT instead of replacing it under pressure.
- It gives scoring a real owner instead of scattering it through UI and task
  editor code.
- It uses `feature:map-runtime` for composition because that dependency edge
  already exists.
- It keeps future testing cheap because the scoring engine stays pure and
  normalized.
