# Display Pose Smoothing Plan

Purpose: Make the live glider marker more responsive without breaking SSOT,
time-base rules, or navigation correctness. This is UI-only and does not
change navigation logic or task scoring.

Scope:
- DisplayPoseSmoother parameters and selection for live display.
- UI state ownership and wiring for display smoothing profile.
- Replay RAW mode remains as-is (raw fixes).

Non-goals:
- No changes to navigation logic or task transitions.
- No changes to FlightDataRepository or sensor fusion.
- No UI redesign beyond a future toggle (not required for this phase).

References:
- ARCHITECTURE.md (SSOT + time base rules)
- CODING_RULES.md (UI-only logic, no business logic in Compose)
- mapposition.md (display pipeline)

----------------------------------------------------------------------
1) Constraints (hard)

- UI-only change: no navigation logic changes.
- Map display still reads from FlightDataRepository via MapScreenViewModel.
- Time base remains unchanged (live monotonic/wall; replay IGC time).
- Single writer: MapStateStore owns display profile (SSOT for UI-only).

----------------------------------------------------------------------
2) Outcome definition

- Live glider marker feels faster/more precise without disabling smoothing.
- Provide two profiles:
  - SMOOTH (current behavior)
  - RESPONSIVE (reduced smoothing constants)
- Replay RAW mode remains separate and unchanged.

----------------------------------------------------------------------
3) Ownership and data flow (SSOT)

Display smoothing profile is UI-only state:

  MapScreenViewModel (writer)
        |
        v
  MapStateStore.displaySmoothingProfile (SSOT)
        |
        v
  LocationManager -> DisplayPoseSmoother (UI-only)

No changes to repositories or navigation engines.

----------------------------------------------------------------------
4) Phase plan

Phase 0: Plan + docs
- This plan doc.
- Update mapposition.md to describe smoothing profiles.

Phase 1: Configurable smoothing
- Introduce DisplayPoseSmoothingConfig.
- Update DisplayPoseSmoother to accept config (defaults = current).

Phase 2: Profile selection
- Add DisplaySmoothingProfile enum with SMOOTH and RESPONSIVE presets.
- MapStateStore owns displaySmoothingProfile.
- MapScreenViewModel sets a default profile (via feature flag).
- LocationManager re-initializes DisplayPoseSmoother when profile changes.

Phase 3: Tests
- Unit test that DisplaySmoothingProfile maps to expected config.
- Existing DisplayPoseSmoother tests must still pass (defaults unchanged).

Phase 4 (optional, later)
- Add a UI toggle in settings to switch profiles.

----------------------------------------------------------------------
5) Proposed parameter changes

Responsive profile targets:
- posSmoothMs: 150 (from 300)
- headingSmoothMs: 120 (from 250)
- deadReckonLimitMs: 250 (from 500)
- staleFixTimeoutMs: unchanged

Rationale:
- Smaller time constants increase responsiveness while preserving smoothing.
- Reduced prediction horizon limits overshoot in live.

----------------------------------------------------------------------
6) Definition of done

- Live marker is visibly more responsive with RESPONSIVE profile.
- Replay RAW mode unchanged and still aligns with navigation events.
- No navigation code changes.
- Unit tests pass.
