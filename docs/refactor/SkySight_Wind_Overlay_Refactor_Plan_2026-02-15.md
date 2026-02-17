# SkySight_Wind_Overlay_Refactor_Plan_2026-02-15.md

## Purpose

Implement SkySight wind overlays in XCPro without breaking MVVM/UDF/SSOT,
timebase rules, or map overlay stability.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/AGENT.md`
6. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

## 0) Metadata

- Title: SkySight Wind Overlay Refactor (MVP)
- Owner: XCPro Team
- Date: 2026-02-15
- Issue/PR: TBD
- Status: Draft

## 0.1 Research Summary (2026-02-15)

Validated by direct endpoint probing and tile payload inspection:

- Wind tiles are available on `edge.skysight.io` via:
  - `https://edge.skysight.io/{REGION}/{YYYYMMDD}/{HHmm}/wind/{z}/{x}/{y}/sfcwind0`
  - `https://edge.skysight.io/{REGION}/{YYYYMMDD}/{HHmm}/wind/{z}/{x}/{y}/bltopwind`
- Wind legends are available on `static2.skysight.io` via:
  - `.../legend/sfcwind0/1800.legend.json`
  - `.../legend/bltopwind/1800.legend.json`
  - tested legend slot behavior: `1800.legend.json` returns `200`; other
    slot values (`0600`, `1200`, etc.) return `403`
- Wind vector tile content differs from thermal/cloud:
  - wind layer geometry: `Point`
  - wind properties: `spd`, `dir`
  - thermal fill layer currently uses indexed polygons with `idx`
- Source-layer naming is mixed by parameter (critical):
  - `wstar_bsratio` tiles decode as layer `bsratio`
  - `zsfclcl`, `accrain`, `dwcrit` decode with time-based layer names (`0600`, `0800`, etc.)
  - `dwcrit` can include extra auxiliary layer (`0800_extra`)
  - wind tiles decode as layer names matching parameter ids (`sfcwind0`, `bltopwind`)
  - `wstar_bsratio` can contain both `HHmm` and `bsratio`, but `bsratio` is the
    consistently available thermal render layer across sampled regions/times
    (`WEST_US`, `EAST_US`, `EUROPE`, including `0600`)
- Zoom behavior differs from current hardcoded assumptions:
  - wind tiles return `200` beyond zoom 5 (validated at least through zoom 16)
  - current adapter hardcodes `minZoom=3`, `maxZoom=5` for all parameters
- Current auth flow is decoupled from overlay fetches:
  - settings "Test Login" validates `/api/auth`
  - forecast tile/legend/value calls do not consume that auth session in current code path
- Parameter catalog in runtime adapter is currently hardcoded, not fetched from
  live entitlement-aware metadata.
- Runtime vector fallback currently uses a magic default source-layer `"1800"`
  if source layer is missing, which is unsafe for most vector params.
- Overlay error handling is currently coarse:
  - `errorMessage != null` causes map overlay clear in map screen wiring
  - legend failures and tile failures are not separated into non-fatal vs fatal states
- Overlay fetch caching key currently includes time; with fixed `1800` legend
  policy this can trigger unnecessary legend refetch on every slot change.
- Current runtime renderer expects `VECTOR_INDEXED_FILL` and `idx`, so wind
  cannot render correctly with only parameter-list changes.

Current evidence references:

- `docs/integrations/skysight/evidence/parameters_success.json`
- `docs/integrations/skysight/evidence/tile_template_success.json`
- `docs/integrations/skysight/evidence/value_success.json`
- additional direct tile decode findings from live probe (2026-02-15)

## 0.2 External Technical References

- MapLibre style spec: symbol layer (icon-based vectors)
  - https://maplibre.org/maplibre-style-spec/layers/#symbol
- MapLibre style spec: circle layer (speed heat markers fallback)
  - https://maplibre.org/maplibre-style-spec/layers/#circle
- MapLibre style spec: expressions/data-driven styling
  - https://maplibre.org/maplibre-style-spec/expressions/

## 0.3 Deep-Dive Delta (2026-02-16)

Additional misses found during code re-scan (not fully captured in earlier draft):

- Current provider ports have hidden preference coupling:
  - `SkySightForecastProviderAdapter` reads `ForecastPreferencesRepository.currentPreferences()`
    inside `getTileSpec()` and `getLegend()`.
  - This creates non-explicit selection inputs (region/date/time) and can produce
    inconsistent tile/legend payloads if preferences mutate mid-fetch.
- Current UI error model is too coarse for safe flying UX:
  - `ForecastOverlayUiState` exposes one `errorMessage`.
  - `MapScreenContent` clears overlay when `errorMessage != null`.
  - Result: transient legend/network failures remove a previously valid overlay.
- Repository fetch/caching still assumes one combined payload:
  - fetch key includes time for both tile and legend.
  - with legend fixed-slot (`1800`) this causes unnecessary legend refetch per slot.
  - current key/cache path stores one `lastFetchError` and skips re-fetch while
    key is unchanged, so transient failures can remain "stuck" until slot/selection changes.
- Fetch ordering prevents non-fatal legend behavior today:
  - repository currently calls legend first, then tile.
  - if legend fails, tile is not attempted in that cycle.
- Missing legend fallback is unsafe for indexed fills:
  - renderer currently falls back to solid black when legend colors are absent.
  - keeping tile visible with null legend would otherwise produce misleading black overlays.
- Runtime branch-switch cleanup requirements are underspecified:
  - `ForecastRasterOverlay` currently has one vector fill layer only.
  - wind branch will need multiple layer IDs and strict cleanup when switching
    `VECTOR_INDEXED_FILL <-> VECTOR_WIND_POINTS`.
- Wind orientation behavior is not yet acceptance-locked:
  - no explicit contract for `iconRotationAlignment`/`iconPitchAlignment` and
    track-up vs north-up interaction.
- Point value UX for wind is underspecified:
  - current callout displays a single scalar; wind should define whether to show
    speed only or speed + direction.
- Legacy ID/default mismatch remains:
  - `DEFAULT_FORECAST_PARAMETER_ID` is `THERMAL` while SkySight catalog uses
    `wstar_bsratio`; selection currently relies on fallback, not explicit default.
- Evidence tooling/docs drift exists:
  - `docs/integrations/skysight/evidence/tile_template_success.json` and
    `scripts/integrations/capture_skysight_evidence.ps1` still encode
    `sourceLayer=HHmm` and `maxZoom=5`, which conflicts with decoded thermal/wind findings.
- Runtime tests for map overlay renderer are still missing:
  - no existing `ForecastRasterOverlay` test suite, so fill<->wind and style reload
    regressions are currently unguarded.
- Repository has duplicate state pipelines:
  - `overlayState` and `loadingOverlayState()` both exist with different fetch/error semantics.
  - current ViewModel uses `loadingOverlayState()`; this dual-path design is a drift risk.

## 1) Scope

- Problem statement:
  - XCPro currently supports SkySight thermal/cloud/rain-style overlays but not
    wind overlays as selectable/renderable map layers.
  - Existing vector overlay rendering path is fill-index based and incompatible
    with wind point vectors (`spd`, `dir`).
- Why now:
  - Wind is core pilot planning data and is explicitly expected in SkySight MVP
    scope docs.
- In scope:
  - Add wind parameters to provider catalog in XCPro.
  - Add wind tile URL/template resolution in SkySight adapter.
  - Make source-layer mapping explicit per parameter family (not one global rule).
  - Adjust zoom-range contract for wind (avoid hardcoded `maxZoom=5` limitation).
  - Add wind-specific vector rendering mode in map runtime.
  - Keep long-press point value working for wind speed.
  - Make provider contract inputs explicit (selection context passed through ports;
    no provider-side hidden reads of mutable preferences).
  - Fix/guard thermal source-layer mapping so wind work does not preserve hidden blank-layer regressions.
  - Decouple tile and legend failure behavior so legend errors do not hide valid tiles.
  - Ensure fetch retry behavior recovers from transient failures without requiring
    user parameter/time changes.
  - Avoid misleading fill rendering when legend is unavailable (no black-overlay fallback).
  - Separate fatal vs non-fatal overlay fetch states so transient failures do not
    clear a previously valid overlay.
  - Add deterministic source-layer fallback candidates for parameters with mixed layer names.
  - Validate schema consistency across at least `WEST_US`, `EAST_US`, and `EUROPE`
    before hard-locking render assumptions.
  - Keep parameter catalog extension architecture-ready for future entitlement-driven
    metadata sourcing (no UI hardcoding).
  - Correct legacy default parameter handling (`THERMAL` fallback path) to use
    real SkySight IDs directly.
  - Keep runtime cleanup exhaustive when switching render formats (fill <-> wind).
  - Consolidate overlay-state flow contract (single authoritative pipeline) to
    prevent divergent error/loading semantics.
  - Define wind arrow orientation behavior for both north-up and track-up map states.
  - Refresh stale evidence/script assumptions so plan/test artifacts match the real contract.
  - Add tests and update architecture pipeline docs where wiring changes.
- Out of scope:
  - Full SkySight tool suite (windgram, skew-t, route forecast, xsection).
  - Offline tile mirroring/proxy.
  - New backend services.
- User-visible impact:
  - Users can select wind parameters in Forecast overlays and see wind vectors
    on map for selected region/time.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Forecast enabled/opacity/region/parameter/time | `ForecastPreferencesRepository` | `Flow<ForecastPreferences>` | Compose `remember` mirrors for authoritative values |
| Available forecast parameters (including wind) | `ForecastCatalogPort` (`SkySightForecastProviderAdapter`) | `List<ForecastParameterMeta>` | Hardcoded UI-only parameter lists |
| Selected overlay render payload (legend + tile spec) | `ForecastOverlayRepository` | `Flow<ForecastOverlayUiState>` | Map runtime-local copies beyond last rendered state |
| Overlay fetch severity (fatal tile failure vs non-fatal legend warning) | `ForecastOverlayRepository` | `Flow<ForecastOverlayUiState>` typed status fields | Single coarse `errorMessage` that forces overlay clear |
| Forecast selection context used for fetches (region/date/time/parameter) | `ForecastOverlayRepository` resolved selection | explicit fetch arguments into provider ports | Adapter-side `currentPreferences()` reads during port calls |
| Wind tile render semantics (point-vector vs indexed-fill) | `ForecastTileSpec` fields from provider | `ForecastOverlayUiState.tileSpec` | Implicit runtime guesses from parameter id strings |
| Tile source-layer and property schema (`idx` vs `spd/dir`) | `SkySightForecastProviderAdapter` contract mapping | `ForecastTileSpec` optional schema fields | Runtime hardcoded per-parameter `when` branches |
| Tile zoom bounds per parameter | `SkySightForecastProviderAdapter` contract mapping | `ForecastTileSpec.minZoom/maxZoom` | Single hardcoded zoom range for all parameters |
| Source-layer fallback candidate order | `SkySightForecastProviderAdapter` | `ForecastTileSpec` candidate list | Runtime blind single-name assumption |
| Legend slot policy (`1800` fixed) | `SkySightForecastProviderAdapter` contract mapping | `ForecastLegendSpec` fetch behavior | Time-based legend slot assumptions |
| Point value at long-press location | `ForecastOverlayRepository.queryPointValue` via `ForecastValuePort` | `ForecastPointQueryResult` | UI-calculated values |
| Forecast auth check status in settings | `ForecastAuthRepository` + settings VM | `authConfirmation`, `authReturnCode` UI state | Treating auth success as guaranteed overlay availability |
| Provider parameter availability by entitlement/region | `ForecastCatalogPort` implementation | `List<ForecastParameterMeta>` | Hardcoded parameter list in UI or static assumptions |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-cases -> data adapter`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/forecast/*`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` (only if needed)
  - `feature/map/src/test/java/com/example/xcpro/forecast/*`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - Rendering behavior must stay in map runtime classes, not ViewModel/use-case.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Wind tile path resolution (`/wind/.../{param}`) | Not implemented | `SkySightForecastProviderAdapter` | Provider-specific contract belongs in adapter | Adapter unit tests for URL templates |
| Mixed source-layer resolution (time vs `bsratio` vs wind param id) | Implicit/incorrect global mapping | `SkySightForecastProviderAdapter` | Prevent blank overlays from wrong source-layer selection | Adapter unit tests for source-layer per parameter |
| Forecast fetch context ownership (region/date/time) | Hidden adapter reads from preferences | `ForecastOverlayRepository` passes explicit selection to ports | Prevent cross-flow mismatch and improve testability | Repository + adapter contract tests |
| Zoom-range policy for wind/fill params | Single fixed adapter value | `SkySightForecastProviderAdapter` | Prevent low-resolution wind overlay due incorrect `maxZoom` | Adapter tests for per-parameter zoom ranges |
| Forecast HTTP client ownership | Shared unqualified app `OkHttpClient` | Forecast-specific qualified client (if needed) | Isolate forecast timeout/retry behavior from ADS-B client tuning | DI binding tests + manual auth/legend/value smoke |
| Legend fetch cache strategy | Bundled with tile cache key (includes time) | `ForecastOverlayRepository` split caches by concern | Avoid unnecessary legend refetch on slot changes | Repository tests (slot change call counts) |
| Transient fetch recovery policy | Cached error blocks same-key retries | `ForecastOverlayRepository` retry/backoff-aware refresh policy | Prevent "stuck error until slot change" behavior | Repository tests with recover-after-failure scenario |
| Overlay error severity state | Single `errorMessage` string | `ForecastOverlayUiState` typed fatal/non-fatal status | Keep valid overlay visible during non-fatal failures | ViewModel/repository tests + map wiring smoke |
| Tile vs legend fetch orchestration | Sequential legend->tile in one try/catch | `ForecastOverlayRepository` independent tile+legend fetch paths | Ensure tile still updates when legend fails | Repository tests + manual smoke |
| Source-layer fallback selection | Not implemented | `ForecastRasterOverlay` uses spec-provided candidate order | Handle mixed source-layer payloads reliably across times/regions | Overlay runtime tests |
| Vector source-layer default safety | Runtime magic fallback | `ForecastTileSpec` contract (required source-layer for vector formats) | Avoid silent blank overlays from wrong default layer | model tests + runtime tests |
| Legend-missing fill behavior | Black fallback color expression | `ForecastRasterOverlay` keeps last good legend or suppresses fill update | Prevent misleading full-black overlays | Runtime tests |
| Wind vector draw policy (point + dir/spd) | Not implemented | `ForecastRasterOverlay` runtime | MapLibre-specific rendering belongs in UI/runtime controller | Runtime tests for layer/source creation |
| Wind orientation alignment policy (track-up/north-up) | Unspecified | `ForecastRasterOverlay` explicit symbol alignment properties | Prevent pilot-facing directional ambiguity | Runtime tests + manual flight-mode smoke |
| Wind parameter availability in sheet | Adapter lacked wind entries | `ForecastCatalogPort` result propagated by repository | Keep UI generic and state-driven | ViewModel/repository state tests |
| Entitlement-aware catalog extensibility | Static adapter metadata only | `SkySightForecastProviderAdapter` (staged static + future dynamic source seam) | Avoid refactor churn when live catalog endpoint is wired | adapter tests + doc note |
| Overlay state pipeline duplication | Two repository flows with drift risk | Single authoritative overlay flow contract | Prevent semantic divergence over time | Repository + ViewModel tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | No direct UI bypass identified for forecast overlay | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Forecast slot generation (`06:00-20:00`, region local day) | Wall (injected `Clock.nowWallMs`) | User forecast time selection is wall-calendar based |
| Auto-time "Now" updates | Wall (injected clock, minute tick) | UX sync to current forecast slot |
| Tile URL date/time parts | Wall-derived regional local time | Matches SkySight endpoint contract |
| Point query timestamp payload (`YYYYMMDDHHmm`) | Wall-derived regional local time | Matches SkySight point endpoint contract |

Forbidden comparisons retained:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - network/JSON parsing: `Dispatchers.IO` (via `@IoDispatcher`)
  - map rendering calls: main thread/UI runtime
- Primary cadence/gating:
  - minute-level auto-time tick from `ForecastOverlayRepository`
  - no per-frame network calls
  - legend/tile fetch cadence separated so fixed-slot legend policy does not
    re-fetch on every time-slot change
  - catalog/time-slot resolution must remain local/cache-backed; if live catalog
    is introduced later it must not trigger per-minute network calls
- Hot-path latency budget:
  - parameter/time switch should update visible layer within user-perceived
    interaction budget (< 1s typical network path, no UI jank)

### 2.5 Replay Determinism

- Deterministic for same input: Yes (forecast overlay is wall-time driven and
  separate from replay fusion math).
- Randomness used: No.
- Replay/live divergence rules:
  - Forecast overlay remains user-selected wall-time UI feature.
  - No replay-domain calculations depend on forecast overlay results.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Business logic drifts into UI | ARCHITECTURE.md, CODING_RULES.md UI rules | Review + unit tests | `ForecastOverlayRepositoryTest` |
| MapLibre types leak into ViewModel/domain | ARCHITECTURE.md dependency direction | enforceRules + review | changed files in `forecast/*` vs `map/*` |
| Hidden adapter preference reads cause selection mismatch | SSOT + explicit dependency contract | adapter/repository tests + review | `SkySightForecastProviderAdapterTest`, `ForecastOverlayRepositoryTest` |
| Cached same-key error prevents recovery | Runtime resiliency + UX stability | repository tests | `ForecastOverlayRepositoryTest` |
| Wrong source-layer mapping creates blank overlays | SSOT + contract correctness | unit test + manual smoke | `SkySightForecastProviderAdapterTest` |
| Single source-layer assumption fails for mixed-layer payloads | Contract correctness | runtime unit test + manual smoke | `ForecastRasterOverlay` tests |
| Wrong zoom bounds suppress valid tiles | Data contract correctness | unit test + manual smoke | `SkySightForecastProviderAdapterTest` |
| Wrong legend slot assumptions (`HHmm` instead of `1800`) | Legend fetch fails (`403`) and can suppress overlays | adapter tests + non-fatal legend error handling | `SkySightForecastProviderAdapterTest`, `ForecastOverlayRepositoryTest` |
| Coarse error state clears valid overlay on transient warning | UI/runtime stability | repository + UI wiring tests | `ForecastOverlayRepositoryTest`, `MapScreenContent` wiring checks |
| Legend-first fetch blocks tile refresh | Resiliency + fetch orchestration correctness | repository tests + review | `ForecastOverlayRepositoryTest` |
| Wind render path breaks existing fill overlays | Regression tests | unit test + manual smoke | new `ForecastRasterOverlay` tests |
| Wrong wind endpoint path or field mapping | Adapter contract tests | unit test | `SkySightForecastProviderAdapterTest` |
| Time slot drift from contract window | Existing + new time tests | unit test | `SkySightForecastProviderAdapterTest` |
| Legacy default parameter ID mismatch (`THERMAL`) | Incorrect initial selection semantics | unit test + review | `ForecastOverlayRepositoryTest`, `ForecastPreferencesRepositoryTest` |
| Missing legend causes misleading black fill | Safety/UX correctness | runtime tests + manual smoke | `ForecastRasterOverlay` tests |
| Evidence/script drift reintroduces wrong assumptions | Documentation/runtime mismatch | doc review + script update test smoke | `scripts/integrations/capture_skysight_evidence.ps1` |
| Duplicate overlay state flows diverge | SSOT contract drift | repository + ViewModel tests | `ForecastOverlayRepositoryTest`, `ForecastOverlayViewModelTest` |
| Hardcoded catalog diverges from entitlement reality | SSOT/data-contract correctness | review + adapter contract tests | `SkySightForecastProviderAdapterTest` + plan docs |

## 3) Data Flow (Before -> After)

Before:

`Forecast sheet -> ForecastOverlayViewModel -> ForecastOverlayRepository -> SkySight adapter -> tileSpec(format=VECTOR_INDEXED_FILL, idx polygons) -> ForecastRasterOverlay(FillLayer)`

After:

`Forecast sheet (includes wind params) -> ForecastOverlayViewModel -> ForecastOverlayRepository -> SkySight adapter (wind path + render hints) -> tileSpec(format=VECTOR_WIND_POINTS for wind) -> ForecastRasterOverlay(Symbol/Circle wind layer with dir/spd styling)`

## 4) Implementation Phases

### Phase 0 - Contract + Baseline

- Goal:
  - Lock wind endpoint contract and current behavior baseline.
- Files to change:
  - `docs/refactor/SkySight_Wind_Overlay_Refactor_Plan_2026-02-15.md` (this file)
  - optionally add wind evidence notes under `docs/integrations/skysight/evidence/`
  - optionally add decoded tile schema notes (layer names + property keys)
- Tests to add/update:
  - none in this phase
- Exit criteria:
  - contract and scope are explicit and reviewed.

### Phase 1 - Forecast Model and Adapter Contract

- Goal:
  - Add wind parameters and wind tile contract outputs in provider adapter.
- Planned changes:
  - refactor provider port contract so adapter does not read mutable preferences
    during fetch calls:
    - pass explicit selection context (region + resolved time slot/date parts)
      from repository into tile/legend/value fetch methods
    - keep adapter pure relative to method inputs for deterministic tests
  - extend `ForecastTileFormat` to represent wind-point rendering mode
  - add provider-neutral schema fields in `ForecastTileSpec` as needed:
    - source layer name
    - optional ordered source-layer fallback candidates
    - value property for indexed fills
    - direction/speed property names for wind vectors
  - make vector source-layer contract explicit:
    - vector tile specs must provide source-layer mapping/candidates
    - remove dependence on runtime magic default layer names
  - add SkySight wind parameters:
    - `sfcwind0` (surface wind)
    - `bltopwind` (boundary layer top wind)
  - keep catalog generation in adapter (not UI) and isolate it behind one method
    so future live metadata parsing can replace static list without UI/domain edits.
  - resolve wind tile URL format:
    - `.../{date}/{time}/wind/{z}/{x}/{y}/{param}`
  - resolve source-layer mapping explicitly:
    - `wstar_bsratio -> bsratio`
    - wind params -> source layer = parameter id
    - remaining indexed-fill params -> source layer = time part (`HHmm`)
  - define source-layer fallback candidates for mixed payloads:
    - thermal candidate order: `bsratio`, then `HHmm`
    - indexed-fill params with extras keep primary `HHmm`
  - resolve zoom bounds explicitly:
    - wind params use higher max zoom (validated > 5)
    - keep indexed-fill params on current safe range unless evidence expands them
  - resolve point-field mapping:
    - `sfcwind0 -> sfcwindspd`
    - `bltopwind -> bltopwindspd`
  - normalize default parameter handling to real provider IDs:
    - remove legacy dependence on `THERMAL` fallback matching
    - keep default selection deterministic with the catalog contract
  - prefer provider-provided legend unit metadata when available instead of
    static unit assumptions.
  - keep legend loading via `static2` legend path with fixed slot `1800`.
  - optionally introduce a forecast-qualified `OkHttpClient` binding to avoid
    coupling forecast endpoint behavior to ADS-B network module tuning.
  - update evidence artifacts and capture script defaults to avoid reintroducing
    stale layer/zoom assumptions during future contract refreshes.
- Candidate files:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastPorts.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
  - `scripts/integrations/capture_skysight_evidence.ps1`
- Tests:
  - adapter tests for wind URL templates and point-field mapping
  - adapter tests that fetch methods are deterministic from explicit inputs
    (no hidden preference reads)
  - adapter tests for source-layer mapping rules above
  - adapter tests for zoom-range mapping rules
  - adapter tests for parameter list inclusion/order
  - script/evidence test smoke for updated capture defaults (no stale
    sourceLayer/zoom hardcoding in generated evidence templates)
  - repository/preferences tests for real default parameter ID behavior
- Exit criteria:
  - repository state exposes wind params and wind tile specs without runtime
    rendering changes yet.

### Phase 2 - Map Runtime Wind Rendering

- Goal:
  - Render wind vector tiles correctly on map.
- Planned changes:
  - add wind render branch in `ForecastRasterOverlay`:
    - source: vector tile source
    - layer type: symbol (arrow icon rotated by `dir`) and/or circle color by `spd`
    - expression-based styling from feature properties (`dir`, `spd`) with legend-driven speed color mapping
    - per-layer opacity mapping for wind branch (icon/circle opacity)
    - robust style-reload behavior (re-register icon image before symbol layer)
    - deterministic wind layer ordering when using both symbol + circle sublayers
    - explicit symbol alignment contract for pilot orientation:
      - `iconRotationAlignment` and `iconPitchAlignment` chosen deliberately and
        verified under north-up and track-up camera states
  - preserve existing indexed-fill rendering for thermal/cloud/rain.
  - for indexed fills, replace black fallback behavior when legend is absent:
    - keep last-good legend for that parameter/date where possible, or
    - suppress fill layer update instead of painting unknown values black.
  - include explicit regression guard for thermal indexed-fill source-layer behavior.
  - define stable layer/source ID set for both branches and ensure full cleanup:
    - remove fill + wind symbol + wind circle layers when switching branches
    - no orphan layer/source IDs after style reload or parameter switches
  - ensure cleanup/reapply on style reload does not leak duplicate sources/layers,
    including both wind and fill branch IDs.
- Candidate files:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` (only if reapply behavior requires updates)
- Tests:
  - runtime unit tests around source/layer branch selection
  - runtime tests for branch switch cleanup (no leftover wind/fill layers)
  - runtime tests for orientation alignment configuration in wind symbol layer
  - regression check that non-wind overlays still render via fill path.
- Exit criteria:
  - wind overlays visible and switchable at runtime.

### Phase 3 - UI, UX, and Guardrails

- Goal:
  - Ensure wind options are usable and behavior is stable.
- Planned changes:
  - verify parameter chips show wind options (chip row already scrollable)
  - verify long-press point value works for wind params
  - keep defaults and fallback behavior safe if wind tile has no data (`204`)
  - repository behavior change: keep showing tile if legend fails (legend optional render aid, not hard dependency for tile visibility)
  - repository fetch orchestration change:
    - tile and legend fetched independently (tile fetch not blocked by legend failures)
    - add retry/backoff policy for transient failures on unchanged selection key
      so auto-time/manual users recover without forced slot/parameter changes
    - split cache entries for tile payload, legend payload, and last warnings/errors
  - replace single coarse error field with explicit severity:
    - fatal failures (no usable tile spec) may clear overlay
    - non-fatal warnings (legend fetch failure, transient refresh errors) keep
      last good overlay visible and surface warning text
  - collapse duplicate repository output paths into one authoritative overlay-state
    flow contract to eliminate semantic drift.
  - split tile vs legend caching so time-slot changes do not refetch fixed-slot
    legend unnecessarily.
  - gate long-press point query when overlay is disabled to avoid unnecessary
    gesture-side noise in normal map usage.
  - define wind callout display contract (speed only vs speed + direction) and
    keep it consistent with point-field availability.
  - optional UX correction: time label should reflect selected forecast region timezone
    (currently uses device timezone formatting).
  - settings UX note: make clear that auth check is connectivity/credential test and
    does not by itself guarantee forecast tile availability at selected region/time.
- Candidate files:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt` (only if labels/order/category tweaks needed)
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt` (only if no-data UX handling needed)
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayUseCases.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
- Tests:
  - repository/ViewModel state tests for parameter selection + no-data handling
  - repository tests for fatal vs non-fatal fetch outcomes and last-good overlay retention
  - UI wiring tests for map overlay clear behavior (clear only on fatal)
  - point query tests for disabled-overlay gating and wind callout semantics
- Exit criteria:
  - manual map checks pass for parameter changes and time changes.

### Phase 4 - Hardening, Docs, Verification

- Goal:
  - Finalize docs, tests, and required checks.
- Planned changes:
  - update forecast overlay section in `docs/ARCHITECTURE/PIPELINE.md`
    (remove stale "Track A fake provider" wording if still present)
  - optionally add wind evidence artifacts under
    `docs/integrations/skysight/evidence/` (headers + decoded contract notes)
  - update `scripts/integrations/capture_skysight_evidence.ps1` and related
    evidence JSON templates so documented defaults match validated source-layer
    and zoom behavior (no stale `HHmm`/`maxZoom=5` assumptions).
- Tests and commands:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - relevant instrumentation only if map/device path changed materially
- Exit criteria:
  - all required checks green and docs synced.

## 5) Test Plan

- Unit tests:
  - `SkySightForecastProviderAdapterTest`
    - wind parameter metadata exposed
    - wind tile URL templates use `/wind/{z}/{x}/{y}/{param}`
    - adapter fetch methods use explicit selection inputs (no hidden
      `currentPreferences()` coupling)
    - source-layer mapping:
      - `wstar_bsratio -> bsratio`
      - wind params -> param id
      - others -> `HHmm`
    - fixed legend slot policy (`1800`) for supported parameters
    - point value mapping returns wind speed fields
  - `ForecastOverlayRepositoryTest`
    - parameter select to wind emits correct tileSpec format
    - auto-time and selected-time behavior unchanged
    - split cache behavior:
      - legend fetch is not repeated on slot-only changes when legend slot is fixed
      - tile fetch still refreshes on slot change
    - tile fetch proceeds even when legend fetch fails in same cycle
    - retry/backoff behavior:
      - transient tile/legend failure retries on unchanged selection key
      - recovery emits usable state without requiring manual selection changes
    - tile remains visible when legend fetch fails but tile fetch succeeds
    - fatal vs non-fatal error state transitions are explicit and deterministic
    - single authoritative state flow semantics are verified (no drift between
      alternate repository state pipelines)
    - real default parameter ID behavior does not rely on legacy `THERMAL` fallback
  - `ForecastRasterOverlay` tests (new):
    - fill branch and wind branch layer/source creation do not conflict
    - style reapply keeps single source/layer set per branch
    - switching branches removes all obsolete layer IDs (fill + wind sublayers)
    - wind symbol alignment properties stay consistent for north-up/track-up use
    - fallback source-layer selection works for mixed-layer thermal payload
    - missing legend does not render full-black fallback fill
    - no implicit `"1800"` source-layer fallback for vector specs
- Replay/regression tests:
  - verify no replay-domain coupling introduced by forecast wind work
- UI/instrumentation tests (if needed):
  - map overlay smoke: enable overlay, switch to each wind parameter, verify no crash
  - if MapLibre layer mocking is unstable in JVM tests, add focused device/emulator
    instrumentation checks for wind layer render/reapply behavior.
  - region matrix smoke:
    - `WEST_US`, `EAST_US`, `EUROPE`
    - one thermal + one wind parameter each
- Degraded/failure-mode tests:
  - handle `204 No Content` tile responses without crash
  - legend fetch errors do not suppress otherwise valid tile rendering
  - transient fetch warnings do not clear last good overlay on map
  - transient same-key failures recover after retry without forcing parameter/time change
  - overlay-disabled long-press does not trigger point query network calls
- Boundary tests for removed bypasses:
  - N/A

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Source-layer mismatch (e.g., `wstar_bsratio` vs `HHmm`) | Overlay appears blank despite successful tile fetch | Explicit adapter mapping + tests for layer names | XCPro Team |
| Wrong thermal fallback precedence (`HHmm` over `bsratio`) | Thermal can disappear at slots/regions where `HHmm` layer is absent | Use `bsratio` as thermal primary and `HHmm` only as fallback | XCPro Team |
| Hardcoded `maxZoom=5` limits wind usability | Wind overlay too coarse or missing at normal map zooms | Parameter-specific zoom policy + evidence-backed defaults | XCPro Team |
| Hidden adapter preference reads | Tile/legend contract can desync from resolved UI selection | Pass explicit selection context into ports; ban provider-side mutable preference reads | XCPro Team |
| Same-key transient failure becomes sticky | Overlay can remain failed for entire slot until user changes selection | Add retry/backoff on unchanged keys and clear stale cached-error lock behavior | XCPro Team |
| Legend-first fetch ordering | Tile refresh blocked by legend outage | Fetch tile and legend independently; classify legend errors as non-fatal | XCPro Team |
| Wind direction semantic mismatch (`from` vs `to`) | Arrows appear reversed to pilots | Validate against known weather sample and adjust rotation rule once | XCPro Team |
| Wind unit ambiguity (`m/s` vs `kt`) | Incorrect legend/point labels | Confirm unit source from provider metadata/evidence before final label lock | XCPro Team |
| Auth success interpreted as data-availability guarantee | User confusion when valid auth but no data for region/time/tile | Add explicit UI messaging and no-data diagnostics | XCPro Team |
| Static catalog differs from user entitlement | User sees parameters that return no data or unsupported layers | Keep catalog seam isolated and add entitlement-aware follow-up task | XCPro Team |
| Legacy default parameter (`THERMAL`) fallback behavior | Non-obvious startup selection; brittle future catalog changes | Switch default to real provider ID and add regression tests | XCPro Team |
| Legacy runtime default source-layer (`"1800"`) masks contract bugs | Intermittent blank overlays and hard-to-debug behavior | Require explicit vector source-layer mapping from adapter and test it | XCPro Team |
| Null legend paints black indexed fill | Misleading map output under legend failures | Retain last legend or suppress fill update; remove black fallback expression | XCPro Team |
| Wind tile schema differs by region/run | No render or broken styling | Validate layer/property names from sample tiles per supported region; keep fallback handling | XCPro Team |
| Symbol density/performance issues | Map jank on low-end devices | Cap zoom range, tune icon size/overlap, keep style simple | XCPro Team |
| `204 No Content` prevalence at some z/x/y/time | User sees no wind overlay unexpectedly | Show clear no-data state and keep previous stable selection behavior | XCPro Team |
| Coarse error handling clears active overlay | In-flight transient failures blank map overlay | Typed fatal/non-fatal status + last-good overlay retention | XCPro Team |
| Dual repository overlay flows drift | Future behavior diverges between observers and tests | Keep one authoritative overlay state pipeline and remove/align duplicates | XCPro Team |
| Rate limit / backend constraints | Intermittent failures | Keep current repository fetch-key caching and avoid per-frame reloads | XCPro Team |
| Tile auth strategy changes upstream | Overlay fails suddenly | Keep auth/header evidence pack current; isolate host-specific request behavior | XCPro Team |
| Shared `OkHttpClient` tuning side effects | Forecast endpoints regress when ADS-B client settings change | Use qualified forecast client if coupling appears in tests/smoke | XCPro Team |
| Style reload loses custom icon/layers | Overlay disappears after style change | Re-register icon/source/layers in reapply path | XCPro Team |
| Evidence/script assumptions drift from real contract | New implementations regress to stale `HHmm`/`maxZoom=5` behavior | Keep capture script and evidence templates synchronized with validated findings | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains explicit and injected-clock driven
- Existing thermal/cloud/rain overlays still render
- Wind overlays render for supported region/time
- Provider fetch contract uses explicit selection inputs (no hidden preference reads)
- Non-fatal forecast errors do not clear previously valid overlays
- Transient same-selection failures recover via retry without manual slot/parameter changes
- Indexed-fill overlays never fall back to full-black rendering when legend is unavailable
- Point query returns wind speed values for wind parameters
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - wind parameters in adapter metadata
  - wind tile format/model additions
  - wind runtime rendering branch
- Recovery steps if regression detected:
  1. Disable wind parameters from adapter list.
  2. Keep existing indexed-fill path only.
  3. Re-run `enforceRules`, unit tests, and assemble checks.
  4. Re-enable behind a feature flag only after contract/test fixes.
