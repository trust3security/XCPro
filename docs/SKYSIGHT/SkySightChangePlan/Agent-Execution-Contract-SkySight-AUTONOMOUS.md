# Agent-Execution-Contract-SkySight.md — XCPro Autonomous Execution Contract (SkySight Integration)

This document is a **Codex-ready**, end-to-end execution contract for implementing SkySight-backed forecast overlays inside XCPro.
It is based on the generic skeleton you provided and is fully populated so an autonomous agent can execute without further checkpoints.

Use together with:
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `AGENTS.md` / `AGENTS` repo rules

Change-plan compliance:
- This contract is intended to be executed as a concrete change plan aligned with
  `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md` for non-trivial feature/refactor work.

---

# 0) Agent Execution Contract (Read First)

## 0.1 Authority
- Execute end-to-end without checkpoint confirmations.
- Ask questions only when blocked by missing information that cannot be inferred from the repo **or** cannot be obtained from SkySight API responses.
- If ambiguity exists, choose the most architecture-consistent option and document assumptions (Section 5).

## 0.2 Responsibilities
- Implement the full change request in Section 1 (treat it as binding).
- Preserve MVVM + UDF + SSOT and dependency direction (`UI -> domain -> data`).
- Keep business logic testable and free of Android/UI framework dependencies unless explicitly required.
- Use injected clocks/time sources in domain/fusion paths (no direct system time in domain).
- Preserve replay determinism and avoid hidden global mutable state.
- Fix build/test/lint failures introduced by the change.
- Do **not** introduce vendor names into production strings or public APIs (vendor-neutral UX + types).

## 0.3 Workflow Rules
- Execute phases in order (Section 2).
- Do not leave partial implementations or TODO placeholders in production paths.
- If tests are modified or removed, justify why this is required by Section 1.
- Update architecture docs when wiring/policy changes (see Section 2.5).

## 0.4 Definition of Done
Work is complete only when:
- All phases are complete and gates are satisfied.
- Acceptance criteria in Section 3 are satisfied.
- Required verification in Section 4 passes (or limitations recorded in Section 4.2).
- Verification evidence table in Section 4.1 is complete.
- Architecture drift self-audit (Section 6) is complete.
- Quality rescore (Section 7) is complete with evidence.
- Manual verification steps are provided (Section 7 final output).

## 0.5 Mandatory Read Order
Read these before implementation (agent must open them in-repo, not rely on memory):
- `AGENTS.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/AGENT.md`

---

# 1) Change Request (Autofilled)

## 1.1 Feature Summary (1-3 sentences)
Implement **provider-backed forecast overlays** in XCPro using SkySight as the first provider.
The user must be able to enable forecast overlays on the map, select a forecast parameter (layer), select forecast day + time, adjust opacity, see a legend, and long-press the map to query the exact value at that location.

## 1.2 User Stories / Use Cases
- As a pilot, I want to view forecast overlays (wind/thermal/cloud/etc.) on my XCPro map so I can decide where to fly today.
- As a pilot, I want to scrub time and day to see how conditions change so I can pick the best launch time/window.
- As a pilot, I want to long-press any point to see the forecast value (for the currently selected parameter/time) so I can validate conditions at a specific area.

## 1.3 Scope and Non-Goals

### In scope (MVP)
- Authentication/session for SkySight:
  - Either secure credential-based login (if required) **or** partner linking (if supported).
  - Persist session state securely; recover gracefully on expiry.
- Fetch/maintain a provider catalog:
  - Supported parameters/layers, time availability, legend metadata.
- Map overlay rendering:
  - Render raster forecast tiles on the XCPro MapLibre map as an overlay.
  - Support layer selection, day/time selection, and opacity.
  - Keep overlay state deterministic and SSOT-owned (no duplicated state in UI/runtime).
- Legend/scale UI:
  - Show legend for current layer/time, including unit labels.
- Exact value query:
  - Long-press the map to request and display the exact value at that point for the current layer/time.
- Robust error and empty-state UX:
  - Not logged in, subscription not entitled, no data for selected time, network issues.
- Tests:
  - Unit tests for domain and state machines, ViewModel tests for state transitions.
- Documentation updates if pipeline wiring changes.

### Out of scope (explicit non-goals for this change)
- Full SkySight “tools” suite (Skew‑T, Windgram, Route Forecast, Wave Cross Section) beyond the MVP point query.
- Offline region prefetch / bulk downloads (optional follow-up plan).
- Any new backend services (unless required as a last-resort to make tiles work with auth headers; see Section 5 ADR).

## 1.4 Constraints
- Modules/layers affected:
  - Map feature/UI (overlay controller + HUD/sheet)
  - Domain (provider-neutral models + use-cases)
  - Data (SkySight adapters + repositories + persistence)
- Performance/battery limits:
  - No per-frame network calls; all tile updates must be debounced to user commits (e.g., slider release or throttled cadence).
  - Do not recreate MapLibre style layers unnecessarily; update in-place.
- Compatibility/migrations:
  - New persisted preferences must be migration-safe (defaults keep feature disabled).
  - Existing map overlay stacking and task rendering must remain unchanged when overlays disabled.
- Safety/compliance constraints:
  - Secure storage for tokens/credentials (encrypted storage).
  - No vendor names in production strings/public APIs.
  - Avoid “mirroring”/redistribution patterns unless explicitly permitted in SkySight partnership terms.
  - API keys and secrets must not be checked into source; use build config + CI secrets.

## 1.5 Inputs / Outputs
Inputs:
- User intents: enable/disable overlays, select parameter, change time/day, change opacity, long-press coordinate.
- Map state: style loaded, viewport changes (for legend scaling if needed), lifecycle transitions.
- Provider data: catalog metadata, tile availability, point value results, auth tokens.

Outputs:
- Map overlay raster layer (rendered forecast tiles).
- Legend UI + units label.
- Pinned callout / bottom sheet showing point value after long-press.
- Logs/metrics (non-sensitive) for error diagnostics and cache behavior (if present).

## 1.6 Behavior Parity (must remain identical)
- When overlays are disabled (default), map rendering, task drawing, and existing overlays behave identically to current release.
- Existing gestures (tap/drag/zoom) must remain unchanged unless forecast overlay is enabled and the long-press gesture is not already claimed by another mode (task editing).

## 1.7 Time Base Declaration (required)
| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Forecast selected time (`ForecastValidTimeUtcMs`) | Wall | Forecast is wall-clock semantic; must be explicit state, not implicit `now()` |
| Default selection time when enabling overlays in live mode | Wall (injected clock) | UX convenience only; must not affect replay determinism |
| Replay “follow time” mode (optional toggle) | Replay | Deterministic mapping in replay mode |
| Token expiry/refresh timing | Wall (data layer injected clock) | Auth lifecycle is wall-time based |
| Cache TTL decisions | Wall (data layer injected clock) | I/O policy; keep out of domain |

## 1.8 SSOT Ownership Declaration
(Agent must implement exactly; if changes required, document in Section 5.)

- Forecast overlay enabled:
  - Authoritative owner: `ForecastPreferencesRepository`
  - Exposed as: `StateFlow<Boolean>`
  - Forbidden duplicates: any mirror `enabled` flags in ViewModel/UI/runtime controller

- Selected parameter/layer:
  - Authoritative owner: `ForecastPreferencesRepository`
  - Exposed as: `StateFlow<ForecastParameterId>`
  - Forbidden duplicates: cached selected parameter in ViewModel/UI/runtime

- Selected day/time:
  - Authoritative owner: `ForecastPreferencesRepository` (persist last used) or `ForecastSelectionRepository` (if kept separate)
  - Exposed as: `StateFlow<ForecastTimeSelection>`
  - Forbidden duplicates: “current time” in runtime overlay controller

- Opacity:
  - Authoritative owner: `ForecastPreferencesRepository`
  - Exposed as: `StateFlow<Float>`
  - Forbidden duplicates: duplicate alpha in multiple state owners

- Auth/session:
  - Authoritative owner: `ForecastAuthRepository`
  - Exposed as: `StateFlow<ForecastAuthState>`
  - Forbidden duplicates: tokens or credential material in ViewModel or UI state

- Provider catalog (parameters, time availability, legend metadata):
  - Authoritative owner: `ForecastCatalogRepository`
  - Exposed as: `StateFlow<ForecastCatalogState>`
  - Forbidden duplicates: UI-managed cached catalog lists

- Point query result:
  - Authoritative owner: `ForecastRepository` (or `ForecastPointRepository` if split)
  - Exposed as: `SharedFlow<ForecastPointResult>` (event-like) or `StateFlow` if the UI needs persistence
  - Forbidden duplicates: storing the same point value in multiple caches without clear ownership

---

# 2) Execution Plan (Agent Owns, Must Execute)

## Phase 0 — Baseline and Safety Net
Goals:
- Identify affected flows and entry points for map overlays and gestures.
- Confirm default behavior is “feature disabled”.
- Add regression coverage so later changes can’t silently alter baseline map behavior.

Tasks:
1) Locate existing overlay orchestration:
   - Find `MapOverlayManager`, `MapOverlayStack`, map style reload handling, and existing overlay patterns (e.g., traffic/airspace).
2) Identify gesture pipeline:
   - Locate long-press behavior; determine conflicts with task editing or other modes.
3) Add “disabled by default” feature flag/pref:
   - Add preference default `false`; ensure nothing changes if never enabled.
4) Add baseline tests:
   - Unit test: default overlay state is disabled and produces no tile overlay spec.
   - ViewModel test: on startup, overlay UI state is Disabled.

Gate:
- No functional changes with overlays disabled.
- Repo builds.

## Phase 1 — Pure Logic Implementation
Goals:
- Provider-neutral domain logic and state machines.
- Deterministic, framework-agnostic logic with unit tests.

Tasks:
1) Domain model:
   - `ForecastParameterId`, `ForecastTimeSelection`, `ForecastOverlayConfig`, `ForecastLegendSpec`, `ForecastTileSpec`, `ForecastPointValue`
2) Define state machines (sealed classes) and transitions:
   - Auth state: LoggedOut / LoggingIn / Ready / Error
   - Catalog state: Loading / Ready / Error
   - Overlay state: Disabled / EnabledLoading / EnabledReady / Error
3) UseCases:
   - `ObserveForecastOverlayUiStateUseCase`
   - `SetForecastEnabledUseCase`
   - `SelectForecastParameterUseCase`
   - `SetForecastTimeUseCase`
   - `SetForecastOpacityUseCase`
   - `QueryForecastValueAtPointUseCase`
4) Determinism rules:
   - Domain must not call system time. Any “default time” selection uses injected clock and occurs in use-case/repo layer with explicit reasoning.
5) Unit tests:
   - Transition coverage for state machines
   - Time clamping and validation (e.g., requested time outside provider availability)
   - TileSpec derivation is pure and stable

Gate:
- Deterministic unit tests pass.

## Phase 2 — Repository and SSOT Wiring
Goals:
- Authoritative state ownership via repositories.
- Clean ports/adapters boundaries for network/persistence.
- No UI/data dependency violations.

Tasks:
1) Ports (domain interfaces):
   - `ForecastAuthPort`
   - `ForecastCatalogPort`
   - `ForecastTilesPort`
   - `ForecastPointQueryPort`
2) Data adapters (SkySight implementation):
   - `SkySightAuthAdapter` (uses `/api/auth` + app key header)
   - `SkySightCatalogAdapter` (fetch layers/parameters + time availability + legend metadata)
   - `SkySightTilesAdapter` (build tile URL templates for MapLibre raster source)
   - `SkySightPointQueryAdapter` (lat/lon/time -> value endpoint)
3) Repositories:
   - `ForecastPreferencesRepository` (DataStore)
   - `ForecastAuthRepository` (secure storage)
   - `ForecastCatalogRepository` (caching, refresh strategy)
   - `ForecastRepository` (composes auth + prefs + catalog and provides overlay-ready state)
4) DI (Hilt):
   - Bind ports to SkySight adapters in data layer modules.
5) Error handling:
   - Model errors explicitly (domain error types), surface as state (no silent failures).
6) Security:
   - Ensure credentials/tokens never appear in logs.
   - Secrets injected via build configs, not hard-coded.

Gate:
- No duplicate state ownership.
- Dependency direction preserved (`UI -> domain -> data`).

## Phase 3 — ViewModel and UI Wiring
Goals:
- ViewModels consume stable domain-facing seams only (no direct managers or low-level infra/data types).
- UI only renders state and emits intents.
- MapLibre objects live only in UI/runtime controllers.

Tasks:
1) ViewModel:
   - Add `ForecastOverlayUiState` to map screen state (or a dedicated overlay VM if the architecture prefers).
   - Hook intents to use-cases.
2) UI:
   - Add a “Forecast overlays” entry point (HUD button) that opens a sheet:
     - Enable toggle
     - Parameter list
     - Day selector
     - Time-of-day slider
     - Opacity slider
     - Legend preview
3) Runtime controller (UI-owned):
   - `ForecastOverlayController` that:
     - Adds/removes MapLibre raster source + raster layer
     - Updates tile source URL template when selection changes
     - Updates opacity dynamically
     - Re-applies on style reload
4) Gesture:
   - Implement long-press to query point value:
     - Trigger intent -> use-case -> repository -> UI event
     - Render pinned callout + dismiss
   - Ensure conflict handling with task editing mode:
     - Either disable point-query long-press while editing OR require “info mode” toggle.

Gate:
- End-to-end behavior works in debug.

## Phase 4 — Hardening and Cleanup
Goals:
- Threading/cancellation correctness.
- Performance and correctness under lifecycle changes.
- Documentation updates and final verification.

Tasks:
1) Performance:
   - Debounce slider-driven updates (avoid thrash).
   - Avoid recreating style layers repeatedly.
2) Threading:
   - IO dispatcher for network/persistence, main-safe UI updates.
3) Lifecycle:
   - Background/foreground, rotation, process restore.
4) Replay determinism:
   - Ensure replay mode does not silently depend on wall time.
   - If “follow replay time” is added, map replay timestamps -> forecast time deterministically.
5) Docs:
   - Update `docs/ARCHITECTURE/PIPELINE.md` if new overlay pipeline wiring is added.
   - If any rule is intentionally violated, record in `KNOWN_DEVIATIONS.md` with expiry.
6) Final cleanup:
   - Remove dead code.
   - Ensure no secrets in code.

Gate:
- Section 4 passes.

## 2.5 Documentation Sync Rules
- If pipeline wiring changes, update `docs/ARCHITECTURE/PIPELINE.md`.
- If policies/rules change, update `docs/ARCHITECTURE/ARCHITECTURE.md` and/or `docs/ARCHITECTURE/CODING_RULES.md`.
- If any rule is intentionally violated, add a time-boxed entry in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue, owner, expiry).

---

# 3) Acceptance Criteria (Autofilled, Agent Must Satisfy)

## 3.1 Functional Criteria
- [ ] Given overlays are disabled by default, when the app starts, then the map renders exactly as before (no extra sources/layers added).
- [ ] Given the user enables forecast overlays, when authenticated and catalog is available, then a raster overlay is visible on the map.
- [ ] Given overlays are enabled, when the user changes parameter/layer, then the overlay updates to the chosen parameter.
- [ ] Given overlays are enabled, when the user changes day/time, then the overlay updates to the selected valid time (with throttling/debounce to avoid thrash).
- [ ] Given overlays are enabled, when the user changes opacity, then the overlay opacity updates immediately.
- [ ] Given overlays are enabled, when the user long-presses on the map, then the app displays the exact forecast value for the current parameter/time at that coordinate.
- [ ] Given authentication expires, when a request is made, then the UI shows a recoverable “sign in again” or auto-refresh behavior without crashing.

## 3.2 Edge Cases
- [ ] No network: user sees “offline / no data” states; overlay does not crash the map.
- [ ] No entitlement / subscription: user sees a clear “not available” state.
- [ ] No data for selected day/time/region: user sees “no data for selection”.
- [ ] Lifecycle transitions:
  - [ ] background/foreground does not lose SSOT state
  - [ ] rotation does not reset selection unexpectedly
  - [ ] process death restore keeps preferences (enabled/layer/time/opacity)
- [ ] Gesture conflicts:
  - [ ] long-press does not break task editing mode; behavior is explicitly defined and tested/manual-verified.
- [ ] Style reload:
  - [ ] overlay re-applies correctly when the map style reloads; no duplicate layers or leaks.

## 3.3 Required Test Coverage
- [ ] Unit tests for business logic and state machines (Phase 1).
- [ ] ViewModel tests for state transitions (Phase 3).
- [ ] Integration/instrumentation tests if the repo has existing infrastructure for map UI; otherwise provide manual verification steps and justify per Section 4.2.

---

# 4) Required Verification (Agent Must Run and Report)

Minimum:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Run when relevant (device/emulator available):
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

Release/CI verification:
- `./gradlew connectedDebugAndroidTest --no-parallel`

Optional quality checks:
- `./gradlew detekt`
- `./gradlew ktlintCheck`

## 4.1 Verification Evidence Table (Required in Final Report)
| Command | Purpose | Result (PASS/FAIL) | Duration | Failures fixed | Notes |
|---|---|---|---|---|---|
| `./gradlew enforceRules` | Architecture/coding rule enforcement | [ ] | [ ] | [ ] | [ ] |
| `./gradlew testDebugUnitTest` | Unit/regression test coverage | [ ] | [ ] | [ ] | [ ] |
| `./gradlew assembleDebug` | Build integrity | [ ] | [ ] | [ ] | [ ] |
| `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` | App-module instrumentation (when relevant) | [ ] | [ ] | [ ] | [ ] |
| `./gradlew connectedDebugAndroidTest --no-parallel` | Full multi-module instrumentation (release/CI parity) | [ ] | [ ] | [ ] | [ ] |

## 4.2 Local Limitations Rule
If any required verification command cannot be run locally, record:
- The exact command not run.
- The exact blocker.
- What was run instead.
- Required follow-up verification step for CI/device with explicit owner.
- Risk of shipping without that check.

---

# 5) Notes / ADR (Agent Must Fill During Execution)

## ADR-001: Tile Authentication Strategy (must decide in Phase 2)
Decision options (choose based on actual repo + MapLibre capabilities):
1) **Preferred**: SkySight provides tile URL templates that authenticate via query token / signed URL.
2) **Acceptable**: MapLibre networking stack supports host-based header injection; add headers for SkySight tile requests only.
3) **Fallback**: Implement an on-device local proxy for tiles (localhost) that injects required headers upstream.
4) **Last resort**: Implement a backend gateway/proxy service (out of scope by default; requires explicit approval and ToS review).

Record:
- Decision:
- Alternatives considered:
- Why chosen:
- Impact/risks:
- Follow-ups:

## ADR-002: Vendor Neutral UX and Strings
- Ensure production UI strings do not mention SkySight explicitly.
- Provider branding (if required) must be handled via an approved deviation entry.

---

# 6) Architecture Drift Self-Audit (Mandatory Before Completion)

Verify:
- [ ] No business logic moved into UI.
- [ ] No UI/data dependency direction violations.
- [ ] No direct system time calls in domain/fusion logic.
- [ ] No new hidden global mutable state.
- [ ] No manager/controller escape hatches exposed through ViewModels/use-cases.
- [ ] Replay remains deterministic for identical inputs.
- [ ] No new rule violations, or deviation recorded in `KNOWN_DEVIATIONS.md`.

---

# 7) Agent Output Format (Mandatory)

At end of each phase:

## Phase N Summary
- What changed:
- Files touched:
- Tests added/updated:
- Verification results:
- Risks/notes:

At final completion:
- Done checklist (Sections 0.4 and 6)
- Quality rescore (with evidence):
  - Architecture cleanliness: __ / 5
  - Maintainability/change safety: __ / 5
  - Test confidence on risky paths: __ / 5
  - Release readiness: __ / 5
- PR-ready summary (what/why/how)
- Manual verification steps (2-5 steps)
