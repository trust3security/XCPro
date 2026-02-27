# HOTSPOTS - Index

This folder is the execution and maintenance contract for the OGN thermal hotspot feature.

## Current status (2026-02-24)

- General Settings now exposes a `Hotspots` button.
- Hotspots settings open in modal style with the same top-bar icon language used by other modal settings surfaces.
- Retention slider is persisted (`1 hour` .. `All day`) and enforced by repository runtime policy.
- Display share slider is persisted (`5%` .. `100%`) and enforced by repository strongest-first policy.
- Thermal confirmation enforces fake-climb suppression (`cumulative turn > 730 deg`).
- Hotspot publishing enforces one best hotspot per area using distance-based dedupe with active/recent-first winner policy and strength tie-breaks.

Implementation update (2026-02-27):

- Completed: area winner policy now prefers active/recent hotspots inside the 700 m dedupe radius before strength tie-breaks.
- Plan and rollout summaries are documented in `04_ACTIVE_RECENT_WINNER_IMPLEMENTATION_PLAN.md` and `05_ACTIVE_RECENT_WINNER_SUMMARIES.md`.

## Re-pass findings (2026-02-25)

Open issues discovered during code re-pass:

- Hotspots can be enabled while OGN overlay is off, which results in no visible hotspots and a misleading UX.
- Forecast/weather/satellite overlay anchor ordering can place raster/vector layers above thermal circles, making hotspots appear missing.
- `OgnThermalsButton` exists but is not wired in map UI flow; controls are currently bottom-sheet-only.

Re-pass cycle 2 (2026-02-25) confirmed the same issue set; no additional hotspot-display defects were found.

Implementation update (2026-02-25):

- Fixed: turning Hotspots on now auto-enables OGN overlay.
- Fixed: forecast/weather/satellite anchor lists now include thermal layers so overlays do not occlude hotspots.
- Remaining low-severity cleanup: `OgnThermalsButton` is still unused.
- Crash-hardening update: confirmed tracker ID recovery prevents invariant crash, and thermal overlay render path is guarded against runtime map exceptions.

## Read order

1. `docs/ARCHITECTURE/CHANGE_PLAN_HOTSPOTS_THERMALS_2026-02-24.md`
2. `01_IMPLEMENTATION_PLAN.md`
3. `02_MODIFICATION_GUIDE.md`
4. `03_TESTING_AND_REPASS_CHECKLIST.md`
5. `04_ACTIVE_RECENT_WINNER_IMPLEMENTATION_PLAN.md`
6. `05_ACTIVE_RECENT_WINNER_SUMMARIES.md`

## Feature intent (must remain true)

- General Settings has a `Hotspots` entry.
- Hotspots settings open as a modal-style UX (matching existing modal visual language).
- User selects hotspot visibility window from `1 hour` to `All day`.
- User selects hotspot display share from `5%` to `100%` (strongest-first filtering).
- Map Tab 4 control label uses `Hotspots (TH)` terminology (not `Thermals`).
- Turning Hotspots on auto-enables OGN overlay when needed.
- `1 hour` means drop hotspots older than 1 hour.
- `All day` means keep hotspots until local midnight (12:00 AM).
- `5%` means show only the strongest top 5% hotspots; `100%` shows all retained hotspots.
- Fake climb suppression: thermal must include more than `730` degrees cumulative turn.
- Area dedupe: show only one hotspot per local area radius using active/recent-first winner policy, then strength tie-breaks.

## Why the display share slider exists

- Keep the map readable in dense traffic by reducing hotspot clutter.
- Prioritize tactical information by showing strongest climbs first.
- Preserve safety/awareness by keeping policy deterministic and repository-owned.

## Primary code owners

- Hotspot runtime and policy: `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
- Thermal model: `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalModels.kt`
- Display share bounds/clamp: `feature/map/src/main/java/com/example/xcpro/ogn/OgnHotspotsDisplayPercent.kt`
- Preferences: `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- Hotspots settings UI: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/HotspotsSettingsScreen.kt`
- Settings navigation entry: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`

## Architecture guardrails

- Keep MVVM + UDF + SSOT.
- Keep hotspot policy in repository/domain paths, not in Composables.
- Use injected `Clock` time sources; do not mix ad-hoc system calls in policy logic.
- Keep replay behavior deterministic for deterministic inputs.
