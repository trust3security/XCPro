
# XCPro -- "HAWK-like" Phone Variometer (S22 Ultra)  
**Implementation Plan for Codex / Engineering**  
Date: 2026-02-04  
Scope: **Real-time only**, phone sensors only (barometer + IMU + GNSS as available). No IGC replay.

---

## 0) TL;DR (what we're building)
A **HAWK-inspired variometer experience** on a modern Android phone: **fast**, **stable**, and **low false-lift spikes**, by doing the boring engineering well:

- **Baro-clocked** (update the vertical channel only when baro delivers a new sample)
- **Baro-gated fusion** (accel speeds response but cannot create lift "out of thin air")
- **Aggressive baro QC** (outlier rejection for pressure transients)
- **Adaptive trust in accel** (gust/turbulence/handling automatically reduces accel influence)
- **QNH decoupling** (QNH is for *display altitude*, not for the vario physics channel)

---

## 1) Verbatim design directive (keep this intact)

> Here's the blunt recommendation: ship "HAWK-like" without adding raw gyro and without replay IMU.  
> Use what XCPro already has (earth-frame vertical accel + attitude + baro) and build the "fast but not spiky" behavior with baro-gated fusion + aggressive outlier handling + adaptive trust in accel. Add gyro/replay IMU later only if you prove you need it.
>
> Why I'm confident that's the right call (based on your findings)
>
> No gyro feed: adding it forces you to widen the core sensor contract + plumbing + testing. That's a big blast radius for a feature that might not materially improve vario feel because...
>
> You already have attitude + earth-frame vertical acceleration via OrientationProcessor / AccelData.verticalAcceleration with isReliable. That's the key ingredient for a HAWK-style "quick response without false lift spikes."
>
> Replay has no IMU. Any "replay IMU" you invent will lie to you and will absolutely poison tuning.
>
> XCPro's vario loop is baro-gated. That's exactly how you avoid "IMU-only rocket spikes" and it matches the feel you're trying to replicate.
>
> So: don't boil the ocean. Build the HAWK feel inside the constraints of the existing pipeline first.
>
> What you should do (practical plan)  
> Phase 1 (MVP that can genuinely feel "HAWK-inspired")
>
> Goal: fast, stable, low false-lift spikes with current sensors.
>
> 1) Make a dedicated HAWK vario engine that is baro-clocked
>
> Trigger the filter only on new baro samples (like you noted is "non-negotiable").
>
> Treat accel as a helper input that can speed response, not as a driver that can create lift on its own.
>
> Key behavior:  
> If baro didn't change, vario shouldn't jump just because accel twitched.
>
> 2) Use the existing earth-frame vertical acceleration and gate it hard
>
> Consume AccelData.verticalAcceleration  
> Only use it when isReliable == true  
> When it's unreliable or stale: fall back to baro-only mode.
>
> This single decision is a massive part of "gust spike suppression."
>
> 3) Implement "gust filtering" as adaptive distrust of accel (not as fancy wind estimation)
>
> Most "false lift spikes" on phones come from turbulence/handling/rotation artifacts in accel and small pressure transients. A HAWK-like feel comes from not believing accel too much when the world is messy.
>
> Concrete mechanism (works well in practice):
>
> Maintain a short rolling window of vertical accel (e.g. last 300-700 ms).  
> Compute something like aRms or variance.  
> Map that to an adaptive accel noise value:
>
> When accel is smooth -> trust it more (faster response).  
> When accel is chattery -> trust it less (stability, fewer spikes).
>
> So gusts get "filtered" because they increase accel variance, which automatically turns accel's influence down.
>
> 4) Use a small Kalman filter (or complementary filter) that fuses baro + accel
>
> You don't need a huge EKF. A simple 2-state filter works:
>
> State:  
> h = altitude (or pressure-altitude)  
> v = vertical speed
>
> Model update at each baro tick:  
> Predict using accel as input (when reliable)  
> Correct using baro altitude
>
> The gust suppression happens via:
>
> adaptive accel noise (described above)  
> baro innovation gating (reject pressure blips)  
> no IMU-only update (baro-gated loop)
>
> 5) Handle QNH jumps cleanly (avoid them!)
>
> Your note is important: TE resets on QNH jumps; HAWK must not explode when someone changes QNH.
>
> Best approach for a vario engine:
>
> Drive the filter from raw pressure / pressure altitude with a fixed reference (QNE) for vario physics  
> Let display altitude do whatever it wants with QNH, but keep the vario filter decoupled
>
> If you must use a QNH-coupled altitude stream, then implement:
>
> Detect step changes (big instantaneous altitude delta with no physical acceleration support)  
> Reset / re-seed the filter when that happens
>
> 6) Integrate it where it will actually run
>
> You already found the truth: VarioServiceManager owns lifecycle.  
> So:
>
> Start/stop HAWK pipeline there (or via a parallel manager invoked there)  
> Don't bury it somewhere that dies when UI sleeps
>
> 7) Add a real settings/flag surface (don't ship "magic")
>
> You need:
>
> A feature flag: HAWK_ENABLED  
> A small config model: response feel, sink/lift smoothing, accel trust baseline, etc.
>
> Even if you keep UI minimal, you need a way to tune without code changes.
>
> 8) Reuse existing wind fusion (don't duplicate)
>
> Your existing WindSensorFusionRepository already does circling wind + confidence decay. Great.  
> Do not entangle wind into gust filtering in v1. Keep HAWK focused: vertical channel quality.
>
> Phase 1.5 (Make tuning possible without lying to yourself)
>
> This is the part most teams skip and then wonder why it never matches the "feel."
>
> Add a "truthful replay" path for tuning
>
> Since replay has no IMU:
>
> In replay mode: run baro-only and label it as such (internally via confidence)
>
> For real tuning/regression: create a separate sensor log format captured during live flights:
>
> baro pressure + timestamp  
> verticalAcceleration + reliability + timestamp  
> attitude (optional)  
> GPS (optional)
>
> Then you can replay real IMU later without inventing physics.
>
> You already have hawk_vario_logs*.txt. Lean into that: make it a first-class regression input.
>
> Should you add gyro now?
>
> No. Not for a HAWK-like vario feel.
>
> Add raw gyro only if you hit one of these:
>
> isReliable is false too often in real use (magnetic interference / phone placement) and the accel gating causes too much baro-only sluggishness  
> You want to replace Android's rotation vector with your own attitude estimation (bigger project)  
> You want consistent behavior across weird devices whose rotation vector implementation is bad
>
> Otherwise, gyro is complexity without guaranteed payoff.
>
> If you do add gyro later: make it optional, and don't make the HAWK engine depend on it.
>
> Should you add replay IMU now?
>
> Also no.
>
> IGC replay won't have it.  
> Synthetic IMU derived from GPS/pressure is basically "make up data that feels plausible," and you'll tune against nonsense.
>
> Do replay IMU only if you control the recording format and actually recorded IMU live.
>
> Exactly what I'd implement into XCPro next (in order)
>
> HawkVarioEngine (new)
>
> Inputs: baro samples, accel samples (+reliability), clock  
> Output: vario, smoothedVario, confidence, debug metrics (innovation, accelVariance)
>
> Barycentric / Kalman fusion with adaptive accel trust
>
> Adaptive accel noise from accel RMS window  
> Innovation gating on baro  
> Baro-gated stepping only
>
> Fallback modes
>
> If accel unreliable -> baro-only  
> If baro missing -> hold last output / decay confidence (do not integrate IMU alone)
>
> QNH jump guard
>
> Prefer pressure/QNE inside filter  
> Otherwise reset on detected step
>
> Service integration
>
> Start/stop inside VarioServiceManager  
> Respect background execution realities
>
> Preferences + feature flag
>
> Minimal UI, but tunable constants via prefs
>
> Regression harness
>
> Use those existing hawk log files  
> Add a way to dump state for A/B comparisons vs current vario
>
> The "gust filtering" story you can honestly claim
>
> If you do the above, your marketing claim can be true without hand-waving:
>
> Fast because accel contributes to prediction when it's clean  
> Stable because baro is the governor and accel gets downweighted during turbulence  
> Low false-lift spikes because:
>
> no IMU-only stepping  
> accel reliability gating  
> adaptive accel noise in bumps  
> baro outlier rejection
>
> That's basically the whole recipe.

---

## 2) "Set QNH once at launch" -- is that a good idea?
**Yes, with one condition:** the **variometer physics channel must be QNH-independent**.

### What QNH actually does (relevant to your vario)
- QNH is a **sea-level pressure reference** used to translate pressure into **AMSL altitude**.
- Changing QNH changes **absolute altitude** as a constant offset (plus ISA model effects), but it does **not** represent a real climb/sink.

### The trap
If you compute altitude from pressure using the current QNH and then differentiate it for vario, a QNH change causes a **step** in altitude -> the derivative looks like a huge climb/sink spike.

### The right architecture
- **Engine (vario physics):** run on **pressure / pressure altitude** with a fixed reference (QNE/STD), or treat altitude as "relative from start."  
- **UI / airspace / log:** apply QNH to produce **display AMSL altitude** separately.

### Practical recommendation
- Let QNH be set at app start / launch (and optionally at "armed for launch"), then:
  - **Lock it once flight starts** (unless user explicitly overrides)
  - If user changes QNH in flight: apply it to display altitude only; do **not** inject it into the vario filter state.

---

## 3) Phone reality check (S22 Ultra) -- what will and won't match HAWK hardware
What you can match well:
- **Vario feel** (fast response without chatter)
- **Spike suppression** (adaptive accel trust + baro QC)
- **Useful wind in circles** (from GNSS drift + turn detection), but not "true instantaneous wind"

Hard limits:
- Smartphone barometers can see **pressure transients** (airflow/handling), and update rates vary by device.
- Android IMU stacks differ across vendors; **reliability flags matter**.
- When the phone is in power-saving or background restricted states, sensors can degrade; the vario must run as a **foreground service** during flight.

---

## 4) Data acquisition rules (Android)
These are the boring details that decide whether the filter is stable.

### 4.1 Use sensor timestamps, not wall clock
Use `SensorEvent.timestamp` and treat it as **monotonic nanos since boot**, aligned with `SystemClock.elapsedRealtimeNanos()`.  
Ref: Android SensorEvent docs.  
https://developer.android.com/reference/android/hardware/SensorEvent

### 4.2 Rate limiting matters
If you register sensors via `registerListener()`, the motion sensors are **limited to 200 Hz** on Android 12+ unless you declare `HIGH_SAMPLING_RATE_SENSORS`. You don't need >200 Hz for a vario anyway.  
Ref: Sensors overview.  
https://developer.android.com/guide/topics/sensors/sensors_overview.html

### 4.3 Prefer baro-clocked loop
Even if accel arrives at 100-200 Hz, your vertical channel should step only on baro events (e.g., 10-50 Hz depending on device).

### 4.4 Prefer GAME rotation vector over full rotation vector (often)
If you're deriving attitude from Android sensors:
- `TYPE_GAME_ROTATION_VECTOR` avoids magnetometer drift (gyro+accel fusion).
- It often reduces "isReliable flapping" in real flight conditions.

(If XCPro already provides earth-frame vertical accel + reliability, use it.)

---

## 5) HAWK-like engine design

### 5.1 External interface (what the engine consumes/produces)
**Inputs**
- `baro`: pressure (Pa or hPa) + timestamp (ns)
- `accelZ`: earth-frame vertical acceleration (m/s^2) + timestamp (ns) + `reliable` boolean
- optional: GNSS vertical speed / climb for validation only (not as primary vario)

**Outputs**
- `v`: instantaneous vertical speed (m/s)
- `v_smooth`: "pilot feel" vertical speed (m/s) for audio
- `confidence`: 0..1
- debug: innovations, accel variance, gating decisions

### 5.2 Baro preprocessing + QC (pressure transients are your enemy)
Recommended pipeline (simple, effective):
1. Convert pressure -> "pressure altitude" using a fixed reference (STD/QNE) or use log-pressure.
2. Apply a small median/robust filter (e.g., 3-5 sample median) to kill single-sample spikes.
3. Innovation gating:
   - If the implied climb rate from baro between ticks exceeds a sane bound (e.g. 25-40 m/s for paragliding, higher for gliders), treat as outlier and skip correction.
   - Track "baro health" -> feed into measurement noise `R`.

### 5.3 Gust filtering (what it really is)
On a phone, most "gust spikes" are actually:
- accel artifacts (rotation/handling) leaking into vertical channel
- short pressure transients from airflow across the baro port
- timing jitter

So gust filtering is:
- **adaptive reduction of accel influence** when accel variance rises
- **baro outlier rejection** when pressure jumps are implausible

### 5.4 Minimal 2-state Kalman filter (recommended MVP)
State:  
`x = [h, v]^T`  (altitude, vertical speed)

At each **baro tick**, with `dt` since last tick:

**Prediction** (use accel only if reliable):
- `h_pred = h + v*dt + 0.5*a*dt^2`
- `v_pred = v + a*dt`
Where `a = accelZ` (earth-frame, gravity removed)

**Process noise** (key to stability):
- Set accel noise `sigma_a` adaptively from a rolling window:
  - Keep last 300-700 ms of accelZ
  - Compute RMS or variance
  - Map to `sigma_a` using a floor + gain, clamp to sane range

Then set `Q` from `sigma_a` and `dt` (standard constant-acceleration model).

**Measurement**:
- `z = h_baro` (from pressure altitude)
- `R = sigma_h^2` where `sigma_h` increases when baro looks noisy / outliers detected

**Update**:
Standard KF update with `H = [1, 0]`.

**Critical rule:** if accel is unreliable, set `a=0` and/or inflate `sigma_a` heavily -> baro-only behavior.

### 5.5 Output smoothing for "pilot feel"
You usually want two outputs:
- `v_raw` from the KF state (responsive)
- `v_audio` = lightly smoothed (to avoid audio chatter)

Implement a 1-pole low-pass on `v_raw` with different time constants for lift vs sink if desired:
- Lift: slightly faster (e.g., 0.25-0.5 s)
- Sink: slightly slower (e.g., 0.5-0.8 s)

Add hysteresis/deadband around 0 (e.g., +/-0.05 m/s) to stop "tick tick tick" on weak noise.

---

## 6) XCPro integration (practical)
Because we can't assume exact package names in this doc, treat these as **search terms** in the repo.

### 6.1 Where to hook
Search for:
- `VarioServiceManager` (or whatever component starts vario + audio)
- current baro processing loop (where vario is computed)
- the audio/beeper interface (consumer of vario signal)
- `OrientationProcessor`, `AccelData.verticalAcceleration`, `isReliable` (as you observed)

Goal: insert `HawkVarioEngine` as a **drop-in producer** of `vario` feeding the same downstream audio/UI consumers.

### 6.2 Suggested module layout
- `vario/hawk/HawkVarioEngine.kt`
- `vario/hawk/HawkConfig.kt`
- `vario/hawk/HawkDebug.kt` (ring buffers, counters)
- `vario/hawk/BaroQc.kt`
- `vario/hawk/AdaptiveAccelNoise.kt`

### 6.3 Feature flag + config
Add `HAWK_ENABLED` (remote flag or build-time + preference override).
Add `HawkConfig` fields:
- `accelTrustBase` (sigma_a floor)
- `accelTrustGain` (how fast sigma_a rises with variance)
- `baroNoiseBase` (sigma_h floor)
- `baroOutlierGate` (innovation threshold)
- `audioLiftTau`, `audioSinkTau`, `deadband`
- clamps: `vClamp`, `aClamp`

### 6.4 Lifecycle and background operation
Ensure the vario loop runs in a **foreground service** (flight mode), otherwise sensors can stall or degrade when the screen sleeps / OS throttles background work.

---

## 7) Acceptance criteria (what "HAWK-like" means in tests)
You need measurable targets, not vibes.

### 7.1 On-ground tests
- Phone sitting still indoors:
  - `v_audio` should be near 0 with no chatter
  - No spikes > +/-0.3 m/s (tunable)
- Phone gently rotated/handled:
  - `v_audio` should not spike unless baro changes accordingly

### 7.2 In-flight tests (minimum)
- Straight glide in smooth air:
  - no rapid "fake lift" beeps
- Turbulent air:
  - fewer false lift spikes than baseline algorithm
- Entering a thermal:
  - detection latency comparable to baseline or better (subjective + logged)

### 7.3 Logging
Log at runtime:
- baro pressure + timestamp
- h_baro, v_baro
- accelZ + reliable + variance
- KF state (h,v), innovation, gating decisions
- final outputs (v_raw, v_audio)

You can tune *without* lying to yourself.

---

## 8) Recommended tooling (Android Studio / Gradle) -- as of Feb 2026
Use the latest **stable channel** unless you have a specific bug that requires preview.

- **Android Studio (Stable):** Android Studio **Otter 3 Feature Drop | 2025.2.3** (stable release announced 2026-01-15).  
  Release note: https://androidstudio.googleblog.com/2026/01/android-studio-otter-3-feature-drop_0923772896.html
- **Android Gradle Plugin:** **9.0.0** (January 2026 release line).  
  https://developer.android.com/build/releases/gradle-plugin
- Keep Kotlin/AGP aligned (AGP 9.0 introduces "built-in Kotlin"; read the migration notes in the AGP release page).

---

## 9) Future extensions (only after MVP proves itself)
- Add raw gyro only if `isReliable` is frequently false in flight and you need your own attitude estimator.
- Add "netto" vario and "wind variance" smoothing as separate layers (do not mix into gust filtering in v1).
- Device-specific calibration profiles (baro noise, accel bias).

---

## 10) Engineer checklist (do this in order)
1. Wire baro + accelZ + reliable into a single timestamp-aligned stream.
2. Implement baro QC + robust smoothing.
3. Implement adaptive accel noise from accelZ variance.
4. Implement 2-state KF (baro tick loop).
5. Implement output smoothing + deadband + audio mapping.
6. Integrate behind `HAWK_ENABLED` flag.
7. Add logs + quick tuning UI (hidden dev menu is fine).
8. Flight test; tune; lock constants; ship.


