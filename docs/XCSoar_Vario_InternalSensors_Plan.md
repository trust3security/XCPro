# XCSoar‑style Vario Using Only Internal Phone Sensors (Baro + IMU)  
*Implementation notes + Kotlin/Compose/MVVM plan for Codex*

> Goal: replicate the **“feel”** of XCSoar’s vario pipeline on Android **without external TE/pitot**.  
> Reality: XCSoar’s primary vario is **barometric**; GPS is typically a slow helper.  
> This doc focuses on **internal sensors only**: phone **barometer + IMU** (accel/gyro).

---

## 1) What “XCSoar vario” means in practice

XCSoar’s vario behavior, from a pilot’s perspective, is basically:

1. **Barometric altitude** is the authoritative altitude source.
2. **Vario** is the *time derivative* of baro altitude (climb/sink rate).
3. The derivative is **filtered** heavily enough to avoid “needle chatter” but lightly enough to feel fast.
4. Optional helpers (often GPS) are used for slow bias/drift correction; **do not** drive audio directly.

On a phone with no pitot/TE probe, you can still produce an excellent vario by doing:
- Baro altitude → robust derivative → filter → audio/needle.
- IMU (vertical acceleration) → optional **anticipation / de‑lag** and turbulence handling.

---

## 2) Data sources (Android)

### 2.1 Barometer
- `Sensor.TYPE_PRESSURE` provides pressure in **hPa**.
- Convert pressure to altitude via ISA approximation (good enough for vario):
  - Use Android’s `SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)`
  - Or your own formula if you want control.

### 2.2 IMU (optional, for “Hawk-ish” feel but still XCSoar-like)
- Accelerometer: `TYPE_ACCELEROMETER`
- Gyro: `TYPE_GYROSCOPE`
- Rotation vector: `TYPE_GAME_ROTATION_VECTOR` (or `TYPE_ROTATION_VECTOR`) for roll/pitch estimation.
  - You mainly need **roll/pitch** to estimate vertical accel in earth frame.
  - Yaw doesn’t matter much for vario.

### 2.3 GPS (NOT used here)
This doc intentionally excludes GPS. If you later add GPS, treat it as:
- Slow drift control only (low rate, low weight).
- Never the primary instantaneous audio vario.

---

## 3) Core algorithm (baro-only baseline)

### 3.1 Convert pressure → altitude
Let `h[k]` be altitude in meters at sample time `t[k]`.

### 3.2 Compute raw vertical speed (finite difference)
Avoid naive `Δh/Δt` with tiny dt noise → still okay, but filter right after.

Raw:
```
v_raw[k] = (h[k] - h[k-1]) / (t[k] - t[k-1])
```

### 3.3 Filter vario (recommended: IIR low-pass on v_raw)
Single-pole low-pass:
```
alpha = exp(-dt / tau)
v_f[k] = alpha * v_f[k-1] + (1 - alpha) * v_raw[k]
```
Typical starting values:
- `tau = 0.7s` for fast needle/audio
- `tau = 1.2s` for smoother needle
- For **average** vario display: `tau = 8–15s`

> You can implement two channels:  
> **fastVario** (audio/needle) and **avgVario** (trend).

### 3.4 Optional: “deadband” around zero
To reduce annoying beeps and micro-chatter:
- If `abs(v_f) < deadband`, treat as 0 for audio or compress.

Typical:
- `deadband = 0.05 to 0.15 m/s` depending on taste.

---

## 4) Improving “feel” with IMU (still internal sensors)

The baro derivative can lag slightly in turbulence. IMU can help by providing short‑term vertical acceleration.

### 4.1 Get vertical specific acceleration in earth frame
Use rotation vector → rotation matrix `R_nb` (body→nav).

1. Read accelerometer `a_b` (m/s²). It includes gravity.
2. Rotate to nav: `a_n = R_nb * a_b`
3. Extract “up” axis component (choose ENU or NED consistently). In ENU, `up = +Z`.
4. Remove gravity: `a_up = a_n_up - g` (sign depends on your convention).

Sanity check standing still:
- `a_up` should be near **0** after gravity removal.

### 4.2 Complementary fusion (“de-lag”)
A simple and effective approach:
- Keep baro-derived `v_baro` as truth at low frequency.
- Use IMU integrated acceleration as high-frequency boost.

One practical form (complementary filter on velocity):
1. Maintain `v_imu[k] = v_imu[k-1] + a_up * dt`
2. Fuse:
```
v_fused = v_baro_low + (v_imu - v_imu_low)
```
Where:
- `v_baro_low` is baro vario filtered with `tau_low ~ 1.0s`
- `v_imu_low` is the IMU-integrated velocity filtered with a longer tau (e.g., 2–4s)
This keeps **fast response** (IMU) but avoids IMU drift (baro anchors).

> If you want to keep it ultra-XCSoar-like, you can skip IMU fusion entirely.  
> If you want “Hawk-ish snappiness” without external TE, IMU fusion is worth it.

### 4.3 Gating IMU contribution
IMU is noisy when:
- phone moves in mount, vibration, tapping.
So gate IMU boost when:
- rotation rate is huge
- accelerometer magnitude deviates wildly
- phone not firmly mounted (optional heuristic)

---

## 5) Output channels (what to expose in app)

### 5.1 Instantaneous vario (audio + needle)
- Source: `fastVario` (baro derivative with tau ~ 0.7–1.2s)
- Optional: IMU boost.

### 5.2 Average vario (trend / thermal strength)
- Source: `avgVario` (tau ~ 10s)

### 5.3 Barograph
- Plot `h[k]` and/or `fastVario` and/or `avgVario`.
- Barograph should use baro altitude, smoothed gently.

---

## 6) JS1‑C 18m polar integration (optional, for Netto-like display)

XCSoar’s “netto” concept is typically:
```
netto = vario - expectedSink(speed, ballast, bugs, bank)
```
With phone-only you only have **groundspeed**, not TAS, so treat it as approximate.

### 6.1 Represent polar as a quadratic sink curve
Use sink (positive down) as:
```
sink0(V) = a*V^2 + b*V + c    (V in m/s, sink in m/s)
```
Fit `a,b,c` from JS1‑C 18m polar points.

### 6.2 Bank/load-factor adjustment (simple)
A common approximation:
```
sink(V, n) = sink0(V) * n^(3/2)
```
Estimate load factor from IMU:
- `n ≈ |specificForce| / g`

### 6.3 Phone-netto (approx)
```
V = groundspeed_mps
netto ≈ fastVario - sink(V, n)
```
Keep this optional and **low-trust** in strong wind.

---

## 7) Kotlin implementation plan (suggested)

### 7.1 Modules / layers (MVVM + UDF)
**Data layer**
- `BaroSensorDataSource`: pressure → altitude stream
- `ImuSensorDataSource`: accel/gyro/rotation stream
- (Optional later) `GpsDataSource`

**Domain layer**
- `VarioEngine` (pure Kotlin):
  - accepts timestamped samples
  - outputs `fastVario`, `avgVario`, `altitude`, `netto` (optional)
- `Filters`:
  - `IirLowPass`
  - `ComplementaryVelocityFusion` (optional)

**Presentation**
- `VarioViewModel`:
  - single state: `VarioUiState(alt, fast, avg, netto, quality, ...)`
- Compose UI:
  - needle, numeric, graph, audio toggles

### 7.2 Update rates / threading
- Sensors arrive at different rates; unify with timestamps.
- Use coroutines + `Flow`:
  - Baro: request fastest practical (`SENSOR_DELAY_GAME` or custom microseconds)
  - IMU/rotation: similar
- In `VarioEngine`, process events as they arrive, but compute output at a stable cadence (e.g., 20–50 Hz) using the latest samples.

### 7.3 Quality flags (important)
Expose a `VarioQuality` enum:
- `OK`
- `BARO_STALE`
- `IMU_UNRELIABLE`
- `MOUNT_MOVING`
- etc.
Use these to avoid bad audio.

### 7.4 Test strategy
- Unit test filters with synthetic signals:
  - step climb, sine wave thermal, turbulence noise
- Replay mode:
  - feed recorded sensor logs through the same engine.

---

## 8) Recommended default parameters (starting point)
- Baro altitude smoothing (optional): `tau_h = 0.3–0.6s`
- Fast vario: `tau_fast = 0.8s`
- Avg vario: `tau_avg = 12s`
- Deadband: `0.10 m/s`
- IMU fusion:
  - IMU velocity low-pass: `tau_imu_low = 3s`
  - IMU boost clamp: `±8 m/s` (safety)

Tune in flight, not on the ground.

---

## 9) Deliverables Codex should generate
1. `VarioEngine.kt` (pure Kotlin, no Android deps)
2. `IirLowPass.kt`
3. `AltitudeFromPressure.kt`
4. `ImuVerticalAccel.kt` (rotation matrix + gravity removal)
5. `SensorRepository.kt` (Flows for baro + IMU)
6. `VarioViewModel.kt`
7. Compose UI:
   - needle + numeric + average + optional netto
8. Optional audio tone generator tied to `fastVario` with deadband and hysteresis

---

## 10) Notes / honest limitations (phone-only)
- Phone baro is surprisingly good for vario, but:
  - susceptible to cabin pressure changes / vents / phone heating
- IMU helps response, but:
  - requires stable mounting and decent attitude estimation
- Without pitot/TAS:
  - true wind estimation is not robust
  - netto using groundspeed is approximate in strong winds

---

## Appendix A: Minimal pseudocode

```kotlin
fun onBaroSample(t: Double, pressureHpa: Double) {
  val h = pressureToAltitude(pressureHpa)
  val dt = t - lastT
  if (dt <= 0) return

  val vRaw = (h - lastH) / dt
  fastV = lpFast.update(vRaw, dt)   // tau ~0.8s
  avgV  = lpAvg.update(vRaw, dt)    // tau ~12s

  // optional IMU fusion
  if (imuEnabled && hasImu) fastV = fuse(fastV, imuVerticalAccel, dt)

  // optional netto
  if (nettoEnabled) {
     val V = lastGroundSpeedMps // if you later add GPS; else omit
     val sink = polarSink(V, loadFactor)
     netto = fastV - sink
  }

  emitState(h, fastV, avgV, netto)
  lastH = h; lastT = t
}
```

---

If you want the next step: a **concrete Kotlin skeleton** (files + interfaces + DI wiring) matching your XCPro architecture.
