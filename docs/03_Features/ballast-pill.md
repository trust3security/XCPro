# Ballast Pill Map Overlay

## Purpose
- Give pilots an at-a-glance view of current water ballast directly on the map so they understand how the glider's polar curve is being modified during flight.
- Provide quick simulated Fill/Drain interactions that keep the polar ballast value in sync with the single source of truth used throughout the Polar screen and flight calculations.
- Maintain visual consistency with the existing pill-shaped map card while introducing a vertical orientation that reads like a fuel gauge.

## Current Context
- `MapScreenContent.kt` already renders multiple overlaid widgets (task status, compass, variometer) on top of MapLibre layers.
- Polar ballast today is adjusted via the nav drawer path `General -> Polar -> Ballast` implemented in `screens/navdrawer/PolarCards.kt`, which updates `GliderRepository.config.waterBallastKg`.
- Flight performance code (for example `glider/PolarCalculator.kt`) relies on the value supplied by `GliderRepository`, so we must keep that as the SSOT.

## Existing Code Analysis (2025-10-25)
- **Repository singletons:** `GliderRepository` (app/src/main/java/com/example/xcpro/glider/GliderRepository.kt) exposes `config: StateFlow<GliderConfig>` and persists values through `SharedPreferences`. `updateConfig` is the only mutator, making it an ideal SSOT anchor.
- **Polar UI:** `PolarCards.kt` (app/src/main/java/com/example/xcpro/screens/navdrawer/PolarCards.kt) reads the repository via `collectAsState()` and directly writes ballast back through `updateConfig`. Any new ballast UI must reuse the same flows to stay consistent.
- **Map screen ownership:** `MapScreenViewModel` (app/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt) builds the MapScreen dependency graph. It already injects `FlightDataManager`, `MapUIWidgetManager`, etc., so it is the correct MVVM view model for the pill.
- **Overlay layer:** `MapScreenContent.kt` (app/src/main/java/com/example/xcpro/MapScreenContent.kt) collects state from the view model and composes overlays using animated visibility, `BoxWithConstraints`, and z-index ordering. The new pill should live alongside compass and variometer widgets to inherit existing gesture handling.
- **Scheduling precedent:** The project uses coroutines with `viewModelScope` for long-running tasks (for example, `locationManager` updates). Timing animations for fill/drain should mirror this approach instead of creating ad-hoc timers.

## Target Experience
- **Layout:** A vertical pill positioned near the existing compact card on the right edge of the map. Suggested size about 90 dp tall by 32 dp wide, with rounded corners matching the 12 dp radius used elsewhere.
- **Border styling:** 1 dp stroke. Idle/steady state uses `MaterialTheme.colorScheme.outlineVariant`. Transitioning states tint the stroke green (`containerColor = onSurfaceVariant.copy(alpha = 0.6f)`) during Fill and red during Drain.
- **Fill gradients:** Interior should show a smooth gradient from the "liquid" level upward. Consider using `Brush.verticalGradient` with colors drawn from the secondary palette to align with polar visuals.
- **Buttons:**
  - Top mini button labelled `Fill`, aligned centrally above the pill.
  - Bottom mini button labelled `Drain`, aligned below.
  - Buttons use the compact `FilledTonalButton` style with a width slightly wider than the pill to reinforce their association.
- **Animation timing:**
  - Fill animates the liquid level from current to max in **20 seconds**.
  - Drain animates from current to 0 in **180 seconds**.
  - Use easing curves that feel hydraulic (for example `CubicBezierEasing(0.3f, 0.0f, 0.2f, 1.0f)`).
- **Feedback:** While a transition runs, disable the opposite button and show the active button in a pressed state. Optionally display the remaining time (mm:ss) below the pill using `AnimatedContent`.
- **Accessibility:** Provide content descriptions reflecting current ballast percentage and status (`"Water ballast 35 percent, draining"`).

## MAAV + SSOT + KISS Architecture
- **Model (M):**
  - Promote `GliderRepository.config` as the SSOT by adding a small extension inside `map/ballast/BallastRepositoryAdapter.kt` that exposes `waterBallastRatio: StateFlow<Float>`.
  - Expose immutable `BallastSnapshot` data (current kg, max kg, ratio) so UI and other consumers operate on the same read-only structure.
- **Action (A):**
  - Define `BallastCommand` (`StartFill`, `StartDrain`, `Cancel`, `ImmediateSet(Double kg)`) handled centrally by a `BallastController`.
  - Constrain all writes to a single coroutine channel so simultaneous button presses collapse into a predictable sequence (MAAV).
- **Adapter (A):**
  - `BallastController` (new class in `map/ballast/`) owns the animation coroutine, exposes `StateFlow<BallastUiState>`, and calls `GliderRepository.updateConfig` for each frame. It receives a `DispatcherProvider` to remain testable.
  - The controller listens to `GliderRepository.config` to resync if external screens change ballast (nav drawer). Divergence triggers cancellation of any in-flight animation and restarts with the updated baseline (SSOT enforcement).
- **View (V):**
  - Create `@Composable BallastPill(state: BallastUiState, onCommand: (BallastCommand) -> Unit)` under `map/ballast/ui/`.
  - Use existing `MapScreenContent` layout conventions (alignment, `zIndex`, `AnimatedVisibility`) to keep composition simple (KISS) and rely on the view model for orchestration (MVVM).
- **Keep It Simple:** No new global singletons or service locators. The view model obtains the shared repository instance once, constructs the controller, and exposes only the minimal flows required by the composable. Follow AGENTS.md guidance by extracting helpers if files approach 500 lines and by preferring immutable state carriers.

## Integration Steps
1. **Create package:** `app/src/main/java/com/example/xcpro/map/ballast/`.
2. **Controller:**
   - `BallastController` holds `MutableStateFlow<BallastUiState>` with remaining duration, ratio, and active command.
   - Uses `Animatable` under the hood to drive the value; updates repository each frame.
3. **ViewModel wiring:**
   - Instantiate the controller in `MapScreenViewModel`, passing `viewModelScope`.
   - Expose `val ballastUiState: StateFlow<BallastUiState> = ballastController.state`.
4. **Compose integration:**
   - Add `BallastPill` into `MapScreenContent` overlay stack near the compass (`Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 140.dp)` as a starting point).
   - Feed `state = ballastUiState.collectAsState()` and dispatch button events back to the controller.
5. **Nav drawer unity:**
   - Observe `repo.config` changes to ensure nav drawer sliders immediately reflect fill/drain updates.
   - If the nav drawer modifies ballast while an animation is running, cancel the current animation to honour the latest manual input.
6. **Telemetry and persistence:**
   - Persist final ballast values via existing repository logic (already handled through `updateConfig`).
   - Optional: log major transitions to `Log.d("BallastController", ...)` for debugging.

## Edge Cases and Safeguards
- Clamp values between `0f` and `maxWaterBallastKg`.
- Handle `maxWaterBallastKg == 0` (gliders without ballast) by disabling buttons and showing "N/A".
- Resume correct level after app process death by reading the current repository state on init.
- Prevent rapid button tapping from spawning overlapping coroutines (use a shared job reference and mutex).
- Use the aircraft's declared `WaterBallastCapacity.totalLiters` when available; otherwise, fall back to the lesser of 200 kg or the current configured value so the UI remains functional for models missing capacity data.

## Testing Suggestions
- **Unit:** Test that `BallastController` calculates expected intermediate values given a fake dispatcher (for example, 10 s in yields 33% progress during Fill).
- **UI:** Compose screenshot test for the three states (idle, filling, draining).
- **Integration:** Add an instrumentation test verifying that activating Fill drives `GliderRepository.config.waterBallastKg` to `maxWaterBallastKg` over the expected duration (use `TestCoroutineScheduler` to advance time).

## Additional Recommendations
- Surface the current ballast percentage in the task/flight summary HUD so pilots see the effect even when the pill is collapsed (future iteration).
- Consider tying Fill/Drain commands to physical input events (long-press on vario knob) once hardware support lands.
- Document the feature in `docs/Future_Features` once implemented, including screenshots for release notes.

## Implementation Plan (Pre-Work, No Code Changes Yet)
- **Discovery**
  - Audit existing map overlays (compass, task ribbon, vario) in `MapScreenContent.kt` to confirm layering constraints and identify a target z-index for the pill.
  - Review `GliderRepository` API to verify concurrent callers update `config` safely and check whether ballast preferences already persist across app restarts.
- **Design Alignment**
  - Validate exact positioning, sizing, and color tokens with design stakeholders; capture updated specs or mockups in this document if they diverge from current assumptions.
  - Confirm animation expectations (linear vs. eased, staging of disabled buttons) and accessibility copy with product/UX.
- **Architecture Prep**
  - Draft `BallastController` interface and `BallastUiState` data model in a design snippet to review with the core team; make sure it satisfies MAAV, SSOT, and KISS goals spelled out in `AGENTS.md`.
  - Decide whether controller lives inside `MapScreenViewModel` package or a dedicated `map/ballast` subpackage; document rationale.
- **Integration Strategy**
  - Create a dependency list for the controller (repository, coroutine scope, dispatcher provider) and note any DI adjustments required.
  - Outline Compose state collection flow (`collectAsState` vs. `stateIn` exposure) and how UI will debounce repository updates to avoid recomposition thrash.
- **Testing Strategy**
  - Specify targeted unit tests (value interpolation, cancellation behaviour) and instrumentation/UI tests (animation state, nav drawer sync) before coding.
  - Identify any new test utilities (fake repository, TestScope helpers) that need to be created or extended.
- **Rollout Checklist**
 - Prepare change log entries, QA scenarios (fill/drain mid-flight, nav drawer edits during animation), and manual validation steps.
 - Record open questions or risks (performance impact during long drains, interaction with autopilot modes) for resolution before implementation starts.

## MVVM Responsibility Matrix
- **Model layer**
  - `GliderRepository` remains the persistence and truth holder for `GliderConfig.waterBallastKg`.
  - `BallastSnapshot` (new data class) mirrors repository values plus derived ratio for downstream consumers.
- **ViewModel layer**
  - `MapScreenViewModel` instantiates `BallastController` and exposes:
    - `val ballastState: StateFlow<BallastUiState>`
    - `fun onBallastCommand(command: BallastCommand)`
  - View model forwards lifecycle events (for example, `onCleared`) to the controller to stop coroutines cleanly.
- **View layer**
  - `MapScreenContent` collects `ballastState` via `collectAsState()` and renders `BallastPill`.
  - The composable delegates button taps back to `MapScreenViewModel.onBallastCommand`, keeping business logic outside the UI.

## SSOT Enforcement Checklist
- All ballast changes flow through `GliderRepository.updateConfig`.
- Controller compares the target animation end state with the repository's `maxWaterBallastKg` (from `GliderModel.waterBallastKg`) to prevent overshoot.
- Nav drawer, ballast pill, and any future HUD widget listen to the same `StateFlow`, preventing split-brain issues.
- Shared test fakes mock `GliderRepository` to guarantee deterministic behaviour during unit tests.
