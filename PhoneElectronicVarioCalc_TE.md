# XC Pro: Electronic TE Variometer Design (Android / S22 Ultra)

## Overview
This document defines the architecture and math for implementing an **electronic Total Energy (TE) variometer** in XC Pro using only the sensors available on a **Samsung S22 Ultra** — no pitot or static plumbing. The design fuses **barometric pressure**, **IMU accelerations**, and **GPS velocity**, aligns them to the NED frame, and models energy transitions while rejecting “stick lift” and gust artifacts.

---

## 1. Sensor Inputs and Rates

| Sensor | Quantity | Units | Target Rate | Notes |
|:--|:--|:--|:--|:--|
| Barometer | Pressure (p) | Pa | 25–50 Hz | Converts to altitude and climb rate |
| Accelerometer | a_b | m/s² | 100–200 Hz | Linear acceleration in body frame |
| Gyroscope | ω_b | rad/s | 100–200 Hz | Angular rate for pitch/roll/yaw |
| Rotation Vector | Quaternion q_b→n | — | 50–100 Hz | Orientation body → NED |
| GPS | v_g, track, climb | m/s | 1–10 Hz | Slow; used for long-term velocity correction |

---

## 2. Coordinate Transform and Gravity Compensation

Transform accelerations to NED (North-East-Down):
```
a_n = q_b→n * a_b * q_b→n⁻¹
a_lin_n = a_n - [0, 0, -g]
```
This removes gravity from the body-frame accelerations.

---

## 3. Pressure to Altitude and Climb Rate

Convert pressure to geopotential altitude:
```
h = (T0/L) * [1 - (p/p0)^(R*L/g)]
```
Then smooth and differentiate altitude (Savitzky–Golay or KF) to get climb rate \.dot h_baro.

---

## 4. Electronic TE Proxy

Without pitot input, forward speed comes from GPS + short-term IMU integration.

```
dot_h_TE ≈ dot_h_baro + (V/g) * a_x
```
Where V is forward velocity (GPS-smoothed + IMU high-pass term), and a_x is forward acceleration projected into NED.

---

## 5. Gust and Stick-Lift Rejection

- **Load factor filter:** ignore readings when |n_z − 1| > 0.05  
- **Pitch-rate gate:** down-weight when |θ̇| > 10–15°/s  
- **Lateral accel guard:** suppress updates if |a_y| > 0.5 m/s²

These prevent false vario spikes during turbulence or control inputs.

---

## 6. Airmass (Netto) Calculation

With the JS1C 18m heavy-wing polar:

```
S(V) = 6.9081e-5 * V² - 6.4877e-3 * V + 0.4543
w ≈ dot_h_TE - S(V)
```
V in km/h, S(V) in m/s.

---

## 7. Kalman Filter (EKF)

State vector:
```
x = [h, dot_h, V, b_p, b_ax]
```
Predicts altitude, climb, and velocity; estimates baro/accel biases. Updates with baro and GPS measurements.  
Tuned for fast V response and low-latency climb output.

---

## 8. Filtering and Audio

Two-stage smoothing:
- Fast (0.3–0.5 s) for audio tone
- Slow (1.2–1.8 s) for display

Predict 150–200 ms latency using acceleration derivative.

---

## 9. Mounting and Alignment

Phone aligned to fuselage at calibration; quaternion defines forward vector \hat{x}_n.  
If roll exceeds ±45° for 2+ s, re-align.

---

## 10. Polar and Bank Penalty

Optional user-entered static polar (quadratic, per glider).  
Bank penalty: ΔS = k_φ * sin²φ

---

## 11. Tone Mapping

Map airmass w (m/s) → tone frequency:

| w (m/s) | Tone | Type |
|:--|:--|:--|
| < -2.0 | 300 Hz | Sink alarm |
| -0.1 – +0.1 | — | Deadband |
| +0.5 | 500 Hz | Low lift |
| +3.0 | 1800 Hz | Strong lift |

Duty cycle increases with w. Use AudioTrack with short precomputed waveforms.

---

## 12. Sanity Checks

- Clamp |dot_h_TE| ≤ 10 m/s
- Clamp |a_x| ≤ 6 m/s²
- Ignore GPS speed jumps >8 m/s
- Freeze TE during shock events

---

## 13. Simulation and Replay

- IGC file playback injects V(t), h(t), a_x(t) into same pipeline.
- Bench test: static = 0 m/s.
- Drive test: ensure V/g * a_x cancels speed-vario.
- Compare vs reference vario to tune α, guard thresholds, EKF Q/R.

---

## 14. Kotlin Integration

- **Repository:** collects sensors → Flow<RawSample>
- **UseCase:** EKF fusion, TE calc, filtering
- **ViewModel:** UiState(dot_h_TE, w, V, h)
- **AudioService:** tone loop 50 Hz
- **Simulator:** feed IGC data

---

## 15. Tunables

| Parameter | Typical | Description |
|:--|:--|:--|
| α_fast | 0.35–0.45 | Audio EMA |
| α_slow | 1.5 s | Display EMA |
| n_z gate | ±0.05 | Load rejection |
| θ̇ gate | 12°/s | Pitch gate |
| ΔV leak | 6–10 s | Integrator time |
| R_h | 1.5–3 m | Baro noise |
| R_V | 0.6–1.2 m/s | GPS noise |
| Q_V | 0.5–1.0 (m/s)²/s | Process noise |
| k_f | 200–300 Hz/m/s | Audio slope |

---

## 16. Outcome

This gives an **electronic TE variometer** that reacts within ~0.3 s, rejects control-stick artifacts, approximates a HAWK’s TE logic, and runs entirely on a smartphone.

