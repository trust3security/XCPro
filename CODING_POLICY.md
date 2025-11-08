# CODING_POLICY.md — XC Pro (Android/Kotlin)

A blunt, practical standard for writing and reviewing XC Pro code. This is the source of truth for Codex/Copilot and humans.

---

## 0) Purpose
- Ship a **reliable, low‑latency digital variometer** using phone sensors.
- Keep the codebase **predictable, testable, and explainable** to future humans and AI.

**Non‑negotiables:** Kotlin + Jetpack Compose, MVVM + UDF, Hilt DI, Coroutines + Flow, Clean modules, SSOT, no deprecated APIs, no global mutable state.

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

**This file is authoritative.** If a decision isn’t covered, pick the simplest option that preserves SSOT, testability, and low latency—then document the rationale with an `AI-NOTE`.
