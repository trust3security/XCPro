# Coding Rules for Android App (Compose‑first, MVVM+UDF, SSOT, Modular)

**Goal:** Refactor and enforce a professional Android architecture. Produce maintainable, testable, offline‑first features. Keep it simple first; scale when pain appears.

---

## 1) Architecture at a Glance
- **UI:** Jetpack Compose only. No XML for new code.
- **Pattern:** **MVVM + UDF (unidirectional data flow)**.
- **SSOT:** Screen state lives in the **ViewModel**; persisted data SSOT lives in **Room**.
- **Layers:** UI → ViewModel → (UseCases optional) → Repository → DataSources (Local/Remote).
- **DI:** Hilt for everything.
- **Navigation:** Compose Navigation. Pass only IDs.
- **Modularity:** Start small; move to feature modules when build time or ownership demands it.

---

## 2) Module Structure
**Small/Medium (default):**
```
app/
core/
  common/        # result types, error mappers, utils
  ui/            # theme, atoms/molecules, design tokens
  network/       # Retrofit/Ktor, interceptors, serialization
  database/      # Room DB, DAOs, entities, migrations
feature/
  <feature-name>/
    ui/          # composables, nav entry points
    data/        # repository, DTOs, mappers
    domain/      # use-cases (only if logic warrants)
```
**Large:** split `core:*` modules and each feature as its own Gradle module. Features depend on core, not on each other. `app/` owns the NavHost.

Gradle:
- Use **version catalogs** (`libs.versions.toml`).
- Enable **R8**; turn on **minify** for release.
- Separate `debug`, `release`, optional `staging` with different `BuildConfig`s.

---

## 3) State, Events, Effects (UDF Contract)
Each screen defines a contract:
```kotlin
// Immutable screen state (SSOT in VM)
data class UiState(
    val isLoading: Boolean = false,
    val items: List<ItemUi> = emptyList(),
    val error: String? = null
)

// User intents
sealed interface UiEvent {
    data object Refresh : UiEvent
    data class Select(val id: String) : UiEvent
}

// One-off side effects (toasts, nav, share)
sealed interface UiEffect {
    data class Toast(val message: String) : UiEffect
}
```
Rules:
- `UiState` is **immutable**. Expose `StateFlow<UiState>` only.
- `UiEvent` is the only way UI talks to VM (`onEvent(event)`).
- `UiEffect` for non-stateful one-offs via `SharedFlow`.

---

## 4) ViewModel Rules
```kotlin
@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val repo: ItemsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _effects = MutableSharedFlow<UiEffect>()
    val effects: SharedFlow<UiEffect> = _effects

    init { load() }

    fun onEvent(e: UiEvent) = when (e) {
        UiEvent.Refresh -> load(force = true)
        is UiEvent.Select -> viewModelScope.launch {
            _effects.emit(UiEffect.Toast("Selected ${e.id}"))
        }
    }

    private fun load(force: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        repo.items(force).collect { result ->
            _state.update {
                when (result) {
                    is Result.Success -> it.copy(isLoading = false, items = result.value)
                    is Result.Error   -> it.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
```
Rules:
- No business logic in Composables. All in ViewModel/use-cases.
- Use **structured concurrency** (`viewModelScope`). No `GlobalScope`.
- Use `SavedStateHandle` for route args.

---

## 5) Compose UI Rules
- Compose screens are **pure functions** of `UiState`.
- Collect state with `collectAsStateWithLifecycle()`.
- Handle `UiEffect`s in a `LaunchedEffect(Unit)` block.
- Keep Composables small; hoist state; no side effects in previews.
- Lists use **Paging 3** when needed.
- Navigation: pass IDs only; fetch at destination.

Example route:
```kotlin
@Composable
fun ItemsRoute(
    vm: ItemsViewModel = hiltViewModel(),
    onOpenDetails: (String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            if (eff is UiEffect.Toast) {/* show snackbar */}
        }
    }

    ItemsScreen(
        state = state,
        onRefresh = { vm.onEvent(UiEvent.Refresh) },
        onSelect = { id -> vm.onEvent(UiEvent.Select(id)); onOpenDetails(id) }
    )
}
```

---

## 6) Repository & Data Rules (Offline‑first)
**Principles:**
- DB is the data SSOT. Repos expose **Flow** from Room. Network updates the DB.
- DTOs → Entities → UI models via mappers. Don’t leak DTOs to UI.

Result wrapper:
```kotlin
sealed interface Result<out T> {
    data class Success<T>(val value: T): Result<T>
    data class Error(val message: String, val cause: Throwable? = null): Result<Nothing>
}
```
Repository pattern:
```kotlin
class ItemsRepository @Inject constructor(
    private val api: ItemsApi,
    private val dao: ItemDao
) {
    fun items(force: Boolean): Flow<Result<List<ItemUi>>> = flow {
        if (force) refresh()
        emitAll(dao.observeAll()
            .map { list -> Result.Success(list.map { it.toUi() }) as Result<List<ItemUi>> }
        )
    }.catch { emit(Result.Error("Failed to load", it)) }

    suspend fun refresh() {
        val remote = api.fetchItems()
        dao.upsert(remote.map { it.toEntity() })
    }
}
```

Local/Remote:
- **Local:** Room + DataStore.
- **Remote:** Retrofit or Ktor + Kotlinx Serialization. Timeouts sane. Interceptors: auth, logging (debug only).
- **WorkManager** for deferred sync/background ops.

---

## 7) Domain (Use‑cases)
- Start without use-cases. Add them when logic piles up.
- Keep them tiny and composable.
```kotlin
class RefreshItems @Inject constructor(private val repo: ItemsRepository) {
    suspend operator fun invoke() = repo.refresh()
}
```

---

## 8) Dependency Injection (Hilt)
- One module per concern. No service locators.
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides fun okHttp(): OkHttpClient = OkHttpClient.Builder().build()
    @Provides fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
}
```

---

## 9) Navigation
- Central `NavHost` in `app`.
- Type-safe route helpers. Only primitive args.
- Deep links defined per feature entry.

---

## 10) Concurrency & Flow
- Prefer `StateFlow`/`SharedFlow` over LiveData.
- Cancellation‑safe coroutines. No blocking on main.
- Use backpressure‑safe operators (e.g., `debounce`, `distinctUntilChanged`).

---

## 11) Errors, Logging, and UX
- Map exceptions → domain errors early (repository).
- Surface user-friendly messages in `UiState`.
- Centralized logger (Timber). **No logs in release** except crash reporting.
- Show errors inline; retry actions obvious.

---

## 12) Testing
**Unit:** ViewModels, repositories, mappers, use-cases using `kotlinx-coroutines-test`, Turbine for Flows.

**DB:** Room with in‑memory DB; verify migrations.

**Network:** OkHttp `MockWebServer`/Ktor Mock; test both success and failure.

**UI:** Compose UI tests; use semantics; avoid sleep.

**Pyramid:** more unit tests than UI tests.

---

## 13) Quality Gates & Tooling
- **Detekt + Ktlint**: fail CI on violation.
- **Static analysis** on PRs; **min PR review 1**.
- **Danger/Spotless** optional.
- **Secrets** via Gradle secrets or env vars; never committed.
- **R8/MiniAPK**; Proguard rules curated.

---

## 14) Performance & Accessibility
- Avoid heavy work in composition; use `remember` wisely.
- Paging for large lists.
- Strict no main‑thread I/O.
- A11y: content descriptions, touch target sizes, contrast, dynamic type.

---

## 15) Data & Migrations
- Room migrations are mandatory for schema changes.
- Version the DB; include fallback only in debug builds.

---

## 16) Feature Flags & Config
- Use build‑time flags for env; remote flags behind a single interface.

---

## 17) Code Style (hard rules)
- No DTOs in UI layer. No business logic in Composables.
- Expose interfaces from repositories; keep impls internal.
- Public API minimal; prefer `internal` in feature modules.
- Name ViewModels `<Feature>ViewModel`, states `<Feature>UiState`.
- One screen → one VM. Shared VM only when truly shared state.

---

## 18) PR Checklist (agent must enforce)
- [ ] Follows module boundaries and dependencies.
- [ ] New screens have `UiState/UiEvent/UiEffect` contracts.
- [ ] Repos return `Flow<Result<…>>` (or clear contract) and map DTOs internally.
- [ ] No blocking I/O on main. Uses coroutines properly.
- [ ] DI bindings added and tested.
- [ ] Tests updated/added (VM, repo, mappers, UI if needed).
- [ ] Detekt/Ktlint clean. No new TODOs without an issue link.
- [ ] Strings and dimens externalized. A11y passes basics.
- [ ] Release build compiles with R8.

---

## 19) When to Scale Up
- Build time hurts or team ownership unclear → split by feature.
- Shared UI grows → `core:ui`/`designsystem` module.
- Complex business rules → introduce use-cases.

---

## 20) What NOT to do
- Don’t expose `MutableStateFlow` to UI.
- Don’t pass fat objects through navigation.
- Don’t couple features (no cross‑feature deps).
- Don’t leak networking or database classes outside data layer.
- Don’t add use-cases “just because”.

---

**Outcome:** A Compose‑first, offline‑capable, modular app where each feature is testable, stateful via SSOT, and easy to refactor.

