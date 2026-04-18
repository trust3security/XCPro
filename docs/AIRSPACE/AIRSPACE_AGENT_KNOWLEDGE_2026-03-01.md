# AIRSPACE Agent Knowledge Base (2026-03-01)

## Purpose

This file is the handoff context for future Codex/agent work on XCPro airspace.
It captures current architecture boundaries, confirmed defects, and safe implementation guidance.

Date: March 1, 2026
Status: Active reference for airspace bugfix/hardening work

## Read First (Required)

Before changing airspace code, read in this order:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

For this airspace effort, also read:

- `docs/refactor/Airspace_Correctness_Compliance_Plan_2026-03-01.md`

## Current Airspace Flow (as implemented)

UI state flow:

`AirspaceViewModel -> AirspaceUseCase -> AirspaceRepository -> AirspaceParser/AirspaceIO`

Map runtime flow:

`MapScreenRootEffects -> MapOverlayManager.refreshAirspace() -> loadAndApplyAirspace() -> MapLibre style layer/source`

Primary files:

- `feature/map/src/main/java/com/trust3/xcpro/airspace/AirspaceViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/airspace/AirspaceUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/utils/AirspaceRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/utils/AirspaceParser.kt`
- `feature/map/src/main/java/com/trust3/xcpro/utils/AirspaceApply.kt`
- `feature/map/src/main/java/com/trust3/xcpro/utils/AirspaceIO.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`

## Confirmed Defects (March 1, 2026)

1. Stale overlay not cleared when all files are disabled.
- `AirspaceApply.kt` returns early on empty enabled files before removing map layer/source.

2. "All classes OFF" intent is unstable.
- Runtime apply path can auto-enable defaults when selected class set is empty.

3. Refresh race can render stale airspace.
- `MapOverlayManager.refreshAirspace()` launches uncancelled concurrent jobs; older apply can finish after newer state.

4. Class-state wipe on temporary disable-all.
- `AirspaceViewModel.reconcileClassStates()` returns empty map when enabled files are empty; this can be persisted and later drift behavior.

5. OpenAir validation too strict.
- `validateOpenAirFile()` requires `DP`, rejecting valid files that use other geometry forms.

6. Parser misses common valid OpenAir formats.
- Strict directive matching and incomplete directive coverage (`V`/`DB` paths incomplete).
- Coordinate parser misses common decimal-minute formats (`DDMM.MMM[N/S]`, `DDDMM.MMM[E/W]`).

7. Import metadata guard weakness.
- `AirspaceIO` assumes `DISPLAY_NAME` availability without robust fallback checks.

8. Low-risk cache correctness gap.
- Class-filter cache key uses hash-only representation (theoretical collision risk).

## Fast Repro Checklist

Use any valid OpenAir file and try:

1. Enable file, confirm airspace appears.
2. Disable all files; confirm overlay is fully removed.
3. Re-enable file after setting all classes OFF; confirm classes stay OFF (no default re-enable).
4. Rapidly toggle file/class states and watch for stale overlay reappearing.
5. Import a file using non-`DP` geometry directives and check acceptance behavior.
6. Import coordinates in decimal-minute format and verify geometry appears.

## Architecture Guardrails (Non-Negotiable)

1. Keep MVVM + UDF + SSOT boundaries.
2. Do not let map runtime helpers persist class state.
3. Keep map style mutations on `Dispatchers.Main.immediate`.
4. Keep parsing/import IO on `Dispatchers.IO`.
5. Avoid hidden global mutable state.
6. Keep replay behavior deterministic (airspace path must remain static/state-driven).

## Implementation Plan Reference

Execution plan is here:

- `docs/refactor/Airspace_Correctness_Compliance_Plan_2026-03-01.md`

That plan includes:

- SSOT and boundary ownership
- phase-by-phase implementation (0-4)
- required tests and enforcement gates
- rollback strategy

## Testing Expectations for Airspace Work

Minimum local checks for non-trivial changes:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Add/maintain focused tests for:

- apply clear semantics
- class selection persistence semantics
- parser directive/coordinate compatibility
- overlay refresh ordering/race safety
- import metadata fallback behavior

## Practical Do/Do-Not List

Do:

- Keep class selection SSOT in ViewModel/repository path.
- Make map apply read-only from persisted state.
- Add regression tests before and after fixes.

Do not:

- Auto-write default class selections from runtime overlay apply code.
- Treat empty selected class set as implicit "show all" unless explicitly intended and tested.
- Assume all OpenAir files contain `DP`.

## Update Rule

If airspace wiring/ownership/semantics change, update this file and
`docs/ARCHITECTURE/PIPELINE.md` in the same PR.
