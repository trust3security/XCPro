# REFACTOR_LOCATION_MANAGER.md

## Purpose
Reduce `LocationManager` size while keeping all behavior and architecture rules intact.
This is a UI-layer refactor only (no SSOT changes, no business logic moves).

## Ownership
- Owner: Map/UI layer
- Reviewers: Map + Sensors maintainers

## SSOT / Data Flow
- Sensors -> Repositories -> MapStateStore (SSOT)
- LocationManager reads MapStateStore (via MapStateReader) and renders MapLibre UI only
- No new state owners introduced

## Phase Plan
### Phase 1 (safe extraction)
1) Extract raw fix building into `LocationFeedAdapter`
2) Extract display smoothing + pose selection into `DisplayPosePipeline`
3) Move `DisplayClock` to its own file
4) Keep camera logic in `LocationManager`
5) Add unit tests for time base mapping in `LocationFeedAdapter`

### Phase 2 (optional follow-up)
1) Extract camera tracking + padding logic into a `CameraTracker`
2) Extract return/recenter state into a `MapNavigationState` wrapper

## Tests (Phase 1)
- `LocationFeedAdapterTest`:
  - monotonic vs wall time base selection
  - replay time base mapping
  - heading + track wiring

## Time Base Rules (must remain unchanged)
- Live fixes: use monotonic timestamps when present; fall back to wall time
- Replay fixes: use replay timestamps only
- Never mix time bases inside fusion logic

