# RainViewer Industry Hardening Plan (10/10 Target)

Date: 2026-02-20  
Owner: XCPro Team  
Status: Core hardening + compliance + Phase 5B map-runtime regression tranche complete; Phase 5C is the remaining tranche  
Issue/PR: TBD-RAINVIEWER-INDUSTRY-HARDENING  
Supersedes:
- `docs/RAINVIEWER/01_RAINVIEWER_RAIN_OVERLAY_REPLACEMENT_PLAN.md`
- `docs/RAINVIEWER/02_RAINVIEWER_BLEND_FADE_TRANSITION_PLAN.md`

## Purpose

Move the current RainViewer integration from "working" to "industry-grade":
- stronger realtime behavior
- safer failure handling
- clearer stale-data UX
- tighter compliance
- stronger automated verification

Read first:
1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
10. `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md`

Execution harness:
- This plan follows `docs/ARCHITECTURE/AGENT.md` phased execution and acceptance gates.

## 0) Deep Code Pass Findings (2026-02-20)

Current integration quality: solid baseline, not yet top tier.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Metadata polling is fixed cadence only (no adaptive backoff). | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt` | Can over-poll during 429/network failures and delay recovery policy. | Phase 1 |
| Medium | No explicit stale/live UX state on map for users. | `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | Users can interpret stale frames as live radar. | Phase 2 |
| Medium | Metadata refresh is not single-flight protected in repository. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Duplicate refresh calls are possible with multiple collectors/routes. | Phase 1 |
| Low | Weather settings state for advanced controls lacked explicit test coverage in the baseline. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt` | Settings drift risk during future UI refactors. | Phase 5 |
| Low | Vendor-specific text appears in production Weather settings copy. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | Conflicts with vendor-neutral architecture policy. | Phase 4 |
| Low | Core runtime paths lack focused tests (use-case frame selection, renderer behavior). | `feature/map/src/test/java/com/trust3/xcpro/weather/rain/*` | Lower change safety on highest-risk behavior. | Phase 5 |

Verification rerun during deep pass:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.weather.rain.*"`: PASS
- `./gradlew enforceRules`: PASS

Execution update (2026-02-20):
- Implemented now:
  - single-flight refresh guard + rapid-call dedupe in metadata repository.
  - adaptive metadata refresh interval policy by status.
  - runtime stale/fresh metadata model fields and derivation.
  - Weather settings stale/live and metadata status visibility.
  - provider-neutral Weather settings rain-overlay description copy.
  - expanded tests for refresh policy, stale policy, and use-case window selection.
- Verification rerun after implementation:
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS
- Final polish update (2026-02-20):
  - fixed stale-age/live-state freeze when metadata payload is unchanged by allowing periodic metadata ticker emissions and deriving freshness from injected wall clock each cycle.
  - renamed weather animation toggle semantics from `animatePastTenMinutes` to `animatePastWindow` across settings/use-case/repository/runtime models to match 10/20/30 minute options.
  - kept DataStore storage compatibility by preserving legacy persisted key name.
  - added regression test: `invoke_updatesMetadataAgeEvenWhenMetadataPayloadIsUnchanged`.
- Verification rerun after final polish:
  - `./gradlew :feature:map:testDebugUnitTest`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS
- Tranche B implementation update (2026-02-20):
  - implemented 30-minute playback quality filtering: oldest boundary frame is excluded for dense 30m windows, with deterministic sparse-window fallback.
  - implemented window-aware transition tuning (10m/20m/30m) to reduce blend load in longer windows.
  - completed metadata hardening items in runtime code path: weather-specific HTTP client qualifier, version-warning compatibility parse, monotonic dedupe gate, locale-safe host normalization, explicit content-age modeling, conditional request validators, and 304 success path.
  - completed runtime stale-render policy and churn controls: transient stale semantics, status-only render suppression, and stale overlay dimming.
  - expanded weather test coverage for repository semantics and transition/frame-quality policies.
- Verification rerun after tranche B:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.weather.rain.*"`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS
- Phase 2C execution update (2026-02-20):
  - exposed advanced weather preference controls in Weather settings:
    - frame source mode (`LATEST` / `MANUAL`)
    - manual frame index selection
    - render options (`smooth`, `snow`)
  - added settings-to-runtime mapping regression coverage in weather use-case tests.

Second deep-pass delta findings (2026-02-20):
- Status: mostly implemented; remaining item is attribution/compliance documentation (Phase 4B).

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| High | 30-minute playback still includes oldest boundary frame without quality guard. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt` | Blurred/low-value boundary frame can persist in ping-pong cycle; largest UX complaint source in 30m mode. | Phase 3A |
| High | Transition duration is not window-aware (10/20/30 share one policy). | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`, `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettings.kt` | 30m mode can look muddy at fast/balanced/smooth due to high blend fraction. | Phase 3B |
| Medium | Stale policy marks non-OK as stale immediately even with fresh last-success metadata. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettings.kt` | "Stale" flicker during short transient failures/rate-limit events. | Phase 2B |
| Medium | Provider attribution exception is not yet explicitly documented against vendor-neutral policy. | `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`, `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | Compliance ambiguity for legally required attribution string. | Phase 4B |
| Low | Overlay re-apply can trigger on status-only changes with unchanged frame/opacity/duration. | `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` | Avoidable render churn; low risk but unnecessary work. | Phase 3C |

Modeled impact estimates from current runtime behavior:
- Phase 3A (frame quality filtering): removes blurred-frame exposure by ~16.7% to ~33.3% of 30m displayed frames when one bad frame exists in a 4-frame cycle.
- Phase 3B (window-aware transition tuning): estimated blend-time reduction of ~35% to ~66% depending on speed/quality combination in 30m mode.
- Combined 3A + 3B: estimated perceived blur-load reduction of ~62% to ~73% in problematic 30m scenarios.

Third deep-pass delta findings (2026-02-20):
- Status: mostly implemented; remaining item is map-runtime weather test coverage (Phase 5B).

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Animation ticker is keyed to full preferences object; any preference write (for example opacity) restarts tick to zero. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt` | Playback jumps/restarts during live setting changes; unnecessary animation jitter. | Phase 3D |
| Medium | Weather metadata repository uses unqualified shared `OkHttpClient` currently provided from ADS-B network module. | `feature/map/src/main/java/com/trust3/xcpro/di/AdsbNetworkModule.kt`, `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Cross-feature coupling risk; ADS-B transport changes can unintentionally affect RainViewer metadata fetch behavior. | Phase 1B |
| Medium | Metadata version handling is strict `2.x` hard-fail. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Provider major-version bump can hard-break overlay even if payload is otherwise compatible. | Phase 1C |
| Low | Refresh rapid-dedupe uses wall-clock attempt time. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | System wall-clock rewind can over-suppress refresh attempts until wall time catches up. | Phase 1D |
| Low | Phase 2C controls are now exposed, but they still need dedicated ViewModel/Compose regression coverage. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt` | Future UI changes can regress manual-frame/render-toggle semantics without failing fast. | Phase 5C |
| Low | No focused tests for map-side weather runtime glue (`MapWeatherOverlayEffects`, `MapOverlayManager` weather branch, `WeatherRainOverlay`). | `feature/map/src/test/java/com/trust3/xcpro` | Reduced regression confidence on style reload/reapply/render-churn behavior. | Phase 5B |

Fourth deep-pass delta findings (2026-02-20):
- Status: mostly implemented; remaining item is in-map confidence signaling (Phase 2D).

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Host normalization in metadata repository uses locale-sensitive lowercase without explicit locale. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Locale edge cases (for example Turkish locale casing) can cause avoidable host normalization mismatches. | Phase 1F |
| Medium | Stale/live status is visible in Weather settings only; no in-map user signal when radar is stale/error. | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | Pilot can view stale radar on map without immediate confidence cue. | Phase 2D |
| Medium | Freshness age is derived from `lastSuccessfulFetchWallMs` update logic keyed only to metadata `generated`, not general successful fetch/content change. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`, `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt` | Age semantics can drift from operator expectation ("last fetch" vs "last generated change"), causing interpretation ambiguity. | Phase 1E |
| Low | Metadata frame list is sorted but not deduplicated. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Duplicate frame entries can increase animation redundancy and reduce visual quality in edge payloads. | Phase 3E |
| Low | Runtime state does not expose explicit selected-frame age metric (only formatted UTC timestamp and metadata freshness age). | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | Harder to reason about "how old is the visible radar image" under degraded metadata conditions. | Phase 2E |

Fifth deep-pass delta findings (2026-02-20):
- Status: implemented.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| High | Map render path ignores stale/error semantics; overlay keeps rendering full-strength when metadata is stale/error as long as a cached frame exists. | `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt` | Users can continue seeing stale radar as authoritative map content, especially without opening Weather settings. | Phase 2F |
| Medium | Metadata fetch path has no conditional request/cache validators (`ETag`/`If-Modified-Since`) or explicit HTTP cache policy. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`, `feature/map/src/main/java/com/trust3/xcpro/di/AdsbNetworkModule.kt` | Extra network/battery usage and avoidable provider load during frequent polling windows. | Phase 1G |

Sixth deep-pass delta findings (2026-02-20):
- Status: partially implemented; remaining item is Phase 5C UI-level regression coverage for settings controls.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Low | Manual-frame slider remained visible in Weather settings even when frame source was `LATEST` or cycle mode was enabled. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | Avoidable UI clutter weakened discoverability of active control path. | Phase 2C-Polish |
| Medium | No dedicated UI-level regression tests exist for newly exposed Phase 2C settings controls (`frameMode`, manual index, `smooth`, `snow`). | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt`, `feature/map/src/test/java/com/trust3/xcpro` | Control-surface regressions can slip through despite use-case tests passing. | Phase 5C |

Phase 2C-Polish execution update (2026-02-20):
- implemented conditional visibility for manual-frame controls:
  - manual slider is shown only when overlay is enabled, cycle mode is off, frame mode is `MANUAL`, and frames are available.
  - frame-source chips are visible but disabled while cycle mode is enabled to avoid non-actionable interaction.
- added settings-visibility policy tests:
  - `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreenPolicyTest.kt`

Seventh deep-pass delta findings (2026-02-20, Phase 2D re-pass):
- Status: implemented (Phase 2D confidence signal + mapping + tests are in).

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | In-map weather confidence indicator is still missing while rain overlay is active. | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt` | Users can still misread stale/error radar as live without opening Weather settings. | Phase 2D |
| Medium | No compose/UI tests exist for weather confidence state mapping on map. | `feature/map/src/test/java/com/trust3/xcpro/map/ui/` | Phase 2D regressions can ship undetected even when weather use-case tests pass. | Phase 2D + Phase 5C |
| Low | Weather status text mapping is private to settings UI and not reusable by map UI. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | High drift risk between settings and map confidence semantics. | Phase 2D |
| Low | Top-center map UI is already occupied by GPS/forecast chips; weather-chip placement policy is not yet specified. | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt` | New confidence chip can overlap or clutter critical map overlays. | Phase 2D |
| Low | Weather runtime state is currently consumed only by side-effect wiring, not shared map presentation state. | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt` | Adding Phase 2D UI naively can duplicate collectors/mapping and weaken SSOT clarity. | Phase 2D |

Phase 2D implementation recommendations (2026-02-20 re-pass):
- Hoist weather overlay runtime state once in map root wiring and pass it to both:
  - map overlay apply effects (`MapWeatherOverlayEffects`)
  - map confidence chip host in map UI.
- Add a minimal `WeatherMapConfidenceChip` for rain-overlay-on state with deterministic mapping:
  - `Live`: `metadataStale=false` and status `OK`.
  - `Stale`: `metadataStale=true` and last usable frame exists.
  - `Error`: no usable frame or hard error status (`NO_METADATA`, `NO_FRAMES`, `PARSE_ERROR`, sustained network failure path).
- Extract status label mapping from settings-only private function into shared weather UI mapping utility to keep semantics aligned.
- Place chip at `Alignment.TopEnd` with fixed offsets to avoid collisions with:
  - top-center GPS banner
  - top-center forecast query/status chips.
- Add tests:
  - policy unit tests for status-to-chip mapping.
  - compose tests for visibility/label/state combinations (overlay off/on, live/stale/error).

Modeled UX impact from completing these Phase 2D items:
- perceived trust/readability improvement: ~20% to ~30%.
- stale-as-live interpretation risk reduction in map-first usage: ~30% to ~45%.

Phase 2D execution update (2026-02-20):
- implemented in-map weather confidence signaling:
  - added `WeatherMapConfidenceChip` (`Rain Live` / `Rain Stale` / `Rain Error`) in map UI.
  - chip is shown only when rain overlay is enabled.
  - chip is placed top-end to avoid top-center GPS/forecast chip collisions.
- implemented shared weather status mapping utilities:
  - added shared `weatherRadarStatusLabel`.
  - added `resolveWeatherMapConfidenceState` for deterministic map-chip state mapping.
  - removed settings-only private status label mapping and switched Weather settings to the shared mapper.
- added regression coverage:
  - policy tests: `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherOverlayUiMappingTest.kt`
  - compose tests: `feature/map/src/test/java/com/trust3/xcpro/map/ui/WeatherMapConfidenceChipTest.kt`
- targeted verification:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.weather.rain.WeatherOverlayUiMappingTest" --tests "com.trust3.xcpro.map.ui.WeatherMapConfidenceChipTest"`: PASS

Eighth deep-pass delta findings (2026-02-20, Phase 4B re-pass):
- Status: not implemented; attribution/policy decision remains open.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Vendor-neutrality policy conflict is still unresolved for required provider literals in production code (`rainviewer.com` host allowlists, metadata endpoint, tile attribution). | `docs/ARCHITECTURE/ARCHITECTURE.md`, `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`, `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRainTileUrlBuilder.kt`, `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Compliance ambiguity remains auditable risk until policy decision is explicit (bounded exception or architecture wording update). | Phase 4B |
| Medium | Required bounded-exception path is not documented yet in architecture deviations registry. | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | Merge/review ambiguity: current plan allows exception path, but current deviations state remains empty. | Phase 4B |
| Medium | Attribution visibility is configured in raster tile metadata but lacks explicit runtime evidence documentation (where/how user sees it). | `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`, `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md` | Legal attribution may be present technically but not yet auditable as user-visible behavior in XCPro runtime documentation. | Phase 4B |
| Low | No focused regression/smoke check exists for attribution metadata retention across style reload/reapply paths. | `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`, `feature/map/src/test/java/com/trust3/xcpro/map/` | Future refactors could unintentionally drop attribution metadata without failing fast. | Phase 4B + Phase 5B |

Phase 4B implementation recommendations (2026-02-20 re-pass):
- Choose and document one compliance path explicitly:
  - Option A (recommended for minimal policy churn): add bounded exception entry in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` for legally required provider literals in weather integration (issue ID, owner, expiry).
  - Option B: update architecture wording to permit legally required attribution/endpoint literals only in non-UI implementation internals while keeping UI/public API vendor-neutral.
- Add auditable attribution evidence in `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md`:
  - exact runtime mechanism (`TileSet.attribution` in `WeatherRainOverlay`)
  - expected user-visible location/path in app (and manual verification steps).
- Add a lightweight regression guard:
  - unit-level check that weather raster source attribution string is non-empty and preserved in overlay source construction path, or
  - map-runtime smoke test checklist captured in docs if automated check is not feasible.
- Record explicit completion criteria for Phase 4B:
  - policy decision merged (exception or architecture update),
  - evidence doc updated,
  - verification checklist rerun.

Ninth deep-pass delta findings (2026-02-20, Phase 4B second re-pass):
- Status: still open; additional attribution-specific deltas identified.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Weather raster attribution uses plain domain text (`rainviewer.com`) instead of explicit link form despite provider terms requesting source mention with link. | `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`, `https://www.rainviewer.com/api.html` | Potential compliance gap between provider expectation ("with link") and runtime attribution payload. | Phase 4B |
| Medium | Attribution control visibility is implicit (default MapLibre behavior) with no explicit enable/verification step in map initialization. | `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt` | Future SDK/default changes could hide attribution without immediate detection. | Phase 4B |
| Low | Evidence doc still does not capture an explicit in-app attribution verification path (where to check and expected text/link shape). | `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md` | Compliance evidence remains weak for PR/release audits. | Phase 4B |

Phase 4B second re-pass recommendations (2026-02-20):
- Update weather tile attribution payload from plain domain to explicit link-form source text consistent with provider terms.
- Add explicit attribution visibility guard in map setup policy:
  - either explicit UI settings enable path, or
  - documented and tested assumption artifact pinned to current MapLibre defaults.
- Extend compliance evidence note with release-check steps:
  - where attribution is visible in app,
  - expected provider text/link form,
  - pass/fail checklist for QA.

Tenth deep-pass delta findings (2026-02-20, Phase 4B third re-pass):
- Status: still open; attribution-clickability and evidence-traceability deltas identified.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Full-screen custom gesture overlay likely intercepts taps, so MapLibre attribution link/button click-through is not guaranteed during normal map use. | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/MapGestureSetup.kt`, `feature/map/src/main/java/com/trust3/xcpro/gestures/CustomMapGestures.kt` | Even if attribution text is rendered, required "with link" behavior can fail in practice. | Phase 4B |
| Medium | No explicit in-app fallback attribution link exists in Weather settings/about surfaces. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt` | If map attribution visibility/clickability regresses, there is no secondary compliant attribution path. | Phase 4B |
| Low | Permission evidence note is only a session summary and does not reference a primary provider artifact (email/contract/ticket ID). | `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md` | Compliance audit traceability remains weak for release review and future maintainers. | Phase 4B |

Phase 4B third re-pass recommendations (2026-02-20):
- Add a deterministic attribution-accessibility decision:
  - either carve out a gesture pass-through region for map attribution UI, or
  - provide a first-party clickable attribution link in Weather/About that is always reachable.
- Add an explicit fallback compliance surface in app UI:
  - `Weather` settings or `About` section with provider source text + clickable link.
- Strengthen evidence artifacts:
  - record where provider permission evidence is stored (or note absence explicitly as a release risk),
  - include an auditable manual verification checklist that covers both visibility and clickability.

Phase 4B execution update (2026-02-20, implementation pass):
- implemented attribution/policy closure path:
  - added bounded architecture deviation entry for legally required provider literals (`RULES-20260220-11`) in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
- implemented explicit link-form raster attribution payload:
  - weather tiles now set `TileSet.attribution` via shared attribution helper (`weatherRainTileAttributionHtml()`).
- implemented attribution visibility/access guards:
  - explicit `map.uiSettings.isAttributionEnabled = true` in map initialization.
  - bottom-start gesture pass-through zone in custom map gesture layer to reduce attribution tap interception risk.
- implemented in-app fallback clickable attribution surface:
  - Weather settings now exposes "Open radar source link" with provider URL.
- implemented compliance evidence expansion:
  - `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md` now includes runtime paths and a release QA checklist for attribution visibility and link reachability.
- implemented regression guards:
  - `WeatherRainAttributionTest` for link-form payload and URL constant.
  - `CustomMapGesturesAttributionPassThroughTest` for attribution pass-through geometry policy.
  - `WeatherSettingsScreenPolicyTest` assertion for attribution URL validity.
- remaining Phase 4B residual:
  - none in implementation scope; provider artifact record is now documented in `docs/RAINVIEWER/evidence/RAINVIEWER_PROVIDER_PERMISSION_ARTIFACT.md`.

Eleventh deep-pass delta findings (2026-02-20, Phase 5B re-pass):
- Status: open; Phase 5B needs broader map-runtime weather regression coverage than previously listed.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | No focused tests verify `MapOverlayManager` weather dedupe matrix (`status`-only no-render vs `stale`/render-driving changes). | `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` | Render churn/stale-dimming regressions can return silently during refactors. | Phase 5B |
| Medium | No explicit regression test covers style reload reapply path across runtime command callback (`MapRuntimeController`) and weather overlay reattachment (`MapOverlayManager.onMapStyleChanged`). | `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapRuntimeController.kt`, `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` | Style swaps can regress weather overlay reapply behavior without failing tests. | Phase 5B |
| Low | No lifecycle cleanup test verifies weather overlay teardown/nulling on destroy/cleanup paths. | `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt` | Overlay/resource cleanup regressions can leak runtime map artifacts across map recreation. | Phase 5B |
| Medium | `WeatherRainOverlay` map-style runtime behaviors remain untested (legacy artifact cleanup, cache prune cap, anchor fallback ordering). | `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt` | Layer/source ordering or cache regressions can degrade radar rendering quality and stability. | Phase 5B |

Phase 5B re-pass recommendations (2026-02-20):
- Add a dedicated map-runtime weather regression tranche that includes manager, runtime controller, lifecycle, and renderer-policy tests.
- Keep behavior unchanged in production logic; if tests need seam points, use minimal internal test seams only (no architecture boundary changes).
- Gate Phase 5B completion on explicit pass of new weather map-runtime test classes plus `enforceRules`.

## 1) Scope

Problem statement:
- RainViewer overlay works, but resilience/observability/testing are below industry-grade bar.

Why now:
- User expectation is realtime confidence and high-quality motion playback.
- Existing docs were fragmented and partially stale.

In scope:
- refresh/backoff hardening
- stale/live status model and user messaging
- single-flight metadata fetch protection
- test expansion for weather runtime behavior
- policy/compliance cleanup for UI copy and attribution contract clarity
- documentation consolidation
- 30-minute frame quality filtering policy (drop low-value boundary frame candidates)
- window-aware transition-duration policy by animation window size
- stale-status refinement for transient non-OK states when last-success metadata is still fresh
- render-path churn reduction for status-only runtime changes

Out of scope:
- switching weather provider
- adding additional remote-weather overlay data products beyond current rain radar
- replacing MapLibre

User-visible impact:
- clearer "live vs stale" confidence
- more predictable behavior under provider errors/rate limits
- cleaner Weather settings semantics

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Rain preferences (`enabled`, `opacity`, animation settings) | `WeatherOverlayPreferencesRepository` | `preferencesFlow` | UI-local mirrored settings state persisted outside repository |
| Metadata snapshot + fetch status | `WeatherRadarMetadataRepository` | `WeatherRadarMetadataState` | separate metadata caches in ViewModel/UI |
| Runtime frame selection + transition duration | `ObserveWeatherOverlayStateUseCase` | `WeatherOverlayRuntimeState` | frame picking logic in Composables |
| Map raster layers/sources and cross-fade runtime state | `WeatherRainOverlay` | internal fields only | repository/viewmodel copies of render internals |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case -> data`

Files touched (planned):
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayModels.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- new/updated tests under `feature/map/src/test/java/com/trust3/xcpro/weather/rain/`

Boundary risk:
- keep MapLibre-specific logic in `WeatherRainOverlay`/map runtime only.
- keep provider/network behavior in repository and use-case only.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Retry/backoff policy | implicit fixed loop in use-case ticker | explicit refresh policy owned by metadata repository/use-case contract | ensure adaptive and testable behavior | repository policy tests |
| Stale/live status interpretation | implicit operator interpretation | use-case/runtime state fields consumed by UI | prevent stale data ambiguity | use-case + UI rendering tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Map weather UX | map layer updates without stale-state messaging | runtime status model surfaced to UI with freshness age | Phase 2 |
| Error handling | generic apply failure log only | structured error reason + status propagation | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `lastSuccessfulFetchWallMs` | Wall | provider freshness and user-facing status timestamps |
| metadata refresh cadence | Monotonic coroutine delay loop | refresh scheduling/backoff uses duration-based coroutine delay ticks |
| frame epoch from provider (`time`) | Wall (UTC epoch seconds) | provider contract |
| animation tick progression | Monotonic coroutine delay loop | deterministic frame cycling while active |

Forbidden comparisons to preserve:
- monotonic vs wall time arithmetic
- replay clock vs wall clock comparisons

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - network/JSON parse: `IO`
  - runtime state mapping: coroutine flow pipeline
  - map render updates: main/map style thread callbacks
- Primary cadence:
  - metadata refresh baseline: 60 seconds
  - adaptive backoff under failure/rate-limit (Phase 1)
- Hot path budget:
  - animation frame switch should remain visual-only and bounded by transition quality

### 2.5 Replay Determinism

- Deterministic for same input: Yes (weather overlay is external-live, but frame selection logic is deterministic for identical metadata + preferences).
- Randomness used: No.
- Replay/live divergence rules:
  - replay pipeline does not own weather provider truth; weather overlay remains external state.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| duplicate/hidden state | SSOT rules | unit test + review | new use-case/repository tests |
| wrong time source usage | Timebase rules | enforceRules + test | existing enforceRules + new policy tests |
| UI business logic drift | MVVM/UDF rules | review + compose state tests | Weather settings tests |
| weak failure handling | quality/stability intent | unit tests | metadata refresh/backoff tests |

## 3) Data Flow (Before -> After)

Current:

`RainViewer API -> WeatherRadarMetadataRepository -> ObserveWeatherOverlayStateUseCase -> WeatherOverlayViewModel -> MapWeatherOverlayEffects -> MapOverlayManager -> WeatherRainOverlay`

Target (hardened):

`RainViewer API -> MetadataRepository (single-flight + adaptive refresh policy + explicit status) -> UseCase (frame + stale/live derivation) -> ViewModel -> Map/Settings UI (status-visible) -> MapOverlayManager/WeatherRainOverlay (deterministic rendering + structured failures)`

## 4) Implementation Phases

### Phase 0 - Baseline Lock

Goal:
- Freeze current behavior and codify current score before hardening.

Files:
- `docs/RAINVIEWER/01_RAINVIEWER_INDUSTRY_HARDENING_PLAN_2026-02-20.md`
- `docs/RAINVIEWER/00_INDEX.md`

Tests:
- rerun existing weather test slice and rule gate.

Exit:
- baseline score captured and verification rerun recorded.

### Phase 1 - Network and Refresh Resilience

Goal:
- prevent duplicate refresh calls and reduce pressure during failures.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettings.kt`

Changes:
- add single-flight protection (`Mutex`) around refresh.
- add adaptive refresh intervals by status:
  - OK: 60s
  - NETWORK_ERROR/PARSE_ERROR: 120s
  - RATE_LIMIT: 300s
- keep immediate refresh when overlay is enabled.

Tests:
- refresh concurrency test (single outbound call under concurrent triggers).
- backoff policy tests by status code.

Exit:
- no duplicate refresh under concurrent collectors.
- rate-limit behavior slows requests automatically.

### Phase 1B - Weather Network Client Isolation

Goal:
- decouple RainViewer metadata transport from ADS-B network client behavior.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/di/AdsbNetworkModule.kt`
- `feature/map/src/main/java/com/trust3/xcpro/di/*` (weather network provider module, qualifier)
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`

Changes:
- add weather-specific qualified `OkHttpClient` binding for RainViewer metadata fetches.
- keep explicit timeout policy aligned with RainViewer metadata polling behavior.
- avoid accidental interceptor/header coupling from ADS-B transport.

Tests:
- DI wiring test or compile-level binding validation.

Exit:
- weather metadata fetch path is isolated and explicit in DI.

### Phase 1C - Metadata Version Compatibility Hardening

Goal:
- avoid avoidable outages from strict provider-version checks.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`

Changes:
- replace strict major-version hard-fail with compatibility-first validation:
  - parse required fields first.
  - downgrade unknown version to warning detail when payload is still structurally valid.
  - keep hard-fail only for structurally invalid payload.

Tests:
- unknown version + valid structure should parse.
- invalid structure still fails deterministically.

Exit:
- version metadata no longer causes unnecessary hard failure.

### Phase 1D - Refresh Gap Timebase Hardening

Goal:
- keep rapid-call dedupe robust under wall-clock changes.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`

Changes:
- use monotonic time for in-process rapid dedupe, or explicitly handle wall-clock rewind by resetting dedupe gate.

Tests:
- simulated wall-clock rewind does not suppress refresh attempts indefinitely.

Exit:
- refresh gating remains stable regardless of wall-clock jumps.

### Phase 1E - Freshness Semantics Clarification

Goal:
- make freshness semantics explicit and consistent with user-visible wording.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayModels.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCaseTest.kt`

Changes:
- define and persist two explicit freshness concepts if needed:
  - last successful fetch wall time
  - last metadata-content-change wall time
- align stale derivation and UI labels with the chosen semantics.

Tests:
- unchanged-generated successful fetch behavior is deterministic and documented.
- changed-content with same generated (if encountered) follows explicit policy.

Exit:
- freshness labels and stale state semantics are unambiguous.

### Phase 1F - Locale-Safe Host Normalization

Goal:
- eliminate locale-sensitive string normalization in security-critical host checks.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`

Changes:
- replace `lowercase()` usage with explicit locale-safe normalization (`Locale.US`).

Tests:
- host trust/normalization remains deterministic across locale overrides.

Exit:
- host normalization is locale-safe and consistent with tile URL builder behavior.

### Phase 1G - Metadata Transport Efficiency

Goal:
- reduce unnecessary metadata transfer cost while preserving realtime behavior.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
- weather-qualified network module from Phase 1B
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`

Changes:
- add conditional request support where provider headers allow:
  - persist `ETag` / `Last-Modified` markers in-memory for runtime session.
  - send `If-None-Match` / `If-Modified-Since` on subsequent polls.
- handle `304 Not Modified` explicitly as successful freshness confirmation under chosen semantics from Phase 1E.

Tests:
- `304` response path preserves metadata and updates freshness semantics according to policy.

Exit:
- metadata polling remains timely with lower transfer overhead.

### Phase 2 - Realtime Confidence and Stale-State UX

Goal:
- make stale/live status explicit to user and runtime.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayModels.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`

Changes:
- add runtime fields: freshness age, stale flag, derived status label.
- expose "last update age" and stale/rate-limited status in Weather UI.
- keep status enum aligned with provider/runtime contract (unsupported states removed).

Tests:
- use-case tests for stale transitions and frame selection invariants (10/20/30 windows).

Exit:
- user can distinguish live/stale/rate-limited states without guessing.

### Phase 2B - Transient Stale Policy Refinement

Goal:
- avoid false "Stale" labeling during short provider/network blips when last-success metadata is still fresh.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettings.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettingsTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCaseTest.kt`

Changes:
- split freshness from transport status:
  - keep status visible (`NETWORK_ERROR`, `RATE_LIMIT`, etc.)
  - derive `metadataStale` from freshness-age policy with explicit grace behavior for transient statuses.
- preserve conservative stale behavior when no successful metadata exists.

Tests:
- recent last-success + transient error should keep `metadataStale=false` within grace window.
- missing/old last-success remains `metadataStale=true`.

Exit:
- stale indicator reflects real freshness, not momentary transport jitter.

### Phase 2C - Settings Surface Alignment

Goal:
- remove dead hidden controls or expose them intentionally.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayPreferencesRepository.kt`

Changes:
- decide one path and document it:
  - expose `frameMode`/manual frame and render options in UI, or
  - remove dead preference paths from runtime/persistence.

Tests:
- settings-to-runtime mapping test coverage for whichever path is chosen.

Exit:
- no dead or inaccessible weather preference state remains.

### Phase 2C-Polish - Control Visibility Refinement

Goal:
- reduce control-surface clutter by showing manual-frame controls only when relevant.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`

Changes:
- conditionally display (not only disable) manual-frame slider/help text when:
  - `frameMode == MANUAL`
  - cycle mode is off
  - frame data is available
- keep `frameMode` and render toggles always visible.

Tests:
- compose/UI state mapping tests for visibility rules.

Exit:
- manual-frame controls are visible only when actionable.

### Phase 2D - In-Map Confidence Signaling

Goal:
- show stale/error confidence directly on map while overlay is active.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/*` (map overlay/status UI host)
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayModels.kt`

Changes:
- add minimal in-map weather status badge/chip (for example Live/Stale/Error).
- keep map UI as consumer only; no business logic move from use-case.

Tests:
- compose rendering tests for map status indicator state mapping.

Exit:
- users can assess rain confidence without opening Weather settings.

### Phase 2E - Visible Frame-Age Metric

Goal:
- expose how old the currently displayed frame is.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayModels.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- optional map status UI files from Phase 2D

Changes:
- derive selected-frame age from `selectedFrame.frameTimeEpochSec` vs injected wall clock.
- surface it in Weather settings and optional map badge details.

Tests:
- deterministic frame-age derivation tests.

Exit:
- operator can read both metadata freshness and visible-frame freshness.

### Phase 2F - Stale Render Policy on Map

Goal:
- prevent stale/error radar from appearing fully authoritative on map.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayModels.kt`
- map UI files used by Phase 2D

Changes:
- define explicit map stale policy:
  - option A: dim stale overlay opacity to configured floor.
  - option B: auto-hide overlay when stale threshold exceeded and show status badge.
- policy must be deterministic and user-visible.

Tests:
- map weather runtime tests for stale transition behavior (render -> dim/hide).

Exit:
- map presentation of stale/error radar is explicit and safer.

### Phase 3 - Renderer and Error Hardening

Goal:
- keep smooth playback and improve diagnosability.

Execution order within Phase 3:
- 3A: 30m frame-quality filtering
- 3B: window-aware transition tuning
- 3C: status-only re-render suppression
- 3D: animation ticker stability under non-animation preference changes
- 3E: metadata frame dedupe normalization

Files:
- `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`

Changes:
- keep deterministic frame cache behavior under rapid window/speed changes.
- improve structured logging for render failures (include exception reason).
- ensure no source/layer leaks through repeated enable/disable/style reload cycles.
- add quality-aware 30m frame filtering:
  - exclude oldest boundary frame candidate when it produces degraded playback value.
  - keep deterministic fallback for sparse frame sets.
- add window-aware transition-duration policy:
  - 10m retains current baseline quality.
  - 20m moderate blend reduction.
  - 30m stronger blend reduction to improve clarity.
- avoid status-only re-render churn:
  - status updates should not trigger map raster render when frame/opacity/duration are unchanged.
- stabilize animation ticker:
  - ticker should depend only on animation-driving preferences (`enabled`, animate flag, speed),
    not full preference object updates such as opacity.
- normalize metadata animation frame list:
  - dedupe duplicate timestamps/paths before runtime frame selection.

Tests:
- renderer behavior tests for cache prune + transition boundaries (where feasible).
- map manager reapply tests for repeated style changes.
- use-case tests for 30m filtered-frame selection and deterministic fallback.
- transition-policy tests per window (10/20/30) and speed.
- map manager tests asserting no render call on status-only state changes.
- test ensuring opacity/transition setting writes do not reset animation tick sequence.
- repository/use-case tests for duplicate-frame payload normalization.

Exit:
- no duplicate layer/source failures in stress loops.
- failure logs are actionable.
- 30m playback clarity improved without breaking 10m baseline behavior.

### Phase 4 - Compliance and Policy Alignment

Goal:
- align implementation with repo policies and provider obligations.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapGestureSetup.kt`
- `feature/map/src/main/java/com/trust3/xcpro/gestures/CustomMapGestures.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRainAttribution.kt`
- `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (only if exception is required)

Changes:
- remove vendor-specific wording from settings copy.
- keep required provider attribution in tile metadata.
- if attribution string conflicts with architecture wording, add explicit bounded exception entry (issue/owner/expiry) or update policy text to permit legally required attribution only.
- document explicit compliance decision for required attribution:
  - either add bounded exception entry in `KNOWN_DEVIATIONS.md`, or
  - update architecture wording to allow legally required attribution strings only.

Tests:
- UI copy sanity check (no provider-name coupling in production settings labels).
- attribution payload format and URL stability checks.
- attribution gesture pass-through geometry checks.

Exit:
- compliance story is explicit and auditable.

### Phase 5 - Test Expansion and Release Gates

Goal:
- increase confidence for ongoing changes.

Files:
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCaseTest.kt` (new)
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRainOverlayTest.kt` (new if feasible)

Tests:
- frame window selection for 10/20/30 mins.
- ping-pong playback under sparse frame sets.
- stale age derivation from wall clock.
- backoff interval selection.
- single-flight refresh under parallel requests.

Exit:
- risky behavior is covered by deterministic tests.

### Phase 5B - Map Runtime Weather Test Coverage

Goal:
- add targeted regression tests for map-side weather glue, style callback wiring, and runtime cleanup behavior.

Files:
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerWeatherRainTest.kt` (new)
- `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapRuntimeControllerWeatherStyleTest.kt` (new)
- `feature/map/src/test/java/com/trust3/xcpro/map/MapLifecycleManagerWeatherCleanupTest.kt` (new)
- `feature/map/src/test/java/com/trust3/xcpro/map/WeatherRainOverlayPolicyTest.kt` (new)
- `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapWeatherOverlayEffects*` (new if feasible without DI churn)
- optional seam-only updates if needed for deterministic tests:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`

Tests:
- `MapOverlayManager` weather dedupe behavior:
  - status-only updates do not force render.
  - stale-driving updates do apply render and stale opacity clamp.
  - clear/reset path invalidates dedupe and re-enable reapplies render.
- `MapOverlayManager` style-change behavior:
  - weather overlay is recreated and latest runtime config is reapplied once per style callback.
- `MapRuntimeController` callback policy:
  - stale style callbacks are ignored and only latest callback invokes `overlayManager.onMapStyleChanged`.
- `MapLifecycleManager` cleanup policy:
  - weather overlay cleanup is called and runtime handle is nulled on `cleanup()` and `ON_DESTROY`.
- `WeatherRainOverlay` renderer policy:
  - zero-duration frame switch path.
  - cache prune bound preserves active/protected frames.
  - clear removes both cached and legacy artifacts.

Exit:
- map-side weather behavior has explicit regression safety across manager/runtime/lifecycle/renderer paths.
- no uncovered Phase 5B high/medium findings remain in this plan.

Phase 5B execution update (2026-02-20):
- implemented new map-runtime weather regression tests:
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerWeatherRainTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapRuntimeControllerWeatherStyleTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapLifecycleManagerWeatherCleanupTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/WeatherRainOverlayPolicyTest.kt`
- coverage added in this tranche:
  - `MapOverlayManager` dedupe matrix (`status`-only no-render, stale-driven reapply, clear/reset dedupe invalidation, explicit reapply after overlay replacement).
  - runtime style callback protection in `MapRuntimeController` (stale callback ignore and clear-map invalidation).
  - lifecycle cleanup guarantees for weather overlay teardown in `MapLifecycleManager`.
  - `WeatherRainOverlay` renderer policy checks for zero-duration frame switch handling, cache-cap pruning, and legacy artifact cleanup.
- verification rerun for Phase 5B implementation:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapOverlayManagerWeatherRainTest" --tests "com.trust3.xcpro.map.ui.MapRuntimeControllerWeatherStyleTest" --tests "com.trust3.xcpro.map.MapLifecycleManagerWeatherCleanupTest" --tests "com.trust3.xcpro.map.WeatherRainOverlayPolicyTest"`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS

Twelfth deep-pass delta findings (2026-02-20, Phase 5C re-pass):
- Status: open; core Phase 5C freshness/status semantics still has targeted regression gaps.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Locale-safety is implemented in production host normalization, but tests do not force non-US locale (for example Turkish) to lock regression behavior. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`, `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRainTileUrlBuilder.kt`, `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt`, `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRainTileUrlBuilderTest.kt` | Future locale-related regressions in host normalization could slip through CI. | Phase 5C |
| Medium | Freshness regression tests assert `metadataFreshnessAgeMs` evolution, but do not explicitly lock `selectedFrameAgeMs` and `metadataContentAgeMs` semantics under unchanged payload/fresh fetch cycles. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCase.kt`, `feature/map/src/test/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCaseTest.kt` | Drift between fetch-age/content-age/frame-age semantics can regress without failing tests. | Phase 5C |
| Medium | Transient-error stale semantics are tested for `NETWORK_ERROR`, but not explicitly for `RATE_LIMIT` with fresh last-success metadata. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlaySettings.kt`, `feature/map/src/test/java/com/trust3/xcpro/weather/rain/ObserveWeatherOverlayStateUseCaseTest.kt` | Rate-limit status behavior can regress toward stale flicker without deterministic guard. | Phase 5C |
| Low | HTTP 304-without-cache fallback path in metadata repository is implemented but not directly tested. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt` | Degraded bootstrap edge-case semantics can drift undetected. | Phase 5C |
| Low | Weather settings policy tests currently verify control visibility/link policy, but not user-visible freshness/content/frame-age label semantics across live/stale/error states. | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`, `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreenPolicyTest.kt` | UI-facing confidence semantics can regress even if use-case tests still pass. | Phase 5C |

Phase 5C re-pass recommendations (2026-02-20):
- Add locale-forced normalization tests:
  - set default locale to Turkish within test scope and verify trusted host normalization remains deterministic in both repository and tile-url builder paths.
- Expand freshness semantics tests in `ObserveWeatherOverlayStateUseCaseTest`:
  - unchanged payload: `metadataFreshnessAgeMs` increases while `metadataContentAgeMs` remains stable.
  - stale-frame scenario: explicit `selectedFrameAgeMs` behavior when selected frame is old.
  - transient `RATE_LIMIT` with fresh last-success should remain non-stale.
- Add metadata repository edge test for 304-without-cache bootstrap behavior.
- Add lightweight Weather settings semantics tests for visible age/status label decisions (policy-level if full compose tests are not feasible in this tranche).

Thirteenth deep-pass delta findings (2026-02-20, Phase 5C implementation-readiness re-pass):
- Status: open; an additional settings state-wiring regression gap remains.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Low | No focused ViewModel/UseCase regression tests exist for Weather settings state wiring and setter passthrough (`overlayEnabled`, `animatePastWindow`, `animationWindow`, `animationSpeed`, `transitionQuality`, `frameMode`, `manualFrameIndex`, `smooth`, `snow`). | `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsUseCase.kt`, `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreenPolicyTest.kt` | Preference wiring regressions can ship undetected even when UI control-visibility tests still pass. | Phase 5C |

Phase 5C implementation-readiness recommendations (2026-02-20):
- Add `WeatherSettingsUseCaseTest` for flow projection and setter delegation against the weather preferences repository contract.
- Add `WeatherSettingsViewModelTest` for state defaults, updates, and intent passthrough behavior across all weather controls.

Fourteenth deep-pass delta findings (2026-02-20, Phase 5C re-pass #2):
- Status: open; degraded-path and confidence-branch regression gaps remain.

| Severity | Finding | Evidence | Impact | Planned Fix Phase |
|---|---|---|---|---|
| Medium | Metadata repository `NO_FRAMES` degraded path is implemented but not directly regression-tested (including fallback to cached metadata and detail semantics). | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`, `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherRadarMetadataRepositoryTest.kt` | Provider payloads with empty `radar.past` can regress fallback behavior without failing CI. | Phase 5C |
| Low | Weather confidence mapping tests validate hard-error behavior with `PARSE_ERROR`, but not explicit `NO_FRAMES`-with-frame and `RATE_LIMIT`-fresh branches. | `feature/map/src/main/java/com/trust3/xcpro/weather/rain/WeatherOverlayUiMapping.kt`, `feature/map/src/test/java/com/trust3/xcpro/weather/rain/WeatherOverlayUiMappingTest.kt` | Map-chip semantics can drift for degraded/transient states while status-label tests still pass. | Phase 5C |

Phase 5C re-pass #2 recommendations (2026-02-20):
- Add `WeatherRadarMetadataRepositoryTest` coverage for `NO_FRAMES` responses:
  - first fetch returns empty `radar.past` and status is `NO_FRAMES`.
  - subsequent empty-frame response preserves last known metadata when available.
- Expand `WeatherOverlayUiMappingTest` with explicit confidence-state assertions for:
  - `NO_FRAMES` while a selected frame exists -> `Rain Error`.
  - `RATE_LIMIT` with fresh metadata (`metadataStale=false`) -> non-error confidence state per policy.

### Phase 5C - Freshness and Status Semantics Regression Suite

Goal:
- lock behavior for freshness semantics and user-visible confidence signals.

Files:
- `feature/map/src/test/java/com/trust3/xcpro/weather/rain/*`
- `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettings*.kt`
- compose/map UI tests for Phase 2D/2E where applicable

Tests:
- metadata freshness age vs selected-frame age behavior under:
  - unchanged-generated successful fetches
  - transient error with fresh last-success
  - stale frame scenarios
- locale-safe host normalization tests.
- metadata-content-age stability tests when payload is unchanged.
- 304-without-cache metadata bootstrap semantics test.
- `NO_FRAMES` degraded-path repository semantics tests (with and without cached metadata).
- settings-level confidence/age label policy tests for live/stale/error render paths.
- confidence-mapping branch tests for `NO_FRAMES` and `RATE_LIMIT`.
- Weather settings use-case/viewmodel regression tests for flow mapping and setter passthrough.

Exit:
- confidence semantics are regression-safe and explicit.
- no uncovered Phase 5C high/medium findings remain in this plan.

## 5) Test Plan

- Unit tests:
  - weather refresh policy and single-flight behavior
  - runtime state derivation and stale-status mapping
  - frame selection and playback invariants
  - map runtime weather branch safety (overlay manager + runtime style callback + lifecycle cleanup + renderer policy)
- Replay/regression:
  - not replay-critical; ensure deterministic frame selection from fixed metadata fixtures
- UI/instrumentation:
  - Weather settings stale/live status render test
- Failure/degraded mode:
  - rate-limit and network error transitions preserve last known usable frame and mark stale
- Boundary tests for removed bypasses:
  - map weather UX path remains use-case/runtime-state driven (no direct UI bypass to renderer policy).
  - error/status propagation remains explicit from repository -> use-case -> UI/runtime mapping.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional (when device available):

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-polling during provider issues | user/API pressure | adaptive backoff + single-flight | XCPro Team |
| Stale data presented as live | user trust degradation | explicit stale/live UX + age | XCPro Team |
| Transient failures marked stale immediately | misleading status flicker | freshness-based stale derivation with transient grace | XCPro Team |
| Cross-feature HTTP coupling | hidden regressions | weather-qualified `OkHttpClient` DI binding | XCPro Team |
| Provider version bump outage | avoidable feature break | compatibility-first metadata version handling | XCPro Team |
| Wall-clock rewind suppressing rapid refresh | temporary live-update stall | monotonic dedupe gate or rewind-safe fallback | XCPro Team |
| Locale-sensitive host normalization | edge-case parse/security mismatch | explicit locale normalization in repository host handling | XCPro Team |
| Metadata polling transfer overhead | avoidable network/battery cost | conditional request validators and 304 handling | XCPro Team |
| Ambiguous freshness semantics | operator confusion on "age" meaning | explicit fetch-age vs content-age modeling and labels | XCPro Team |
| Compliance ambiguity on vendor naming vs attribution | policy drift | neutral UI copy + explicit attribution policy handling | XCPro Team |
| Regression in animation quality | perceived UX drop | renderer tests + transition guards | XCPro Team |
| 30m boundary frame blur | degraded radar readability | frame-quality filtering + deterministic fallback | XCPro Team |
| Duplicate metadata frames | redundant animation steps | frame dedupe normalization before selection | XCPro Team |
| Status-only render churn | unnecessary map work | runtime config key excludes non-render fields | XCPro Team |
| Animation jitter after non-animation setting changes | degraded UX continuity | stabilize ticker dependencies to animation-only inputs | XCPro Team |
| No in-map confidence cue | stale radar can be misread on map | in-map live/stale/error weather badge | XCPro Team |
| Stale/error radar remains full-strength on map | confidence/safety risk | explicit stale render policy (dim/hide) | XCPro Team |
| No explicit visible-frame age metric | weak realtime confidence interpretation | expose selected-frame age in runtime/UI | XCPro Team |
| Advanced settings UI lacks dedicated UI-level regression tests | future settings drift | add ViewModel/Compose tests for frame-mode/manual-index/smooth/snow | XCPro Team |
| Manual-frame controls visible when not actionable | UI clarity drop | Phase 2C-Polish conditional visibility rules | XCPro Team |
| Under-tested runtime behavior | future breakage | add focused use-case/renderer tests | XCPro Team |

## 7) Acceptance Gates

- No architecture/rules violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- Metadata refresh is single-flight and adaptive by status.
- Weather metadata transport is isolated with a weather-qualified network client.
- Metadata parser tolerates unknown provider version when required fields remain valid.
- Refresh rapid-dedupe remains correct under wall-clock rewind/adjustment scenarios.
- Host normalization is locale-safe and deterministic.
- Metadata polling supports conditional validator requests and 304-not-modified efficiency path.
- Freshness semantics are explicit and consistent (fetch age vs content/frame age).
- Stale/live status is explicit in runtime state and visible in Weather settings.
- Transient status errors do not force stale=true when last-success metadata is still fresh.
- Animation ticker is stable and does not reset on non-animation preference writes.
- 30m frame-selection policy removes low-value boundary blur frames while preserving deterministic fallback.
- Transition duration policy is window-aware (10/20/30) and reduces excessive blend in 30m mode.
- Metadata frame list is normalized (dedupe) before animation selection.
- In-map weather confidence indicator is present and consistent with settings status semantics.
- Stale/error radar map presentation follows explicit dim/hide policy.
- Selected-frame age is visible to users.
- Weather preference surface is aligned: hidden dead controls removed or intentionally exposed.
- Weather status enum remains explicit and free of dead/unsupported states.
- Weather settings production copy is provider-neutral.
- Layer/source lifecycle remains stable across map style changes.
- Status-only runtime updates do not trigger unnecessary rain overlay re-render.
- Attribution compliance decision is explicitly documented (policy update or bounded exception).
- Required verification commands pass.
- `docs/ARCHITECTURE/PIPELINE.md` remains accurate.

## 8) Rollback Plan

- Revert independently:
  - stale/live UX layer only (keep base rain overlay behavior)
  - adaptive backoff policy only (restore fixed interval temporarily)
  - single-flight guard only
- Recovery steps:
  1. lock overlay to latest frame with animation on/off unchanged
  2. retain metadata fetch + parse path
  3. rerun required verification commands

## 9) Quality Target and Score

Target: 10/10 (industry-grade)

Current deep-pass score before this hardening execution:
- Architecture/layering: 8.5/10
- Runtime resilience: 6.5/10
- User trust signaling (live/stale clarity): 6.0/10
- Test confidence on risky paths: 6.5/10
- Overall: 7.1/10

Execution quality score after completed tranche (Phases 1/2/4/5 partial):
- Architecture/layering: 9.5/10
- Runtime resilience: 9.0/10
- User trust signaling (live/stale clarity): 8.7/10
- Test confidence on risky paths: 8.8/10
- Overall: 9.0/10

Revised score after second deep-pass delta findings:
- Architecture/layering: 9.3/10
- Runtime resilience: 8.7/10
- User trust signaling (live/stale clarity): 8.3/10
- Test confidence on risky paths: 8.5/10
- Overall: 8.7/10

Revised score after third deep-pass delta findings:
- Architecture/layering: 9.1/10
- Runtime resilience: 8.3/10
- User trust signaling (live/stale clarity): 8.2/10
- Test confidence on risky paths: 8.1/10
- Overall: 8.4/10

Revised score after fourth deep-pass delta findings:
- Architecture/layering: 9.0/10
- Runtime resilience: 8.0/10
- User trust signaling (live/stale clarity): 7.8/10
- Test confidence on risky paths: 7.9/10
- Overall: 8.1/10

Revised score after fifth deep-pass delta findings:
- Architecture/layering: 8.9/10
- Runtime resilience: 7.8/10
- User trust signaling (live/stale clarity): 7.4/10
- Test confidence on risky paths: 7.7/10
- Overall: 7.9/10

Execution score after tranche B implementation (2026-02-20):
- Architecture/layering: 9.4/10
- Runtime resilience: 9.1/10
- User trust signaling (live/stale clarity): 8.8/10
- Test confidence on risky paths: 9.0/10
- Overall: 9.1/10

Revised score after sixth deep-pass delta findings:
- Architecture/layering: 9.4/10
- Runtime resilience: 9.1/10
- User trust signaling (live/stale clarity): 8.6/10
- Test confidence on risky paths: 8.7/10
- Overall: 8.9/10

Revised score after tenth deep-pass delta findings (Phase 4B third re-pass):
- Architecture/layering: 9.3/10
- Runtime resilience: 9.1/10
- User trust signaling (live/stale clarity): 8.6/10
- Test confidence on risky paths: 8.6/10
- Overall: 8.8/10

Revised score after Phase 4B implementation pass:
- Architecture/layering: 9.4/10
- Runtime resilience: 9.2/10
- User trust signaling (live/stale clarity): 9.0/10
- Test confidence on risky paths: 9.0/10
- Overall: 9.2/10

Revised score after eleventh deep-pass delta findings (Phase 5B re-pass):
- Architecture/layering: 9.4/10
- Runtime resilience: 9.1/10
- User trust signaling (live/stale clarity): 9.0/10
- Test confidence on risky paths: 8.8/10
- Overall: 9.1/10

Execution score after Phase 5B implementation pass:
- Architecture/layering: 9.4/10
- Runtime resilience: 9.2/10
- User trust signaling (live/stale clarity): 9.0/10
- Test confidence on risky paths: 9.2/10
- Overall: 9.2/10

Revised score after twelfth deep-pass delta findings (Phase 5C re-pass):
- Architecture/layering: 9.4/10
- Runtime resilience: 9.1/10
- User trust signaling (live/stale clarity): 8.9/10
- Test confidence on risky paths: 8.9/10
- Overall: 9.1/10

Path back to 10/10:
- implement Phase 5C freshness/status regression tranche
- rerun required verification + targeted weather/rain test slice
- complete acceptance gates in Section 7
