# 02 — Real-Time AAT Engine Spec

## 1. Objective

Implement a real-time AAT leaderboard in XCPro that can answer:

- who is **officially leading** right now
- who is **provisionally leading** right now
- who is **projected to lead** if the task ended according to the configured projection model

The implementation must be deterministic, testable, and compatible with existing AAT geometry/calculation code.

## 2. Scope

### In scope

- organizer-configured AAT scoring profile
- live pilot state ingestion from trackers
- official / provisional / projected state separation
- AAT credited-fix optimization for achieved areas
- classic AAT day-parameter computation
- live leaderboard ranking
- finish closure handling
- FR-log reconciliation

### Out of scope for V1 unless already largely present

- full Alternative scoring implementation
- cloud/backend redesign
- cross-class scoring
- deleting existing custom AAT features
- official protest / penalties workflow

## 3. Assumptions about the existing repo

The uploaded docs indicate XCPro likely already has some combination of:

- `AATTaskCalculator`
- `AATPathOptimizer`
- `AATTaskValidator`
- area geometry helpers
- `calculateFlightResult(...)`
- `calculateRealTimeRecommendation(...)`

Codex must **inspect the actual repo** and extend the real implementation path instead of creating a parallel one.

## 4. Architecture rules

1. Put all scoring math in pure domain services.
2. UI layers should consume already-calculated state.
3. Use one authoritative config object for the task/day.
4. Make clocks injectable.
5. Treat tracker input and FR-log input as separate data sources.
6. Keep live results and officialized results separately addressable.
7. Persist algorithm version and config hash with scored outputs.

## 5. Status model

Each pilot row must have exactly one of these top-level result statuses:

- `OFFICIAL`
- `PROVISIONAL`
- `PROJECTED`
- `PENDING_ACCOUNTING`
- `NOT_STARTED`
- `WITHDRAWN` (optional if already supported)

### Meaning

- `OFFICIAL`: computed from accepted FR log(s)
- `PROVISIONAL`: finished or outlanded from live tracking / uploaded but unaccepted log
- `PROJECTED`: airborne; leaderboard value is a deterministic estimate
- `PENDING_ACCOUNTING`: not fully accounted for before finish closure; hidden from rank
- `NOT_STARTED`: no valid start yet

## 6. Suggested domain model

Use repo-native naming if needed, but the effective model should cover at least this information.

```kotlin
enum class RulesProfile {
    FAI_ANNEX_A_CURRENT,
    CUSTOM_LOCAL_RULES
}

enum class ScoringSystem {
    CLASSIC,
    ALTERNATIVE
}

enum class LeaderboardMetric {
    PROJECTED_CLASSIC_SCORE,
    PROJECTED_HANDICAPPED_SPEED,
    PROVISIONAL_CLASSIC_SCORE,
    OFFICIAL_CLASSIC_SCORE
}

enum class ProjectionMode {
    OPTIMIZE_TO_MIN_TIME,
    HEAD_HOME_NOW
}

enum class RankingVisibility {
    ADMIN_ONLY,
    PUBLIC_SPECTATOR,
    PILOT_VISIBLE
}

enum class PilotTaskStatus {
    NOT_STARTED,
    STARTED,
    ACHIEVED_AREAS_IN_SEQUENCE,
    FINISHED,
    OUTLANDED,
    FINISH_CLOSED_UNFINISHED,
    PENDING_ACCOUNTING
}

data class AatCompetitionConfig(
    val competitionId: String,
    val classId: String,
    val taskId: String,
    val rulesProfile: RulesProfile,
    val scoringSystem: ScoringSystem,
    val handicapEnabled: Boolean,
    val minimumTaskTimeSec: Long,
    val finishClosureTimeUtc: Instant?,
    val rankingVisibility: RankingVisibility,
    val leaderboardMetric: LeaderboardMetric,
    val projectionMode: ProjectionMode,
    val cylinderStartWaiverRef: String?,
    val algorithmVersion: String,
    val algorithmHash: String
)

data class TrackFix(
    val timeUtc: Instant,
    val latDeg: Double,
    val lonDeg: Double,
    val gpsAltM: Double?,
    val groundSpeedMps: Double?,
    val source: FixSource,
    val quality: FixQuality
)

data class AreaVisitWindow(
    val areaIndex: Int,
    val startTimeUtc: Instant,
    val endTimeUtc: Instant?,
    val candidatePoints: List<CandidatePoint>,
    val achieved: Boolean
)

data class CandidatePoint(
    val latDeg: Double,
    val lonDeg: Double,
    val timeUtc: Instant?,
    val kind: CandidatePointKind,
    val confidence: CandidateConfidence
)

data class CreditedFix(
    val areaIndex: Int,
    val point: CandidatePoint,
    val officiality: ResultOfficiality
)

data class PilotLiveAatState(
    val pilotId: String,
    val pilotName: String,
    val handicap: Double,
    val taskStatus: PilotTaskStatus,
    val validStartTimeUtc: Instant?,
    val finishTimeUtc: Instant?,
    val outlandingTimeUtc: Instant?,
    val lastFix: TrackFix?,
    val achievedAreaCount: Int,
    val areaWindows: List<AreaVisitWindow>,
    val provisionalCreditedFixes: List<CreditedFix>,
    val markedDistanceM: Double?,
    val markedTimeSec: Long?,
    val markingSpeedMps: Double?,
    val handicappedDistanceM: Double?,
    val handicappedSpeedMps: Double?,
    val projectedDistanceM: Double?,
    val projectedTimeSec: Long?,
    val projectedSpeedMps: Double?,
    val projectedHandicappedSpeedMps: Double?,
    val projectedClassicScore: Double?,
    val resultOfficiality: ResultOfficiality
)

data class LiveDayParameters(
    val doM: Double,
    val voMps: Double,
    val toSec: Long,
    val n1: Int,
    val n2: Int,
    val n3: Int,
    val n4: Int,
    val n: Int,
    val pm: Double,
    val pdm: Double,
    val pvm: Double,
    val f: Double,
    val fcr: Double,
    val stable: Boolean
)
```

## 7. Event flow

### 7.1 Input events

The live engine should react to:

- task config saved / updated
- pilot roster loaded
- tracker fix received
- finish closure reached
- FR log uploaded
- FR log accepted / rejected
- pilot manually marked withdrawn / not launched (if supported)

### 7.2 Main recompute loop

```kotlin
fun onTrackerFix(pilotId: String, fix: TrackFix) {
    val state0 = pilotStateRepo.get(pilotId)
    val state1 = startFinishAndAreaDetector.apply(state0, fix)
    val state2 = creditedFixOptimizer.update(taskConfig, state1)
    val state3 = liveProjectionEngine.update(taskConfig, state2, now())
    pilotStateRepo.save(state3)

    val allStates = pilotStateRepo.getAllForTask(taskConfig.taskId)
    val params = dayParameterEngine.compute(taskConfig, allStates, now())
    val leaderboard = leaderboardEngine.rank(taskConfig, allStates, params)
    leaderboardRepo.save(taskConfig.taskId, leaderboard)
}
```

## 8. Algorithm details

### 8.1 Start detection

Use existing repo geometry if present.

Rules:

- line start crossing time is interpolated to the nearest second
- cylinder start is allowed only if config says a waiver exists

Implementation:

- line start:
  - detect segment crossing in required direction
  - interpolate crossing timestamp
- cylinder start:
  - only active when `cylinderStartWaiverRef != null`
  - use existing repo logic if present
  - if no reliable repo logic exists, defer cylinder start support and block save under FAI profile

### 8.2 Finish detection

Support finish ring and finish line.

Rules:

- finish time is the first valid crossing / entry
- finish ring is the preferred default
- after valid finish, the task is complete and later fixes do not change scored finish time

### 8.3 Area achievement detection

This must support both:

- actual in-area fixes
- line-segment intersection between consecutive fixes and the area observation zone

Do not require a sampled fix strictly inside the zone for achievement.

### 8.4 Build candidate points per achieved area

For each area visit window, candidate points should include:

- raw live fixes inside the zone
- interpolated segment entry points
- interpolated segment exit points
- optionally the current fix if the pilot is currently inside the active area

Each candidate point should carry a confidence level, because live trackers may be sparse.

Suggested confidence levels:

- `RECORDED_FIX`
- `INTERPOLATED_SEGMENT_INTERSECTION`
- `LIVE_TRACK_APPROXIMATION`

### 8.5 Provisional credited-fix optimizer

For the achieved prefix of the task, choose the set of credited fixes that maximizes total credited distance.

Use a dynamic-programming style optimizer over ordered area candidate sets.

#### Objective

For areas `1..k`, maximize:

`dist(startAnchor, c1) + sum(dist(ci, c(i+1))) + terminalTerm`

Where:

- `startAnchor` = start point / credited start point according to geometry
- `terminalTerm` depends on current state:
  - finished: distance to finish point
  - outlanded last leg: distance treatment for last leg
  - outlanded earlier leg: nearest point of next area to outlanding point
  - airborne/projected: current anchor chosen by the projection mode

#### Pseudocode

```kotlin
fun optimizeCreditedFixes(
    startAnchor: GeoPoint,
    candidateSets: List<List<CandidatePoint>>,
    endAnchor: GeoPoint
): OptimizedFixSet {
    if (candidateSets.isEmpty()) return OptimizedFixSet(emptyList(), dist(startAnchor, endAnchor))

    val dp = mutableListOf<MutableList<Double>>()
    val prev = mutableListOf<MutableList<Int>>()

    candidateSets.forEachIndexed { i, set ->
        dp += MutableList(set.size) { Double.NEGATIVE_INFINITY }
        prev += MutableList(set.size) { -1 }

        if (i == 0) {
            for (j in set.indices) {
                dp[i][j] = geoDistance(startAnchor, set[j].point())
            }
        } else {
            for (j in set.indices) {
                for (k in candidateSets[i - 1].indices) {
                    val score = dp[i - 1][k] + geoDistance(candidateSets[i - 1][k].point(), set[j].point())
                    if (score > dp[i][j]) {
                        dp[i][j] = score
                        prev[i][j] = k
                    }
                }
            }
        }
    }

    var bestLast = 0
    var bestScore = Double.NEGATIVE_INFINITY
    val lastIndex = candidateSets.lastIndex
    for (j in candidateSets[lastIndex].indices) {
        val total = dp[lastIndex][j] + geoDistance(candidateSets[lastIndex][j].point(), endAnchor)
        if (total > bestScore) {
            bestScore = total
            bestLast = j
        }
    }

    val chosen = mutableListOf<CandidatePoint>()
    var idx = bestLast
    for (i in lastIndex downTo 0) {
        chosen += candidateSets[i][idx]
        idx = prev[i][idx]
    }
    chosen.reverse()

    return OptimizedFixSet(chosen, bestScore)
}
```

### 8.6 Marking distance calculation

Codex should explicitly implement separate methods for:

- `computeFinishedMarkingDistance(...)`
- `computeOutlandedLastLegDistance(...)`
- `computeOutlandedEarlierLegDistance(...)`

Do not bury these three rule paths in one untestable method.

### 8.7 Marking time and speed

- finishers:
  - `T = max(elapsedTime, minimumTaskTime)`
  - `V = D / T`
  - `Dh = D / H`
  - `Vh = V / H`
- non-finishers:
  - no marking time
  - no speed

### 8.8 Projection engine — V1 design

The projection engine must be deterministic.

#### Principle

A projected result is not “what the pilot will definitely score.”
It is “what XCPro predicts under the configured projection model.”

#### Supported V1 projection modes

##### A. `OPTIMIZE_TO_MIN_TIME`

Use when the pilot is still under minimum task time.

1. Compute time remaining to minimum task time:
   - `budgetSec = max(0, Td - elapsedSec)`

2. Estimate remaining cruise/average task speed:
   - default = EWMA of the pilot’s last valid tracker speeds over a short window
   - fallback = completed distance / elapsed time
   - fallback of last resort = task nominal speed if the repo has one, otherwise config default

3. Convert time budget to target distance:
   - `targetDistance = estimatedSpeed * budgetSec`

4. Clamp target distance to the feasible remaining distance range from the current state:
   - between shortest legal remaining route and longest sampled/optimized legal remaining route

5. Ask the existing or new path optimizer for the target points that best realize the target distance.

6. Produce:
   - `D_proj`
   - `T_proj`
   - `V_proj`
   - `Dh_proj`
   - `Vh_proj`

##### B. `HEAD_HOME_NOW`

Use when the pilot is already over minimum task time, or as a conservative fallback.

1. Compute the shortest legal remaining route through remaining mandatory areas to finish.
2. Assume the pilot goes home on that route immediately.
3. Compute projected distance/time/speed from that route.

#### Default recommendation

- under minimum task time: `OPTIMIZE_TO_MIN_TIME`
- at or beyond minimum task time: `HEAD_HOME_NOW`

This default is intentionally conservative once a pilot is already legal to finish.

### 8.9 Day parameter engine for Classic scoring

Compute live versions of:

- `Do`
- `Vo`
- `To`
- `n1`
- `n2`
- `n3`
- `n4`
- `N`
- `Pm`
- `Pdm`
- `Pvm`
- `F`
- `FCR`

#### Data source precedence

For each pilot:

1. official result if available
2. provisional finished/outlanded result
3. projected result if airborne
4. pending-accounting placeholder if not yet accounted for before finish closure

#### Treatment of `PENDING_ACCOUNTING`

Before finish closure:

- do not include these pilots in visible ranking
- optionally include them in hidden preliminary counts to keep parameter estimation stable

Implementation choice for V1:

- increment hidden `n1` / `n2` assumptions as needed for preliminary stability
- do not let hidden pilots displace a visible pilot from the ranked list
- mark leaderboard as `unstable = true` until finish closure

### 8.10 Per-pilot classic score

For Classic scoring:

- finishers:
  - `Pd = Pdm`
  - `Pv = Pvm * (Vh - 2/3 * Vo) / (1/3 * Vo)` if `Vh >= 2/3 * Vo`, else `Pv = 0`
- non-finishers:
  - `Pd = Pdm * (Dh / Do)`
  - `Pv = 0`
- daily score:
  - `S = F * FCR * (Pv + Pd)`

Round only at the presentation/output boundary if the existing repo architecture already does so there.

### 8.11 Leaderboard ranking rules

Default visible ranking order:

1. `OFFICIAL`
2. `PROVISIONAL`
3. `PROJECTED`

Within the same officiality/status bucket:

- rank by configured metric:
  - projected or provisional classic score when classic scoring is enabled
  - otherwise projected/provisional handicapped speed
- tie-break:
  1. higher handicapped speed
  2. higher handicapped distance
  3. earlier completion time if both finished
  4. stable deterministic pilot id / contest number

### 8.12 Finish closure behavior

At finish closure:

- any still-airborne, unfinished pilot becomes `FINISH_CLOSED_UNFINISHED`
- outlanding position = last valid GNSS fix immediately before closure
- recompute non-finisher marked distance
- remove projected status
- convert to provisional non-finisher unless already officialized from a log

### 8.13 FR-log reconciliation

When official logs arrive:

1. rebuild achievement windows from FR data
2. rerun credited-fix optimizer from FR data
3. recompute marking distance/time/speed
4. recompute day parameters
5. re-rank leaderboard
6. preserve old live result for audit/debug if storage permits

Do not overwrite without keeping enough information to explain why official and live values differ.

## 9. Suggested service boundaries

Suggested service set:

- `AatRulesProfileValidator`
- `AatStartFinishDetector`
- `AatAreaAchievementDetector`
- `AatCandidatePointBuilder`
- `AatCreditedFixOptimizer`
- `AatMarkingDistanceEngine`
- `AatLiveProjectionEngine`
- `AatClassicDayParameterEngine`
- `AatLeaderboardEngine`
- `AatOfficialReconciliationService`

## 10. Suggested file structure

Adapt to actual repo structure, but conceptually aim for:

```text
tasks/aat/
  config/
    AatCompetitionConfig.kt
    AatRulesProfile.kt
    AatScoringSystem.kt

  live/
    AatLivePilotState.kt
    AatStartFinishDetector.kt
    AatAreaAchievementDetector.kt
    AatCandidatePointBuilder.kt
    AatLiveProjectionEngine.kt
    AatLeaderboardEngine.kt
    AatOfficialReconciliationService.kt

  scoring/
    AatCreditedFixOptimizer.kt
    AatMarkingDistanceEngine.kt
    AatClassicDayParameterEngine.kt

  ui/
    setup/
      AatSetupScreen.kt
      AatSetupViewModel.kt
    leaderboard/
      AatLiveLeaderboardScreen.kt
      AatLiveLeaderboardViewModel.kt
```

## 11. Performance expectations

V1 should comfortably handle a normal contest class in near real time.

Target budget:

- per-fix update for one pilot: under 30 ms on typical modern device / service runtime
- full leaderboard recompute for ~50 pilots / ~5 areas: under 200 ms
- no UI-thread geometry or scoring work

If the repo already uses coroutines, run recomputation off the main thread and debounce visible updates.

## 12. Failure handling

### Sparse or delayed tracking

If live tracking is sparse:

- continue to use segment-intersection logic for achievement
- reduce confidence on candidate points
- keep projected rows visible but mark them as low-confidence when necessary

### Missing handicap

If handicapping is enabled but a pilot has no handicap:

- block official score
- show setup/runtime validation error
- keep pilot out of scored ranking until resolved

### Unsupported Alternative scoring

If setup selects Alternative scoring and the repo does not support it:

- block save or block live leaderboard activation
- show a clear message instead of silently falling back to Classic

## 13. Acceptance criteria

Codex is done when all are true:

- a saved AAT config can drive live scoring behavior
- FAI profile blocks or warns on non-standard geometry
- achieved areas are detected by fix or segment intersection
- credited-fix optimization is not hard-coded to area centers
- finishers, outlanders, and airborne pilots each get correct status handling
- classic day parameters update as pilots change status
- leaderboard differentiates official / provisional / projected
- finish closure converts airborne pilots correctly
- official FR-log reconciliation works
- tests cover the critical rule paths
