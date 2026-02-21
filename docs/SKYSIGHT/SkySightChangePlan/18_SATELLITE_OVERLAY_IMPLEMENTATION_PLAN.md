# Satellite Overlay Implementation Plan (HighSight API -> XCPro)

Date: 2026-02-18
Owner: XCPro Team
Status: Draft (re-audited, 10-pass delta applied)

## Purpose

Define an architecture-compliant plan for satellite imagery overlay in XCPro using HighSight XYZ tiles, while preserving MVVM + UDF + SSOT and meeting compliance constraints.

Read first:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

## 0) Metadata

- Title: Satellite Overlay (HighSight-backed)
- Related API docs:
  - `https://api.highsight.dev/v1/reference`
  - `https://api.highsight.dev/v1/openapi.json`
- Issue/PR: TBD

### 0.0 Gap-Closure Research (2026-02-18)

This section closes the three open contract gaps with official sources and direct endpoint probes.

Gap 1: exact tile templates and parameters

- Satellite template:
  - `https://api.highsight.dev/v1/satellite/{z}/{x}/{y}?key={API_KEY}&date={YYYY/MM/DD/HHmm|latest}`
- Radar template:
  - `https://api.highsight.dev/v1/radar/{z}/{x}/{y}?key={API_KEY}&date={YYYY/MM/DD/HHmm|latest}&palette={slug?}`
- Common constraints:
  - zoom: `3..8` (out-of-range rejected)
  - date format: UTC `YYYY/MM/DD/HHmm`
  - cadence:
    - satellite: 10-minute buckets
    - radar: 5-minute buckets
- Notable behavior from docs:
  - `date` is optional but strongly recommended to avoid cross-tile inconsistency.
  - omitted `date` resolves to latest available imagery.
  - docs state tiles are served as 512px (retina).

Gap 2: auth method and error semantics

- Auth methods documented:
  - query param `key=...`
  - `Authorization` header alternative
- Direct probe results (verified):
  - missing key -> `401 Unauthorized` with body `API key is required`
  - invalid key -> `401 Unauthorized` with body `Unauthorized`
- Error semantics documented in OpenAPI:
  - `400` invalid tile params/date format
  - `429` usage limit exceeded (`5000 requests` in schema text)
  - `500` backend/provider error
- Note:
  - header scheme format (`Bearer` vs raw token) is not specified in the spec; keep query-key path as MVP default.

Gap 3: usage/compliance constraints

- OpenAPI usage policy states:
  - tiles must be streamed directly to end users
  - scraping/bulk download is not permitted
  - caching proxies/CDNs in front of tiles are not permitted
- OpenAPI examples also state attribution is required for certain plans.
- highsight.dev pricing confirms a public free plan quota baseline (`5,000 tiles/month`), relevant to quota/rate-limit UX.

### 0.1 Baseline Code Paths (Current State)

Forecast path (existing):

- `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`

Weather rain path (existing):

- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayPreferencesRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenState.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt`

### 0.2 Ten-Pass Deep Audit (Missed Items Added)

| Pass | Focus | Missed Item Added To This Plan |
|---|---|---|
| 1 | SSOT ownership | Existing weather key storage is rain-provider key storage; do not reuse one key slot for rain and satellite providers. |
| 2 | Runtime layering | `WeatherRainOverlay` anchors only to `BlueLocationOverlay.LAYER_ID`; satellite must define explicit anchor/z-order contract and tests. |
| 3 | Test coverage | No unit tests currently cover `MapOverlayManager` weather branches, `ObserveWeatherOverlayStateUseCase`, or `WeatherRadarMetadataRepository`. |
| 4 | Network/auth coupling | `SkySightMapLibreNetworkConfigurator` is one-shot global object; avoid satellite auth designs requiring dynamic per-user headers. |
| 5 | Error visibility | `MapOverlayManager.applyWeatherRainOverlay` logs generic failure only; satellite path needs observable error/status flow for user feedback. |
| 6 | Time/cadence | Weather rain path has no time model; satellite must add injected-clock cadence model (no `System.currentTimeMillis` in domain/use-case). |
| 7 | Lifecycle teardown | Satellite overlay must be added to runtime cleanup in `MapLifecycleManager.clearRuntimeOverlays`. |
| 8 | Map-ready reapply | Satellite needs reapply on map ready path (`MapScreenScaffoldInputs` currently re-applies forecast + rain only). |
| 9 | Metadata resilience | Rain metadata parsing/fallback and host validation need dedicated test coverage. |
| 10 | Docs drift | Satellite plan existed, but master SkySight docs still primarily describe forecast-only scope; update required. |

## 1) Scope

- Problem:
  - XCPro has forecast and rain overlays, but no satellite imagery overlay.
- Why now:
  - HighSight exposes documented XYZ tile APIs (contract-driven integration).
- In scope:
  - Satellite raster overlay MVP.
  - Optional radar mode only if same architecture contract remains valid.
  - Secure key handling and settings UI.
  - Style reload + map ready reapply + lifecycle cleanup integration.
- Out of scope:
  - Proxy caching, mirroring, or offline bulk tile download.
  - Backend gateway.
  - Lightning/vector products in MVP.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicate |
|---|---|---|---|
| rain enabled/opacity | existing rain prefs repo | `Flow` | UI-owned booleans/sliders |
| satellite enabled/mode/opacity/time | `WeatherImageryPreferencesRepository` (new) | `Flow` | runtime-only mutable config |
| rain metadata state | `WeatherRadarMetadataRepository` | in-memory state via use-case flow | repurposing satellite credential path |
| HighSight API key | `WeatherImageryApiKeyRepository` (new, secure) | `StateFlow<String?>` | BuildConfig/UI hardcoded key |
| satellite runtime config | `ObserveWeatherImageryRuntimeStateUseCase` (new) | `Flow<WeatherImageryRuntimeState>` | composable/manual assembly |
| satellite status (`OK`, `MISSING_KEY`, `AUTH`, `RATE_LIMIT`, `ERROR`) | imagery status use case/repo (new) | `StateFlow` | runtime-only hidden state |

Key decision:

- Keep rain and satellite credentials separate.
- Do not share one key slot across providers.

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> use case -> repository/runtime adapter`

Touched files/modules:

- `feature/map/src/main/java/com/example/xcpro/weather/imagery/*` (new)
- `feature/map/src/main/java/com/example/xcpro/map/WeatherSatelliteOverlay.kt` (new)
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenState.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettings*`
- `feature/map/src/test/java/com/example/xcpro/weather/*`

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Validation |
|---|---|---|---|
| rain-only runtime state assembly | `ObserveWeatherOverlayStateUseCase` | keep for rain + add imagery use case | unit tests |
| weather runtime overlays | `WeatherRainOverlay` only | rain + satellite overlays | runtime tests |
| credential scope | one weather key repo | imagery key repo only (rain is keyless metadata) | repo tests |
| weather overlay error surfacing | runtime log only | use-case/repo-backed status flow | unit tests + UI assertions |

### 2.2B Bypass Removal Plan (Mandatory)

| Current Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapWeatherOverlayEffects` -> `setWeatherRainOverlay(enabled, apiKey, opacity)` | provider-specific args from UI effect | keep rain call; add separate `setWeatherSatelliteOverlay(config)` fed by imagery VM | Phase 2 |
| `MapScreenScaffoldInputs` map-ready reapply | only forecast + rain reapply | add satellite reapply call | Phase 2 |

### 2.3 Time Base

| Value | Time Base | Reason |
|---|---|---|
| imagery selected timestamp | Wall UTC | provider contract uses UTC date buckets |
| auto-time cadence tick | Wall UTC | contract cadence (satellite 10m, radar 5m) |
| map animation/runtime | UI runtime | not authoritative business state |

Rules:

- No monotonic/wall mixing in imagery domain/use-case.
- Inject clock into imagery time selection logic.
- Serialize outgoing tile timestamps exactly as `YYYY/MM/DD/HHmm` in UTC.

### 2.4 Threading and Cadence

- Use-case/repository calculations: `Dispatchers.Default` or `Dispatchers.IO`.
- Map layer/source mutations: main/UI runtime.
- Recreate source only when URL template changes.
- Bucket to fixed intervals:
  - satellite: 10-minute buckets
  - radar: 5-minute buckets

### 2.5 Replay Determinism

- Satellite overlay state machine must be deterministic for identical inputs.
- Replay behavior unchanged unless explicitly enabling replay-synced imagery mode (out of MVP).

### 2.6 Enforcement Coverage

| Risk | Guard | File/Test |
|---|---|---|
| business logic in UI | architecture review + tests | imagery use-case tests |
| key reuse across providers | repo contract tests | rain key repo + imagery key repo tests |
| style reload/map-ready regressions | manager tests | `MapOverlayManager` weather tests |
| lifecycle leaks | cleanup tests | `MapLifecycleManager` weather cleanup tests |
| cadence/time bugs | clocked unit tests | imagery time-rounding tests |
| silent auth/rate-limit failures | status flow tests | imagery status mapper tests |

## 3) Data Flow (Before -> After)

Current rain flow:

`Weather settings -> WeatherOverlayPreferencesRepository + WeatherRadarMetadataRepository -> ObserveWeatherOverlayStateUseCase -> WeatherOverlayViewModel -> MapWeatherOverlayEffects -> MapOverlayManager.setWeatherRainOverlay -> WeatherRainOverlay`

Added satellite flow:

`Weather settings -> WeatherImageryPreferencesRepository + WeatherImageryApiKeyRepository -> ObserveWeatherImageryRuntimeStateUseCase -> WeatherImageryViewModel -> MapWeatherOverlayEffects -> MapOverlayManager.setWeatherSatelliteOverlay -> WeatherSatelliteOverlay`

Both flows are independent and share only map runtime manager coordination.

## 4) Implementation Phases

### Phase 0 - Contract + Compliance Lock

- Capture HighSight evidence pack:
  - OpenAPI snapshot.
  - Auth failure samples (`401` missing key, `401` invalid key).
  - Rate-limit sample (429) if available.
  - Tile URL pattern and required params:
    - satellite: `/v1/satellite/{z}/{x}/{y}`
    - radar: `/v1/radar/{z}/{x}/{y}`
    - query: `key`, `date`, optional radar `palette`
- Confirm legal constraints:
  - direct tile streaming only
  - no proxy cache/mirroring
  - capture customer-facing ToS/plan policy URL from account console for release evidence (public account pages are bot-protected).
- Confirm auth method for MVP:
  - default to query key (`key=`) for map tile URLs
  - do not require runtime global-header mutation via `SkySightMapLibreNetworkConfigurator`.

Exit:

- Evidence committed under `docs/integrations/highsight/evidence/`.

### Phase 1 - Imagery Domain/Repository Scaffolding

- Add `weather/imagery` package:
  - `WeatherImageryMode`
  - `WeatherImageryTimeSelection`
  - `WeatherImageryPreferencesRepository`
  - `WeatherImageryApiKeyRepository` (secure, fail-closed)
  - `WeatherImageryTileUrlBuilder`
  - `ObserveWeatherImageryRuntimeStateUseCase`
  - optional status mapper/use case
- Keep existing rain repositories unchanged.
- Add bucket rounding helpers with injected clock.
- Enforce URL allowlist (`https://api.highsight.dev/...` only).
- Encode concrete URL builders:
  - satellite builder with `{z}/{x}/{y}` + `key` + `date`
  - radar builder with optional `palette` enum:
    - `nws`, `bw`, `dark-sky`, `meteored`, `nexrad`, `original`, `rainbow-at-selex-si`, `titan`, `twc`, `universal-blue`

Tests:

- URL host/scheme/path enforcement.
- bucket rounding for 10m and 5m.
- preferences persistence.
- key repo mutation/storage availability behavior.
- parameter validation tests (zoom/date/palette builder-side guardrails).

### Phase 2 - Map Runtime Integration

- Add `WeatherSatelliteOverlay` runtime class.
- Extend map runtime state/manager:
  - `MapScreenState` add `weatherSatelliteOverlay`.
  - `MapOverlayManager` add:
    - `setWeatherSatelliteOverlay(...)`
    - `reapplyWeatherSatelliteOverlay()`
    - `clearWeatherSatelliteOverlay()`
  - `MapLifecycleManager.clearRuntimeOverlays()` include satellite cleanup.
  - `MapScreenScaffoldInputs` map-ready reapply includes satellite.
- Define deterministic layer order contract:
  - base map
  - satellite
  - rain (if both enabled)
  - forecast layers
  - airspace/task/traffic/ownship
- Define explicit anchor list for satellite overlay (not BlueLocation-only fallback).
- Use satellite map source/layer config aligned to contract:
  - min zoom `3`
  - max zoom `8`
  - tileSize `512` unless integration tests prove `256` is required for MapLibre raster handling.

Tests:

- manager weather branch tests: enable/disable/reapply/dedupe.
- style reload and map-ready reapply tests.
- cleanup tests for lifecycle disposal.

### Phase 3 - Settings/UI Wiring

- Add satellite controls in Weather settings:
  - satellite enable
  - mode selector (`SATELLITE`, optional `RADAR`)
  - opacity
  - auto/manual time selection (if MVP includes manual)
  - HighSight key input/status
- Keep rain controls and rain key in separate section.
- Keep production strings vendor-neutral.

Exit:

- Rain and satellite can be configured independently without key collision.

### Phase 4 - Hardening + Release Gates

- Add explicit handling for:
  - missing key
  - auth failures
  - rate limits
  - malformed contract response
- Map status mapping:
  - `401` -> auth/key error state
  - `429` -> quota/rate-limit state
  - `400` -> invalid request state (format/cadence/zoom)
  - `500` -> transient provider failure state
- Ensure no logs include full key/full URL with key.
- Add tile attribution in overlay source (plan-gated where required by provider plan terms/docs).
- Update docs:
  - `docs/ARCHITECTURE/PIPELINE.md` (new satellite flow)
  - SkySight docs index/readme references.

## 5) Test Plan

- Unit:
  - `WeatherImageryTileUrlBuilderTest`
  - `WeatherImageryTimeBucketingTest`
  - `WeatherImageryPreferencesRepositoryTest`
  - `WeatherImageryApiKeyRepositoryTest`
  - `ObserveWeatherImageryRuntimeStateUseCaseTest`
- Runtime/manager:
  - `MapOverlayManager` weather satellite branch tests.
  - style reload reapply tests.
  - map-ready reapply tests.
  - lifecycle cleanup tests.
- Failure/degraded:
  - missing key => disabled state + explicit status.
  - invalid key => `401` auth status surfaced.
  - missing key request => `401` status surfaced.
  - auth/rate-limit mapping => non-crashing surfaced error.
  - invalid date format/cadence => `400` mapped status.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When emulator/device is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| one-key-for-two-providers design | rain/satellite break each other | separate key repositories and UI sections |
| global network interceptor coupling | brittle auth design | avoid dynamic-header requirement; keep provider auth runtime-safe |
| silent runtime failures | poor diagnosability | add status flow and tests |
| overlay order conflicts | map readability issues | explicit anchor/z-order contract + tests |
| quota/rate-limit exhaustion | unstable UX | bucket cadence + status surfacing |
| legal policy breach | legal/commercial risk | no proxy cache/mirroring, direct tiles only |

## 7) Acceptance Gates

- Satellite overlay renders with valid HighSight key.
- Rain overlay behavior remains unchanged.
- Satellite credentials work independently of keyless rain metadata flow.
- Style reload + map-ready reapply + lifecycle cleanup all validated.
- No architecture rule violations.
- No secret leakage in logs.
- Required checks pass.

## 8) Rollback Plan

- Feature-gate or remove satellite runtime path only.
- Keep rain path and settings untouched.
- Keep imagery preferences/credentials stored but satellite rendering disabled.

## 9) Compliance Advice

1. Ship satellite-only first, add radar after stable telemetry and tests.
2. Keep provider naming out production UI/public APIs.
3. Use secure, user-provided keys (no hardcoded partner key in UX path).
4. Do not add proxy cache/mirroring layers.
5. Treat 401/429 handling as release gate, not backlog cleanup.

## 10) Research Sources

- HighSight API Reference:
  - `https://api.highsight.dev/v1/reference`
- HighSight OpenAPI contract:
  - `https://api.highsight.dev/v1/openapi.json`
- HighSight product site (pricing/features context):
  - `https://highsight.dev`
- HighSight official map examples repository:
  - `https://github.com/skysightsoaringweather/highsight-examples`

Direct probe notes (2026-02-18):

- Verified `401` on missing key and invalid key for:
  - `/v1/satellite/{z}/{x}/{y}`
  - `/v1/radar/{z}/{x}/{y}`
- Body examples:
  - missing key: `API key is required`
  - invalid key: `Unauthorized`
