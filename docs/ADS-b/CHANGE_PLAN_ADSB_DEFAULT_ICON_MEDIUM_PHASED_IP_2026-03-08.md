# ADS-B Default Icon Medium - Production-Grade Phased IP

Date: 2026-03-08  
Owner: XCPro Team  
Status: In Progress (deep code pass complete; phase uplift execution pending)

Execution update (2026-03-08):
- Deep code pass completed across icon mapping, metadata hydration, overlay projection, and test coverage.
- Verified in code:
  - `AdsbAircraftIcon.Unknown` visual fallback uses `ic_adsb_plane_medium.png`.
  - Bounded parallel on-demand metadata lookup is active (`ON_DEMAND_FETCH_PARALLELISM = 4`).
- Current implementation status:
  - Phase 0 runtime icon telemetry counters are wired in map runtime counters.
  - Phase 1 default medium fallback policy is active while preserving unknown semantic truth.
  - Phase 2 bounded parallel + fairness hardening is active.
  - Phase 3 sticky icon anti-flicker projection cache is implemented.
  - Phase 4 rollout flag + rollback/kill-switch path is implemented in ADS-B preferences and overlay runtime wiring.
- Targeted test evidence (green):
  - `.\test-safe.bat :feature:map:testDebugUnitTest --tests com.trust3.xcpro.adsb.ui.AdsbAircraftIconMapperTest --tests com.trust3.xcpro.adsb.ui.AdsbAircraftIconTest --tests com.trust3.xcpro.map.AdsbGeoJsonMapperTest --tests com.trust3.xcpro.adsb.metadata.AircraftMetadataRepositoryImplTest --tests com.trust3.xcpro.adsb.metadata.AdsbMetadataEnrichmentUseCaseTest`

## 1) Objective

Deliver production-grade ADS-B icon behavior where unresolved aircraft render with
`ic_adsb_plane_medium.png` as the default map icon, while preserving architecture
rules, deterministic behavior, and clear observability.

## 2) Problem Summary

Current behavior shows `ic_adsb_unknown` (question-mark icon) when:
- OpenSky category is ambiguous/missing (`null`, `0`, `1`, `13`), and
- metadata (`typecode` / `icaoAircraftType`) is not yet available.

This produces:
- low-confidence visual UX (question marks in live traffic),
- delayed icon upgrades when metadata hydration is slow.

## 3) Architecture Contract (Non-Negotiable)

1. SSOT ownership remains unchanged.
- Target truth owner: ADS-B repository/store output (`AdsbTrafficUiModel`).
- Metadata truth owner: aircraft metadata repository.
- UI icon is a projection only; it must not overwrite domain truth.

2. Dependency direction remains `UI -> domain -> data`.

3. Replay determinism remains unchanged.
- No wall-time logic in domain classification paths.
- No random icon decisions.

4. Honest-output requirement remains intact.
- "Unknown classification" remains explicit in data/details.
- Map fallback icon changes to medium plane for unresolved visual state only.

## 4) Proposed Default-Icon Policy

1. Domain classification stays as-is (`Unknown` remains a valid domain class).
2. Map rendering fallback changes:
- `Unknown` map icon -> `ic_adsb_plane_medium.png`.
3. Authoritative non-fixed-wing categories remain authoritative:
- helicopter, glider, balloon, parachutist, hangglider, drone, heavy.
4. Selected-target details still show unknown metadata availability truth.

## 5) Scoring Rubric

Each phase is scored `/100` using:
- Architecture compliance: 30
- UX outcome: 25
- Performance/latency: 20
- Test confidence: 15
- Rollout safety/observability: 10

## 6) Deep Pass Baseline and Target Scores

This code pass uses executable phase gates (not lightweight advisory estimates).

| Phase | Baseline (deep pass) | Target after phase closure | Status |
|---|---:|---:|---|
| Phase 0 | 89/100 | 96/100 | Implemented |
| Phase 1 | 94/100 | 96/100 | Implemented |
| Phase 2 | 94/100 | 97/100 | Implemented |
| Phase 3 | 74/100 | 97/100 | Implemented |
| Phase 4 | 71/100 | 98/100 | Implemented (basic build gate complete) |

## 7) Phased Plan (All Phase Gates >95)

### Phase 0 - Baseline Lock + Runtime Telemetry Contract (89 -> 96)

Scope:
- Lock behavior with deterministic tests and telemetry-backed counters.
- Add runtime telemetry contracts:
  - `adsb_icon_unknown_render_count`
  - `adsb_icon_resolve_latency_ms` (first seen -> first resolved non-default icon)
  - `adsb_metadata_lookup_latency_ms`
- Keep timebase explicit:
  - icon resolve timing uses injected monotonic clock.
  - no wall-time math in domain/projection decisions.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
- `feature/map/src/test/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/adsb/metadata/AircraftMetadataRepositoryImplTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/AdsbGeoJsonMapperTest.kt`

Exit criteria:
- Runtime counters can be sampled in debug diagnostics and are monotonic-safe.
- Baseline metrics captured in controlled replay/debug run.
- No architecture drift.

Target phase score: **96/100**

### Phase 1 - Default Medium Icon Policy (Truth Preserved) (94 -> 96)

Scope:
- Implement visual fallback mapping:
  - `AdsbAircraftClass.Unknown` -> `ic_adsb_plane_medium.png` in map icon projection.
- Keep domain/data truth unchanged (`Unknown` remains semantic state).
- Add projection-level tests for unknown map icon id and details truth visibility.
- Keep non-fixed-wing authoritative precedence untouched.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`
- `feature/map/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIcon.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
- icon/projection tests and docs.

Exit criteria:
- Question-mark icon no longer appears for unresolved map targets.
- Non-fixed-wing icon classes still render correctly.
- Tests prove unknown truth is preserved in details state and map projection.

Target phase score: **96/100**

### Phase 2 - Metadata Hydration Latency and Fairness Hardening (94 -> 97)

Scope:
- Keep bounded parallel lookup path for on-demand metadata (`max batch`, `parallelism`, cooldown).
- Add no-starvation lookup fairness under repeated high-churn lists.
- Preserve cooldown, in-flight guards, and retry policy.
- Add measurable latency/throttle counters for regression detection.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt`
- `feature/map/src/test/java/com/trust3/xcpro/adsb/metadata/AircraftMetadataRepositoryImplTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/adsb/metadata/AdsbMetadataEnrichmentUseCaseTest.kt`

Exit criteria:
- p95 metadata lookup latency reduced versus Phase 0 baseline.
- No rate-limit regression (error/throttle counters stable).
- Repeated lookup cycles cannot starve tail ICAO24 entries.

Target phase score: **97/100**

### Phase 3 - Sticky Icon Stability (Anti-Flicker) (74 -> 97)

Scope:
- Add deterministic session-level sticky icon projection cache by ICAO24.
- Rule:
  - If current sample classifies `Unknown` and prior strong icon exists, hold prior icon for bounded TTL.
  - Authoritative non-fixed-wing category classes always override sticky hold.
- Use injected monotonic clock and bounded cache cleanup.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- dedicated sticky TTL and downgrade-rule tests in `feature/map/src/test/java/com/trust3/xcpro/map/`.

Exit criteria:
- Reduced icon flicker and fewer fallback oscillations.
- Deterministic outputs for same ordered inputs.
- No replay determinism regressions.

Target phase score: **97/100**

### Phase 4 - Rollout Safety + Release Gate (71 -> 98)

Scope:
- Feature-flag rollout (`defaultMediumUnknownIconEnabled`).
- Add rollback kill switch and dashboard thresholds:
  - unknown-truth mismatch incidents = 0
  - metadata resolve latency guardrail
  - error-rate regression guardrail
- Publish release-safe observability contract for icons and metadata hydration.

Exit criteria:
- Required gates pass:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Optional connected tests when device/emulator available.
- Rollback procedure documented and tested in debug flow.

Target phase score: **98/100**

## 8) Agent Execution Contract (Automation)

Per phase, autonomous agent must produce:
- changed files list
- explicit test evidence and command outputs summary
- score breakdown by rubric bucket
- remaining deductions and owner

Stop conditions:
- architecture rule conflict
- replay determinism conflict
- repeated gate failure after two fix attempts
- scope creep outside ADS-B without explicit user request

## 9) Risk Register

1. Risk: visual default may hide true uncertainty from users.
- Mitigation: keep unknown status explicit in details and metadata availability labels.

2. Risk: parallel lookup burst may increase throttling.
- Mitigation: bounded concurrency + cooldown + throttle telemetry.

3. Risk: sticky cache may mask valid class changes.
- Mitigation: strict TTL and authoritative-category override precedence.

## 10) Verification Matrix

Minimum commands:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Windows file-lock resilient test loop:

```bat
test-safe.bat :feature:map:testDebugUnitTest
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 11) Final Quality Target

Target release score after Phase 4: **>= 96/100** with:
- no architecture violations,
- no determinism regressions,
- measurable reduction in icon fallback time and unresolved-icon user impact,
- every phase gate closed at **>95/100**.
