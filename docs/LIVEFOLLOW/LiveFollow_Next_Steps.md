# LiveFollow - What to do next

Date: 2026-03-17

## 1) Replace the current planning docs

Use these as the working baseline:
- `livefollow_v2.md`
- `XCPro_LiveFollow_Change_Plan_v4.md`
- `PIPELINE_LiveFollow_Addendum.md`

Do not keep coding from the old `livefollow.md` or the earlier v2/v3 execution plan without merging the corrections first.

## 2) Do not update repo-wide policy docs unless policy changes

Right now:
- do update `PIPELINE.md` when code wiring lands,
- do add the change plan before implementation,
- do keep a repo-local feature intent doc,
- do not change `AGENTS.md`, `ARCHITECTURE.md`, or `CODING_RULES.md` unless you are changing repo policy rather than implementing a feature.

## 3) First PR should be docs + contracts only

Target a small PR:
- add the change plan,
- add/replace the LiveFollow intent doc,
- add a draft `PIPELINE.md` section or TODO marker,
- add any baseline regression tests needed to lock current task/map/OGN behavior.

No feature wiring yet.

## 4) Then implement in this order

### PR 1 - Docs + baseline
- add docs above
- baseline tests

### PR 2 - Pure domain logic
- typed identity model
- source arbitration state machine
- stale/offline hysteresis logic
- replay side-effect block rules

### PR 3 - Repository and DI
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`
- backend adapters + DI

### PR 4 - UI / route wiring
- `feature:livefollow`
- pilot start/stop controls
- follower route / FCM entry
- thin map-render consumption

### PR 5 - Hardening and doc sync
- final `PIPELINE.md` update
- replay/privacy hardening
- quality rescore in PR description

## 5) What to tell Codex / the coding agent

Give it this brief:
- read `AGENTS.md` first,
- follow the phase model in `AGENT.md`,
- use `XCPro_LiveFollow_Change_Plan_v4.md` as the implementation contract,
- keep map runtime render-only,
- consume task snapshots from `MapTasksUseCase` / `TaskRenderSnapshot`,
- do not reintroduce manager escape hatches,
- do not bind watch mode to ordinary OGN overlay or SCIA trail preferences,
- use explicit SI units and explicit timebase handling,
- keep replay side-effect free,
- update `PIPELINE.md` in the same PR that changes wiring.

## 6) Verification you should require on every non-trivial PR

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 7) The one architectural mistake to avoid

Do not let LiveFollow become a disguised patch to `feature:map`.

If the feature only works when normal OGN overlay prefs are on, or if it needs raw task manager/coordinator internals, the design is already wrong.
