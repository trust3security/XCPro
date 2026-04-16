
# KNOWN_DEVIATIONS.md

Audit date: 2026-02-18

This file lists active deviations from `ARCHITECTURE.md` and `CODING_RULES.md`.
Each entry must include an issue ID, owner, and expiry date.

Lifecycle rules:
- This file is the only allowed location for temporary rule exceptions.
- Expired entries block merge until they are removed or explicitly renewed.
- Entries must be updated when mitigation, scope, or exit criteria changes.

README consistency rule:
- `docs/ARCHITECTURE/README.md` must never duplicate or summarize deviation status.
- This file is the only authoritative deviation ledger.

## Current deviations

1) Legally required weather provider literals and attribution link usage in implementation internals
- Rule: Vendor neutrality (`ARCHITECTURE.md`: no vendor names in production strings or public APIs).
- Issue: RULES-20260220-11
- Introduced: 2026-02-20
- Approved by: XCPro Team (backfilled 2026-03-14)
- Owner: XCPro Team
- Next review: 2026-05-15
- Expiry: 2026-06-30
- Scope:
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRainAttribution.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRainTileUrlBuilder.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
  - `feature/weather/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- Rationale:
  - Weather provider endpoints/host validation and legally required source attribution require provider literals.
  - UI/public API naming remains provider-neutral outside explicit attribution/compliance surfaces.
  - Deviation is bounded to weather integration internals and required attribution link handling.

Compliance note (2026-02-20):
- This deviation is time-boxed and must be removed by either:
  - architecture wording update that explicitly allows legally required attribution literals, or
  - provider abstraction changes that remove direct literals from production code.
- Deep-pass findings and closure steps are tracked in
  `docs/RAINVIEWER/01_RAINVIEWER_INDUSTRY_HARDENING_PLAN_2026-02-20.md`.

2) MAPSCREEN `pkg-e1` fails `MS-UX-01` threshold gate in strict completion contract runs
- Rule: Map visual SLO gate (`CODING_RULES.md` section `1A Enforcement`; `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`).
- Issue: RULES-20260305-12
- Introduced: 2026-03-05
- Approved by: XCPro Team (backfilled 2026-03-14)
- Owner: XCPro Team
- Next review: 2026-04-10
- Expiry: 2026-04-15
- Scope:
  - `artifacts/mapscreen/phase3/pkg-e1/20260305-193049/`
  - `artifacts/mapscreen/phase3/pkg-e1/20260305-195205/`
  - `artifacts/mapscreen/phase3/pkg-e1/20260405-071602/`
  - `artifacts/mapscreen/phase3/pkg-e1/20260405-190316/`
  - `scripts/qa/run_mapscreen_completion_contract.ps1` (phase-5 gate evidence)
- Risk:
  - Map interaction smoothness does not meet required `MS-UX-01` p95/p99/jank thresholds under automated gesture capture.
  - Emulator-only Tier B evidence is noisy enough to produce misleading optimization targets and can trigger avoidable production perf churn after Tier A is already green.
- Mitigation:
  - Keep phase-2 package lanes (`pkg-d1`, `pkg-g1`, `pkg-w1`) green and isolated.
  - Continue targeted `pkg-e1` runtime work with strict Tier A/B evidence capture before release promotion.
  - Treat emulator Tier B as smoke/relative evidence only unless it is confirmed by a repeatable trace or by a trustworthy physical/remote-device Tier B target.
  - Do not open new production perf slices from emulator-only Tier B failures when current physical-device Tier A evidence is already green.
  - Do not mark `pkg-r1` green unless this deviation is removed or an approved release exception is explicitly documented.
  - Defer final `MS-UX-01` closure to next focused optimization cycle (tracked in `docs/MAPSCREEN` execution backlog/contract docs).
- Removal steps:
  - Deliver additional `MS-UX-01` runtime optimizations and re-run strict contract without allow-failure flags.
  - Replace emulator-only Tier B closure evidence with a trustworthy physical-device or approved remote-device Tier B capture before using Tier B as the final promotion gate.
  - Produce a `pkg-e1` artifact where phase-5 verification reports `ready_for_promotion`.
  - Remove this entry after successful strict completion contract run (phases `0..8`) for the same code line.
- Exit criteria:
  - `verify_mapscreen_package_evidence.ps1 -PackageId pkg-e1` passes with no failed SLOs.
  - `run_mapscreen_completion_contract.ps1` reaches phase 8 with no allow-failure flags.

3) Production logging drift bypasses the canonical redaction and hot-path gating seam
- Rule:
  - Logging architecture and privacy-safe production logging (`ARCHITECTURE.md` section "Logging Architecture"; `CODING_RULES.md` section `13 Logging Rules`).
- Issue: RULES-20260314-17
- Introduced: 2026-03-14
- Approved by: XCPro Team (backfilled 2026-03-14)
- Owner: XCPro Team
- Next review: 2026-04-15
- Expiry: 2026-05-15
- Execution plan:
  - `docs/refactor/Logging_Architecture_Standardization_Phased_IP_2026-03-14.md`
- Scope:
  - `core/common/src/main/java/com/example/xcpro/core/common/logging/AppLogger.kt`
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt`
  - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - hotspot clusters in `feature/variometer`, `feature/map`, `feature/map-runtime`, `feature/tasks`, and `dfcards-library`
- Risk:
  - Privacy-sensitive production logs currently print exact coordinates, names, IDs, and session identifiers directly from feature code.
  - Hot-path debug logs remain inconsistent and can add avoidable string-formatting/log-I/O overhead in runtime-heavy paths.
  - Review quality is weaker because logging policy is still decided ad hoc at callsites instead of one owned seam.
- Mitigation:
  - Harden `AppLogger` as the canonical production logging boundary and make its non-authoritative infra-state contract explicit.
  - Remove or redact the highest-risk privacy-sensitive raw logs first.
  - Migrate hotspot clusters incrementally instead of doing blind repo-wide replacement.
  - Add static enforcement for new production raw `Log.*` drift with narrow platform-edge exceptions only.
- Removal steps:
  - Complete the phased remediation in `Logging_Architecture_Standardization_Phased_IP_2026-03-14.md`.
  - Eliminate scoped privacy-sensitive raw `Log.*` callsites or route them through explicit redaction/gating.
  - Add enforcement that blocks new production raw `Log.*` except for documented allowlisted edges.
  - Remove this entry once the canonical seam is hardened and the scoped drift is closed.
- Exit criteria:
  - No scoped privacy-sensitive production logs bypass the canonical redaction/removal policy.
  - `AppLogger` has explicit contract coverage for redaction/gating behavior.
  - New raw production `Log.*` drift is blocked by automation except for narrow documented platform-edge exceptions.
