# Stage B Implementation Plan (SkySight Adapter for XCPro MVP)

Date: 2026-02-15

## 1) Purpose

This plan defines the concrete implementation path for Stage B:
- replace the fake forecast provider binding with a real SkySight-backed adapter
- preserve MVVM + UDF + SSOT and map runtime boundaries
- ship MVP overlay + point value behavior without guessing hidden contracts

This plan is execution-focused and must be used with:
- `AGENTS.md`
- `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/SKYSIGHT/SkySightChangePlan/SKYSIGHT_API_CONTRACT_REQUIRED.md`

Template compliance:
- This document is the SkySight-specific instantiation of `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`.
- Any Stage B implementation PR should reference this file as the task change plan artifact.

## 2) Current status and gate

Confirmed:
- Auth endpoint works: `POST https://skysight.io/api/auth`

Not acceptable for implementation:
- guessed `https://skysight.io/api/forecast/*` paths are `404`

Discovered from authenticated SkySight web app bundle inspection (inferred, must be confirmed by evidence):
- tiles and updates use `edge.skysight.io`
- point values use `cf.skysight.io/point/...`
- parameter catalog appears in authenticated bootstrap payload (`params` object)

Stage B code implementation is allowed only when the evidence pack captures at least one full happy path:
- parameter metadata
- tile template + tile headers
- point value success

## 3) MVP scope

In scope:
- real provider adapter for:
  - parameter metadata
  - tile template/source for current parameter/time/region
  - legend mapping (if derived from metadata or endpoint)
  - point value query
- region-aware requests using user-selected region from Forecast settings
- DI switch from fake to SkySight provider
- error mapping and fallback state rendering

Out of scope:
- full SkySight planner tools (route4d, skewt, windgram, xsection)
- offline tile mirroring/proxy
- backend gateway service

## 4) Architecture contract (must hold)

Dependency direction:
- UI -> domain/usecase -> data adapters

SSOT owners:
- `ForecastPreferencesRepository` owns:
  - `enabled`
  - `opacity`
  - `selectedParameterId`
  - `selectedTimeUtcMs`
  - `selectedRegion`
- auth/session state remains repository-owned
- ViewModel consumes use cases/repositories only; no direct networking

Map boundary:
- MapLibre types remain in runtime/UI controllers (`ForecastRasterOverlay`, `MapOverlayManager`)
- no MapLibre types in ViewModel/domain

## 5) Stage B phases

### Phase B0 - Contract lock and evidence normalization

Goal:
- convert discovery into stable implementation contract files

Tasks:
1. Capture and store redacted evidence for active endpoints:
   - `parameters_success.json` from authenticated metadata source
   - `tile_template_success.json` with resolved template fields
   - `tile_sample_headers_ok.txt` and `tile_sample_headers_fail.txt`
   - `value_success.json` and `value_no_data.txt`
2. Keep legacy `/api/forecast/*` calls as negative evidence only.
3. Write `RATE_LIMIT_NOTES.md` from observed statuses/headers.
4. Record any inference explicitly as inference.

Gate:
- evidence files present and parseable
- no secrets in repo

### Phase B1 - Data contracts and adapter interfaces

Goal:
- define stable mapper boundary for the real provider

Tasks:
1. Add SkySight DTO models for:
   - parameter metadata payload
   - tile template payload
   - point value payload
2. Add mapping functions DTO -> domain models:
   - `ForecastParameterMeta`
   - `ForecastTileSpec`
   - `ForecastLegendSpec`
   - `ForecastPointValue`
3. Keep all provider-specific field names isolated in adapter package.

Gate:
- unit tests for mapping pass
- no provider DTO types leak outside adapter layer

### Phase B2 - Real provider implementation

Goal:
- implement concrete adapter for existing forecast ports

Tasks:
1. Implement `SkySightForecastProviderAdapter` for:
   - `ForecastCatalogPort`
   - `ForecastTilesPort`
   - `ForecastLegendPort`
   - `ForecastValuePort`
2. Use region from preferences to target requests/URLs.
3. Use selected time slot formatted exactly as contract requires.
4. Implement robust error mapping:
   - auth failure
   - entitlement/no data
   - network timeout
5. Add request throttling/debouncing at use-case or repository edge where needed.

Gate:
- adapter produces valid `ForecastTileSpec` and point values in manual smoke checks

### Phase B3 - DI switch and runtime verification

Goal:
- replace fake binding with real binding safely

Tasks:
1. Update `ForecastModule` binding:
   - fake -> real adapter (or keep fake behind debug flag if needed)
2. Ensure overlay remains disabled by default.
3. Validate style reload behavior and layer reapply behavior unchanged.
4. Verify region selection impacts real provider requests.

Gate:
- map overlay appears with real data for authenticated user in supported region/time

### Phase B4 - Hardening and release readiness

Goal:
- production-safe behavior and documentation completeness

Tasks:
1. Audit logs for secrets and precise location leakage in release paths.
2. Confirm 429/backoff behavior and no aggressive retry loops.
3. Confirm clear UX for:
   - not signed in
   - missing entitlement
   - no data for selection
4. Update docs:
   - `docs/ARCHITECTURE/PIPELINE.md` if flow wiring changes
   - `docs/SKYSIGHT/...` contract docs with final endpoint contract summary

Gate:
- required verification commands pass

## 6) File-level implementation targets

Primary likely targets:
- `feature/map/src/main/java/com/trust3/xcpro/forecast/`
  - add real adapter(s), DTOs, mappers
  - keep existing ports and overlay repository contracts stable
- `feature/map/src/main/java/com/trust3/xcpro/di/ForecastModule.kt`
  - switch binding to real adapter
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettings*`
  - keep auth/region configuration path aligned with adapter needs

Evidence and docs:
- `docs/integrations/skysight/evidence/*`
- `docs/SKYSIGHT/SkySightChangePlan/*`

## 7) Test plan for Stage B

Unit tests:
- adapter mapping tests for each endpoint payload
- error mapping tests (401/403/404/timeout/no-data)
- region selection propagation tests

ViewModel/use-case tests:
- unchanged behavior for enable/disable, time, opacity, and point query state transitions

Manual smoke:
1. Save credentials + test login succeeds.
2. Select region, enable overlay, confirm tiles appear.
3. Change parameter/time and confirm overlay updates.
4. Long-press map and confirm point value updates.
5. Switch base map/style and confirm overlay re-applies.

Required verification commands:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- instrumentation commands when device/emulator is available per `AGENTS.md`

## 8) Risks and mitigations

Risk: endpoint contract drift
- Mitigation: keep adapter mapping isolated and evidence-backed

Risk: region/time mismatch yields empty map
- Mitigation: explicit no-data states and default fallback region/time strategy

Risk: tile auth behavior changes
- Mitigation: keep tile header/auth proofs updated in evidence pack

Risk: rate-limit regressions from time scrubbing
- Mitigation: debounce/throttle + backoff handling

## 9) Definition of done (Stage B)

Done when all are true:
- real provider bound in DI for forecast ports
- authenticated user can render forecast overlay with selected parameter/time/region
- long-press point value works for active selection
- no architecture rule violations introduced
- required checks pass (or documented local limitations)
- final contract summary in docs reflects implemented behavior
