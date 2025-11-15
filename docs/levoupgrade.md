# Levo Upgrade Plan

## Objectives
1. **Replay fidelity** – remove the “5 s jump” feeling by inserting interpolated samples whenever the gap between consecutive B-records exceeds 1 s, but keep original data for archival / QA. The interpolator must be:
   - Linear for lat/lon/alt/time and only active inside `IgcReplayController` so live sensors stay untouched.
   - Aware of missing pressure altitude (fallback to GPS alt) and QNH (reuse loaded value per session).
2. **Developer replay shortcut** – drop a debug-only Floating Action Button (FAB) on the map that auto-loads the bundled `docs/2025-11-11.igc`, opens the replay HUD, and starts playback so QA can jump straight into verification without using SAF every time.
3. **Vario realism / audio parity** – re-enable audio beeps during replay by feeding the replay calculator with `enableAudio = true`, and document the follow-up filter tuning so Levo’s needle/audio match XCSoar both in live flight and during replays.

## Guardrails (from CODING_POLICY.md)
- Do not mutate the live sensor pathway: interpolation + auto-replay must live strictly inside the replay module.
- Preserve Clean Architecture boundaries: `MapScreen` drives UI, `MapScreenViewModel` talks to `IgcReplayController`, and repositories remain the SSOT for card data.
- Use StateFlows/DI for all new surfaces; no global singletons or cross-module side effects.
- Tests must consume real data (IGC logs) or deterministic math, never synthetic noise.

## Implementation Notes
- **Interpolation pipeline**
  - Extend `IgcReplayController.loadFile` to run `densifyPoints(originalPoints)`. This helper will walk the original list, insert `IgcPoint`s every second (or faster if the delta < 1 s), and preserve exact timestamps by distributing uniformly.
  - Each synthetic point inherits lat/lon/alt heading via linear interpolation; `pressureAltitude` falls back to GPS altitude when either endpoint is null.
  - Record metadata (start/duration) after densification so HUD progress matches actual playback time.
- **Replay HUD + FAB**
  - Add a `MapFeatureFlags.showReplayDebugFab` that defaults to `BuildConfig.DEBUG` so release builds remain clean.
  - Compose FAB lives in `MapOverlayWidgets.kt` (same layer as other dev affordances). On tap it calls a new `MapScreenViewModel.triggerReplayFromAsset()`; the VM asks `IgcReplayController` to stream an asset URI instead of SAF.
  - Bundle the canonical log under `app/src/main/assets/replay/2025-11-11.igc` and expose a helper in the controller (e.g., `suspend fun loadAsset(assetName: String)`), so we reuse the same parsing code path.
- **Vario audio**
  - Instantiate `FlightDataCalculator` with `enableAudio = true` for replay sessions. The calculator takes care of gating audio via the `VarioServiceManager` we already stop/resume when replay starts/stops.
  - Document the planned damping filter (moving average or XCSoar-style Kalman) as a follow-up item so we can reason about real-flight impact before coding it.

## Progress (2025-11-14)
- Interpolation helper now densifies sparse B-records to 1 s cadence before replay starts; cards and Levo needles move smoothly even when logs were recorded at >1 s.
- Map developer FAB (guarded by `MapFeatureFlags.showReplayDebugFab`) auto-loads `replay/2025-11-11.igc`, starts playback, and opens the HUD so QA can test without the SAF flow.
- Replay calculator runs with audio enabled, so the Levo beeper reflects replayed vario rates; sensors resume with audio once the session stops.
- Netto computation now mirrors XCSoar: we subtract the polar sink derived from the TAS proxy (wind fusion first, GPS-speed fallback second) and always publish the value to cards, tagging it "NO POLAR" when the sink isn't trustworthy instead of blanking the card.
- Straight-flight wind EKF got a shorter blackout window, richer diagnostics, and an explicit quality bonus inside `WindStore`, so cruise segments keep publishing `WindSource.EKF` and the TAS/Netto pipeline no longer falls back to raw GPS speed.
- Netto display now feeds on a 5 s moving average before the low-pass/clamp stage, matching XCSoar’s infobox damping so the Levo card needle no longer snaps to ±7 m/s when the air is rough.
- `docs/Wind-IAS-TAS.md` and this plan are in sync with the current implementation.
- FlightDataCalculator now publishes TE-vs-GPS vario metadata, 30 s brutto/netto averages, and thermal (T Avg / TC Avg / TC Gain) metrics so cards can mirror XCSoar’s info boxes.

## XCSoar Reference Notes
- **Pressure-domain Kalman** – Each Android/I²C baro sample is smoothed by `SelfTimingKalmanFilter1d` with tuned `var_x_accel` (0.0075 for phones, 0.3 for noisy I²C) and a 5 s max-gap watchdog before XCSoar derives vario from pressure (`src/Device/Descriptor.hpp`, `src/Device/AndroidSensors.cpp`).
- **Vario source selection** – `BasicComputer::ComputeBruttoVario()` prefers total-energy vario, then GPS vario; netto subtracts sink rate afterwards, and `AverageVarioComputer` maintains 30 s FIFOs for both brutto/netto (`src/Computer/BasicComputer.cpp`, `src/Computer/AverageVarioComputer.cpp`).
- **Audio gating** – `MergeThread` only feeds `AudioVarioGlue::SetValue()` when `brutto_vario_available` is true; otherwise it calls `NoValue()`, so XCSoar mutes audio on stale data (`src/MergeThread.cpp`).
- **TE compensation** – XCSoar’s TE vario comes from probe/pressure data, so stick-thermal rejection happens before sink/polar math. Our implementation adds kinetic energy based on ground-speed deltas, which fails when groundspeed ≠ airspeed.

## Implementation Plan (Phase 2 – Vario smoothing)
1. **Pressure Kalman stage**
   - Port XCSoar’s `SelfTimingKalmanFilter1d` logic (variance constants + max-Δt reset) into a Kotlin helper that runs directly on hPa before altitude conversion.
   - Feed that pressure-domain output into both the TE computation and the `Modern3StateKalmanFilter`. When accelerometer data is unavailable, default to the pressure Kalman output instead of the `AdvancedBarometricFilter`.
2. **Vario source selection & averaging**
   - Mirror XCSoar’s “brutto/netto” pipeline: prefer TE vario, fall back to GPS vario, then expose both 30 s moving averages for cards/HUD.
   - Replace the current 10-sample `verticalSpeedHistory` with time-based circular buffers (e.g., 30 s @ 1 Hz) so thermal averages and card readings match XCSoar’s damping.
3. **Audio gating**
   - Track a `varioValid` flag; only update `VarioAudioEngine` when fresh data arrives, otherwise call a new `setSilence()` shim so replay/live audio matches XCSoar’s mute behavior.
4. **TE/netto alignment**
   - Use true airspeed (from TAS proxy or polar) in the kinetic-energy term, and keep the TE pipeline consistent whether data comes from live sensors or IGC replay.
5. **Instrumentation & verification**
   - Add logging hooks so we can compare (optimized vs. XCSoar-like) filters during replay.
   - Verification: run `./gradlew :feature:map:testDebugUnitTest`, then use the built-in replay FAB plus the SAF flow to confirm the Levo vario needle/audio track the XCSoar log (smooth 1 s updates, no 5 s steps).

### Detailed Blueprint (2025-11-14)
- **Modules**
  - `PressureKalmanFilter`: already mirrors XCSoar’s pressure smoother; keep two presets (`PHONE` = 0.0075, `I2C` = 0.3) and allow runtime swaps once we tag noisy sensors.
  - `FlightDataCalculator`:
    - Split outputs into `varioRawPressure`, `varioTE`, `varioGPS`, and `bruttoVario`. `bruttoVario = varioTE if available else varioGPS`.
    - Maintain `MovingAverage30s` helper (1 Hz samples) for brutto + netto so cards/HUD render smoothed fields.
    - Promote `varioValidUntil` timestamp and guard audio updates + card publishes with it.
  - `VarioAudioEngine`: add `setSilence()` entry to mirror XCSoar’s `AudioVarioGlue::NoValue`.
  - `CompleteFlightData`: extend with `bruttoAvg30s`, `nettoAvg30s`, and `varioValid`.
- **Algorithm parity**
  - TE: replace ground-speed delta with `trueAirspeed` from TAS proxy when available, else degrade gracefully to GPS speed.
  - Netto: `netto = brutto - sinkProvider.sinkAtSpeed(trueAirspeed)`.
  - Replay: ensure IGC samples call the same interfaces, so `varioValid` toggles identical to live flight.
- **Consumer updates**
  - Cards in Time & Weather + Variometer HUD should switch to the new averaged values.
  - Map overlays should display source badges (`TEK`, `GPS`), similar to XCSoar’s infobox logic.

## Verification Checklist
1. `:feature:map:testDebugUnitTest` passes.
2. Manual: tap the new FAB, confirm the HUD opens, cards update smoothly, and vario audio beeps in sync with the replay trace.
3. Manual: start/stop via SAF flow still works, HUD state persists when returning to General > IGC Replay.
4. Confirm live flight (sensor mode) is unaffected by checking that sensors resume and audio continues once replay stops.

## Next Phases (after this PR)
- Add windowed-wind smoothing (XCSoar’s 30 s exponential) so circling/straight estimates stay stable in turbulence.
- Pipe replay events into the vario audio service so we can mock accelerometer-derived netto later.
- Consider a HUD scrubber on the map screen for forward/rewind controls with coarse thumbnails.
