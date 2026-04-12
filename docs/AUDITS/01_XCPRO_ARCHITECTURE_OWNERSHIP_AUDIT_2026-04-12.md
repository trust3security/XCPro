# XCPro Architecture Ownership Audit

Date: 2026-04-12

## Purpose

Audit whether the current XCPro branch follows the repo's documented
architecture, ownership, SSOT, dependency-direction, and replay/timebase
contracts.

This is an audit, not a proposal.

## Scope and commands run

Docs read first:

- `AGENTS.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE_INDEX.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

Relevant ADRs / owner-boundary docs consulted:

- `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`
- `docs/ARCHITECTURE/ADR_FINAL_GLIDE_RUNTIME_BOUNDARY_2026-03-25.md`
- `docs/ARCHITECTURE/ADR_MAP_RUNTIME_BOUNDARY_TIGHTENING_2026-04-06.md`
- `docs/ARCHITECTURE/ADR_MAP_RUNTIME_TRAIL_OWNER_2026-03-16.md`
- `docs/ARCHITECTURE/ADR_FLIGHT_RUNTIME_BOUNDARY_2026-03-15.md`
- `docs/ARCHITECTURE/ADR_TASK_SYNC_READ_SEAM_2026-04-06.md`
- `docs/ARCHITECTURE/ADR_CURRENT_LD_PILOT_FUSED_METRIC_2026-04-08.md`
- `docs/ARCHITECTURE/ADR_FLIGHT_MGMT_ROUTE_PORT_2026-04-06.md`

Commands run:

- `python scripts/arch_gate.py` -> pass
- `./gradlew enforceArchitectureFast` -> pass
- `./gradlew :feature:map:compileDebugKotlin` -> pass
- `./gradlew enforceRules` -> pass
- `./gradlew testDebugUnitTest` -> pass
- `./gradlew assembleDebug` -> pass

## A. Overall verdict

**MIXED**

The repo is not failing by collapse. The core SSOT seams are mostly in place and
all requested gates passed.

It also does not cleanly pass its own professional architecture standard.
Several UI/runtime owner leaks remain, one concrete live-timebase slip exists in
map flight-time derivation, and multiple hotspot classes still mix concerns in
ways the repo explicitly tells contributors not to.

## B. Scores

- ownership: **74/100**
- SSOT integrity: **86/100**
- dependency direction: **76/100**
- UI purity: **67/100**
- replay/timebase discipline: **81/100**
- maintainability: **63/100**
- docs-vs-code truthfulness: **79/100**

## C. Top 10 concrete issues

1. Severity: **high**
   - File: `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
   - What is wrong:
     `ProfileAndConfigurationEffects` loads persistence-backed mode visibility
     and performs fallback-mode selection inside a Composable effect.
   - Correct owner:
     ViewModel or use-case orchestration, with UI limited to rendering and
     intent forwarding.
   - Why this violates repo rules:
     `AGENTS.md` says UI owns rendering, user input forwarding, and display-only
     logic. This effect calls `flightDataManager.loadVisibleModes(...)`,
     evaluates visibility, and selects a replacement mode.

2. Severity: **high**
   - File: `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
   - What is wrong:
     `FlightDataManager` reads `CardPreferences` directly to derive
     profile-visible flight modes.
   - Correct owner:
     A cards/profile repository or use-case seam above the UI-adjacent manager.
   - Why this violates repo rules:
     This pulls persistence ownership into a map UI manager and weakens the
     dependency direction the repo expects.

3. Severity: **high**
   - File: `feature/map/src/main/java/com/example/xcpro/map/FlightDataUiAdapter.kt`
   - What is wrong:
     `feature:map` directly constructs `TrailProcessor()`, which is a stateful
     trail runtime owner.
   - Correct owner:
     `feature:map-runtime`, exposed via DI or another explicit runtime seam.
   - Why this violates repo rules:
     `feature:map` is supposed to remain consumer/adapter/render. This code
     instantiates a runtime owner directly from the map shell.

4. Severity: **medium**
   - File: `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
   - What is wrong:
     Live flight time is derived from `data.gps?.timestamp ?: data.timestamp`,
     even though the repo has an explicit monotonic calculation time path.
   - Correct owner:
     A timebase-aware mapper or use-case that uses monotonic time for live and
     replay IGC time for replay.
   - Why this violates repo rules:
     The repo requires injected/explicit time sources and replay determinism.
     This path mixes live wall time into a runtime-derived display value.

5. Severity: **medium**
   - File: `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskRepository.kt`
   - What is wrong:
     The "UI projector" repository owns task-point conversion, observation-zone
     resolution, AAT target optimization, validation projection, and envelope
     geometry math.
   - Correct owner:
     Task domain/use-case/runtime owners, with the repository remaining a
     projection seam only.
   - Why this violates repo rules:
     `AGENTS.md` says `TaskRepository` is UI projection only and must not become
     a cross-feature runtime authority. It is not misused cross-feature today,
     but it owns too much business logic for its stated role.

6. Severity: **medium**
   - File: `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`
   - What is wrong:
     The coordinator still owns too many responsibilities: task snapshot
     publication, persistence bridge triggering, AAT edit-mode sync, mutation
     routing, racing advance state, handlers, and helper calculations.
   - Correct owner:
     A narrower coordinator facade with more behavior split into focused helper
     owners.
   - Why this violates repo rules:
     This is a maintainability hotspot and a mixed-responsibility class, even if
     it remains under the repo-wide hard line cap.

7. Severity: **medium**
   - File: `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
   - What is wrong:
     One observer class combines 11 inputs and owns live-data projection, trail
     updates, replay toasts, readiness flags, debug logging, and wind-state
     enrichment.
   - Correct owner:
     Split observers and mappers by concern.
   - Why this violates repo rules:
     This is another mixed-responsibility hotspot in the map shell, making
     owner boundaries difficult to audit and maintain.

8. Severity: **medium**
   - File: `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`
   - What is wrong:
     The class comment claims it maintains "a domain TaskRepository for
     validation/stats."
   - Correct owner:
     The comment should describe `TaskRepository` as a UI projection seam.
   - Why this violates repo rules:
     This conflicts with the documented contract that `TaskRepository` is UI
     projection only. The behavior is mostly aligned; the ownership label is not.

9. Severity: **low**
   - File: `feature/tasks/src/main/java/com/example/xcpro/tasks/aat/validation/AATValidationBridge.kt`
   - What is wrong:
     Validation conversion silently generates IDs with `UUID.randomUUID()` when
     the source task ID is empty.
   - Correct owner:
     Explicit task creation/import seams, not validation conversion.
   - Why this violates repo rules:
     This is hidden identity generation in a validation path and weakens
     deterministic ownership.

10. Severity: **low**
   - File: `feature/map/src/main/java/com/example/xcpro/igc/data/IgcMetadataSources.kt`
   - What is wrong:
     `TaskRepositoryIgcTaskDeclarationSource` is named as if it reads from
     `TaskRepository`, but it actually reads from `TaskManagerCoordinator`.
   - Correct owner:
     Same implementation seam, but the naming should match the real owner.
   - Why this violates repo rules:
     Behavior is compliant; the name is not. Misleading names in this repo are
     documentation debt because they imply the wrong authority.

## D. False alarms / acceptable exceptions

- `FlightDataRepository` is acting as the fused-flight-data SSOT and remains
  aligned with `AGENTS.md`.
- Cross-feature task runtime reads are mostly compliant and use
  `TaskManagerCoordinator.taskSnapshotFlow` / `currentSnapshot()` rather than
  forbidden raw manager state.
- Final glide ownership is aligned with the current ADR direction:
  `GlideComputationRepository` derives from fused flight data, wind, task
  snapshot, and route seams rather than moving task/glide ownership into map UI.
- Broad raw `Log.*` drift exists, but it is already a tracked temporary
  deviation in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
- `TaskSheetViewModel` already has a documented temporary hotspot line-budget
  exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.

## E. Quick wins vs structural refactors

Quick wins:

- Move visible-mode loading and fallback selection out of Compose effects.
- Rename `TaskRepositoryIgcTaskDeclarationSource` to match its true owner.
- Fix the misleading `TaskSheetViewModel` comment.
- Remove UUID generation from `AATValidationBridge`.

Structural refactors:

- Pull `CardPreferences` access out of `FlightDataManager`.
- Move `TrailProcessor` construction to `feature:map-runtime` DI/runtime seams.
- Split `MapScreenObservers` by concern.
- Split business geometry and target-resolution logic out of `TaskRepository`.
- Narrow `TaskManagerCoordinator` responsibilities further.

## F. Final recommendation

**phased cleanup needed**

Current status is not "leave as is" and not yet "architecture drift is
serious."

The core seams are substantially better than the remaining hotspots suggest, but
the documented contracts are still being bent in a few concentrated owner
leakage points. The right call is a phased cleanup of those seams rather than a
full rewrite or a no-action pass.
