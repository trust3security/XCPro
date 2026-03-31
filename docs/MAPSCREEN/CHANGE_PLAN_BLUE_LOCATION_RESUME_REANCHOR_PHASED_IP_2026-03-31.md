# Blue Ownship Resume Re-anchor Phased IP

## 0) Metadata

- Title: Snap blue ownship overlay to the latest fix after app foreground resume gaps
- Owner: XCPro Team
- Date: 2026-03-31
- Issue/PR: TBD
- Status: In Progress

Required pre-read order:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
9. `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`

## 1) Scope

- Problem statement:
  - When XCPro is backgrounded and the pilot moves, the blue ownship triangle can remain near the old foreground position after returning to the app.
  - Once the first fresh fix is rendered, the visual pose can walk toward the true location in small clamped steps instead of re-anchoring immediately.
  - This creates a wrong-location visual state on resume even when the latest fix is already authoritative in the flight-data SSOT.
- Why now:
  - This is a user-visible correctness issue in the map runtime path.
  - The symptom reduces trust in current-position rendering at exactly the moment the user expects an immediate current-location answer.
- In scope:
  - Lock the failing resume-gap behavior with regression tests.
  - Add a visual-only re-anchor policy for large fix timestamp gaps in the display-pose owner.
  - Preserve existing outlier clamping for normal in-session GNSS noise.
  - Document the updated display-smoothing behavior.
- Out of scope:
  - No changes to `FlightDataRepository` SSOT ownership.
  - No changes to sensor cadence policy or GPS provider selection.
  - No changes to `BlueLocationOverlay` rendering contracts or map camera-follow ownership.
  - No replay/business-logic changes outside the visual pose owner.
- User-visible impact:
  - After returning to XCPro, the blue ownship overlay should snap to the latest valid fix instead of crawling from the stale pre-background pose.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| authoritative ownship fix | `FlightDataRepository` -> `FlightDataUseCase` | `StateFlow<CompleteFlightData?>` | UI/runtime-owned location mirrors |
| map UI ownship fix projection | `MapScreenViewModel` | `mapLocation: StateFlow<MapLocationUiModel?>` | direct sensor reads in UI/runtime |
| visual display pose smoothing state | `DisplayPoseSmoother` via `DisplayPosePipeline` / `DisplayPoseCoordinator` | internal runtime state only | ViewModel/store/repository copies |
| resume-gap re-anchor decision | `DisplayPoseSmoother` | internal visual-only policy | lifecycle-owned mutable flags or overlay-owned duplicates |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| latest raw visual fix | `DisplayPoseSmoother` | `pushRawFix(...)` | `tick(...)` | `MapLocationUiModel` / replay pose feed | none | smoother reset/clear | live monotonic or replay timestamp passed through existing feed | unit tests |
| last rendered visual pose | `DisplayPoseSmoother` | `tick(...)` | `tick(...)` result consumed by `DisplayPoseRenderCoordinator` | previous rendered pose + latest raw fix | none | smoother reset/clear or large-gap re-anchor | same as active display feed time base | unit tests |
| large-gap re-anchor decision | `DisplayPoseSmoother` | `pushRawFix(...)` | internal only | delta between successive raw-fix timestamps in the active time base | none | each new raw fix, smoother reset/clear | same as active feed time base | unit tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

Modules/files touched:
- `feature:map-runtime`
- `feature:map`
- `docs/MAPSCREEN`
- `mapposition.md`

Boundary risk:
- do not move visual re-anchor policy into `BlueLocationOverlay`
- do not add lifecycle-owned mutable state for ownship position
- do not bypass `MapScreenViewModel.mapLocation`
- do not push resume-gap policy into repositories or sensor owners

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/DisplayPoseSmoother.kt` | canonical owner for visual-only map pose smoothing and outlier clamp | keep visual smoothing policy local to the smoothing owner | add an explicit long-gap re-anchor rule ahead of normal clamp behavior |
| `feature/map/src/test/java/com/example/xcpro/map/DisplayPoseSmootherTest.kt` | existing regression home for smoothing/clamp policy | extend focused policy tests instead of adding broad integration scaffolding first | add resume-gap coverage instead of only same-session clamp coverage |
| `feature/map/src/test/java/com/example/xcpro/map/DisplayPoseCoordinatorTest.kt` | existing owner test for reset and time-base transitions | keep time-base/reset behavior verified at the coordinator seam | no functional change planned unless implementation proves coordinator needs explicit gap handling |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| long-gap stale-pose re-anchor policy | implicit by-product of general smoother clamp behavior | explicit `DisplayPoseSmoother` visual policy | this is display smoothing behavior, not lifecycle/business/repository behavior | smoother unit tests |

### 2.2C Bypass Removal Plan

No bypass removal is planned. Existing flow remains:

`FlightDataRepository -> MapScreenViewModel.mapLocation -> MapComposeEffects -> LocationManager -> DisplayPoseCoordinator -> DisplayPoseSmoother -> DisplayPoseRenderCoordinator -> BlueLocationOverlay`

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/MAPSCREEN/CHANGE_PLAN_BLUE_LOCATION_RESUME_REANCHOR_PHASED_IP_2026-03-31.md` | New | task-level plan and architecture contract for this slice | required for non-trivial map-runtime change | not an ADR because no durable boundary move is planned | No |
| `feature/map/src/test/java/com/example/xcpro/map/DisplayPoseSmootherTest.kt` | Existing | regression tests for visual smoothing/re-anchor policy | existing focused home for display smoothing policy | not a broad map integration test first; phase 0 should lock the exact policy seam | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/DisplayPoseSmoother.kt` | Existing | visual-only smoothing, outlier clamp, and large-gap re-anchor owner | this file already owns visual pose policy | not lifecycle manager, not overlay renderer, not repository | No |
| `mapposition.md` | Existing | user-facing/maintainer-facing map visual behavior contract | display smoothing semantics change here | not `PIPELINE.md` unless runtime wiring changes | No |

### 2.2E Module and API Surface

No new public or cross-module API is planned.

### 2.2F Scope Ownership and Lifetime

No new long-lived scope is planned.

### 2.2G Compatibility Shim Inventory

No compatibility shim is planned.

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| large-gap display-pose re-anchor threshold and policy | `feature/map-runtime/src/main/java/com/example/xcpro/map/DisplayPoseSmoother.kt` | `DisplayPoseSmootherTest.kt`, `mapposition.md` | this file already owns the related visual-only clamp/smoothing policy | No |

### 2.2I Stateless Object / Singleton Boundary

No new `object` or singleton is planned.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| live ownship resume-gap detection | Monotonic when available, else wall, matching existing `LocationFeedAdapter` rules | the gap decision must use the same time base as the visual feed |
| replay gap detection | Replay timestamp | preserves deterministic replay pose handling under the same rule |
| user-visible camera/overlay re-anchor decision | active feed time base only | visual policy must not compare monotonic and wall time |

Explicitly forbidden comparisons:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - existing map runtime/UI path only
- Primary cadence/gating sensor:
  - incoming ownship fix timestamps
- Hot-path latency budget:
  - no new loops
  - no new blocking work
  - one cheap timestamp-gap check per raw fix

### 2.4A Logging and Observability Contract

No new production logging is planned.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none planned beyond existing active time-base handling
  - replay should remain deterministic for the same sequence of timestamps and fixes

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| large timestamp gap between successive fixes | Degraded visual continuity | `DisplayPoseSmoother` | re-anchor to the latest fix instead of crawling from the stale pose | first fresh fix resets visual pose continuity | smoother tests |
| normal same-session noisy GNSS jump | Recoverable | `DisplayPoseSmoother` | keep existing outlier clamp behavior | unchanged clamp path | smoother tests |
| stale latest fix with no fresh update yet | Unavailable / stale visual input | existing display pose stale-fix rule | keep existing last pose behavior | unchanged stale-fix timeout path | existing behavior + regression review |

### 2.5B Identity and Model Creation Strategy

No IDs or new timestamps are created.

### 2.5C No-Op / Test Wiring Contract

No new `NoOp` or convenience path is planned.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| visual re-anchor logic drifts into lifecycle/overlay owners | responsibility matrix, map display pipeline rules | review + focused unit tests | `DisplayPoseSmoother.kt`, `DisplayPoseSmootherTest.kt` |
| long-gap reset breaks normal outlier clamp | display smoothing behavior contract | unit tests | `DisplayPoseSmootherTest.kt` |
| mixed time-base comparison sneaks into the fix | time-base rules | `enforceRules` + review | touched runtime files |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| ownship does not remain visibly stale after app resume | `MS-UX-03` | stale marker can crawl toward latest fix after a resume gap | first fresh fix re-anchors marker immediately | unit regression coverage now; artifact capture later if required | Phase 1 |
| lifecycle resume does not add duplicate cadence owners or loops | `MS-ENG-06` | current lifecycle sync contract | no lifecycle-owner duplication introduced | existing lifecycle tests + code review | Phase 1 |

## 3) Data Flow (Before -> After)

Before:

```text
FlightDataRepository
  -> MapScreenViewModel.mapLocation
  -> MapComposeEffects.updateLocationFromGPS(...)
  -> LocationManager.pushRawFix(...)
  -> DisplayPoseSmoother.tick(...)
  -> normal outlier clamp still applies after a large background gap
  -> blue ownship walks from stale pose toward latest fix
```

After:

```text
FlightDataRepository
  -> MapScreenViewModel.mapLocation
  -> MapComposeEffects.updateLocationFromGPS(...)
  -> LocationManager.pushRawFix(...)
  -> DisplayPoseSmoother.pushRawFix(...) detects a large same-timebase gap
  -> smoother re-anchors visual continuity before the next tick
  -> first fresh fix renders at the latest position
```

## 4) Implementation Phases

### Phase 0 - Seam/code pass and regression lock

- Goal:
  - Confirm the failing seam and lock it with focused tests before production edits.
- Files to change:
  - this plan
  - `feature/map/src/test/java/com/example/xcpro/map/DisplayPoseSmootherTest.kt`
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - large-gap re-anchor test
  - normal same-session outlier clamp preservation test confirmation
- Exit criteria:
  - the stale-crawl symptom is captured at the smoothing seam
  - the test proves the failure mode without changing repository or overlay ownership

Phase 0 seam/code pass outcome:
- `FlightDataRepository` is not the bug owner; it already remains the SSOT.
- `BlueLocationOverlay` is not the bug owner; it only renders the pose it is given.
- `MapLifecycleManager` exposes the symptom window on resume, but it is not the correct state owner for the fix.
- `DisplayPoseSmoother` is the correct fix owner because the bad behavior comes from visual clamp continuity being reused across a long timestamp gap.

### Phase 1 - Visual policy implementation

- Goal:
  - Add explicit large-gap re-anchor behavior in the display-pose owner.
- Files to change:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/DisplayPoseSmoother.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/DisplayPoseSmootherTest.kt`
  - `mapposition.md`
- Ownership/file split changes in this phase:
  - keep the policy in `DisplayPoseSmoother`
  - do not widen lifecycle or overlay APIs
- Tests to add/update:
  - first fresh fix after a large gap snaps to the latest location
  - normal in-session outlier clamp still limits unrealistic jumps
- Exit criteria:
  - stale resume-gap crawl is removed
  - existing clamp behavior remains for same-session noise
  - docs reflect the new visual policy

Phase 1 seam/code pass expectation:
- no `MapLifecycleManager` API widening
- no `DisplayPoseCoordinator` ownership move unless implementation requires a coordinator-only threshold anchor
- no new state in `MapStateStore`, `MapScreenViewModel`, or `BlueLocationOverlay`

### Phase 2 - Verification

- Goal:
  - prove the slice with targeted tests, then repo gates appropriate for the change.
- Files to change:
  - none unless verification uncovers a narrow follow-up
- Tests to add/update:
  - none beyond phase 0/1 unless a missed seam appears
- Exit criteria:
  - targeted smoother tests pass
  - required repo gates pass or any blocker is reported precisely

## 5) Test Plan

- Unit tests:
  - `DisplayPoseSmootherTest.largeGap_reanchorsToLatestFix`
  - existing outlier clamp test remains green
- Replay/regression tests:
  - deterministic same-input smoothing behavior remains covered at the unit seam
- UI/instrumentation tests (if needed):
  - not required for the first slice unless local/manual behavior contradicts the unit seam
- Degraded/failure-mode tests:
  - preserve stale-fix timeout behavior
- Boundary tests for removed bypasses:
  - none
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | `DisplayPoseSmootherTest.kt` |
| Time-base / replay / cadence | Fake/semi-synthetic timestamp gap coverage | `DisplayPoseSmootherTest.kt` |
| Persistence / settings / restore | Not applicable | none |
| Ownership move / bypass removal / API boundary | Boundary lock tests | plan + unchanged public seams |
| UI interaction / lifecycle | UI/integration only if needed | existing lifecycle tests remain unchanged unless new evidence says otherwise |
| Performance-sensitive path | no new loop, no new heavy work | code review + normal repo gates |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| threshold chosen too low and resets on ordinary sparse fixes | marker becomes too eager to snap | keep the rule narrow and test normal clamp behavior alongside the gap case | XCPro Team |
| fix belongs in coordinator rather than smoother | wrong owner and future drift | start with smoother seam only; move only if tests show smoother cannot own it cleanly | XCPro Team |
| stale-fix timeout behavior regresses | marker could show incorrect old data too long or disappear unexpectedly | keep stale-fix tests/behavior unchanged | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  - this is a narrow visual smoothing policy change, not a durable boundary move

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time-base handling remains explicit and same-base only
- Replay behavior remains deterministic
- No new lifecycle or overlay owner drift
- Impacted visual outcome is locked by regression coverage

## 8) Rollback Plan

- What can be reverted independently:
  - `DisplayPoseSmoother` re-anchor logic
  - regression tests and doc note
- Recovery steps if regression is detected:
  - revert the smoothing-policy slice only
  - restore prior clamp-only continuity behavior
