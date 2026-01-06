# Architecture Decisions Log

## Purpose
Capture what changed, how it changed, and why it changed so future work stays aligned
with ARCHITECTURE.md. Keep this short and add new entries when major refactors land.

## 2026-01-03 Map UDF and SSOT Refactor
What:
- Introduced MapStateStore/MapStateReader/MapStateActions to make the ViewModel the
  single writer for map UI state.
- Added MapCommand flow and MapRuntimeController so MapLibre calls stay in the UI
  layer while state remains in the ViewModel.
- Moved map state mutations out of composables and routed them through MapScreenViewModel.

How:
- MapScreenViewModel now owns MapStateStore and exposes read-only flows.
- MapScreenRoot and managers read state via MapStateReader and send intents via
  MapStateActions.
- Map runtime actions (camera moves, overlays) are applied imperatively by the UI.

Why:
- Enforce UDF and SSOT rules (ARCHITECTURE.md sections 1 and 2).
- Avoid derived state in the UI and prevent multiple state owners.
- Keep high-frequency map interactions smooth without breaking architecture rules.

## 2026-01-03 Replay and Flight Data Refactor
What:
- Split replay models and IO into IgcReplayModels.kt and IgcReplayIo.kt.
- Split FlightDataCalculator into FlightDataCalculatorEngine plus supporting helpers.

How:
- Replay data models and IO now live in isolated files so the controller stays focused
  on orchestration and state updates.
- Flight data fusion logic now lives in a focused engine class with separate helpers
  for logging and calculations.

Why:
- Keep files under the 500-line limit (CODING_RULES.md).
- Improve maintainability and testability by separating IO, orchestration, and math.
- Preserve replay determinism by isolating time-source decisions inside the engine.

## Open Refactors
- REFACTOR.md: map UDF/SSOT follow-ups and cleanup steps.
- REFACTOR_GEOPOINT.md: remove MapLibre types from GPSData and move replay/live location
  selection out of Compose.
