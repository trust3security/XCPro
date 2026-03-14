# Logging Architecture Standardization Phased IP

## 0) Metadata

- Title: Standardize production logging through one privacy-safe, reviewable boundary
- Owner: XCPro Team
- Date: 2026-03-14
- Issue/PR: TBD
- Status: Approved
- Execution rules:
  - This is a privacy, observability, and review-discipline track, not a repo-wide blind replacement exercise.
  - Do not translate low-value log spam from `Log.*` to `AppLogger`; remove unnecessary logs instead.
  - Harden the canonical logging seam before broad migration.
  - Land one hotspot cluster at a time with behavior parity and narrow rollback.
  - Keep direct `Log.*` only for explicit platform/bootstrap or low-level wrapper edges.
  - Do not mix this plan with singleton scope lifecycle cleanup, identity/time cleanup, or unrelated feature behavior changes.
- Progress:
  - Phase 0 complete: logging drift, privacy risk, and hotspot clusters were baselined; temporary deviation recorded.
  - Phase 1 complete: `AppLogger` now has an explicit seam contract, lazy rate-limited/sampled debug helpers, locale-stable coordinate formatting, and focused contract coverage in app-side JVM tests.
  - Phase 2 complete: the scoped privacy-risk files no longer print exact profile identifiers, waypoint names/coordinates, or session IDs directly from raw `Log.*` callsites.
  - Phase 3 in progress: the `feature/variometer` audio hotspot cluster now routes production logging through `AppLogger`, uses shared rate limiting for recurring loop/focus/audio-track failure paths, and no longer contains raw `Log.*` callsites in `com.example.xcpro.audio`.
  - Phase 3 in progress: the map gesture/runtime cluster removed pure gesture drag spam from `VariometerWidget`, moved `LocationSensorsController` to rate-limited operational logging through `AppLogger`, and replaced raw `MapUserInteractionController` logs with minimal canonical diagnostics.
  - Phase 3 in progress: the remaining map widget gesture cluster removed low-value dropdown and drag debug chatter from `FlightModeMenu`, `FlightModeMenuImpl`, `SideHamburgerMenu`, and `SideHamburgerMenuImpl` without changing widget behavior.
  - Phase 3 in progress: the map bootstrap cluster reduced `MapInitializer` to canonical bootstrap/timeout/error diagnostics only, deleting gesture chatter and moving retained logs onto `AppLogger`.
  - Phase 3 in progress: the map lifecycle/bootstrap cleanup removed raw style-reload and surface-lifecycle chatter from `MapOverlayRuntimeMapLifecycleDelegate` and `MapLifecycleSurfaceAdapter`, leaving only failure diagnostics on the canonical seam.
  - Phase 3 in progress: the map-runtime overlay-ops/lifecycle cleanup removed raw success chatter from `MapOverlayManagerRuntimeBaseOpsDelegate` and `MapLifecycleManager`, leaving only failure diagnostics on the canonical seam and fixing waypoint-refresh error handling so coroutine failures are logged at the real owner boundary.
  - Phase 3 in progress: the task-panel cluster removed raw task-panel/UI chatter from `MapTaskScreenManager`, leaving only save-failure diagnostics on the canonical seam.
  - Phase 3 in progress: the vario service-runtime cluster removed raw lifecycle and IGC session chatter from `VarioServiceManager`, leaving only operational readiness warnings and true failures on the canonical seam.
  - Phase 3 in progress: the replay coordinator cluster removed raw demo-start chatter and coordinate-bearing racing debug logs from `MapScreenReplayCoordinator`, leaving only replay-start failures on the canonical seam.
  - Phase 3 in progress: the card-preferences cluster removed raw profile/template/card debug dumps from `CardPreferences`, leaving only a canonical legacy-template migration warning.
  - Phase 4 complete: `archGate` now freezes production raw `Log.*` drift with a count-based allowlist in `config/quality/raw_log_allowlist.txt`, so new files and count increases fail fast without forcing repo-wide cleanup in the same patch.

## 1) Scope

- Problem statement:
  - Production logging is split between the canonical logger and uncontrolled raw `Log.*` callsites.
  - Static code pass results found roughly:
    - `Log.*` in about `100` production files / `482` callsites
    - `AppLogger` usage in about `34` production files / `194` references
    - `0` production uses of `AppLogger.redactLatLon(...)` or `AppLogger.redactCoord(...)`
  - Current raw-log drift includes privacy-sensitive and review-noisy examples such as:
    - `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt`
    - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
    - `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - Hot-path and hotspot clusters remain concentrated in:
    - `feature/variometer`
    - `feature/map`
    - `feature/map-runtime`
    - `feature/tasks`
    - `dfcards-library`
  - The repo already has a logging seam in `core/common`, but callsites still decide privacy, debug-only behavior, rate limiting, and sampling ad hoc.
- Why now:
  - Application quality:
    - raw logs currently print live coordinates, IDs, and names in production paths, which weakens privacy safety and release hygiene.
  - Developer efficiency:
    - reviewers currently have to re-check every new log for debug gating, redaction, and hot-path safety instead of trusting one owned seam.
  - Operational efficiency:
    - hotspot logs still do uncontrolled string formatting and log I/O where shared rate limiting or deletion would be safer and cheaper.
- In scope:
  - Canonical production logging boundary (`AppLogger`) and its contract
  - Privacy-sensitive production logs containing coordinates, names, IDs, session identifiers, or similar metadata
  - Hot-path logging in sensor, replay, map, traffic, and audio-adjacent code
  - Static enforcement for new raw `Log.*` drift in production code
  - Explicit narrow exception categories for allowed direct platform logging
- Out of scope:
  - Replacing every remaining raw log in one pass
  - Changing user-visible feature behavior
  - Repo-wide scope/lifecycle cleanup
  - Building a new generic observability framework
  - Analytics, crash reporting, or telemetry pipelines outside the current logging boundary
- User-visible impact:
  - None intended
  - Debug builds may produce fewer noisy logs and more consistently redacted logs in sensitive paths

## 2) Seam Pass Findings

### 2.1 Concrete Drift

| Seam | Current drift | Why it matters |
|---|---|---|
| Raw production logging | `Log.*` still dominates many production files | privacy, review, and operational policy are decided ad hoc |
| Redaction seam | `AppLogger.redactLatLon(...)` / `redactCoord(...)` exist but are unused in production | the only central redaction path is currently bypassed |
| Hot-path gating | shared `rateLimit(...)` / `sample(...)` helpers exist but are used only selectively | high-frequency code still risks spam and wasted log I/O |
| Canonical logger contract | `AppLogger` is a mutable infra singleton with internal rate-limit state | the canonical seam itself needs a documented, tested boundary |

### 2.2 Existing Seams To Reuse

- `core/common/src/main/java/com/example/xcpro/core/common/logging/AppLogger.kt`
  - existing canonical logging seam
  - already provides debug gating, rate limiting, sampling, and coordinate redaction helpers
- Existing healthy callsite patterns:
  - `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/DistanceCirclesOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayControllerRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`

### 2.3 Dependency Barrier Check

- No dependency barrier blocks adoption in the main hotspot modules.
- `feature/map`, `feature/tasks`, and `feature/variometer` already depend on `:core:common`.

## 3) Target Logging Standard

### 3.1 Ownership Contract

| Concern | Authoritative owner | Required callsite shape | Forbidden pattern |
|---|---|---|---|
| Production Kotlin logging policy | `AppLogger` boundary in `core/common` | route through canonical logger or documented platform-edge exception | arbitrary raw `Log.*` in feature/app/domain code |
| Sensitive coordinate / ID / name logging | canonical logger redaction helpers or log removal | explicit redaction or deletion | direct raw interpolation of sensitive values |
| Hot-path log frequency | canonical logger sampling / rate limiting | explicit gated emission | unconditional repeated debug logging in high-frequency paths |
| Platform/bootstrap edge logging | narrow documented edge files | direct `Log.*` only when Android/platform wrapper constraints justify it | feature-level convenience use of raw `Log.*` |
| Temporary debug investigations | explicit removal note or tracked exception | bounded short-lived raw logging | untracked debug logs left in production sources |

Required rules for this track:
- Sensitive values must be redacted, coarsened, or removed; exact coordinates and identity metadata must not be casually logged in production sources.
- If a log does not need to survive review, remove it instead of migrating it.
- Hot-path logs must be rate-limited, sampled, or deleted.
- `AppLogger` may remain an infrastructure singleton only if its non-authoritative mutable state is documented and test-covered.
- New production raw `Log.*` usage must be blocked unless it matches an approved exception category.

### 3.2 Allowed Exception Categories

Direct `Log.*` remains acceptable only in:
- Android/bootstrap lifecycle edges where introducing the canonical logger is disproportionate
- low-level wrappers/adapters whose only job is to bridge platform logging calls
- temporary debug investigations with explicit inline removal notes or tracked deviation coverage

Not acceptable:
- feature-layer convenience logging
- hot-path sensor/replay/audio/map loops
- privacy-sensitive values logged directly from feature code

### 3.3 Generic Data Flow

Before:

```text
feature/app code
  -> direct Log.*
  -> each callsite decides privacy/debug/rate-limit policy itself
```

After:

```text
feature/app code
  -> AppLogger (or approved platform-edge exception)
  -> one owned policy for gating, redaction, sampling, and rate limiting
```

## 4) Architecture Contract

### 4.1 Ownership and Boundary Table

| Responsibility | Owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| logging policy | `AppLogger` boundary | stable helper methods and documented exception rules | per-feature informal logging policy |
| coordinate redaction | `AppLogger` helper surface | explicit redact helper usage | direct raw coordinate interpolation |
| hot-path gating policy | `AppLogger` helper surface + narrow callsite keys | shared rate-limit/sample helpers | repeated ad hoc timing logic at callsites |
| platform logging exceptions | plan/deviation + narrow edge files | explicit allowlist rationale | broad untracked raw `Log.*` use |

### 4.2 Dependency Direction

Required flow:

`feature/app code -> core/common logging seam -> android.util.Log`

Boundary rules:
- Do not add feature-to-feature logging wrappers.
- Do not route logging through ViewModels or repositories as a side-channel API.
- Do not introduce a second production logger abstraction without an ADR.

### 4.3 Time Base

This is not a time-base redesign.

Rules:
- Callsites must not invent their own timing state for log throttling when `AppLogger` already owns that concern.
- If `AppLogger` timing behavior changes, it must stay non-authoritative and test-covered.

### 4.4 Threading and Cadence

- Hot-path logging in map, replay, sensor, traffic, and audio paths must be treated as performance-sensitive.
- Deleting logs is preferred over wrapping spam in infrastructure.
- Where logging is still needed in hot paths, use explicit shared gating.

### 4.5 Enforcement Coverage

| Risk | Rule reference | Guard type | File/test |
|---|---|---|---|
| new raw `Log.*` drift | `ARCHITECTURE.md` logging architecture; `CODING_RULES.md` logging rules | `enforceRules` / arch-gate + review | static grep/script allowlist |
| sensitive logs bypass redaction | privacy-safe production logging contract | targeted grep + review + focused tests | scoped hotspot files + logger tests |
| canonical logger remains underspecified | stateless-object / infra-state rules | unit tests + KDoc/review | `AppLogger.kt` |
| hot-path spam survives migration | hot-path logging rules | targeted tests + review + grep | hotspot clusters |

## 5) Implementation Phases

### Phase 0 - Contract lock and baseline

- Goal:
  - Freeze the logging standard, hotspot inventory, and allowed-exception categories before code changes begin.
- Files to change:
  - plan/deviation docs only
- Tests to add/update:
  - none
- Exit criteria:
  - target logging contract is documented
  - hotspot clusters are identified
  - temporary deviation is recorded
- Completion note:
  - Completed 2026-03-14 by baselining raw-log drift, documenting the target contract, and recording the temporary deviation.

### Phase 1 - Canonical seam hardening

- Goal:
  - Make `AppLogger` an explicitly acceptable professional boundary instead of an implicit mutable utility.
- Files to change:
  - `core/common/src/main/java/com/example/xcpro/core/common/logging/AppLogger.kt`
  - logger unit tests in `core/common` or the owning module
  - docs only if minor clarification is required
- Tests to add/update:
  - logger tests covering:
    - non-fatal JVM behavior
    - redaction helper output
    - rate limiting behavior
    - sampling contract boundaries
- Exit criteria:
  - `AppLogger` has an explicit tested contract
  - any mutable internal infra state is documented as non-authoritative
  - sensitive-callsite migration can proceed without inventing new helper variants ad hoc
- Completion note:
  - Completed 2026-03-14 by documenting `AppLogger` as the allowed infrastructure singleton, adding lazy `dRateLimited(...)` and `dSampled(...)` helpers for future hotspot migration, switching coordinate formatting to locale-stable output, and locking the seam with focused JVM tests in `app/src/test`.

### Phase 2 - Privacy hotspot remediation

- Goal:
  - Remove or centralize the highest-risk raw logs that print coordinates, names, IDs, or session identifiers.
- Files to change:
  - `feature/tasks/src/main/java/com/example/xcpro/tasks/racing/turnpoints/FinishLineDisplay.kt`
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/calculations/OpenMeteoElevationApi.kt`
  - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
  - `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - any narrow supporting logging helpers needed by the chosen callsite shape
- Tests to add/update:
  - targeted regression tests where logging helpers change code shape materially
  - logger tests if new redaction helper surfaces are added
- Exit criteria:
  - scoped privacy-sensitive raw logs are removed or routed through explicit redaction/gating
  - no exact coordinate / ID / profile-name interpolation remains in the scoped production files
- Completion note:
  - Completed 2026-03-14 by removing raw coordinate/name dumps from `FinishLineDisplay`, routing `OpenMeteoElevationApi` through `AppLogger`, removing profile ID/name logging from `MainActivityScreen`, and dropping the session ID from the WeGlide prompt log in `VarioServiceManager`.

### Phase 3 - Hotspot cluster migration

- Goal:
  - Reduce uncontrolled logging in the largest hotspot files and modules without broad churn.
- Files to change:
  - top hotspot clusters first, likely including:
    - `feature/variometer/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt`
    - `feature/variometer/src/main/java/com/example/xcpro/audio/VarioToneGenerator.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/LocationSensorsController.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/VariometerWidget.kt`
    - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapUserInteractionController.kt`
- Tests to add/update:
  - targeted tests only where logging helper changes affect branching or guard conditions
  - hotspot grep/count evidence attached in review notes
- Exit criteria:
  - top hotspot files no longer use uncontrolled raw `Log.*` for feature-level debug noise
  - kept hot-path logs use shared rate limiting/sampling or are explicitly justified as platform edges
- Progress note:
  - First cluster completed 2026-03-14 in `feature/variometer/src/main/java/com/example/xcpro/audio`.
  - Raw `Log.*` callsites in the audio slice were reduced from `49` to `0` by migrating:
    - `VarioAudioEngine.kt`
    - `VarioToneGenerator.kt`
    - `VarioBeepController.kt`
    - `VarioAudioController.kt`
    - `AudioFocusManager.kt`
  - Repeated audio-engine stats, focus-denied warnings, beep-loop failures, and AudioTrack reset/write failures now use shared rate limiting instead of uncontrolled per-loop logging.
  - Second cluster completed 2026-03-14 across:
    - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/VariometerWidget.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/LocationSensorsController.kt`
    - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapUserInteractionController.kt`
  - The widget now emits no gesture debug spam, the sensor controller keeps only rate-limited operational logs, and the runtime controller keeps only minimal return/recenter diagnostics through `AppLogger`.
  - Third cluster completed 2026-03-14 across:
    - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/FlightModeMenu.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/FlightModeMenuImpl.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/SideHamburgerMenu.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/SideHamburgerMenuImpl.kt`
  - The menu widgets now emit no drag or dropdown debug chatter; the cluster was closed by deleting low-value logs instead of re-routing them through `AppLogger`.
  - Fourth cluster completed 2026-03-14 in:
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - `MapInitializer` now keeps only bootstrap/timeout/error diagnostics through `AppLogger`; stale style, idle-camera, gesture begin/end, and return-button chatter were deleted instead of migrated.
  - Fifth cluster completed 2026-03-14 across:
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeMapLifecycleDelegate.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleSurfaceAdapter.kt`
  - The lifecycle/bootstrap seam now keeps only error or warning diagnostics that belong to lifecycle owners; raw style-reload, overlay-init, map-view lifecycle, and snapshot-success chatter were deleted instead of migrated.
  - Sixth cluster completed 2026-03-14 across:
    - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeBaseOpsDelegate.kt`
    - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt`
  - The map-runtime overlay/lifecycle seam now keeps only failure diagnostics through `AppLogger`; success/cancel/replay-skip chatter was removed, and waypoint refresh now logs coroutine failures inside the launched owner scope instead of pretending synchronous success.
  - Seventh cluster completed 2026-03-14 in:
    - `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt`
  - The task-panel seam now emits no raw task-panel lifecycle/UI chatter; only save failure remains, through `AppLogger`, so panel state transitions and navigation-toggle noise no longer consume raw-log budget.
  - Eighth cluster completed 2026-03-14 in:
    - `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - The service-runtime seam now keeps only startup/readiness warnings and true failure diagnostics through `AppLogger`; start/stop chatter, GPS cadence success logs, sensor-retry success logs, and IGC session event chatter were removed, and repeated retry warnings now use shared rate limiting.
  - Ninth cluster completed 2026-03-15 in:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`
  - The replay coordinator seam now emits no raw replay-start chatter or coordinate-bearing racing debug logs; only replay-start failures remain, through `AppLogger`, so demo/replay orchestration no longer carries its own ad hoc debug policy.
  - Tenth cluster completed 2026-03-15 in:
    - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt`
  - The persistence seam now emits no raw profile/template/card debug dumps; only the legacy template-ID migration path retains a canonical warning through `AppLogger`, so settings persistence no longer leaks profile IDs, template IDs, or card lists through raw `Log.*`.

### Phase 4 - Static enforcement and allowlist closure

- Goal:
  - Prevent reintroduction of uncontrolled raw logs after the first migrations land.
- Files to change:
  - `scripts/arch_gate.py` and/or `scripts/ci/enforce_rules.ps1`
  - any allowlist/config file needed for narrow platform-edge exceptions
  - `docs/ARCHITECTURE/CODING_RULES.md` only if enforcement wording needs tightening
- Tests to add/update:
  - enforcement script coverage if the repo already tests those paths
  - local verification grep examples in docs/comments where useful
- Exit criteria:
  - new production raw `Log.*` usage is blocked unless allowlisted
  - allowed exception categories are narrow and documented
  - reviewers no longer rely on memory to detect new logging drift
- Completion note:
  - Completed 2026-03-14 by adding count-based raw `Log.*` drift enforcement to `scripts/arch_gate.py`, checking it into `./gradlew enforceRules`, and tracking the bounded current debt in `config/quality/raw_log_allowlist.txt`.

### Phase 5 - Deviation closeout

- Goal:
  - Remove the temporary logging deviation once high-risk privacy leaks are fixed and enforcement is active.
- Files to change:
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - plan doc status/progress
- Tests to add/update:
  - none beyond phase verification
- Exit criteria:
  - no scoped privacy-sensitive raw logs remain
  - canonical logger contract is stable and test-covered
  - raw production logging drift is guarded by enforcement

## 6) Acceptance Criteria

- No privacy-sensitive production logging in the scoped files bypasses the canonical redaction/removal policy.
- `AppLogger` is an explicit, tested, and reviewable canonical logging boundary.
- Hot-path logs in the scoped hotspot clusters are removed or gated consistently.
- New production raw `Log.*` drift is blocked by automation except for narrow documented platform-edge exceptions.
- Reviewers can answer “is this log safe, gated, and intentional?” from one documented seam instead of ad hoc callsite judgment.

## 7) Verification Plan

- Required commands for code phases:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Useful grep checks while executing this plan:

```text
rg -n --glob '!**/test/**' --glob '!**/androidTest/**' "Log\\.(d|e|i|v|w|wtf)\\(" app core feature dfcards-library
rg -n --glob '!**/test/**' --glob '!**/androidTest/**' "AppLogger\\.redactLatLon|AppLogger\\.redactCoord|appLogger\\.redactLatLon|appLogger\\.redactCoord" app core feature dfcards-library
rg -n --glob '!**/test/**' --glob '!**/androidTest/**' "AppLogger\\.rateLimit|appLogger\\.rateLimit|AppLogger\\.sample|appLogger\\.sample" app core feature dfcards-library
```

- Phase evidence expectations:
  - Phase 1: `AppLogger` contract tests
  - Phase 2: hotspot file diff showing privacy-sensitive raw logs removed or redacted
  - Phase 3: hotspot count reduction and targeted regression coverage where needed
  - Phase 4: enforcement diff plus local guard verification
