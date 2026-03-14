# Map Camera Location Lifecycle Runtime Extraction Agent Contract

## 0) Purpose

This is the task-specific autonomous execution contract for implementing
the full camera/location/lifecycle runtime extraction plan.

It exists to prevent:

- ad hoc boundary moves
- speculative cleanup outside the measured compile-time goal
- shell/runtime back-edges
- mixing unrelated repo-health fixes into the extraction work

This contract is subordinate to:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/AGENT.md`
3. `docs/refactor/Map_Camera_Location_Lifecycle_Runtime_Extraction_Plan_2026-03-13.md`

If these conflict, follow the stricter architectural rule.

## 1) Mission

Reduce inner-loop compile time by extracting the heavy camera/location/lifecycle
runtime cluster from `feature:map` into `:feature:map-runtime`, while keeping
`feature:map` as a thin shell.

Success means:

- edits in moved runtime-owner files compile in `:feature:map-runtime`
- shell files stop depending on the moved concrete owners
- no new reverse dependency from `:feature:map-runtime` back to `feature:map`
- no product behavior drift in camera, location, lifecycle, replay, or permission flow

## 2) Fixed Scope

### 2.1 In Scope

- `feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleEffects.kt`
- shell fan-out files and helper graph explicitly listed in the dedicated plan

### 2.2 Explicitly Out of Scope

- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` as an owner move in the first cycle
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` as an owner move
- `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` line-budget cleanup
- unrelated repo test failures already known outside this extraction

Do not widen scope unless the dedicated plan is updated first.

## 3) Non-Negotiables

- No direct `MapView`, `MapLibreMap`, or Compose shell types in runtime public contracts.
- No `:feature:map-runtime -> feature:map` dependency.
- No owner move before the phase-specific seam pass is completed and documented.
- No cleanup-only changes that do not reduce shell fan-out, remove a blocker, or complete the current owner move.
- No unrelated bug fixes bundled into an extraction phase.
- `MapLifecycleEffects.kt` stays shell-owned unless the plan is explicitly changed.
- `MapScreenRoot.kt`, `MapScreenScaffold.kt`, and route/shell entrypoints remain shell-owned.

## 4) Execution Order

The agent must execute phases strictly in this order:

1. Phase A: Runtime Contract Extraction
2. Phase B: Shell Fan-Out Boundary Narrowing
3. Phase C: Camera Runtime Owner Move
4. Phase D: Location / Display-Pose Runtime Owner Move
5. Phase E: Lifecycle Runtime Owner Move
6. Phase F: Remeasure and Stop Rule

The agent must not skip ahead.

## 5) Per-Phase Procedure

For every phase, use this exact loop:

1. Run one seam pass on that phase only.
2. Update the dedicated plan if a new blocker changes phase shape.
3. Implement only the bounded phase payload.
4. Update active docs:
   - `docs/ARCHITECTURE/PIPELINE.md` if wiring changed
   - `docs/refactor/Map_Camera_Location_Lifecycle_Runtime_Extraction_Plan_2026-03-13.md`
   - `docs/refactor/Feature_Map_Compile_Speed_Release_Grade_Phased_Plan_2026-03-12.md` if progress changed
5. Run verification.
6. Stop and report before beginning the next phase.

Do not chain multiple phases into one implementation batch.

## 6) Phase-Specific Contract

### Phase A

Objective:
- define shell-safe ports for camera/location/lifecycle behavior

Required seam targets:
- `MapCameraEffects.kt`
- `MapComposeEffects.kt`
- `MapScreenRuntimeEffects.kt`
- `MapGestureSetup.kt`
- `LocationPermissionUi.kt`
- `MapTaskIntegration.kt`
- `OverlayActions.kt`
- `MapScreenRootEffects.kt`
- `MapScreenScaffoldContentHost.kt`
- `MapScreenContentRuntime.kt`

Allowed work:
- extract interfaces/contracts
- extract top-level runtime value types
- remove shell leaks from public APIs

Forbidden work:
- moving the concrete owners themselves
- changing runtime behavior beyond what is required to support the contracts

Exit criteria:
- shell-facing ports exist for all documented call sites
- contracts are free of Compose shell types and direct `MapView` / `MapLibreMap`

### Phase B

Objective:
- stop shell fan-out of concrete camera/location/lifecycle owners

Required shell files:
- `MapScreenManagers.kt`
- `MapScreenScaffoldInputs.kt`
- `MapScreenScaffoldInputModel.kt`
- `MapScreenScaffoldContentHost.kt`
- `MapScreenContentRuntime.kt`
- `MapScreenRoot.kt`
- `MapScreenRootHelpers.kt`
- `MapCameraEffects.kt`
- `MapComposeEffects.kt`
- `MapScreenRuntimeEffects.kt`
- `MapGestureSetup.kt`
- `LocationPermissionUi.kt`
- `MapTaskIntegration.kt`
- `OverlayActions.kt`
- `MapScreenRootEffects.kt`

Allowed work:
- narrow shell signatures
- replace concrete manager parameters with ports/handles
- keep shell-owned bridges local to `feature:map`

Forbidden work:
- owner move of `MapCameraManager`, `LocationManager`, or `MapLifecycleManager`
- changing out-of-scope shell systems

Exit criteria:
- shell paths no longer pass concrete owners where the plan says they should not
- `MapScreenManagers.kt` no longer acts as an unbounded concrete-manager fan-out

### Phase C

Objective:
- narrow `MapCameraManager.kt` off direct shell handle ownership, then move it into `:feature:map-runtime`

Allowed work:
- add one narrow shell-safe camera surface bridge for `MapLibreMap` / `MapView` access
- move owner plus its runtime-owned collaborators proven clean by the seam pass
- update shell to depend on narrowed camera-facing ports

Forbidden work:
- folding `LocationManager` or lifecycle changes into the same move
- moving shell-only helpers that still depend on `MapScreenState`, `MapView`, or Compose
- moving `MapScreenState` itself as part of the camera owner move

Exit criteria:
- `MapCameraManager` no longer owns `MapScreenState` directly
- camera owner compiles in `:feature:map-runtime`
- shell camera effects no longer depend on the concrete moved owner

### Phase D

Objective:
- move `LocationManager.kt` and the runtime-owned display-pose/location pipeline into `:feature:map-runtime`

Allowed work:
- move `LocationManager` plus the helper cluster explicitly proven runtime-owned by the seam pass
- keep permission launcher and other shell bridges in `feature:map`

Forbidden work:
- moving shell-owned `ActivityResultLauncher` handling into runtime
- widening scope to unrelated overlay or task systems

Exit criteria:
- location/display-pose owner compiles in `:feature:map-runtime`
- shell uses only narrowed location-facing ports

### Phase E

Objective:
- move lifecycle runtime ownership into `:feature:map-runtime`

Allowed work:
- move `MapLifecycleManager.kt`
- keep `MapLifecycleEffects.kt` shell-owned
- isolate shell cleanup/lifecycle surfaces behind explicit bridges

Forbidden work:
- moving Compose lifecycle observers/effects into runtime

Exit criteria:
- lifecycle runtime no longer depends on a concrete shell-owned `LocationManager`
- lifecycle runtime no longer mutates `MapScreenState` directly for shell-only cleanup surfaces

### Phase F

Objective:
- verify compile-scope improvement and stop unless evidence justifies more work

Required evidence:
- warm one-line edit timing in moved runtime files
- warm one-line edit timing in retained shell files
- targeted compile results for `:feature:map-runtime` and `:feature:map`

Stop rule:
- if the move does not produce a real compile-scope win, stop structural refactoring
- do not invent a follow-up phase without a fresh seam pass and plan update

## 7) Verification Contract

Required per implementation phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew :feature:map-runtime:compileDebugKotlin
./gradlew :feature:map:compileDebugKotlin
```

Known unrelated failures may remain, but the agent must prove the current phase did not introduce new ones.

For compile-speed evidence, the agent must also run targeted warm-edit timings when a real owner move lands.

## 8) Evidence Required in the Final Report

For each completed phase, report:

- what changed
- exact files touched
- tests added or updated
- verification results
- remaining blockers
- whether the phase improved compile-scope ownership

At the end of the whole program, report:

- before/after compile timing evidence
- final module ownership boundary
- residual shell-heavy hotspots, if any
- whether further structural work is justified

## 9) Anti-Churn Rules

- One seam pass per phase, no recursive re-audits unless the phase shape changes.
- One implementation phase per change batch.
- No opportunistic file moves.
- No renames unless required for dependency direction or package stability.
- No unrelated cleanup folded into the extraction.
- If a blocker is discovered that materially changes a phase, stop, update the plan, and restart from that phase.

## 10) Current Known Unrelated Blockers

These are baseline issues and must not be mixed into this program:

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` line-budget gate
- unrelated app/map test failures already documented outside this extraction

The agent may mention them in verification, but must not expand scope to fix them under this contract.
