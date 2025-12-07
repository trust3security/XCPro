# CODING_POLICY.md — XC Pro (Android/Kotlin)

A blunt, practical standard for writing and reviewing XC Pro code. This is the source of truth for Codex/Copilot and humans.

---

## 0) Purpose
- Ship a **reliable, low‑latency digital variometer** using phone sensors.
- Keep the codebase **predictable, testable, and explainable** to future humans and AI.

**Non‑negotiables:** Kotlin + Jetpack Compose, MVVM + UDF, Hilt DI, Coroutines + Flow, Clean modules, SSOT, no deprecated APIs, no global mutable state.

---

## XCSoar Reference
- When you need to mirror XCSoar behavior, use the standalone checkout at `C:\Users\Asus\AndroidStudioProjects\XCSoar` (currently 7.44). Do not rely on any bundled `xcsoar-7.20` snapshot in this repo; that tree has been removed.
- Any existing code that still contains the literal string "xcsoar" must be cleaned up; do not introduce new references to "xcsoar" anywhere in the codebase.

---

## 1) Architecture
**Pattern:** Clean Architecture with MVVM + Unidirectional Data Flow (UDF)

- **UI (compose/)**: Composables are **stateless**; they render from `UiState` and dispatch `UiEvent`.
- **Presentation (viewmodel/)**: `ViewModel` collects domain `Flow`s, maps to `UiState`, handles `UiEvent` -> calls UseCases.
- **Domain (domain/)**: Pure Kotlin use cases and models; no Android deps; business rules incl. TE/vario math and filters. Keep use cases narrowly scoped (e.g., `CalculateTotalEnergy`, `ReadBaroSample`) and split orchestration into composed calls rather than monolithic managers.
- **Data (data/)**: Repositories implement interfaces; sources: Sensors, GNSS, Baro, Accel, IGC Replay, Persistence.
- **DI (di/)**: Hilt modules bind repos/use cases/sensor services.

**SSOT:** All time‑varying data (sensors, simulation, settings) flow through a **single StateFlow per concern** owned by the repository layer and exposed to domain/presentation.

---

## 2) Modules & Package Layout
Use a multi‑module Gradle project:
```
app/                       // Android app; wires screens + navigation
core/common/               // utils, result types, logging façade, time, units
core/ui/                   // design system, reusable Compose components
feature/map/               // map screen + overlays + gestures
feature/variometer/        // vario widgets, audio, UI
feature/sensors/           // sensor services (baro/accel/gnss), permissions
feature/simulation/        // IGC parser, replay engine, synthetic profiles
feature/te/                // TE & vertical speed domain logic, filters
feature/settings/          // preferences, device caps, calibration

// Internal package guidance
<feature>/ui/              // composables, UiState, UiEvent
<feature>/viewmodel/       // ViewModels
<feature>/domain/          // use cases, models (no Android)
<feature>/data/            // repositories, data sources
<feature>/di/              // Hilt bindings
```

---

## 3) Data Flow (UDF)
1. **Source** → Repository (`Flow<RawSample>`)
2. Repository → UseCase (fusion/filtering) → `Flow<DomainModel>`
3. ViewModel collects domain → maps to `UiState`
4. Composables render `UiState`, emit `UiEvent`
5. ViewModel handles `UiEvent` → UseCases/Repos

**Never** call Android sensors or I/O from composables.

### Flow Discipline
- Repositories own `MutableStateFlow` but expose only read-only `StateFlow`/`Flow` via `asStateFlow()` or adapters.
- ViewModels convert upstream streams with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initialValue)` (or an equivalent helper) before binding to `UiState`.
- Passing a mutable flow across architectural layers is a review blocker unless an `AI-NOTE` justifies the exception.

---


---

## File Size & Merge Discipline
- **Hard cap: 500 lines per Kotlin file.** When a class passes ~450 lines (e.g., `FlightDataCalculator.kt`), extract the math into smaller domain use cases (`CalculateFlightMetricsUseCase`, `WindRepository`, etc.) before submitting PRs. Keeping the calculator monolithic is the fastest way to recreate merge hell.
- **Extend metrics, not blobs.** Add new flight outputs by updating `FlightMetricsResult` and `FlightDisplaySnapshot`, then map them in `FlightDisplayMapper`. Do **not** bolt extra fields straight onto `FlightDataCalculator`; that guarantees multi-branch conflicts.
- **Rebase sensor branches daily.** For code under `feature/map/src/main/java/com/example/xcpro/sensors/`, run `git fetch origin && git rebase origin/main` before opening a PR or merging `main`. Resolve conflicts locally, then push; leaving the calculator conflicted in `featureA` is a policy violation.

Examples (blocking rule in reviews):

```kotlin
// BAD: exposes a mutable flow outside the class/layer
val snapshotFlow: MutableStateFlow<Snapshot> = MutableStateFlow(Snapshot())

// GOOD: keep it private and expose read-only
private val _snapshot = MutableStateFlow(Snapshot())
val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()
```

ViewModel helper (preferred): see Appendix “Snippets – Flow.inVm”.

## 4) Sensors & TE Computation
- **Fusion inputs:** Barometer (pressure altitude), Accelerometer (specific force), GNSS (vertical/ground speed). Optionally gyro for attitude assist if needed.
- **Outputs:** TE vario (m/s), filtered vertical speed, confidence/quality metrics.
- **Filters:** Prefer **discrete Kalman** (or complementary if latency budget is tight). Document tuning constants.
- **Sampling:** Backpressure via `callbackFlow` + `conflate()`; aim ≤ **25–50 ms** end‑to‑end latency to audio.
- **Threading:** Collect and fuse sensor feeds on `Dispatchers.Default` (or a named executor tuned for sensors); never block the main thread, and ensure scopes cancel promptly when the owner stops listening.
- **No ad‑hoc Main scopes:** Non‑UI types must not create `CoroutineScope(Dispatchers.Main + …)`. Inject dispatchers/scopes via DI (see DispatchersModule snippet) and collect sensors on `Default` or a named dispatcher.
- **WhileSubscribed:** When using `SharingStarted.WhileSubscribed`, ensure essential producers aren’t unintentionally stopped when the UI unsubscribes (document the choice with an `AI-NOTE`).
- **Samsung S22 Ultra specifics:** Use high‑rate sensor modes where supported; guard with capability checks; expose settings.
- **Simulation mode:** IGC parser feeds the same repository streams. No special‑case logic in UI; mode swap is a DI/config switch.

### Background Variometer Service (new requirement)
- The TE/Netto chain **must continue running when the UI is backgrounded** so audio, telemetry, and overlays stay continuous. Own `UnifiedSensorManager`, `FlightDataCalculator`, and `VarioAudioEngine` inside a dedicated **foreground service (or equivalent process-wide component)** instead of a composable/ViewModel scope.
- That service publishes the SSOT flows (e.g., `StateFlow<CompleteFlightData>`). Activities/fragments/composables only observe and issue commands through repository APIs or a bound-service adapter; lifecycle events from UI layers **must not directly start/stop sensors**.
- Justification: Android routinely pauses or kills activities; only a foreground service grants stable sensor + audio access and complies with OS rules. Pilots expect no gaps when they lock the screen, switch apps, or glance at notifications.

### Aircraft Polar Awareness (new requirement)
- Netto, speed-to-fly, and related cues **must use the user-selected glider polar** (`GliderRepository` + `PolarCalculator`) instead of hard-coded sink buckets. Inject a pure-domain "still-air sink provider" into TE helpers so compensation reflects aircraft model, ballast, and bug settings.
- Provide a logged fallback only when no polar/config exists, and annotate the fallback path with `// AI-NOTE: no polar configured`.
- Justification: Netto is air-mass climb (raw vertical speed minus aircraft sink). Using a generic curve introduces +/-0.5 m/s error and breaks trust; we already capture polar data, so the fusion layer is required to honor it.

### Flight Data Stack (new requirement)
- To keep the TE/variometer chain predictable, every feature touching flight data **must adhere to the four-layer stack below**. Each component owns a single responsibility and exposes only read-only flows to upstream consumers.
1. **SensorFusionRepository (feature/sensors/data)**  
   - Owns the mutable `StateFlow`s for fused GPS, baro, IMU, terrain queries, and replay feeds.  
   - Maintains history buffers (`FixedSampleAverageWindow`, circling history, wind samples) and timestamp bookkeeping.  
   - Handles throttling/backpressure and injects dispatchers; exposes read-only streams like `Flow<SensorSnapshot>` and `Flow<CompleteFlightDataRaw>`.  
   - No UI logic, no audio triggers, no `Log.d` except guarded debug hooks.
2. **CalculateFlightMetricsUseCase (feature/sensors/domain)**  
   - Pure Kotlin math: TE altitude, netto, thermal averages (TC30 / TC_AVG / T_AVG), wind vectors, L/D, QNH confidence.  
   - Receives fusion snapshots and deterministic dependencies (`StillAirSinkProvider`, `SimpleAglCalculator`, filters).  
   - Emits a `Flow<FlightMetrics>`; contains zero Android references and zero side effects beyond calculations.  
   - Contains extracted helpers like `FlightCalculationHelpers`, `CirclingDetector`, Kalman config; write tests here first.
3. **FlightDisplayMapper (feature/sensors/presentation)**  
   - Maps `FlightMetrics` + sensor context into `CompleteFlightData` / `RealTimeFlightData` structs for UI and cards.  
   - Owns display-only smoothing (clamps, decay constants, net-zero offsets) so presentation tweaks never touch the domain math.  
   - Responsible for unit conversions and labeling for downstream components; nothing else writes to `CompleteFlightData`.
4. **VarioAudioController (feature/variometer/audio)**  
   - Subscribes to the `FlightMetrics` flow and manages `VarioAudioEngine` lifecycle, gating, and mute/toggle events.  
   - Lives outside of repositories/use cases so domain logic remains side-effect free; integrates with the background service described above.

- **Data flow contract:** `SensorFusionRepository` ? `CalculateFlightMetricsUseCase` ? `FlightDisplayMapper` ? SSOT `StateFlow<CompleteFlightData>` observed by ViewModels/Compose; `VarioAudioController` taps into the same metrics flow but never feeds back into repositories.
- **Testing contract:** Record/replay fixtures at each boundary. A regression test must prove that TC30, TC_AVG, T_AVG, netto, and display vario values remain bit-for-bit identical after refactors.
- **DI contract:** Bind each component via Hilt modules; callers depend on interfaces so simulation/replay sources can be swapped without touching UI.


### Sensor Front-End (BasicComputer parity) — new requirement
- Introduce/maintain a dedicated **SensorFrontEnd** module (Kotlin) that mirrors XCSoar's BasicComputer role. It is the single place that:
  - Chooses nav altitude (baro QNH if calibrated & enabled, else GPS).
  - Derives IAS/TAS (pitot/dyn pressure if present; else GS+wind fallback) and flags validity.
  - Computes energy height and TE altitude = navAltitude + TAS² / (2g).
  - Computes gps/baro/pressure vario derivatives with timestamp gating; selects brutto/netto vario (prefer TE vario if available).
  - Exposes an immutable SensorSnapshot consumed by fusion, thermal tracking, and display mapping; no component re-computes these fundamentals downstream.
- Lifecycle hooks: resets on time warp and circling edge, so downstream averages don't drift.
- Test-first: add unit tests for altitude selection, TE altitude, vario derivation, and time-warp reset. Regression tests must replay IGC samples at 1× and assert TC30/TC_AVG parity with XCSoar.
- Reference implementation to mirror: C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Computer\BasicComputer.cpp (nav altitude, airspeed, TE altitude, vario) and ...GlideComputerAirData.cpp (CurrentThermal, circling reset). Do not rely on any bundled snapshots.
- Any future sensor/vario change that bypasses SensorFrontEnd is a review blocker; centralize, then fan out via the snapshot.


---

## 5) State, Errors, and Resilience
- Represent UI state with a single `data class UiState(...)` + sealed side‑effects (`OneShot` events).
- Model domain errors with sealed classes; surface user‑visible errors through `UiState`.
- All external calls return `Result<T>` or throw domain exceptions only inside domain; catch at boundaries.
- Map exceptions to sealed `Result<T>` types or explicit error sub-states at layer boundaries; ViewModels surface those errors through `UiState` rather than rethrowing.
- Offline‑first: cache last‑known calibration/settings; degrade gracefully if a sensor drops.

---

Typed errors only (no raw strings in repositories):

```kotlin
// Domain
sealed interface DomainError {
  data object NotFound : DomainError
  data class Io(val cause: Throwable) : DomainError
  data object PermissionDenied : DomainError
}

// ViewModel state carries a typed error that UI renders to text
data class UiState(
  val isLoading: Boolean = false,
  val data: Data? = null,
  val error: DomainError? = null,
)
```

Repositories should map exceptions to `DomainError`; UI converts `DomainError` to strings/messages at render time.

## 6) Comments & Documentation (for humans and AI)
**Mandatory.** Every class, public function, and non‑obvious block must explain **why**, not just what.

- **Header comment per file:** role in the architecture + invariants.
- **Rationale notes:** when using specific filters, constants, or threading decisions.
- **Event flow notes:** when pointer input/gesture consumption is involved.
- **Prompt‑hint marker:** Add `// AI-NOTE:` before rationale that helps future AI agents keep intent intact.

Example:
```kotlin
// AI-NOTE: We consume DOWN in the map gesture layer but DO NOT return early.
// This lets overlay composables receive the event (UDF), avoiding scattered logic.
```

---

## 7) Compose Rules
- Composables are **pure** render functions; no I/O, no long-running work.
- Use `remember`/`rememberSaveable` intentionally; prefer `derivedStateOf` for computed values.
- Provide `@Preview` for each screen and complex component.
- Hoist state; pass events as lambdas.
- Keep files under **500 lines** (prefer <= 350). Split when larger.
- Pointer input consumption: `consumePositionChange()` is deprecated. When touching pointer handlers, switch to `change.consume()` (or the newer helpers) and remove the warnings as part of the refactor.

### Live Telemetry & Recomposition Discipline
- **Collect lifecycle-aware**: UI layers must use `collectAsStateWithLifecycle()` (or `stateIn(viewModelScope, SharingStarted.WhileSubscribed)` inside the ViewModel) for every sensor/telemetry flow. No raw `collectAsState()` on `gpsFlow`, `orientationFlow`, etc.it keeps emitting while backgrounded, wastes battery, and re-triggers full-screen recompositions.
- **Push fast values to leaves**: High-frequency data (climb rate, airspeed, wind, audio state) must be read only by the composable that renders it. Do **not** plumb entire `FlightDataManager` or `RealTimeFlightData` through `MapScreen`; instead expose precise `StateFlow`s/`State` for each fast datum and pass them directly to the leaf widget. Large parents (Map screens, Card containers, MapView holders) should never recompose at telemetry rates.
- **Use `rememberUpdatedState` for tick loops**: Any `LaunchedEffect`/`Canvas`/animation loop that runs faster than 5?Hz (vario tones, needles, spark lines) must capture the latest value with `rememberUpdatedState` and read inside the loop. This keeps the effect scoped to lifecycle changes instead of every sensor tick.
- **Wrap derived formatting**: Whenever you format numbers (`UnitsFormatter`, string interpolation, smoothing, delta math) for display, wrap it in `remember(value) { derivedStateOf {  } }`. Only the text composable should recompose; heavy parents must remain stable.
- **Split heavyweight composables**: Files like `MapScreen`, `MapOverlayWidgets`, `MapUIWidgets`, etc., must be decomposed so that pointer gestures, MapView hosting, and card containers sit in their own composables. A change in the compass, slider, or single HUD number should *not* notify `AndroidView(MapView)` or the entire overlay tree.
- **Stabilize models**: Mark UI data classes that cross Compose boundaries (`RealTimeFlightData`, HUD models, widget state) as `@Immutable` (or expose individual primitives) so Compose skips recompositions when unrelated fields change.
- **Throttle debug logging**: Never leave per-pointer/per-tick logging in production composables (e.g., gesture logs inside `pointerInteropFilter`). Guard logs behind `BuildConfig.DEBUG` or remove themlogging inside hot loops causes GC churn and frame drops.
- **Prefer Canvas/animation clocks for gauges**: Needles, tapes, or sparklines should render inside a small composable that owns its draw scope. Drive them with `rememberInfiniteTransition`, `Animatable`, or a tight `LaunchedEffect`, keeping the outer layout stable.
- **Verification**: Use Layout Inspectors recomposition counters after each feature. The acceptance bar is only the leaf composable re-renders when telemetry ticks.

### Recommended Split Targets (for XC Pro)
| Type | Ideal Size | Hard Limit | Split Trigger |
|------|------------|------------|---------------|
| Composables / UI | <= 300 LOC | 500 LOC | Multiple @Preview blocks or branching paths |
| ViewModels | <= 250 LOC | 400 LOC | Handling more than 3 UiEvents |
| UseCases | <= 150 LOC | 200 LOC | Doing orchestration or complex coordination |
| Repository / Data Source | <= 300 LOC | 450 LOC | Owning more than one sensor/data adapter |
| Utility / Extension file | <= 200 LOC | 300 LOC | Mixing unrelated helpers |

---

## 8) Testing Strategy
- **Unit (domain):** TE math, filter correctness, unit conversions, edge cases (gusts, stick thermals).
- **Integration (data→domain):** Sensor streams → fusion → expected outputs under synthetic profiles.
- **UI tests:** State rendering, gesture paths (e.g., hamburger/variometer long‑press), regression for event routing.
- **Golden tests:** Snapshot crucial UI states.
- **Flow mocking:** Default to mocking repository/use-case flows in unit tests; use Turbine or equivalent to assert emissions so each layer is verifiable in isolation.
- **Determinism:** Simulation provides repeatable seeds; avoid `System.currentTimeMillis()` in domain.

---

## 9) Performance Budget
- End‑to‑end TE update to audio: **≤ 50 ms** typical.
- Avoid allocations in hot paths; prefer value classes and pre‑allocated buffers.
- Use `Dispatchers.Default` for math, `Main.immediate` for UI.

---

## 10) Logging & Telemetry
- Use a logging façade in `core/common` with levels: DEBUG (dev only), INFO, WARN, ERROR.
- No PII; logs must not include GPS traces unless user enables debug.
- Provide a toggleable on‑device diagnostics overlay in debug builds.

---

## 11) Security & Privacy
- Minimal permissions: sensors + location only when needed.
- Clearly separate analytics from app logic; off by default in dev.

---

## 12) Dependencies (Gradle, Kotlin DSL)
Keep versions centralized in `gradle/libs.versions.toml`.

Required (indicative):
- Kotlin, Coroutines, Kotlinx‑Datetime
- AndroidX: Core, Lifecycle, Navigation, Activity‑Compose
- Compose BOM (+ UI, Material3, Tooling)
- Hilt (dagger/hilt-android, hilt‑compiler)
- Accompanist (permissions if needed)
- Testing: JUnit, Turbine, MockK, Robolectric/Compose UI Test
- Detekt + ktlint; baseline checked in

---

## 13) Git & Branching
- Short‑lived feature branches; PRs under 400 lines if possible.
- Commit messages: `scope: concise change` with a one‑line reason in the body.
- No WIP PRs without failing tests annotated.

---

## 14) PR Checklist (must pass)
- [ ] Follows module boundaries; no Android deps in domain.
- [ ] ViewModels talk to UseCases/Repos only; Composables are stateless.
- [ ] **Comments added** (class/function/rationale). `AI-NOTE` present where intent matters.
- [ ] Unit tests for new logic; UI/integration tests for flows/gestures.
- [ ] No deprecated APIs; no global mutable state.
- [ ] Lint/detekt clean; Compose previews compile.
- [ ] Performance budget respected in hot paths.

---

- [ ] No public `MutableStateFlow`/`MutableSharedFlow`; only `StateFlow`/`Flow` exposed.
- [ ] ViewModel flows use `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initial)` or `Flow.inVm` helper.
- [ ] UseCases are single‑verb, ≤150 LOC, no Android deps; `operator fun invoke(...)` entrypoint.
- [ ] Long‑running/sensor collectors use `Dispatchers.Default` or a named dispatcher; none on Main.
- [ ] Errors are typed (sealed) or `Result<T>`; no raw string errors in repository → ViewModel paths.
- [ ] Flow tests use Turbine (repositories/use‑cases) and `runTest` + `MainDispatcherRule` (ViewModels).
- [ ] No ad‑hoc `CoroutineScope(Dispatchers.Main + …)` in non‑UI types; dispatchers provided via DI.

## Appendix – Enforcement & Snippets

**Enforcement**
- Pre‑commit guard (temporary, before a detekt rule). Blocks public `Mutable(State|Shared)Flow` outside tests:
  ```bash
  # .githooks/pre-commit
  #!/usr/bin/env bash
  rg -n --glob '!**/test/**' \
     '(^\s*public\s+.*Mutable(State|Shared)Flow)|(:\s*Mutable(State|Shared)Flow<[^>]+>\s*$)' \
     app dfcards-library core feature && {
    echo 'Refuse public Mutable(State|Shared)Flow. Expose StateFlow/Flow.' >&2; exit 1; }
  ```
- Detekt (long‑term): add a “forbidden types in public API” rule targeting
  `kotlinx.coroutines.flow.MutableStateFlow` and `MutableSharedFlow`.

**Snippets**
- Flow.inVm helper (use in all ViewModels):
  ```kotlin
  // core/common/FlowExt.kt
  package com.example.common
  import kotlinx.coroutines.CoroutineScope
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.SharingStarted
  import kotlinx.coroutines.flow.StateFlow
  import kotlinx.coroutines.flow.stateIn

  fun <T> Flow<T>.inVm(
      scope: CoroutineScope,
      initial: T,
      started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
  ): StateFlow<T> = stateIn(scope, started, initial)
  ```

- Dispatchers via DI (avoid ad‑hoc Main scopes):
  ```kotlin
  // core/common/DispatchersModule.kt
  package com.example.common.di
  import dagger.Module
  import dagger.Provides
  import dagger.hilt.InstallIn
  import dagger.hilt.components.SingletonComponent
  import javax.inject.Qualifier
  import kotlinx.coroutines.Dispatchers

  @Qualifier annotation class DefaultDispatcher
  @Qualifier annotation class IoDispatcher
  @Qualifier annotation class MainDispatcher

  @Module
  @InstallIn(SingletonComponent::class)
  object DispatchersModule {
    @DefaultDispatcher @Provides fun providesDefault() = Dispatchers.Default
    @IoDispatcher @Provides fun providesIo() = Dispatchers.IO
    @MainDispatcher @Provides fun providesMain() = Dispatchers.Main
  }
  ```

- Turbine test template for repository/use‑case flows:
  ```kotlin
  @Test fun repo_emits_updates() = runTest {
    val repo = createRepoUnderTest()
    repo.flow.test {
      assertEquals(initial, awaitItem())
      repo.triggerUpdate()
      assertEquals(updated, awaitItem())
      cancelAndIgnoreRemainingEvents()
    }
  }
  ```

## 15) Standard Generation Prompt (for Codex/Copilot)
Paste this at the top of tasks that need code:

```
You are a senior Android engineer building XC Pro.
Write Kotlin using Jetpack Compose, MVVM + UDF, Hilt DI, Coroutines + Flow, Clean Architecture.
Honor SSOT for sensor/GNSS/simulation streams. Implement TE fusion (baro+accel+GNSS) with Kalman/complementary smoothing.
Support Live and Simulation modes via the same repositories. UI is reactive to StateFlow; no I/O or logic in composables.
Provide full imports, DI bindings, Gradle deps, and **add rationale comments** (use `AI-NOTE` markers where intent matters).
Include unit/integration/UI tests where relevant. Optimize for Samsung S22 Ultra sensors.
```

---

## 16) Examples to Emulate
- Rationale comments near gesture consumption, filter tuning, latency choices.
- Repository exposes `StateFlow<TeState>`; ViewModel maps to `UiState` with one‑shot events for snackbars/audio.

---

## 17) Out of Bounds
- No direct sensor calls from UI; no service singletons; no blocking calls on main; no hidden state in objects.

---

## 18) Acceptance Criteria for Features
A feature is done when:
1. Code adheres to this policy and passes PR checklist.
2. Tests cover happy path + edge cases; CI green.
3. Comments explain **why**. Hot paths meet latency budget.
4. Simulation reproduces the scenario deterministically.

---



## 19) Reference Code Locations
- XCSoar reference code lives at `C:\Users\Asus\AndroidStudioProjects\XCSoar`; use that path whenever you need to inspect or cite XCSoar sources so instructions remain consistent across tasks.
- When syncing behavior, cite the external repo but scrub the literal string "xcsoar" from our source files; treat new occurrences as violations that must be deleted.

---

**This file is authoritative.** If a decision isn’t covered, pick the simplest option that preserves SSOT, testability, and low latency—then document the rationale with an `AI-NOTE`.



## 20) Telemetry Architecture (Blackboard pattern)
- Introduce a fusion/blackboard layer that owns mutable sensor state (pressure/altitude deltas, QNH jump detection, spike filtering, 30 s windows, circling flags, last IAS/TAS). Expose only immutable snapshots to domain use cases. Keep Android deps out; inject time/providers.
- Keep domain calculators pure and small (e.g., ThermalTracker, DisplayVarioSmoother, WindEvaluator); use cases orchestrate helpers and assemble results but do not manage rolling windows or sensor state.
- Presentation stays stateless: mappers format values; Compose/UI never performs flight math or I/O.
- Size guardrails: fusion layer �300 LOC, helpers �200 LOC, use cases �200 LOC; split before exceeding policy limits.
- Testing contract: unit tests for helpers (spike/QNH guards, TC30 stability, wind eval); golden/regression tests to prove vario/netto/TC30 outputs unchanged after refactors.

## 21) UI Data Cadence & Compose Throttling (pilot-facing)
- Split UI streams by metric: expose separate display-ready `StateFlow`s for vario, netto, altitude, wind, LD, mode; cards/overlays collect only what they render.
- Bucket + distinct: vario/netto to 0.1?m/s, altitude to 0.5?m, wind to 1?kt / 5�, LD to 0.1; apply `distinctUntilChangedBy` on buckets before Compose.
- Cadence targets (UI only): numbers 10�15?Hz; needles/charts 6�10?FPS; map icon/camera 15�20?Hz. Audio/tone remains full-rate (50�100?Hz) but must not drive recomposition.
- Throttle helpers: provide shared `throttleFrame(frameMs)`/`sample(frameMs)` in presentation; do throttling in ViewModel scope, not inside Composables.
- derivedStateOf at edges: format/bucket in `remember { derivedStateOf { � } }` keyed to bucketed values to avoid recomposition storms.
- Raw vs display flows: keep raw/high-rate streams for logging/audio/diagnostics; only surface display-ready (bucketed + throttled) flows to UI.
- Scopes: `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` for UI flows; no ad-hoc `CoroutineScope` creation in Composables.
- Instrumentation: run macrobench/frame-timing on mid-tier hardware; target <5% janky frames during climb/replay scenarios.
