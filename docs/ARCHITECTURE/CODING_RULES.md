
# CODING_RULES.md

## Purpose
This document defines **day-to-day coding rules** that enforce the architecture.

These rules exist to:
- Prevent architectural drift
- Keep AI output predictable
- Reduce review friction
- Make refactors safe
- Make simulator and replay modes trivial

If code violates this file, it is wrong -- even if it "works".

---

## 1. General Rules
- Clarity beats cleverness
- Explicit beats implicit
- Predictable beats concise
- Architecture beats convenience

---

## 1A. Enforcement (CI)

The following checks must fail the build when violated:
- Timebase: no System.currentTimeMillis, SystemClock, Date(), or Instant.now in domain or fusion logic.
- Units: no non-SI internal math contracts in domain/fusion/task/scoring/replay paths.
- DI: core pipeline components must be injected, not constructed inside managers.
- ViewModel purity: no SharedPreferences and no androidx.compose.ui.* types in ViewModels.
- ViewModel boundaries: no business geospatial math/policy (distance/radius/zone-entry logic) in ViewModels.
- Compose lifecycle: use collectAsStateWithLifecycle for UI state collection.
- Task UDF boundaries: no direct TaskManagerCoordinator mutation/query calls from Composables.
- UseCase boundary: use-case wrappers must not expose raw manager/controller handles that bypass use-case APIs.
- Manager state model: non-UI managers/domain classes must not use Compose runtime state (mutableStateOf, derivedStateOf, remember).
- Racing task runtime IDs: no `UUID.randomUUID()` in racing runtime initialization paths; use deterministic IDs.
- Task point-type mutation contract: no `Any?` bridge signatures in task VM/use-case/coordinator/manager mutation chain.
- Racing validity contract: no inline `waypoints.size >= 2` shortcuts in `RacingTaskManager` and `DefaultRacingTaskEngine`; use shared structure rules.
- Phase-1 canonical runtime contract: no `toSimpleRacingTask()` runtime bypass in `TaskNavigationController` or `TaskMapRenderRouter`.
- Phase-1 hydrate contract: coordinator persistence/switch paths must not use waypoint-only `initializeFromGenericWaypoints(...)`.
- Phase-1 replay contract: replay task helpers must not bypass canonical task flow via `toSimpleRacingTask()`.
- Phase-1 coordinator authority: `TaskManagerCoordinator` must not read manager `currentRacingTask` state directly.
- Phase-2 RT validity contract: manager/engine/replay validity must route through `RacingTaskStructureRules.validate(...)` (no `hasMinimumWaypoints(...)` shortcut authority).
- Phase-2 RT profile gate: default validation profile is `FAI_STRICT`; `XC_PRO_EXTENDED` is opt-in only.
- Vendor strings: no "xcsoar" or "XCSoar" literals in production Kotlin source.
- Encoding: no non-ASCII characters in production Kotlin source.
- App identity stability: `app/build.gradle.kts` must keep the approved
  `applicationId` and debug `applicationIdSuffix` unless an explicit migration
  plan is documented and approved.
- File size budget: Kotlin source files must be `<= 500` lines by default, with stricter hotspot caps enforced in `scripts/ci/enforce_rules.ps1`.
- Map visual SLO gate: map/overlay/replay/task-gesture runtime changes must pass
  impacted SLOs from `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
  with evidence defined in
  `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`.

---

### 1A.1 Static Analysis Expectations (Timebase)

In addition to runtime review, CI should include static checks that flag forbidden time calls
in domain/fusion paths (and ideally repo-wide, excluding tests):

- `System.currentTimeMillis`
- `SystemClock.*`
- `Date()`
- `Instant.now()`

Acceptable: injected `Clock`/`TimeSource` interfaces.

### 1A.2 Static Analysis Expectations (Units)

CI should include static checks that detect unit-contract drift in internal logic:

- Meter-labeled variables assigned from speed values (`*Meters = *Ms`).
- km-returning helpers compared directly to meter thresholds.
- Mixed-unit comparisons (`km`/`NM`/`mi`/`ft` against meter fields) in domain/fusion/task/replay logic.
- New internal `kmh`/`kt`/`mph` arithmetic outside explicit boundary adapters.

Acceptable:
- Explicit conversion at adapter/UI boundaries.
- SI-only values inside repositories/use-cases/engines after conversion.

### 1A.3 Automated Enforcement Entry Points

Enforcement artifacts:
- Aggregate Gradle gate: `./gradlew enforceRules` (includes `archGate`)
- Fast architecture gate: `./gradlew enforceArchitectureFast`
- Underlying local static gate: `scripts/arch_gate.py`
- CI workflow: `.github/workflows/quality-gates.yml`
- Canonical time abstraction for production code: `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
- DI binding anchor: `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

Local validation minimum:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When adding or tightening a gate rule:
- Document the rationale in this file first.
- Add the corresponding static check in `scripts/arch_gate.py` or `scripts/ci/enforce_rules.ps1`.
- Update affected architecture docs in the same change set.
- If immediate compliance is not possible, add a time-boxed entry in `KNOWN_DEVIATIONS.md`.

### 1A.4 Static Analysis Expectations (Line Budget)

CI should include static checks that fail when Kotlin files exceed allowed line budgets:

- Global default: `<= 500` lines per Kotlin file.
- Hotspot files may define stricter caps (`<= 450`, `<= 350`, `<= 320`, or `<= 300`) in `scripts/ci/enforce_rules.ps1`.
- Budget checks must name the file and configured cap in failure output.

Exception rule:
- If a file must temporarily exceed budget, record a time-boxed exception in `KNOWN_DEVIATIONS.md` (issue ID, owner, expiry, removal plan).

### 1A.5 Map Visual SLO Enforcement

When a change touches map interaction or map-rendered overlay behavior
in `feature/map` (including replay, weather, traffic, task-drag, or startup paths):

Required evidence:
- Impacted SLO IDs are explicitly listed (`MS-UX-*`, `MS-ENG-*`).
- Baseline + post-change metrics are attached for each impacted SLO.
- Validation follows the matrix in
  `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`.

Merge rule:
- Block merge on any impacted mandatory SLO miss.
- Exception only via time-boxed `KNOWN_DEVIATIONS.md` entry
  (issue ID, owner, expiry, mitigation, rollback plan).

## 1B. Exception Process

Exceptions require:
- An issue ID
- A named owner
- An expiry date
- A brief rationale

Exceptions must be listed in `KNOWN_DEVIATIONS.md` and reviewed before expiry.

---

## 1C. Test Gates

Required tests:
- Determinism: replay the same IGC twice and assert identical outputs.
- Timebase: unit tests fail if wall time affects replay logic.
- Units: unit tests fail when meter thresholds are evaluated using non-meter values.

## 2. Package Structure Rules

Code MUST follow this conceptual structure (names may vary):

```
data/
  sensors/
  repository/

domain/
  usecase/
  model/

ui/
  screen/
  viewmodel/
```

### Rules
- UI code never imports `data`
- Repositories never import `ui`
- UseCases never import Android UI classes
- Domain models contain no Android types
- Domain/use-case packages define boundary contracts (interfaces) for external I/O used by business logic.
- Data packages implement those contracts with adapters and bind them through DI.
- UseCases/engines depend on contracts, not concrete data adapters.

---

## 3. Naming Rules

### Classes
- Repositories: `XRepository`
- UseCases: `XUseCase`
- ViewModels: `XViewModel`
- UI state: `XUiState`

### Flows
- Private mutable: `_state`
- Public immutable: `state`

```kotlin
private val _state = MutableStateFlow(...)
val state: StateFlow<UiState> = _state
```

---

## 4. State Rules (Critical)

### Allowed
- Repositories may hold mutable state internally
- ViewModels may derive UI state
- UI reads state only

### Forbidden
- Mutable state exposed publicly
- State duplication across layers
- UI remembering derived values
- ViewModel caching domain data
- Compose runtime state in non-UI/domain/manager classes

If state exists in two places, one is wrong.

---

## 5. Flow & Coroutine Rules

### Flow
- Prefer `StateFlow` for UI state
- Prefer cold `Flow` for data streams
- No shared mutable state outside Flow
- Use `SharedFlow` for one-off events; do not model events as `StateFlow`

### Coroutines
- Use structured concurrency only
- Every coroutine must have an owner
- Jobs must be cancellable

### Forbidden
- `GlobalScope`
- `runBlocking`
- Manual thread management
- Blocking calls inside flows

---

## 6. Dispatcher Rules

Dispatchers must be explicit.

- Math / filtering: `Dispatchers.Default`
- File I/O: `Dispatchers.IO`
- UI: `Dispatchers.Main`

Forbidden:
- Heavy work on Main
- Implicit dispatcher assumptions

---

## 7. ViewModel Rules

Allowed:
- State transformation
- Intent handling
- Combining flows
- Mapping domain -> UI models

Forbidden:
- File I/O
- Sensor access
- Long-running loops
- Platform APIs
- Business math
- Exposing raw managers/controllers as public ViewModel handles
- Constructing domain/service collaborators directly when DI/factory is available

---

## 8. UI (Compose) Rules

Allowed:
- Frame-ticker loops for visual-only smoothing when scoped to `LaunchedEffect` and cancel on composition end
- Collect flows with lifecycle-aware APIs (`collectAsStateWithLifecycle` in Compose; `repeatOnLifecycle` elsewhere)
- Rendering
- Animations
- User input
- Visual effects

Forbidden:
- Unbounded loops outside the frame-ticker display exception
- Business logic
- State derivation
- Domain calculations
- Direct calls from Composables to manager/repository methods for domain mutations or business queries
- Reading manager internals as UI state (for example currentTask/currentLeg/currentAATTask)
- Collecting flows directly in Composables without lifecycle awareness
- Manual coroutine scopes
- Side effects outside `LaunchedEffect`

---

## 9. Repository Rules

Repositories:
- Own authoritative data (SSOT)
- Hide data sources
- Expose `Flow` / `StateFlow` only
- Own preference access (UI/ViewModel must not construct SharedPreferences or preference wrappers)
- Implement boundary contracts consumed by domain/use-case code when those boundaries are change-prone.

Forbidden:
- UI logic
- Android UI imports
- Caching UI state

## 9A. Adapter Depth Rules (Goldilocks)

Use the full ports/adapters pattern (domain contract + data adapter + DI) when any are true:
- Multiple data sources/backends exist or are expected.
- Live/replay/simulator/external-device implementations must be swappable.
- Offline/cache policy is business-relevant.
- Domain logic is non-trivial (state machines, filters, fusion, scoring, routing).
- Business logic needs unit tests without Android/DB/network/file stack.
- Provider/file-format/device boundaries are likely to change.

A simplified repository is acceptable when all are true:
- Single stable storage source.
- Trivial logic and low change risk.
- No practical need to swap implementation in tests/features.

Even in simplified cases:
- Keep Android/framework types out of domain/use-case code.
- Keep repositories DI-injected and behind MVVM/UDF/SSOT boundaries.

---

## 10. UseCase Rules

UseCases:
- Contain business logic
- Are pure where possible
- Are testable without Android

Forbidden:
- UI concerns
- Lifecycle awareness
- Platform APIs
- Hidden state
- Exposing raw managers/controllers that allow callsites to bypass use-case methods

---

## 11. Service Rules

Foreground services:
- Exist for OS constraints only
- Do not own state
- Do not perform business logic

Forbidden:
- Acting as SSOT
- Holding mutable domain state
- Becoming a "manager" god object

---

## 12. Error Handling Rules

- Errors are values
- No silent failures
- No swallowed exceptions
- Errors flow through the same pipeline as data

---

## 13. Logging Rules

- No logs in tight loops
- Do not log location data in release builds
- Logs must not be required for correctness

---

## 14. File Encoding Rules

- Keep source files ASCII or UTF-8 (no smart quotes or special punctuation)
- Avoid hidden non-ASCII characters in code, docs, and commit messages
- If a file is not UTF-8, edit with byte-safe tools and document why

---

## 15. Testing Rules

Must be testable without Android:
- Repositories (with fakes)
- UseCases
- ViewModels

No "hard to test" exceptions.

---

## 15A. Regression Resistance Rules

These rules make changes durable and reduce future rewrites.

- Any non-trivial refactor must include a written plan (phases, ownership, tests).
- SSOT ownership must be documented with a simple flow diagram or bullet flow.
- Time base must be explicit and enforced in code and tests (monotonic or replay only).
- Time-dependent refactors must add tests with a fake Clock and TestDispatcher.
- State machines must be explicit: list states and transitions in docs.
- Add regression tests for new behavior (unit tests first, replay tests when applicable).

Review red flags (reject changes):
- Business logic in UI code.
- Shared utilities across task types.
- Wall time used in navigation or scoring logic.
- Duplicate state owners for the same data.

---

## 16. AI / Codex Rules

Assume:
- AI forgets everything
- Prompts will be partial
- Code will be regenerated

Therefore:
- Code must be explicit
- Intent must be readable from files
- Architecture must enforce correctness

---

## 17. Time Base and Sensor Cadence Rules

These rules prevent hidden timing bugs in sensor fusion and replay.

- Live sensor math (deltas, validity windows) must use monotonic time
  (`SensorEvent.timestamp` or `Location.elapsedRealtimeNanos`).
- Wall time is for UI output, persistence, and user-entered data
  (example: manual wind). Never compare or subtract across time bases.
- Replay uses replay timestamps as the simulation clock. Do not mix with wall clock.
- Domain logic must use an injected Clock; do not call SystemClock/System.currentTimeMillis directly.
- Fusion loops that combine sensors of different cadences must advance only
  when the primary sensor updates (example: baro-gated vario loop). High-rate
  only ticks are forbidden if they would reuse stale samples.

---

## 18. SI Unit Discipline Rules

These rules prevent subtle "almost correct" behavior in competition-critical logic.

- Internal canonical units:
  - distance/altitude in meters
  - speed/wind/vario in m/s
  - acceleration in m/s^2
  - pressure in Pa/hPa with explicit labels
- Allowed non-SI values (km/h, knots, feet, NM, miles) are boundary-only.
- Convert once at ingress/egress; do not chain repeated ad-hoc conversions.
- Keep unit suffixes in APIs and fields (`*Meters`, `*Ms`, `*Kmh`, etc.).
- Never compare values unless they are the same unit.

XCPro-specific rationale:
- FAI task geometry (start/finish zones, turnpoint cylinders) and scoring are unit-sensitive.
- Typical constraints like 500 m cylinders and >= 3 km finish rings can be silently corrupted by mixed units.
- Mixed-unit bugs can pass casual testing while breaking start crossing, OZ intersection, STF, polar interpolation, near-miss filtering, and distance scoring.

---

## Final Rule

If a rule is unclear:
- Clarify it
- Write it down
- Enforce it

Unwritten rules do not exist.

