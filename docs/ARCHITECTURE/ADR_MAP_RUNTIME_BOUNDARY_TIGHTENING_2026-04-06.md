# ADR: Map Runtime Boundary Tightening (Phase 5)

- Date: 2026-04-06
- Status: Accepted
- Owners: XCPro Team

## Context

Phase 5 of the architecture hardening track re-checked `feature:map-runtime`
with compile proof instead of package-name assumptions.

Two boundary facts were confirmed:

1. `MapTasksUseCase` is map-shell orchestration over
   `TaskManagerCoordinator`, not a runtime/render primitive.
2. The previous assumption that `:feature:igc` and `:feature:profile` were
   stale `feature:map-runtime` dependencies was incorrect. Current runtime code
   still consumes:
   - `ReplayDisplayPose`
   - replay `SessionState` / `SessionStatus`
   - `MapOrientationPreferences`
   - `TrailSettings`, `TrailLength`, `TrailType`
   - `MapShiftBiasCalculator` and related models

A third boundary check also matters:

- `feature/traffic/.../TrafficOverlayRuntimeState.kt` is the accepted map-free
  traffic overlay handle seam used by `feature:map-runtime`.
- Moving that seam into `feature:map-runtime` would require `feature:traffic`
  to depend on `feature:map-runtime` while `feature:map-runtime` already depends
  on `feature:traffic`, creating a module cycle.
- Existing accepted OGN/traffic change plans already treat that seam as
  feature:traffic-owned.

A fourth boundary check landed in the seam-hardening pass:

- `feature:map-runtime` had a real but low-value dependency on
  `:dfcards-library` only because runtime-facing contracts exposed
  `FlightModeSelection`.
- Replay ownship ingestion also accepted `RealTimeFlightData` directly instead
  of a runtime-owned replay DTO.
- Those seams were shell/UI leakage, not durable runtime/render requirements.

## Decision

1. `MapTasksUseCase` moves to `feature:map`.
   - It is the map-shell adapter over `TaskManagerCoordinator`.
   - It remains in package `com.example.xcpro.map` for low-churn consumers.

2. `TaskRenderSnapshot` stays in `feature:map-runtime`.
   - It is still consumed by runtime owners such as
     `TaskRenderSyncCoordinator` and `MapCameraRuntimePort`.
   - The runtime model must not move upward with the shell adapter.

3. `:feature:igc` and `:feature:profile` stay declared dependencies of
   `feature:map-runtime` for now.
   - Their current usage is real, not stale.
   - Any future removal requires explicit extraction or rewiring of the
     runtime-owned types listed above.

4. `TrafficOverlayRuntimeState` stays in `feature:traffic`.
   - `feature:traffic` continues to own the concrete traffic overlay
     implementation seam.
   - Phase 5 does not force a contract move that would create a cycle.

5. `feature:map-runtime` must not depend on `:dfcards-library`.
   - Runtime-facing camera/state contracts use app-owned `FlightMode`.
   - Replay ownship ingestion uses runtime-owned `ReplayLocationFrame`.
   - Any `FlightModeSelection` conversion stays in `feature:map` at the
     card/UI boundary.

## Consequences

- Phase 5 still narrows the boundary by removing one clear shell-owned adapter
  from `feature:map-runtime`.
- The runtime module also drops a card/UI dependency that was only present for
  runtime-facing enum/DTO leakage.
- Phase 5 does not claim false progress by removing dependencies that compile
  proof shows are still required.
- Traffic overlay contract ownership remains intentionally asymmetric until a
  future cycle-free extraction path exists.

## Validation

- `./gradlew :feature:map-runtime:compileDebugKotlin :feature:map:compileDebugKotlin`
  passed after moving `MapTasksUseCase` and retaining the real `igc` / `profile`
  dependencies.
- `./gradlew :feature:map:testDebugUnitTest :feature:map-runtime:testDebugUnitTest`
  passed after replacing the runtime-facing `FlightModeSelection` and replay
  ownship seams.

## Follow-up

- Re-audit future opportunities to extract replay/session and profile-owned
  runtime types into a narrower shared owner if that can be done without
  creating a new god module or widening public APIs.
- Keep `TrafficOverlayRuntimeState` in `feature:traffic` until a cycle-free
  traffic/runtime contract split is explicitly designed.
