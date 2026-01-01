# Levo Replay Context (Codex CLI Handoff)

Purpose: capture the current replay/variometer debugging work so a new Codex CLI session can continue without losing context.
ASCII-only note: keep this file strictly ASCII (no Unicode symbols or smart quotes) to avoid cleanup passes.

## Goal
We are debugging the Levo variometer UI + replay pipeline so the blue needle, center number, and audio all match the replayed vario profile (0 -> +10 kt -> 0 in 30s) and behave like a real vario (XCSoar-style continuity, no fake jumps).

The user provided multiple logcat snippets showing:
- On replay, the center number sometimes freezes or drops to 0 while vario values keep updating.
- Blue needle jumps or does not match the center number.
- Audio sometimes continues after replay ends; later logs show REPLAY_AUDIO silence reason=finish works.

## What changed already (in this worktree)
1) Replay vario demo asset added
- `app/src/main/assets/replay/vario-demo-0-10-0-30s.igc`

2) Replay test added
- `feature/map/src/test/java/com/example/xcpro/sensors/LevoVarioPipelineTest.kt`

3) Logging added to replay pipeline
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - `REPLAY_BARO` log at 1 Hz with baro ts, pressure, alt, vs, dt, gpsAlt, validUntil.
- `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDataRepository.kt`
  - source gating between LIVE and REPLAY updates (`activeSource`), so live sensors can’t overwrite replay data.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `REPLAY_UI` log with `displayMs`, `displayUi`, label, units, src.
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
  - audio silence on replay finish/stop (`REPLAY_AUDIO silence reason=finish|stop`), sets activeSource back to LIVE.

4) Needle unit mismatch fix
- `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt`
  - display value and needle now use user units (converted from m/s via `unitsPreferences.verticalSpeed.fromSi(...)`).
  - label strips unit for consistency.

5) Replay validity window fix (to prevent display dropping to 0)
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - `varioValidUntil` now uses `max(VARIO_VALIDITY_MS, replayDeltaTimeMs)` so 1 Hz replay stays valid.

6) REPLAY_NEEDLE logging added
- `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt`
  - Logs `needle`, `target`, `delta`, stepN/stepT, overshoot flag, angle every 200 ms while replay is PLAYING (DEBUG only) to diagnose jitter.
  - Angle uses same sweep as `UIVariometer` (14 kt max, 300deg/28, -90deg offset) with a progressive scale (more resolution near 0, compressed at high climb).
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - passes `replayState` into `VariometerPanel`.

7) Replay chosen-vario logging
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
  - `REPLAY_CHOICE` (1 Hz) shows replayIgc, pressure vario, chosen vario, display, validity, source. Replay now prefers IGC vario when present.

8) Clean handoff back to LIVE after replay
- `IgcReplayController.finishReplay()/stop()` now clears replay data, fully resets the fusion pipeline, switches `activeSource` to LIVE, and immediately pushes a `null` LIVE sample so the UI/audio drop to zero without waiting for a live sensor tick.

9) Real-time sim wiring (replay cadence + noise)
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
  - Added `ReplayMode.REALTIME_SIM` with defaults: 10 Hz baro (100 ms), 1 Hz GPS, +/-30 ms jitter, pressure noise ~0.04 hPa, GPS altitude noise ~1.5 m, warmup 8 s.
  - Baro emits every step with noise; GPS emits at gpsStepMs; replay uses pressure path (no IGC vario override) in sim mode.

10) Replay realism fixes (Vario demo focus)
- `FlightDataCalculator`: replay now emits flight data at baro cadence (10 Hz) using cached GPS, vario validity floor = 5 s, and replay IGC vario timestamps use replay time (not wall clock) so pause/seek doesn’t age out values.
- `IgcReplayController`: passes replay timestamps into `updateReplayRealVario`.
- `OverlayPanels`: replay needle animation switches to critically damped spring to avoid overshoot with 10 Hz updates.
11) Live/replay parity improvements (baro-driven UI)
- `FlightDataCalculator`: baro loop now emits display frames (throttled ~10 Hz) in both live + replay using cached GPS.
- GPS loop emits only when baro is stale/unavailable (prevents 1 Hz UI jumps while keeping a fallback).
- Vario validity floor (5 s) applies to both live and replay.

## Parity plan: make replay == live (for on-ground troubleshooting)
- Keep vario baro-driven for both live + replay; throttle UI/audio to ~10 Hz on the baro loop using cached GPS.
- GPS loop is fallback only (emit when baro is stale/unavailable) so UI never steps at 1 Hz while baro is running.
- Use the same vario validity floor for both live and replay (~5 s); never inject zeros between valid samples.
- Keep identical smoothing/bucketing (0.1 m/s bucket, ~12 Hz UI throttle) for live and replay; no replay-only buckets.
- Keep noise/jitter configurable but default to realistic values; allow "noise off" only for deterministic tests.
- IMU note: XCSoar phone-sensor path does **not** use accelerometer for vario; accel only gates polar/netto (g-load). For parity, keep replay as baro-driven vario and skip synthetic IMU.

## Log filters to use
```
REPLAY_UI
REPLAY_NEEDLE
REPLAY_BARO
REPLAY_FORWARD
REPLAY_SAMPLE
REPLAY_AUDIO
REPLAY_CHOICE
```

## Key files to inspect next
- `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayPanels.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`

## Known remaining issue(s)
- Needle still appears jumpy; need fresh logs with new 200 ms `REPLAY_NEEDLE` to see if the spring animation or value jitter is the cause.
- Re-test repeat replays to confirm the new finish/stop reset (explicit null LIVE update) leaves both needle and center number at 0 immediately after completion.
- Confirm `REPLAY_CHOICE` shows `chosen` == `replayIgc` during replay; if not, continue investigating the override path.

## How to reproduce
1) Build and run debug.
2) On map screen, press the replay dev FAB and select `Vario demo`.
3) Press play; watch needle and center number.
4) Collect logcat with filters above; on second run ensure values reset to 0 after finish.
5) For needle jitter, grab a short replay with high-frequency logs (keep log span < 40s).

## XCSoar reference (phone sensors only)
- Brutto/TE vario is primary; if TE is missing, fallback is pressure/GPS altitude derivative. Value remains available until it expires (~5 s), not zeroed each frame.
- Continuity: UI/audio hold last valid vario between samples; no injected zeros while inside validity window.
- Validity is time-based off sample timestamps; expiration (~5 s) triggers silence/blank. Otherwise last value persists.
- Derivatives use sensor timestamps and update only on new samples; unchanged samples do not force zero.
- Audio uses same availability: climb beeps are pulsed, sink is continuous; silence only when vario becomes unavailable.
- Availability windows (from `Validity::Expire` in XCSoar): vario availability expires after ~5 s; altitude, speed, etc. have longer windows, so vario continuity is the limiting factor.
- Derivative path: `BasicComputer::Compute()` calculates gps_vario from pressure altitude first, then baro altitude, then GPS altitude as a last resort; only updates on a new timestamp, otherwise retains prior value (no zero).
- Brutto selection: `ComputeBruttoVario()` picks TE vario when available, else gps/pressure derivative; this becomes the single value that UI/audio and averages use.
- Averages: `AverageVarioComputer` runs a 30 s rolling filter for brutto/netto; reset when circling state changes or time jumps.
- Audio flow: `MergeThread::Tick()` sends brutto vario to `AudioVarioGlue::SetValue`; if availability expired, it calls `NoValue()` to silence; the synthesizer pulses climb tones and continuous sink.
- Expiry mechanics: `NMEAInfo::Expire()` only clears vario when its availability expires; it does not force zero between samples. The clock for expiry is steady-clock based, not “value changed.”

## What to adopt for Levo
- During replay, keep replay vario valid for several seconds (match continuity) and avoid per-frame zeros; expire only when the window passes or replay stops.
- Use replay sample timestamps as the timebase for validity/derivatives.
- Drive UI and audio from the same availability flag; silence only on expiry/stop.
- Keep live sensor updates gated out while replay is active; hand back to LIVE after stop/finish.
- If a strict mode remains for live sensors, keep replay on the continuity model so needle/number/audio stay aligned.

## Expected variometer behavior (needle + center number + audio)
- Smooth, continuous motion at sensor cadence (5–20 Hz typical) with gentle damping; avoid 1 Hz stair steps.
- Moderate damping so it settles to new lift/sink in ~300–500 ms without overshoot or bounce.
- Same units and dial scale as the widget (14 kt max with progressive scale); labels reflect the true value even when the needle is compressed at high climb.
- Hold last valid value while it is fresh (≈3–5 s); never inject zeros between valid samples.
- Needle, numeric display, and tones must reflect the same filtered value at the same moments.
- Small realistic noise wiggle is fine; large frame-to-frame jumps should only occur on true spikes.
- Cross zero smoothly on direction changes; on stop/finish, drop to zero (or blank) immediately rather than lingering.

## XCSoar (phone sensors) reference notes
- Baro path: Android `NonGPSSensors` feeds pressure events into a Kalman filter (`DeviceDescriptor::OnBarometricPressureSensor`), then computes uncompensated vario from pressure derivative (`ComputeNoncompVario`), updating both pressure and vario every sensor event (no throttling).
- Validity/expiry: vario availability expires after 5 s (`noncomp_vario_available.Expire(5s)`), GPS/pressure altitudes after 30 s, so the last vario value is held (no injected zeros) until the window expires.
- Derivation cadence: `ComputeGPSVario` only updates when a new pressure/altitude sample arrives; otherwise it keeps the previous vario—no frame-by-frame zeroing between samples.
- Source selection: `ComputeBruttoVario` prefers TE vario if present; otherwise uses the pressure-derived/gps vario. GPS time deltas come from the sensor timestamps, not wall clock.
- Continuity model takeaway: fast baro-derived vario drives UI/audio; slow GPS is just a fallback/timebase, and values persist across gaps until explicit expiry.

## Real-time sim (closer to in-flight behavior)
- To get even closer to true in-flight behavior than just higher cadence, we should also mimic the sensor characteristics and processing chain that real flights experience.
  The replay should act like the phone baro/GPS are actually producing data, not just a perfect profile. That means:
  1) Match real sensor timing jitter
     - Real sensors are not perfectly periodic. Add small randomized jitter to sample intervals (e.g., +/-20-50 ms around 100-200 ms).
     - This exposes UI/audio stability under realistic timing.
  2) Replay raw pressure, not just altitude/vario
     - The phone provides pressure, which then converts to altitude, then vario.
     - Feeding altitude directly skips one layer of noise and smoothing.
     - Convert the profile to pressure using the same formula as the live path and emit pressure samples.
  3) Inject realistic sensor noise
     - Baro noise: add small Gaussian noise to pressure (or altitude) matching device characteristics.
     - GPS noise: add small altitude noise at its slower rate (1 Hz).
     - This tests the filtering and how jitter shows up on the needle/audio.
  4) Match the live filter chain
     - Ensure replay goes through the exact same smoothing (same baro filters, low-pass, vario smoothing) as live data.
     - Avoid any "shortcut" in replay that bypasses those filters.
  5) Model "sensor warm-up"
     - Live sensors often stabilize after a few seconds; mimic this by ramping noise/variance down over the first 5-10 s.
  6) Match audio update cadence
     - Audio may be triggered at different cadence than UI. Ensure audio is fed the same values and timing as live.
  7) Use a real flight recording to validate
     - Compare the replay output against a short real flight log (pressure/GPS) to tune noise/jitter parameters until the UI/audio "feels" identical.

Summary: The closest simulation isn't just more points; it's pressure-level replay with realistic timing jitter + sensor noise + same filter chain. That will make the
needle/audio behave almost exactly like bench-tested live sensors. If you want, I can outline a minimal "real-time sim" mode with defaults for jitter/noise so we can
iterate fast.

## Repo housekeeping
- `AGENTS.md` not found in this repo (searched). CODING_POLICY.md is authoritative.
- Git status currently has multiple modified files + new asset/test/doc (see `git status -sb`).
